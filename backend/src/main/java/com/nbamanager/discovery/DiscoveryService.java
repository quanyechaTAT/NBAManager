package com.nbamanager.discovery;

import com.nbamanager.discovery.DiscoveryTypes.*;
import com.nbamanager.exception.ApiException;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class DiscoveryService {
    private final DiscoveryCatalog catalog;
    private final DiscoveryStore store;
    private final DiscoveryGateway gateway;
    private final PlatformTransactionManager transactions;
    private final Map<String,Snapshot> fallbackSnapshots=new ConcurrentHashMap<>();
    private record Snapshot(Map<String,Object> binding, List<Map<String,Object>> rows, long expires) {}

    public Response fetch(long user, String operation, Map<String,String> parameters) {
        Map<String,Object> request=normalize(user,operation,parameters);
        Preferences preferences=store.preferences(user);
        request.put("personalized",preferences.personalized()); request.put("preferenceRevision",preferences.revision());
        if(preferences.clearedAt()!=null) request.put("clearedAt",preferences.clearedAt());
        // Search does not rank by user interests; authoritative exclusions still run after hydration.
        if(operation.equals("recommendations")) {
            request.put("followed",preferences.personalized()?store.followed(user):List.of());
            request.put("blocked",store.blocked(user));
        }
        String cursor=(String)request.get("cursor");
        Map<String,Object> response=cursor!=null&&cursor.startsWith("db:")?fallback(request):gateway.fetch(operation,request);
        if(response==null || !(response.get("items") instanceof List<?>)) {
            if(cursor!=null) throw new ApiException(HttpStatus.GONE,"检索服务暂不可用，请刷新列表重试");
            response=fallback(request);
        }
        // JPA hydration and the following JDBC checks share one short transaction/connection.
        // Keep the network call outside it; OSIV must not hold one connection while JDBC asks for another.
        Map<String,Object> selected=response;
        return new TransactionTemplate(transactions).execute(status -> complete(user,request,selected,preferences));
    }

    private Response complete(long user,Map<String,Object> request,Map<String,Object> response,Preferences preferences) {
        List<Map<String,Object>> rows=rows(response.get("items"));
        List<String> keys=rows.stream().map(row->(String)row.get("itemKey")).toList();
        Map<String,Item> existing=catalog.hydrate(keys);
        Set<String> blocked=store.blockedAmong(user,keys);
        List<Item> items=new ArrayList<>();
        List<Map<String,Object>> served=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(var row:rows) {
            String key=(String)row.get("itemKey");
            Item item=existing.get(key);
            if(item==null||blocked.contains(key)||!seen.add(key)) continue;
            items.add(new Item(item.itemKey(),item.type(),item.id(),item.title(),item.summary(),item.imageUrl(),
                    item.publishedAt(),row.get("reason") instanceof String reason?reason:null,item.target()));
            served.add(row);
        }
        if(store.preferences(user).revision()!=preferences.revision())
            throw new ApiException(HttpStatus.CONFLICT,"推荐偏好已更新，请刷新列表");
        String strategy=(String)response.getOrDefault("strategyVersion","baseline-v1");
        boolean degraded=Boolean.TRUE.equals(response.get("degraded"));
        String requestId=store.served(user,(String)request.get("scene"),strategy,(String)response.get("modelVersion"),
                Map.of("personalized",preferences.personalized(),"preferenceRevision",preferences.revision(),
                        "degraded",degraded),served);
        return new Response(requestId,items,(String)response.get("nextCursor"),strategy,degraded);
    }

    private Map<String,Object> fallback(Map<String,Object> request) {
        long now=System.currentTimeMillis();
        Map<String,Object> binding=new HashMap<>(request); binding.remove("cursor"); binding.remove("blocked"); binding.remove("followed");
        String cursor=(String)request.get("cursor");
        int limit=(int)request.get("limit"),offset=0;
        String token;
        Snapshot snapshot;
        if(cursor==null) {
            fallbackSnapshots.entrySet().removeIf(entry->entry.getValue().expires()<now);
            if(fallbackSnapshots.size()>=1000) fallbackSnapshots.remove(fallbackSnapshots.keySet().iterator().next());
            token=UUID.randomUUID().toString();
            snapshot=new Snapshot(binding,catalog.fallback(request),now+600000);
            fallbackSnapshots.put(token,snapshot);
        } else {
            String[] parts=cursor.split(":");
            try { token=parts[1]; offset=Integer.parseInt(parts[2]); }
            catch(Exception error) { throw new ApiException(HttpStatus.GONE,"列表游标无效，请刷新"); }
            snapshot=fallbackSnapshots.get(token);
            if(snapshot==null||snapshot.expires()<now||!snapshot.binding().equals(binding)||offset<0||offset>=snapshot.rows().size())
                throw new ApiException(HttpStatus.GONE,"列表已过期或不属于当前查询，请刷新");
        }
        int end=Math.min(snapshot.rows().size(),offset+limit);
        Map<String,Object> response=new HashMap<>();
        response.put("items",snapshot.rows().subList(offset,end));
        response.put("nextCursor",end<snapshot.rows().size()?"db:"+token+":"+end:null);
        response.put("strategyVersion","database-fallback-v1"); response.put("degraded",true);
        return response;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> rows(Object value) {
        if(!(value instanceof List<?> list)||list.size()>100) throw new IllegalStateException("Invalid retrieval response");
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Object row:list) {
            if(!(row instanceof Map<?,?> map)||!(map.get("itemKey") instanceof String key)) throw new IllegalStateException("Invalid item");
            Key.parse(key); rows.add((Map<String,Object>)map);
        }
        return rows;
    }

    static Map<String,Object> normalize(long user,String operation,Map<String,String> parameters) {
        Map<String,Object> request=new HashMap<>(); request.put("userId",user);
        String scene=operation.equals("recommendations")?parameters.getOrDefault("scene","HOME"):"SEARCH";
        if(!DiscoveryTypes.SCENES.contains(scene)||operation.equals("recommendations")&&scene.equals("SEARCH")) throw DiscoveryTypes.bad("推荐场景无效");
        request.put("scene",scene);
        String q=parameters.getOrDefault("q","").trim(); if(q.length()>200) throw DiscoveryTypes.bad("关键词过长");
        request.put("q",q);
        String types=parameters.getOrDefault("types","").trim();
        List<String> selected=types.isBlank()?List.of():Arrays.stream(types.split(",")).distinct().sorted().toList();
        if(!DiscoveryTypes.TYPES.containsAll(selected)) throw DiscoveryTypes.bad("搜索类型无效"); request.put("types",selected);
        int limit;
        try { limit=Integer.parseInt(parameters.getOrDefault("limit",operation.equals("suggest")?"8":scene.equals("HOME")?"6":"20")); }
        catch(NumberFormatException error) { throw DiscoveryTypes.bad("分页数量无效"); }
        if(limit<1||limit>20) throw DiscoveryTypes.bad("分页数量须为 1 至 20"); request.put("limit",limit);
        for(String key:List.of("team","season","itemKey","cursor")) {
            String value=parameters.get(key); if(value==null||value.isBlank()) continue;
            if(value.length()>100) throw DiscoveryTypes.bad("筛选参数过长"); request.put(key,value);
        }
        if(request.containsKey("team")&&!Key.parse((String)request.get("team")).type().equals("TEAM")) throw DiscoveryTypes.bad("球队筛选无效");
        if(scene.endsWith("_DETAIL")) {
            Key key=Key.parse((String)request.get("itemKey"));
            if(!scene.startsWith(key.type())) throw DiscoveryTypes.bad("详情类型与场景不一致");
        }
        String mode=parameters.getOrDefault("mode","hybrid");
        if(!Set.of("hybrid","lexical").contains(mode)) throw DiscoveryTypes.bad("搜索模式无效"); request.put("mode",mode);
        try {
            for(String key:List.of("dateFrom","dateTo")) if(parameters.containsKey(key)&&!parameters.get(key).isBlank()) {
                LocalDate date=LocalDate.parse(parameters.get(key));
                if(key.equals("dateTo")) date=date.plusDays(1);
                request.put(key,date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
            }
            if(request.containsKey("dateFrom")&&request.containsKey("dateTo")&&(long)request.get("dateFrom")>=(long)request.get("dateTo"))
                throw DiscoveryTypes.bad("日期范围无效");
        } catch(java.time.format.DateTimeParseException error) { throw DiscoveryTypes.bad("日期格式应为 YYYY-MM-DD"); }
        return request;
    }
}

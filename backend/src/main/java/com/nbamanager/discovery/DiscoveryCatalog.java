package com.nbamanager.discovery;

import com.nbamanager.discovery.DiscoveryTypes.*;
import com.nbamanager.domain.*;
import com.nbamanager.repository.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoritative hydration after retrieval: no stale titles, private fields, or deleted content. */
@Service
@RequiredArgsConstructor
public class DiscoveryCatalog {
    private final PlayerRepository players;
    private final TeamRepository teams;
    private final MatchRecordRepository matches;
    private final GameNewsRepository news;
    private final PostRepository posts;
    private final JdbcTemplate jdbc;
    private static final Map<String,String> TABLES = Map.of("PLAYER","players","TEAM","teams",
            "MATCH","match_records","NEWS","game_news","POST","posts");

    @Transactional(readOnly=true)
    public Map<String, Item> hydrate(Collection<String> keys) {
        var grouped = keys.stream().map(Key::parse).collect(Collectors.groupingBy(Key::type,
                Collectors.mapping(Key::id, Collectors.toSet())));
        Map<String,Item> result = new HashMap<>();
        for (var group : grouped.entrySet()) {
            switch (group.getKey()) {
                case "PLAYER" -> players.findDiscoveryByIdIn(group.getValue()).forEach(p -> put(result, "PLAYER", p.getId(),
                        p.getName(), text(p.getNameEn()) + " · " + p.getTeam().getName() + " · " + text(p.getPosition()),
                        null, null, "/players/detail", Map.of("id", p.getId().toString())));
                case "TEAM" -> teams.findAllById(group.getValue()).forEach(t -> put(result,"TEAM",t.getId(),t.getName(),
                        text(t.getNameEn()) + " · " + t.getCity() + " · " + t.getConference(), t.getLogoUrl(), null,
                        "/teams/detail", Map.of("id",t.getId().toString(),"name",t.getName())));
                case "MATCH" -> matches.findAllById(group.getValue()).forEach(m -> {
                    Map<String,String> query = new HashMap<>();
                    query.put("homeTeam",m.getHomeTeam()); query.put("awayTeam",m.getAwayTeam());
                    query.put("status",m.getStatus()); query.put("date",m.getMatchDate().toString());
                    query.put("recordId",m.getId().toString());
                    if (m.getNbaGameId()!=null && !m.getNbaGameId().isBlank()) query.put("gameId",m.getNbaGameId());
                    put(result,"MATCH",m.getId(),m.getHomeTeam()+" vs "+m.getAwayTeam(),
                            m.getSeason()+" · "+m.getMatchDate()+" · "+score(m,"homeScore",m.getHomeScore())+":"+score(m,"awayScore",m.getAwayScore()),null,
                            m.getMatchDate().atStartOfDay(),"/match-detail",query);
                });
                case "NEWS" -> news.findAllById(group.getValue()).forEach(n -> put(result,"NEWS",n.getId(),n.getTitle(),
                        n.getSummary(),n.getImageUrl(),n.getPublishedAt()==null?n.getCreateTime():n.getPublishedAt(),
                        "/news",Map.of("newsId",n.getId().toString())));
                case "POST" -> posts.findAllById(group.getValue()).forEach(p -> put(result,"POST",p.getId(),p.getTitle(),
                        p.getContent(),null,p.getCreateTime(),"/community/post",Map.of("id",p.getId().toString())));
                default -> throw DiscoveryTypes.bad("未知内容类型");
            }
        }
        return result;
    }

    private static void put(Map<String,Item> result, String kind, long id, String title, String summary,
                            String image, LocalDateTime published, String path, Map<String,String> query) {
        String key = kind+":"+id;
        String plain = text(summary).replaceAll("<[^>]*>"," ").replaceAll("\\s+"," ").trim();
        if (plain.length()>240) plain=plain.substring(0,240)+"…";
        result.put(key,new Item(key,kind,id,title,plain,image,
                published==null?null:published.atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString(),
                null,new Target(path,query)));
    }
    private static String text(String value) { return value==null?"":value; }
    private static String score(MatchRecord match,String field,Integer value) {
        return !"SCHEDULED".equalsIgnoreCase(match.getStatus())&&match.canDisplay(field)&&value!=null&&value>=0?value.toString():"--";
    }

    /** Bounded emergency scan, never an unbounded LIKE over the source tables. */
    public List<Map<String,Object>> fallback(Map<String,Object> request) {
        String scene = (String) request.get("scene");
        @SuppressWarnings("unchecked") List<String> selected = (List<String>)request.get("types");
        if (!scene.equals("SEARCH")) selected = scene.startsWith("NEWS")?List.of("NEWS"):
                (scene.startsWith("POST")||scene.equals("COMMUNITY_FEED"))?List.of("POST"):List.of("NEWS","POST");
        if (selected==null||selected.isEmpty()) selected=List.of("PLAYER","TEAM","MATCH","NEWS","POST");
        String q=text((String)request.get("q")).toLowerCase(Locale.ROOT);
        List<Map<String,Object>> result = new ArrayList<>();
        for (String kind:selected) {
            String table=TABLES.get(kind);
            if(table==null) throw DiscoveryTypes.bad("未知内容类型");
            String fields=switch(kind) {
                case "PLAYER" -> "id,name,name_en,team_id";
                case "TEAM" -> "id,name,name_en,abbreviation";
                case "MATCH" -> "id,home_team,away_team,home_team_en,away_team_en,season,match_date";
                case "NEWS" -> "id,title,title_en,home_team,away_team,published_at,create_time";
                default -> "id,title,tags,create_time";
            };
            var rows=jdbc.queryForList("SELECT "+fields+" FROM "+table+" ORDER BY id DESC LIMIT 500");
            for(var row:rows) {
                String key=kind+":"+row.get("id");
                if(key.equals(request.get("itemKey"))) continue;
                if(!q.isBlank()&&row.values().stream().noneMatch(value->value!=null&&value.toString().toLowerCase(Locale.ROOT).contains(q))) continue;
                if(request.get("season")!=null&&!request.get("season").equals(row.get("season"))) continue;
                if(request.get("team")!=null) {
                    long teamId=Key.parse((String)request.get("team")).id();
                    if(kind.equals("TEAM")&&((Number)row.get("id")).longValue()!=teamId) continue;
                    if(kind.equals("PLAYER")&&((Number)row.get("team_id")).longValue()!=teamId) continue;
                    if(!kind.equals("TEAM")&&!kind.equals("PLAYER")) continue;
                }
                Object rawDate=row.getOrDefault("published_at",row.getOrDefault("match_date",row.get("create_time")));
                if(rawDate==null) rawDate=row.get("create_time");
                long date=rawDate instanceof java.sql.Timestamp t?t.toLocalDateTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli():
                        rawDate instanceof java.sql.Date d?d.toLocalDate().atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli():
                        rawDate instanceof LocalDateTime t?t.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli():
                        rawDate instanceof LocalDate d?d.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli():0;
                if(request.get("dateFrom") instanceof Number start&&date<start.longValue()) continue;
                if(request.get("dateTo") instanceof Number end&&date>=end.longValue()) continue;
                result.add(Map.of("itemKey",key,"reason",scene.equals("SEARCH")?"关键词匹配":"近期内容"));
            }
        }
        if (scene.equals("HOME")) {
            var newsRows=result.stream().filter(row->row.get("itemKey").toString().startsWith("NEWS:")).iterator();
            var postRows=result.stream().filter(row->row.get("itemKey").toString().startsWith("POST:")).iterator();
            List<Map<String,Object>> mixed=new ArrayList<>();
            while (mixed.size()<100 && (newsRows.hasNext() || postRows.hasNext())) {
                if (newsRows.hasNext()) mixed.add(newsRows.next());
                if (mixed.size()<100 && postRows.hasNext()) mixed.add(postRows.next());
            }
            return mixed;
        }
        return result.subList(0,Math.min(100,result.size()));
    }
}

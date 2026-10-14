package com.nbamanager.discovery;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Short-lived retrieval IDs only. Hydration, preferences and attribution stay outside this cache. */
final class DiscoverySuggestionCache {
    private static final List<String> FILTERS=List.of("q","types","team","season","dateFrom","dateTo","limit","mode");
    private static final int CAPACITY=256;
    private final LinkedHashMap<Map<String,Object>,Entry> entries=new LinkedHashMap<>(16,.75f,true);
    private final LongSupplier clock;
    private record Lookup(Map<String,Object> body,boolean reusable) {}
    private static final class Entry {
        final CompletableFuture<Lookup> result=new CompletableFuture<>();
        long expires=Long.MAX_VALUE;
    }
    DiscoverySuggestionCache() { this(System::nanoTime); }
    DiscoverySuggestionCache(LongSupplier clock) { this.clock=clock; }

    Map<String,Object> get(Map<String,Object> request,int timeout,Supplier<Map<String,Object>> loader) {
        Map<String,Object> key=new HashMap<>();
        for(String field:FILTERS) {
            Object value=request.get(field);
            if(value!=null) key.put(field,value instanceof List<?> list?List.copyOf(list):value);
        }
        Entry entry; boolean owner=false;
        synchronized(entries) {
            entry=entries.get(key);
            if(entry==null||entry.expires<=clock.getAsLong()) {
                entry=new Entry(); entries.put(key,entry); owner=true;
                while(entries.size()>CAPACITY) entries.remove(entries.keySet().iterator().next());
            }
        }
        if(owner) {
            try {
                Map<String,Object> result=loader.get();
                boolean reusable=result!=null && result.get("items") instanceof List<?> && result.get("nextCursor")==null
                        && Boolean.FALSE.equals(result.get("degraded")) && "bm25-v1".equals(result.get("strategyVersion"));
                if(reusable) {
                    Map<String,Object> snapshot=new HashMap<>(result);
                    snapshot.put("items",((List<?>)result.get("items")).stream().map(row->
                            row instanceof Map<?,?> map?Collections.unmodifiableMap(new HashMap<>(map)):row).toList());
                    result=Collections.unmodifiableMap(snapshot);
                }
                synchronized(entries) {
                    if(reusable) entry.expires=clock.getAsLong()+TimeUnit.SECONDS.toNanos(1);
                    else entries.remove(key,entry);
                }
                entry.result.complete(new Lookup(result,reusable));
            } catch(RuntimeException error) {
                synchronized(entries) { entries.remove(key,entry); }
                entry.result.completeExceptionally(error);
            }
        }
        try {
            Lookup value=entry.result.get(timeout,TimeUnit.MILLISECONDS);
            // A future may have coalesced before a downstream cursor/degradation was known.
            return owner||value.reusable()?value.body():loader.get();
        }
        catch(InterruptedException error) { Thread.currentThread().interrupt(); return null; }
        catch(TimeoutException error) { return null; }
        catch(ExecutionException error) {
            if(error.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException(error.getCause());
        }
    }
}

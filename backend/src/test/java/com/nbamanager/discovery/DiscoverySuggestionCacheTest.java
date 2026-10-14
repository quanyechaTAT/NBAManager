package com.nbamanager.discovery;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DiscoverySuggestionCacheTest {
    Map<String,Object> result(String key) {
        return Map.of("items",List.of(Map.of("itemKey",key)),"strategyVersion","bm25-v1","degraded",false);
    }
    @Test void ttlAndCapacityBoundStalenessAndMemory() {
        AtomicLong clock=new AtomicLong(); var cache=new DiscoverySuggestionCache(clock::get);
        AtomicInteger loads=new AtomicInteger();
        var loader=(java.util.function.Supplier<Map<String,Object>>)()->result("NEWS:"+loads.incrementAndGet());
        var first=cache.get(Map.of("q","first"),800,loader);
        assertThat(cache.get(Map.of("q","first","userId",2),800,loader)).isSameAs(first);
        assertThatThrownBy(()->first.put("items",List.of())).isInstanceOf(UnsupportedOperationException.class);
        clock.set(TimeUnit.SECONDS.toNanos(1));
        assertThat(cache.get(Map.of("q","first"),800,loader)).isNotSameAs(first);
        assertThat(loads.get()).isEqualTo(2);
        for(int i=0;i<256;i++) cache.get(Map.of("q","other"+i),800,loader);
        cache.get(Map.of("q","first"),800,loader);
        assertThat(loads.get()).isEqualTo(259);
    }
    @Test void failedLookupsAndFilterChangesNeverReusePreviousResults() {
        var cache=new DiscoverySuggestionCache(); AtomicInteger loads=new AtomicInteger();
        assertThat(cache.get(Map.of("q","q"),800,()->null)).isNull();
        var loader=(java.util.function.Supplier<Map<String,Object>>)()->result("NEWS:"+loads.incrementAndGet());
        cache.get(Map.of("q","q","team","TEAM:1"),800,loader);
        cache.get(Map.of("q","q","team","TEAM:2"),800,loader);
        cache.get(Map.of("q","q","types",List.of("POST")),800,loader);
        assertThat(loads.get()).isEqualTo(3);
    }
    @Test void unexpectedPrivateCursorIsNotSharedWithPendingFollowers() throws Exception {
        var cache=new DiscoverySuggestionCache(); var clients=Executors.newFixedThreadPool(2);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try {
            var first=clients.submit(()->cache.get(Map.of("q","q","userId",1),800,()->{
                entered.countDown();
                try { release.await(1,TimeUnit.SECONDS); }
                catch(InterruptedException error) { throw new IllegalStateException(error); }
                return Map.of("items",List.of(),"nextCursor","user1:8");
            }));
            assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();
            var second=clients.submit(()->cache.get(Map.of("q","q","userId",2),800,
                    ()->Map.of("items",List.of(),"nextCursor","user2:8")));
            release.countDown();
            assertThat(first.get(2,TimeUnit.SECONDS)).containsEntry("nextCursor","user1:8");
            assertThat(second.get(2,TimeUnit.SECONDS)).containsEntry("nextCursor","user2:8");
        } finally { release.countDown(); clients.shutdownNow(); }
    }
}

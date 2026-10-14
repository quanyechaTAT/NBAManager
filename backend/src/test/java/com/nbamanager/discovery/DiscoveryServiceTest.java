package com.nbamanager.discovery;

import com.nbamanager.discovery.DiscoveryTypes.*;
import com.nbamanager.exception.ApiException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiscoveryServiceTest {
    DiscoveryCatalog catalog=mock(DiscoveryCatalog.class);
    DiscoveryStore store=mock(DiscoveryStore.class);
    DiscoveryGateway gateway=mock(DiscoveryGateway.class);
    DiscoveryService service=new DiscoveryService(catalog,store,gateway,mock(PlatformTransactionManager.class));

    @BeforeEach void setup() {
        when(store.preferences(anyLong())).thenReturn(new Preferences(true,0,null));
        when(store.followed(anyLong())).thenReturn(List.of());
        when(store.blocked(anyLong())).thenReturn(List.of());
        when(store.blockedAmong(anyLong(),any())).thenReturn(Set.of());
        when(store.served(anyLong(),anyString(),anyString(),any(),anyMap(),anyList())).thenReturn("request");
    }
    Item item(long id) { return new Item("NEWS:"+id,"NEWS",id,"业务库标题","摘要",null,null,null,new Target("/news",Map.of("newsId",""+id))); }

    @Test void deletedBlockedAndDuplicateResultsCannotBeServedOrAttributed() {
        when(gateway.fetch(anyString(),anyMap())).thenReturn(Map.of("items",List.of(
                Map.of("itemKey","NEWS:1","features",Map.of("privateFeature",1)),
                Map.of("itemKey","NEWS:2"),Map.of("itemKey","NEWS:3"),Map.of("itemKey","NEWS:1")),"strategyVersion","test"));
        when(catalog.hydrate(any())).thenReturn(Map.of("NEWS:1",item(1),"NEWS:2",item(2)));
        when(store.blockedAmong(eq(1L),any())).thenReturn(Set.of("NEWS:2"));
        Response response=service.fetch(1,"recommendations",Map.of("scene","NEWS_FEED"));
        assertThat(response.items()).containsExactly(item(1));
        verify(store).served(eq(1L),eq("NEWS_FEED"),eq("test"),isNull(),anyMap(),argThat(rows->rows.size()==1));
    }
    @Test void searchSkipsUnusedRecommendationContextButRechecksBlockedItemsAndPreferences() {
        when(gateway.fetch(anyString(),anyMap())).thenReturn(Map.of("items",List.of(
                Map.of("itemKey","NEWS:1"),Map.of("itemKey","NEWS:2")),"strategyVersion","bm25-v1"));
        when(catalog.hydrate(any())).thenReturn(Map.of("NEWS:1",item(1),"NEWS:2",item(2)));
        when(store.blockedAmong(eq(1L),any())).thenReturn(Set.of("NEWS:2"));
        for(String operation:List.of("search","suggest")) {
            assertThat(service.fetch(1,operation,Map.of("q","test")).items()).containsExactly(item(1));
        }
        verify(store,never()).followed(anyLong());
        verify(store,never()).blocked(anyLong());
        verify(store,times(2)).blockedAmong(eq(1L),eq(List.of("NEWS:1","NEWS:2")));
        verify(store,times(4)).preferences(1L);
        verify(store,times(2)).served(eq(1L),eq("SEARCH"),eq("bm25-v1"),isNull(),anyMap(),argThat(rows->rows.size()==1));
    }
    @Test void fallbackCursorIsBoundToUserQueryAndPreferences() {
        when(catalog.fallback(anyMap())).thenReturn(List.of(Map.of("itemKey","NEWS:1"),Map.of("itemKey","NEWS:2")));
        when(catalog.hydrate(any())).thenReturn(Map.of("NEWS:1",item(1),"NEWS:2",item(2)));
        Response first=service.fetch(1,"search",Map.of("q","test","limit","1"));
        assertThat(first.degraded()).isTrue(); assertThat(first.nextCursor()).startsWith("db:");
        var parameters=Map.of("q","test","limit","1","cursor",first.nextCursor());
        assertThat(service.fetch(1,"search",parameters).items()).extracting(Item::id).containsExactly(2L);
        assertThatThrownBy(()->service.fetch(2,"search",parameters)).isInstanceOf(ApiException.class);
        when(store.preferences(1)).thenReturn(new Preferences(false,1,null));
        assertThatThrownBy(()->service.fetch(1,"search",parameters)).isInstanceOf(ApiException.class);
    }
    @Test void validatesFiltersBeforeAnyDownstreamCall() {
        for(var params:List.of(Map.of("types","USER"),Map.of("team","PLAYER:1"),Map.of("limit","999"),
                Map.of("dateFrom","2026-10-20","dateTo","2026-10-10"))) {
            assertThatThrownBy(()->service.fetch(1,"search",params)).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(()->service.fetch(1,"recommendations",Map.of("scene","NEWS_DETAIL","itemKey","POST:1")))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(gateway);
    }
    @Test void normalizesInclusiveLocalDateRangeAndDistinctTypes() {
        var request=DiscoveryService.normalize(1,"search",Map.of("types","NEWS,NEWS,POST","dateFrom","2026-10-14","dateTo","2026-10-14"));
        assertThat(request.get("types")).isEqualTo(List.of("NEWS","POST"));
        assertThat((long)request.get("dateTo")-(long)request.get("dateFrom")).isEqualTo(86400000L);
    }
}

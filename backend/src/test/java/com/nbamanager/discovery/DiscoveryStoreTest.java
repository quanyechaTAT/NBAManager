package com.nbamanager.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbamanager.discovery.DiscoveryTypes.Event;
import com.nbamanager.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class DiscoveryStoreTest {
    JdbcTemplate jdbc;
    DiscoveryStore store;
    MutableClock clock;
    TransactionTemplate tx;
    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source); tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        String migration = new ClassPathResource("db/migration/V20261014_01__discovery_data_foundation.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        var tables = Pattern.compile("(?s)CREATE TABLE IF NOT EXISTS discovery_.*?;").matcher(migration);
        while (tables.find()) jdbc.execute(tables.group().replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", ""));
        clock = new MutableClock(); store = new DiscoveryStore(jdbc, new ObjectMapper(), clock);
    }

    String serve() {
        return store.served(1, "NEWS_FEED", "baseline-v1", null, Map.of("personalized", true,"preferenceRevision",store.preferences(1).revision()),
                List.of(Map.of("itemKey", "NEWS:1", "features", Map.of("freshness", .5))));
    }
    Event event(String request, String id, String kind, int dwell) {
        return new Event(id, request, "NEWS:1", kind, 0, dwell, clock.instant());
    }

    @Test void anotherUserCannotAttributeEventsToARequest() {
        String request = serve();
        assertThatThrownBy(() -> store.events(2, List.of(event(request, "x", "IMPRESSION", 0))))
                .isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM discovery_events", Integer.class)).isZero();
    }
    @Test void duplicateExposureIsIdempotentEvenWithANewEventId() {
        String request = serve();
        assertThat(store.events(1, List.of(event(request, "a", "IMPRESSION", 0))).accepted()).isEqualTo(1);
        assertThat(store.events(1, List.of(event(request, "a", "IMPRESSION", 0))).duplicates()).isEqualTo(1);
        assertThat(store.events(1, List.of(event(request, "b", "IMPRESSION", 0))).duplicates()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM discovery_events", Integer.class)).isEqualTo(1);
    }
    @Test void forgedFavoritesAndUnreturnedItemsRejectWholeBatch() {
        String request = serve();
        assertThatThrownBy(() -> tx.execute(status -> store.events(1, List.of(
                event(request, "a", "IMPRESSION", 0), event(request, "b", "FAVORITE", 0)))))
                .isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM discovery_events", Integer.class)).isZero();
        Event wrong = new Event("c", request, "NEWS:2", "CLICK", 0, 0, clock.instant());
        assertThatThrownBy(() -> store.events(1, List.of(wrong))).isInstanceOf(ApiException.class);
    }
    @Test void readRequiresAClickAndCannotExceedElapsedTime() {
        String request = serve();
        assertThatThrownBy(() -> store.events(1, List.of(event(request, "a", "READ", 30_000))))
                .isInstanceOf(ApiException.class);
        store.events(1, List.of(event(request, "b", "CLICK", 0)));
        clock.time = clock.time.plusSeconds(20);
        store.events(1, List.of(event(request, "c", "READ", 30_000)));
        assertThat(jdbc.queryForObject("SELECT dwell_ms FROM discovery_events WHERE event_type='READ'", Integer.class))
                .isEqualTo(20_000);
    }
    @Test void clearRemovesProfilesAndDismissalsWithoutEnablingDisabledPersonalization() {
        store.preferences(1, false);
        store.feedback(1, "NEWS:1", true);
        jdbc.update("INSERT INTO discovery_profiles(user_id,profile_json,updated_at) VALUES(1,'{}',CURRENT_TIMESTAMP)");
        var before = store.preferences(1);
        var after = store.clearPreferences(1);
        assertThat(after.personalized()).isFalse();
        assertThat(after.revision()).isGreaterThan(before.revision());
        assertThat(after.clearedAt()).isNotNull();
        assertThat(store.blocked(1)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM discovery_profiles", Integer.class)).isZero();
    }
    @Test void expiredRequestsCannotBecomeTrainingLabels() {
        String request = serve(); clock.time = clock.time.plusSeconds(86401);
        assertThatThrownBy(() -> store.events(1, List.of(event(request, "a", "CLICK", 0))))
                .isInstanceOf(ApiException.class);
    }
    @Test void clearingPreferencesRejectsDelayedEventsFromOldPages() {
        String old = serve();
        clock.time = clock.time.plusSeconds(1);
        store.clearPreferences(1);
        assertThatThrownBy(() -> store.events(1, List.of(event(old, "late", "CLICK", 0))))
                .isInstanceOf(ApiException.class);
        clock.time = clock.time.plusSeconds(1);
        assertThat(store.events(1, List.of(event(serve(), "new", "CLICK", 0))).accepted()).isEqualTo(1);
    }
    @Test void aResponseFinishingAfterClearCannotRestoreThePreviousPreferenceVersion() {
        store.clearPreferences(1);
        clock.time = clock.time.plusSeconds(1);
        String late=store.served(1,"NEWS_FEED","baseline-v1",null,Map.of("personalized",true,"preferenceRevision",0),
                List.of(Map.of("itemKey","NEWS:1")));
        assertThatThrownBy(() -> store.events(1,List.of(event(late,"late-response","CLICK",0))))
                .isInstanceOf(ApiException.class);
    }
    static class MutableClock extends Clock {
        Instant time = Instant.parse("2026-10-14T04:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return time; }
    }
}

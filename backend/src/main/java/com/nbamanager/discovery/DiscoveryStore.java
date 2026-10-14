package com.nbamanager.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbamanager.discovery.DiscoveryTypes.*;
import com.nbamanager.exception.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable attribution and user controls; Kafka is deliberately outside business transactions. */
@Service
public class DiscoveryStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    @Autowired public DiscoveryStore(JdbcTemplate jdbc, ObjectMapper json) { this(jdbc, json, Clock.systemUTC()); }
    DiscoveryStore(JdbcTemplate jdbc, ObjectMapper json, Clock clock) { this.jdbc = jdbc; this.json = json; this.clock = clock; }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }

    public Preferences preferences(long userId) {
        return jdbc.query("SELECT personalized,revision,cleared_at FROM discovery_preferences WHERE user_id=?", (rs, row) ->
                new Preferences(rs.getBoolean(1), rs.getLong(2), rs.getTimestamp(3) == null ? null
                        : rs.getTimestamp(3).toLocalDateTime().atOffset(ZoneOffset.UTC).toString()), userId)
                .stream().findFirst().orElse(new Preferences(true, 0, null));
    }

    @Transactional public Preferences preferences(long userId, boolean enabled) {
        jdbc.update("INSERT INTO discovery_preferences(user_id,personalized,updated_at,revision) VALUES(?,?,?,1) "
                + "ON DUPLICATE KEY UPDATE personalized=VALUES(personalized),updated_at=VALUES(updated_at),revision=revision+1",
                userId, enabled, now());
        return preferences(userId);
    }

    @Transactional public Preferences clearPreferences(long userId) {
        var time = now();
        jdbc.update("INSERT INTO discovery_preferences(user_id,personalized,cleared_at,updated_at,revision) VALUES(?,TRUE,?,?,1) "
                + "ON DUPLICATE KEY UPDATE cleared_at=VALUES(cleared_at),updated_at=VALUES(updated_at),revision=revision+1",
                userId, time, time);
        jdbc.update("DELETE FROM discovery_profiles WHERE user_id=?", userId);
        jdbc.update("DELETE FROM discovery_feedback WHERE user_id=?", userId);
        return preferences(userId);
    }

    public List<String> followed(long userId) {
        return jdbc.query("SELECT target_type,target_id FROM user_favorites WHERE user_id=? ORDER BY create_time DESC LIMIT 500",
                (rs, row) -> rs.getString(1) + ":" + rs.getLong(2), userId);
    }

    public List<String> blocked(long userId) {
        return jdbc.queryForList("SELECT item_key FROM discovery_feedback WHERE user_id=? AND active=TRUE "
                + "ORDER BY created_at DESC LIMIT 1000", String.class, userId);
    }

    public Set<String> blockedAmong(long userId, Collection<String> keys) {
        if (keys.isEmpty()) return Set.of();
        return new HashSet<>(new NamedParameterJdbcTemplate(jdbc).queryForList(
                "SELECT item_key FROM discovery_feedback WHERE user_id=:user AND active=TRUE AND item_key IN (:keys)",
                Map.of("user", userId, "keys", keys), String.class));
    }

    @Transactional public void feedback(long userId, String itemKey, boolean active) {
        Key.parse(itemKey);
        jdbc.update("INSERT IGNORE INTO discovery_feedback(user_id,item_key,active,created_at) VALUES(?,?,FALSE,?)",userId,itemKey,now());
        Boolean current=jdbc.queryForObject("SELECT active FROM discovery_feedback WHERE user_id=? AND item_key=? FOR UPDATE",
                Boolean.class,userId,itemKey);
        if (Boolean.valueOf(active).equals(current)) return;
        jdbc.update("UPDATE discovery_feedback SET active=?,created_at=? WHERE user_id=? AND item_key=?",active,now(),userId,itemKey);
        recordAction(userId, itemKey, active ? "DISMISS" : "UNDISMISS");
    }

    @Transactional public String served(long userId, String scene, String strategy, String model,
            Map<String, Object> context, List<Map<String, Object>> items) {
        String requestId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO discovery_requests(request_id,user_id,scene,strategy_version,feature_version,model_version,"
                + "context_json,items_json,created_at) VALUES(?,?,?,?,?,?,?,?,?)", requestId, userId, scene, strategy,
                DiscoveryTypes.FEATURE_VERSION, model, encode(context), encode(items), now());
        return requestId;
    }

    @Transactional public EventReceipt events(long userId, List<Event> events) {
        // Serialize receipt against preference clearing, including users with no profile yet.
        jdbc.update("INSERT IGNORE INTO discovery_preferences(user_id,personalized,updated_at,revision) VALUES(?,TRUE,?,0)", userId, now());
        long revision = jdbc.queryForObject("SELECT revision FROM discovery_preferences WHERE user_id=? FOR UPDATE", Long.class, userId);
        int accepted = 0;
        Map<String, RequestSnapshot> requests = new HashMap<>();
        for (Event event : events) {
            Key.parse(event.itemKey());
            if (!Set.of("IMPRESSION", "CLICK", "READ").contains(event.eventType())) throw DiscoveryTypes.bad("客户端事件类型无效");
            var request = requests.computeIfAbsent(event.requestId(), id -> request(id, userId));
            var created = request.createdAt();
            if (request.preferenceRevision() != revision)
                throw new ApiException(HttpStatus.GONE, "推荐偏好已更新，请刷新页面后重试");
            var time = clock.instant();
            if (time.isAfter(created.plusSeconds(86400)) || event.occurredAt().isBefore(created)
                    || event.occurredAt().isAfter(time.plusSeconds(120))) throw DiscoveryTypes.bad("行为事件超出归因时间窗口");
            var items = decodeList(request.itemsJson());
            if (event.position() >= items.size() || !event.itemKey().equals(items.get(event.position()).get("itemKey")))
                throw DiscoveryTypes.bad("行为事件与返回内容不匹配");
            int dwell = 0;
            if (event.eventType().equals("READ")) {
                var clicks = jdbc.query("SELECT received_at FROM discovery_events WHERE user_id=? AND request_id=? "
                        + "AND item_key=? AND event_type='CLICK' ORDER BY received_at LIMIT 1",
                        (rs, row) -> rs.getTimestamp(1).toLocalDateTime().toInstant(ZoneOffset.UTC), userId, event.requestId(), event.itemKey());
                if (clicks.isEmpty()) throw DiscoveryTypes.bad("阅读事件缺少有效点击");
                dwell = (int) Math.min(event.dwellMs(), Math.max(0, Duration.between(clicks.get(0), time).toMillis()));
            }
            // Unique attribution tuple also rejects replay with a freshly generated eventId.
            accepted += jdbc.update("INSERT IGNORE INTO discovery_events(event_id,user_id,request_id,item_key,event_type,"
                    + "position,dwell_ms,occurred_at,received_at,client_dedupe_key) VALUES(?,?,?,?,?,?,?,?,?,?)", event.eventId(), userId,
                    event.requestId(), event.itemKey(), event.eventType(), event.position(), dwell,
                    LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC), now(),
                    event.requestId()+":"+event.itemKey()+":"+event.eventType());
        }
        return new EventReceipt(accepted, events.size() - accepted);
    }

    private record RequestSnapshot(Instant createdAt, String itemsJson, long preferenceRevision) {}
    private RequestSnapshot request(String requestId, long userId) {
        var rows = jdbc.query("SELECT created_at,items_json,context_json FROM discovery_requests WHERE request_id=? AND user_id=?",
                (rs,row) -> new RequestSnapshot(rs.getTimestamp(1).toLocalDateTime().toInstant(ZoneOffset.UTC),rs.getString(2),contextRevision(rs.getString(3))),
                requestId, userId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "请求记录不存在或不属于当前用户");
        return rows.get(0);
    }

    private long contextRevision(String context) {
        try { return json.readTree(context).path("preferenceRevision").asLong(0); }
        catch (Exception error) { throw new IllegalStateException("Invalid stored request context", error); }
    }

    /** Called inside the successful favorite/like transaction, never from untrusted telemetry. */
    @Transactional public void recordAction(long userId, String itemKey, String type) {
        Key.parse(itemKey);
        if (!Set.of("LIKE", "UNLIKE", "FAVORITE", "UNFAVORITE", "DISMISS", "UNDISMISS").contains(type))
            throw new IllegalArgumentException("Unsupported trusted action");
        var recentClicks = jdbc.queryForList("SELECT request_id FROM discovery_events WHERE user_id=? AND item_key=? "
                + "AND event_type='CLICK' AND received_at>=? ORDER BY received_at DESC LIMIT 1", String.class,
                userId, itemKey, now().minusHours(24));
        String requestId = recentClicks.isEmpty() ? null : recentClicks.get(0);
        jdbc.update("INSERT IGNORE INTO discovery_events(event_id,user_id,request_id,item_key,event_type,occurred_at,received_at) "
                + "VALUES(?,?,?,?,?,?,?)", UUID.randomUUID().toString(), userId, requestId, itemKey, type, now(), now());
    }

    public String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalArgumentException("Cannot serialize discovery data", error); }
    }
    public List<Map<String, Object>> decodeList(String value) {
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception error) { throw new IllegalStateException("Invalid stored discovery data", error); }
    }
}

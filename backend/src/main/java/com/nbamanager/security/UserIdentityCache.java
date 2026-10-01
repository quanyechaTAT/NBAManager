package com.nbamanager.security;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * 认证链路用户身份缓存（第 8 轮 P0-3）。
 *
 * 根因：JwtAuthenticationFilter 对每个带 Bearer 的请求执行 loadUserByUsername
 * （一次 DB 查询）——所有 authenticated 端点每请求 +1 查询，是 Hikari 默认
 * 10 连接在 500 并发下需要 bulkhead 大量 429 的推手之一。
 *
 * 语义与安全红线：
 * - 缓存的是 UserDetails（含 BCrypt hash）；过滤器对缓存的 hash 做 pwdm 校验，
 *   校验逻辑与未缓存时完全一致——不改变任何安全语义；
 * - 失效点：改密码 / 改用户名（AuthController 显式 evict，改密旧 token 立即失效的
 *   行为不变）、身份变更事件（UserIdentityEventListener，防御性）、其余靠 TTL
 *   自然过期（测试/运维删用户 ≤45s 内仍然放行——与任务书"自然过期"一致）；
 * - 容量上限 10k 条防内存增长，超限时先清过期再整表收缩。
 *
 * 依赖说明：不引入 Caffeine（离线构建环境），手写有界过期表即可满足
 * TTL + 上限 + 并发语义。
 */
@Component
public class UserIdentityCache {

    private static final long TTL_MS = 45_000;
    private static final int MAX_ENTRIES = 10_000;

    private record Entry(UserDetails details, long expiresAt) { }

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public UserIdentityCache() {
        this(System::currentTimeMillis);
    }

    UserIdentityCache(LongSupplier clock) {
        this.clock = clock;
    }

    public UserDetails get(String username) {
        if (username == null) {
            return null;
        }
        Entry entry = cache.get(username);
        if (entry == null) {
            return null;
        }
        if (clock.getAsLong() > entry.expiresAt()) {
            cache.remove(username, entry);
            return null;
        }
        return entry.details();
    }

    public void put(String username, UserDetails details) {
        if (username == null || details == null) {
            return;
        }
        if (cache.size() >= MAX_ENTRIES) {
            purge();
        }
        cache.put(username, new Entry(details, clock.getAsLong() + TTL_MS));
    }

    public void evict(String username) {
        if (username != null) {
            cache.remove(username);
        }
    }

    /** 超限时先清过期；仍超限则整表收缩（下次请求重新加载，正确性优先于命中率）。 */
    private void purge() {
        long now = clock.getAsLong();
        Iterator<Map.Entry<String, Entry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            if (now > it.next().getValue().expiresAt()) {
                it.remove();
            }
        }
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
    }
}

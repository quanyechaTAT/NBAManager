package com.nbamanager.security;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class UserIdentityCacheTest {

    @Test
    void returnsCachedIdentityUntilTtlThenMisses() {
        AtomicLong now = new AtomicLong(1_000L);
        UserIdentityCache cache = new UserIdentityCache(now::get);
        UserDetails details = User.withUsername("alice").password("{bcrypt}hash").roles("USER").build();

        cache.put("alice", details);
        assertSame(details, cache.get("alice"));

        now.set(46_001L);
        assertNull(cache.get("alice"));
    }

    @Test
    void explicitEvictRemovesIdentityImmediately() {
        UserIdentityCache cache = new UserIdentityCache(() -> 1_000L);
        UserDetails details = User.withUsername("alice").password("hash").roles("USER").build();

        cache.put("alice", details);
        cache.evict("alice");

        assertNull(cache.get("alice"));
    }

    @Test
    void ignoresNullInputs() {
        UserIdentityCache cache = new UserIdentityCache(() -> 1_000L);

        cache.put(null, null);
        cache.evict(null);

        assertNull(cache.get(null));
    }
}

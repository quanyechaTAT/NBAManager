package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.config.RedisConfig;
import com.nbamanager.web.dto.PlayerDto;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;

/**
 * F5 回归：球员列表短 TTL 缓存（压测报告 §十 10.3 建议 3）。
 *
 * 该列表在 50 并发下是最慢的读路径（无缓存，每次 findAll + count）。
 * 这里锁定四件事，避免日后被无意改坏，也避免重犯一个已实测踩到的坑：
 * 1. 列表确实带缓存；
 * 2. 缓存键覆盖全部影响结果的入参（否则不同筛选会互相串数据）；
 * 3. 球员增删改会连带失效列表缓存（否则改完看到旧数据）；
 * 4. <b>缓存值必须能被 Redis 序列化器往返</b>——直接缓存 {@code PageImpl}
 *    会在缓存命中时报 "Cannot construct instance of PageImpl"，即命中即 500。
 */
class PlayerListCacheTest {

    private static Method loadMethod() throws Exception {
        return PlayerListCache.class.getMethod("load", String.class, Long.class, String.class, Pageable.class);
    }

    @Test
    void listIsCacheable() throws Exception {
        Cacheable cacheable = loadMethod().getAnnotation(Cacheable.class);

        assertThat(cacheable).as("球员列表必须带缓存").isNotNull();
        assertThat(cacheable.value()).containsExactly("playerList");
    }

    @Test
    void cacheKeyCoversAllFilterParameters() throws Exception {
        String key = loadMethod().getAnnotation(Cacheable.class).key();

        // 缺任何一个入参都会让不同筛选条件共用同一缓存条目
        assertThat(key).contains("#keyword").contains("#teamId")
                .contains("#position").contains("#pageable");
    }

    @Test
    void writeOperationsEvictListCache() throws Exception {
        for (String name : new String[]{"create", "update", "delete"}) {
            Method m = java.util.Arrays.stream(PlayerService.class.getMethods())
                    .filter(mm -> mm.getName().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("找不到方法 " + name));
            CacheEvict evict = m.getAnnotation(CacheEvict.class);
            assertThat(evict).as("%s 必须有 @CacheEvict", name).isNotNull();
            assertThat(evict.value())
                    .as("%s 必须失效列表缓存，否则写后读到旧数据", name)
                    .contains("playerList");
        }
    }

    /**
     * 用应用真实的 Redis 序列化器做一次往返。
     *
     * 这是本任务中实测踩到的坑：缓存 Page 时写入成功、读取失败（PageImpl 无默认构造器），
     * 表现为"首次请求 200、之后全是 500"。缓存值类型必须能通过这个往返。
     */
    @Test
    void cachePayloadSurvivesRedisSerializerRoundTrip() {
        RedisConfig config = new RedisConfig(1800, 60, 60);
        var serializer = config.jacksonRedisSerializer();

        PlayerDto dto = new PlayerDto(1L, 100L, "勒布朗·詹姆斯", "LeBron James", "DONE",
                10L, "湖人", "SF", 25.0, 7.0, 8.0, 1.0, 60, 35.0,
                0.5, 0.4, 0.75, 0.5, 3.0, 28.0, 0.6, 0.3, "23", "2.06m", 113,
                "USA", 25.0, 10.0, 5.0, 6.0, 115.0, 110.0);

        PlayerListCache.PagePayload original =
                new PlayerListCache.PagePayload(List.of(dto), 1L);

        byte[] bytes = serializer.serialize(original);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored)
                .as("缓存值必须能被真实序列化器还原，否则缓存命中即 500")
                .isInstanceOf(PlayerListCache.PagePayload.class);
        PlayerListCache.PagePayload payload = (PlayerListCache.PagePayload) restored;
        assertThat(payload.totalElements()).isEqualTo(1L);
        assertThat(payload.content()).hasSize(1);
        assertThat(payload.content().get(0).name()).isEqualTo("勒布朗·詹姆斯");
    }
}

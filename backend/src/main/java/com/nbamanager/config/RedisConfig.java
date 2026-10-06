package com.nbamanager.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    private final int defaultTtlSeconds;
    private final int playerListTtlSeconds;
    private final int postListTtlSeconds;

    public RedisConfig(@org.springframework.beans.factory.annotation.Value("${app.cache.ttl-seconds:1800}") int defaultTtlSeconds,
                       @org.springframework.beans.factory.annotation.Value("${app.cache.player-list-ttl-seconds:60}") int playerListTtlSeconds,
                       @org.springframework.beans.factory.annotation.Value("${app.cache.post-list-ttl-seconds:60}") int postListTtlSeconds) {
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.playerListTtlSeconds = playerListTtlSeconds;
        this.postListTtlSeconds = postListTtlSeconds;
    }

    /**
     * 缓存故障 fail-open（安全报告 §十四 Y5）：Redis 硬故障时 @Cacheable 读路径
     * 不得把 500 抛给公开读面——get 失败视为 miss 回落 DB，put/evict/clear 失败
     * 吞掉（失效丢失由 TTL 60s 兜底）。全工程唯一 CachingConfigurer 在本类，
     * 与 cacheManager 同属缓存域，避免多 CachingConfigurer 的覆盖歧义。
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheFailOpenErrorHandler();
    }

    @Bean
    public GenericJackson2JsonRedisSerializer jacksonRedisSerializer() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType("com.nbamanager.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                // @Cacheable 方法若返回 Page（如资讯列表缓存），值类型是
                // org.springframework.data.domain.PageImpl，不放行会在写入缓存时序列化失败
                .allowIfSubType("org.springframework.data.domain.")
                .build();

        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING);
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new GenericJackson2JsonRedisSerializer(om);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory,
                                                        GenericJackson2JsonRedisSerializer jacksonRedisSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jacksonRedisSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jacksonRedisSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory,
                                      GenericJackson2JsonRedisSerializer jacksonRedisSerializer) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(defaultTtlSeconds))
                .computePrefixWith(name -> (
                        java.util.Set.of("matchRecords", "matchRecordsByTeam", "headToHead", "boxScore", "quarterScores").contains(name)
                                ? "match-status-v2::"
                                : java.util.Set.of("players", "playerList", "playerDetail", "playerCareer", "leagueAverages", "teams", "rankings", "dashboard", "shotChart").contains(name)
                                        ? "data-contract-v3::" : "") + name + "::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jacksonRedisSerializer))
                .disableCachingNullValues();

        // 球员列表是 50 并发下最慢的热点读（压测报告 §十 10.3 建议 3），
        // 用短 TTL 兼顾命中率与批量同步带来的陈旧读窗口
        RedisCacheConfiguration playerListConfig = defaultConfig
                .entryTtl(Duration.ofSeconds(playerListTtlSeconds));

        // 帖子列表同样按短 TTL：写路径（发帖/评论/点赞/收藏/改身份）都已显式失效，
        // TTL 只作为兜底——例如运维直接改库、或将来新增写路径漏了失效注解。
        RedisCacheConfiguration postListConfig = defaultConfig
                .entryTtl(Duration.ofSeconds(postListTtlSeconds));

        // 投篮热区 10min（第 8 轮 P1-5）：低频变化数据，外部拉取后有 DB 层缓存，
        // 应用层再缓存一层避免重复查库；管理端清理有主动 evict
        RedisCacheConfiguration shotChartConfig = defaultConfig
                .entryTtl(Duration.ofSeconds(600));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("playerList", playerListConfig)
                .withCacheConfiguration("posts", postListConfig)
                .withCacheConfiguration("shotChart", shotChartConfig)
                .build();
    }
}

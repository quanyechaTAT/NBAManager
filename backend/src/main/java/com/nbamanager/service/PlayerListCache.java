package com.nbamanager.service;

import com.nbamanager.web.dto.PlayerDto;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 球员列表短 TTL 缓存（压测报告 §十 10.3 建议 3）。
 *
 * 为什么单独一个 bean：{@code @Cacheable} 靠代理生效，PlayerService 内部自调用不会走缓存，
 * 必须把缓存入口放在独立 bean 上才能被织入。
 *
 * 为什么用 {@link ObjectProvider} 拿 PlayerService：PlayerService 需要本类来读缓存，
 * 本类需要 PlayerService 来查库，构成循环依赖（Spring Boot 2.6+ 默认禁止）。
 * ObjectProvider 把解析推迟到首次调用，从而断开这个环——而不是用 @Lazy 代理或
 * 把查询逻辑复制一份。
 *
 * 为什么缓存 {@link PagePayload} 而不是直接缓存 {@code Page}/{@code PageImpl}：
 * 本项目 Redis 用 {@code GenericJackson2JsonRedisSerializer} + 默认类型信息，
 * 写进去没问题，但读回来时 Jackson 需要构造 {@code PageImpl}，而它<b>没有默认构造器</b>
 * （实测报错 "Cannot construct instance of PageImpl (no Creators...)"）。
 * 那会造成"缓存命中即 500"，比不缓存更糟。因此这里只缓存纯数据，由调用方重建 PageImpl。
 *
 * TTL 见 RedisConfig 的 playerList 配置（短周期，默认 60s）：球员由 PlayerSyncWorker
 * 批量写入，短 TTL 把同步造成的陈旧读限制在一个窗口内。
 */
@Service
public class PlayerListCache {

    /** 缓存值：只含可被 Jackson 直接往返的纯数据。 */
    public record PagePayload(List<PlayerDto> content, long totalElements) {}

    private final ObjectProvider<PlayerService> playerService;

    public PlayerListCache(ObjectProvider<PlayerService> playerService) {
        this.playerService = playerService;
    }

    /**
     * 键覆盖全部影响结果的入参，避免不同筛选互相命中。
     * 深翻页（pageNumber >= 20）不缓存：命中率极低，避免 key 无界增长。
     */
    @Cacheable(value = "playerList",
            key = "#keyword + '|' + #teamId + '|' + #position + '|' + #pageable",
            condition = "#pageable.pageNumber < 20")
    public PagePayload load(String keyword, Long teamId, String position, Pageable pageable) {
        return playerService.getObject().queryPage(keyword, teamId, position, pageable);
    }
}

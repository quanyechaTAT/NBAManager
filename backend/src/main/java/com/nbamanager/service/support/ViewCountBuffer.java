package com.nbamanager.service.support;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 浏览量写合并缓冲（第 8 轮 P0-2）。
 *
 * 根因：帖子详情每读一次执行一次 viewCount 原子 UPDATE——单行热点写。
 * 实测同并发下无写的球员详情 3481 RPS vs 有写的帖子详情 199.7 RPS（17.4x）；
 * 第 5 轮 P3 的列表 p99 劣化（100 详情并发 ×3.13）即此单行锁争用。
 *
 * 机制：内存 {@link ConcurrentHashMap}<Long, AtomicLong> 累加，定时（默认 5s）
 * 把 ≥1 的计数批量交给落库函数（由 PostService 注册）。崩溃丢失最近一个窗口的
 * 浏览量（秒级、可接受——浏览量是非关键计数，取舍见 PERF_REPORT）。
 *
 * 详情返回值语义：接受"秒级陈旧"——详情读取 DB 值（不含缓冲），缓冲落库后
 * 下一次读可见。选择理由：读取路径保持零额外开销；缓冲值参与返回需要为每次读
 * 做跨线程汇总，复杂度不成比例（取舍写入 PERF_REPORT）。
 */
@Slf4j
@Component
public class ViewCountBuffer {

    private final ConcurrentHashMap<Long, AtomicLong> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService flusher =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "view-count-flusher");
                t.setDaemon(true);
                return t;
            });

    private final long flushIntervalMs;
    private volatile Consumer<List<Map.Entry<Long, Long>>> persister = batch -> { };

    public ViewCountBuffer(@Value("${app.viewcount.flush-millis:5000}") long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
        if (flushIntervalMs > 0) {
            flusher.scheduleWithFixedDelay(this::flush, flushIntervalMs, flushIntervalMs,
                    TimeUnit.MILLISECONDS);
        }
    }

    /** 由使用方（PostService）注册持久化函数；未注册时缓冲照常累加、落库为空操作。 */
    public void setPersister(Consumer<List<Map.Entry<Long, Long>>> persister) {
        this.persister = persister;
    }

    /** 详情读路径调用：内存累加，不做 IO。 */
    public void increment(Long postId) {
        pending.computeIfAbsent(postId, k -> new AtomicLong()).incrementAndGet();
    }

    /** 当前缓冲总量（测试/对账用）。 */
    public long pendingTotal() {
        long total = 0;
        for (AtomicLong v : pending.values()) {
            total += v.get();
        }
        return total;
    }

    /** 定时落库：把缓冲中 ≥1 的计数交给持久化函数并清空对应缓冲。 */
    public void flush() {
        if (pending.isEmpty()) {
            return;
        }
        List<Map.Entry<Long, Long>> batch = new java.util.ArrayList<>();
        for (Map.Entry<Long, AtomicLong> e : pending.entrySet()) {
            long drained = e.getValue().getAndSet(0);
            if (drained > 0) {
                batch.add(Map.entry(e.getKey(), drained));
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            persister.accept(batch);
            log.debug("浏览量缓冲落库: {} 个帖子", batch.size());
        } catch (Exception e) {
            // 落库失败把计数还回去（下个窗口重试），不丢
            for (Map.Entry<Long, Long> entry : batch) {
                pending.computeIfAbsent(entry.getKey(), k -> new AtomicLong()).addAndGet(entry.getValue());
            }
            log.warn("浏览量缓冲落库失败，已还回缓冲下窗重试: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        // 关闭钩子：尽力落盘一次（应用正常关闭时窗口内计数不丢）
        flush();
        flusher.shutdownNow();
    }
}

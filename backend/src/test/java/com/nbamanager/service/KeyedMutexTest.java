package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.service.support.KeyedMutex;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * {@link KeyedMutex} 的行为契约：同一 key 串行、不同 key 并行。
 *
 * 这是点赞/收藏切换消除 InnoDB 死锁的基础，因此单独锁住语义。
 */
class KeyedMutexTest {

    @Test
    void sameKeyExecutionsDoNotOverlap() throws Exception {
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        int workers = 8;

        Thread[] threads = new Thread[workers];
        for (int i = 0; i < workers; i++) {
            threads[i] = new Thread(() -> {
                try {
                    start.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                KeyedMutex.execute("post-like:1:1", () -> {
                    int now = concurrent.incrementAndGet();
                    maxConcurrent.accumulateAndGet(now, Math::max);
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    concurrent.decrementAndGet();
                });
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread t : threads) {
            t.join(5000);
        }

        assertThat(maxConcurrent.get()).isEqualTo(1);
    }

    @Test
    void differentKeysCanRunConcurrently() throws Exception {
        CountDownLatch bothEntered = new CountDownLatch(2);
        Thread a = new Thread(() -> KeyedMutex.execute("post-like:1:1", () -> {
            bothEntered.countDown();
            try {
                bothEntered.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        Thread b = new Thread(() -> KeyedMutex.execute("post-like:2:2", () -> {
            bothEntered.countDown();
            try {
                bothEntered.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        a.start();
        b.start();
        a.join(5000);
        b.join(5000);

        assertThat(bothEntered.getCount()).isZero();
    }

    @Test
    void exceptionStillReleasesLockSoNextCallerProceeds() {
        try {
            KeyedMutex.execute("post-like:9:9", () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // ignore
        }

        assertThat(KeyedMutex.execute("post-like:9:9", () -> "ok")).isEqualTo("ok");
    }
}

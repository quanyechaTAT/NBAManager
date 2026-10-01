package com.nbamanager.service.support;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 按业务键串行化"切换类"写操作（点赞/收藏）。
 *
 * 背景（压测 R4 点赞风暴实测）：
 * 点赞切换是 DELETE + INSERT 两步。同一个 (postId, userId) 上有 20 个并发请求时，
 * 各事务先 DELETE 拿到 next-key 锁、再 INSERT 申请插入意向锁，形成循环等待，
 * MySQL 直接以 "Deadlock found ... try restarting transaction" 回滚，
 * 表现为 84% 的请求失败（其中大批 500）。
 *
 * 这类"状态翻转"本身没有确定的并发语义——20 个并发 toggle 的正确结果取决于执行顺序，
 * 因此按 key 串行化既消除了死锁，也让结果可解释（等价于顺序执行 20 次）。
 *
 * 注意锁的释放时机：必须在事务提交/回滚之后。若在方法返回时就释放，
 * 下一个线程会在前一个事务尚未提交时进入，锁形同虚设、死锁依旧。
 * 所以这里注册 {@link TransactionSynchronization#afterCompletion(int)} 回调释放；
 * 没有事务时退回 finally 释放。
 */
public final class KeyedMutex {

    private static final int STRIPES = 64;
    private static final ReentrantLock[] LOCKS = new ReentrantLock[STRIPES];

    static {
        for (int i = 0; i < STRIPES; i++) {
            LOCKS[i] = new ReentrantLock();
        }
    }

    private KeyedMutex() {
    }

    /** 有返回值的加锁执行。 */
    public static <T> T execute(String key, LockedAction<T> action) {
        ReentrantLock lock = lockFor(key);
        lock.lock();
        boolean unlockRegistered = false;
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        lock.unlock();
                    }
                });
                unlockRegistered = true;
            }
            return action.run();
        } finally {
            if (!unlockRegistered) {
                lock.unlock();
            }
        }
    }

    /** 无返回值的加锁执行。 */
    public static void execute(String key, Runnable action) {
        execute(key, () -> {
            action.run();
            return null;
        });
    }

    private static ReentrantLock lockFor(String key) {
        int hash = key == null ? 0 : key.hashCode();
        return LOCKS[Math.floorMod(hash, STRIPES)];
    }

    @FunctionalInterface
    public interface LockedAction<T> {
        T run();
    }
}

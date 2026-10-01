package com.nbamanager.service.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 测试用事务管理器。
 *
 * {@code PostService/CommentService.toggleLike} 用
 * {@code new TransactionTemplate(transactionManager).execute(...)} 达到"每次重试开新事务"
 * 的目的，单元测试里没有 Spring 容器，所以用一个最小实现驱动它：
 *
 * - getTransaction 时开启事务同步（TransactionSynchronizationManager.initSynchronization），
 *   这样 KeyedMutex 会走"事务完成后释放锁"的生产路径，而不是 finally 兜底；
 * - commit 触发 afterCommit / afterCompletion(COMMITTED)；
 * - rollback 触发 afterCompletion(ROLLED_BACK)；
 * - 两者都清理同步状态，避免线程本地变量在测试间泄漏。
 */
public class FakeTransactionManager implements PlatformTransactionManager {

    /** 提交次数，用于断言重试行为。 */
    public int commitCount;
    /** 回滚次数。 */
    public int rollbackCount;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }
        return new SimpleTransactionStatus(true);
    }

    @Override
    public void commit(TransactionStatus status) {
        commitCount++;
        complete(status, true);
    }

    @Override
    public void rollback(TransactionStatus status) {
        rollbackCount++;
        complete(status, false);
    }

    private void complete(TransactionStatus status, boolean committed) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        // 先快照再清理：KeyedMutex 的解锁回调注册在 afterCompletion
        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(TransactionSynchronization::beforeCompletion);
        TransactionSynchronizationManager.clearSynchronization();
        if (committed) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        int completionStatus = committed
                ? TransactionSynchronization.STATUS_COMMITTED
                : TransactionSynchronization.STATUS_ROLLED_BACK;
        synchronizations.forEach(s -> s.afterCompletion(completionStatus));
    }
}
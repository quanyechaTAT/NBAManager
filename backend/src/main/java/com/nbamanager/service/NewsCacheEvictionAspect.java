package com.nbamanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 新闻列表缓存的统一失效网（第 8 轮 P1-4）。
 *
 * game_news 的写方分散在 4 个服务（GameNewsService CRUD、NbaLiveSyncService 同步、
 * NewsPersistenceService 批量导入、SmartDataService 更新），且部分写在循环/批量里。
 * 逐点加 @CacheEvict 容易漏（漏一处 = 60s 陈旧新闻），因此在**仓库层**用
 * execution 切面统一拦截 GameNewsRepository 的全部写方法
 * （save 系、delete 系、bump 系），一处声明覆盖所有现有与未来的调用方。
 *
 * 双重失效时机：
 * 1. @AfterReturning 立即失效——覆盖非事务写与当前事务内的读取；
 * 2. 事务存在时注册 afterCommit 再失效一次——封住"evict 后、commit 前，
 *    另一请求用旧数据回填缓存"的竞态窗口（两次失效都是幂等的整表清空）。
 *
 * 覆盖核对：GameNewsRepository 的变更方法 = save/saveAll（save*）、
 * deleteById/deleteAll/deleteAllInBatch/delete（delete*）、bumpFavoriteCount（bump*）。
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class NewsCacheEvictionAspect {

    private final NewsListCache newsListCache;

    @AfterReturning("execution(* com.nbamanager.repository.GameNewsRepository+.save*(..))"
            + " || execution(* com.nbamanager.repository.GameNewsRepository+.delete*(..))"
            + " || execution(* com.nbamanager.repository.GameNewsRepository+.bump*(..))")
    public void evictNewsList() {
        runEvict();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runEvict();
                }
            });
        }
    }

    private void runEvict() {
        newsListCache.evictAll();
    }
}

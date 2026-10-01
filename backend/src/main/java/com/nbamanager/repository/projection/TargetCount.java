package com.nbamanager.repository.projection;

/**
 * 按目标聚合的计数投影（用于把"每行一条 COUNT"的 N+1 收敛成一条 GROUP BY）。
 *
 * 用接口投影而不是 Object[]：JPQL 别名与 getter 名一一对应，列顺序变化不会静默错位。
 */
public interface TargetCount {

    Long getTargetId();

    Long getCnt();
}

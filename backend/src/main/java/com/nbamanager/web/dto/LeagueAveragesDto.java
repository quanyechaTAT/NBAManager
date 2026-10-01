package com.nbamanager.web.dto;

import java.math.BigDecimal;

/**
 * 联盟平均与极值统计（用于球员详情页的“联盟相对水平”对比条）。
 * 仅统计该赛季出场数达到门槛的球员，避免边缘样本拉偏均值。
 */
public record LeagueAveragesDto(
        BigDecimal avgPoints,
        BigDecimal avgRebounds,
        BigDecimal avgAssists,
        BigDecimal avgSteals,
        BigDecimal avgBlocks,
        BigDecimal avgFieldGoalPct,
        BigDecimal avgThreePointPct,
        BigDecimal maxPoints,
        BigDecimal maxRebounds,
        BigDecimal maxAssists,
        BigDecimal maxSteals,
        BigDecimal maxBlocks,
        BigDecimal maxFieldGoalPct,
        BigDecimal maxThreePointPct,
        long playerCount) {

    /** 空赛季兜底：全部置零，前端据此隐藏对比条 */
    public static LeagueAveragesDto empty() {
        BigDecimal zero = BigDecimal.ZERO;
        return new LeagueAveragesDto(zero, zero, zero, zero, zero, zero, zero,
                zero, zero, zero, zero, zero, zero, zero, 0L);
    }
}

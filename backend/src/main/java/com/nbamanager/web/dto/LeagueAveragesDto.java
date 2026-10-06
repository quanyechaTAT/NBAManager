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
        long playerCount,
        DataMeta dataMeta) {

    /** An empty sample has missing statistics, not measured zeroes. */
    public static LeagueAveragesDto empty() {
        return new LeagueAveragesDto(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, 0L, DataMeta.unverified());
    }
}

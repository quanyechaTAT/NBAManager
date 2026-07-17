package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class SyncSchedulePolicyTest {

    @Test
    void cronPolicyDetectsMissedDailyExecution() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        SyncSchedulePolicy policy = SyncSchedulePolicy.cron("0 0 3 * * ?", zone);
        ZonedDateTime lastSuccess = ZonedDateTime.of(2026, 7, 16, 3, 10, 0, 0, zone);
        assertThat(policy.isDue(lastSuccess, ZonedDateTime.of(2026, 7, 17, 8, 0, 0, 0, zone))).isTrue();
    }

    @Test
    void cronPolicyDoesNotRunBeforeNextSchedule() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        SyncSchedulePolicy policy = SyncSchedulePolicy.cron("0 0 3 * * ?", zone);
        ZonedDateTime lastSuccess = ZonedDateTime.of(2026, 7, 17, 3, 10, 0, 0, zone);
        assertThat(policy.isDue(lastSuccess, ZonedDateTime.of(2026, 7, 17, 2, 0, 0, 0, zone))).isFalse();
    }

    @Test
    void fixedPolicyDoesNotRunBeforeInterval() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        SyncSchedulePolicy policy = SyncSchedulePolicy.fixed(Duration.ofMinutes(5));
        ZonedDateTime lastSuccess = ZonedDateTime.of(2026, 7, 17, 8, 0, 0, 0, zone);
        assertThat(policy.isDue(lastSuccess, lastSuccess.plusMinutes(4))).isFalse();
    }

    @Test
    void fixedPolicyRunsAfterInterval() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        SyncSchedulePolicy policy = SyncSchedulePolicy.fixed(Duration.ofMinutes(5));
        ZonedDateTime lastSuccess = ZonedDateTime.of(2026, 7, 17, 8, 0, 0, 0, zone);
        assertThat(policy.isDue(lastSuccess, lastSuccess.plusMinutes(5))).isTrue();
    }

    @Test
    void fixedPolicyRunsWhenNeverExecuted() {
        SyncSchedulePolicy policy = SyncSchedulePolicy.fixed(Duration.ofMinutes(5));
        ZonedDateTime epoch = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneId.of("Asia/Shanghai"));
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 17, 8, 0, 0, 0, ZoneId.of("Asia/Shanghai"));
        assertThat(policy.isDue(epoch, now)).isTrue();
    }
}

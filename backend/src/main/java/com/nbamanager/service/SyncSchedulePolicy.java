package com.nbamanager.service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;

@FunctionalInterface
public interface SyncSchedulePolicy {

    boolean isDue(ZonedDateTime lastSuccess, ZonedDateTime now);

    static SyncSchedulePolicy cron(String expression, ZoneId zone) {
        CronExpression cron = CronExpression.parse(expression);
        return (lastSuccess, now) -> {
            ZonedDateTime next = cron.next(lastSuccess.withZoneSameInstant(zone));
            return next != null && !next.isAfter(now.withZoneSameInstant(zone));
        };
    }

    static SyncSchedulePolicy fixed(Duration interval) {
        return (lastSuccess, now) -> !lastSuccess.plus(interval).isAfter(now);
    }
}

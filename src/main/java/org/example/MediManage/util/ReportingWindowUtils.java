package org.example.MediManage.util;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

public final class ReportingWindowUtils {

    private ReportingWindowUtils() {
    }

    public static WeeklyWindow currentMondayToSunday(ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        return currentMondayToSunday(Clock.system(zoneId));
    }

    static WeeklyWindow currentMondayToSunday(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        return mondayToSundayWindow(LocalDate.now(clock));
    }

    public static WeeklyWindow mondayToSundayWindow(LocalDate referenceDate) {
        Objects.requireNonNull(referenceDate, "referenceDate must not be null");
        LocalDate start = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new WeeklyWindow(start, start.plusDays(6));
    }

    public record WeeklyWindow(LocalDate startDate, LocalDate endDate) {
        public WeeklyWindow {
            Objects.requireNonNull(startDate, "startDate must not be null");
            Objects.requireNonNull(endDate, "endDate must not be null");
            if (startDate.isAfter(endDate)) {
                throw new IllegalArgumentException("startDate must be on or before endDate");
            }
        }
    }
}

package org.example.MediManage.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportingWindowUtilsTest {

    @Test
    void mondayToSundayWindowUsesMondayAsStartForAnyReferenceDate() {
        ReportingWindowUtils.WeeklyWindow mondayWindow = ReportingWindowUtils.mondayToSundayWindow(
                LocalDate.of(2026, 2, 23));
        assertEquals(LocalDate.of(2026, 2, 23), mondayWindow.startDate());
        assertEquals(LocalDate.of(2026, 3, 1), mondayWindow.endDate());

        ReportingWindowUtils.WeeklyWindow sundayWindow = ReportingWindowUtils.mondayToSundayWindow(
                LocalDate.of(2026, 3, 1));
        assertEquals(LocalDate.of(2026, 2, 23), sundayWindow.startDate());
        assertEquals(LocalDate.of(2026, 3, 1), sundayWindow.endDate());
    }

    @Test
    void currentMondayToSundayUsesProvidedClockTimezoneDate() {
        Instant anchor = Instant.parse("2026-02-23T01:30:00Z");
        ReportingWindowUtils.WeeklyWindow losAngelesWindow = ReportingWindowUtils.currentMondayToSunday(
                Clock.fixed(anchor, ZoneId.of("America/Los_Angeles")));
        assertEquals(LocalDate.of(2026, 2, 16), losAngelesWindow.startDate());
        assertEquals(LocalDate.of(2026, 2, 22), losAngelesWindow.endDate());

        ReportingWindowUtils.WeeklyWindow kolkataWindow = ReportingWindowUtils.currentMondayToSunday(
                Clock.fixed(anchor, ZoneId.of("Asia/Kolkata")));
        assertEquals(LocalDate.of(2026, 2, 23), kolkataWindow.startDate());
        assertEquals(LocalDate.of(2026, 3, 1), kolkataWindow.endDate());
    }
}

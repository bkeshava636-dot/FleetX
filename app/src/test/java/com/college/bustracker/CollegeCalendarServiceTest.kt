package com.college.bustracker

import com.college.bustracker.data.model.CalendarException
import com.college.bustracker.util.AutomaticRideManager
import com.college.bustracker.util.AutomaticRideState
import com.college.bustracker.util.CollegeCalendarService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CollegeCalendarServiceTest {

    @Before
    fun setup() {
        // Reset cached exceptions before each test
        CollegeCalendarService.updateCachedExceptions(emptyList())
    }

    @Test
    fun testNormalWeekdays_areWorkingDaysByDefault() {
        // September 2026
        val monday = LocalDate.of(2026, 9, 7)
        val tuesday = LocalDate.of(2026, 9, 8)
        val wednesday = LocalDate.of(2026, 9, 9)
        val thursday = LocalDate.of(2026, 9, 10)
        val friday = LocalDate.of(2026, 9, 11)

        assertTrue("Monday must be a working day", CollegeCalendarService.isWorkingDay(monday))
        assertFalse("Monday must not be a holiday", CollegeCalendarService.isHoliday(monday))
        assertNull(CollegeCalendarService.getHolidayName(monday))

        assertTrue("Tuesday must be a working day", CollegeCalendarService.isWorkingDay(tuesday))
        assertTrue("Wednesday must be a working day", CollegeCalendarService.isWorkingDay(wednesday))
        assertTrue("Thursday must be a working day", CollegeCalendarService.isWorkingDay(thursday))
        assertTrue("Friday must be a working day", CollegeCalendarService.isWorkingDay(friday))
    }

    @Test
    fun testSundays_areAlwaysHolidaysByDefault() {
        val sun1 = LocalDate.of(2026, 9, 6)
        val sun2 = LocalDate.of(2026, 9, 13)
        val sun3 = LocalDate.of(2026, 9, 20)
        val sun4 = LocalDate.of(2026, 9, 27)

        val sundays = listOf(sun1, sun2, sun3, sun4)
        for (sun in sundays) {
            assertFalse("Sunday $sun must not be a working day", CollegeCalendarService.isWorkingDay(sun))
            assertTrue("Sunday $sun must be a holiday", CollegeCalendarService.isHoliday(sun))
            assertEquals("Sunday", CollegeCalendarService.getHolidayName(sun))
        }
    }

    @Test
    fun testRecurringSaturdayRules_exactRulesEnforced() {
        // September 2026 Saturdays:
        // Day 5: 1st Saturday -> Holiday
        // Day 12: 2nd Saturday -> Working Day
        // Day 19: 3rd Saturday -> Holiday
        // Day 26: 4th Saturday -> Working Day
        val firstSat = LocalDate.of(2026, 9, 5)
        val secondSat = LocalDate.of(2026, 9, 12)
        val thirdSat = LocalDate.of(2026, 9, 19)
        val fourthSat = LocalDate.of(2026, 9, 26)

        // 1st Saturday
        assertTrue("1st Saturday must be a holiday", CollegeCalendarService.isHoliday(firstSat))
        assertFalse("1st Saturday must not be working", CollegeCalendarService.isWorkingDay(firstSat))
        assertEquals("1st Saturday", CollegeCalendarService.getHolidayName(firstSat))

        // 2nd Saturday
        assertTrue("2nd Saturday must be a working day", CollegeCalendarService.isWorkingDay(secondSat))
        assertFalse("2nd Saturday must not be a holiday", CollegeCalendarService.isHoliday(secondSat))
        assertNull(CollegeCalendarService.getHolidayName(secondSat))

        // 3rd Saturday
        assertTrue("3rd Saturday must be a holiday", CollegeCalendarService.isHoliday(thirdSat))
        assertFalse("3rd Saturday must not be working", CollegeCalendarService.isWorkingDay(thirdSat))
        assertEquals("3rd Saturday", CollegeCalendarService.getHolidayName(thirdSat))

        // 4th Saturday
        assertTrue("4th Saturday must be a working day", CollegeCalendarService.isWorkingDay(fourthSat))
        assertFalse("4th Saturday must not be a holiday", CollegeCalendarService.isHoliday(fourthSat))
        assertNull(CollegeCalendarService.getHolidayName(fourthSat))
    }

    @Test
    fun testFifthSaturday_isWorkingDayByDefault() {
        // May 2026 has 5 Saturdays:
        // 2 (1st), 9 (2nd), 16 (3rd), 23 (4th), 30 (5th)
        val fifthSat = LocalDate.of(2026, 5, 30)

        assertTrue("5th Saturday must be a working day by default", CollegeCalendarService.isWorkingDay(fifthSat))
        assertFalse("5th Saturday must not be a holiday", CollegeCalendarService.isHoliday(fifthSat))
        assertNull(CollegeCalendarService.getHolidayName(fifthSat))
    }

    @Test
    fun testAdminFullDayHolidayOverride_overridesNormalWeekday() {
        val weekday = LocalDate.of(2026, 9, 15) // Tuesday
        // Normally working
        assertTrue(CollegeCalendarService.isWorkingDay(weekday))

        val override = CalendarException(
            date = "2026-09-15",
            type = CalendarException.TYPE_FULL_DAY_HOLIDAY,
            name = "Engineers Day"
        )
        val exceptions = mapOf(override.date to override)

        assertFalse("Override must make Tuesday a holiday", CollegeCalendarService.isWorkingDay(weekday, exceptions))
        assertTrue("Override must make Tuesday a holiday", CollegeCalendarService.isHoliday(weekday, exceptions))
        assertEquals("Engineers Day", CollegeCalendarService.getHolidayName(weekday, exceptions))

        val evaluation = CollegeCalendarService.evaluateDay(weekday, exceptions)
        assertTrue(evaluation.isOverride)
        assertEquals("Holiday — Engineers Day", evaluation.summary)
    }

    @Test
    fun testAdminWorkingDayOverride_onSunday() {
        val sunday = LocalDate.of(2026, 9, 6)
        // Normally Sunday is a holiday
        assertTrue(CollegeCalendarService.isHoliday(sunday))

        val override = CalendarException(
            date = "2026-09-06",
            type = CalendarException.TYPE_WORKING_DAY,
            name = "Special Sunday Exam"
        )
        val exceptions = mapOf(override.date to override)

        assertTrue("Override must make Sunday a working day", CollegeCalendarService.isWorkingDay(sunday, exceptions))
        assertFalse("Override must make Sunday not a holiday", CollegeCalendarService.isHoliday(sunday, exceptions))
        assertNull(CollegeCalendarService.getHolidayName(sunday, exceptions))

        val evaluation = CollegeCalendarService.evaluateDay(sunday, exceptions)
        assertTrue(evaluation.isOverride)
        assertEquals("Special Sunday Exam", evaluation.summary)
    }

    @Test
    fun testAdminWorkingDayOverride_onFirstAndThirdSaturday() {
        val firstSat = LocalDate.of(2026, 9, 5)
        val thirdSat = LocalDate.of(2026, 9, 19)

        val exceptions = mapOf(
            "2026-09-05" to CalendarException(date = "2026-09-05", type = CalendarException.TYPE_WORKING_DAY, name = "Working 1st Sat"),
            "2026-09-19" to CalendarException(date = "2026-09-19", type = CalendarException.TYPE_WORKING_DAY, name = "Working 3rd Sat")
        )

        assertTrue("Override on 1st Saturday makes it a working day", CollegeCalendarService.isWorkingDay(firstSat, exceptions))
        assertTrue("Override on 3rd Saturday makes it a working day", CollegeCalendarService.isWorkingDay(thirdSat, exceptions))
    }

    @Test
    fun testAdminOverrideTakesPrecedence_overDefaultRule() {
        val sunday = LocalDate.of(2026, 10, 4)
        val workingSun = CalendarException(date = "2026-10-04", type = CalendarException.TYPE_WORKING_DAY, name = "Compensatory Day")

        // Without override -> Holiday
        assertFalse(CollegeCalendarService.isWorkingDay(sunday, emptyMap()))
        // With override -> Working Day
        assertTrue(CollegeCalendarService.isWorkingDay(sunday, mapOf(workingSun.date to workingSun)))
    }

    @Test
    fun testMonthAndYearBoundaryCases() {
        // Leap year: Feb 29, 2024 was Thursday (working day)
        val leapDay = LocalDate.of(2024, 2, 29)
        assertTrue(CollegeCalendarService.isWorkingDay(leapDay))

        // Non-leap year: Feb 28, 2026 is Saturday (4th Saturday -> working day)
        val feb28 = LocalDate.of(2026, 2, 28)
        assertTrue(CollegeCalendarService.isWorkingDay(feb28))

        // Month boundary: March 1, 2026 is Sunday (Holiday)
        val mar1 = LocalDate.of(2026, 3, 1)
        assertTrue(CollegeCalendarService.isHoliday(mar1))
        assertEquals("Sunday", CollegeCalendarService.getHolidayName(mar1))

        // Year boundary: Dec 31, 2026 is Thursday (Working day)
        val dec31 = LocalDate.of(2026, 12, 31)
        assertTrue(CollegeCalendarService.isWorkingDay(dec31))

        // Jan 1, 2027 is Friday (Working day by default)
        val jan1 = LocalDate.of(2027, 1, 1)
        assertTrue(CollegeCalendarService.isWorkingDay(jan1))
    }

    @Test
    fun testStudentAutomaticContribution_isBlockedOutsideWorkingDay() {
        // In AutomaticRideManager, if isWithinWindow is false (which occurs when isWorkingDay is false),
        // state transitions to WAITING_FOR_WINDOW and shouldStopSharing is triggered if contributing.
        val decision = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.CONTRIBUTING,
            score = 90,
            now = 1000L,
            consecutiveHighCount = 3,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = false // Simulated holiday effect
        )

        assertEquals(AutomaticRideState.WAITING_FOR_WINDOW, decision.nextState)
        assertFalse(decision.shouldStartSharing)
        assertTrue("Must immediately stop sharing on non-working day", decision.shouldStopSharing)
    }

    @Test
    fun testStudentAutomaticContribution_proceedsNormallyOnWorkingDay() {
        val decision = AutomaticRideManager.evaluateStateTransition(
            currentState = AutomaticRideState.WAITING_FOR_BUS,
            score = 85,
            now = 1000L,
            consecutiveHighCount = 0,
            consecutiveDivergenceCount = 0,
            firstDivergenceTimestamp = 0L,
            isWithinWindow = true // Working day inside window
        )

        assertEquals(AutomaticRideState.POSSIBLY_RIDING, decision.nextState)
    }

    @Test
    fun testDateFormattingAndParsing_isRobust() {
        val date = LocalDate.of(2026, 8, 15)
        val formatted = CollegeCalendarService.formatDate(date)
        assertEquals("2026-08-15", formatted)

        val parsed = CollegeCalendarService.parseDate(formatted)
        assertNotNull(parsed)
        assertEquals(date, parsed)

        assertNull(CollegeCalendarService.parseDate("invalid-date"))
        assertNull(CollegeCalendarService.parseDate("2026-13-45"))
    }
}

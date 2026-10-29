package es.berenguer.protectorllamadas400

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneRulesTest {

    @Test
    fun `blocking schedule applies only on selected weekdays`() {
        assertTrue(
            PhoneRules.isWithinBlockingSchedule(
                timestamp(2026, Calendar.OCTOBER, 26, 10, 0),
                9 * 60,
                17 * 60,
                setOf(Calendar.MONDAY.toString())
            )
        )
        assertFalse(
            PhoneRules.isWithinBlockingSchedule(
                timestamp(2026, Calendar.OCTOBER, 27, 10, 0),
                9 * 60,
                17 * 60,
                setOf(Calendar.MONDAY.toString())
            )
        )
    }

    @Test
    fun `overnight schedule continues into the next day`() {
        val mondayOnly = setOf(Calendar.MONDAY.toString())

        assertTrue(
            PhoneRules.isWithinBlockingSchedule(
                timestamp(2026, Calendar.OCTOBER, 26, 23, 30),
                22 * 60,
                7 * 60,
                mondayOnly
            )
        )
        assertTrue(
            PhoneRules.isWithinBlockingSchedule(
                timestamp(2026, Calendar.OCTOBER, 27, 6, 30),
                22 * 60,
                7 * 60,
                mondayOnly
            )
        )
        assertFalse(
            PhoneRules.isWithinBlockingSchedule(
                timestamp(2026, Calendar.OCTOBER, 27, 8, 0),
                22 * 60,
                7 * 60,
                mondayOnly
            )
        )
    }

    @Test
    fun `equal times and empty weekdays disable the schedule`() {
        val mondayAtNoon = timestamp(2026, Calendar.OCTOBER, 26, 12, 0)

        assertFalse(
            PhoneRules.isWithinBlockingSchedule(
                mondayAtNoon,
                12 * 60,
                12 * 60,
                setOf(Calendar.MONDAY.toString())
            )
        )
        assertFalse(
            PhoneRules.isWithinBlockingSchedule(
                mondayAtNoon,
                9 * 60,
                17 * 60,
                emptySet()
            )
        )
    }

    @Test
    fun `prefix catalog includes Spain and allows matching international numbers`() {
        assertTrue(
            PhoneRules.countryPrefixOptions().any {
                it.prefix == "+34" && it.label.contains("España")
            }
        )
        assertEquals("+34", PhoneRules.normalizeCountryPrefix("0034"))
        assertTrue(PhoneRules.isCountryPrefixAllowed("+34600123456", setOf("+34")))
        assertFalse(PhoneRules.isCountryPrefixAllowed("+14155550123", setOf("+34")))
    }

    @Test
    fun `blocked prefixes match both international and local numbers`() {
        assertEquals("900", PhoneRules.normalizeBlockedPrefix(" 900 "))
        assertEquals("+3491", PhoneRules.normalizeBlockedPrefix("+34 91"))
        assertTrue(PhoneRules.matchesBlockedPrefix("900123456", setOf("900")))
        assertTrue(PhoneRules.matchesBlockedPrefix("+34912345678", setOf("+34 91")))
        assertFalse(PhoneRules.matchesBlockedPrefix("+34912345678", setOf("900")))
        assertFalse(PhoneRules.matchesBlockedPrefix("900123456", setOf("+3491")))
    }

    @Test
    fun `schedule exceptions match normalized local and international numbers`() {
        assertTrue(
            PhoneRules.matchesAnyNumberKey(
                "+34 600 12 34 56",
                setOf("600123456")
            )
        )
        assertTrue(
            PhoneRules.matchesAnyNumberKey(
                "600123456",
                setOf("+34600123456")
            )
        )
        assertFalse(PhoneRules.matchesAnyNumberKey("600123456", emptySet()))
        assertFalse(PhoneRules.matchesAnyNumberKey("600123456", setOf("600999999")))
    }

    private fun timestamp(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, hour, minute)
    }.timeInMillis
}

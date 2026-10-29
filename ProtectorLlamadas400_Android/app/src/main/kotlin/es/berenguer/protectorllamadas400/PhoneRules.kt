package es.berenguer.protectorllamadas400

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import java.util.Calendar
import java.util.Locale

data class CountryPrefixOption(
    val prefix: String,
    val label: String
)

object PhoneRules {

    private val phoneNumberUtil = PhoneNumberUtil.getInstance()

    fun countryPrefixOptions(): List<CountryPrefixOption> {
        val countriesByPrefix = sortedMapOf<String, MutableSet<String>>()
        val spanishLocale = Locale.forLanguageTag("es")

        for (region in phoneNumberUtil.supportedRegions) {
            val countryCode = phoneNumberUtil.getCountryCodeForRegion(region)
            if (countryCode <= 0) continue

            val countryName = Locale("", region).getDisplayCountry(spanishLocale)
            if (countryName.isNotBlank()) {
                countriesByPrefix.getOrPut("+$countryCode") { sortedSetOf() }.add(countryName)
            }
        }

        for (countryCode in phoneNumberUtil.supportedGlobalNetworkCallingCodes) {
            countriesByPrefix.getOrPut("+$countryCode") { sortedSetOf() }
                .add("Red internacional")
        }

        return countriesByPrefix.map { (prefix, countries) ->
            CountryPrefixOption(prefix, "$prefix — ${countries.joinToString()}")
        }.sortedBy { it.label }
    }

    fun normalizeCountryPrefix(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        val countryCode = when {
            digits.startsWith("00") -> digits.drop(2)
            trimmed.startsWith("+") -> digits
            else -> digits
        }

        return if (countryCode.length in 1..3 && countryCode.firstOrNull() != '0') {
            "+$countryCode"
        } else {
            ""
        }
    }

    fun isCountryPrefixAllowed(rawNumber: String, allowedPrefixes: Set<String>): Boolean {
        if (allowedPrefixes.isEmpty()) return true

        val parsedNumber = parsePhoneNumber(rawNumber) ?: return false
        val callingCode = "+${parsedNumber.countryCode}"
        return allowedPrefixes.any { normalizeCountryPrefix(it) == callingCode }
    }

    fun matchingKeys(raw: String): Set<String> {
        val keys = mutableSetOf<String>()
        val nationalNumber = normalize(raw)
        if (nationalNumber.isNotEmpty()) keys.add(nationalNumber)

        val parsedNumber = parsePhoneNumber(raw)
        if (parsedNumber != null && phoneNumberUtil.isPossibleNumber(parsedNumber)) {
            keys.add(phoneNumberUtil.format(parsedNumber, PhoneNumberFormat.E164))
        }
        return keys
    }

    fun matchesAnyNumberKey(rawNumber: String, keys: Set<String>): Boolean {
        // Compare both local and E.164 forms so exception rules work across number formats.
        if (keys.isEmpty()) return false
        val number = normalize(rawNumber)
        return matchingKeys(rawNumber).any { it in keys } || number in keys
    }

    fun preferredMatchKey(raw: String): String {
        return matchingKeys(raw).firstOrNull { it.startsWith("+") }
            ?: normalize(raw)
    }

    fun normalizeBlockedPrefix(raw: String): String {
        val value = raw.trim()
        val digits = value.filter { it.isDigit() }
        if (digits.isEmpty() || digits.length > 15) return ""
        return if (value.startsWith("+")) "+$digits" else digits
    }

    fun matchesBlockedPrefix(rawNumber: String, prefixes: Set<String>): Boolean {
        if (prefixes.isEmpty()) return false
        val keys = matchingKeys(rawNumber)
        return prefixes.any { prefix ->
            val normalizedPrefix = normalizeBlockedPrefix(prefix)
            if (normalizedPrefix.isEmpty()) {
                false
            } else if (normalizedPrefix.startsWith("+")) {
                keys.any { it.startsWith("+") && it.startsWith(normalizedPrefix) }
            } else {
                keys.any { key ->
                    val digits = key.filter { it.isDigit() }
                    digits.startsWith(normalizedPrefix)
                }
            }
        }
    }

    fun isWithinBlockingSchedule(
        timestamp: Long,
        startMinute: Int,
        endMinute: Int,
        selectedDays: Set<String>
    ): Boolean {
        if (startMinute !in 0 until 24 * 60 || endMinute !in 0 until 24 * 60 ||
            startMinute == endMinute || selectedDays.isEmpty()
        ) {
            return false
        }

        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        val currentMinute = calendar.get(Calendar.HOUR_OF_DAY) * 60 +
            calendar.get(Calendar.MINUTE)
        val todaySelected = currentDay.toString() in selectedDays

        return if (startMinute < endMinute) {
            todaySelected && currentMinute >= startMinute && currentMinute < endMinute
        } else {
            val previousDay = if (currentDay == Calendar.SUNDAY) {
                Calendar.SATURDAY
            } else {
                currentDay - 1
            }
            (todaySelected && currentMinute >= startMinute) ||
                (previousDay.toString() in selectedDays && currentMinute < endMinute)
        }
    }

    fun normalize(raw: String): String {
        val numberWithoutExtension = raw.replace(
            Regex("(?i)(?:;ext=|ext\\.?|x)\\s*\\d+.*$"),
            ""
        )
        val digits = numberWithoutExtension.filter { it.isDigit() }

        return when {
            digits.startsWith("0034") && digits.length >= 13 ->
                digits.substring(4, 13)
            digits.startsWith("34") && digits.length >= 11 ->
                digits.substring(2, 11)
            digits.length == 9 ->
                digits
            else ->
                ""
        }
    }

    private fun parsePhoneNumber(raw: String) =
        try {
            val value = raw.trim()
            val internationalNumber = when {
                value.startsWith("+") -> value
                value.filter { it.isDigit() }.startsWith("00") ->
                    "+${value.filter { it.isDigit() }.drop(2)}"
                else -> value
            }
            phoneNumberUtil.parse(internationalNumber, "ES")
        } catch (_: NumberParseException) {
            null
        }

    fun formatNumber(number: String): String {
        return if (number.length == 9) {
            "${number.substring(0, 3)} ${number.substring(3, 6)} ${number.substring(6, 9)}"
        } else {
            number
        }
    }

    fun isSpanish400(number: String): Boolean {
        return number.length == 9 && number.startsWith("400")
    }

    fun isKnownScam(number: String): Boolean {
        val known = setOf<String>(
            // Añadir aquí números confirmados como fraudulentos.
        )
        return known.contains(number)
    }
}

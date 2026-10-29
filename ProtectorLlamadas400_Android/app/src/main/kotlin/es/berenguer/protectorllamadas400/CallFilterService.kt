package es.berenguer.protectorllamadas400

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.util.Log
import java.io.IOException

class CallFilterService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val rawNumber = callDetails.handle?.schemeSpecificPart ?: ""
        val number = PhoneRules.normalize(rawNumber)
        val matchingKeys = PhoneRules.matchingKeys(rawNumber)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        val block400 = prefs.getBoolean("block_400", true)
        val blocked = prefs.getStringSet("blocked_numbers", emptySet()) ?: emptySet()
        val allowed = prefs.getStringSet("allowed_numbers", emptySet()) ?: emptySet()
        val allowedCountryPrefixes =
            prefs.getStringSet("allowed_country_prefixes", emptySet()) ?: emptySet()
        val blockedPrefixes =
            prefs.getStringSet("blocked_prefixes", emptySet()) ?: emptySet()
        val blockPrivateNumbers = prefs.getBoolean("block_private_numbers", false)
        val blockExceptContacts = prefs.getBoolean("block_except_contacts", false)
        val scheduleEnabled = prefs.getBoolean("blocking_schedule_enabled", false)
        val scheduleActive = !scheduleEnabled || PhoneRules.isWithinBlockingSchedule(
            System.currentTimeMillis(),
            prefs.getInt("blocking_schedule_start", 22 * 60),
            prefs.getInt("blocking_schedule_end", 7 * 60),
            prefs.getStringSet("blocking_schedule_days", defaultScheduleDays()) ?: emptySet()
        )
        val scheduleExemptNumbers =
            prefs.getStringSet("blocking_schedule_exempt_numbers", emptySet()) ?: emptySet()
        val isScheduleExempt = scheduleEnabled && scheduleActive &&
            PhoneRules.matchesAnyNumberKey(rawNumber, scheduleExemptNumbers)

        // La lista blanca tiene prioridad absoluta.
        val importedContactNumbers = ContactDirectory.importedNumbers(this)
        val isImportedContact = matchingKeys.any { it in importedContactNumbers } ||
            number in importedContactNumbers
        val isAllowed = (allowed.any { it in matchingKeys } || allowed.contains(number)) &&
            !(scheduleEnabled && scheduleActive && isImportedContact && !isScheduleExempt)
        val isCountryAllowed =
            PhoneRules.isCountryPrefixAllowed(rawNumber, allowedCountryPrefixes)
        val isManuallyBlocked = blocked.any { it in matchingKeys } || blocked.contains(number)
        val isPrivateNumber = rawNumber.isBlank() ||
            callDetails.handlePresentation != TelecomManager.PRESENTATION_ALLOWED
        val isBlockedPrefix = PhoneRules.matchesBlockedPrefix(rawNumber, blockedPrefixes)
        val is400 = PhoneRules.isSpanish400(number)
        val isKnownScam = PhoneRules.isKnownScam(number)

        val mustBlock = scheduleActive && !isAllowed && !isScheduleExempt && (
            (blockPrivateNumbers && isPrivateNumber) ||
                (blockExceptContacts && !isAllowed) ||
                !isCountryAllowed ||
                isManuallyBlocked ||
                isBlockedPrefix ||
                (block400 && is400) ||
                isKnownScam
        )

        if (!mustBlock && prefs.getBoolean("call_alert_enabled", true)) {
            try {
                CallAlertNotification.show(this, rawNumber)
            } catch (exception: SecurityException) {
                Log.w("CallFilterService", "Android no permitió mostrar el aviso de llamada.", exception)
            }
        }

        try {
            CallLogStore.record(this, rawNumber, mustBlock)
        } catch (exception: IOException) {
            Log.e("CallFilterService", "No se pudo guardar la llamada en el registro.", exception)
        }

        val response = if (mustBlock) {
            // Carrier routing after rejection depends on the device and mobile operator.
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .setSkipCallLog(false)
                .build()
        } else {
            CallResponse.Builder().build()
        }

        respondToCall(callDetails, response)
    }

    private fun defaultScheduleDays(): Set<String> =
        setOf("1", "2", "3", "4", "5", "6", "7")
}

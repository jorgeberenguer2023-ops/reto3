package es.berenguer.protectorllamadas400

import android.content.Context

internal object BlockedNumbers {

    enum class Result {
        BLOCKED,
        PRIVATE_NUMBER,
        INVALID_NUMBER
    }

    fun add(context: Context, rawNumber: String): Result {
        if (rawNumber.isBlank()) return Result.PRIVATE_NUMBER

        val keys = PhoneRules.matchingKeys(rawNumber)
        if (keys.isEmpty()) return Result.INVALID_NUMBER

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val number = PhoneRules.normalize(rawNumber)
        val blocked = (prefs.getStringSet("blocked_numbers", emptySet()) ?: emptySet())
            .toMutableSet()
        val allowed = (prefs.getStringSet("allowed_numbers", emptySet()) ?: emptySet())
            .toMutableSet()
        blocked.addAll(keys)
        if (number.isNotEmpty()) blocked.add(number)
        allowed.removeAll(keys)
        if (number.isNotEmpty()) allowed.remove(number)
        prefs.edit()
            .putStringSet("blocked_numbers", blocked)
            .putStringSet("allowed_numbers", allowed)
            .apply()
        return Result.BLOCKED
    }
}

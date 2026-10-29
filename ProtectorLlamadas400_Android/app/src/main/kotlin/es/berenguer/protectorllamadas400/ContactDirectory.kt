package es.berenguer.protectorllamadas400

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import org.json.JSONException
import org.json.JSONObject

/**
 * Keeps an on-device lookup of imported contacts; WhatsApp groups are not exposed by Android.
 */
internal object ContactDirectory {

    private const val PREFS_NAME = "contact_directory"
    private const val CONTACTS_KEY = "names_by_number"
    private const val NUMBERS_KEY = "numbers"
    private const val OPTIONS_KEY = "schedule_options"

    fun save(context: Context, contacts: Map<String, String>) {
        val names = JSONObject()
        val scheduleOptions = JSONObject()
        val numbers = mutableSetOf<String>()
        contacts.forEach { (rawNumber, name) ->
            val keys = PhoneRules.matchingKeys(rawNumber)
            numbers.addAll(keys)
            val preferredKey = PhoneRules.preferredMatchKey(rawNumber)
            if (preferredKey.isNotBlank()) {
                scheduleOptions.put(preferredKey, name.ifBlank { preferredKey })
            }
            if (name.isNotBlank()) keys.forEach { names.put(it, name) }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(CONTACTS_KEY, names.toString())
            .putStringSet(NUMBERS_KEY, numbers)
            .putString(OPTIONS_KEY, scheduleOptions.toString())
            .apply()
    }

    fun nameFor(context: Context, rawNumber: String): String? {
        val names = storedNames(context)
        val savedName = PhoneRules.matchingKeys(rawNumber)
            .firstNotNullOfOrNull { names.optString(it).takeIf(String::isNotBlank) }
        if (savedName != null || rawNumber.isBlank() ||
            context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return savedName
        }

        // Check the system address book as well so a contact saved after import is recognized.
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(rawNumber)
        )
        return try {
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val resolvedName = context.contentResolver.query(
                lookupUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    )
                } else {
                    null
                }
            }?.takeIf(String::isNotBlank) ?: return null
            cacheName(context, rawNumber, resolvedName)
            resolvedName
        } catch (exception: SecurityException) {
            Log.w("ContactDirectory", "Android no permitió consultar el nombre de la llamada.", exception)
            null
        }
    }

    fun scheduleOptions(context: Context): List<Pair<String, String>> {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = preferences.getString(OPTIONS_KEY, null) ?: return emptyList()
        return try {
            val options = JSONObject(stored)
            options.keys().asSequence().map { number ->
                number to options.optString(number).ifBlank { number }
            }.sortedBy { it.second.lowercase() }.toList()
        } catch (exception: JSONException) {
            Log.w("ContactDirectory", "No se pudieron leer los contactos para las excepciones del horario.", exception)
            emptyList()
        }
    }

    fun importedNumbers(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(NUMBERS_KEY, emptySet()) ?: emptySet()

    private fun storedNames(context: Context): JSONObject {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(CONTACTS_KEY, null) ?: return JSONObject()
        return try {
            JSONObject(json)
        } catch (exception: JSONException) {
            Log.w("ContactDirectory", "La agenda local está dañada y no se pueden resolver nombres.", exception)
            JSONObject()
        }
    }

    private fun cacheName(context: Context, rawNumber: String, name: String) {
        val names = storedNames(context)
        PhoneRules.matchingKeys(rawNumber).forEach { names.put(it, name) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(CONTACTS_KEY, names.toString())
            .apply()
    }
}

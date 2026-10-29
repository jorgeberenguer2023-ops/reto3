package es.berenguer.protectorllamadas400

import android.content.Context
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException

data class CallLogEntry(
    val number: String,
    val timestamp: Long,
    val blocked: Boolean,
    val direction: String = CallDirection.INCOMING,
    val contactName: String? = null
)

// Persisted direction values let the call log separate received and placed calls.
object CallDirection {
    const val INCOMING = "incoming"
    const val OUTGOING = "outgoing"
}

object CallLogStore {

    private const val FILE_NAME = "call_log.jsonl"
    private const val MAX_ENTRIES = 10_000
    private const val PREFS_NAME = "call_log_index"
    private const val ENTRY_COUNT_KEY = "entry_count"
    private val fileLock = Any()

    @Throws(IOException::class)
    fun record(
        context: Context,
        number: String,
        blocked: Boolean,
        direction: String = CallDirection.INCOMING,
        contactName: String? = ContactDirectory.nameFor(context, number),
        timestamp: Long = System.currentTimeMillis()
    ) {
        // New fields are additive; readAll supplies defaults for entries created by older builds.
        val entry = JSONObject()
            .put("number", number.ifBlank { "Número oculto" })
            .put("timestamp", timestamp)
            .put("blocked", blocked)
            .put("direction", direction)
            .put("contactName", contactName)
            .toString()

        synchronized(fileLock) {
            val index = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentCount = if (index.contains(ENTRY_COUNT_KEY)) {
                index.getInt(ENTRY_COUNT_KEY, 0)
            } else {
                readRawLines(context).size
            }

            context.openFileOutput(FILE_NAME, Context.MODE_APPEND).bufferedWriter().use {
                it.append(entry)
                it.newLine()
            }

            val newCount = currentCount + 1
            if (newCount > MAX_ENTRIES) {
                val lines = readRawLines(context)
                context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).bufferedWriter().use {
                    for (line in lines.takeLast(MAX_ENTRIES)) {
                        it.append(line)
                        it.newLine()
                    }
                }
                index.edit().putInt(ENTRY_COUNT_KEY, MAX_ENTRIES).apply()
            } else {
                index.edit().putInt(ENTRY_COUNT_KEY, newCount).apply()
            }
        }
    }

    @Throws(IOException::class)
    fun readAll(context: Context): List<CallLogEntry> = synchronized(fileLock) {
        readRawLines(context).mapNotNull { line ->
            try {
                val json = JSONObject(line)
                CallLogEntry(
                    number = json.getString("number"),
                    timestamp = json.getLong("timestamp"),
                    blocked = json.getBoolean("blocked"),
                    direction = json.optString("direction", CallDirection.INCOMING),
                    contactName = json.optString("contactName").takeIf(String::isNotBlank)
                )
            } catch (exception: JSONException) {
                Log.w("CallLogStore", "Se omite una entrada de registro dañada.", exception)
                null
            }
        }
    }

    @Throws(IOException::class)
    private fun readRawLines(context: Context): List<String> {
        val input = try {
            context.openFileInput(FILE_NAME)
        } catch (_: FileNotFoundException) {
            return emptyList()
        }

        return input.bufferedReader().use { it.readLines() }
    }
}

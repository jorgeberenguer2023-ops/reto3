package es.berenguer.protectorllamadas400

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.text.Normalizer

internal enum class AssistantMessageSender {
    USER,
    ASSISTANT
}

internal data class AssistantMessage(
    val sender: AssistantMessageSender,
    val text: String
)

/**
 * Builds replies only from the call history stored on this device; it does not invent transcripts.
 */
internal object CallAssistant {

    fun reply(
        question: String,
        calls: List<CallLogEntry>,
        locale: Locale = Locale.getDefault()
    ): String {
        val normalizedQuestion = Normalizer.normalize(
            question.lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        ).replace(Regex("\\p{Mn}+"), "")
        val latestCalls = calls.sortedByDescending { it.timestamp }

        if (latestCalls.isEmpty()) {
            return "Todavía no hay llamadas guardadas en el registro. Cuando haya llamadas, podré ayudarte a consultarlas."
        }
        if (listOf("buzon", "mensaje", "audio", "transcripcion").any(normalizedQuestion::contains)) {
            return "NoMolesten no guarda el audio ni la transcripción del buzón del operador. Puedes abrir la aplicación Teléfono para escucharlos y gestionarlos."
        }
        if (listOf("bloquead", "rechazad").any(normalizedQuestion::contains)) {
            val blockedCalls = latestCalls.filter { it.blocked }
            return if (blockedCalls.isEmpty()) {
                "No hay llamadas bloqueadas en el registro."
            } else {
                val noun = if (blockedCalls.size == 1) "llamada bloqueada" else "llamadas bloqueadas"
                "Hay ${blockedCalls.size} $noun. Las más recientes: ${
                    describeCalls(blockedCalls.take(3), locale)
                }."
            }
        }
        if (listOf("realizad", "saliente", "he llamado").any(normalizedQuestion::contains)) {
            val outgoingCalls = latestCalls.filter { it.direction == CallDirection.OUTGOING }
            return if (outgoingCalls.isEmpty()) {
                "No hay llamadas realizadas guardadas en el registro."
            } else {
                val noun = if (outgoingCalls.size == 1) "llamada realizada" else "llamadas realizadas"
                "Hay ${outgoingCalls.size} $noun. Las más recientes: ${
                    describeCalls(outgoingCalls.take(3), locale)
                }."
            }
        }
        if (listOf("ultima", "ultimas", "reciente", "quien me ha llamado").any(normalizedQuestion::contains)) {
            return "Las llamadas más recientes: ${describeCalls(latestCalls.take(3), locale)}."
        }

        val blockedCount = latestCalls.count { it.blocked }
        val outgoingCount = latestCalls.count { it.direction == CallDirection.OUTGOING }
        return "Hay ${latestCalls.size} llamadas guardadas, $blockedCount bloqueadas y $outgoingCount realizadas. Las más recientes: ${
            describeCalls(latestCalls.take(3), locale)
        }."
    }

    private fun describeCalls(calls: List<CallLogEntry>, locale: Locale): String {
        val dateFormat = SimpleDateFormat("dd/MM HH:mm", locale)
        return calls.joinToString("; ") { call ->
            val caller = call.contactName?.takeIf(String::isNotBlank) ?: call.number
            val status = when {
                call.direction == CallDirection.OUTGOING -> "realizada"
                call.blocked -> "bloqueada"
                else -> "recibida"
            }
            "$caller, ${dateFormat.format(Date(call.timestamp))}, $status"
        }
    }
}

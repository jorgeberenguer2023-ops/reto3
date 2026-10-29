package es.berenguer.protectorllamadas400

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CallAssistantTest {

    @Test
    fun emptyHistoryDoesNotInventCalls() {
        val reply = CallAssistant.reply("¿Qué llamadas he recibido?", emptyList())

        assertTrue(reply.contains("no hay llamadas", ignoreCase = true))
    }

    @Test
    fun blockedQuestionOnlySummarizesBlockedCalls() {
        val calls = listOf(
            CallLogEntry("+34600000001", 2_000L, blocked = false),
            CallLogEntry("+34600000002", 1_000L, blocked = true, contactName = "Ana")
        )

        val reply = CallAssistant.reply("¿Qué llamadas están bloqueadas?", calls, Locale("es", "ES"))

        assertTrue(reply.contains("1 llamada bloqueada"))
        assertTrue(reply.contains("Ana"))
        assertTrue(!reply.contains("+34600000001"))
    }

    @Test
    fun voicemailQuestionExplainsAudioIsNotAvailableInApp() {
        val calls = listOf(CallLogEntry("+34600000001", 1_000L, blocked = true))

        val reply = CallAssistant.reply("¿Qué mensajes hay en el buzón?", calls)

        assertTrue(reply.contains("no guarda el audio", ignoreCase = true))
        assertTrue(reply.contains("Teléfono"))
    }
}

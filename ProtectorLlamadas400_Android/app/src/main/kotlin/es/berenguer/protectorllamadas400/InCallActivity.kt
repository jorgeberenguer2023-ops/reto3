package es.berenguer.protectorllamadas400

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.telecom.Call
import android.telecom.VideoProfile
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class InCallActivity : AppCompatActivity() {

    private lateinit var callerText: TextView
    private lateinit var stateText: TextView
    private lateinit var answerButton: Button
    private lateinit var endButton: Button
    private lateinit var blockButton: Button
    private var waitForOutgoingCallUntil = 0L
    private val refresh = object : Runnable {
        override fun run() {
            refreshCall()
            if (!isFinishing) handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        buildUi()
        if (intent.getBooleanExtra(EXTRA_WAIT_FOR_CALL, false)) {
            waitForOutgoingCallUntil = SystemClock.elapsedRealtime() + OUTGOING_CALL_TIMEOUT_MS
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(40), dp(32), dp(40))
            setBackgroundColor(Color.rgb(36, 42, 52))
        }
        callerText = TextView(this).apply {
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        stateText = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(16), 0, dp(32))
        }
        root.addView(callerText)
        root.addView(stateText)

        answerButton = button("Contestar", Color.rgb(46, 125, 50)) {
            PhoneCallRegistry.primaryCall()?.answer(VideoProfile.STATE_AUDIO_ONLY)
            refreshCall()
        }
        endButton = button("Finalizar llamada", Color.rgb(198, 40, 40)) {
            PhoneCallRegistry.primaryCall()?.disconnect()
        }
        blockButton = button("Bloquear y colgar", Color.rgb(88, 101, 232)) {
            blockAndEndCall()
        }
        root.addView(answerButton, buttonLayoutParams())
        root.addView(endButton, buttonLayoutParams())
        root.addView(blockButton, buttonLayoutParams())
        setContentView(root)
    }

    private fun refreshCall() {
        val call = PhoneCallRegistry.primaryCall()
        if (call == null) {
            if (SystemClock.elapsedRealtime() < waitForOutgoingCallUntil) {
                stateText.text = "Conectando llamada…"
                return
            }
            finish()
            return
        }
        waitForOutgoingCallUntil = 0L

        val rawNumber = call.details.handle?.schemeSpecificPart.orEmpty()
        val number = PhoneRules.preferredMatchKey(rawNumber).ifBlank {
            if (rawNumber.isBlank()) "Número privado" else rawNumber
        }
        callerText.text = ContactDirectory.nameFor(this, rawNumber)
            ?.let { "$it\n$number" }
            ?: number
        stateText.text = when (call.details.state) {
            Call.STATE_RINGING -> "Llamada entrante"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "Llamando…"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "Selecciona la tarjeta SIM"
            Call.STATE_NEW -> "Preparando llamada…"
            Call.STATE_ACTIVE -> "Llamada en curso"
            Call.STATE_HOLDING -> "Llamada en espera"
            Call.STATE_DISCONNECTING -> "Finalizando llamada…"
            else -> "Gestiona la llamada"
        }
        answerButton.visibility =
            if (call.details.state == Call.STATE_RINGING) android.view.View.VISIBLE
            else android.view.View.GONE
        endButton.text =
            if (call.details.state == Call.STATE_RINGING) "Rechazar llamada"
            else "Finalizar llamada"
        blockButton.isEnabled =
            rawNumber.isNotBlank() && PhoneRules.matchingKeys(rawNumber).isNotEmpty()
    }

    private fun blockAndEndCall() {
        val call = PhoneCallRegistry.primaryCall() ?: return
        val rawNumber = call.details.handle?.schemeSpecificPart.orEmpty()
        when (BlockedNumbers.add(this, rawNumber)) {
            BlockedNumbers.Result.BLOCKED -> {
                Toast.makeText(this, "Número bloqueado; llamada finalizada.", Toast.LENGTH_SHORT)
                    .show()
                call.disconnect()
            }
            BlockedNumbers.Result.PRIVATE_NUMBER ->
                Toast.makeText(this, "No se puede bloquear un número oculto.", Toast.LENGTH_SHORT)
                    .show()
            BlockedNumbers.Result.INVALID_NUMBER ->
                Toast.makeText(this, "El número no tiene un formato válido.", Toast.LENGTH_SHORT)
                    .show()
        }
    }

    private fun button(label: String, color: Int, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            setOnClickListener { onClick() }
        }

    private fun buttonLayoutParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(12)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val EXTRA_WAIT_FOR_CALL = "wait_for_outgoing_call"
        const val REFRESH_INTERVAL_MS = 500L
        const val OUTGOING_CALL_TIMEOUT_MS = 10_000L
    }
}

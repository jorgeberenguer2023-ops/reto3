package es.berenguer.protectorllamadas400

import android.os.Handler
import android.os.Looper
import android.telecom.Call

internal object PhoneCallRegistry {

    private val calls = linkedMapOf<Call, Call.Callback>()

    @Synchronized
    fun add(call: Call, onChanged: () -> Unit) {
        if (calls.containsKey(call)) return

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = onChanged()

            override fun onDetailsChanged(call: Call, details: Call.Details) = onChanged()
        }
        calls[call] = callback
        call.registerCallback(callback, Handler(Looper.getMainLooper()))
    }

    @Synchronized
    fun remove(call: Call) {
        calls.remove(call)?.let(call::unregisterCallback)
    }

    @Synchronized
    fun primaryCall(): Call? {
        val currentCalls = calls.keys
        return currentCalls.firstOrNull { it.details.state == Call.STATE_RINGING }
            ?: currentCalls.firstOrNull { it.details.state == Call.STATE_ACTIVE }
            ?: currentCalls.firstOrNull {
                it.details.state == Call.STATE_DIALING ||
                    it.details.state == Call.STATE_CONNECTING ||
                    it.details.state == Call.STATE_HOLDING ||
                    it.details.state == Call.STATE_SELECT_PHONE_ACCOUNT ||
                    it.details.state == Call.STATE_NEW
            }
    }
}

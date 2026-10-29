package es.berenguer.protectorllamadas400

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class OutgoingCallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!OutgoingCallNotification.isBlockAction(intent)) return
        val number = OutgoingCallNotification.numberFrom(intent)
        if (number.isNullOrBlank()) {
            Log.w("OutgoingCallActionReceiver", "La acción de bloqueo no contiene un número.")
            return
        }
        when (BlockedNumbers.add(context, number)) {
            BlockedNumbers.Result.BLOCKED -> {
                OutgoingCallNotification.cancel(context, number)
                Toast.makeText(context, "Número bloqueado.", Toast.LENGTH_SHORT).show()
            }
            BlockedNumbers.Result.PRIVATE_NUMBER ->
                Toast.makeText(context, "No se puede bloquear un número oculto.", Toast.LENGTH_SHORT)
                    .show()
            BlockedNumbers.Result.INVALID_NUMBER ->
                Toast.makeText(context, "El número no tiene un formato válido.", Toast.LENGTH_SHORT)
                    .show()
        }
    }
}

package es.berenguer.protectorllamadas400

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.IOException

class BlockCallerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!CallAlertNotification.isBlockCallerAction(intent)) return

        val rawNumber = CallAlertNotification.phoneNumberFrom(intent)
        val numberToBlock = rawNumber ?: ""
        when (BlockedNumbers.add(context, numberToBlock)) {
            BlockedNumbers.Result.PRIVATE_NUMBER -> {
                Log.w("BlockCallerReceiver", "No se puede bloquear un número oculto.")
                return
            }
            BlockedNumbers.Result.INVALID_NUMBER -> {
                Log.w("BlockCallerReceiver", "El número recibido no tiene un formato válido.")
                return
            }
            BlockedNumbers.Result.BLOCKED -> Unit
        }

        try {
            CallLogStore.record(context, numberToBlock, blocked = true)
        } catch (exception: IOException) {
            Log.e("BlockCallerReceiver", "No se pudo registrar el bloqueo manual.", exception)
        }

        context.getSystemService(android.app.NotificationManager::class.java)
            .cancel(4001)
    }
}

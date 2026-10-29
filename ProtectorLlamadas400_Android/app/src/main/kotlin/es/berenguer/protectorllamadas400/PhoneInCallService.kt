package es.berenguer.protectorllamadas400

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.IOException

class PhoneInCallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        createCallNotificationChannel()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        PhoneCallRegistry.add(call, ::updateCallNotification)
        updateCallNotification()
    }

    override fun onCallRemoved(call: Call) {
        val details = call.details
        val rawNumber = details.handle?.schemeSpecificPart.orEmpty()
        // Record and offer follow-up actions only for calls placed by the user.
        if (details.callDirection == Call.Details.DIRECTION_OUTGOING && rawNumber.isNotBlank()) {
            try {
                CallLogStore.record(
                    this,
                    rawNumber,
                    blocked = false,
                    direction = CallDirection.OUTGOING,
                    contactName = ContactDirectory.nameFor(this, rawNumber)
                )
            } catch (exception: IOException) {
                Log.e("PhoneInCallService", "No se pudo guardar la llamada realizada.", exception)
            }
            try {
                OutgoingCallNotification.show(this, rawNumber)
            } catch (exception: SecurityException) {
                Log.w("PhoneInCallService", "Android no permitió mostrar las acciones de llamada.", exception)
            }
        }
        PhoneCallRegistry.remove(call)
        updateCallNotification()
        super.onCallRemoved(call)
    }

    private fun createCallNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Llamadas en curso",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Muestra los controles de llamadas entrantes y en curso."
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun updateCallNotification() {
        val call = PhoneCallRegistry.primaryCall()
        val manager = getSystemService(NotificationManager::class.java)
        if (call == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }

        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val rawNumber = call.details.handle?.schemeSpecificPart.orEmpty()
        val callerName = ContactDirectory.nameFor(this, rawNumber)
        val number = PhoneRules.preferredMatchKey(rawNumber).ifBlank {
            if (rawNumber.isBlank()) "Número privado" else rawNumber
        }
        val isRinging = call.details.state == Call.STATE_RINGING
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone_block)
            .setContentTitle(if (isRinging) "Llamada entrante" else "Llamada en curso")
            .setContentText(callerName?.let { "$it · $number" } ?: number)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, isRinging)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (exception: SecurityException) {
            Log.w("PhoneInCallService", "Android no permitió mostrar la notificación de llamada.", exception)
        }
    }

    private companion object {
        const val CHANNEL_ID = "in_call_controls"
        const val NOTIFICATION_ID = 4002
    }
}

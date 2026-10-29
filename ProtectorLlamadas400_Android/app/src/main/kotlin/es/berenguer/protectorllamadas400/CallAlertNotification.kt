package es.berenguer.protectorllamadas400

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object CallAlertNotification {

    internal const val CHANNEL_ID = "incoming_call_alerts"
    private const val NOTIFICATION_ID = 4001
    private const val ACTION_BLOCK_CALLER =
        "es.berenguer.protectorllamadas400.action.BLOCK_CALLER"
    private const val EXTRA_PHONE_NUMBER = "phone_number"

    fun show(context: Context, rawNumber: String) {
        ensureChannel(context)

        val phoneNumber = PhoneRules.preferredMatchKey(rawNumber).ifBlank {
            if (rawNumber.isBlank()) "Número privado" else rawNumber
        }
        val callerName = ContactDirectory.nameFor(context, rawNumber)
        val isDefaultPhone = Build.VERSION.SDK_INT >= 29 &&
            context.getSystemService(android.app.role.RoleManager::class.java)
                .isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)
        val activity = if (isDefaultPhone) {
            InCallActivity::class.java
        } else {
            MainActivity::class.java
        }
        val openAppIntent = Intent(context, activity).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone_block)
            .setContentTitle("Llamada entrante")
            .setContentText(callerName?.let { "$it · $phoneNumber" } ?: phoneNumber)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        if (rawNumber.isNotBlank() && PhoneRules.preferredMatchKey(rawNumber).isNotBlank()) {
            val blockIntent = Intent(context, BlockCallerReceiver::class.java).apply {
                action = ACTION_BLOCK_CALLER
                data = Uri.parse("protectorllamadas://block/${Uri.encode(rawNumber)}")
                putExtra(EXTRA_PHONE_NUMBER, rawNumber)
            }
            val blockPendingIntent = PendingIntent.getBroadcast(
                context,
                rawNumber.hashCode(),
                blockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_phone_block,
                "Bloquear número",
                blockPendingIntent
            )
        }

        if (!isDefaultPhone) builder.setFullScreenIntent(contentIntent, true)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Avisos de llamadas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Muestra quién llama y permite bloquear el número."
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }

    internal fun isBlockCallerAction(intent: Intent): Boolean =
        intent.action == ACTION_BLOCK_CALLER

    internal fun phoneNumberFrom(intent: Intent): String? =
        intent.getStringExtra(EXTRA_PHONE_NUMBER)
}

package es.berenguer.protectorllamadas400

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat

/** Offers a block action and the system contact editor after an outgoing call ends. */
internal object OutgoingCallNotification {

    private const val CHANNEL_ID = "outgoing_call_follow_up"
    private const val CHANNEL_NAME = "Llamadas realizadas"
    private const val ACTION_BLOCK = "es.berenguer.protectorllamadas400.BLOCK_OUTGOING"
    private const val EXTRA_NUMBER = "outgoing_phone_number"

    fun show(context: Context, rawNumber: String) {
        createChannel(context)
        val name = ContactDirectory.nameFor(context, rawNumber)
        val number = PhoneRules.preferredMatchKey(rawNumber).ifBlank { rawNumber }
        val notificationId = rawNumber.hashCode() xor 0x4000
        val blockIntent = Intent(context, OutgoingCallActionReceiver::class.java).apply {
            action = ACTION_BLOCK
            data = Uri.parse("nomolesten://outgoing/block/${Uri.encode(rawNumber)}")
            putExtra(EXTRA_NUMBER, rawNumber)
        }
        val blockPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val saveContactIntent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, rawNumber)
        }
        val saveContactPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            saveContactIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone_block)
            .setContentTitle("¿Qué quieres hacer con esta llamada?")
            .setContentText(name?.let { "$it · $number" } ?: number)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .addAction(0, "Bloquear", blockPendingIntent)
            .addAction(0, "Guardar con nombre", saveContactPendingIntent)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification)
    }

    fun cancel(context: Context, rawNumber: String) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(rawNumber.hashCode() xor 0x4000)
    }

    internal fun isBlockAction(intent: Intent): Boolean = intent.action == ACTION_BLOCK

    internal fun numberFrom(intent: Intent): String? = intent.getStringExtra(EXTRA_NUMBER)

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        )
    }
}

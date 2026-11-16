package ma.sanai3i.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives Firebase push messages (data: title, body, link) and shows them; keeps the device token. */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        saveToken(this, token)
        tokenListener?.invoke(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "Brikol"
        val body = data["body"] ?: message.notification?.body ?: return
        show(this, title, body, data["link"] ?: "/app/")
    }

    companion object {
        const val CHANNEL = "brikol"
        private const val PREFS = "brikol"
        var tokenListener: ((String) -> Unit)? = null

        fun saveToken(ctx: Context, token: String) =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("push_token", token).apply()

        fun token(ctx: Context): String = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("push_token", "") ?: ""

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Brikol", NotificationManager.IMPORTANCE_HIGH))
                }
            }
        }

        fun show(ctx: Context, title: String, body: String, link: String) {
            ensureChannel(ctx)
            val open = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("link", link)
            val pi = PendingIntent.getActivity(ctx, link.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .build()
            try {
                NotificationManagerCompat.from(ctx).notify(System.currentTimeMillis().toInt(), n)
            } catch (e: SecurityException) {
                // notifications not allowed by the user
            }
        }
    }
}

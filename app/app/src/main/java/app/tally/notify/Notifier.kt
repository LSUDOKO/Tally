package app.tally.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.tally.MainActivity
import app.tally.R

/**
 * "Was this you?": every agent payment posts a notification with a Freeze action. The action only
 * opens Tally's freeze confirmation; freezing still needs the owner's wallet signature.
 */
object Notifier {
    const val ACTION_FREEZE = "app.tally.action.FREEZE"
    private const val CHANNEL = "payments"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Agent payments", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Every payment your agent makes, with a one-tap freeze."
                },
            )
        }
    }

    fun canPost(context: Context) =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Intent that opens Tally straight to the freeze confirmation. */
    fun freezeIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).setAction(ACTION_FREEZE).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun paid(context: Context, item: String, merchant: String, amount: String) {
        if (!canPost(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val freeze = PendingIntent.getActivity(context, 1, freezeIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setColor(0xFF0066FF.toInt())
            .setContentTitle("Your agent paid \$$amount")
            .setContentText("$item at $merchant. Wasn't you? Freeze your capsule.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$item at $merchant, checked on-chain against your rules.\nWasn't you? Freeze your capsule and nothing more can be spent."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Freeze capsule", freeze)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), n) }
    }
}

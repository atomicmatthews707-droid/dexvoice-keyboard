package com.tektomic.dexvoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Persistent low-priority notification with a Start/Stop action. */
object ControlNotification {
    const val CHANNEL_ID = "dexvoice_control"
    const val NOTIFICATION_ID = 4711
    const val ACTION_TOGGLE = "com.tektomic.dexvoice.TOGGLE"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DeX Voice control", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Start or stop dictation from the notification shade"
                    setShowBadge(false)
                }
            )
        }
    }

    fun build(context: Context, state: VoiceState): Notification {
        ensureChannel(context)
        val (title, action) = when (state) {
            is VoiceState.Recording -> "Listening…" to "Stop"
            is VoiceState.Transcribing -> "Transcribing…" to "Please wait"
            is VoiceState.Error -> state.message to "Start"
            else -> "Ready to dictate" to "Start"
        }
        val pi = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_TOGGLE).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, SettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("DeX Voice")
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, action, pi).build())
            .build()
    }
}

/** Handles the notification's Start/Stop button. */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ControlNotification.ACTION_TOGGLE) {
            try { VoiceController.get(context).toggle() } catch (_: Exception) {}
        }
    }
}

/** Optionally restarts the bubble service after boot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val s = Settings(context)
        if (s.bubbleEnabled && s.bubbleOnBoot) {
            try { BubbleService.start(context) } catch (_: Exception) {}
        }
    }
}

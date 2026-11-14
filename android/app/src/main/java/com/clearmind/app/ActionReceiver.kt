package com.clearmind.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Notification buttons that act without opening the app. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_CLEAN = "com.clearmind.app.CLEAN"
        const val ACTION_DISMISS = "com.clearmind.app.DISMISS"
    }

    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            ACTION_CLEAN -> {
                Store.queue(c, "clean", "")
                NotificationManagerCompat.from(c).cancel(310)
                Notifier.nudge(
                    c, "Day ${Store.tbDay(c)} locked in",
                    "That's another one behind you. Sleep well.",
                    withCraveButton = false, id = 313
                )
            }
            ACTION_DISMISS -> {
                // Nothing to record — the point is that it costs one tap to say "I'm fine".
            }
        }
    }
}

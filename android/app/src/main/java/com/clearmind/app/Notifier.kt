package com.clearmind.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.random.Random

/**
 * Every notification ClearMind sends. The ongoing clean-time notification is a
 * live count-up chronometer, so the clock is visible without opening the app.
 */
object Notifier {
    const val CH_NUDGE = "nudges"       // risky-time heads-up, morning, evening, weekly
    const val CH_CLOCK = "clock"        // silent ongoing clean-time
    const val CH_CRAVE = "craving"      // live 10-minute countdown

    const val ID_CLOCK = 1
    const val ID_CRAVE = 2

    fun ensureChannels(c: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_NUDGE, "Check-ins and heads-ups", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Messages before your risky times, plus morning and evening check-ins."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CLOCK, "Clean-time clock", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A quiet, permanent notification showing how long you have been clean."
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CRAVE, "Craving timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The live 10-minute timer while you are riding out a craving."
                setShowBadge(false)
            }
        )
    }

    fun openApp(c: Context, dest: String, reqCode: Int): PendingIntent {
        val i = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("dest", dest)
        }
        return PendingIntent.getActivity(c, reqCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(c: Context, what: String, reqCode: Int): PendingIntent {
        val i = Intent(c, ActionReceiver::class.java).setAction(what)
        return PendingIntent.getBroadcast(c, reqCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun can(c: Context) = NotificationManagerCompat.from(c).areNotificationsEnabled()

    /** A nudge: title, body, and a "I want to smoke" button that opens the craving screen. */
    fun nudge(c: Context, title: String, body: String, withCraveButton: Boolean = true, id: Int = Random.nextInt(1000, 9999)) {
        if (!can(c)) return
        ensureChannels(c)
        val b = NotificationCompat.Builder(c, CH_NUDGE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(0xFF8FA58A.toInt())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(c, "home", id))
        if (withCraveButton) {
            b.addAction(0, "I want to smoke", openApp(c, "crave", id + 1))
            b.addAction(0, "I'm good", action(c, ActionReceiver.ACTION_DISMISS, id + 2))
        }
        NotificationManagerCompat.from(c).notify(id, b.build())
    }

    /** Evening check-in with two one-tap answers. */
    fun eveningCheck(c: Context) {
        if (!can(c)) return
        ensureChannels(c)
        val day = Store.tbDay(c)
        val b = NotificationCompat.Builder(c, CH_NUDGE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Day $day — clean today?")
            .setContentText("One tap. Being honest with yourself is the whole point.")
            .setColor(0xFF8FA58A.toInt())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(c, "home", 310))
            .addAction(0, "Yes, clean", action(c, ActionReceiver.ACTION_CLEAN, 311))
            .addAction(0, "I slipped", openApp(c, "slip", 312))
        NotificationManagerCompat.from(c).notify(310, b.build())
    }

    /** Silent, ongoing: the clean-time clock counting up on the lock screen. */
    fun showClock(c: Context) {
        if (!can(c)) { return }
        ensureChannels(c)
        if (!Store.running(c)) { NotificationManagerCompat.from(c).cancel(ID_CLOCK); return }
        val last = Store.lastUse(c)
        val day = Store.tbDay(c)
        val days = Store.tbDays(c)
        val sub = if (Store.tbOpen(c)) "Day $day" else "Day $day of $days"
        val b = NotificationCompat.Builder(c, CH_CLOCK)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Clean")
            .setContentText(sub)
            .setColor(0xFF8FA58A.toInt())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp(c, "home", 100))
            .addAction(0, "I want to smoke", openApp(c, "crave", 101))
        if (last > 0) {
            b.setUsesChronometer(true).setWhen(last).setShowWhen(true)
        }
        NotificationManagerCompat.from(c).notify(ID_CLOCK, b.build())
    }

    /** Live countdown while a craving is being ridden out. */
    fun showCrave(c: Context, endAt: Long) {
        if (!can(c)) return
        ensureChannels(c)
        val b = NotificationCompat.Builder(c, CH_CRAVE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Riding it out")
            .setContentText("Hold on. It peaks and then it fades.")
            .setColor(0xFFC9925E.toInt())
            .setOngoing(true)
            .setSilent(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endAt)
            .setShowWhen(true)
            .setContentIntent(openApp(c, "crave", 200))
        NotificationManagerCompat.from(c).notify(ID_CRAVE, b.build())
    }

    fun cancelCrave(c: Context) = NotificationManagerCompat.from(c).cancel(ID_CRAVE)

    /** When the 10 minutes are up. */
    fun craveDone(c: Context) {
        cancelCrave(c)
        if (!can(c)) return
        val b = NotificationCompat.Builder(c, CH_NUDGE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("10 minutes. You held on.")
            .setContentText("How is the craving now?")
            .setColor(0xFF8FA58A.toInt())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(c, "crave", 210))
        NotificationManagerCompat.from(c).notify(211, b.build())
    }
}

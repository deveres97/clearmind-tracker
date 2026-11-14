package com.clearmind.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * All the times ClearMind reaches out. Each alarm reschedules itself for the
 * next day when it fires, so nothing depends on the app being opened.
 */
object Scheduler {
    const val KIND = "kind"
    const val KIND_RISK = "risk"       // a few minutes before a risky hour
    const val KIND_MORNING = "morning"
    const val KIND_EVENING = "evening"
    const val KIND_WEEKLY = "weekly"
    const val KIND_CRAVE_END = "crave_end"
    const val KIND_CLOCK = "clock"     // keeps the ongoing clock fresh

    private const val RC_MORNING = 900
    private const val RC_EVENING = 901
    private const val RC_WEEKLY = 902
    private const val RC_CRAVE = 903
    private const val RC_CLOCK = 904
    private const val RC_RISK_BASE = 1000

    private fun am(c: Context) = c.getSystemService(AlarmManager::class.java)

    private fun pi(c: Context, kind: String, rc: Int, extra: String = ""): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).apply {
            putExtra(KIND, kind)
            putExtra("extra", extra)
            // Identity is kind + request code only — never the extra. Two PendingIntents
            // match when their intents are equal, and extras are ignored in that test
            // while data is not. Keeping the time out of the URI means a risky-hour
            // alarm can still be cancelled after the user changes that time.
            data = android.net.Uri.parse("clearmind://$kind/$rc")
        }
        return PendingIntent.getBroadcast(c, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Next occurrence of hh:mm, today if still ahead, else tomorrow. */
    private fun nextAt(hh: Int, mm: Int, dayOfWeek: Int? = null): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hh); set(Calendar.MINUTE, mm)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (dayOfWeek != null) {
            while (cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek || cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun set(c: Context, at: Long, p: PendingIntent) {
        val a = am(c) ?: return
        try {
            // Exact where allowed; inexact is fine for a nudge and never gets denied.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !a.canScheduleExactAlarms()) {
                a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            } else {
                a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            }
        } catch (e: SecurityException) {
            a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    /** Re-plan everything. Safe to call as often as you like. */
    fun syncAll(c: Context) {
        cancelAll(c)
        Notifier.showClock(c)
        ClockWidget.refresh(c)
        if (!Store.running(c)) return

        // Risky times: fire 10 minutes early so there is time to act.
        Store.risk(c).forEachIndexed { idx, hhmm ->
            val parts = hhmm.split(":")
            if (parts.size == 2) {
                val hh = parts[0].toIntOrNull() ?: return@forEachIndexed
                val mm = parts[1].toIntOrNull() ?: return@forEachIndexed
                var total = hh * 60 + mm - 10
                if (total < 0) total += 24 * 60
                set(c, nextAt(total / 60, total % 60), pi(c, KIND_RISK, RC_RISK_BASE + idx, hhmm))
            }
        }
        set(c, nextAt(8, 30), pi(c, KIND_MORNING, RC_MORNING))
        set(c, nextAt(21, 30), pi(c, KIND_EVENING, RC_EVENING))
        if (Store.personUpdates(c)) set(c, nextAt(18, 0, Calendar.SUNDAY), pi(c, KIND_WEEKLY, RC_WEEKLY))
        // Refresh the ongoing clock and widget a few times a day.
        set(c, System.currentTimeMillis() + 6 * 3600_000L, pi(c, KIND_CLOCK, RC_CLOCK))
    }

    fun cancelAll(c: Context) {
        val a = am(c) ?: return
        a.cancel(pi(c, KIND_MORNING, RC_MORNING))
        a.cancel(pi(c, KIND_EVENING, RC_EVENING))
        a.cancel(pi(c, KIND_WEEKLY, RC_WEEKLY))
        a.cancel(pi(c, KIND_CLOCK, RC_CLOCK))
        // Cancel generously: more slots than the UI can ever offer, so times the
        // user has since removed cannot keep firing.
        for (i in 0 until 24) a.cancel(pi(c, KIND_RISK, RC_RISK_BASE + i))
    }

    fun craveStart(c: Context, endAt: Long) {
        Notifier.showCrave(c, endAt)
        set(c, endAt, pi(c, KIND_CRAVE_END, RC_CRAVE))
    }

    fun craveCancel(c: Context) {
        am(c)?.cancel(pi(c, KIND_CRAVE_END, RC_CRAVE))
        Notifier.cancelCrave(c)
    }

    /** Called by AlarmReceiver after it has handled a firing. */
    fun rescheduleOne(c: Context, kind: String, extra: String) {
        when (kind) {
            KIND_RISK -> {
                val idx = Store.risk(c).indexOf(extra)
                val parts = extra.split(":")
                val hh = parts.getOrNull(0)?.toIntOrNull()
                val mm = parts.getOrNull(1)?.toIntOrNull()
                if (idx >= 0 && hh != null && mm != null) {
                    var total = hh * 60 + mm - 10
                    if (total < 0) total += 24 * 60
                    set(c, nextAt(total / 60, total % 60), pi(c, KIND_RISK, RC_RISK_BASE + idx, extra))
                }
            }
            KIND_MORNING -> set(c, nextAt(8, 30), pi(c, KIND_MORNING, RC_MORNING))
            KIND_EVENING -> set(c, nextAt(21, 30), pi(c, KIND_EVENING, RC_EVENING))
            KIND_WEEKLY -> set(c, nextAt(18, 0, Calendar.SUNDAY), pi(c, KIND_WEEKLY, RC_WEEKLY))
            KIND_CLOCK -> set(c, System.currentTimeMillis() + 6 * 3600_000L, pi(c, KIND_CLOCK, RC_CLOCK))
        }
    }
}

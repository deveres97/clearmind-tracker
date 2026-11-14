package com.clearmind.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * What the app actually says when it reaches out. The wording matters more than
 * the plumbing: a nudge that reads like a nag gets swiped away and then ignored.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(c: Context, i: Intent) {
        val kind = i.getStringExtra(Scheduler.KIND) ?: return
        val extra = i.getStringExtra("extra") ?: ""
        Scheduler.rescheduleOne(c, kind, extra)
        Notifier.showClock(c)
        ClockWidget.refresh(c)

        if (kind == Scheduler.KIND_CRAVE_END) { Notifier.craveDone(c); return }
        if (!Store.running(c)) return

        val name = Store.name(c)
        val day = Store.tbDay(c)
        val days = Store.tbDays(c)
        val reason = Store.reason(c)

        when (kind) {
            Scheduler.KIND_RISK -> {
                val lines = listOf(
                    "This is usually when you'd light up. Decide now what you're doing instead.",
                    "Your risky hour starts in 10 minutes. Get ahead of it: step outside, make chai, start something.",
                    "Heads up — the craving is about to show up on schedule. It's expected, and it passes.",
                    if (reason.isNotEmpty()) "You said you wanted this: “$reason”. The next hour is where that gets decided." else "The next hour is the one that counts today."
                )
                Notifier.nudge(c, "Day $day — risky hour coming", lines[day % lines.size], id = 400 + (extra.hashCode() and 0xFF))
            }
            Scheduler.KIND_MORNING -> {
                val wins = Store.wins(c)
                val body = when {
                    day <= 1 -> "Day one. Today the only job is to not start. Everything else can wait."
                    day <= 3 -> "Day $day of $days. This is the hardest stretch — withdrawal peaks around now and then eases. Eat properly and get outside."
                    day <= 7 -> "Day $day of $days. Nearly through the worst week. Sleep gets better from here."
                    wins > 0 -> "Day $day of $days. You've beaten $wins cravings so far. Each one makes the next weaker."
                    else -> "Day $day of $days. Clean head, fresh start."
                }
                Notifier.nudge(c, if (name.isNotEmpty()) "Morning, $name" else "Morning", body, withCraveButton = false, id = 300)
            }
            Scheduler.KIND_EVENING -> Notifier.eveningCheck(c)
            Scheduler.KIND_WEEKLY -> {
                val who = Store.personName(c).ifEmpty { "your person" }
                Notifier.nudge(
                    c, "Sunday — send $who your update",
                    "Day $day, ${Store.cleanDays(c)} clean days, ${Store.wins(c)} cravings beaten. Tap to send it.",
                    withCraveButton = false, id = 320
                )
            }
        }
    }
}

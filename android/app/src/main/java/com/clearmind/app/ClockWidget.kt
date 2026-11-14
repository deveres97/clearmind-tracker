package com.clearmind.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

/**
 * Home-screen widget: the clean-time clock counting up, and the craving button
 * right there. The point is that it is visible without opening anything.
 */
class ClockWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(c, mgr, it) }
    }

    companion object {
        fun refresh(c: Context) {
            val mgr = AppWidgetManager.getInstance(c) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(c, ClockWidget::class.java)) ?: return
            ids.forEach { render(c, mgr, it) }
        }

        private fun render(c: Context, mgr: AppWidgetManager, id: Int) {
            val v = RemoteViews(c.packageName, R.layout.widget_clock)
            val running = Store.running(c)
            val last = Store.lastUse(c)

            if (running && last > 0) {
                // Chronometer counts up from the last session, live, with no updates needed.
                v.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime() - (System.currentTimeMillis() - last), null, true)
                v.setViewVisibility(R.id.w_chrono, View.VISIBLE)
                v.setViewVisibility(R.id.w_big, View.GONE)
                v.setTextViewText(R.id.w_label, "CLEAN FOR")
                val day = Store.tbDay(c)
                v.setTextViewText(
                    R.id.w_sub,
                    if (Store.tbOpen(c)) "Day $day" else "Day $day of ${Store.tbDays(c)}"
                )
            } else {
                v.setViewVisibility(R.id.w_chrono, View.GONE)
                v.setViewVisibility(R.id.w_big, View.VISIBLE)
                v.setTextViewText(R.id.w_label, "CLEARMIND")
                v.setTextViewText(R.id.w_big, if (Store.onboarded(c)) "Ready" else "Set up")
                v.setTextViewText(R.id.w_sub, if (Store.onboarded(c)) "Start a T-break" else "Tap to begin")
            }

            v.setOnClickPendingIntent(R.id.w_root, open(c, "home", 500 + id))
            v.setOnClickPendingIntent(R.id.w_btn, open(c, "crave", 600 + id))
            mgr.updateAppWidget(id, v)
        }

        private fun open(c: Context, dest: String, rc: Int): PendingIntent {
            val i = Intent(c, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("dest", dest)
                data = android.net.Uri.parse("clearmind://widget/$dest/$rc")
            }
            return PendingIntent.getActivity(c, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

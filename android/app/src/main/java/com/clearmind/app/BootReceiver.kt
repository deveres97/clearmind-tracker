package com.clearmind.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms die on reboot, so put them all back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Notifier.ensureChannels(c)
        Scheduler.syncAll(c)
    }
}

package com.chenyuan.baogang

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("baogang", Context.MODE_PRIVATE)
            val running = prefs.getBoolean("running", false)
            if (running) {
                val interval = prefs.getInt("intervalMin", 30)
                val auto = prefs.getBoolean("isAuto", true)
                AlarmScheduler.schedule(context, AlarmScheduler.nextTarget(interval, auto))
            }
        }
    }
}

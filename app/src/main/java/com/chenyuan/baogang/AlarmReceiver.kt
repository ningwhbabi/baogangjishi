package com.chenyuan.baogang

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 到点：拉起全屏报警界面（锁屏也能显示并响铃）
        AlarmActivity.start(context)
    }
}

package com.chenyuan.baogang

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * AlarmManager 到点后唤醒此广播，再由它拉起前台服务 AlarmService。
 * 用「广播 + 前台服务 + 全屏通知」链路，绕过小米等 ROM 对「后台直接 startActivity」的拦截。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmService.start(context)
    }
}

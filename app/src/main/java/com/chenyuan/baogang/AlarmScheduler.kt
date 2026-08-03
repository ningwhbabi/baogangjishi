package com.chenyuan.baogang

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import java.util.TimeZone

object AlarmScheduler {
    private const val ACTION = "com.chenyuan.baogang.ALARM"

    /** 北京时间（Asia/Shanghai）当前毫秒 */
    fun nowBejing(): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).timeInMillis

    /**
     * 计算下一个报岗目标时刻（对齐墙钟刻度，自动模式提前 1 分钟）。
     * Nmin=30 -> 整点/半点；Nmin=15 -> 整点/15/30/45 分；自定义 N -> 每 N 分。
     */
    fun nextTarget(intervalMin: Int, isAuto: Boolean): Long {
        val now = nowBejing()
        val Nms = intervalMin * 60_000L
        val base = (now / Nms) * Nms
        var next = base + Nms
        val lead = if (isAuto) 60_000L else 0L
        var tgt = next - lead
        while (tgt - now < 3000) {
            next += Nms
            tgt = next - lead
        }
        return tgt
    }

    fun schedule(context: Context, targetMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // 无精确闹钟权限时退化为"尽量精确"，仍可响（可能略有偏差）
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMs, pi)
            return
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMs, pi)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent {
        // AlarmManager 唤醒广播接收器，再由接收器拉起前台服务（播放声音+震动）
        // 并通过「全屏通知」弹出 AlarmActivity，锁屏/后台/切应用都能可靠触发。
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

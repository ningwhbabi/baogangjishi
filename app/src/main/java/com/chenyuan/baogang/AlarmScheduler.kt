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
        // 关键：Android 10+ 禁止后台应用直接 startActivity，
        // 因此 PendingIntent 必须直接指向 AlarmActivity（由 AlarmManager 触发），
        // 这样锁屏/后台也能可靠全屏弹出并响铃。
        val intent = Intent(context, AlarmActivity::class.java).setAction(ACTION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

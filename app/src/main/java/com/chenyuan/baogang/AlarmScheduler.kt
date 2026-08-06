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
     * 计算下一个报岗目标时刻（对齐北京时间墙钟刻度，自动模式提前 1 分钟）。
     * 以「北京时间当天 00:00」为基准格点，无论 N 取何值都严格对齐北京时间整点体系：
     *   N=30 -> 整点/半点；N=15 -> 整点/15/30/45 分；自定义 N -> 每 N 分（从零点起算）。
     */
    fun nextTarget(intervalMin: Int, isAuto: Boolean): Long {
        val now = nowBejing()
        val Nms = intervalMin * 60_000L
        // 对齐到「北京时间当天 00:00」的毫秒，保证格点基于北京时间而非 UTC
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dayStart = cal.timeInMillis
        var tgt = dayStart + ((now - dayStart) / Nms + 1) * Nms
        val lead = if (isAuto) 60_000L else 0L
        tgt -= lead
        while (tgt - now < 3000) tgt += Nms
        return tgt
    }

    /**
     * 排程报岗闹钟。
     * 优先使用 AlarmManager.setAlarmClock()——这是安卓最高优先级闹钟，
     * Doze 省电机制【无法】延迟它，可保证长时间后台/息屏后依然准时触发
     * （专门解决「越往后越晚」的问题）。无需用户授予「精确闹钟」权限。
     */
    fun schedule(context: Context, targetMs: Long) {
        // 持久化当前这一轮的目标时刻，供主界面读取展示
        // 修复「报警后倒计时卡 00:00」：报警界面排下一轮时主界面内存目标未同步
        context.getSharedPreferences("baogang", Context.MODE_PRIVATE)
            .edit().putLong("targetMs", targetMs).apply()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context)
        // 状态栏展示 intent：点击状态栏的闹钟提示可跳到「停止提醒」界面
        val showPi = PendingIntent.getActivity(
            context, 1,
            Intent(context, AlarmActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 最高优先级，Doze 也无法延迟——用于保证报岗准时
            am.setAlarmClock(AlarmManager.AlarmClockInfo(targetMs, showPi), pi)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // 老系统/无精确权限时退化为「尽量精确」，仍可响（可能略有偏差）
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMs, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMs, pi)
        }
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

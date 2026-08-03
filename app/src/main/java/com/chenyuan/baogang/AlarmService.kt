package com.chenyuan.baogang

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

/**
 * 前台服务：到点后由 AlarmReceiver 启动。
 * 负责：① 播放循环响铃（USAGE_ALARM，走系统闹钟音量）；② 震动；
 * ③ 发一条高优先级「全屏通知」(fullScreenIntent)，锁屏/后台也能弹出 AlarmActivity。
 * 前台服务能在小米等 ROM 的后台管控下存活，保证切应用、息屏都照常提醒。
 */
class AlarmService : Service() {

    companion object {
        const val CHANNEL_ID = "baogang_alarm"
        const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AlarmService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }

    @Volatile
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            startForeground(NOTIF_ID, buildNotification())
            Sound.startLoop()   // 循环响铃（系统闹钟音量，静音也响）
            vibrate()            // 持续震动
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "报岗闹钟", NotificationManager.IMPORTANCE_HIGH
                )
                ch.description = "报岗倒计时到点提醒"
                ch.setBypassDnd(true)                       // 绕过勿扰
                ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, AlarmActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("到时间报岗！")
            .setContentText("请按要求上报岗")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pi, true)   // 锁屏/后台全屏弹出
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun vibrate() {
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 0 表示无限循环，直到 stop
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), 0))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 400, 200, 400, 200, 400), 0)
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        running = false
        Sound.stopLoop()
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}

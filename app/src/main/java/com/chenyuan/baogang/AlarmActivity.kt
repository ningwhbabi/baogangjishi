package com.chenyuan.baogang

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AlarmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 锁屏也能显示并点亮屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alarm)

        // 继续排下一轮（基于已保存的设置）
        val prefs = getSharedPreferences("baogang", MODE_PRIVATE)
        val running = prefs.getBoolean("running", false)
        if (running) {
            val interval = prefs.getInt("intervalMin", 30)
            val auto = prefs.getBoolean("isAuto", true)
            AlarmScheduler.schedule(this, AlarmScheduler.nextTarget(interval, auto))
        }

        // 声音与震动由 AlarmService（前台服务）负责，锁屏/后台都可靠

        findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            AlarmService.stop(this)   // 停止响铃 + 关通知 + 停服务
            finish()
        }
    }

    override fun onDestroy() {
        // 任何方式关闭界面都确保停止响铃（幂等）
        AlarmService.stop(this)
        // 通知主界面重新武装前台兜底，确保下一轮即使系统闹钟被延迟也能兜底弹出
        try { sendBroadcast(android.content.Intent(MainActivity.REARM_ACTION)) } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, AlarmActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        }
    }
}

package com.chenyuan.baogang

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
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

        // 震动 + 循环响铃
        vibrate(this)
        Sound.startLoop()

        findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            Sound.stopLoop()
            finish()
        }
    }

    override fun onDestroy() {
        Sound.stopLoop()
        super.onDestroy()
    }

    private fun vibrate(context: Context) {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 400, 200, 400, 200, 400), -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 400, 200, 400, 200, 400), -1)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, AlarmActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        }
    }
}

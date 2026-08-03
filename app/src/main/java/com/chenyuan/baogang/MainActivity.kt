package com.chenyuan.baogang

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private val PREFS = "baogang"

    private var intervalMin = 30
    private var isAuto = true
    private var running = false
    private var targetMs = 0L
    private var armed = true   // 当前这一轮是否已触发（防止重复拉起报警界面）

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 250)
        }
    }

    private lateinit var tvClock: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etCustom: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvClock = findViewById(R.id.tvClock)
        tvTime = findViewById(R.id.tvTime)
        tvStatus = findViewById(R.id.tvStatus)
        etCustom = findViewById(R.id.etCustom)

        findViewById<Button>(R.id.btnDay).setOnClickListener { selectMode(30, true); startRun() }
        findViewById<Button>(R.id.btnNight).setOnClickListener { selectMode(15, true); startRun() }
        findViewById<Button>(R.id.btnCustom).setOnClickListener {
            val v = etCustom.text.toString().toIntOrNull()
            if (v == null || v < 1) {
                Toast.makeText(this, "请输入有效分钟数", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectMode(v, false)
            startRun()
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startRun() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { Sound.playBeep(3) }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopRun() }
        findViewById<Button>(R.id.btnReset).setOnClickListener { startRun() }

        loadPrefs()
        handler.post(tick)
    }

    private fun selectMode(min: Int, auto: Boolean) {
        intervalMin = min
        isAuto = auto
    }

    private fun startRun() {
        running = true
        armed = true
        savePrefs()
        targetMs = AlarmScheduler.nextTarget(intervalMin, isAuto)
        AlarmScheduler.schedule(this, targetMs)
        ensureExactAlarmPermission()
        updateUI()
    }

    private fun stopRun() {
        running = false
        armed = false
        AlarmScheduler.cancel(this)
        savePrefs()
        updateUI()
    }

    private fun updateUI() {
        val now = AlarmScheduler.nowBejing()
        tvClock.text = "北京时间 " + fmtClock(now)

        val tgt = if (running) targetMs else AlarmScheduler.nextTarget(intervalMin, isAuto)
        val remain = maxOf(0L, tgt - now) / 1000
        tvTime.text = String.format("%02d:%02d", remain / 60, remain % 60)

        if (running) {
            tvStatus.text = "下次提醒：" + fmtTime(tgt) + if (isAuto) "（提前1分钟）" else ""
            if (remain <= 0 && armed) {
                armed = false
                AlarmScheduler.schedule(this, AlarmScheduler.nextTarget(intervalMin, isAuto))
                targetMs = AlarmScheduler.nextTarget(intervalMin, isAuto)
                AlarmActivity.start(this)
            }
        } else {
            tvStatus.text = "距下次提醒 " + fmtTime(tgt) + if (isAuto) "（提前1分钟）" else "" + "（点开始计时）"
        }
    }

    private fun ensureExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().apply {
            putInt("intervalMin", intervalMin)
            putBoolean("isAuto", isAuto)
            putBoolean("running", running)
            apply()
        }
    }

    private fun loadPrefs() {
        val p = getSharedPreferences(PREFS, MODE_PRIVATE)
        intervalMin = p.getInt("intervalMin", 30)
        isAuto = p.getBoolean("isAuto", true)
        running = p.getBoolean("running", false)
        if (running) {
            targetMs = AlarmScheduler.nextTarget(intervalMin, isAuto)
            AlarmScheduler.schedule(this, targetMs)
            ensureExactAlarmPermission()
        }
    }

    private fun fmtClock(ms: Long): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        c.timeInMillis = ms
        return String.format(
            "%02d:%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)
        )
    }

    private fun fmtTime(ms: Long): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        c.timeInMillis = ms
        return String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}

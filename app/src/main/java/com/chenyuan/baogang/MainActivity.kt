package com.chenyuan.baogang

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    // 选择本地音频作为铃声
    private val pickRingtone = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            // 持久化权限，重启 App 后仍能播放该文件
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        Sound.setRingtoneUri(this, uri)
        Toast.makeText(this, "已设为铃声，试听中…", Toast.LENGTH_SHORT).show()
        Sound.testRingtone(this) // 立刻试听
    }

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
        findViewById<Button>(R.id.btnTest).setOnClickListener { Sound.testRingtone(this) }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopRun() }
        findViewById<Button>(R.id.btnReset).setOnClickListener { startRun() }
        findViewById<Button>(R.id.btnPickRing).setOnClickListener {
            // 选音频文件作为铃声（mp3/m4a/wav 等均可）
            pickRingtone.launch(arrayOf("audio/*"))
        }
        findViewById<Button>(R.id.btnDefaultRing).setOnClickListener {
            Sound.setRingtoneUri(this, null)
            Toast.makeText(this, "已恢复默认蜂鸣声", Toast.LENGTH_SHORT).show()
            Sound.testRingtone(this)
        }

        loadPrefs()
        handler.post(tick)
        ensureNotifyPermission()
        ensureBatteryOptimization()
    }

    private fun ensureNotifyPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    private fun ensureBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    i.data = android.net.Uri.parse("package:$packageName")
                    startActivity(i)
                } catch (_: Exception) {
                }
            }
        }
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
                // 前台也走前台服务，确保声音/震动/全屏通知都可靠触发
                AlarmService.start(this)
                AlarmActivity.start(this)
            }
        } else {
            val lead = if (isAuto) "（提前1分钟）" else ""
            tvStatus.text = "距下次提醒 " + fmtTime(tgt) + lead + "（点开始计时）"
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

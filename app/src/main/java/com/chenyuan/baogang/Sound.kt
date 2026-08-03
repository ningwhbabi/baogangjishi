package com.chenyuan.baogang

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 报警发声。
 * - 默认：用 AudioTrack 实时合成双音蜂鸣（无需音频文件），走 USAGE_ALARM 通道，手机静音/勿扰也不压。
 * - 自定义：若用户在 APP 里选过铃声（保存其 Uri），则循环播放该音频文件，同样走 USAGE_ALARM 通道。
 */
object Sound {
    private const val PREF_RINGTONE = "ringtone_uri"

    @Volatile private var looping = false
    private var scheduler = Executors.newSingleThreadScheduledExecutor()
    private var mediaPlayer: MediaPlayer? = null

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("baogang", Context.MODE_PRIVATE)

    /** 读取用户选择的铃声 Uri（未选返回 null -> 用合成音） */
    fun getRingtoneUri(ctx: Context): Uri? {
        val s = prefs(ctx).getString(PREF_RINGTONE, null)
        return if (s.isNullOrEmpty()) null else Uri.parse(s)
    }

    /** 保存/清除用户选择的铃声 Uri */
    fun setRingtoneUri(ctx: Context, uri: Uri?) {
        prefs(ctx).edit().putString(PREF_RINGTONE, uri?.toString()).apply()
    }

    private fun alarmAttrs(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun makeBeep(durSec: Double): ShortArray {
        val sr = 44100
        val n = (sr * durSec).toInt()
        val buf = ShortArray(n)
        val freqs = doubleArrayOf(988.0, 1319.0) // 偏高更醒目
        for (i in 0 until n) {
            val t = i / sr.toDouble()
            var s = 0.0
            for (f in freqs) s += Math.sin(2.0 * Math.PI * f * t)
            s /= freqs.size
            val env = Math.min(1.0, Math.min(t, durSec - t) * 20.0)
            val v = (s * 0.5 * 32767 * env).toInt()
            buf[i] = v.toShort()
        }
        return buf
    }

    private fun playBuffer(buf: ShortArray) {
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(alarmAttrs())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(Math.max(minBuf, buf.size * 2))
                .build()
            track.play()
            track.write(buf, 0, buf.size)
            Thread.sleep((buf.size / 44100.0 * 1000).toLong() + 60)
            track.stop()
            track.release()
        } catch (_: Exception) {
        }
    }

    /** 测试响铃：有自定义铃声就播一次铃声，否则合成音 */
    fun testRingtone(ctx: Context) {
        val uri = getRingtoneUri(ctx)
        if (uri != null) {
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(alarmAttrs())
                mp.setDataSource(ctx, uri)
                mp.setOnCompletionListener { it.release() }
                mp.prepare()
                mp.start()
                return
            } catch (_: Exception) {
            }
        }
        playBeep(3)
    }

    /** 点「测试响铃」用：立即响 times 声（合成音） */
    fun playBeep(times: Int = 1) {
        val buf = makeBeep(0.18)
        Thread {
            repeat(times) {
                playBuffer(buf)
                Thread.sleep(300)
            }
        }.start()
    }

    /** 报警时循环响：优先播用户铃声，失败回退合成音 */
    fun startLoop(ctx: Context) {
        if (looping) return
        looping = true

        val uri = getRingtoneUri(ctx)
        if (uri != null) {
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(alarmAttrs())
                mp.setDataSource(ctx, uri)
                mp.isLooping = true
                mp.setOnPreparedListener { it.start() }
                mp.setOnErrorListener { mp2, _, _ -> mp2.release(); true }
                mp.prepareAsync()
                mediaPlayer = mp
                return
            } catch (_: Exception) {
                mediaPlayer = null
            }
        }

        // 回退到合成音
        if (scheduler.isShutdown) {
            scheduler = Executors.newSingleThreadScheduledExecutor()
        }
        val buf = makeBeep(0.18)
        scheduler.scheduleAtFixedRate({
            if (!looping) return@scheduleAtFixedRate
            playBuffer(buf)
        }, 0, 900, TimeUnit.MILLISECONDS)
    }

    fun stopLoop() {
        looping = false
        scheduler.shutdownNow()
        mediaPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        mediaPlayer = null
    }
}

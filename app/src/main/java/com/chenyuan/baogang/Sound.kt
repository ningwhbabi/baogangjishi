package com.chenyuan.baogang

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 用 AudioTrack 实时合成双音蜂鸣（无需音频文件）。
 * 使用 USAGE_ALARM：走系统"闹钟"音量通道，手机静音/勿扰也不会被压掉。
 */
object Sound {
    @Volatile private var looping = false
    private var scheduler = Executors.newSingleThreadScheduledExecutor()

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
            // 简易包络，避免爆音
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
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
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

    /** 点「测试响铃」用：立即响 times 声 */
    fun playBeep(times: Int = 1) {
        val buf = makeBeep(0.18)
        Thread {
            repeat(times) {
                playBuffer(buf)
                Thread.sleep(300)
            }
        }.start()
    }

    /** 报警时循环响 */
    fun startLoop() {
        if (looping) return
        looping = true
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
    }
}

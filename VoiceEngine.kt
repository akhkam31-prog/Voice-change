package com.example.voicechanger

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process

class VoiceEngine(private val settings: VoiceSettings) {

    private val sampleRate = 44100
    @Volatile private var running = false
    private var worker: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true

        val inMin = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val outMin = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (inMin <= 0 || outMin <= 0) return false

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(inMin, 4096)
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(outMin, 4096))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        running = true
        recorder.startRecording()
        track.play()

        worker = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val block = 256
            val inBuf = ShortArray(block)
            val outBuf = ShortArray(block)
            val chain = EffectChain(sampleRate.toDouble())

            while (running) {
                val n = recorder.read(inBuf, 0, block)
                if (n <= 0) continue
                chain.update(settings)
                for (i in 0 until n) {
                    val y = chain.process(inBuf[i] / 32768.0)
                    outBuf[i] = (y.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
                }
                track.write(outBuf, 0, n)
            }

            recorder.stop()
            recorder.release()
            track.stop()
            track.release()
        }.apply {
            name = "voice-worker"
            start()
        }
        return true
    }

    fun stop() {
        running = false
        worker?.join(500)
        worker = null
    }
}

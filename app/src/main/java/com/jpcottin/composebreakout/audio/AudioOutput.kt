package com.jpcottin.composebreakout.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.jpcottin.composebreakout.engine.Synth
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Pushes the [Synth] to the speakers: a low-latency float AudioTrack at the
 * device's native rate, fed in small blocks by a dedicated thread. [pause]
 * while the activity is not resumed; [release] from the owner's teardown.
 */
class AudioOutput {
    val sampleRate: Int = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: 48000
    val synth = Synth(sampleRate)

    private val lock = ReentrantLock()
    private val wake = lock.newCondition()
    @Volatile private var paused = true
    @Volatile private var released = false
    private val thread = Thread(::run, "breakout-audio").apply { isDaemon = true; start() }

    fun resume() = lock.withLock { paused = false; wake.signalAll() }
    fun pause() = lock.withLock { paused = true; wake.signalAll() }
    fun release() = lock.withLock { released = true; wake.signalAll() }

    private fun run() {
        val track = try { open() } catch (e: Exception) { Log.w(TAG, "No audio output: $e"); return }
        val buf = FloatArray(BLOCK)
        var writeErrors = 0
        try {
            while (!released) {
                lock.withLock {
                    while (paused && !released) {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
                        wake.await()
                    }
                }
                if (released) break
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                synth.render(buf, BLOCK)
                val written = track.write(buf, 0, BLOCK, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    // An error returns at once (a dead track after an audio
                    // server restart): pace the loop, and give the track up.
                    if (++writeErrors >= MAX_WRITE_ERRORS) { Log.w(TAG, "AudioTrack write keeps failing ($written); giving up"); return }
                    Thread.sleep(BLOCK * 1000L / sampleRate)
                } else writeErrors = 0
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun open(): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBytes, BLOCK * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        check(track.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack not initialized" }
        Log.i(TAG, "AudioTrack: $sampleRate Hz, buffer ${track.bufferSizeInFrames} frames")
        return track
    }

    companion object {
        private const val TAG = "AudioOutput"
        /** Frames per render: about 5 ms at 48 kHz. */
        private const val BLOCK = 256
        private const val MAX_WRITE_ERRORS = 200   // about a second of blocks
    }
}

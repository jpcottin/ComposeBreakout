package com.jpcottin.composebreakout.engine

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Every sound in the game, synthesised in real time: there are no audio
 * files. Pure Kotlin, so it is tested on the JVM; the app feeds its output
 * to an AudioTrack.
 *
 * Two threads touch it: the game thread triggers effects ([trigger],
 * [brickBreak], [musicEnabled]) and the audio thread renders ([render]).
 * Triggers are a bit mask and a pitch the audio thread drains at the start
 * of every block, so the game never waits on audio.
 */
class Synth(sampleRate: Int) {

    private val dt = 1f / sampleRate

    private enum class Kind { PADDLE, WALL, HIT, BREAK, LOST, CLEAR, POWERUP, LIFE }

    private class Voice {
        var kind = Kind.PADDLE
        var t = 0f
        var phase = 0f
        var freq = 0f      // BREAK: pitch chosen at trigger time
        var active = false
    }

    private val voices = Array(VOICES) { Voice() }
    private val pending = AtomicInteger(0)
    @Volatile private var breakPending = 0f
    /** Whether the ambient track plays. Set from the game thread. */
    @Volatile var musicEnabled = false
    private var rng = 0xDEADBEEF.toInt()

    // ── Background music state (audio thread only) ───────────────────────────
    // 92 BPM ambient track in C major: pad + bass + arpeggio. Wrapping phases
    // instead of an ever-growing time accumulator: a float that keeps
    // counting seconds loses the per-sample step after ~8 minutes.
    private var tremPhase = 0f             // [0,1) at 0.25 Hz
    private var barPhase = 0f              // [0,4) beats
    private val padPhase = FloatArray(3)
    private var bassPhase = 0f
    private var bassEnv = 0f
    private var arpPhase = 0f
    private var arpEnv = 0.55f             // start audible on step 0 immediately
    private var arpTimer = 0f
    private var arpStep = 0
    private var lastBeat = -1

    // ── Game thread ───────────────────────────────────────────────────────────

    fun trigger(event: SoundEvent) {
        val bit = when (event) {
            SoundEvent.PADDLE -> 1
            SoundEvent.WALL -> 2
            SoundEvent.BRICK_HIT -> 4
            SoundEvent.BALL_LOST -> 8
            SoundEvent.LEVEL_CLEAR -> 16
            SoundEvent.POWER_UP -> 32
            SoundEvent.EXTRA_LIFE -> 64
        }
        pending.getAndUpdate { it or bit }
    }

    /** A brick shattering, pitched by the caller (the row). */
    fun brickBreak(hz: Float) {
        if (hz > 0f) breakPending = hz
    }

    // ── Audio thread ──────────────────────────────────────────────────────────

    /** Fills [out] with [frames] mono samples in -1..1. */
    fun render(out: FloatArray, frames: Int) {
        val mask = pending.getAndSet(0)
        if (mask and 1 != 0) activate(Kind.PADDLE)
        if (mask and 2 != 0) activate(Kind.WALL)
        if (mask and 4 != 0) activate(Kind.HIT)
        if (mask and 8 != 0) activate(Kind.LOST)
        if (mask and 16 != 0) activate(Kind.CLEAR)
        if (mask and 32 != 0) activate(Kind.POWERUP)
        if (mask and 64 != 0) activate(Kind.LIFE)
        val hz = breakPending
        if (hz > 0f) { breakPending = 0f; activate(Kind.BREAK, hz) }
        val music = musicEnabled
        for (i in 0 until frames) {
            var s = 0f
            for (v in voices) if (v.active) s += genVoice(v)
            if (music) s += genMusic()
            // Soft clip: stacked breaks and blips compress instead of turning
            // into a square wave.
            out[i] = tanh(s)
        }
    }

    /** True while any effect is sounding (tests, and the output can idle). */
    val anyVoiceActive: Boolean get() = voices.any { it.active }

    private fun activate(kind: Kind, freq: Float = 0f) {
        val v = voices.firstOrNull { !it.active } ?: voices[0]
        v.kind = kind; v.t = 0f; v.phase = 0f; v.freq = freq; v.active = true
    }

    private fun white(): Float {
        var x = rng
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        rng = x
        return (x and 0xFFFF).toFloat() / 32768f - 1f
    }

    /** Advance a wrapping oscillator phase and return the sine at it. */
    private fun osc(v: Voice, freq: Float): Float {
        v.phase += dt * freq
        if (v.phase >= 1f) v.phase -= 1f
        return sin(v.phase * TAU)
    }

    private fun genVoice(v: Voice): Float {
        var s = 0f
        when (v.kind) {
            Kind.PADDLE -> {
                // Short bright blip, pitch dipping as it decays.
                val dur = 0.07f
                if (v.t > dur) { v.active = false } else {
                    val prog = v.t / dur
                    s = osc(v, 560f - 140f * prog) * (1f - prog) * 0.38f
                }
            }
            Kind.WALL -> {
                // Dull low tick: a square wave with a very fast decay.
                val dur = 0.045f
                if (v.t > dur) { v.active = false } else {
                    v.phase += dt * 240f
                    if (v.phase >= 1f) v.phase -= 1f
                    val sq = if (v.phase < 0.5f) 1f else -1f
                    s = sq * exp(-v.t / 0.012f) * 0.22f
                }
            }
            Kind.HIT -> {
                // A brick taking a hit and holding: a hard click with no tone.
                val dur = 0.06f
                if (v.t > dur) { v.active = false } else {
                    val click = white() * exp(-v.t / 0.006f) * 0.30f
                    val knock = osc(v, 900f) * exp(-v.t / 0.02f) * 0.22f
                    s = click + knock
                }
            }
            Kind.BREAK -> {
                // A brick shattering: pitched pop (the game picks the pitch by
                // row) over a burst of noise.
                val dur = 0.16f
                if (v.t > dur) { v.active = false } else {
                    val pop = osc(v, v.freq) * exp(-v.t / 0.045f) * 0.36f
                    val burst = white() * exp(-v.t / 0.015f) * 0.22f
                    s = pop + burst
                }
            }
            Kind.LOST -> {
                // The ball dropping out: a slide down from 320 Hz to 70 Hz.
                val dur = 0.45f
                if (v.t > dur) { v.active = false } else {
                    val prog = v.t / dur
                    val freq = 320f * (70f / 320f).pow(prog)
                    s = osc(v, freq) * (1f - prog * prog) * 0.42f
                }
            }
            Kind.CLEAR -> {
                // Rising four-note arpeggio: C5 E5 G5 C6.
                val dur = 0.64f
                val segDur = dur / 4f
                if (v.t > dur) { v.active = false } else {
                    val ni = (v.t / segDur).toInt().coerceAtMost(3)
                    val nt = v.t - ni * segDur
                    val env = sin(nt / segDur * PI_F)
                    s = osc(v, CLEAR_FREQS[ni]) * env * 0.45f
                }
            }
            Kind.POWERUP -> {
                // Rising sparkle: four quick notes over 0.28 s.
                val dur = 0.28f
                val noteDur = dur / 4f
                if (v.t > dur) { v.active = false } else {
                    val ni = (v.t / noteDur).toInt().coerceAtMost(3)
                    val nt = v.t - ni * noteDur
                    val env = sin(nt / noteDur * PI_F)
                    s = osc(v, POWERUP_FREQS[ni]) * env * 0.38f
                }
            }
            Kind.LIFE -> {
                // Extra life jingle: E5 G5 then a held C6.
                val dur = 0.55f
                if (v.t > dur) { v.active = false } else {
                    val ni = if (v.t >= LIFE_STARTS[2]) 2 else if (v.t >= LIFE_STARTS[1]) 1 else 0
                    val nt = v.t - LIFE_STARTS[ni]
                    val env = if (ni == 2) exp(-nt / 0.18f) else sin(nt / 0.11f * PI_F)
                    s = osc(v, LIFE_FREQS[ni]) * env * 0.40f
                }
            }
        }
        v.t += dt
        return s
    }

    private fun wrapOsc(phase: Float, freq: Float): Float {
        var p = phase + dt * freq
        if (p >= 1f) p -= 1f
        return p
    }

    private fun genMusic(): Float {
        var s = 0f
        // Slow tremolo LFO at 0.25 Hz
        tremPhase += dt * 0.25f
        if (tremPhase >= 1f) tremPhase -= 1f
        val tremolo = 0.72f + 0.28f * sin(tremPhase * TAU)
        // Pad chord: C3 + E3 + G3 sustained
        for (i in 0 until 3) {
            padPhase[i] = wrapOsc(padPhase[i], PAD_FREQS[i])
            s += sin(padPhase[i] * TAU) * 0.022f * tremolo
        }
        // Bass: C2 (65.4 Hz) on beats 1 and 3 of a 4-beat bar
        barPhase += dt * TEMPO
        if (barPhase >= 4f) barPhase -= 4f
        val beat = barPhase.toInt()
        if (beat != lastBeat) {
            lastBeat = beat
            if (beat == 0 || beat == 2) bassEnv = 1f
        }
        bassEnv *= 0.9989f
        bassPhase = wrapOsc(bassPhase, 65.4f)
        s += sin(bassPhase * TAU) * bassEnv * 0.065f
        // Melody arpeggio: C4 E4 G4 C5 cycling on 8th notes
        arpTimer += dt
        if (arpTimer >= ARP_INTERVAL) {
            arpTimer -= ARP_INTERVAL
            arpStep = (arpStep + 1) and 3
            arpEnv = 0.55f
        }
        arpEnv *= 0.9988f
        arpPhase = wrapOsc(arpPhase, ARP_FREQS[arpStep])
        s += sin(arpPhase * TAU) * arpEnv * 0.018f
        return s
    }

    companion object {
        const val VOICES = 8
        private const val TAU = 6.28318530f
        private const val PI_F = 3.14159265f
        private const val TEMPO = 92f / 60f                  // beats/s
        private const val ARP_INTERVAL = 60f / 92f / 2f      // 8th note
        private val PAD_FREQS = floatArrayOf(130.8f, 164.8f, 196.0f)          // C3 E3 G3
        private val ARP_FREQS = floatArrayOf(261.6f, 329.6f, 392.0f, 523.3f)  // C4 E4 G4 C5
        private val CLEAR_FREQS = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f)
        private val POWERUP_FREQS = floatArrayOf(440.0f, 554.4f, 659.3f, 880.0f)
        private val LIFE_FREQS = floatArrayOf(659.25f, 783.99f, 1046.5f)
        private val LIFE_STARTS = floatArrayOf(0.0f, 0.11f, 0.22f)
    }
}

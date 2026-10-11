package com.jpcottin.composebreakout.engine

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val RATE = 48000

private fun render(s: Synth, seconds: Float): FloatArray {
    val n = (seconds * RATE).toInt()
    val out = FloatArray(n)
    var i = 0
    while (i < n) {
        val block = minOf(256, n - i)
        val tmp = FloatArray(block)
        s.render(tmp, block)
        tmp.copyInto(out, i)
        i += block
    }
    return out
}

private fun peak(a: FloatArray, from: Int = 0, to: Int = a.size): Float {
    var p = 0f
    for (i in from until to) p = maxOf(p, abs(a[i]))
    return p
}

class SynthTest {
    @Test fun silentUntilTriggered() {
        val s = Synth(RATE)
        assertEquals(0f, peak(render(s, 0.2f)), 0f)
        assertFalse(s.anyVoiceActive)
    }

    @Test fun everyEffectSoundsAndEnds() {
        for (e in SoundEvent.entries) {
            val s = Synth(RATE)
            s.trigger(e)
            val a = render(s, 1.0f)
            assertTrue("$e is silent", peak(a, 0, RATE / 10) > 0.05f)
            assertFalse("$e never ends", s.anyVoiceActive)
            assertEquals("$e leaves a tail", 0f, peak(a, a.size - RATE / 10, a.size), 0f)
        }
    }

    @Test fun brickBreakIsPitched() {
        // The pop carries the pitch the game asked for: its energy at that
        // frequency dwarfs the energy at the other row's pitch.
        val low = 523.25f
        val high = 1046.5f
        for ((hz, other) in listOf(low to high, high to low)) {
            val s = Synth(RATE)
            s.brickBreak(hz)
            val a = render(s, 0.12f)
            val from = RATE * 20 / 1000   // past the noise burst
            val atPitch = energyAt(a, from, hz)
            val atOther = energyAt(a, from, other)
            assertTrue("$hz Hz: $atPitch vs $atOther", atPitch > 3f * atOther)
        }
    }

    /** Magnitude of the DFT at [hz] over the samples from [from] on. */
    private fun energyAt(a: FloatArray, from: Int, hz: Float): Float {
        var re = 0.0; var im = 0.0
        for (i in from until a.size) {
            val ph = 2.0 * Math.PI * hz * i / RATE
            re += a[i] * Math.cos(ph); im -= a[i] * Math.sin(ph)
        }
        return Math.hypot(re, im).toFloat()
    }

    @Test fun ignoresANonPositivePitch() {
        val s = Synth(RATE)
        s.brickBreak(0f)
        s.brickBreak(-5f)
        assertEquals(0f, peak(render(s, 0.1f)), 0f)
    }

    @Test fun outputStaysInRangeWhenEverythingStacks() {
        val s = Synth(RATE)
        for (e in SoundEvent.entries) s.trigger(e)
        s.brickBreak(880f)
        s.musicEnabled = true
        val a = render(s, 1.0f)
        assertTrue(peak(a) <= 1f)
        assertTrue(peak(a) > 0.3f)
    }

    @Test fun musicPlaysOnlyWhenEnabled() {
        val s = Synth(RATE)
        assertEquals(0f, peak(render(s, 0.5f)), 0f)
        s.musicEnabled = true
        assertTrue(peak(render(s, 0.5f)) > 0.02f)
        s.musicEnabled = false
        assertEquals(0f, peak(render(s, 0.5f)), 0f)
    }

    @Test fun musicKeepsItsLevelOverMinutes() {
        // Wrapping phases, not a growing clock: after ten minutes the track
        // is as loud as in its first bar.
        val s = Synth(RATE)
        s.musicEnabled = true
        val first = peak(render(s, 2f))
        repeat(10 * 30) { render(s, 2f) }   // ten minutes
        val later = peak(render(s, 2f))
        assertEquals(first, later, first * 0.25f)
    }

    @Test fun triggersCoalesceWithinABlock() {
        // Ten paddle hits before the next block: one voice, not ten stacked.
        val s = Synth(RATE)
        repeat(10) { s.trigger(SoundEvent.PADDLE) }
        val a = render(s, 0.05f)
        val single = Synth(RATE).also { it.trigger(SoundEvent.PADDLE) }.let { render(it, 0.05f) }
        assertEquals(peak(single), peak(a), 1e-5f)
    }

    @Test fun voicesAreCappedAndTheOldestIsStolen() {
        val s = Synth(RATE)
        repeat(Synth.VOICES + 3) { s.brickBreak(400f + it); render(s, 0.001f) }
        assertTrue(s.anyVoiceActive)
        render(s, 1f)
        assertFalse(s.anyVoiceActive)
    }
}

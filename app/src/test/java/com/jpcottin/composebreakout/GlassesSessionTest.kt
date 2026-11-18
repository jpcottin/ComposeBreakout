package com.jpcottin.composebreakout

import com.jpcottin.composebreakout.glasses.GlassesSession
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesSessionTest {
    @After
    fun tearDown() {
        GlassesSession.connectionChanged(false)
    }

    @Test
    fun glassesThatGoAwayGiveTheGameBack() {
        GlassesSession.connectionChanged(true)
        GlassesSession.setActive(true)

        GlassesSession.connectionChanged(false)

        assertFalse(GlassesSession.connected.value)
        assertFalse(GlassesSession.active.value)
    }

    @Test
    fun aConnectionReportedAgainKeepsTheSession() {
        GlassesSession.connectionChanged(true)
        GlassesSession.setActive(true)

        GlassesSession.connectionChanged(true)

        assertTrue(GlassesSession.active.value)
    }
}

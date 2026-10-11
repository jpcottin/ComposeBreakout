@file:OptIn(ExperimentalProjectedApi::class)

package com.jpcottin.composebreakout.glasses

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The AI Glasses, process-wide: whether a pair is connected, whether the
 * game is running on them right now, and the two moves — launch the game
 * onto the projected display, bring it back to the phone.
 *
 * [connected] follows androidx.xr.projected's pairing flow (API 36+; older
 * devices never report a pair). [active] is set by [GlassesActivity] for
 * its lifetime: the phone's game freezes under an "on glasses" banner while
 * it is true, and clearing it makes the glasses activity finish.
 */
object GlassesSession {
    private const val TAG = "Glasses"

    private val connectedState = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = connectedState.asStateFlow()

    private val activeState = MutableStateFlow(false)
    val active: StateFlow<Boolean> = activeState.asStateFlow()

    private var scope: CoroutineScope? = null

    /** Begin observing pairing; one observer per process. */
    fun startMonitoring(context: Context) {
        if (Build.VERSION.SDK_INT < 36 || scope != null) return
        val app = context.applicationContext
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch {
            // A transient failure of the projected-device flow must not end
            // monitoring for the process lifetime: retry with a backoff so
            // the glasses can reappear after a hiccup.
            while (isActive) {
                try {
                    ProjectedContext.isProjectedDeviceConnected(app, s.coroutineContext).collect { value ->
                        connectedState.value = value
                        Log.i(TAG, "Glasses connected: $value")
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Glasses monitoring failed, retrying in 10 s", t)
                    connectedState.value = false
                }
                delay(10_000)
            }
        }
    }

    /** Start [GlassesActivity] on the projected display. False when it could not. */
    fun launch(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < 36 || !connectedState.value) return false
        return try {
            val options = ProjectedContext.createProjectedActivityOptions(activity)
            activity.startActivity(Intent(activity, GlassesActivity::class.java), options.toBundle())
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to launch on glasses", t)
            false
        }
    }

    /** The phone asks for the game back: the glasses activity finishes. */
    fun bringBack() {
        activeState.value = false
    }

    internal fun setActive(value: Boolean) {
        activeState.value = value
    }
}

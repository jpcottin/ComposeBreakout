package com.jpcottin.composebreakout.ui

import android.content.res.Configuration
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.mediaQuery
import androidx.compose.ui.platform.LocalConfiguration

/*
 * Device traits the UI adapts to, resolved through Compose's MediaQuery API
 * (androidx.compose.ui.mediaQuery). The API is gated behind
 * ComposeUiFlags.isMediaQueryIntegrationEnabled, which BreakoutApplication
 * turns on before the first composition. When the flag is off (Android Studio
 * previews render without the Application class) each query falls back to the
 * uiMode heuristic, so previews keep working.
 */

@Composable
private fun <T> mediaQueryOr(fallback: @Composable () -> T, query: UiMediaScope.() -> T): T =
    if (ComposeUiFlags.isMediaQueryIntegrationEnabled) mediaQuery(query) else fallback()

/** True in leanback (TV) UI mode: a platform fact, used for the launcher and the D-pad hint. */
@Composable
fun isTelevision(): Boolean =
    LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION

/** The screen is viewed from across a room (TV): overscan-safe margins, larger text. */
@Composable
fun isFarViewing(): Boolean =
    mediaQueryOr({ isTelevision() }) { viewingDistance == UiMediaScope.ViewingDistance.Far }

/**
 * No pointing device at all (TV remote, keyboard-only desktop, XR without hand
 * tracking): focus is the only cursor, and the D-pad steers the paddle.
 */
@Composable
fun hasNoPointer(): Boolean =
    mediaQueryOr({ isTelevision() }) { pointerPrecision == UiMediaScope.PointerPrecision.None }

/** Foldable half-opened with the hinge across the middle, like a laptop. */
@Composable
fun isTabletopPosture(): Boolean =
    mediaQueryOr({ false }) { windowPosture == UiMediaScope.Posture.Tabletop }

/** Logs the resolved traits whenever they change; handy when reading emulator logcat in CI. */
@Composable
fun LogDeviceTraits() {
    if (!ComposeUiFlags.isMediaQueryIntegrationEnabled) return
    val summary = mediaQuery {
        "viewingDistance=$viewingDistance pointer=$pointerPrecision keyboard=$keyboardKind " +
            "posture=$windowPosture window=${windowWidth}x$windowHeight"
    }
    LaunchedEffect(summary) { Log.d("DeviceTraits", summary) }
}

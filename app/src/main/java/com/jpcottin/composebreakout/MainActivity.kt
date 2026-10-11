package com.jpcottin.composebreakout

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jpcottin.composebreakout.glasses.GlassesSession
import com.jpcottin.composebreakout.theme.BreakoutTheme
import com.jpcottin.composebreakout.ui.GameScreen
import com.jpcottin.composebreakout.ui.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The game is dark whatever the system setting; tell the system to
        // render dark scrims so status/nav bar icons are light and legible.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        GlassesSession.startMonitoring(applicationContext)
        setContent {
            val viewModel: GameViewModel = viewModel(factory = GameViewModel.factory(applicationContext))
            // A launch asked for an unattended round: Auto Play on, round
            // started. Used from the command line to watch the game run.
            //   adb shell am start -n com.jpcottin.composebreakout/.MainActivity --ez autoplay true
            if (intent.getBooleanExtra(EXTRA_AUTOPLAY, false) && savedInstanceState == null) {
                LaunchedEffect(Unit) { viewModel.startUnattendedRound() }
            }
            BreakoutTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GameScreen(viewModel)
                }
            }
        }
    }
}

/** Intent extra: start an Auto Play round as soon as the screen is up. */
const val EXTRA_AUTOPLAY = "autoplay"

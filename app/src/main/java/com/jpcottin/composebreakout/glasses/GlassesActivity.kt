package com.jpcottin.composebreakout.glasses

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jpcottin.composebreakout.theme.BreakoutTheme
import com.jpcottin.composebreakout.ui.GameScreen
import com.jpcottin.composebreakout.ui.GameViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * The game on the AI Glasses: the same screen on the projected display
 * (see the manifest's requiredDisplayCategory), with its own engine in
 * touchbar mode — finger position on the bar steers, contact launches —
 * no gear, and a pure black background, which renders transparent on
 * additive AR lenses. It lives for the glasses session: created when the
 * phone hands the game over, finished when the phone asks for it back.
 */
class GlassesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Projected displays may blank on idle; the game is the foreground
        // experience while this activity lives.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        GlassesSession.setActive(true)
        lifecycleScope.launch {
            GlassesSession.active.filter { !it }.collect { finish() }
        }
        setContent {
            val viewModel: GameViewModel =
                viewModel(factory = GameViewModel.factory(applicationContext, GameViewModel.Role.GLASSES))
            BreakoutTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    GameScreen(viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) GlassesSession.setActive(false)
    }
}

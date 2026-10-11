package com.jpcottin.composebreakout.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.engine.GameHost
import com.jpcottin.composebreakout.engine.HighScore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The player's choices and the high-score table, persisted in DataStore. */
data class Settings(
    val soundEnabled: Boolean = true,
    val autoPlay: Boolean = false,
)

/**
 * Owns the [Game] for the activity's lifetime (it survives rotation and
 * fold/unfold), persists settings and high scores, and is the [GameHost]
 * the engine talks to. The frame loop lives in the composable that draws the
 * game; this class only exposes the engine and the persisted state.
 */
class GameViewModel(private val store: DataStore<Preferences>) : ViewModel(), GameHost {

    val game: Game = Game(host = this)

    /** Last persisted settings; the switches in the settings sheet edit these. */
    val settings = store.data.map { prefs ->
        Settings(
            soundEnabled = prefs[KEY_SOUND] ?: true,
            autoPlay = prefs[KEY_AUTO_PLAY] ?: false,
        )
    }

    init {
        viewModelScope.launch {
            val prefs = store.data.first()
            game.soundEnabled = prefs[KEY_SOUND] ?: true
            game.autoPlayActive = prefs[KEY_AUTO_PLAY] ?: false
            game.restoreHighScores(decodeScores(prefs[KEY_HIGH_SCORES]))
        }
    }

    fun setSoundEnabled(on: Boolean) {
        game.soundEnabled = on
        viewModelScope.launch { store.edit { it[KEY_SOUND] = on } }
    }

    fun setAutoPlay(on: Boolean) {
        game.autoPlayActive = on
        viewModelScope.launch { store.edit { it[KEY_AUTO_PLAY] = on } }
    }

    // ── GameHost ──────────────────────────────────────────────────────────────

    override fun highScoresChanged(table: List<HighScore>) {
        viewModelScope.launch { store.edit { it[KEY_HIGH_SCORES] = encodeScores(table) } }
    }

    companion object {
        private val KEY_SOUND = booleanPreferencesKey("sound")
        private val KEY_AUTO_PLAY = booleanPreferencesKey("auto_play")
        private val KEY_HIGH_SCORES = stringPreferencesKey("high_scores")

        /** "score:level,score:level,..." — small enough that a string key is the simplest store. */
        internal fun encodeScores(table: List<HighScore>): String =
            table.joinToString(",") { "${it.score}:${it.level}" }

        internal fun decodeScores(encoded: String?): List<HighScore> =
            encoded.orEmpty().split(',').mapNotNull { entry ->
                val parts = entry.split(':')
                val score = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val level = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                if (score <= 0L) null else HighScore(score, level)
            }

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { GameViewModel(context.applicationContext.settingsStore) }
        }
    }
}

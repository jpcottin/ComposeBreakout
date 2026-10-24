package com.jpcottin.composebreakout.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Bundle
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jpcottin.composebreakout.audio.AudioOutput
import com.jpcottin.composebreakout.engine.Game
import com.jpcottin.composebreakout.engine.GameHost
import com.jpcottin.composebreakout.engine.HighScore
import com.jpcottin.composebreakout.engine.SoundEvent
import com.jpcottin.composebreakout.glasses.GlassesSession
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The player's choices, persisted in DataStore. */
data class Settings(
    val soundEnabled: Boolean = true,
    val autoPlay: Boolean = false,
    val haptics: Boolean = true,
)

/**
 * Owns the [Game] for the activity's lifetime (it survives rotation and
 * fold/unfold), persists settings and high scores, owns the audio output
 * and the vibrator, and is the [GameHost] the engine talks to. A round in progress also survives process death:
 * level, score and lives go into the saved state, and the next instance
 * resumes them with a fresh wall. The frame loop lives in the composable
 * that draws the game; this class only exposes the engine and the
 * persisted state.
 */
class GameViewModel(
    private val store: DataStore<Preferences>,
    savedState: SavedStateHandle,
    private val vibrator: Vibrator?,
    val role: Role = Role.PHONE,
) : ViewModel(), GameHost {

    /** Which screen this engine plays on. */
    enum class Role {
        /** The phone: the strip steers, the gear opens Settings, Settings can hand the game to the glasses. */
        PHONE,
        /** The glasses' projected display: the whole touchbar steers, no gear, black background. */
        GLASSES,
    }

    val game: Game = Game(host = this)

    /** The synth and its speaker thread. Paused unless the activity is resumed. */
    val audio = AudioOutput()

    /** The preferences as stored, with a read error (a damaged file, a failed disk)
     *  read as "nothing stored" rather than thrown into whoever collects: a
     *  crash on launch would otherwise follow the file around until it is cleared. */
    private val prefs: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    /** Last persisted settings; the switches in the settings sheet edit these. */
    val settings = prefs.map { prefs ->
        Settings(
            soundEnabled = prefs[KEY_SOUND] ?: true,
            autoPlay = prefs[KEY_AUTO_PLAY] ?: false,
            haptics = prefs[KEY_HAPTICS] ?: true,
        )
    }

    init {
        game.seed(System.nanoTime().toInt())
        if (role == Role.GLASSES) game.controlMode = Game.ControlMode.TOUCHBAR
        else viewModelScope.launch {
            // The phone freezes while the glasses play, and adopts whatever the
            // glasses instance scored when the session ends.
            GlassesSession.active.collect { active ->
                game.glassesActive = active
                if (!active) game.restoreHighScores(decodeScores(prefs.first()[KEY_HIGH_SCORES]) + game.highScores)
            }
        }
        // The round saved by a previous instance of this process, if any.
        savedState.get<Bundle>(KEY_SESSION)?.let { s ->
            if (s.getBoolean(SESSION_MID_GAME))
                game.restoreSession(s.getInt(SESSION_LEVEL), s.getLong(SESSION_SCORE), s.getInt(SESSION_LIVES))
        }
        savedState.setSavedStateProvider(KEY_SESSION) {
            Bundle().apply {
                putBoolean(SESSION_MID_GAME, game.isMidGame)
                putInt(SESSION_LEVEL, game.resumeLevel)
                putLong(SESSION_SCORE, game.score)
                putInt(SESSION_LIVES, game.lives)
            }
        }
        viewModelScope.launch {
            val prefs = prefs.first()
            game.soundEnabled = prefs[KEY_SOUND] ?: true
            game.autoPlayActive = prefs[KEY_AUTO_PLAY] ?: false
            hapticsEnabled = prefs[KEY_HAPTICS] ?: true
            game.restoreHighScores(decodeScores(prefs[KEY_HIGH_SCORES]))
            // A restored session may already sit past the stored record.
            game.furthestLevel = maxOf(game.furthestLevel, prefs[KEY_FURTHEST_LEVEL] ?: 1)
            // A launch asked for an unattended round: Auto Play on, round
            // started. It comes after the settings load so the stored switch
            // cannot turn the autopilot back off, and the handle carries the
            // launching intent's extras, so this runs once per ViewModel
            // whatever the activity does.
            //   adb shell am start -n com.jpcottin.composebreakout/.MainActivity --ez autoplay true
            if (savedState.get<Boolean>(EXTRA_AUTOPLAY) == true) {
                startUnattendedRound()
                // Consumed: the launching intent's extras come back with every
                // restored state, and a second unattended round after process
                // death would override whatever the player chose since.
                savedState[EXTRA_AUTOPLAY] = false
            }
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

    /** Whether the vibrator speaks at all: the Haptics switch in Settings. */
    @Volatile private var hapticsEnabled = true

    fun setHaptics(on: Boolean) {
        hapticsEnabled = on
        viewModelScope.launch { store.edit { it[KEY_HAPTICS] = on } }
    }

    /** Auto Play on and a round started: the game plays itself from here. */
    fun startUnattendedRound() {
        setAutoPlay(true)
        if (game.state == Game.State.TITLE) game.tap()
    }

    /** Forget the high scores and the furthest level, in the game and on disk. */
    fun resetProgress() {
        game.restoreHighScores(emptyList())
        game.furthestLevel = 1
        viewModelScope.launch { store.edit { it.remove(KEY_HIGH_SCORES); it.remove(KEY_FURTHEST_LEVEL) } }
    }

    override fun onCleared() {
        audio.release()
    }

    // ── GameHost ──────────────────────────────────────────────────────────────

    override fun sound(event: SoundEvent) = audio.synth.trigger(event)
    override fun brickBreak(hz: Float) = audio.synth.brickBreak(hz)
    override fun brickBroken() {
        // A light tick under the finger for every brick, the heavy click
        // staying for a lost ball; both only when the effect exists. The
        // tick does not depend on the sound switch: haptics are not sound.
        if (hapticsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tick?.let { vibrator?.vibrate(it) }
    }

    private val tick: VibrationEffect? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true)
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        else null
    override fun music(on: Boolean) { audio.synth.musicEnabled = on }

    override fun haptic() {
        val v = vibrator ?: return
        if (!hapticsEnabled || !v.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        else VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
        v.vibrate(effect)
    }

    override fun furthestLevelChanged(level: Int) {
        viewModelScope.launch { store.edit { it[KEY_FURTHEST_LEVEL] = maxOf(it[KEY_FURTHEST_LEVEL] ?: 1, level) } }
    }

    override fun highScoresChanged(table: List<HighScore>) {
        // The phone and the glasses instances share the store: fold the other
        // one's rows in rather than overwrite them.
        viewModelScope.launch {
            store.edit { prefs -> prefs[KEY_HIGH_SCORES] = mergeScores(prefs[KEY_HIGH_SCORES], table) }
        }
    }

    companion object {
        private val KEY_SOUND = booleanPreferencesKey("sound")
        private val KEY_AUTO_PLAY = booleanPreferencesKey("auto_play")
        private val KEY_HAPTICS = booleanPreferencesKey("haptics")
        private val KEY_HIGH_SCORES = stringPreferencesKey("high_scores")
        private val KEY_FURTHEST_LEVEL = intPreferencesKey("furthest_level")
        private const val KEY_SESSION = "session"
        /** Intent extra: start an Auto Play round as soon as the game is up. */
        const val EXTRA_AUTOPLAY = "autoplay"
        private const val SESSION_MID_GAME = "midGame"
        private const val SESSION_LEVEL = "level"
        private const val SESSION_SCORE = "score"
        private const val SESSION_LIVES = "lives"

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

        /** The stored table with [table] folded in: no duplicate rows, best
         *  first, capped at [Game.MAX_SCORES]. Encoded, ready to store. */
        internal fun mergeScores(stored: String?, table: List<HighScore>): String =
            encodeScores((decodeScores(stored) + table).distinct().sortedByDescending { it.score }.take(Game.MAX_SCORES))

        fun factory(context: Context, role: Role = Role.PHONE): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                else @Suppress("DEPRECATION") (app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                GameViewModel(app.settingsStore, createSavedStateHandle(), vibrator, role)
            }
        }
    }
}

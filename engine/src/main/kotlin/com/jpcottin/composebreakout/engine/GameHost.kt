package com.jpcottin.composebreakout.engine

/** A sound the game asks its host to play. */
enum class SoundEvent { PADDLE, WALL, BRICK_HIT, BALL_LOST, LEVEL_CLEAR, POWER_UP, EXTRA_LIFE }

/** One row of the high-score table. */
data class HighScore(val score: Long, val level: Int)

/**
 * Everything the game needs from the outside world. The engine never touches
 * Android: the app implements this with AudioTrack, the vibrator and
 * DataStore, the tests and the bench with nothing at all.
 */
interface GameHost {
    /** Play a one-shot effect. */
    fun sound(event: SoundEvent) {}
    /** A brick broke: a pop pitched by its row (Hz). */
    fun brickBreak(hz: Float) {}
    /** Whether the ambient track should play right now. */
    fun music(on: Boolean) {}
    /** A short pulse: a ball was lost. */
    fun haptic() {}
    /** The high-score table changed and should be persisted. */
    fun highScoresChanged(table: List<HighScore>) {}
    /** A level never seen before was reached; persist it so the title can
     *  offer it as a starting point. */
    fun furthestLevelChanged(level: Int) {}

    /** The host that does nothing: tests, the bench, previews. */
    object None : GameHost
}

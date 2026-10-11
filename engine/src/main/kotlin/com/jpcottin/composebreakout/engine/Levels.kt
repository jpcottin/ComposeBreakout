package com.jpcottin.composebreakout.engine

/** The ten levels, easy to hard: how many rows, which bricks, how fast. */
object Levels {
    const val COUNT = 10

    fun clamp(level: Int): Int = level.coerceIn(1, COUNT)

    /** Rows of bricks per level: 4 at level 1, up to 9 at level 10. */
    private val ROWS = intArrayOf(4, 5, 5, 6, 6, 7, 7, 8, 8, 9)
    fun rows(level: Int): Int = ROWS[clamp(level) - 1]

    /** Base ball speed per level, in world units per second (the screen is 2 tall). */
    fun ballSpeed(level: Int): Float = 0.90f + 0.065f * (clamp(level) - 1)

    /** The ball gets up to this much faster as the wall thins out (classic). */
    const val SPEED_RAMP = 0.20f
    /** The slow-ball bonus. */
    const val SLOW_FACTOR = 0.60f

    /** The wall of a level, slot by slot; null is a hole. */
    fun brickAt(level: Int, row: Int, col: Int, rows: Int): Game.BrickType? {
        val oddCol = col % 2 == 1
        return when (clamp(level)) {
            1, 2 -> Game.BrickType.NORMAL
            3 -> if (row == 0) Game.BrickType.TOUGH else Game.BrickType.NORMAL
            4 -> when {
                row == rows - 1 && oddCol -> null
                row <= 1 -> Game.BrickType.TOUGH
                else -> Game.BrickType.NORMAL
            }
            5 -> when {
                row == 3 && col % 3 == 1 -> Game.BrickType.STEEL
                row <= 1 -> Game.BrickType.TOUGH
                else -> Game.BrickType.NORMAL
            }
            6 -> when {
                row == 4 && col % 3 == 0 -> Game.BrickType.STEEL
                row <= 2 -> Game.BrickType.TOUGH
                else -> Game.BrickType.NORMAL
            }
            7 -> when {
                row == 4 && col % 3 == 1 -> Game.BrickType.STEEL
                row == rows - 1 && oddCol -> null
                row == 0 -> Game.BrickType.ARMOR
                row <= 2 -> Game.BrickType.TOUGH
                else -> Game.BrickType.NORMAL
            }
            8 -> when {
                row == 5 && !oddCol -> Game.BrickType.STEEL
                row == 0 -> Game.BrickType.ARMOR
                row <= 3 -> Game.BrickType.TOUGH
                else -> Game.BrickType.NORMAL
            }
            9 -> when {
                row == 5 && oddCol -> Game.BrickType.STEEL
                row <= 1 -> Game.BrickType.ARMOR
                row <= 3 -> Game.BrickType.TOUGH
                else -> Game.BrickType.NORMAL
            }
            else -> when {
                row == 6 && col % 3 != 1 -> Game.BrickType.STEEL
                row <= 1 -> Game.BrickType.ARMOR
                row <= 4 -> Game.BrickType.TOUGH
                else -> Game.BrickType.NORMAL
            }
        }
    }
}

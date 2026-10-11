package com.jpcottin.composebreakout.engine

import kotlin.math.min

/**
 * The playfield's fixed geometry, in world units.
 *
 * World coordinates: x in [-asp, asp], y in [-1, 1], y pointing DOWN (the
 * bricks sit at the top, the paddle near the bottom). asp = width / height
 * of the view, so a world unit is the same size on both axes and shapes stay
 * square on screen. The renderer maps a world point to pixels with one scale
 * (half the view height) around the view centre.
 */
object World {
    /** Half-width of the playfield lane. A wide screen (landscape, an unfolded
     *  foldable, a tablet, an XR panel) gets side margins rather than a wall
     *  three times wider than a paddle can cover: at most 10 columns. */
    const val MAX_FIELD_HW = 0.63f
    fun fieldHalfWidth(aspect: Float): Float = min(aspect, MAX_FIELD_HW)

    // Bricks have a fixed size; the number of columns follows the lane width.
    const val BRICK_HW = 0.055f
    const val BRICK_HH = 0.021f
    const val BRICK_GAP_X = 0.012f
    const val BRICK_GAP_Y = 0.012f
    const val WALL_TOP_Y = -0.78f      // centre of row 0
    const val SIDE_MARGIN = 0.020f     // smallest gap between the wall and the lane's side

    // Paddle line and the pit below it.
    const val PADDLE_Y = 0.62f
    const val PADDLE_HW = 0.100f       // half-width (normal)
    const val WIDE_PADDLE_HW = 0.160f  // half-width with the wide bonus
    const val PADDLE_HH = 0.014f
    const val PADDLE_SPEED = 2.8f      // on a portrait phone; scales with the lane width
    const val BALL_R = 0.016f
    const val PIT_Y = 0.72f            // ball centre past this: lost

    // The ceiling, just below the HUD.
    const val TOP_WALL_Y = -0.83f

    /** Control strip: the band below the paddle where a finger steers. */
    const val STRIP_TOP_Y = 0.70f

    /** Columns of bricks that fit across the lane at this aspect ratio. */
    fun wallColumns(aspect: Float): Int {
        val usable = 2f * (fieldHalfWidth(aspect) - SIDE_MARGIN) + BRICK_GAP_X
        return (usable / (2f * BRICK_HW + BRICK_GAP_X)).toInt().coerceAtLeast(1)
    }

    /** The paddle keeps the same speed relative to the lane width on every screen. */
    fun paddleSpeed(aspect: Float): Float = PADDLE_SPEED * fieldHalfWidth(aspect) / 0.45f
}

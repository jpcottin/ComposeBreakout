package com.jpcottin.composebreakout.engine

import kotlin.math.abs

/**
 * A brick-breaking game: a paddle, a ball and a wall of coloured bricks.
 * Pure Kotlin: the app draws what this class holds and feeds it input in
 * world coordinates (see [World]); the host gets sounds and haptics through
 * [GameHost].
 *
 * Controls: the bottom strip of the screen (below the paddle) is the control
 * zone — while a finger presses there, the paddle slides toward the finger's
 * x. On the glasses the whole touchbar is the zone ([ControlMode.TOUCHBAR]).
 * Any tap launches a ball that is resting on the paddle.
 */
class Game(private val host: GameHost = GameHost.None) {

    enum class State { TITLE, PLAYING, LEVEL_CLEAR, GAME_OVER, WIN }

    /** Brick kinds. Hits to break: 1, 2, 3; steel never breaks and does not
     *  count toward clearing the level. */
    enum class BrickType(val hits: Int, val points: Int) {
        NORMAL(1, 10), TOUGH(2, 20), ARMOR(3, 30), STEEL(0, 0)
    }

    /** How touch input drives the paddle. */
    enum class ControlMode {
        /** Phone: the strip below the paddle steers. */
        STRIP,
        /** AI glasses: the whole touch surface is the bar, finger x steers. */
        TOUCHBAR,
    }

    /** One brick of the wall. Its position derives from its (row, col) slot. */
    class Brick internal constructor(val row: Int, val col: Int, val type: BrickType) {
        var hp: Int = type.hits
            internal set
        var alive: Boolean = true
            internal set
    }

    private class Pointer(var active: Boolean = false, var x: Float = 0f, var y: Float = 0f)

    // --- viewport ---
    /** Width / height of the view; the world spans x in [-aspect, aspect]. */
    var aspect: Float = 0.45f
        private set
    val fieldHalfWidth: Float get() = World.fieldHalfWidth(aspect)

    // --- state ---
    var state: State = State.TITLE
        private set
    var level: Int = 1
        private set
    var lives: Int = 5
        private set
    var score: Long = 0
        private set
    /** Seconds since start; drives pulses and blinks in the renderer. */
    var animTime: Float = 0f
        private set

    // --- the wall ---
    private val bricksList = ArrayList<Brick>()
    val bricks: List<Brick> get() = bricksList
    /** Columns the current wall was built with. */
    var wallColumns: Int = 1
        private set

    // --- title demo: a ball bouncing under the title, a paddle chasing it ---
    var demoBallX = 0f; private set
    var demoBallY = 0.20f; private set
    private var demoBallVx = 0.55f
    private var demoBallVy = 0.80f
    var demoPaddleX = 0f; private set

    // --- settings (owned by the app, mirrored here) ---
    var soundEnabled: Boolean = true
        set(value) { field = value; syncMusic() }
    var autoPlayActive: Boolean = false
    var controlMode: ControlMode = ControlMode.STRIP
    /** True on the phone while the round runs on the glasses: gameplay freezes. */
    var glassesActive: Boolean = false
        set(value) { field = value; syncMusic() }

    // --- high scores ---
    private val table = ArrayList<HighScore>()
    val highScores: List<HighScore> get() = table

    // --- input ---
    private val pointers = Array(MAX_POINTERS) { Pointer() }
    private var tapPending = false
    private var tapX = 0f
    private var tapY = 0f

    private var rng: Int = 0x1234567

    init {
        buildDemoWall()
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    /** The view's aspect ratio (width / height). */
    fun setAspect(aspect: Float) {
        if (aspect <= 0f || aspect == this.aspect) return
        this.aspect = aspect
        if (World.wallColumns(aspect) != wallColumns && state == State.TITLE) buildDemoWall()
        val lim = fieldHalfWidth - World.PADDLE_HW
        demoPaddleX = demoPaddleX.coerceIn(-lim, lim)
    }

    /** Randomize the xorshift state (production only — tests rely on the fixed
     *  default seed for deterministic choreography). */
    fun seed(seed: Int) {
        rng = if (seed == 0) 0x1234567 else seed
    }

    /** Adopt a persisted high-score table. */
    fun restoreHighScores(scores: List<HighScore>) {
        table.clear()
        table.addAll(scores.take(MAX_SCORES))
    }

    // ── Input (world coordinates) ─────────────────────────────────────────────

    fun onPointerDown(id: Int, wx: Float, wy: Float) {
        if (id in 0 until MAX_POINTERS) pointers[id].apply { active = true; x = wx; y = wy }
        if (!tapPending) { tapX = wx; tapY = wy }   // first finger wins
        tapPending = true
    }

    fun onPointerMove(id: Int, wx: Float, wy: Float) {
        if (id in 0 until MAX_POINTERS && pointers[id].active) pointers[id].apply { x = wx; y = wy }
    }

    fun onPointerUp(id: Int) {
        if (id in 0 until MAX_POINTERS) pointers[id].active = false
    }

    fun onPointersCancel() {
        pointers.forEach { it.active = false }
    }

    /** A tap without a position (a D-pad select, a glasses touchbar press). */
    fun tap() {
        if (!tapPending) { tapX = 0f; tapY = 0f }
        tapPending = true
    }

    // ── The wall ──────────────────────────────────────────────────────────────

    fun brickX(b: Brick): Float =
        (b.col - (wallColumns - 1) * 0.5f) * (2f * World.BRICK_HW + World.BRICK_GAP_X)

    fun brickY(b: Brick): Float =
        World.WALL_TOP_Y + b.row * (2f * World.BRICK_HH + World.BRICK_GAP_Y)

    /** The title screen's wall: five full rows, one colour per row. */
    private fun buildDemoWall() {
        wallColumns = World.wallColumns(aspect)
        bricksList.clear()
        for (r in 0 until 5) for (c in 0 until wallColumns) bricksList.add(Brick(r, c, BrickType.NORMAL))
        demoBallX = 0f; demoBallY = 0.20f
        demoBallVx = 0.55f; demoBallVy = 0.80f
        demoPaddleX = 0f
    }

    // ── Update ────────────────────────────────────────────────────────────────

    fun update(dtIn: Float) {
        val dt = if (dtIn > 0.05f) 0.05f else dtIn   // clamp huge hitches
        animTime += dt

        val tapped = tapPending
        tapPending = false

        when (state) {
            State.TITLE -> {
                // Demo under the title: the ball bounces between the lane's
                // walls, the bottom of the wall and the paddle line, and the
                // paddle slides to meet it.
                demoBallX += demoBallVx * dt
                demoBallY += demoBallVy * dt
                val lim = fieldHalfWidth - World.BALL_R
                if (demoBallX > lim) { demoBallX = lim; demoBallVx = -abs(demoBallVx) }
                if (demoBallX < -lim) { demoBallX = -lim; demoBallVx = abs(demoBallVx) }
                val ceiling = DEMO_CEILING_Y
                if (demoBallY < ceiling) { demoBallY = ceiling; demoBallVy = abs(demoBallVy) }
                val floor = World.PADDLE_Y - World.PADDLE_HH - World.BALL_R
                if (demoBallY > floor) { demoBallY = floor; demoBallVy = -abs(demoBallVy) }
                val dx = demoBallX - demoPaddleX
                val step = 1.6f * dt
                demoPaddleX = if (abs(dx) <= step) demoBallX else demoPaddleX + (if (dx > 0f) step else -step)
                if (tapped) { /* the round starts here with the next step of the project */ }
            }
            State.PLAYING, State.LEVEL_CLEAR, State.GAME_OVER, State.WIN -> Unit
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** The single rule for the music flag: it plays from the start of a round
     *  through the end screen, when sound is on, and not on the phone while
     *  the round runs on the glasses. */
    private fun syncMusic() {
        val handedOff = glassesActive && controlMode == ControlMode.STRIP
        host.music(soundEnabled && state != State.TITLE && !handedOff)
    }

    private fun frand(): Float {
        var x = rng
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        rng = x
        return (x and 0xFFFFFF).toFloat() / 0x1000000.toFloat()
    }

    private fun frange(a: Float, b: Float): Float = a + (b - a) * frand()

    companion object {
        const val MAX_SCORES = 5
        /** The demo ball bounces under the title, which sits below the demo wall. */
        const val DEMO_CEILING_Y = -0.10f
        const val MAX_LIVES = 9
        private const val MAX_POINTERS = 16
    }
}

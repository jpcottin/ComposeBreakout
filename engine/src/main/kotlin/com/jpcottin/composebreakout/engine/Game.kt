package com.jpcottin.composebreakout.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A brick-breaking game: a paddle, a ball and a wall of coloured bricks, ten
 * levels from easy to hard, bonuses dropping from broken bricks. Pure
 * Kotlin: the app draws what this class holds and feeds it input in world
 * coordinates (see [World]); the host gets sounds, haptics and the
 * high-score table through [GameHost].
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

    /** Bonuses dropped by broken bricks. */
    enum class PowerUpType { WIDE, SLOW, MULTI, LIFE }

    /** How touch input drives the paddle. */
    enum class ControlMode {
        /** Phone: the strip below the paddle steers. */
        STRIP,
        /** AI glasses: the whole touch surface is the bar, finger x steers. */
        TOUCHBAR,
    }

    /** One brick of the wall. Its position derives from its (row, col) slot. */
    class Brick internal constructor(val row: Int, val col: Int, val type: BrickType, present: Boolean) {
        var hp: Int = if (type == BrickType.STEEL) 1 else type.hits
            internal set
        var alive: Boolean = present
            internal set
    }

    /** (dx, dy) is a unit direction; the speed is a property of the level
     *  (see [ballSpeed]), so a slow-ball bonus scales every ball at once. */
    class Ball internal constructor(var x: Float, var y: Float, var dx: Float, var dy: Float) {
        var alive = true
            internal set
        /** Seconds in flight since this ball last touched a brick. */
        var dry = 0f
            internal set
    }

    class PowerUp internal constructor(var x: Float, var y: Float, val type: PowerUpType) {
        var alive = true
            internal set
    }

    /** A brick fragment: colour as (r, g, b) in 0..1, the renderer tints it. */
    class Particle internal constructor(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var rot: Float, val spin: Float, val maxLife: Float, val size: Float,
        val r: Float, val g: Float, val b: Float,
    ) {
        var t = 0f
        /** 1 at birth, 0 at death. */
        val life: Float get() = 1f - t / maxLife
    }

    /** An expanding, fading flash ring. */
    class Explosion internal constructor(
        val x: Float, val y: Float, val radius: Float, val maxLife: Float,
        val r: Float, val g: Float, val b: Float,
    ) {
        var t = 0f
        val progress: Float get() = t / maxLife
    }

    private class Pointer(val id: Int, var x: Float, var y: Float)

    // --- viewport ---
    /** Width / height of the view; the world spans x in [-aspect, aspect]. */
    var aspect: Float = 0.45f
        private set
    val fieldHalfWidth: Float get() = World.fieldHalfWidth(aspect)

    // --- state ---
    var state: State = State.TITLE
        private set
    /** Seconds since start; drives pulses and blinks in the renderer. */
    var animTime: Float = 0f
        private set
    /** Seconds in the current LEVEL_CLEAR / GAME_OVER / WIN screen. */
    var stateTimer: Float = 0f
        private set
    /** A round frozen mid-flight (the screen went away, Settings opened).
     *  A tap resumes it; the autopilot resumes by itself. */
    var paused: Boolean = false
        private set

    // --- the wall ---
    private val bricksList = ArrayList<Brick>()
    val bricks: List<Brick> get() = bricksList
    var rows: Int = 0
        private set
    /** Columns the current wall was built with. */
    var wallColumns: Int = 1
        private set
    private var wallTotal = 0      // breakable bricks at level start (speed ramp)

    // --- paddle (fixed altitude, slides horizontally) ---
    var paddleX: Float = 0f
        private set
    val paddleHalfWidth: Float get() = if (wideTimer > 0f) World.WIDE_PADDLE_HW else World.PADDLE_HW

    // --- balls ---
    private val ballsList = ArrayList<Ball>()
    val balls: List<Ball> get() = ballsList
    /** A ball rests on the paddle, waiting for a tap. */
    var ballHeld: Boolean = false
        private set
    private var serveTimer = 0f    // grace before a held ball may launch
    /** The held ball may launch: the grace after a serve is over. */
    val serveReady: Boolean get() = ballHeld && serveTimer <= 0f
    private var speedBoost = 0f    // extra speed earned by breaking bricks (0..1)

    // --- bonuses ---
    private val powerUpsList = ArrayList<PowerUp>()
    val powerUps: List<PowerUp> get() = powerUpsList
    var wideTimer: Float = 0f
        private set
    var slowTimer: Float = 0f
        private set

    // --- effects ---
    private val particlesList = ArrayList<Particle>()
    val particles: List<Particle> get() = particlesList
    private val explosionsList = ArrayList<Explosion>()
    val explosions: List<Explosion> get() = explosionsList
    private var shakeAmt = 0f
    var shakeX: Float = 0f
        private set
    var shakeY: Float = 0f
        private set
    /** The points just earned by a bonus, shown briefly where they were earned. */
    var bonusFlash: Long = 0
        private set
    var bonusFlashTimer: Float = 0f
        private set
    var bonusFlashX: Float = 0f
        private set
    var bonusFlashY: Float = 0f
        private set

    // --- title demo: a ball bouncing under the title, a paddle chasing it ---
    var demoBallX = 0f; private set
    var demoBallY = 0.20f; private set
    private var demoBallVx = 0.55f
    private var demoBallVy = 0.80f
    var demoPaddleX = 0f; private set

    // --- progression / scoring ---
    var level: Int = 1
        private set
    var lives: Int = 5
        private set
    var score: Long = 0
        private set
    /** Bench statistics. */
    var paddleHits: Int = 0
        private set
    var bricksBroken: Int = 0
        private set
    var ballsLost: Int = 0
        private set

    // --- settings (owned by the app, mirrored here) ---
    var soundEnabled: Boolean = true
        set(value) { field = value; syncMusic() }
    var autoPlayActive: Boolean = false
    var controlMode: ControlMode = ControlMode.STRIP
    /** True on the phone while the round runs on the glasses: gameplay freezes. */
    var glassesActive: Boolean = false
        set(value) { if (field != value) { field = value; syncMusic() } }

    // --- high scores ---
    private val table = ArrayList<HighScore>()
    val highScores: List<HighScore> get() = table
    var newHighScore: Boolean = false
        private set
    /** 0-based rank of the score just set, or -1. */
    var newHighScoreRank: Int = -1
        private set

    // --- auto-play (drives the same control path as a finger) ---
    private var aiMove = false
    private var aiTargetX = 0f
    private var aiLaunch = false
    private val aiPlans = ArrayList<Plan>()
    /** The autopilot is steering the paddle somewhere this frame. */
    val aiHasTarget: Boolean get() = aiMove
    /** Where the autopilot is taking the paddle. */
    val aiTarget: Float get() = aiTargetX
    /** The primary ball's predicted landing (tests, bench). */
    var aiLanding: Flight = Flight()
        private set
    /** The hit offset chosen for the primary ball, -1..1 across the paddle. */
    var aiPlanU: Float = 0f
        private set

    // --- input ---
    private val pointers = ArrayList<Pointer>()
    private var tapPending = false

    private var rng: Int = 0x1234567

    init {
        buildDemoWall()
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    /**
     * The view's aspect ratio (width / height). The wall is laid out for the
     * lane width: a fold/unfold mid-level changes how many columns fit, and
     * the level's wall is rebuilt (score and lives stay) rather than left
     * sticking out of the new screen.
     */
    fun setAspect(aspect: Float) {
        if (aspect <= 0f || aspect == this.aspect) return
        this.aspect = aspect
        if (World.wallColumns(aspect) != wallColumns) {
            when (state) {
                State.TITLE -> buildDemoWall()
                State.PLAYING -> {
                    buildWall(level)
                    resetBattlefield()
                    speedBoost = 0f
                    serveBall()
                }
                else -> Unit   // the next level or the title rebuilds
            }
        }
        clampPaddle()
        val lim = fieldHalfWidth - World.PADDLE_HW
        demoPaddleX = demoPaddleX.coerceIn(-lim, lim)
    }

    /** Randomize the xorshift state (production only — tests rely on the fixed
     *  default seed for deterministic choreography). */
    fun seed(seed: Int) {
        rng = if (seed == 0) 0x1234567 else seed
    }

    /** The highest level ever reached on this device; the host persists it.
     *  The title screen lets a round start at any level up to this one. */
    var furthestLevel: Int = 1
        set(value) {
            field = value.coerceIn(1, Levels.COUNT)
            if (startLevel > field) startLevel = field
        }

    /** The level the next round starts at, chosen on the title screen. */
    var startLevel: Int = 1
        private set

    /** Move the title screen's level pick by [delta] levels, within 1 and
     *  [furthestLevel]. Does nothing once a round is under way. */
    fun selectLevel(delta: Int) {
        if (state == State.TITLE) startLevel = (startLevel + delta).coerceIn(1, furthestLevel)
    }

    /** Adopt a persisted high-score table. */
    fun restoreHighScores(scores: List<HighScore>) {
        table.clear()
        for (s in scores) mergeHighScore(s.score, s.level)
    }

    /** Resume a run after process death: same level, score and lives, but a
     *  fresh wall. Bounds-checked because the saved values come back from the
     *  OS, not from us. */
    fun restoreSession(level: Int, score: Long, lives: Int) {
        if (level !in 1..Levels.COUNT || lives !in 1..MAX_LIVES || score < 0) return
        startGame()
        if (level > 1) startLevel(level)
        this.score = score
        this.lives = lives
    }

    /** A round is in progress (worth saving across process death). */
    val isMidGame: Boolean get() = state == State.PLAYING || state == State.LEVEL_CLEAR

    /** The level a saved session should come back to: in the level-clear
     *  interlude the wall is gone and its bonus banked, so it is the next one
     *  (restoring the cleared level would replay it and pay the bonus twice). */
    val resumeLevel: Int get() = if (state == State.LEVEL_CLEAR) level + 1 else level

    /** The screen is going away or something covers it: freeze a round in
     *  flight. Nothing to freeze on the title, a held serve or an end screen. */
    fun pause() {
        if (state == State.PLAYING && !ballHeld) paused = true
    }

    /** The screen is back. A human gets a "tap to resume" moment; the
     *  autopilot, which nobody is watching anyway, carries on at once. */
    fun resume() {
        if (paused && autoPlayActive) paused = false
    }

    // ── Input (world coordinates) ─────────────────────────────────────────────

    fun onPointerDown(id: Int, wx: Float, wy: Float) {
        val p = pointers.firstOrNull { it.id == id }
        if (p != null) { p.x = wx; p.y = wy } else pointers.add(Pointer(id, wx, wy))
        tapPending = true
    }

    fun onPointerMove(id: Int, wx: Float, wy: Float) {
        pointers.firstOrNull { it.id == id }?.apply { x = wx; y = wy }
    }

    fun onPointerUp(id: Int) {
        pointers.removeAll { it.id == id }
    }

    fun onPointersCancel() {
        pointers.clear()
        steer = 0
    }

    /** A tap without a position (a D-pad select, a glasses touchbar press). */
    fun tap() {
        tapPending = true
    }

    /**
     * Steer with a key or a D-pad: -1 left, +1 right, 0 released. The paddle
     * slides at its usual speed for as long as the key is held, which also
     * launches a held ball like a finger on the strip does.
     */
    fun setSteer(direction: Int) {
        steer = direction.coerceIn(-1, 1)
    }
    private var steer = 0

    // In TOUCHBAR mode (AI glasses) the whole surface is the control bar; in
    // STRIP mode only the strip below the paddle counts.
    private fun inControlZone(p: Pointer): Boolean =
        controlMode == ControlMode.TOUCHBAR || p.y > World.STRIP_TOP_Y

    /** A finger, a key or the autopilot is steering the paddle right now. */
    val moveHeld: Boolean
        get() = steer != 0 || pointers.any { inControlZone(it) } || (autoPlayActive && aiMove)

    private fun controlTargetX(): Float {
        if (steer != 0) return steer * (fieldHalfWidth - paddleHalfWidth)   // the lane's edge, that way
        return pointers.firstOrNull { inControlZone(it) }?.x ?: aiTargetX
    }

    // ── The wall ──────────────────────────────────────────────────────────────

    fun brickX(b: Brick): Float =
        (b.col - (wallColumns - 1) * 0.5f) * (2f * World.BRICK_HW + World.BRICK_GAP_X)

    fun brickY(b: Brick): Float =
        World.WALL_TOP_Y + b.row * (2f * World.BRICK_HH + World.BRICK_GAP_Y)

    /** Breakable bricks still standing. */
    val breakableLeft: Int get() = bricksList.count { it.alive && it.type != BrickType.STEEL }

    private fun buildWall(level: Int) {
        wallColumns = World.wallColumns(aspect)
        rows = Levels.rows(level)
        bricksList.clear()
        for (r in 0 until rows) for (c in 0 until wallColumns) {
            val t = Levels.brickAt(level, r, c, rows)
            bricksList.add(Brick(r, c, t ?: BrickType.NORMAL, t != null))
        }
        wallTotal = breakableLeft
    }

    /** The title screen's wall: five full rows, one colour per row. */
    private fun buildDemoWall() {
        wallColumns = World.wallColumns(aspect)
        rows = 5
        bricksList.clear()
        for (r in 0 until rows) for (c in 0 until wallColumns) bricksList.add(Brick(r, c, BrickType.NORMAL, true))
        wallTotal = bricksList.size
        demoBallX = 0f; demoBallY = 0.20f
        demoBallVx = 0.55f; demoBallVy = 0.80f
        demoPaddleX = 0f
    }

    // ── Rounds ────────────────────────────────────────────────────────────────

    private fun startGame() {
        score = 0
        lives = 5
        paddleHits = 0; bricksBroken = 0; ballsLost = 0
        newHighScore = false
        newHighScoreRank = -1
        bonusFlashTimer = 0f
        level = startLevel
        startLevel(startLevel)
    }

    /** Clear every in-play entity; shared by [startLevel] and the return to
     *  the title so nothing renders frozen there. */
    private fun resetBattlefield() {
        ballsList.clear()
        powerUpsList.clear()
        particlesList.clear()
        explosionsList.clear()
        ballHeld = false
        wideTimer = 0f
        slowTimer = 0f
    }

    private fun startLevel(level: Int) {
        paused = false
        this.level = Levels.clamp(level)
        if (this.level > furthestLevel) {
            furthestLevel = this.level
            host.furthestLevelChanged(furthestLevel)
        }
        buildWall(this.level)
        resetBattlefield()
        paddleX = 0f
        speedBoost = 0f
        shakeAmt = 0f; shakeX = 0f; shakeY = 0f
        serveBall()
        state = State.PLAYING
        syncMusic()
    }

    /** A new ball rests on the paddle until a tap (or the strip) launches it. */
    private fun serveBall() {
        ballsList.clear()
        ballsList.add(Ball(paddleX, World.PADDLE_Y - World.PADDLE_HH - World.BALL_R, 0f, -1f))
        ballHeld = true
        serveTimer = SERVE_DELAY
        aiPlans.clear()
    }

    /** Up and to one side, 14-31 degrees off vertical. */
    private fun launchBall() {
        ballHeld = false
        val a = (if (frand() < 0.5f) -1f else 1f) * frange(0.25f, 0.55f)
        for (b in ballsList) if (b.alive) { b.dx = sin(a); b.dy = -cos(a) }
    }

    val ballSpeed: Float
        get() {
            var s = Levels.ballSpeed(level) * (1f + Levels.SPEED_RAMP * speedBoost)
            if (slowTimer > 0f) s *= Levels.SLOW_FACTOR
            return s
        }

    private fun clampPaddle() {
        val lim = fieldHalfWidth - paddleHalfWidth
        paddleX = paddleX.coerceIn(-lim, lim)
    }

    /** The bounce angle follows where the ball met the paddle: the centre
     *  sends it (almost) straight up, the tips 65 degrees out. That is the
     *  player's aim. */
    private fun bounceOffPaddle(b: Ball) {
        val u = ((b.x - paddleX) / paddleHalfWidth).coerceIn(-1f, 1f)
        var a = u * MAX_BOUNCE_ANGLE
        if (abs(a) < MIN_BOUNCE_ANGLE)
            a = if (u < 0f || (u == 0f && b.dx < 0f)) -MIN_BOUNCE_ANGLE else MIN_BOUNCE_ANGLE
        b.dx = sin(a)
        b.dy = -cos(a)
        paddleHits++
        sound(SoundEvent.PADDLE)
    }

    /** Damage a brick the ball just hit. Returns true if it broke. */
    private fun hitBrick(br: Brick): Boolean {
        if (br.type == BrickType.STEEL) { sound(SoundEvent.BRICK_HIT); return false }
        br.hp--
        if (br.hp > 0) { sound(SoundEvent.BRICK_HIT); return false }
        br.alive = false
        bricksBroken++
        val x = brickX(br); val y = brickY(br)
        val c = ROW_COLORS[br.row % ROW_COLORS.size]
        spawnDebris(x, y, World.BRICK_HW, c[0], c[1], c[2])
        score += br.type.points.toLong() * level
        // The ball speeds up as the wall thins.
        speedBoost = if (wallTotal > 0) 1f - breakableLeft.toFloat() / wallTotal else 0f
        host.brickBroken()
        if (soundEnabled) {
            // One semitone per row, the top row highest: the wall is a keyboard.
            host.brickBreak(523.25f * 2f.pow((rows - 1 - br.row) / 12f))
        }
        if (frand() < DROP_CHANCE) spawnPowerUp(x, y)
        return true
    }

    private fun spawnPowerUp(x: Float, y: Float) {
        // Weighted: wide 35%, slow 30%, multi 25%, extra life 10%.
        val roll = frand()
        val type = when {
            roll < 0.35f -> PowerUpType.WIDE
            roll < 0.65f -> PowerUpType.SLOW
            roll < 0.90f -> PowerUpType.MULTI
            else -> PowerUpType.LIFE
        }
        powerUpsList.add(PowerUp(x, y, type))
    }

    /** Grant a bonus. Shared by the pickup path and the test hook so tests
     *  exercise the production timers, not a copy of them. */
    private fun applyPowerUp(t: PowerUpType) {
        when (t) {
            PowerUpType.WIDE -> wideTimer = WIDE_DURATION
            PowerUpType.SLOW -> slowTimer = SLOW_DURATION
            PowerUpType.MULTI -> {
                // Every ball in play splits into three (fanned 23 degrees
                // apart), up to the cap. A ball still resting on the paddle
                // launches first.
                if (ballHeld) launchBall()
                val n0 = ballsList.size
                for (i in 0 until n0) {
                    if (ballsList.size >= MAX_BALLS) break
                    val src = ballsList[i]
                    if (!src.alive) continue
                    for (s in intArrayOf(-1, 1)) {
                        if (ballsList.size >= MAX_BALLS) break
                        val th = 0.40f * s
                        val b = Ball(src.x, src.y,
                            src.dx * cos(th) - src.dy * sin(th),
                            src.dx * sin(th) + src.dy * cos(th))
                        ensureMinDy(b)
                        ballsList.add(b)
                    }
                }
            }
            PowerUpType.LIFE -> if (lives < MAX_LIVES) lives++
        }
    }

    private fun spawnDebris(ax: Float, ay: Float, ar: Float, cr: Float, cg: Float, cb: Float) {
        explosionsList.add(Explosion(ax, ay, ar * 0.5f, 0.20f, cr, cg, cb))
        repeat(8) {
            val angle = frange(0f, 6.2832f)
            val speed = frange(0.4f, 1.3f)
            particlesList.add(Particle(
                x = ax + cos(angle) * ar * 0.4f, y = ay + sin(angle) * ar * 0.4f,
                vx = cos(angle) * speed, vy = sin(angle) * speed,
                rot = frange(0f, 6.2832f), spin = frange(-5f, 5f),
                maxLife = frange(0.25f, 0.50f), size = ar * frange(0.12f, 0.28f),
                r = cr + frange(-0.08f, 0.08f), g = cg + frange(-0.08f, 0.08f), b = cb + frange(-0.08f, 0.08f),
            ))
        }
    }

    /** The last ball went into the pit: a life, the bonuses and the shake.
     *  The next ball is served at once; the game ends on the last life. */
    private fun loseBall() {
        ballsLost++
        lives--
        wideTimer = 0f
        slowTimer = 0f
        shakeAmt = 1f
        sound(SoundEvent.BALL_LOST)
        host.haptic()
        explosionsList.add(Explosion(paddleX, World.PADDLE_Y, World.PADDLE_HW * 0.6f, 0.25f, 1f, 0.35f, 0.30f))
        if (lives <= 0) {
            ballsList.clear()
            paused = false
            state = State.GAME_OVER; stateTimer = 0f
            checkHighScore()
            return
        }
        clampPaddle()
        serveBall()
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * Advance the game by [dtIn] seconds of wall-clock time. The simulation
     * runs in fixed ticks of [TICK] seconds: a frame's time is accumulated
     * and as many whole ticks as it holds are taken, the remainder carried
     * to the next frame. A 60, 90 or 120 Hz display therefore plays the
     * very same game, and a hitch is clamped to [MAX_FRAME] so nothing warps.
     */
    fun update(dtIn: Float) {
        if (dtIn <= 0f) return
        accumulator += if (dtIn > MAX_FRAME) MAX_FRAME else dtIn
        while (accumulator >= TICK - 1e-6f) {
            accumulator -= TICK
            tick(TICK)
        }
    }

    private var accumulator = 0f

    /** One fixed step of the simulation. */
    private fun tick(dt: Float) {
        animTime += dt

        // Particles and explosions animate in all states. Drag is exponential
        // decay, normalized to a 60 Hz reference so debris behaves the same
        // at any refresh rate.
        val drag = 0.88f.pow(dt * 60f)
        for (p in particlesList) {
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= drag; p.vy *= drag
            p.rot += p.spin * dt
            p.t += dt
        }
        particlesList.removeAll { it.t >= it.maxLife }
        for (e in explosionsList) e.t += dt
        explosionsList.removeAll { it.t >= it.maxLife }

        // Screen shake decay, 60 Hz-normalized like the particle drag above.
        if (shakeAmt > 0f) {
            shakeX = frange(-1f, 1f) * shakeAmt * 0.030f
            shakeY = frange(-1f, 1f) * shakeAmt * 0.030f
            shakeAmt *= 0.78f.pow(dt * 60f)
            if (shakeAmt < 0.01f) { shakeAmt = 0f; shakeX = 0f; shakeY = 0f }
        }
        if (bonusFlashTimer > 0f) bonusFlashTimer -= dt

        val tapped = tapPending
        tapPending = false

        // A paused round waits for a tap; the clock above keeps the overlay
        // breathing. A key or the autopilot steering does not resume it.
        if (paused) {
            if (state != State.PLAYING) paused = false
            else if (tapped || (autoPlayActive && aiMove)) paused = false
            else return
        }

        when (state) {
            State.TITLE -> {
                if (tapped) { startGame(); return }
                updateDemo(dt)
            }
            State.PLAYING -> {
                // While the game runs on the glasses, the phone instance freezes.
                if (glassesActive && controlMode == ControlMode.STRIP) return
                // One frame of gameplay, in order. State may flip to GAME_OVER
                // (last ball lost) or LEVEL_CLEAR / WIN mid-sequence; once it
                // does, the remaining steps are skipped — the high score has
                // already been saved, so nothing may score or collect after
                // the end.
                aiMove = false; aiLaunch = false
                if (autoPlayActive) updateAutoPlay(dt)
                updatePaddle(dt)
                if (serveReady && (tapped || moveHeld || (autoPlayActive && aiLaunch))) launchBall()
                updateBalls(dt)
                if (state == State.PLAYING) updatePowerUps(dt)
                if (state == State.PLAYING) checkLevelClear()
            }
            State.LEVEL_CLEAR -> {
                stateTimer += dt
                if (stateTimer >= LEVEL_CLEAR_PAUSE) startLevel(level + 1)
            }
            State.GAME_OVER, State.WIN -> {
                stateTimer += dt
                if (tapped && stateTimer > END_SCREEN_GRACE) {
                    state = State.TITLE
                    level = 1
                    // Clear every leftover, or it renders frozen on the title
                    // screen forever (nothing updates it there).
                    resetBattlefield()
                    buildDemoWall()
                    shakeAmt = 0f; shakeX = 0f; shakeY = 0f
                    syncMusic()
                }
            }
        }
    }

    /** Demo under the title: the ball bounces between the lane's walls, the
     *  band under the title and the paddle line; the paddle slides to meet it. */
    private fun updateDemo(dt: Float) {
        demoBallX += demoBallVx * dt
        demoBallY += demoBallVy * dt
        val lim = fieldHalfWidth - World.BALL_R
        if (demoBallX > lim) { demoBallX = lim; demoBallVx = -abs(demoBallVx) }
        if (demoBallX < -lim) { demoBallX = -lim; demoBallVx = abs(demoBallVx) }
        if (demoBallY < DEMO_CEILING_Y) { demoBallY = DEMO_CEILING_Y; demoBallVy = abs(demoBallVy) }
        val floor = World.PADDLE_Y - World.PADDLE_HH - World.BALL_R
        if (demoBallY > floor) { demoBallY = floor; demoBallVy = -abs(demoBallVy) }
        val dx = demoBallX - demoPaddleX
        val step = 1.6f * dt
        demoPaddleX = if (abs(dx) <= step) demoBallX else demoPaddleX + (if (dx > 0f) step else -step)
    }


    private fun updatePaddle(dt: Float) {
        if (moveHeld) {
            val target = controlTargetX()
            val dx = target - paddleX
            if (abs(dx) > PADDLE_DEADBAND) {
                var step = World.paddleSpeed(aspect) * dt
                if (step > abs(dx)) step = abs(dx)   // land exactly on the finger
                paddleX += if (dx > 0f) step else -step
            }
        }
        clampPaddle()
        if (ballHeld) {
            if (serveTimer > 0f) serveTimer -= dt
            for (b in ballsList) if (b.alive) { b.x = paddleX; b.y = World.PADDLE_Y - World.PADDLE_HH - World.BALL_R }
        }
    }

    private fun updateBalls(dt: Float) {
        if (ballHeld) return
        val speed = ballSpeed
        // Substep the move so the ball can't tunnel through a brick or the
        // paddle: a step is at most half the ball's radius.
        val steps = (speed * dt / BALL_STEP).toInt() + 1
        val sdt = dt / steps
        val phw = paddleHalfWidth
        val lim = fieldHalfWidth - World.BALL_R
        val top = World.TOP_WALL_Y + World.BALL_R
        val paddleTop = World.PADDLE_Y - World.PADDLE_HH

        for (b in ballsList) {
            if (!b.alive) continue
            b.dry += dt
            var s = 0
            while (s < steps && b.alive) {
                s++
                b.x += b.dx * speed * sdt
                b.y += b.dy * speed * sdt

                // Side walls and the ceiling.
                if (b.x > lim && b.dx > 0f) { b.x = lim; b.dx = -b.dx; sound(SoundEvent.WALL) }
                else if (b.x < -lim && b.dx < 0f) { b.x = -lim; b.dx = -b.dx; sound(SoundEvent.WALL) }
                if (b.y < top && b.dy < 0f) { b.y = top; b.dy = -b.dy; ensureMinDy(b); sound(SoundEvent.WALL) }

                // The paddle: only its top face, only a falling ball.
                if (b.dy > 0f && b.y + World.BALL_R >= paddleTop &&
                    b.y - World.BALL_R <= World.PADDLE_Y + World.PADDLE_HH &&
                    abs(b.x - paddleX) <= phw + World.BALL_R) {
                    b.y = paddleTop - World.BALL_R
                    bounceOffPaddle(b)
                }

                // Bricks: reflect along the axis of least penetration and push
                // the ball out. One brick per substep; the next substep sees
                // the rest.
                for (br in bricksList) {
                    if (!br.alive) continue
                    val dx = b.x - brickX(br); val dy = b.y - brickY(br)
                    val px = World.BRICK_HW + World.BALL_R - abs(dx)
                    val py = World.BRICK_HH + World.BALL_R - abs(dy)
                    if (px <= 0f || py <= 0f) continue
                    if (px < py) {
                        b.dx = if (dx > 0f) abs(b.dx) else -abs(b.dx)
                        b.x += if (dx > 0f) px else -px
                    } else {
                        b.dy = if (dy > 0f) abs(b.dy) else -abs(b.dy)
                        b.y += if (dy > 0f) py else -py
                    }
                    ensureMinDy(b)
                    hitBrick(br)
                    b.dry = 0f
                    break
                }

                // The pit.
                if (b.y - World.BALL_R > World.PIT_Y) b.alive = false
            }
        }
        val any = ballsList.any { it.alive }
        ballsList.removeAll { !it.alive }
        if (!any) loseBall()
    }

    private fun updatePowerUps(dt: Float) {
        if (wideTimer > 0f) {
            wideTimer -= dt
            if (wideTimer <= 0f) { wideTimer = 0f; clampPaddle() }
        }
        if (slowTimer > 0f) {
            slowTimer -= dt
            if (slowTimer <= 0f) slowTimer = 0f
        }
        val phw = paddleHalfWidth
        for (pu in powerUpsList) {
            if (!pu.alive) continue
            pu.y += POWER_UP_SPEED * dt
            if (pu.y > 1.1f) { pu.alive = false; continue }
            if (abs(pu.x - paddleX) < phw + POWER_UP_R && abs(pu.y - World.PADDLE_Y) < World.PADDLE_HH + POWER_UP_R) {
                pu.alive = false
                val bonus = POWER_UP_SCORE.toLong() * level
                score += bonus
                bonusFlash = bonus
                bonusFlashTimer = BONUS_FLASH_TIME
                bonusFlashX = pu.x; bonusFlashY = pu.y - 0.06f
                applyPowerUp(pu.type)
                sound(if (pu.type == PowerUpType.LIFE) SoundEvent.EXTRA_LIFE else SoundEvent.POWER_UP)
            }
        }
        powerUpsList.removeAll { !it.alive }
    }

    private fun checkLevelClear() {
        if (state != State.PLAYING || breakableLeft > 0) return
        score += LEVEL_CLEAR_SCORE.toLong() * level
        sound(SoundEvent.LEVEL_CLEAR)
        ballsList.clear()
        powerUpsList.clear()
        ballHeld = false
        if (level >= Levels.COUNT) {
            state = State.WIN; stateTimer = 0f
            checkHighScore()
            return
        }
        state = State.LEVEL_CLEAR
        stateTimer = 0f
    }

    // ── Auto Play ─────────────────────────────────────────────────────────────
    //
    // The autopilot plays the same way a good player does: it works out where
    // the ball will come down, gets the paddle there, and picks *where on the
    // paddle* to take it so the rebound does the most damage — the hit offset
    // sets the angle, so the choice of offset is the aim. Every candidate
    // rebound is simulated against the wall with the same collision rules as
    // the real update; the one that hits the most bricks per second of flight
    // and still comes down somewhere the paddle can reach wins. Predictions
    // are cached per ball and only redone when the ball changes direction (a
    // bounce), so a trip costs a handful of simulations, not one per frame.
    //
    // Bonus capsules are collected when the paddle can get there and still be
    // back under the ball in time; a ball resting on the paddle is launched
    // as soon as the serve delay is over.

    /** A ball's flight, simulated with the same rules as the real update
     *  until it comes down to the paddle line. */
    data class Flight(
        /** Reached the paddle line within the horizon. */
        val lands: Boolean = false,
        /** Where (centre x) and */
        val x: Float = 0f,
        /** when (seconds from now). */
        val t: Float = 0f,
        /** Brick hits on the way (a break counts twice). */
        val hits: Int = 0,
        /** Highest point reached (smaller is higher). */
        val minY: Float = 1f,
        /** How close the path came to a breakable brick (world units; 0 is a hit). */
        val closest: Float = 9f,
    )

    /** What the autopilot knows about one ball in play: its predicted landing
     *  and the rebound it has chosen for it. Valid while the ball keeps its
     *  direction; any bounce invalidates it. */
    private class Plan {
        var valid = false
        var dx = 0f
        var dy = 0f
        var land = Flight()
        var u = 0f          // chosen hit offset (-1..1 across the paddle)
        var age = 0f        // seconds since the plan was made
    }

    /** The rebound direction for a hit at offset u — the same rule as
     *  [bounceOffPaddle], including the never-vertical minimum. */
    private fun reboundAngle(uIn: Float): Float {
        val u = uIn.coerceIn(-1f, 1f)
        var a = u * MAX_BOUNCE_ANGLE
        if (abs(a) < MIN_BOUNCE_ANGLE) a = if (u < 0f) -MIN_BOUNCE_ANGLE else MIN_BOUNCE_ANGLE
        return a
    }

    /** Fly a ball from (x, y) along (dx, dy) until it comes down to the
     *  paddle line. [hp] is a scratch copy of the wall (hit points per brick,
     *  0 dead, -1 steel) that the flight damages as it goes, so a second
     *  flight from the landing point sees the bricks the first one broke. */
    private fun simulateFlight(x0: Float, y0: Float, dx0: Float, dy0: Float, speed: Float, hp: IntArray, tMax: Float): Flight {
        var x = x0; var y = y0; var dx = dx0; var dy = dy0
        var hits = 0
        var minY = 1f
        var closest = 9f
        val lim = fieldHalfWidth - World.BALL_R
        val top = World.TOP_WALL_Y + World.BALL_R
        val paddleTop = World.PADDLE_Y - World.PADDLE_HH
        // Below this line there is nothing to hit: skip the brick loop.
        val wallBottom = if (rows > 0) World.WALL_TOP_Y + (rows - 1) * (2f * World.BRICK_HH + World.BRICK_GAP_Y) + World.BRICK_HH
                         else World.TOP_WALL_Y
        val sdt = BALL_STEP / speed
        var t = 0f
        var step = 0
        while (step < AI_MAX_STEPS && t < tMax) {
            step++
            x += dx * BALL_STEP
            y += dy * BALL_STEP
            t += sdt
            if (x > lim && dx > 0f) { x = lim; dx = -dx }
            else if (x < -lim && dx < 0f) { x = -lim; dx = -dx }
            if (y < top && dy < 0f) {
                y = top; dy = -dy
                if (abs(dy) < MIN_DY) { val sdx = if (dx < 0f) -1f else 1f; dy = MIN_DY; dx = sdx * sqrt(1f - MIN_DY * MIN_DY) }
            }
            if (y < minY) minY = y
            if (y - World.BALL_R <= wallBottom) {
                for (i in bricksList.indices) {
                    if (hp[i] == 0) continue
                    val br = bricksList[i]
                    val bx = x - brickX(br); val by = y - brickY(br)
                    val px = World.BRICK_HW + World.BALL_R - abs(bx)
                    val py = World.BRICK_HH + World.BALL_R - abs(by)
                    if (hp[i] > 0) {
                        // How near this pass came to a breakable brick: the
                        // gap outside its box, the larger axis counting.
                        val gap = maxOf(-px, -py, 0f)
                        if (gap < closest) closest = gap
                    }
                    if (px <= 0f || py <= 0f) continue
                    if (px < py) {
                        dx = if (bx > 0f) abs(dx) else -abs(dx)
                        x += if (bx > 0f) px else -px
                    } else {
                        dy = if (by > 0f) abs(dy) else -abs(dy)
                        y += if (by > 0f) py else -py
                    }
                    if (abs(dy) < MIN_DY) {
                        val sdy = if (dy < 0f) -1f else 1f
                        val sdx = if (dx < 0f) -1f else 1f
                        dy = sdy * MIN_DY; dx = sdx * sqrt(1f - MIN_DY * MIN_DY)
                    }
                    if (hp[i] > 0) {
                        hp[i]--
                        hits += if (hp[i] == 0) 2 else 1
                    }
                    break
                }
            }
            if (dy > 0f && y + World.BALL_R >= paddleTop) return Flight(true, x, t, hits, minY, closest)
        }
        return Flight(false, 0f, 0f, hits, minY, closest)   // never came down within the horizon
    }

    /** Predict where ball [b] lands and choose the rebound to give it there. */
    private fun makePlan(plan: Plan, b: Ball, ballIndex: Int) {
        plan.valid = true
        plan.dx = b.dx; plan.dy = b.dy
        plan.age = 0f
        plan.u = 0f
        val speed = ballSpeed
        val hp = IntArray(bricksList.size) { i ->
            val br = bricksList[i]
            if (!br.alive) 0 else if (br.type == BrickType.STEEL) -1 else br.hp
        }
        plan.land = simulateFlight(b.x, b.y, b.dx, b.dy, speed, hp, AI_HORIZON)
        if (!plan.land.lands) return
        // The wall as it will be when the ball arrives (this flight's damage).
        val wallAtLanding = hp.copyOf()

        val hw = paddleHalfWidth
        val lim = fieldHalfWidth - hw
        val speedP = World.paddleSpeed(aspect)
        val ballY = World.PADDLE_Y - World.PADDLE_HH - World.BALL_R
        var bestScore = -1e9f
        for (u in AI_OFFSETS) {
            var px = (plan.land.x - u * hw).coerceIn(-lim, lim)   // where the paddle must be
            var uReal = (plan.land.x - px) / hw                   // the offset the clamp allows
            if (abs(uReal) < AI_MIN_OFFSET) {                      // pushed into the dead zone by the clamp
                uReal = if (uReal < 0f) -AI_MIN_OFFSET else AI_MIN_OFFSET
                px = plan.land.x - uReal * hw
                if (px > lim || px < -lim) continue
            }
            // Reachable in time? The paddle slides at the paddle speed.
            if (abs(px - paddleX) - speedP * plan.land.t > 0f) continue
            val a = reboundAngle(uReal)
            val rdx = sin(a); val rdy = -cos(a)
            val hpNext = wallAtLanding.copyOf()
            val next = simulateFlight(plan.land.x, ballY, rdx, rdy, speed, hpNext, AI_HORIZON)
            // Damage per second of flight, the wall's height reached as a tie
            // breaker (a trip that climbs high is a trip that will hit
            // something next time), and a small preference for the middle of
            // the paddle.
            var score: Float
            if (next.lands) {
                score = next.hits / (next.t + 0.4f) + (World.PADDLE_Y - next.minY) * 0.15f
                // The next landing must be reachable from this paddle position;
                // otherwise the hit is a slow way to lose the ball.
                val gap = abs(next.x - px) - hw * AI_CATCH_SLACK - speedP * next.t
                if (gap > 0f) score -= 10f + gap
                // Other balls in play land too: a hit that strands them is bad.
                for (j in aiPlans.indices) {
                    if (j == ballIndex || !aiPlans[j].valid || !aiPlans[j].land.lands) continue
                    val o = aiPlans[j].land
                    val after = o.t - aiPlans[j].age - plan.land.t
                    if (after <= 0f) continue
                    val g = abs(o.x - px) - hw * AI_CATCH_SLACK - speedP * after
                    if (g > 0f) score -= 3f
                }
            } else {
                // Still up there after the horizon: busy breaking bricks, or
                // stuck. Reward the damage, mildly.
                score = next.hits / AI_HORIZON + (World.PADDLE_Y - next.minY) * 0.15f
            }
            if (next.hits == 0) {
                // A trip that hits nothing. When the last bricks are off in a
                // corner, no single rebound reaches them: look one trip
                // further — from where this rebound comes down, which second
                // rebound hits something, and how soon. Taking a dead trip
                // without looking ahead sent the ball up the same empty lane
                // for minutes.
                var best2 = 0f
                if (next.lands) {
                    val px2 = next.x   // the paddle will be under it; offsets from there
                    for (u2 in AI_OFFSETS) {
                        val p2 = next.x - u2 * hw
                        if (p2 > lim || p2 < -lim) continue
                        if (abs(p2 - px2) > speedP * next.t) continue   // not reachable in time
                        val a2 = reboundAngle(u2)
                        val two = simulateFlight(next.x, ballY, sin(a2), -cos(a2), speed, hpNext.copyOf(), AI_HORIZON)
                        if (two.hits == 0) continue
                        val s2 = two.hits / (next.t + (if (two.lands) two.t else AI_HORIZON) + 0.4f)
                        if (s2 > best2) best2 = s2
                    }
                }
                // Nothing within two trips: lean toward the rebound whose
                // path passes nearest a brick, so the ball walks toward the
                // last ones instead of shuttling an empty lane.
                score += 0.8f * best2 + 0.10f * abs(next.x - plan.land.x) + 0.05f * abs(uReal)
                if (best2 == 0f) score += 0.3f * (1f - next.closest.coerceIn(0f, 1f))
                // A ball that has not touched a brick for a while is
                // shuttling a lane the wall no longer reaches into (the last
                // bricks sit behind steel, or off in a corner). Break the
                // pattern: the rebound that carries it furthest sideways
                // wins outright, a vertical shuttle under a shielded brick
                // can never score.
                if (best2 == 0f && b.dry > AI_STALL_SECONDS && next.lands) {
                    score += AI_STALL_BONUS * abs(next.x - plan.land.x) / fieldHalfWidth
                }
            } else {
                score -= 0.02f * abs(uReal)
            }
            if (score > bestScore) { bestScore = score; plan.u = uReal }
        }
        if (bestScore <= -1e8f) {
            // Nothing reachable in time: head for the landing anyway, the
            // nearest tip first.
            plan.u = if (plan.land.x > paddleX) -AI_CATCH_SLACK else AI_CATCH_SLACK
        }
    }

    private fun updateAutoPlay(dt: Float) {
        // A ball on the paddle: launch it the moment the serve delay is over.
        if (ballHeld) {
            aiLaunch = true
            aiPlans.clear()
        }

        // Keep one plan per ball; redo a plan when its ball changed direction.
        while (aiPlans.size < ballsList.size) aiPlans.add(Plan())
        while (aiPlans.size > ballsList.size) aiPlans.removeAt(aiPlans.size - 1)
        for (i in ballsList.indices) {
            val b = ballsList[i]
            val p = aiPlans[i]
            if (!b.alive || ballHeld) { p.valid = false; continue }
            if (p.valid && p.dx == b.dx && p.dy == b.dy) { p.age += dt; continue }
            makePlan(p, b, i)
        }

        // The primary ball: the one that comes down first and can still be
        // caught. A ball that is already out of reach is written off.
        val hw = paddleHalfWidth
        val lim = fieldHalfWidth - hw
        val speedP = World.paddleSpeed(aspect)
        var primary = -1
        var primaryT = 1e9f
        for (i in aiPlans.indices) {
            val p = aiPlans[i]
            if (!p.valid || !p.land.lands) continue
            val t = (p.land.t - p.age).coerceAtLeast(0f)
            val px = (p.land.x - p.u * hw).coerceIn(-lim, lim)
            val catchable = abs(px - paddleX) - speedP * t <= hw * (1f - AI_CATCH_SLACK) + 0.02f
            if (!catchable) continue
            if (t < primaryT) { primaryT = t; primary = i }
        }
        if (primary < 0) {
            // Nothing catchable: chase the soonest landing regardless.
            for (i in aiPlans.indices) {
                val p = aiPlans[i]
                if (!p.valid || !p.land.lands) continue
                val t = p.land.t - p.age
                if (t < primaryT) { primaryT = t; primary = i }
            }
        }

        var desiredX = paddleX
        var haveTarget = false
        if (primary >= 0) {
            val p = aiPlans[primary]
            aiLanding = p.land.copy(t = primaryT)
            aiPlanU = p.u
            desiredX = p.land.x - p.u * hw
            haveTarget = true
        } else {
            aiLanding = Flight()
            aiPlanU = 0f
        }

        // Bonus capsules: worth a detour when the paddle can be under the
        // capsule as it crosses the paddle line and still be back under the
        // ball in time. The most valuable reachable one wins; a bonus already
        // running with plenty of time left is not worth the walk.
        var collect: PowerUp? = null
        var collectValue = -1
        for (pu in powerUpsList) {
            if (!pu.alive) continue
            var tp = (World.PADDLE_Y - pu.y) / POWER_UP_SPEED   // centre reaches the paddle line
            if (tp < -0.25f) continue                            // already past
            if (tp < 0f) tp = 0f
            val value = when (pu.type) {
                PowerUpType.LIFE -> 4
                PowerUpType.MULTI -> 3
                PowerUpType.SLOW -> if (slowTimer > 3f) 0 else 2
                PowerUpType.WIDE -> if (wideTimer > 3f) 0 else 1
            }
            if (value == 0) continue
            val px = pu.x.coerceIn(-lim, lim)
            // Get there before the capsule leaves the paddle band...
            val leave = tp + (World.PADDLE_HH + POWER_UP_R) / POWER_UP_SPEED
            if (abs(px - paddleX) > speedP * leave) continue
            // ...and back under the ball in time for its landing.
            if (haveTarget) {
                val back = primaryT - tp
                if (back < 0f) continue                          // the ball comes first
                if (abs(desiredX - px) - hw * (1f - AI_CATCH_SLACK) > speedP * back) continue
            }
            if (value > collectValue) { collectValue = value; collect = pu }
        }

        when {
            collect != null -> { aiTargetX = collect.x; aiMove = true }
            haveTarget -> { aiTargetX = desiredX; aiMove = true }
            ballHeld -> { aiTargetX = paddleX; aiMove = true }   // keeps the launch path armed
        }
    }

    // ── High scores ───────────────────────────────────────────────────────────

    /** Insert a score at its rank unless the identical entry is already
     *  present. Returns the rank, or -1 when not ranked. */
    private fun mergeHighScore(score: Long, level: Int): Int {
        if (score <= 0) return -1
        if (table.any { it.score == score && it.level == level }) return -1
        var pos = table.indexOfFirst { score > it.score }
        if (pos < 0) pos = table.size
        if (pos >= MAX_SCORES) return -1
        table.add(pos, HighScore(score, level))
        while (table.size > MAX_SCORES) table.removeAt(table.size - 1)
        return pos
    }

    private fun checkHighScore() {
        if (score <= 0) return
        val pos = mergeHighScore(score, level)
        if (pos < 0) return
        newHighScore = true
        newHighScoreRank = pos
        host.highScoresChanged(table.toList())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sound(event: SoundEvent) {
        if (soundEnabled) host.sound(event)
    }

    /** The single rule for the music flag: it plays from the start of a round
     *  through the end screen, when sound is on, and not on the phone while
     *  the round runs on the glasses. */
    private fun syncMusic() {
        val handedOff = glassesActive && controlMode == ControlMode.STRIP
        host.music(soundEnabled && state != State.TITLE && !handedOff)
    }

    /** After a bounce off a wall or a brick: keep the ball from shuttling
     *  almost horizontally, which is a slow way to play nothing. */
    private fun ensureMinDy(b: Ball) {
        if (abs(b.dy) >= MIN_DY) return
        val sdy = if (b.dy < 0f) -1f else 1f
        val sdx = if (b.dx < 0f) -1f else 1f
        b.dy = sdy * MIN_DY
        b.dx = sdx * sqrt(1f - MIN_DY * MIN_DY)
    }

    private fun frand(): Float {
        var x = rng
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        rng = x
        return (x and 0xFFFFFF).toFloat() / 0x1000000.toFloat()
    }

    private fun frange(a: Float, b: Float): Float = a + (b - a) * frand()

    // ── Debug hooks: tests, previews and the bench ────────────────────────────

    /** Ways into the middle of a game for tests, previews and the bench. The
     *  rules are never bypassed: a brick killed here still counts as cleared
     *  on the next update, a ball placed here flies by the same physics. */
    inner class Debug internal constructor() {
        fun startGame() = this@Game.startGame()
        fun startLevel(level: Int) = this@Game.startLevel(level)
        fun killBrick(i: Int) { bricksList[i].alive = false }
        /** Break every breakable brick: the level is one shot from clear. Steel stays. */
        fun clearWall() { for (b in bricksList) if (b.type != BrickType.STEEL) b.alive = false }
        /** Replace every ball with one free ball at (x, y) heading along (dx, dy). */
        fun setBall(x: Float, y: Float, dx: Float, dy: Float) {
            ballsList.clear()
            addBall(x, y, dx, dy)
        }
        fun addBall(x: Float, y: Float, dx: Float, dy: Float) {
            var n = sqrt(dx * dx + dy * dy)
            var ux = dx; var uy = dy
            if (n < 1e-6f) { ux = 0f; uy = -1f; n = 1f }
            ballsList.add(Ball(x, y, ux / n, uy / n))
            ballHeld = false
        }
        fun launch() { if (ballHeld) launchBall() }
        fun setPaddleX(x: Float) { paddleX = x }
        fun spawnPowerUp(x: Float, y: Float, type: PowerUpType) { powerUpsList.add(PowerUp(x, y, type)) }
        fun activatePowerUp(type: PowerUpType) = applyPowerUp(type)
        fun setScore(s: Long) { score = s }
        fun setLives(n: Int) { lives = n }
    }
    val debug: Debug = Debug()

    companion object {
        const val MAX_SCORES = 5
        const val MAX_LIVES = 9
        const val MAX_BALLS = 6
        /** The demo ball bounces under the title, which sits below the demo wall. */
        const val DEMO_CEILING_Y = -0.10f
        /** The simulation's fixed step: 120 ticks a second. */
        const val TICK = 1f / 120f
        /** A frame longer than this (a hitch, a return from the background) counts as this. */
        const val MAX_FRAME = 0.05f
        const val SERVE_DELAY = 0.5f            // a served ball waits this long for a tap
        const val LEVEL_CLEAR_PAUSE = 1.8f
        const val END_SCREEN_GRACE = 0.6f       // an end screen ignores taps this long
        const val WIDE_DURATION = 10f
        const val SLOW_DURATION = 8f
        const val POWER_UP_SPEED = 0.30f
        const val POWER_UP_R = 0.035f
        const val DROP_CHANCE = 0.12f           // per broken brick
        const val POWER_UP_SCORE = 25
        const val LEVEL_CLEAR_SCORE = 100
        const val BONUS_FLASH_TIME = 0.9f
        private const val MAX_BOUNCE_ANGLE = 65f * 3.14159265f / 180f   // off the paddle's tip
        private const val MIN_BOUNCE_ANGLE = 8f * 3.14159265f / 180f    // never straight up
        private const val MIN_DY = 0.25f        // after a wall/brick bounce: no near-horizontal shuttling
        private const val BALL_STEP = 0.008f    // collision substep length (half the ball radius)
        private const val PADDLE_DEADBAND = 0.006f

        // Auto Play
        private const val AI_HORIZON = 8f           // seconds of flight worth simulating
        private const val AI_MAX_STEPS = 4000       // hard cap on a simulation
        private const val AI_CATCH_SLACK = 0.85f    // fraction of the half-width that still catches
        /** Candidate hit offsets across the paddle: -1 is the left tip, +1 the
         *  right. Nothing inside the dead zone (|u| < [AI_MIN_OFFSET]): every
         *  hit there leaves at the 8-degree minimum, left or right by the sign
         *  of a hair's width of paddle placement, so a plan there is a coin
         *  toss the simulation cannot call. */
        private val AI_OFFSETS = floatArrayOf(-0.85f, -0.60f, -0.35f, -0.16f, 0.16f, 0.35f, 0.60f, 0.85f)
        private const val AI_MIN_OFFSET = 0.16f
        private const val AI_STALL_SECONDS = 6f     // flight without a brick before the pattern is broken
        private const val AI_STALL_BONUS = 2f

        /** Brick colours by row as (r, g, b): the debris takes the row's colour. */
        val ROW_COLORS: List<FloatArray> = listOf(
            floatArrayOf(1.00f, 0.32f, 0.30f),   // red
            floatArrayOf(1.00f, 0.60f, 0.20f),   // orange
            floatArrayOf(1.00f, 0.90f, 0.25f),   // yellow
            floatArrayOf(0.40f, 0.95f, 0.40f),   // green
            floatArrayOf(0.30f, 0.85f, 1.00f),   // cyan
            floatArrayOf(0.40f, 0.52f, 1.00f),   // blue
            floatArrayOf(0.80f, 0.45f, 1.00f),   // purple
            floatArrayOf(1.00f, 0.45f, 0.80f),   // pink
            floatArrayOf(0.92f, 0.92f, 0.95f),   // white
        )
    }
}

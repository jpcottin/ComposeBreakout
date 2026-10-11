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
    }

    /** A tap without a position (a D-pad select, a glasses touchbar press). */
    fun tap() {
        tapPending = true
    }

    // In TOUCHBAR mode (AI glasses) the whole surface is the control bar; in
    // STRIP mode only the strip below the paddle counts.
    private fun inControlZone(p: Pointer): Boolean =
        controlMode == ControlMode.TOUCHBAR || p.y > World.STRIP_TOP_Y

    /** A finger (or the autopilot) is steering the paddle right now. */
    val moveHeld: Boolean
        get() = pointers.any { inControlZone(it) } || (autoPlayActive && aiMove)

    private fun controlTargetX(): Float =
        pointers.firstOrNull { inControlZone(it) }?.x ?: aiTargetX

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
        level = 1
        startLevel(1)
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
        this.level = Levels.clamp(level)
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
            state = State.GAME_OVER; stateTimer = 0f
            checkHighScore()
            return
        }
        clampPaddle()
        serveBall()
    }

    // ── Update ────────────────────────────────────────────────────────────────

    fun update(dtIn: Float) {
        val dt = if (dtIn > 0.05f) 0.05f else dtIn   // clamp huge hitches
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

    /** The autopilot. Arrives with the next step of the project; until then
     *  Auto Play leaves the paddle where it is. */
    @Suppress("UNUSED_PARAMETER")
    private fun updateAutoPlay(dt: Float) = Unit

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

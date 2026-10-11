package com.jpcottin.composebreakout.engine

import kotlin.system.exitProcess

/**
 * The Auto Play bench: plays seeded, headless rounds with the autopilot and
 * reports how often it clears each level, how long it takes and how many
 * balls it loses. Every Auto Play change is measured here before it ships
 * (the numbers in the commit messages come from this program).
 *
 *   ./gradlew :engine:run --args="[--runs N] [--width W --height H] [--levels] [--campaigns]
 *                                 [--min-campaign-win PCT] [--seed S] [--level L] [--trace]"
 *
 * --levels    plays each level 1..10 on its own, N seeds each, starting with
 *             five balls (the default when nothing is selected).
 * --campaigns plays N full campaigns from level 1.
 * --min-campaign-win PCT exits 1 when fewer than PCT% of campaigns are won:
 *             the CI regression guard.
 * --seed S    plays seed S alone instead of seeds 1..N: the way to replay
 *             the one run that timed out in a full pass.
 * --level L   plays level L alone (with --levels).
 * --trace     prints the scene (balls, paddle, autopilot intent) at every
 *             lost ball and at a timeout.
 */
private const val DT = 1f / 60f
private const val LEVEL_TIMEOUT = 480f     // seconds before a level counts as stuck
private var trace = false

private class Stats {
    var runs = 0; var cleared = 0; var timeouts = 0
    var ballsLost = 0
    var seconds = 0.0            // summed over cleared runs
    var score = 0L
    var bricks = 0L; var paddleHits = 0L
}

private fun dumpScene(g: Game, t: Float) {
    println("  lost ball L%d t=%.1f paddle x=%.3f (hw %.3f) aiTarget=%.3f move=%b landing=%b x=%.3f t=%.2f u=%.2f balls=%d bricks left=%d".format(
        g.level, t, g.paddleX, g.paddleHalfWidth, g.aiTarget, g.aiHasTarget, g.aiLanding.lands,
        g.aiLanding.x, g.aiLanding.t, g.aiPlanU, g.balls.size, g.breakableLeft))
    for (b in g.balls) println("    ball x=%.3f y=%.3f dir=(%.2f, %.2f)".format(b.x, b.y, b.dx, b.dy))
}

/** Plays from the current PLAYING state until the level ends or the game
 *  does. Returns the seconds played and whether the level was cleared. */
private fun playLevel(g: Game, s: Stats): Pair<Float, Boolean> {
    var seconds = 0f
    val lost0 = g.ballsLost; val bricks0 = g.bricksBroken; val hits0 = g.paddleHits
    var lost = lost0
    while (g.state == Game.State.PLAYING) {
        g.update(DT)
        seconds += DT
        if (g.ballsLost > lost) {
            lost = g.ballsLost
            if (trace) dumpScene(g, seconds)
        }
        if (seconds > LEVEL_TIMEOUT) {
            s.timeouts++
            if (trace) { println("  timeout:"); dumpScene(g, seconds) }
            break
        }
    }
    s.ballsLost += g.ballsLost - lost0
    s.bricks += g.bricksBroken - bricks0
    s.paddleHits += g.paddleHits - hits0
    return seconds to (g.state == Game.State.LEVEL_CLEAR || g.state == Game.State.WIN)
}

private fun printStats(label: String, s: Stats) {
    val avg = if (s.cleared > 0) s.seconds / s.cleared else 0.0
    println("%-10s cleared %5.1f%%  balls lost/run %.2f  timeouts %2d  avg %6.1f s  bricks/min %5.1f  paddle hits/run %5.1f  avg score %7d".format(
        label, 100.0 * s.cleared / s.runs, s.ballsLost.toDouble() / s.runs, s.timeouts, avg,
        if (s.seconds > 0.0) 60.0 * s.bricks / (s.seconds + 1e-9) else 0.0,
        s.paddleHits.toDouble() / s.runs, if (s.runs > 0) s.score / s.runs else 0L))
}

private fun seededGame(seed: Int, w: Int, h: Int): Game = Game().apply {
    setAspect(w.toFloat() / h)
    seed((seed.toLong() * 2654435761L).toInt())
    debug.startGame()
    autoPlayActive = true
}

fun main(args: Array<String>) {
    var runs = 100; var w = 1080; var h = 2400
    var levels = false; var campaigns = false
    var minCampaignWin = -1.0
    var onlySeed = 0; var onlyLevel = 0
    var i = 0
    while (i < args.size) {
        val next = { if (i + 1 < args.size) args[++i].toIntOrNull() ?: 0 else 0 }
        when (args[i]) {
            "--runs" -> runs = next()
            "--width" -> w = next()
            "--height" -> h = next()
            "--levels" -> levels = true
            "--campaigns" -> campaigns = true
            "--trace" -> trace = true
            "--seed" -> onlySeed = next()
            "--level" -> onlyLevel = next()
            "--min-campaign-win" -> minCampaignWin = if (i + 1 < args.size) args[++i].toDoubleOrNull() ?: -1.0 else -1.0
            else -> { System.err.println("unknown argument ${args[i]}"); exitProcess(2) }
        }
        i++
    }
    if (minCampaignWin >= 0.0) campaigns = true
    if (!levels && !campaigns) levels = true
    if (runs <= 0 || w <= 0 || h <= 0) { System.err.println("bad size or run count"); exitProcess(2) }
    if (onlyLevel != 0 && onlyLevel !in 1..Levels.COUNT) { System.err.println("no level $onlyLevel"); exitProcess(2) }
    val seeds = if (onlySeed != 0) listOf(onlySeed) else (1..runs).toList()
    val levelRange = if (onlyLevel != 0) onlyLevel..onlyLevel else 1..Levels.COUNT
    println("Auto Play bench: ${w}x$h, " + (if (onlySeed != 0) "seed $onlySeed" else "$runs seeded runs") +
        (if (onlyLevel != 0) ", level $onlyLevel" else ""))

    if (levels) {
        val total = Stats()
        for (level in levelRange) {
            val s = Stats()
            for (seed in seeds) {
                val g = seededGame(seed, w, h)
                if (level > 1) g.debug.startLevel(level)
                s.runs++
                val (sec, ok) = playLevel(g, s)
                if (ok) { s.cleared++; s.seconds += sec }
                s.score += g.score
            }
            printStats("level %2d".format(level), s)
            total.runs += s.runs; total.cleared += s.cleared; total.timeouts += s.timeouts
            total.ballsLost += s.ballsLost; total.seconds += s.seconds; total.score += s.score
            total.bricks += s.bricks; total.paddleHits += s.paddleHits
        }
        printStats("all", total)
    }

    if (campaigns) {
        val s = Stats()
        var won = 0
        var wonSeconds = 0.0
        val reached = IntArray(Levels.COUNT + 1)
        for (seed in seeds) {
            val g = seededGame(seed, w, h)
            var total = 0f
            s.runs++
            while (true) {
                reached[g.level]++
                val (sec, ok) = playLevel(g, s)
                total += sec
                if (!ok || g.state == Game.State.WIN) break
                // LEVEL_CLEAR: let the interlude run into the next level.
                while (g.state == Game.State.LEVEL_CLEAR) g.update(DT)
            }
            if (g.state == Game.State.WIN) { won++; wonSeconds += total }
            s.score += g.score
        }
        println("campaigns  won %5.1f%%  balls lost/run %.2f  timeouts %2d  avg won %6.1f s  avg score %7d".format(
            100.0 * won / s.runs, s.ballsLost.toDouble() / s.runs, s.timeouts,
            if (won > 0) wonSeconds / won else 0.0, s.score / s.runs))
        println("reached    " + (1..Levels.COUNT).joinToString("  ") { "L$it %3d".format(reached[it]) })
        if (minCampaignWin >= 0.0 && 100.0 * won / s.runs < minCampaignWin) {
            println("FAIL: campaign win rate below %.1f%%".format(minCampaignWin))
            exitProcess(1)
        }
    }
}

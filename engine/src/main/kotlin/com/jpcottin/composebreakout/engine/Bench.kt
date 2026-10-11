package com.jpcottin.composebreakout.engine

/**
 * The Auto Play bench: plays seeded, headless rounds with the autopilot and
 * reports how often it clears each level. It arrives with the autopilot; for
 * now it proves the engine runs a whole round on a bare JVM with a crude
 * player that holds the strip under the ball.
 *
 *   ./gradlew :engine:run --args="--runs 100 --campaigns"
 */
fun main(args: Array<String>) {
    val game = Game()
    game.setAspect(0.45f)
    game.tap()
    game.update(1f / 60f)
    var frames = 0
    while (game.state == Game.State.PLAYING && frames < 60 * 120) {
        val ball = game.balls.firstOrNull()
        if (ball != null) { game.onPointerDown(0, ball.x, 0.9f); game.onPointerMove(0, ball.x, 0.9f) }
        game.update(1f / 60f)
        frames++
    }
    println("ComposeBreakout engine: ${game.state} after ${frames / 60} s, score ${game.score}, " +
        "bricks broken ${game.bricksBroken}, paddle hits ${game.paddleHits}, balls lost ${game.ballsLost}")
    if (args.isNotEmpty()) println("arguments ${args.toList()} are accepted once the autopilot lands")
}

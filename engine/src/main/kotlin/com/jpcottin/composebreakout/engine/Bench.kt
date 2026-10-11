package com.jpcottin.composebreakout.engine

/**
 * The Auto Play bench: plays seeded, headless rounds with the autopilot and
 * reports how often it clears each level. It arrives with the autopilot; for
 * now it only proves the engine runs on a bare JVM.
 *
 *   ./gradlew :engine:run --args="--runs 100 --campaigns"
 */
fun main(args: Array<String>) {
    val game = Game()
    game.setAspect(0.45f)
    repeat(60) { game.update(1f / 60f) }
    println("ComposeBreakout engine: title demo ball at (${"%.2f".format(game.demoBallX)}, ${"%.2f".format(game.demoBallY)}) after one second")
    if (args.isNotEmpty()) println("arguments ${args.toList()} are accepted once the autopilot lands")
}

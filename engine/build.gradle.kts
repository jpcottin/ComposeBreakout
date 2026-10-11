// The game, with nothing Android in it: the model, the simulation and the
// Auto Play autopilot. Its tests run on the JVM in seconds, and the Auto Play
// bench is a plain main().
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.jpcottin.composebreakout.engine.BenchKt")
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}

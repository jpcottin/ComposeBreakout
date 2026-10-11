plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.jpcottin.composebreakout"
    // The Jetpack XR projected APIs (Display AI Glasses) need compileSdk 37.
    compileSdk = 37

    // Required by the Compose Preview Screenshot Testing plugin (screenshotTest source set).
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "com.jpcottin.composebreakout"
        // 26: adaptive icons, VibrationEffect, float PCM AudioTrack. The glasses
        // path needs API 36 at runtime and is guarded; below that it stays off.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so a release build is installable for
            // verification; replace with a real signingConfig before any store
            // distribution.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // The system's per-app language picker (Android 13+) lists the app's
    // languages from a locale config generated out of the res/ folders.
    androidResources {
        generateLocaleConfig = true
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            // Compose Styles API (androidx.compose.foundation.style) and the
            // MediaQuery API (androidx.compose.ui.mediaQuery) are experimental.
            "-opt-in=androidx.compose.foundation.style.ExperimentalFoundationStyleApi",
            "-opt-in=androidx.compose.ui.ExperimentalMediaQueryApi",
            // ComposeUiFlags, used to switch MediaQuery integration on.
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
}

screenshotTests {
    // Text antialiasing differs a little between machines; allow 1% of pixels.
    imageDifferenceThreshold = 0.01f
}

dependencies {
    implementation(project(":engine"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    screenshotTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material.icons.extended)

    // Jetpack XR: projected activity on Display AI Glasses (API 36+ at runtime).
    implementation(libs.androidx.xr.runtime)
    implementation(libs.androidx.xr.projected)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

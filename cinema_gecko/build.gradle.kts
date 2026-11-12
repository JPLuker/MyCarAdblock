/*
 * Optional Android dynamic feature. The base :app must NOT depend on GeckoView; only this
 * module can import org.mozilla.geckoview. GitHub release packaging of the signed split is
 * a separate milestone and must be tested before exposing downloads to users.
 */
plugins {
    id("com.android.dynamic-feature")
}

// Single pinned coordinate for both Gradle resolution and the runtime diagnostic log.
val geckoVersion = "158.0.20261021001945"

android {
    namespace = "io.github.sirionero.mycar.cinema.gecko"
    compileSdk = 37
    defaultConfig {
        minSdk = 31
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "GECKOVIEW_VERSION", "\\"$geckoVersion\\"")
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":app"))
    // Pinned GeckoView stable from Mozilla. Keep synchronized with the matching signed split.
    // A/B diagnostic branch: the ARM64 device reproduces an identical native libxul.so
    // SIGSEGV on 159 even when uBlock/extension startup is bypassed. Keep the
    // production candidate on 159 while testing the previous stable Gecko engine.
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:$geckoVersion")
}

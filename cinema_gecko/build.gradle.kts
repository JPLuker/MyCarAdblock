/*
 * Optional Android dynamic feature. The base :app must NOT depend on GeckoView; only this
 * module can import org.mozilla.geckoview. GitHub release packaging of the signed split is
 * a separate milestone and must be tested before exposing downloads to users.
 */
plugins {
    alias(libs.plugins.android.dynamic.feature)
}

android {
    namespace = "io.github.sirionero.mycar.cinema.gecko"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":app"))
    // Pinned GeckoView stable from Mozilla. Keep synchronized with the matching signed split.
    implementation("org.mozilla.geckoview:geckoview:159.0.20261102144711")
}

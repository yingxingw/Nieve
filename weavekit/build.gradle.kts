/**
 * weavekit — WeaveMask (Miuix) UI kit, extracted from Seyud/WeaveMask (GPL-3.0).
 * Provides theme system, Miuix-style components (floating bottom bar, dialogs, blur,
 * liquid glass effects), navigation3 helpers. No Magisk/root feature code.
 *
 * namespace is deliberately "io.github.seyud.weave.core" so the kit's original
 * `io.github.seyud.weave.core.R` / `.BuildConfig` references keep working unchanged.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "io.github.seyud.weave.core"
    compileSdk {
        version = release(37)
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime)
    implementation(libs.compose.runtime.livedata)
    implementation(libs.compose.foundation)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.collection.ktx)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.androidx.navigation.compose)

    // Miuix
    api(libs.miuix.ui)
    api(libs.miuix.preference)
    api(libs.miuix.icons)
    api(libs.miuix.blur)
    api(libs.miuix.nav)

    // MarkdownText helper (ServiceLocator.markwon shim)
    implementation(libs.markwon.core)

    implementation(libs.timber)
    implementation(libs.libsu.io)
}

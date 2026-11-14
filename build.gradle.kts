// Nieve root build script.
// Merged project: WeaveMask UI (weavekit) + YunX (yunx) + SKIP (skip), unified app shell.
// App name: Nieve · Developer: 酷安初雪
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// 老依赖（utilcodex 等）传递引入的 kotlin-android-extensions-runtime 与
// kotlin-parcelize-runtime 类重复，统一排除（parcelize-runtime 是超集）。
subprojects {
    configurations.configureEach {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
    }
}

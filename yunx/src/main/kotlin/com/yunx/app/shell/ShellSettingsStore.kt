package com.yunx.app.shell

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Nieve shell settings (WeaveMask-style appearance switches), persisted in
 * SharedPreferences "weavex_settings".
 *
 * colorMode follows WeaveMagiskTheme: 0=system, 1=light, 2=dark,
 * 3=Monet(system), 4=Monet(light), 5=Monet(dark).
 */
class ShellSettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("weavex_settings", Context.MODE_PRIVATE)

    var colorMode: Int
        get() = prefs.getInt("color_mode", 2)
        set(value) = prefs.edit().putInt("color_mode", value).apply()

    var blur: Boolean
        get() = prefs.getBoolean("blur", true)
        set(value) = prefs.edit().putBoolean("blur", value).apply()

    var floatingBar: Boolean
        get() = prefs.getBoolean("floating_bar", true)
        set(value) = prefs.edit().putBoolean("floating_bar", value).apply()
}

class ShellSettingsState(private val store: ShellSettingsStore) {
    var colorMode by mutableIntStateOf(store.colorMode)
        private set
    var blur by mutableStateOf(store.blur)
        private set
    var floatingBar by mutableStateOf(store.floatingBar)
        private set

    fun updateColorMode(value: Int) {
        store.colorMode = value
        colorMode = value
    }

    fun updateBlur(value: Boolean) {
        store.blur = value
        blur = value
    }

    fun updateFloatingBar(value: Boolean) {
        store.floatingBar = value
        floatingBar = value
    }

    companion object {
        val COLOR_MODE_NAMES = listOf(
            "跟随系统", "浅色", "深色", "Monet 跟随系统", "Monet 浅色", "Monet 深色"
        )

        fun colorModeName(mode: Int): String =
            COLOR_MODE_NAMES.getOrElse(mode) { COLOR_MODE_NAMES[0] }
    }
}

@Composable
fun rememberShellSettings(): ShellSettingsState {
    val context = LocalContext.current
    return remember { ShellSettingsState(ShellSettingsStore(context.applicationContext)) }
}

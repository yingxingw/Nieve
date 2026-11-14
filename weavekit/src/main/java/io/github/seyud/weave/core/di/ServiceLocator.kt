package io.github.seyud.weave.core.di

import android.annotation.SuppressLint
import android.content.Context
import io.noties.markwon.Markwon

/**
 * Minimal shim of WeaveMask's `core.di.ServiceLocator`.
 *
 * The original ServiceLocator wires the whole Magisk core (root service, module repo,
 * su-log database…). The extracted UI kit only needs the shared [markwon] renderer
 * (used by MarkdownText), so this shim provides just that. The host application must
 * set [appContext] before any MarkdownText is rendered.
 */
@SuppressLint("StaticFieldLeak")
object ServiceLocator {

    @Volatile
    lateinit var appContext: Context

    val markwon: Markwon by lazy { Markwon.builder(appContext).build() }
}

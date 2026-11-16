package io.github.perruzz.stacco

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * A context that speaks the language chosen for Stacco.
 *
 * Android 13+ applies the per-app language to activities only: the application context, receivers,
 * workers, the widget and the Quick Settings tile may still use the phone's language (e.g. an
 * English phone with Stacco set to Italian). Everything shown outside the app goes through this.
 */
@SuppressLint("AppBundleLocaleChanges") // Distributed as a plain APK: every language is always installed.
fun Context.localized(): Context {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val locales = runCatching { getSystemService(LocaleManager::class.java)?.applicationLocales }.getOrNull()
    if (locales == null || locales.isEmpty || resources.configuration.locales == locales) return this
    val config = Configuration(resources.configuration).apply { setLocales(locales) }
    return createConfigurationContext(config)
}

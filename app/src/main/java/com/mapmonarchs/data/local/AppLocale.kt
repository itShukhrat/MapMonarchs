package com.mapmonarchs.data.local

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The single place the app's language is read and changed.
 *
 * Two mechanisms, split on API level, because per-app language is a platform
 * feature only from API 33 (Android 13) and this app supports API 26:
 *
 *  - 33+: the framework [LocaleManager]. The system persists the choice, applies
 *    it on every launch, surfaces it in Settings > App > Language, and restarts
 *    the activity when it changes.
 *  - 26..32: no framework support, so the tag is persisted in [LocalePreferences]
 *    and re-applied by wrapping the base Context's configuration in
 *    Activity.attachBaseContext; changing it recreates the activity by hand.
 *
 * Both paths converge on an activity restart, so the whole Compose tree re-reads
 * string resources for the new locale -- there is no partial in-place swap.
 */
object AppLocale {

    /**
     * Wrap [base] so its resources resolve in the chosen language. Called from
     * Activity.attachBaseContext. A no-op on 33+, where the system has already
     * wrapped the context from the LocaleManager value.
     */
    fun apply(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = LocalePreferences(base).languageTag
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** The active language tag; "" when following the system. */
    fun currentTag(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                .applicationLocales
                .takeUnless { it.isEmpty }
                ?.get(0)
                ?.language
                .orEmpty()
        } else {
            LocalePreferences(context).languageTag
        }

    /**
     * Switch the app language and restart so the change takes hold everywhere.
     * [tag] is "" for "follow the system", or a language tag like "ru" / "uz".
     */
    fun setLanguage(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(tag)
            // Setting applicationLocales is itself a configuration change; the
            // system restarts the activity, so nothing else is needed here.
        } else {
            LocalePreferences(context).languageTag = tag
            context.findActivity()?.recreate()
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

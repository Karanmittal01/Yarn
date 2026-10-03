package app.yarn.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Yarn's own language, independent of the phone's. On Android 13+ this is the system's per-app
 * language (also changeable in system settings); on older versions Yarn applies it itself.
 */
object AppLanguage {
    /** Languages Yarn is translated into, as BCP-47 tags. */
    val supported = listOf("en", "hi")
    private const val PREFS = "yarn_language"
    private const val KEY = "tag"

    /** The chosen language tag, or "" to follow the phone. */
    fun selected(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            return if (locales == null || locales.isEmpty) "" else locales[0].language
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
    }

    /** Switches Yarn's language; the screen is recreated in the new language. */
    fun select(activity: Activity, tag: String) {
        if (tag == selected(activity)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
            applyTo(activity.applicationContext)
            activity.recreate()
        }
    }

    /** The language Yarn is showing right now ("en", "hi"…). */
    fun current(context: Context): String = context.resources.configuration.locales[0].language

    /** Android 10–12: wraps an activity's context in the chosen language. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val locale = chosenLocale(base) ?: return base
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    /** Android 10–12: applies the chosen language to the app's own resources (notifications, workers). */
    @Suppress("DEPRECATION")
    fun applyTo(app: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val locale = chosenLocale(app) ?: Locale.getDefault()
        Locale.setDefault(locale)
        val res = app.resources
        val config = Configuration(res.configuration).apply { setLocales(LocaleList(locale)) }
        res.updateConfiguration(config, res.displayMetrics)
    }

    private fun chosenLocale(context: Context): Locale? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.takeIf { it.isNotEmpty() }?.let(Locale::forLanguageTag)

    /** A language's name written in that language: "English", "हिन्दी". */
    fun nativeName(tag: String): String {
        val l = Locale.forLanguageTag(tag)
        return l.getDisplayLanguage(l).replaceFirstChar { it.titlecase(l) }
    }
}

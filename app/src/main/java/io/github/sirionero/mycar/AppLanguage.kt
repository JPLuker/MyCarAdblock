package io.github.sirionero.mycar

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The languages myCar speaks, the same as Xonos's: on the phone, on the car's screen, and the
 * assistant's voice. The phone's own by default (English when it isn't one of these), or one
 * chosen in Settings. From Android 13 Android keeps the choice (it shows in Android's own settings
 * for myCar too) and every part of myCar gets it, with nothing restarted; on Android 12 myCar
 * applies it itself.
 */
enum class AppLanguage(
    val tag: String,
    val label: String,
    /** As Music Assistant names it, for Xonos inside myCar: it translates its own words into the connection's language. */
    val maLocale: String,
) {
    ENGLISH("en", "English", "en"),
    ITALIAN("it", "Italiano", "it"),
    SPANISH("es", "Español", "es"),
    GERMAN("de", "Deutsch", "de"),
    FRENCH("fr", "Français", "fr"),
    // Music Assistant's pt_PT is much more complete than its pt_BR, as the Xonos app found.
    PORTUGUESE("pt", "Português", "pt_PT"),
    GREEK("el", "Ελληνικά", "el_GR"),
    RUSSIAN("ru", "Русский", "ru_RU"),
    CHINESE("zh-CN", "简体中文", "zh_CN"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        private const val KEY = "app_language"
        private const val KEY_PIRATE = "app_language_pirate"

        /**
         * Pirate speak (the user's wish: every text in pirate terms, the emblem of free choice) is a way
         * of speaking each language, not a language of its own: the BCP 47 variant "pirate", whose
         * words are in res/values-b+<language>+pirate. A text not written there yet comes from the
         * language's own folder, as Android falls back to it.
         */
        const val PIRATE = "pirate"

        /** Pirate speak is on (see [PIRATE]). */
        fun pirate(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 33) {
                val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
                return locales != null && !locales.isEmpty && locales[0].variant.equals(PIRATE, ignoreCase = true)
            }
            return AppSettings.getString(context, KEY_PIRATE, "") == "on"
        }

        /** [language], spoken the pirate way if [pirate]. */
        fun localeFor(language: AppLanguage, pirate: Boolean): Locale =
            if (!pirate) language.locale else Locale.Builder().setLocale(language.locale).setVariant(PIRATE).build()

        /** The one chosen in Settings; null for the phone's. */
        fun chosen(context: Context): AppLanguage? {
            if (Build.VERSION.SDK_INT >= 33) {
                val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
                return if (locales == null || locales.isEmpty) null else of(locales[0])
            }
            val tag = AppSettings.getString(context, KEY, "")
            return entries.firstOrNull { it.tag == tag }
        }

        /** The language myCar is in now: the chosen one, else the phone's when it's one of these. */
        fun current(context: Context): AppLanguage = chosen(context) ?: of(phoneLocale()) ?: ENGLISH

        /** The phone's own language, whatever myCar is in. */
        fun phoneLocale(): Locale = Resources.getSystem().configuration.locales[0] ?: Locale.ENGLISH

        /** One of these for [locale], by language: any Chinese is the simplified one. */
        fun of(locale: Locale): AppLanguage? = entries.firstOrNull { it.locale.language == locale.language }

        /**
         * Chosen in Settings, or null for the phone's. Android 13 and later apply it at once; on
         * Android 12 myCar does, and its screen starts again unless a drive is under way (it would
         * end the drive), in which case the next start is in the new language.
         */
        fun choose(activity: Activity, language: AppLanguage?, pirate: Boolean = pirate(activity)) {
            AppSettings.putString(activity, KEY, language?.tag.orEmpty())
            AppSettings.putString(activity, KEY_PIRATE, if (pirate) "on" else "")
            if (Build.VERSION.SDK_INT >= 33) {
                // Android keeps one language for myCar: pirate speak with the phone's language is the
                // pirate way of the language myCar is in now.
                val spoken = language ?: if (pirate) current(activity) else null
                activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                    if (spoken == null) LocaleList.getEmptyLocaleList() else LocaleList(localeFor(spoken, pirate))
                return
            }
            applyToProcess(activity)
            recheck(activity)
            if (!SessionService.running) activity.recreate()
        }

        private val wantedFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

        /**
         * The language myCar's words must be in (null: the phone's), as it changes while myCar runs: for
         * the car's screen (ScreenProjector), which Android doesn't tell. Before, a language changed during
         * a drive reached [Words] at once and the car's screens only at the next connection, and the same
         * card came out in two languages.
         */
        val wantedTag: kotlinx.coroutines.flow.StateFlow<String?> = wantedFlow

        /** Reads it again: Android has changed the language (MyCarActivity hears it), or myCar has (Android 12). */
        fun recheck(context: Context) { wantedFlow.value = wanted(context)?.toLanguageTag() }

        /** Android 12: the chosen language for the whole process, services included. */
        fun applyToProcess(context: Context) {
            if (Build.VERSION.SDK_INT >= 33) return
            val locale = wanted(context) ?: phoneLocale()
            Locale.setDefault(locale)
            val resources = context.applicationContext.resources
            val config = Configuration(resources.configuration).apply { setLocale(locale) }
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        }

        /** Android 12: [base] in the chosen language, for an activity's attachBaseContext. */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= 33) return base
            val locale = wanted(base) ?: return base
            return inLanguage(base, locale)
        }

        private var appWords: Pair<String, Resources>? = null

        /**
         * The application's resources in myCar's language, for [Words]. Android 13 gives them so;
         * on Android 12 they come from a context of their own, which a change of the phone's
         * settings (a turn of the phone, dark mode) doesn't take back to the phone's language.
         */
        fun resources(app: Context): Resources {
            val locale = wanted(app) ?: return app.resources
            if (speaks(app, locale)) return app.resources
            val wantedTag = locale.toLanguageTag()
            appWords?.let { (tag, words) -> if (tag == wantedTag) return words }
            return inLanguage(app, locale).resources.also { appWords = wantedTag to it }
        }

        /**
         * [context] with its words in myCar's language, for a screen Android doesn't give them to:
         * the car's, on a display of its own, which comes with the phone's language on Android 12 and
         * on later ones too (Android 13 gives the language chosen for myCar to its activities, not to
         * a context made for another display). Display, density and theme stay [context]'s.
         */
        fun localized(context: Context): Context {
            val locale = wanted(context) ?: return context
            if (speaks(context, locale)) return context
            val words = inLanguage(context, locale).resources
            return object : ContextWrapper(context) {
                override fun getResources(): Resources = words
            }
        }

        /**
         * The language myCar's words must be in, null for the phone's own: from Android 13 the one
         * Android gives the process (its default locale, which the chosen language sets; read without
         * asking the system, as [Words] does it for every text), before it the one chosen here.
         */
        private fun wanted(context: Context): Locale? {
            // From Android 13 the default locale is the one Android gives myCar, pirate variant and all.
            if (Build.VERSION.SDK_INT >= 33) return Locale.getDefault().takeIf { of(it) != null }
            val language = chosen(context) ?: if (pirate(context)) current(context) else return null
            return localeFor(language, pirate(context))
        }

        /** [context]'s words are already in [locale]: its language, and pirate speak or not. */
        private fun speaks(context: Context, locale: Locale): Boolean {
            val own = context.resources.configuration.locales[0] ?: return false
            return own.language == locale.language && own.variant.equals(locale.variant, ignoreCase = true)
        }

        private fun inLanguage(context: Context, locale: Locale): Context =
            context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
    }
}

package de.luma.gallery.localization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.app.LocaleManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import de.luma.gallery.R
import java.util.Locale


enum class AppLanguage(val tag: String, val labelRes: Int, val nativeLabel:String?=null) {
    SYSTEM("", R.string.language_system), GERMAN("de", R.string.language_german), ENGLISH("en", R.string.language_english),
    SPANISH("es",R.string.language_heading,"Español"),
    FRENCH("fr",R.string.language_heading,"Français"),
    ITALIAN("it",R.string.language_heading,"Italiano"),
    PORTUGUESE("pt",R.string.language_heading,"Português"),
    DUTCH("nl",R.string.language_heading,"Nederlands"),
    POLISH("pl",R.string.language_heading,"Polski"),
    CZECH("cs",R.string.language_heading,"Čeština"),
    SLOVAK("sk",R.string.language_heading,"Slovenčina"),
    HUNGARIAN("hu",R.string.language_heading,"Magyar"),
    ROMANIAN("ro",R.string.language_heading,"Română"),
    GREEK("el",R.string.language_heading,"Ελληνικά"),
    TURKISH("tr",R.string.language_heading,"Türkçe"),
    RUSSIAN("ru",R.string.language_heading,"Русский"),
    UKRAINIAN("uk",R.string.language_heading,"Українська"),
    SWEDISH("sv",R.string.language_heading,"Svenska"),
    DANISH("da",R.string.language_heading,"Dansk"),
    NORWEGIAN("nb",R.string.language_heading,"Norsk bokmål"),
    FINNISH("fi",R.string.language_heading,"Suomi"),
    ARABIC("ar",R.string.language_heading,"العربية"),
    HEBREW("he",R.string.language_heading,"עברית"),
    PERSIAN("fa",R.string.language_heading,"فارسی"),
    HINDI("hi",R.string.language_heading,"हिन्दी"),
    BENGALI("bn",R.string.language_heading,"বাংলা"),
    INDONESIAN("id",R.string.language_heading,"Bahasa Indonesia"),
    MALAY("ms",R.string.language_heading,"Bahasa Melayu"),
    VIETNAMESE("vi",R.string.language_heading,"Tiếng Việt"),
    THAI("th",R.string.language_heading,"ไทย"),
    JAPANESE("ja",R.string.language_heading,"日本語"),
    KOREAN("ko",R.string.language_heading,"한국어"),
    CHINESE_SIMPLIFIED("zh-Hans",R.string.language_heading,"简体中文"),
    CHINESE_TRADITIONAL("zh-Hant",R.string.language_heading,"繁體中文");

    fun select() { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }

    companion object {
        val supported=entries.filter {it!=SYSTEM}.map {it.tag}.toSet()
        fun languageKey(locale:Locale)=when(locale.language) {"iw"->"he";"in"->"id";"no"->"nb";else->locale.language}
        fun supportedLocale(locale:Locale)=supported.any {languageKey(Locale.forLanguageTag(it))==languageKey(locale)}
        fun current():AppLanguage {
            val locale=AppCompatDelegate.getApplicationLocales()[0] ?: return SYSTEM
            if(locale.language=="zh") return if(locale.script=="Hant" || locale.country in listOf("TW","HK","MO")) CHINESE_TRADITIONAL else CHINESE_SIMPLIFIED
            return entries.firstOrNull {it.tag.isNotEmpty() && languageKey(Locale.forLanguageTag(it.tag))==languageKey(locale)} ?: SYSTEM
        }
    }
}


fun localizedResources(context: Context): Resources {
    val locales = if (Build.VERSION.SDK_INT >= 33)
        LocaleListCompat.wrap(context.getSystemService(LocaleManager::class.java).applicationLocales)
    else AppCompatDelegate.getApplicationLocales()
    val configuration = Configuration(context.resources.configuration)
    if (!locales.isEmpty) configuration.setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
    val supported = (0 until configuration.locales.size()).asSequence().map { configuration.locales[it] }
        .firstOrNull { AppLanguage.supportedLocale(it) } ?: Locale.ENGLISH

    if (configuration.locales[0] == supported && configuration.locales.size() == 1) return if (locales.isEmpty)
        context.resources else context.createConfigurationContext(configuration).resources
    configuration.setLocale(supported)
    return context.createConfigurationContext(configuration).resources
}


val Resources.appLocale: Locale
    get() = (0 until configuration.locales.size()).asSequence().map { configuration.locales[it] }
        .firstOrNull { AppLanguage.supportedLocale(it) } ?: Locale.ENGLISH

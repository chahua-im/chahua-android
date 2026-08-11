package net.paigu.chahua.data

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "chahua_settings",
)

enum class AppLanguage(val code: String, val displayNameRes: Int) {
    SYSTEM("", net.paigu.chahua.R.string.settings_language_system),
    ZH_CN("zh-CN", net.paigu.chahua.R.string.settings_language_simplified),
    ZH_TW("zh-TW", net.paigu.chahua.R.string.settings_language_traditional),
    EN("en", net.paigu.chahua.R.string.settings_language_english),
    ;

    companion object {
        fun from(code: String): AppLanguage = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

enum class ThemeColorOption(val key: String, val displayNameRes: Int) {
    SYSTEM("system", net.paigu.chahua.R.string.settings_theme_system),
    GREEN("green", net.paigu.chahua.R.string.settings_theme_green),
    BLUE("blue", net.paigu.chahua.R.string.settings_theme_blue),
    PURPLE("purple", net.paigu.chahua.R.string.settings_theme_purple),
    ORANGE("orange", net.paigu.chahua.R.string.settings_theme_orange),
    PINK("pink", net.paigu.chahua.R.string.settings_theme_pink),
    ;

    companion object {
        fun from(key: String): ThemeColorOption = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class FontSizeOption(val key: String, val scale: Float, val displayNameRes: Int) {
    SMALL("small", 0.85f, net.paigu.chahua.R.string.settings_font_small),
    NORMAL("normal", 1f, net.paigu.chahua.R.string.settings_font_normal),
    LARGE("large", 1.15f, net.paigu.chahua.R.string.settings_font_large),
    EXTRA_LARGE("extra_large", 1.35f, net.paigu.chahua.R.string.settings_font_extra_large),
    ;

    companion object {
        fun from(key: String): FontSizeOption = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

data class AppSettings(
    val showAllTab: Boolean = false,
    val fontSizeKey: String = FontSizeOption.NORMAL.key,
    val themeColor: String = ThemeColorOption.SYSTEM.key,
    val language: String = AppLanguage.SYSTEM.code,
    val notificationsEnabled: Boolean = true,
    val enterToSend: Boolean = true,
)

/**
 * 应用设置管理：外观（全部标签、字体大小、主题色）、语言、通知开关。
 * 与登录会话（SessionManager）分开存储，避免退出登录时清除用户偏好。
 */
class SettingsManager(context: Context) {

    private object Keys {
        val SHOW_ALL_TAB = booleanPreferencesKey("show_all_tab")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val THEME_COLOR = stringPreferencesKey("theme_color")
        val LANGUAGE = stringPreferencesKey("language")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val ENTER_TO_SEND = booleanPreferencesKey("enter_to_send")
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.settingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsState: Flow<AppSettings> = prefs.data.map { p ->
        AppSettings(
            showAllTab = p[Keys.SHOW_ALL_TAB] ?: false,
            fontSizeKey = p[Keys.FONT_SIZE] ?: FontSizeOption.NORMAL.key,
            themeColor = p[Keys.THEME_COLOR] ?: ThemeColorOption.SYSTEM.key,
            language = p[Keys.LANGUAGE] ?: AppLanguage.SYSTEM.code,
            notificationsEnabled = p[Keys.NOTIFICATIONS_ENABLED] ?: true,
            enterToSend = p[Keys.ENTER_TO_SEND] ?: true,
        )
    }

    /** 非挂起场景（如 Activity.attachBaseContext 应用语言）读取的最近快照。 */
    @Volatile
    private var snapshot: AppSettings = runBlocking { settingsState.first() }

    init {
        scope.launch {
            settingsState.collect { snapshot = it }
        }
    }

    suspend fun current(): AppSettings = settingsState.first()

    fun snapshot(): AppSettings = snapshot

    suspend fun setShowAllTab(enabled: Boolean) {
        prefs.edit { it[Keys.SHOW_ALL_TAB] = enabled }
        snapshot = snapshot.copy(showAllTab = enabled)
    }

    suspend fun setFontSize(key: String) {
        val normalized = FontSizeOption.from(key).key
        prefs.edit { it[Keys.FONT_SIZE] = normalized }
        snapshot = snapshot.copy(fontSizeKey = normalized)
    }

    suspend fun setThemeColor(key: String) {
        val normalized = ThemeColorOption.from(key).key
        prefs.edit { it[Keys.THEME_COLOR] = normalized }
        snapshot = snapshot.copy(themeColor = normalized)
    }

    suspend fun setLanguage(code: String) {
        val normalized = AppLanguage.from(code).code
        prefs.edit { it[Keys.LANGUAGE] = normalized }
        snapshot = snapshot.copy(language = normalized)
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
        snapshot = snapshot.copy(notificationsEnabled = enabled)
    }

    suspend fun setEnterToSend(enabled: Boolean) {
        prefs.edit { it[Keys.ENTER_TO_SEND] = enabled }
        snapshot = snapshot.copy(enterToSend = enabled)
    }
}

/** 按用户选择的语言包装 Context，使 stringResource 等读取对应语言资源。 */
object AppLocale {
    fun wrap(context: Context, languageCode: String): Context {
        val locale = parseLocale(languageCode) ?: return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun parseLocale(code: String): Locale? = when (code) {
        "zh-CN" -> Locale.SIMPLIFIED_CHINESE
        "zh-TW" -> Locale.TRADITIONAL_CHINESE
        "en" -> Locale.ENGLISH
        else -> null
    }
}

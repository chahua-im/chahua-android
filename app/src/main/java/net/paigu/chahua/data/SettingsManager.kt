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
    CUSTOM("custom", net.paigu.chahua.R.string.settings_theme_custom),
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

enum class ThemeModeOption(val key: String, val displayNameRes: Int) {
    SYSTEM("system", net.paigu.chahua.R.string.settings_theme_mode_system),
    LIGHT("light", net.paigu.chahua.R.string.settings_theme_mode_light),
    DARK("dark", net.paigu.chahua.R.string.settings_theme_mode_dark),
    ;

    companion object {
        fun from(key: String): ThemeModeOption = entries.firstOrNull { it.key == key } ?: SYSTEM
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
    val hideThreadsInAllTab: Boolean = false,
    val sortAllByLatest: Boolean = false,
    val fontSizeKey: String = FontSizeOption.NORMAL.key,
    val themeColor: String = ThemeColorOption.SYSTEM.key,
    val customThemeColor: String = "",
    val themeMode: String = ThemeModeOption.SYSTEM.key,
    val language: String = AppLanguage.SYSTEM.code,
    val notificationsEnabled: Boolean = true,
    val persistentNotificationEnabled: Boolean = true,
    val batteryOptimizationPromptDismissed: Boolean = false,
    val enterToSend: Boolean = true,
    val developerEnabled: Boolean = false,
    val showUidInChat: Boolean = false,
    val showLatency: Boolean = false,
    val showAvatarsInMessages: Boolean = true,
    val pinnedReactions: List<String> = DEFAULT_PINNED_REACTIONS,
    val recentReactions: List<String> = DEFAULT_RECENT_REACTIONS,
    val logLevel: String = LogLevelOption.INFO.key,
) {
    /** 快捷表情条：设置里钉住的优先，其余取最近用过的，最多 [MAX_PINNED_REACTIONS] 个。 */
    fun quickReactionEmojis(): List<String> =
        (pinnedReactions + recentReactions.filterNot { it in pinnedReactions })
            .take(MAX_PINNED_REACTIONS)
}

/**
 * 应用设置管理：外观（全部标签、字体大小、主题色）、语言、通知开关。
 * 与登录会话（SessionManager）分开存储，避免退出登录时清除用户偏好。
 */
class SettingsManager(context: Context) {

    private object Keys {
        val SHOW_ALL_TAB = booleanPreferencesKey("show_all_tab")
        val HIDE_THREADS_IN_ALL_TAB = booleanPreferencesKey("hide_threads_in_all_tab")
        val SORT_ALL_BY_LATEST = booleanPreferencesKey("sort_all_by_latest")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val THEME_COLOR = stringPreferencesKey("theme_color")
        val CUSTOM_THEME_COLOR = stringPreferencesKey("custom_theme_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val PERSISTENT_NOTIFICATION_ENABLED = booleanPreferencesKey("persistent_notification_enabled")
        val BATTERY_OPTIMIZATION_PROMPT_DISMISSED =
            booleanPreferencesKey("battery_optimization_prompt_dismissed")
        val ENTER_TO_SEND = booleanPreferencesKey("enter_to_send")
        val DEVELOPER_ENABLED = booleanPreferencesKey("developer_enabled")
        val SHOW_UID_IN_CHAT = booleanPreferencesKey("show_uid_in_chat")
        val SHOW_LATENCY = booleanPreferencesKey("show_latency")
        val SHOW_AVATARS_IN_MESSAGES = booleanPreferencesKey("show_avatars_in_messages")
        val PINNED_REACTIONS = stringPreferencesKey("pinned_reactions")
        val RECENT_REACTIONS = stringPreferencesKey("recent_reactions")
        val LOG_LEVEL = stringPreferencesKey("log_level")
    }

    private companion object {
        /** 列表分隔符：表情符号中不会出现，用于在单个字符串中保序存储。 */
        const val LIST_SEPARATOR = "\u001F"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.settingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsState: Flow<AppSettings> = prefs.data.map { p ->
        AppSettings(
            showAllTab = p[Keys.SHOW_ALL_TAB] ?: false,
            hideThreadsInAllTab = p[Keys.HIDE_THREADS_IN_ALL_TAB] ?: false,
            sortAllByLatest = p[Keys.SORT_ALL_BY_LATEST] ?: false,
            fontSizeKey = p[Keys.FONT_SIZE] ?: FontSizeOption.NORMAL.key,
            themeColor = p[Keys.THEME_COLOR] ?: ThemeColorOption.SYSTEM.key,
            customThemeColor = p[Keys.CUSTOM_THEME_COLOR] ?: "",
            themeMode = p[Keys.THEME_MODE] ?: ThemeModeOption.SYSTEM.key,
            language = p[Keys.LANGUAGE] ?: AppLanguage.SYSTEM.code,
            notificationsEnabled = p[Keys.NOTIFICATIONS_ENABLED] ?: true,
            persistentNotificationEnabled = p[Keys.PERSISTENT_NOTIFICATION_ENABLED] ?: true,
            batteryOptimizationPromptDismissed =
                p[Keys.BATTERY_OPTIMIZATION_PROMPT_DISMISSED] ?: false,
            enterToSend = p[Keys.ENTER_TO_SEND] ?: true,
            developerEnabled = p[Keys.DEVELOPER_ENABLED] ?: false,
            showUidInChat = p[Keys.SHOW_UID_IN_CHAT] ?: false,
            showLatency = p[Keys.SHOW_LATENCY] ?: false,
            showAvatarsInMessages = p[Keys.SHOW_AVATARS_IN_MESSAGES] ?: true,
            pinnedReactions = normalizePinnedReactions(
                decodeList(p[Keys.PINNED_REACTIONS]) ?: DEFAULT_PINNED_REACTIONS,
            ),
            recentReactions = normalizeRecentReactions(
                decodeList(p[Keys.RECENT_REACTIONS]) ?: DEFAULT_RECENT_REACTIONS,
            ),
            logLevel = p[Keys.LOG_LEVEL] ?: LogLevelOption.INFO.key,
        )
    }

    /** 非挂起场景（如 Activity.attachBaseContext 应用语言）读取的最近快照。 */
    @Volatile
    private var snapshot: AppSettings = runBlocking { settingsState.first() }

    init {
        AppLog.minLevel = LogLevelOption.from(snapshot.logLevel)
        scope.launch {
            settingsState.collect {
                snapshot = it
                AppLog.minLevel = LogLevelOption.from(it.logLevel)
            }
        }
    }

    suspend fun current(): AppSettings = settingsState.first()

    /** 会话输入草稿（按 chatId|threadId 存储）。 */
    suspend fun saveChatDraft(chatId: String, threadId: String?, text: String) {
        val key = draftKey(chatId, threadId)
        prefs.edit { store ->
            if (text.isBlank()) {
                store.remove(key)
            } else {
                store[key] = text
            }
        }
    }

    suspend fun chatDraft(chatId: String, threadId: String?): String =
        prefs.data.first()[draftKey(chatId, threadId)].orEmpty()

    private fun draftKey(chatId: String, threadId: String?): Preferences.Key<String> =
        stringPreferencesKey("chat_draft_${chatId}|${threadId.orEmpty()}")

    fun snapshot(): AppSettings = snapshot

    suspend fun setShowAllTab(enabled: Boolean) {
        prefs.edit { it[Keys.SHOW_ALL_TAB] = enabled }
        snapshot = snapshot.copy(showAllTab = enabled)
    }

    suspend fun setHideThreadsInAllTab(enabled: Boolean) {
        prefs.edit { it[Keys.HIDE_THREADS_IN_ALL_TAB] = enabled }
        snapshot = snapshot.copy(hideThreadsInAllTab = enabled)
    }

    suspend fun setSortAllByLatest(enabled: Boolean) {
        prefs.edit { it[Keys.SORT_ALL_BY_LATEST] = enabled }
        snapshot = snapshot.copy(sortAllByLatest = enabled)
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

    suspend fun setCustomThemeColor(hex: String) {
        val normalized = hex.trim().removePrefix("#")
        prefs.edit { it[Keys.CUSTOM_THEME_COLOR] = normalized }
        snapshot = snapshot.copy(customThemeColor = normalized)
    }

    suspend fun setThemeMode(key: String) {
        val normalized = ThemeModeOption.from(key).key
        prefs.edit { it[Keys.THEME_MODE] = normalized }
        snapshot = snapshot.copy(themeMode = normalized)
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

    suspend fun setPersistentNotificationEnabled(enabled: Boolean) {
        prefs.edit { it[Keys.PERSISTENT_NOTIFICATION_ENABLED] = enabled }
        snapshot = snapshot.copy(persistentNotificationEnabled = enabled)
    }

    suspend fun setBatteryOptimizationPromptDismissed(dismissed: Boolean) {
        prefs.edit { it[Keys.BATTERY_OPTIMIZATION_PROMPT_DISMISSED] = dismissed }
        snapshot = snapshot.copy(batteryOptimizationPromptDismissed = dismissed)
    }

    suspend fun setEnterToSend(enabled: Boolean) {
        prefs.edit { it[Keys.ENTER_TO_SEND] = enabled }
        snapshot = snapshot.copy(enterToSend = enabled)
    }

    suspend fun setDeveloperEnabled(enabled: Boolean) {
        prefs.edit { it[Keys.DEVELOPER_ENABLED] = enabled }
        snapshot = snapshot.copy(developerEnabled = enabled)
    }

    suspend fun setShowUidInChat(enabled: Boolean) {
        prefs.edit { it[Keys.SHOW_UID_IN_CHAT] = enabled }
        snapshot = snapshot.copy(showUidInChat = enabled)
    }

    suspend fun setShowLatency(enabled: Boolean) {
        prefs.edit { it[Keys.SHOW_LATENCY] = enabled }
        snapshot = snapshot.copy(showLatency = enabled)
    }

    suspend fun setShowAvatarsInMessages(enabled: Boolean) {
        prefs.edit { it[Keys.SHOW_AVATARS_IN_MESSAGES] = enabled }
        snapshot = snapshot.copy(showAvatarsInMessages = enabled)
    }

    /** 保存钉住的快捷表态（去重并限制最多 [MAX_PINNED_REACTIONS] 个）。 */
    suspend fun setPinnedReactions(reactions: List<String>) {
        val normalized = normalizePinnedReactions(reactions)
        prefs.edit { it[Keys.PINNED_REACTIONS] = encodeList(normalized) }
        snapshot = snapshot.copy(pinnedReactions = normalized)
    }

    /** 记录一次最近使用表态；钉住的表态不再重复记录。 */
    suspend fun addRecentReaction(emoji: String) {
        if (emoji.isBlank() || emoji in snapshot.pinnedReactions) return
        val normalized = normalizeRecentReactions(listOf(emoji) + snapshot.recentReactions)
        prefs.edit { it[Keys.RECENT_REACTIONS] = encodeList(normalized) }
        snapshot = snapshot.copy(recentReactions = normalized)
    }

    suspend fun setLogLevel(key: String) {
        val normalized = LogLevelOption.from(key).key
        prefs.edit { it[Keys.LOG_LEVEL] = normalized }
        snapshot = snapshot.copy(logLevel = normalized)
        AppLog.minLevel = LogLevelOption.from(normalized)
    }

    private fun normalizePinnedReactions(reactions: List<String>?): List<String> =
        reactions.orEmpty().distinct().filter { it.isNotBlank() }.take(MAX_PINNED_REACTIONS)

    private fun normalizeRecentReactions(reactions: List<String>?): List<String> =
        reactions.orEmpty().distinct().filter { it.isNotBlank() }.take(MAX_RECENT_REACTIONS)

    private fun encodeList(list: List<String>): String = list.joinToString(LIST_SEPARATOR)

    private fun decodeList(value: String?): List<String>? =
        value?.takeIf { it.isNotEmpty() }?.split(LIST_SEPARATOR)
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

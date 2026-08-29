package net.paigu.chahua.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.core.BatteryOptimization
import net.paigu.chahua.R
import net.paigu.chahua.data.UpdateChecker
import java.io.File

data class SettingsUiState(
    val message: String? = null,
    val loggedOut: Boolean = false,
)

data class CacheUiState(
    val totalBytes: Long = 0,
    val coilBytes: Long = 0,
    val computing: Boolean = false,
    val clearing: Boolean = false,
    val message: String? = null,
)

data class FriendVerificationUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val mode: String = "direct",
    val question: String = "",
    val message: String? = null,
)

data class UpdateUiState(
    val checking: Boolean = false,
    val updateAvailable: Boolean = false,
    val latestVersion: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val message: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _cacheState = MutableStateFlow(CacheUiState())
    val cacheState: StateFlow<CacheUiState> = _cacheState.asStateFlow()

    private val _friendVerificationState = MutableStateFlow(FriendVerificationUiState())
    val friendVerificationState: StateFlow<FriendVerificationUiState> =
        _friendVerificationState.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    val sessionState = AppGraph.session.sessionState
    val settingsState = AppGraph.settings.settingsState

    /** 添加一个 API 服务器到列表（不切换当前服务器）。 */
    fun addServerUrl(url: String) {
        viewModelScope.launch {
            runCatching { AppGraph.session.addServerUrl(url) }
                .onSuccess { added ->
                    _uiState.value = _uiState.value.copy(
                        message = getString(
                            if (added) R.string.settings_server_added else R.string.settings_server_exists,
                        ),
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        message = getString(R.string.settings_server_add_failed, it.message),
                    )
                }
        }
    }

    /** 切换到列表中的某个 API 服务器并重建实时连接。 */
    fun switchServerUrl(url: String) {
        viewModelScope.launch {
            runCatching {
                AppGraph.session.setServerUrl(url)
                AppGraph.engine.reconnectNow()
            }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        message = getString(R.string.settings_saved),
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        message = getString(R.string.settings_save_failed, it.message),
                    )
                }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun setShowAllTab(enabled: Boolean) {
        viewModelScope.launch { AppGraph.settings.setShowAllTab(enabled) }
    }

    fun setHideThreadsInAllTab(enabled: Boolean) {
        viewModelScope.launch { AppGraph.settings.setHideThreadsInAllTab(enabled) }
    }

    fun setFontSize(key: String) {
        viewModelScope.launch { AppGraph.settings.setFontSize(key) }
    }

    fun setThemeColor(key: String) {
        viewModelScope.launch { AppGraph.settings.setThemeColor(key) }
    }

    fun setThemeMode(key: String) {
        viewModelScope.launch { AppGraph.settings.setThemeMode(key) }
    }

    fun setCustomThemeColor(hex: String) {
        viewModelScope.launch {
            AppGraph.settings.setCustomThemeColor(hex)
            AppGraph.settings.setThemeColor("custom")
        }
    }

    fun setLanguage(code: String, onApplied: (() -> Unit)? = null) {
        viewModelScope.launch {
            AppGraph.settings.setLanguage(code)
            onApplied?.invoke()
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { AppGraph.settings.setNotificationsEnabled(enabled) }
    }

    /** 常驻通知开关：只控制通知栏常驻通知的显示/隐藏，后台服务与推送不受影响。 */
    fun setPersistentNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            AppGraph.settings.setPersistentNotificationEnabled(enabled)
            AppGraph.setPersistentNotification(getApplication(), enabled)
        }
    }

    /** 调起系统授权框，请求允许应用忽略电池优化。 */
    fun requestIgnoreBatteryOptimization() {
        BatteryOptimization.requestIgnore(getApplication())
    }

    /** 检查 GitHub 最新版本；有更新时弹窗提示，无更新/失败时用 Toast 提示。 */
    fun checkForUpdates() {
        if (_updateState.value.checking) return
        viewModelScope.launch {
            _updateState.value = _updateState.value.copy(checking = true, message = null)
            runCatching { UpdateChecker.checkLatest() }
                .onSuccess { result ->
                    _updateState.value = _updateState.value.copy(
                        checking = false,
                        updateAvailable = result.available,
                        latestVersion = result.latestVersion,
                        releaseNotes = result.releaseNotes,
                        downloadUrl = result.downloadUrl,
                        message = if (result.available) {
                            null
                        } else {
                            getString(R.string.update_already_latest)
                        },
                    )
                }
                .onFailure {
                    _updateState.value = _updateState.value.copy(
                        checking = false,
                        message = getString(R.string.update_check_failed),
                    )
                }
        }
    }

    fun dismissUpdateDialog() {
        _updateState.value = _updateState.value.copy(updateAvailable = false)
    }

    fun dismissUpdateMessage() {
        _updateState.value = _updateState.value.copy(message = null)
    }

    fun setEnterToSend(enabled: Boolean) {
        viewModelScope.launch { AppGraph.settings.setEnterToSend(enabled) }
    }

    fun setDeveloperEnabled(enabled: Boolean) {
        viewModelScope.launch { AppGraph.settings.setDeveloperEnabled(enabled) }
    }

    fun setShowUidInChat(enabled: Boolean) {
        viewModelScope.launch { AppGraph.settings.setShowUidInChat(enabled) }
    }

    fun setShowLatency(enabled: Boolean) {
        viewModelScope.launch { AppGraph.settings.setShowLatency(enabled) }
    }

    fun setShowAvatarsInMessages(enabled: Boolean) {
        viewModelScope.launch { AppGraph.settings.setShowAvatarsInMessages(enabled) }
    }

    fun setPinnedReactions(reactions: List<String>) {
        viewModelScope.launch { AppGraph.settings.setPinnedReactions(reactions) }
    }

    fun setLogLevel(key: String) {
        viewModelScope.launch { AppGraph.settings.setLogLevel(key) }
    }

    /** 拉取当前用户的好友验证设置。 */
    fun loadFriendVerification() {
        if (_friendVerificationState.value.loading) return
        viewModelScope.launch {
            _friendVerificationState.value = _friendVerificationState.value.copy(
                loading = true,
                message = null,
            )
            runCatching { AppGraph.api.friendSettings() }
                .onSuccess { settings ->
                    _friendVerificationState.value = FriendVerificationUiState(
                        mode = settings.mode,
                        question = settings.question.orEmpty(),
                    )
                }
                .onFailure {
                    _friendVerificationState.value = _friendVerificationState.value.copy(
                        loading = false,
                        message = getString(R.string.friend_verification_load_failed),
                    )
                }
        }
    }

    /** 删除列表中的某个服务器；若删除的是当前地址则自动切换到剩余第一个并重连。 */
    fun removeServerUrl(url: String) {
        viewModelScope.launch {
            val wasActive = AppGraph.session.snapshot().serverUrl == url
            runCatching { AppGraph.session.removeServerUrl(url) }
                .onSuccess {
                    if (wasActive) {
                        AppGraph.engine.reconnectNow()
                        _uiState.value = _uiState.value.copy(
                            message = getString(R.string.settings_saved),
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            message = getString(R.string.settings_server_removed),
                        )
                    }
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        message = getString(R.string.settings_server_add_failed, it.message),
                    )
                }
        }
    }

    /** 保存好友验证设置（mode: direct | need_message | question | forbid）。 */
    fun saveFriendVerification(mode: String, question: String) {
        if (_friendVerificationState.value.saving) return
        viewModelScope.launch {
            _friendVerificationState.value = _friendVerificationState.value.copy(
                saving = true,
                mode = mode,
                question = question,
                message = null,
            )
            runCatching {
                AppGraph.api.updateFriendSettings(
                    mode = mode,
                    question = if (mode == "question") question.trim() else null,
                )
            }
                .onSuccess {
                    _friendVerificationState.value = _friendVerificationState.value.copy(
                        saving = false,
                        message = getString(R.string.friend_verification_saved),
                    )
                }
                .onFailure {
                    _friendVerificationState.value = _friendVerificationState.value.copy(
                        saving = false,
                        message = getString(R.string.friend_verification_save_failed, it.message),
                    )
                }
        }
    }

    fun dismissFriendVerificationMessage() {
        _friendVerificationState.value = _friendVerificationState.value.copy(message = null)
    }

    fun loadCacheSize() {
        viewModelScope.launch {
            _cacheState.value = _cacheState.value.copy(computing = true, message = null)
            val app = getApplication<Application>()
            val total = folderSize(app.cacheDir)
            val coil = AppGraph.imageLoader.diskCache?.size ?: 0L
            _cacheState.value = _cacheState.value.copy(
                totalBytes = total,
                coilBytes = coil,
                computing = false,
            )
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _cacheState.value = _cacheState.value.copy(clearing = true, message = null)
            withContext(Dispatchers.IO) {
                AppGraph.imageLoader.diskCache?.clear()
                AppGraph.imageLoader.memoryCache?.clear()
                getApplication<Application>().cacheDir.listFiles()?.forEach { child ->
                    child.deleteRecursively()
                }
            }
            loadCacheSize()
            _cacheState.value = _cacheState.value.copy(
                clearing = false,
                message = getString(R.string.settings_cache_cleared),
            )
        }
    }

    fun dismissCacheMessage() {
        _cacheState.value = _cacheState.value.copy(message = null)
    }

    fun logout() {
        viewModelScope.launch {
            AppGraph.stopMessaging(getApplication())
            AppGraph.session.clear()
            AppGraph.store.clear()
            _uiState.value = _uiState.value.copy(loggedOut = true)
        }
    }

    private suspend fun folderSize(file: File): Long = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext 0L
        file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun getString(id: Int, vararg args: Any?): String =
        getApplication<Application>().getString(id, *args)
}

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
import net.paigu.chahua.R
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

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _cacheState = MutableStateFlow(CacheUiState())
    val cacheState: StateFlow<CacheUiState> = _cacheState.asStateFlow()

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

    fun setFontSize(key: String) {
        viewModelScope.launch { AppGraph.settings.setFontSize(key) }
    }

    fun setThemeColor(key: String) {
        viewModelScope.launch { AppGraph.settings.setThemeColor(key) }
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

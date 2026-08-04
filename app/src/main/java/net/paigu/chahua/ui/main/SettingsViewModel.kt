package net.paigu.chahua.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.R

data class SettingsUiState(
    val serverUrl: String = "",
    val saving: Boolean = false,
    val message: String? = null,
    val loggedOut: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val sessionState = AppGraph.session.sessionState

    fun load() {
        viewModelScope.launch {
            val state = AppGraph.session.current()
            _uiState.value = _uiState.value.copy(serverUrl = state.serverUrl)
        }
    }

    fun saveServerUrl(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, message = null)
            runCatching {
                AppGraph.session.setServerUrl(url)
                AppGraph.engine.reconnectNow()
            }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        serverUrl = AppGraph.session.snapshot().serverUrl,
                        message = getString(R.string.settings_saved),
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        message = getString(R.string.settings_save_failed, it.message),
                    )
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            AppGraph.stopMessaging(getApplication())
            AppGraph.session.clear()
            AppGraph.store.clear()
            _uiState.value = _uiState.value.copy(loggedOut = true)
        }
    }

    private fun getString(id: Int, vararg args: Any?): String =
        getApplication<Application>().getString(id, *args)
}

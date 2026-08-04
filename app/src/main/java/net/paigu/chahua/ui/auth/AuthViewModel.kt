package net.paigu.chahua.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.SessionManager
import net.paigu.chahua.R

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /**
     * 登录：key 可为 JWT（自动 Bearer 认证）或开发模式 UID（自动 X-User-Id 认证）。
     * 校验通过后保存会话，并尝试用 /users/auth-token 升级为 JWT。
     */
    fun login(key: String, serverUrl: String) {
        if (key.isBlank()) {
            _uiState.value = AuthUiState(error = getString(R.string.auth_error_key_blank))
            return
        }
        _uiState.value = AuthUiState(loading = true)
        viewModelScope.launch {
            try {
                val session = AppGraph.session
                session.setServerUrl(serverUrl)
                session.saveKey(key)
                val me = AppGraph.api.me()
                session.setMe(me)
                // UID 开发模式：尝试换取 JWT，方便后续 /ws/ticket 等接口
                if (!SessionManager.looksLikeJwt(key)) {
                    runCatching { AppGraph.api.authToken() }
                        .onSuccess { token -> session.saveJwt(token) }
                }
                _uiState.value = AuthUiState(success = true)
            } catch (e: Exception) {
                val message = when {
                    e is net.paigu.chahua.data.ApiException && e.statusCode == 401 ->
                        getString(R.string.auth_error_invalid)
                    else -> getString(R.string.auth_error_failed, e.message ?: getString(R.string.auth_error_network))
                }
                _uiState.value = AuthUiState(error = message)
            }
        }
    }

    private fun getString(id: Int, vararg args: Any?): String =
        getApplication<Application>().getString(id, *args)
}

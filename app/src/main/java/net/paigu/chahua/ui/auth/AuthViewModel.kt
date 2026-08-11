package net.paigu.chahua.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.ApiException
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
     * 登录：通过 shireyishunjian 账号密码接口换取 JWT，
     * 保存为 Bearer 认证并拉取当前用户信息。
     */
    fun login(username: String, password: String, serverUrl: String) {
        if (username.isBlank() || password.isEmpty()) {
            _uiState.value = AuthUiState(error = getString(R.string.auth_error_credentials_blank))
            return
        }
        _uiState.value = AuthUiState(loading = true)
        viewModelScope.launch {
            try {
                val token = try {
                    AppGraph.api.loginWithCredentials(username.trim(), password)
                } catch (e: ApiException) {
                    if (e.statusCode == 401 || e.statusCode == 403) {
                        _uiState.value = AuthUiState(error = getString(R.string.auth_error_invalid_credentials))
                        return@launch
                    }
                    throw e
                }
                completeJwtLogin(token, serverUrl)
                _uiState.value = AuthUiState(success = true)
            } catch (e: Exception) {
                _uiState.value = AuthUiState(error = mapError(e))
            }
        }
    }

    /** 隐藏菜单：直接输入服务器地址和 JWT 登录。 */
    fun loginWithJwt(jwt: String, serverUrl: String) {
        if (jwt.isBlank()) {
            _uiState.value = AuthUiState(error = getString(R.string.auth_error_jwt_blank))
            return
        }
        _uiState.value = AuthUiState(loading = true)
        viewModelScope.launch {
            try {
                completeJwtLogin(jwt.trim(), serverUrl)
                _uiState.value = AuthUiState(success = true)
            } catch (e: Exception) {
                _uiState.value = AuthUiState(error = mapError(e))
            }
        }
    }

    private suspend fun completeJwtLogin(jwt: String, serverUrl: String) {
        val session = AppGraph.session
        session.setServerUrl(serverUrl)
        session.saveJwt(jwt)
        try {
            val me = AppGraph.api.me()
            session.setMe(me)
        } catch (e: Exception) {
            // 校验失败时不保留未登录的会话，避免下次启动直接跳过登录页。
            session.clear()
            throw e
        }
    }

    private fun mapError(e: Exception): String = when {
        e is ApiException && e.statusCode == 401 ->
            getString(R.string.auth_error_invalid)
        else -> getString(R.string.auth_error_failed, e.message ?: getString(R.string.auth_error_network))
    }

    private fun getString(id: Int, vararg args: Any?): String =
        getApplication<Application>().getString(id, *args)
}

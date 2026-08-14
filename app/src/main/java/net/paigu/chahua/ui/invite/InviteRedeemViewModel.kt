package net.paigu.chahua.ui.invite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.models.InvitePreviewResponse
import net.paigu.chahua.data.models.RedeemInviteResponse

data class InviteRedeemUiState(
    val code: String = "",
    val preview: InvitePreviewResponse? = null,
    val loading: Boolean = false,
    val redeeming: Boolean = false,
    val error: String? = null,
    val joined: RedeemInviteResponse? = null,
)

class InviteRedeemViewModel(application: Application) : AndroidViewModel(application) {

    private val api = AppGraph.api

    private val _uiState = MutableStateFlow(InviteRedeemUiState())
    val uiState: StateFlow<InviteRedeemUiState> = _uiState.asStateFlow()

    fun updateCode(code: String) {
        _uiState.value = _uiState.value.copy(code = code)
    }

    fun lookup() {
        val code = _uiState.value.code.trim()
        if (code.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = getString(R.string.invite_invalid_code))
            return
        }
        if (_uiState.value.loading || _uiState.value.redeeming) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null,
                preview = null,
            )
            runCatching { api.invitePreview(code) }
                .onSuccess { preview ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        preview = preview,
                        joined = if (preview.alreadyMember) {
                            RedeemInviteResponse(chat = preview.chat)
                        } else {
                            null
                        },
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = getString(R.string.invite_preview_error, it.message),
                    )
                }
        }
    }

    fun redeem() {
        val code = _uiState.value.code.trim()
        if (code.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = getString(R.string.invite_invalid_code))
            return
        }
        if (_uiState.value.loading || _uiState.value.redeeming) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(redeeming = true, error = null)
            runCatching { api.redeemInvite(code) }
                .onSuccess { joined ->
                    _uiState.value = _uiState.value.copy(redeeming = false, joined = joined)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        redeeming = false,
                        error = getString(R.string.invite_redeem_failed, it.message),
                    )
                }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun getString(id: Int, vararg args: Any?): String =
        getApplication<Application>().getString(id, *args)
}

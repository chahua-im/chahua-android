package net.paigu.chahua.ui.invite

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.AppLocale
import net.paigu.chahua.data.models.InviteGroupDto
import net.paigu.chahua.data.models.InvitePreviewResponse
import net.paigu.chahua.ui.common.UserAvatar
import net.paigu.chahua.ui.theme.ChahuaTheme

class InviteRedeemActivity : ComponentActivity() {

    private val viewModel: InviteRedeemViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, AppGraph.settings.snapshot().language))
    }

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CHAT_NAME = "chat_name"

        fun createIntent(context: Context): Intent =
            Intent(context, InviteRedeemActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChahuaTheme {
                InviteRedeemScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteRedeemScreen(
    viewModel: InviteRedeemViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.joined) {
        val joined = uiState.joined ?: return@LaunchedEffect
        val activity = context.findActivity()
        if (activity != null) {
            Toast.makeText(
                activity,
                activity.getString(R.string.invite_joined_toast, joined.chat.name),
                Toast.LENGTH_SHORT,
            ).show()
            activity.setResult(
                android.app.Activity.RESULT_OK,
                Intent().putExtra(
                    InviteRedeemActivity.EXTRA_CHAT_ID,
                    joined.chat.id,
                ).putExtra(
                    InviteRedeemActivity.EXTRA_CHAT_NAME,
                    joined.chat.name,
                ),
            )
            activity.finish()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.invite_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.code,
                onValueChange = viewModel::updateCode,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.invite_code_label)) },
                placeholder = { Text(stringResource(R.string.invite_code_hint)) },
                singleLine = true,
                enabled = !uiState.loading && !uiState.redeeming,
                supportingText = {
                    Text(stringResource(R.string.invite_code_hint))
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = viewModel::lookup,
                enabled = uiState.code.isNotBlank() && !uiState.loading && !uiState.redeeming,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.invite_lookup))
                }
            }

            uiState.preview?.let { preview ->
                Spacer(modifier = Modifier.height(24.dp))
                InvitePreviewCard(
                    preview = preview,
                    redeeming = uiState.redeeming,
                    onRedeem = viewModel::redeem,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun InvitePreviewCard(
    preview: InvitePreviewResponse,
    redeeming: Boolean,
    onRedeem: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.invite_preview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            GroupInfoRow(group = preview.chat)
            Spacer(modifier = Modifier.height(16.dp))
            if (preview.alreadyMember) {
                Text(
                    text = stringResource(R.string.invite_already_member),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Button(
                    onClick = onRedeem,
                    enabled = !redeeming,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (redeeming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.invite_redeem))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupInfoRow(group: InviteGroupDto) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(
            url = group.avatar,
            name = group.name,
            size = 52.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!group.description.isNullOrBlank()) {
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

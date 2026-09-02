package net.paigu.chahua.ui.main

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.paigu.chahua.R
import net.paigu.chahua.data.UpdateCheckResult

/** 开屏阶段需要以 Material3 弹窗展示的内容。 */
internal sealed interface StartupDialog {

    /** 检测到新版本。 */
    data class Update(val result: UpdateCheckResult) : StartupDialog

    /** 建议忽略电池优化。 */
    data object Battery : StartupDialog
}

/**
 * 在 [net.paigu.chahua.ui.theme.ChahuaTheme] 内渲染开屏弹窗。
 * 调用方负责将 [dialog] 置空以关闭；同时可能有多个待展示弹窗时，
 * 由调用方决定展示顺序。
 */
@Composable
internal fun StartupDialogHost(
    dialog: StartupDialog?,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onNeverAskBatteryAgain: () -> Unit,
) {
    when (dialog) {
        is StartupDialog.Update -> {
            val result = dialog.result
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(stringResource(R.string.update_available_title, result.latestVersion))
                },
                text = {
                    Text(
                        result.releaseNotes.ifBlank {
                            stringResource(R.string.update_release_notes_empty)
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onDownload(result.downloadUrl) }) {
                        Text(stringResource(R.string.update_download))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_later))
                    }
                },
            )
        }
        StartupDialog.Battery -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(stringResource(R.string.battery_optimization_dialog_title))
                },
                text = {
                    Text(stringResource(R.string.battery_optimization_dialog_message))
                },
                dismissButton = {
                    TextButton(onClick = onNeverAskBatteryAgain) {
                        Text(stringResource(R.string.battery_optimization_dialog_never))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.battery_optimization_dialog_cancel))
                    }
                },
                confirmButton = {
                    TextButton(onClick = onOpenBatterySettings) {
                        Text(stringResource(R.string.battery_optimization_dialog_continue))
                    }
                },
            )
        }
        null -> Unit
    }
}

package net.paigu.chahua.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** 电池优化豁免：检测当前状态，并调起系统授权请求。 */
object BatteryOptimization {

    /** 是否已允许本应用忽略电池优化（Android 6.0 以下无此概念，视为已忽略）。 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 直接调起系统授权框（[Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS]），
     * 由用户在弹窗里一步允许本应用忽略电池优化，不再跳转系统设置列表让用户手动查找。
     * 个别 ROM 没有该授权界面时，退回系统“电池优化”设置页。
     */
    fun requestIgnore(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = newTaskAware(
            context,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}")),
        )
        if (runCatching { context.startActivity(intent) }.isFailure) {
            openSettings(context)
        }
    }

    /** 跳转系统“电池优化”设置页，由用户手动关闭本应用的电池优化。 */
    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val intent = newTaskAware(
                context,
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
            context.startActivity(intent)
        }
    }

    /**
     * 从非 Activity 上下文（Application / ViewModel）启动系统界面时必须带
     * FLAG_ACTIVITY_NEW_TASK，否则系统抛 AndroidRuntimeException 导致点击无反应。
     */
    private fun newTaskAware(context: Context, intent: Intent): Intent =
        if (context is Activity) intent else intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

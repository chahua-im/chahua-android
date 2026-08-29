package net.paigu.chahua.core

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

    /** 调起系统授权框，请求允许本应用忽略电池优化。 */
    fun requestIgnore(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }
    }
}

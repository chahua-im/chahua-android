package net.paigu.chahua.data

import android.util.Log
import net.paigu.chahua.R

/** 开发者菜单中可切换的日志输出等级。*/
enum class LogLevelOption(
    val key: String,
    val displayNameRes: Int,
) {
    DEBUG("debug", R.string.settings_log_level_debug),
    INFO("info", R.string.settings_log_level_info),
    WARN("warn", R.string.settings_log_level_warn),
    ERROR("error", R.string.settings_log_level_error),
    OFF("off", R.string.settings_log_level_off),
    ;

    companion object {
        fun from(key: String): LogLevelOption =
            entries.firstOrNull { it.key == key } ?: INFO
    }
}

/** 全局日志出口：所有业务日志统一走这里，按开发者设置的等级过滤。*/
object AppLog {

    @Volatile
    var minLevel: LogLevelOption = LogLevelOption.INFO

    fun d(tag: String, message: String) {
        if (minLevel != LogLevelOption.OFF && minLevel.ordinal <= LogLevelOption.DEBUG.ordinal) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (minLevel != LogLevelOption.OFF && minLevel.ordinal <= LogLevelOption.INFO.ordinal) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String) {
        if (minLevel != LogLevelOption.OFF && minLevel.ordinal <= LogLevelOption.WARN.ordinal) {
            Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String) {
        if (minLevel != LogLevelOption.OFF && minLevel.ordinal <= LogLevelOption.ERROR.ordinal) {
            Log.e(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        if (minLevel != LogLevelOption.OFF && minLevel.ordinal <= LogLevelOption.ERROR.ordinal) {
            Log.e(tag, message, throwable)
        }
    }
}

package cn.nizou.sxd.util

import android.content.SharedPreferences
import cn.nizou.sxd.MODULE_PREFS_NAME

/*
 * 激活状态检测（跨进程，基于 libxposed Remote Preferences）。
 */
object HookStatus {
    private const val KEY_HOOK_ACTIVE = "hook_active"
    private const val KEY_MODULE_ENV = "module_env"

    fun markActive(prefsRemote: SharedPreferences) {
        prefsRemote.edit().putBoolean(KEY_HOOK_ACTIVE, true).apply()
    }

    fun markEnv(prefsRemote: SharedPreferences, api: Int, framework: String) {
        val line = "libxposed API " + api + " · " + framework
        prefsRemote.edit().putString(KEY_MODULE_ENV, line).apply()
        runCatching {
            val ctx = currentApplication()
            ctx.getSharedPreferences(MODULE_PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit().putString(KEY_MODULE_ENV, line).apply()
        }
    }

    fun readEnv(prefsRemote: SharedPreferences?): String? {
        prefsRemote?.let { return it.getString(KEY_MODULE_ENV, null) }
        return runCatching {
            val ctx = currentApplication()
            ctx.getSharedPreferences(MODULE_PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getString(KEY_MODULE_ENV, null)
        }.getOrNull()
    }

    fun isActivated(prefsRemote: SharedPreferences?): Boolean =
        prefsRemote?.getBoolean(KEY_HOOK_ACTIVE, false) ?: localActive

    @Volatile
    var localActive: Boolean = false
        private set

    fun markLocalActive() {
        localActive = true
    }
}
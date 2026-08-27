package cn.nizou.sxd.util

import android.content.SharedPreferences

/**
 * 激活状态检测（跨进程，基于 libxposed Remote Preferences）。
 *
 * 背景：旧实现用「运行在宿主进程」或 `frameworkLoaded`（onModuleLoaded 置位）判激活，
 * 但独立打开模块设置页进程时该回调不触发、context 又是模块进程，导致设置页永远显示
 * "未激活"（真机复现，见 记忆/01 第 9 轮遗留）。
 *
 * 方案：宿主 `com.fenbi.android.leo` 进程在 `onPackageReady` 里 hook 成功后，用
 * `XposedModule.getRemotePreferences(MODULE_PREFS_NAME)` 写入 `hook_active=true`。
 * RemotePreferences 由框架跨进程同步，模块设置页进程（也被 LSPosed 注入）读取同一份，
 * 从而正确反映「模块是否已成功注入宿主」。
 */
object HookStatus {
    private const val KEY_HOOK_ACTIVE = "hook_active"

    /** 宿主进程 hook 成功后调用，标记激活（写入跨进程 RemotePreferences）。 */
    fun markActive(prefsRemote: SharedPreferences) {
        prefsRemote.edit().putBoolean(KEY_HOOK_ACTIVE, true).apply()
    }

    /**
     * 模块设置页进程调用：用模块自身的 RemotePreferences 读取标记。
     * @param prefsRemote 模块进程的 RemotePreferences；为 null 时回退到本进程静态标记。
     */
    fun isActivated(prefsRemote: SharedPreferences?): Boolean =
        prefsRemote?.getBoolean(KEY_HOOK_ACTIVE, false) ?: localActive

    /** 本进程静态标记（onModuleLoaded 时置位，作 RemotePreferences 不可用时的兜底）。 */
    @Volatile
    var localActive: Boolean = false
        private set

    fun markLocalActive() {
        localActive = true
    }
}

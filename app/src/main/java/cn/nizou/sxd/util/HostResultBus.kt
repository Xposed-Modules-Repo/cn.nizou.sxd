package cn.nizou.sxd.util

import android.content.Intent

/**
 * 宿主 Activity.onActivityResult 结果总线。
 *
 * 注入面板跑在宿主的 ComponentDialog（非 Activity，无 ActivityResultRegistry），起不了
 * SAF；而模块的透明 Activity（ConfigTransferActivity）在模块进程，拿不到宿主进程内存里的
 * 配置（真实配置 = 宿主 prefs，模块进程只能 root 读磁盘文件，文件缺失即导出失败）。
 *
 * 因此**宿主进程场景**（注入面板）改用：宿主 Activity.startActivityForResult 发起 SAF →
 * [hook.SettingHook] 钩住宿主设置页 onActivityResult → 分发到这里 → 面板回调在宿主进程内
 * 直接读/写 modulePrefs（内存权威，无需 root、无需磁盘文件）。
 *
 * 注意：dispatch 在宿主主线程（onActivityResult）调用；回调里勿做耗时 IO（用后台线程）。
 */
object HostResultBus {

    const val REQ_EXPORT_CONFIG = 0x5E17
    const val REQ_IMPORT_CONFIG = 0x5E18

    private val listeners = java.util.concurrent.ConcurrentHashMap<Int, (resultCode: Int, data: Intent?) -> Unit>()

    /** 注册一次性回调（触发后自动移除，防泄漏）。 */
    fun register(requestCode: Int, listener: (resultCode: Int, data: Intent?) -> Unit) {
        listeners[requestCode] = listener
    }

    fun unregister(requestCode: Int) {
        listeners.remove(requestCode)
    }

    /** 由 SettingHook 在宿主 Activity.onActivityResult 中调用。 */
    fun dispatch(requestCode: Int, resultCode: Int, data: Intent?) {
        val listener = listeners.remove(requestCode) ?: return
        runCatching { listener(resultCode, data) }
            .onFailure { logI("HostResultBus dispatch failed: $it") }
    }
}

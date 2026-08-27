package cn.nizou.sxd.util

import android.content.Context
import cn.nizou.sxd.HOST_PACKAGE_NAME

/**
 * 激活状态检测（照抄 WeKit `utils/hook_status/HookStatus.kt` 的意图，做适配简化）。
 *
 * 原版用 `io.github.libxposed.service.XposedService` 查宿主是否在 scope。但本项目 pinned 的
 * libxposed **102.0.0** 的 api 工件里并不含 `io.github.libxposed.service.*`（WeKit 是自备
 * compileOnly 桩，且只能在模块自身进程读到服务连接）。对「注入宿主面板」这一真实使用场景，
 * 模块代码既然已经在 `com.fenbi.android.leo` 进程里运行，本身就证明已激活，无需查 service。
 * 因此这里用「是否运行在宿主进程」作为最可靠信号；模块自身进程以框架是否加载本模块兜底。
 *
 * 验证依赖：真机 LSPosed 运行时按需补充 service 桩。
 */
object HookStatus {

    /** 框架是否在 `onModuleLoaded` 时加载了本模块（模块自身进程兜底信号）。 */
    @Volatile
    var frameworkLoaded: Boolean = false
        private set

    fun markFrameworkLoaded() {
        frameworkLoaded = true
    }

    /**
     * 是否已激活（能 hook 到宿主）。
     * - 宿主进程：模块已注入宿主 => 必然已激活。
     * - 模块自身进程：以框架是否加载本模块为准。
     */
    fun isActivated(context: Context): Boolean {
        if (context.packageName == HOST_PACKAGE_NAME) return true
        return frameworkLoaded
    }
}

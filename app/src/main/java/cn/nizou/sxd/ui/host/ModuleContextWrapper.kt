package cn.nizou.sxd.ui.host

import android.content.Context
import android.view.ContextThemeWrapper
import cn.nizou.sxd.util.ModuleResourceInjector

/**
 * 宿主进程内注入 Compose 用的上下文适配器（借鉴 WeKit CommonContextWrapper）。
 *
 * 宿主 Activity 的 context 的 classLoader 是宿主 classLoader，不包含模块的 Compose 运行库；
 * 直接 `new ComposeView(activity)` 时，Compose 内部按 context.getClassLoader() 加载类会
 * NoClassDefFound → 闪退。本包装器把 getClassLoader() 改为**模块 classLoader**（随模块 APK
 * 打包的 Compose 类所在 loader），并把模块资源注入其 Resources，使 Compose 组件能解析模块
 * 资源（resopt 保留包 ID 0x23）。
 *
 * 注意：宿主 Activity 仍是 window 的所有者，WINDOW_SERVICE / WINDOW_TOKEN 需回退宿主，
 * 否则注入的 ComposeView 无法正确获取窗口 token。
 */
class ModuleContextWrapper private constructor(
    base: Context,
    private val windowContext: Context?
) : ContextThemeWrapper(base, base.theme) {

    init {
        ModuleResourceInjector.injectModuleRes(resources)
    }

    override fun getClassLoader(): ClassLoader = moduleClassLoader

    override fun getSystemService(name: String): Any? {
        if (name == Context.WINDOW_SERVICE) {
            windowContext?.let { return it.getSystemService(name) }
        }
        return super.getSystemService(name)
    }

    companion object {
        /** 模块 classLoader：即加载本类（模块 APK）的 loader，含模块内打包的 Compose 运行库。 */
        private val moduleClassLoader: ClassLoader = ModuleContextWrapper::class.java.classLoader!!

        /** 包装宿主 Activity 的 context。找不到宿主 window 时退化为纯 module context。 */
        fun wrap(activity: Context): ModuleContextWrapper {
            val window = findActivity(activity)
            return ModuleContextWrapper(activity, window)
        }

        private fun findActivity(ctx: Context?): Context? {
            var cur: Context? = ctx
            while (cur != null) {
                if (cur is android.app.Activity) return cur
                cur = (cur as? android.content.ContextWrapper)?.baseContext
            }
            return null
        }
    }
}

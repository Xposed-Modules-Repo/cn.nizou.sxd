package cn.nizou.sxd.ui.host

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import java.util.WeakHashMap

/**
 * 注入宿主进程的 ComposeView 用的生命周期/状态宿主（借鉴 WeKit XposedLifecycleOwner +
 * LifecycleOwnerProvider）。
 *
 * 宿主 Activity（小猿口算 SettingsActivity）不是我们可信任的 ComponentActivity 生命周期来源，
 * 且模块注入的 ComposeView 独立于宿主窗口，需要一个自管理的 LifecycleOwner 来驱动 Compose
 * 重组与 SavedState。同时把**宿主 Activity 的真实生命周期事件转发**到 owner（onStart/onResume/
 * onPause/onStop/onDestroy），保证注入面板随宿主页面一起 pause/stop/destroy，避免泄漏。
 */
class XposedLifecycleOwner private constructor() :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    fun onResume() = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onPause() = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onStop() = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }

    companion object {
        fun create(): XposedLifecycleOwner =
            XposedLifecycleOwner().apply { onCreate(); onStart(); onResume() }

        /** 为宿主 Activity 创建/复用 owner，并转发宿主生命周期事件。 */
        fun forActivity(activity: Activity): XposedLifecycleOwner =
            LifecycleProvider.getOrCreate(activity)
    }
}

/** 为每个宿主 Activity 维护一个 owner，并把真实 Activity 生命周期转发给它（借鉴 WeKit）。 */
object LifecycleProvider {

    private val map = WeakHashMap<Activity, XposedLifecycleOwner>()

    fun getOrCreate(activity: Activity): XposedLifecycleOwner {
        return map.getOrPut(activity) {
            val owner = XposedLifecycleOwner.create()
            val app = activity.application ?: return@getOrPut owner
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(a: Activity) { if (a === activity) owner.onStart() }
                override fun onActivityResumed(a: Activity) { if (a === activity) owner.onResume() }
                override fun onActivityPaused(a: Activity) { if (a === activity) owner.onPause() }
                override fun onActivityStopped(a: Activity) { if (a === activity) owner.onStop() }
                override fun onActivityDestroyed(a: Activity) {
                    if (a === activity) {
                        owner.onDestroy()
                        app.unregisterActivityLifecycleCallbacks(this)
                        map.remove(activity)
                    }
                }
                override fun onActivityCreated(a: Activity, savedInstanceState: Bundle?) {}
                override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) {}
            })
            owner
        }
    }
}

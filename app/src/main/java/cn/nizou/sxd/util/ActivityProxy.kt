package cn.nizou.sxd.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityThread
import android.app.Application
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.PersistableBundle
import android.util.Log
import cn.nizou.sxd.HOST_PACKAGE_NAME
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 模块 Activity 借壳引擎（移植 WeKit `ActivityProxy`）。
 *
 * 目标：让模块的 `HostSettingsActivity` 以**真 Activity** 形态寄生在宿主（小猿口算）进程运行，
 * 从而获得 ComponentDialog 无法提供的系统 Activity 转场动画与预测返回（用户 2026-08-31 决策）。
 *
 * 机制（与 WeKit 逐字同构，全部为「反射字段替换 + 动态代理 + Instrumentation 子类」，不依赖
 * libxposed 方法 hook，仅需宿主进程内反射能力）：
 *  1. hook Instrumentation：替换 ActivityThread.mInstrumentation。`newActivity` 拦截——目标类名
 *     是模块代理 Activity 时用**模块 ClassLoader** 加载创建（宿主 ClassLoader 里没有模块类）；
 *     `callActivityOnCreate` 注入模块资源 + 设置 hybrid ClassLoader。
 *  2. hook IActivityManager / IActivityTaskManager：Singleton.mInstance 替换为动态代理。
 *     `startActivity*` 时若 Intent 目标是模块代理 Activity → 把 Intent 换成宿主**壳 Activity**
 *     （SplashActivity / RouterActivity，宿主 manifest 已注册，system_server 能解析），真实 Intent
 *     存 IntentTokenCache（60s），壳 Intent 带 token 走系统栈。
 *  3. hook Handler mH：ActivityThread.mH.mCallback 替换。处理 LAUNCH_ACTIVITY(100) /
 *     EXECUTE_TRANSACTION(159) 消息，把 ActivityClientRecord 里的壳 Intent 换回真实 Intent。
 *  4. hook PackageManager：ActivityThread.sPackageManager + PackageManager.mPM 替换为动态代理，
 *     `getActivityInfo` 对模块代理 Activity 返回伪造 ActivityInfo（packageName=宿主包名，
 *     launchMode=LAUNCH_MULTIPLE），避免系统在宿主进程内按宿主包校验模块 Activity 失败。
 *
 * 结果：用户从宿主设置页点「老挂戏老叟设置」→ startActivity(模块 Activity) → 系统以为启动了
 * 宿主 SplashActivity → 实际创建的实例是模块 HostSettingsActivity（完整 Activity 生命周期、
 * 转场动画、预测返回），配置读写仍在宿主进程直读（与 ComponentDialog 相同，功能不受影响）。
 */
object ActivityProxy {

    private const val TAG = "ActivityProxy"

    // ------------------------------------------------------------------ 配置

    /** 模块包名前缀：类名以它开头的 Activity 视为「模块代理 Activity」（借壳目标）。 */
    private const val MODULE_PACKAGE_PREFIX = "cn.nizou.sxd"

    /** 这些模块 Activity 不借壳（在模块独立进程运行，从桌面/SAF 启动）。 */
    private val NON_PROXY_ACTIVITIES = listOf("MainActivity", "ConfigTransferActivity")

    /** 壳 Activity（宿主 manifest 已注册，system_server 可解析）：
     *  Settings 类模块 Activity 借 SplashActivity（宿主启动页，栈顶概率最低）；
     *  其它模块 Activity 借 RouterActivity（透明中转页，系统级中转语义）。 */
    private const val SETTINGS_PROXY = "com.fenbi.android.leo.splash.SplashActivity"
    private const val TRANSPARENT_PROXY = "com.fenbi.android.leo.activity.RouterActivity"

    private const val ACTIVITY_PROXY_INTENT_TOKEN = "sxd_activity_proxy_token"

    // ------------------------------------------------------------------ 状态

    private var initialized = false
    private var hostApplicationInfo: ApplicationInfo? = null
    private var moduleClassLoader: ClassLoader? = null
    private var hostClassLoader: ClassLoader? = null

    /** hybrid ClassLoader：boot(parent) → 模块 → 宿主，供模块 Activity 内类加载与 Intent extras 反序列化。 */
    val hybridClassLoader: ClassLoader by lazy {
        object : ClassLoader(Context::class.java.classLoader) {
            override fun findClass(name: String): Class<*> {
                moduleClassLoader?.let { mod ->
                    runCatching { return mod.loadClass(name) }.getOrNull()
                }
                hostClassLoader?.let { host ->
                    runCatching { return host.loadClass(name) }.getOrNull()
                }
                throw ClassNotFoundException(name)
            }
        }
    }

    /**
     * 初始化借壳引擎。必须在宿主进程早期（Application.attach 之后、任何模块 Activity 启动前）
     * 调用一次；失败不影响其余 hook（runCatching 包裹）。
     *
     * @param appContext 宿主 Application context（拿 applicationInfo）。
     * @param moduleCl 模块 ClassLoader（加载 HostSettingsActivity 等模块类）。
     * @param hostCl 宿主 Application ClassLoader（加载宿主类，作为 hybrid 兜底）。
     */
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    fun init(appContext: Context, moduleCl: ClassLoader, hostCl: ClassLoader) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            runCatching {
                hostApplicationInfo = appContext.applicationInfo
                moduleClassLoader = moduleCl
                hostClassLoader = hostCl

                val clazzActivityThread = Class.forName("android.app.ActivityThread")
                val currentActivityThread = ActivityThread.currentActivityThread()
                    ?: throw IllegalStateException("ActivityThread not ready")

                // 1) hook Instrumentation（newActivity / callActivityOnCreate 拦截）
                val mInstrumentationField = clazzActivityThread.getDeclaredField("mInstrumentation")
                    .apply { isAccessible = true }
                val instrumentation = mInstrumentationField.get(currentActivityThread) as Instrumentation
                if (instrumentation !is ProxyInstrumentation) {
                    mInstrumentationField.set(currentActivityThread, ProxyInstrumentation(instrumentation))
                }

                // 2) hook Handler mH（LAUNCH_ACTIVITY / EXECUTE_TRANSACTION 还原 Intent）
                val oriHandler = clazzActivityThread.getDeclaredField("mH")
                    .apply { isAccessible = true }.get(currentActivityThread) as Handler
                val callbackField = Handler::class.java.getDeclaredField("mCallback").apply { isAccessible = true }
                val current = callbackField.get(oriHandler) as? Handler.Callback
                if (current == null || current.javaClass.name != ProxyHandlerCallback::class.java.name) {
                    callbackField.set(oriHandler, ProxyHandlerCallback(current))
                }

                // 3) hook IActivityManager / IActivityTaskManager（startActivity 换壳）
                hookActivityManagerProxy()

                // 4) hook PackageManager（getActivityInfo 伪造模块 Activity）
                hookPackageManager(currentActivityThread, clazzActivityThread)

                initialized = true
                Log.i(TAG, "ActivityProxy initialized")
            }.onFailure {
                Log.e(TAG, "ActivityProxy init failed: ${it.message}", it)
            }
        }
    }

    // ------------------------------------------------------------------ IActivityManager 换壳

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun hookActivityManagerProxy() {
        fun hookSingleton(singleton: Any?, iface: Class<*>) {
            if (singleton == null) return
            runCatching {
                val singletonClass = singleton.javaClass
                singletonClass.getDeclaredMethod("get").apply { isAccessible = true }.invoke(singleton)
            }
            val instanceField = singleton.javaClass.getDeclaredField("mInstance").apply { isAccessible = true }
            val instance = instanceField.get(singleton) ?: return
            val proxy = Proxy.newProxyInstance(
                ActivityProxy::class.java.classLoader,
                arrayOf(iface),
                ActivityManagerInvocationHandler(instance),
            )
            instanceField.set(singleton, proxy)
        }

        // API 25-：ActivityManagerNative.gDefault；API 26+：ActivityManager.IActivityManagerSingleton
        val (_, defField) = runCatching {
            val c = Class.forName("android.app.ActivityManagerNative")
            c to c.getDeclaredField("gDefault").apply { isAccessible = true }
        }.getOrElse {
            val c = Class.forName("android.app.ActivityManager")
            c to c.getDeclaredField("IActivityManagerSingleton").apply { isAccessible = true }
        }
        hookSingleton(defField.get(null), Class.forName("android.app.IActivityManager"))

        // Android 10+：IActivityTaskManagerSingleton
        runCatching {
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val singleton = atmClass.getDeclaredField("IActivityTaskManagerSingleton")
                .apply { isAccessible = true }.get(null)
            hookSingleton(singleton, Class.forName("android.app.IActivityTaskManager"))
        }
    }

    @SuppressLint("PrivateApi")
    private fun hookPackageManager(sCurrentActivityThread: Any, clazzActivityThread: Class<*>) {
        runCatching {
            val sPackageManagerField = clazzActivityThread.getDeclaredField("sPackageManager")
                .apply { isAccessible = true }
            val packageManagerImpl = sPackageManagerField.get(sCurrentActivityThread) ?: return
            val iPackageManagerInterface = Class.forName("android.content.pm.IPackageManager")
            val pm = ActivityThread.currentActivityThread()?.applicationContext?.packageManager ?: return
            val mPmField = pm.javaClass.getDeclaredField("mPM").apply { isAccessible = true }

            val pmProxy = Proxy.newProxyInstance(
                iPackageManagerInterface.classLoader,
                arrayOf(iPackageManagerInterface),
                PackageManagerInvocationHandler(packageManagerImpl),
            )
            sPackageManagerField.set(sCurrentActivityThread, pmProxy)
            mPmField.set(pm, pmProxy)
        }.onFailure { Log.e(TAG, "hookPackageManager failed (non-fatal): ${it.message}") }
    }

    // ------------------------------------------------------------------ 判定

    /** 类名是否为「模块代理 Activity」（借壳目标）。 */
    fun isModuleProxyActivity(className: String?): Boolean =
        className?.startsWith(MODULE_PACKAGE_PREFIX) == true &&
            NON_PROXY_ACTIVITIES.none { className.contains(it) }

    // ------------------------------------------------------------------ 内部类型

    /** 换壳判定：仅模块代理 Activity 的 Intent 需要换壳。 */
    private fun shouldProxy(intent: Intent): Boolean =
        intent.component?.let { isModuleProxyActivity(it.className) } == true

    private fun createTokenWrapper(raw: Intent): Intent {
        val token = IntentTokenCache.put(Intent(raw))
        val className = raw.component!!.className
        val proxyClass = if (className.contains("SettingsActivity")) SETTINGS_PROXY else TRANSPARENT_PROXY
        return Intent().apply {
            // ⚠️ 壳是宿主的 Activity：包名必须显式用宿主包名（raw 的 component 是模块包名，
            // system_server 会按「包名+类名」解析壳，用模块包名会找不到宿主 SplashActivity）。
            component = ComponentName(HOST_PACKAGE_NAME, proxyClass)
            flags = raw.flags
            action = raw.action
            setDataAndType(raw.data, raw.type)
            raw.categories?.forEach { addCategory(it) }
            putExtra(ACTIVITY_PROXY_INTENT_TOKEN, token)
            setExtrasClassLoader(hybridClassLoader)
        }.also {
            Log.i(TAG, "hijacked startActivity: $className -> $proxyClass")
        }
    }

    /** IActivityManager / IActivityTaskManager 动态代理：拦截 startActivity 系列换壳。 */
    private inner class ActivityManagerInvocationHandler(private val origin: Any) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            val mutableArgs = args ?: emptyArray()
            if (method.name.startsWith("startActivity")) {
                mutableArgs.forEachIndexed { i, arg ->
                    when (arg) {
                        is Intent -> if (shouldProxy(arg)) mutableArgs[i] = createTokenWrapper(arg)
                        is Array<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            val intents = arg as? Array<Intent?> ?: return@forEachIndexed
                            intents.forEachIndexed { j, intent ->
                                if (intent != null && shouldProxy(intent)) intents[j] = createTokenWrapper(intent)
                            }
                        }
                    }
                }
            }
            return try {
                method.invoke(origin, *mutableArgs)
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    /** ActivityThread.mH 回调：LAUNCH_ACTIVITY / EXECUTE_TRANSACTION 时把壳 Intent 还原为真实 Intent。 */
    private inner class ProxyHandlerCallback(private val next: Handler.Callback?) : Handler.Callback {
        private data class RecoveredIntent(val token: String, val intent: Intent)

        override fun handleMessage(msg: Message): Boolean {
            when (msg.what) {
                100 -> handleLaunchActivity(msg)      // LAUNCH_ACTIVITY（< Android 9）
                159 -> handleExecuteTransaction(msg)  // EXECUTE_TRANSACTION（>= Android 9）
            }
            return runCatching { next?.handleMessage(msg) == true }
                .onFailure { Log.e(TAG, "next callback failed", it) }
                .getOrDefault(false)
        }

        private fun recoverIntent(wrapper: Intent?): RecoveredIntent? {
            wrapper ?: return null
            wrapper.setExtrasClassLoader(hybridClassLoader)
            if (!wrapper.hasExtra(ACTIVITY_PROXY_INTENT_TOKEN)) return null
            val token = wrapper.getStringExtra(ACTIVITY_PROXY_INTENT_TOKEN) ?: return null
            val real = IntentTokenCache.get(token) ?: run {
                Log.w(TAG, "token expired or lost: $token")
                return null
            }
            real.setExtrasClassLoader(hybridClassLoader)
            real.extras?.classLoader = hybridClassLoader
            return RecoveredIntent(token, real)
        }

        private fun handleLaunchActivity(msg: Message) {
            runCatching {
                val record = msg.obj
                val intentField = record.javaClass.getDeclaredField("intent").apply { isAccessible = true }
                val wrapper = intentField.get(record) as? Intent
                recoverIntent(wrapper)?.let { recovered ->
                    intentField.set(record, recovered.intent)
                    IntentTokenCache.remove(recovered.token)
                }
            }.onFailure { Log.e(TAG, "handleLaunchActivity error", it) }
        }

        private fun handleExecuteTransaction(msg: Message) {
            runCatching {
                val transaction = msg.obj
                val callbacks = transaction.javaClass.getDeclaredMethod("getCallbacks")
                    .apply { isAccessible = true }.invoke(transaction) as? List<*> ?: return

                callbacks.forEach { item ->
                    if (item != null && item.javaClass.name.contains("LaunchActivityItem")) {
                        val intentField = item.javaClass.getDeclaredField("mIntent").apply { isAccessible = true }
                        val wrapper = intentField.get(item) as? Intent
                        recoverIntent(wrapper)?.let { recovered ->
                            intentField.set(item, recovered.intent)

                            // Android 12 保留第二份 ActivityClientRecord：只改 LaunchActivityItem
                            // 时 ActivityThread 会实例化壳 Activity，需同步更新 launching record。
                            if (Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2) {
                                updateLaunchingActivityIntent(transaction, recovered.intent)
                            }

                            IntentTokenCache.remove(recovered.token)
                        }
                    }
                }
            }.onFailure { Log.e(TAG, "handleExecuteTransaction error", it) }
        }

        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        private fun updateLaunchingActivityIntent(transaction: Any, intent: Intent) {
            val token = transaction.javaClass.getMethod("getActivityToken").invoke(transaction) as IBinder
            val activityThread = ActivityThread.currentActivityThread()
            val record = activityThread.javaClass
                .getMethod("getLaunchingActivity", IBinder::class.java)
                .invoke(activityThread, token) ?: return
            record.javaClass.getDeclaredField("intent").apply { isAccessible = true }
                .set(record, intent)
        }
    }

    /** Instrumentation 子类：拦截模块 Activity 的创建与 onCreate 前置（资源注入 + hybrid loader）。 */
    private inner class ProxyInstrumentation(private val base: Instrumentation) : Instrumentation() {

        @SuppressLint("NewApi")
        override fun newActivity(cl: ClassLoader, className: String, intent: Intent): Activity {
            if (isModuleProxyActivity(className)) {
                val moduleCl = moduleClassLoader ?: cl
                runCatching {
                    return moduleCl.loadClass(className).getDeclaredConstructor().newInstance() as Activity
                }.onFailure { e ->
                    Log.e(TAG, "module activity load failed: $className", e)
                }
            }
            return base.newActivity(cl, className, intent)
        }

        override fun newActivity(
            clazz: Class<*>, context: Context, token: IBinder, application: Application,
            intent: Intent, info: ActivityInfo, title: CharSequence, parent: Activity?,
            id: String?, lastNonConfigurationInstance: Any?,
        ): Activity = base.newActivity(
            clazz, context, token, application, intent, info, title, parent, id,
            lastNonConfigurationInstance,
        )

        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
            if (isModuleProxyActivity(activity.javaClass.name)) {
                ModuleResourceInjector.injectModuleRes(activity.resources)
                runCatching {
                    Activity::class.java.getDeclaredField("mClassLoader")
                        .apply { isAccessible = true }.set(activity, hybridClassLoader)
                }
                activity.intent?.let { intent ->
                    intent.setExtrasClassLoader(hybridClassLoader)
                    intent.extras?.classLoader = hybridClassLoader
                }
            }
            base.callActivityOnCreate(activity, icicle)
        }

        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?, persistentState: PersistableBundle?) {
            if (isModuleProxyActivity(activity.javaClass.name)) {
                ModuleResourceInjector.injectModuleRes(activity.resources)
            }
            base.callActivityOnCreate(activity, icicle, persistentState)
        }

        // ---- 以下方法全部委托 base（保证各 Android 版本行为与替换前一致） ----

        override fun onCreate(arguments: Bundle?) = base.onCreate(arguments)
        override fun start() = base.start()
        override fun onStart() = base.onStart()
        override fun onException(obj: Any?, e: Throwable?) = base.onException(obj, e)
        override fun sendStatus(resultCode: Int, results: Bundle?) = base.sendStatus(resultCode, results)
        override fun addResults(results: Bundle?) = base.addResults(results)
        override fun finish(resultCode: Int, results: Bundle?) = base.finish(resultCode, results)
        override fun setAutomaticPerformanceSnapshots() = base.setAutomaticPerformanceSnapshots()
        override fun startPerformanceSnapshot() = base.startPerformanceSnapshot()
        override fun endPerformanceSnapshot() = base.endPerformanceSnapshot()
        override fun onDestroy() = base.onDestroy()
        override fun getContext(): Context? = base.context
        override fun getComponentName(): ComponentName? = base.componentName
        override fun getTargetContext(): Context? = base.targetContext
        override fun getProcessName(): String? = base.processName
        override fun isProfiling(): Boolean = base.isProfiling
        override fun startProfiling() = base.startProfiling()
        override fun stopProfiling() = base.stopProfiling()
        override fun setInTouchMode(inTouch: Boolean) = base.setInTouchMode(inTouch)
        override fun waitForIdle(recipient: Runnable?) = base.waitForIdle(recipient)
        override fun waitForIdleSync() = base.waitForIdleSync()
        override fun runOnMainSync(runner: Runnable?) = base.runOnMainSync(runner)
        override fun startActivitySync(intent: Intent): Activity? = base.startActivitySync(intent)
        override fun startActivitySync(intent: Intent, options: Bundle?): Activity =
            base.startActivitySync(intent, options)
        override fun addMonitor(monitor: Instrumentation.ActivityMonitor?) = base.addMonitor(monitor)
        override fun addMonitor(filter: IntentFilter?, result: Instrumentation.ActivityResult?, block: Boolean): Instrumentation.ActivityMonitor =
            base.addMonitor(filter, result, block)
        override fun addMonitor(cls: String?, result: Instrumentation.ActivityResult?, block: Boolean): Instrumentation.ActivityMonitor =
            base.addMonitor(cls, result, block)
        override fun checkMonitorHit(monitor: Instrumentation.ActivityMonitor?, minHits: Int) =
            base.checkMonitorHit(monitor, minHits)
        override fun waitForMonitor(monitor: Instrumentation.ActivityMonitor?): Instrumentation.ActivityMonitor? =
            base.waitForMonitor(monitor)
        override fun waitForMonitorWithTimeout(monitor: Instrumentation.ActivityMonitor?, timeOut: Long): Instrumentation.ActivityMonitor? =
            base.waitForMonitorWithTimeout(monitor, timeOut)
        override fun removeMonitor(monitor: Instrumentation.ActivityMonitor?) = base.removeMonitor(monitor)
        override fun invokeMenuActionSync(targetActivity: Activity?, id: Int, flags: Int) =
            base.invokeMenuActionSync(targetActivity, id, flags)
        override fun invokeContextMenuAction(targetActivity: Activity?, id: Int, flags: Int) =
            base.invokeContextMenuAction(targetActivity, id, flags)
        override fun sendStringSync(text: String?) = base.sendStringSync(text)
        override fun sendKeySync(event: android.view.KeyEvent?) = base.sendKeySync(event)
        override fun sendKeyDownUpSync(key: Int) = base.sendKeyDownUpSync(key)
        override fun sendCharacterSync(keyCode: Int) = base.sendCharacterSync(keyCode)
        override fun sendPointerSync(event: android.view.MotionEvent?) = base.sendPointerSync(event)
        override fun sendTrackballEventSync(event: android.view.MotionEvent?) =
            base.sendTrackballEventSync(event)
        override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
            base.newApplication(cl, className, context)
        override fun callApplicationOnCreate(app: Application?) = base.callApplicationOnCreate(app)
        override fun callActivityOnDestroy(activity: Activity?) = base.callActivityOnDestroy(activity)
        override fun callActivityOnRestoreInstanceState(activity: Activity, savedInstanceState: Bundle) =
            base.callActivityOnRestoreInstanceState(activity, savedInstanceState)
        override fun callActivityOnRestoreInstanceState(activity: Activity, savedInstanceState: Bundle?, persistentState: PersistableBundle?) =
            base.callActivityOnRestoreInstanceState(activity, savedInstanceState, persistentState)
        override fun callActivityOnPostCreate(activity: Activity, savedInstanceState: Bundle?) =
            base.callActivityOnPostCreate(activity, savedInstanceState)
        override fun callActivityOnPostCreate(activity: Activity, savedInstanceState: Bundle?, persistentState: PersistableBundle?) =
            base.callActivityOnPostCreate(activity, savedInstanceState, persistentState)
        override fun callActivityOnNewIntent(activity: Activity?, intent: Intent?) =
            base.callActivityOnNewIntent(activity, intent)
        override fun callActivityOnStart(activity: Activity?) = base.callActivityOnStart(activity)
        override fun callActivityOnRestart(activity: Activity?) = base.callActivityOnRestart(activity)
        override fun callActivityOnResume(activity: Activity?) = base.callActivityOnResume(activity)
        override fun callActivityOnStop(activity: Activity?) = base.callActivityOnStop(activity)
        override fun callActivityOnSaveInstanceState(activity: Activity, outState: Bundle) =
            base.callActivityOnSaveInstanceState(activity, outState)
        override fun callActivityOnSaveInstanceState(activity: Activity, outState: Bundle, outPersistentState: PersistableBundle) =
            base.callActivityOnSaveInstanceState(activity, outState, outPersistentState)
        override fun callActivityOnPause(activity: Activity?) = base.callActivityOnPause(activity)
        override fun callActivityOnUserLeaving(activity: Activity?) = base.callActivityOnUserLeaving(activity)

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated")
        override fun startAllocCounting() = base.startAllocCounting()

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated")
        override fun stopAllocCounting() = base.stopAllocCounting()

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated")
        override fun getAllocCounts(): Bundle = base.allocCounts

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated")
        override fun getBinderCounts(): Bundle = base.binderCounts

        override fun getUiAutomation(): android.app.UiAutomation = base.uiAutomation
        override fun getUiAutomation(flags: Int): android.app.UiAutomation = base.getUiAutomation(flags)
        override fun acquireLooperManager(looper: Looper): android.app.TestLooperManager =
            base.acquireLooperManager(looper)
    }

    /** PackageManager 动态代理：模块代理 Activity 的 getActivityInfo 返回伪造 ActivityInfo。 */
    inner class PackageManagerInvocationHandler(private val target: Any) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            if (method.name == "getActivityInfo" && args != null) {
                var component: ComponentName? = null
                for (arg in args) {
                    when (arg) {
                        is ComponentName -> component = arg
                    }
                }
                if (component != null && isModuleProxyActivity(component.className)) {
                    return makeProxyActivityInfo(component.className)
                }
            }
            return try {
                method.invoke(target, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    /** 伪造模块 Activity 的 ActivityInfo：包名/进程指向宿主，系统校验通过。 */
    fun makeProxyActivityInfo(className: String) = ActivityInfo().apply {
        name = className
        packageName = HOST_PACKAGE_NAME
        enabled = true
        exported = false
        processName = HOST_PACKAGE_NAME
        applicationInfo = hostApplicationInfo ?: ApplicationInfo().apply {
            packageName = HOST_PACKAGE_NAME
        }
        launchMode = ActivityInfo.LAUNCH_MULTIPLE
    }

    /** 真实 Intent 缓存：token → Intent，60s 过期（换壳与还原之间只有毫秒级窗口）。 */
    private object IntentTokenCache {
        private data class Entry(val intent: Intent, val timestamp: Long = System.currentTimeMillis())
        private val cache = ConcurrentHashMap<String, Entry>()
        private const val EXPIRE_MS = 60_000L

        fun put(intent: Intent): String {
            cleanup()
            return UUID.randomUUID().toString().also { cache[it] = Entry(intent) }
        }

        fun get(token: String): Intent? {
            val entry = cache[token] ?: return null
            return entry.intent.takeIf { System.currentTimeMillis() - entry.timestamp <= EXPIRE_MS }
                ?: run { cache.remove(token, entry); null }
        }

        fun remove(token: String) {
            cache.remove(token)
        }

        private fun cleanup() {
            val now = System.currentTimeMillis()
            cache.entries.removeIf { now - it.value.timestamp > EXPIRE_MS }
        }
    }
}

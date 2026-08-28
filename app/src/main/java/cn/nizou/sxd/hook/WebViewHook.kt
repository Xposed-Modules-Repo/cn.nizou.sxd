package cn.nizou.sxd.hook

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import cn.nizou.sxd.Classname
import cn.nizou.sxd.XposedInit
import cn.nizou.sxd.XposedInit.Companion.moduleRes
import cn.nizou.sxd.entities.AutoAnswerMode
import cn.nizou.sxd.util.Debug
import cn.nizou.sxd.util.PK
import cn.nizou.sxd.util.XposedHelpers
import cn.nizou.sxd.util.currentApplication
import cn.nizou.sxd.util.logI
import cn.nizou.sxd.util.pathPoints
import cn.nizou.sxd.util.toJSONArray
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.HookHandle
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class WebViewHook(
    self: XposedInterface,
    classLoader: ClassLoader
) : BaseHook(self, classLoader) {

    override val name: String
        get() = "WebViewHook"

    private val standardJs by lazy {
        moduleRes.assets.open("js/standard.js")
            .bufferedReader().use { it.readText() }
    }

    private val quickJs by lazy {
        moduleRes.assets.open("js/quick.js")
            .bufferedReader().use { it.readText() }
    }

    private val cyclicJs by lazy {
        moduleRes.assets.open("js/cyclic.js")
            .bufferedReader().use { it.readText() }
    }

    private val fastSettleJs by lazy {
        moduleRes.assets.open("js/fastsettle.js")
            .bufferedReader().use { it.readText() }
    }

    private val pkPageLoaded = AtomicBoolean(false)

    private val resultPageLoaded = AtomicBoolean(false)

    private val appropriateCostTime = AtomicLong(0L)

    private var webViewRef: WeakReference<View>? = null

    private val webView get() = webViewRef?.get()

    private var loadUrl: Method? = null

    @JavascriptInterface
    fun log(str: String) {
        logI("console.log >>>>>>>")
        logI(str)
        logI("console.log <<<<<<<")
    }

    @JavascriptInterface
    fun targetCostTime(costTime: Long) {
        appropriateCostTime.set(costTime - 100)
    }

    @JavascriptInterface
    fun quickModeAwait(questionCnt: Int, callback: String) {
        val loadUrl = loadUrl ?: return
        val webView = webView ?: return
        val appropriateCostTime = appropriateCostTime.get()
        val waitTime = if (PK.quickModeMustWin && appropriateCostTime > 0) {
            appropriateCostTime
        } else {
            getSimulateCostTime(questionCnt).coerceAtLeast(questionCnt * 200L)
        }
        logI("waitTime: $waitTime, callback: $callback")
        webView.postDelayed({
            injectJsCode("window.$callback && window.$callback();", loadUrl, webView)
        }, waitTime)
    }

    private fun hookConsoleLog() {
        val loadUrl = loadUrl ?: return
        val webView = webView ?: return
        invokeOriginal(
            loadUrl,
            webView,
            arrayOf("javascript: (function() { let backup_log=console.log;console.log=function(){if(arguments.length>=1){let l=arguments[0];window.AutoOral&&window.AutoOral.log(typeof l===l?l:JSON.stringify(l))}return backup_log(arguments)}; })();")
        )
    }

    override fun startHook() {
        // 新版(3.140+) 已移除 vgo BaseWebApp（WebView 架构重构为 kanyun H5BaseWebApp 等），
        // 类不存在时优雅跳过 WebView 注入。
        val baseWebAppClass = XposedHelpers.findClassIfExists(Classname.BASE_WEB_APP, classLoader)
            ?: return logI("WebViewHook: BaseWebApp not found in host, skip webview hooks")
        val simpleWebAppFireworkClass =
            findClass(Classname.SIMPLE_WEB_APP_FIREWORK_ACTIVITY)
        val webViewField =
            simpleWebAppFireworkClass.fields.firstOrNull { it.type == baseWebAppClass }

        loadUrl = baseWebAppClass.methods.firstOrNull {
            it.name == "loadUrl" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
        }
        val addJavascriptInterface = baseWebAppClass.methods.firstOrNull {
            it.name == "addJavascriptInterface"
        }
        addJavascriptInterface?.let(::hookAddJavascriptInterface)

        simpleWebAppFireworkClass.findMethod("onCreate", Bundle::class.java)
            .intercept("webapp_onCreate") { chain ->
                val r = chain.proceed()
                logI("simpleWebApp onCreate")
                webViewField?.get(chain.thisObject)?.let {
                    webViewRef = WeakReference(it as View)
                    addJavascriptInterface?.invoke(
                        it,
                        this,
                        "AutoOral"
                    )
                }
                r
            }

        loadUrl?.intercept("webapp_loadUrl") { chain ->
            val r = chain.proceed()
            val str = chain.getArg(0).toString()
            when {
                str.startsWith("javascript:") -> Unit
                str.contains("/bh5/leo-web-oral-pk/exercise.html") -> {
                    logI("exercise.html loaded")
                    hookConsoleLog()
                    pkPageLoaded.set(true)
                }

                str.contains("/bh5/leo-web-oral-pk/english-words.html") -> {
                    logI("english-words.html loaded")
                    hookConsoleLog()
                    pkPageLoaded.set(true)
                }

                str.contains("/bh5/leo-web-oral-pk/result.html") -> {
                    logI("result.html loaded")
                    hookConsoleLog()
                    resultPageLoaded.set(true)
                }
            }
            r
        }

        hookJsLoadComplete()
    }

    private fun hookJsLoadComplete() {
        val commonWebViewInterfaceClass = findClass(Classname.COMMON_WEB_VIEW_INTERFACE)
        commonWebViewInterfaceClass.findMethod("jsLoadComplete", String::class.java)
            .intercept("jsLoadComplete") { chain ->
                val r = chain.proceed()
                when {
                    pkPageLoaded.compareAndSet(true, false) -> {
                        injectJs2PkPage()
                    }

                    resultPageLoaded.compareAndSet(true, false) -> {
                        injectJs2ResultPage()
                    }
                }
                r
            }
    }

    private fun injectConfig(loadUrl: Method, webView: View, key: String, value: Any) {
        invokeOriginal(
            loadUrl,
            webView,
            arrayOf("javascript: (function(){window._$key=$value;})();")
        )
    }

    private fun injectJsCode(jsCode: String, loadUrl: Method, webView: View) {
        invokeOriginal(
            loadUrl,
            webView,
            arrayOf("javascript:(function() { $jsCode })();")
        )
        logI("js injected")
    }

    private fun injectJs2PkPage() {
        val loadUrl = loadUrl ?: return
        val webView = webView ?: return
        webView.post {
            val mode = PK.mode
            val jsCode = when (mode) {
                AutoAnswerMode.QUICK -> quickJs

                AutoAnswerMode.CUSTOM -> PK.customJs

                AutoAnswerMode.STANDARD -> standardJs

                AutoAnswerMode.DISABLE -> ""
            }
            // 秒结算：先注入环境加速（CSS 动画 0s/音效静音/自动画线/跳题 0ms），再注入答题 JS。
            // 移植自 ExElectron/Xiaoyuan_Kousuan_2026 的 7 大 patch（运行时版）。
            if (PK.pkFastSettle) {
                injectJsCode(fastSettleJs, loadUrl, webView)
                logI("fastsettle injected")
            }
            if (jsCode.isEmpty()) {
                logI("自动答题配置: ${mode.value}")
            } else {
                injectJsCode(jsCode, loadUrl, webView)
            }
        }
    }

    private fun injectJs2ResultPage() {
        val loadUrl = loadUrl ?: return
        val webView = webView ?: return
        webView.post {
            injectConfig(loadUrl, webView, "pk_cyclic_interval", PK.pkCyclicInterval)

            if (PK.pkCyclic) {
                injectJsCode(cyclicJs, loadUrl, webView)
            }
        }
    }

    private fun hookAddJavascriptInterface(addJavascriptInterface: Method) {
        val openSchemaBeanClass = findClass(Classname.OPEN_SCHEMA_BEAN)
        val dataEncryptBeanClass = findClass(Classname.DATA_ENCRYPT_BEAN)
        var handle: HookHandle? = null
        var count = 0
        handle = addJavascriptInterface.also { it.isAccessible = true }
            .intercept("addJavascriptInterface") { chain ->
                val obj = chain.getArg(0)
                val name = chain.getArg(1)
                logI(name)
                when (name) {
                    "CommonWebView" -> {
                        val caller = XposedHelpers.callMethod(obj!!, "get", openSchemaBeanClass)!!
                        hookOpenSchema(caller.javaClass)
                        count++
                    }

                    "LeoSecureWebView" -> {
                        obj!!.javaClass.declaredFields.firstOrNull {
                            Map::class.java.isAssignableFrom(it.type)
                        }?.let {
                            val caller = (it.get(obj) as Map<*, *>)[dataEncryptBeanClass]!!
                            hookDataEncrypt(caller.javaClass)
                        }
                        count++
                    }

                    else -> {}
                }
                if (count >= 2) {
                    handle?.unhook()
                }
                chain.proceed()
            }
    }

    private fun hookOpenSchema(caller: Class<*>) {
        var lastSchemas: Any? = null
        caller.allMethod("call").forEach { m ->
            m.also { it.isAccessible = true }.intercept("openSchema_call") { chain ->
                if (!PK.pkCyclic) {
                    return@intercept chain.proceed()
                }
                val arg0 = chain.getArg(0)!!
                val schemas = XposedHelpers.getObjectField(arg0, "schemas") as Array<*>
                val url = Uri.parse(schemas[0].toString()).getQueryParameter("url")!!
                val targetUri = Uri.parse(url)
                when (targetUri.path) {
                    "/bh5/leo-web-study-group/motivation-honor-roll.html" -> {
                        when (targetUri.getQueryParameter("fromType")) {
                            "oralPkResult" -> {
                                XposedHelpers.callMethod(
                                    arg0,
                                    "trigger",
                                    webView,
                                    null,
                                    emptyArray<Any>()
                                )
                                null
                            }

                            "resultPageJs" -> {
                                // 防御：lastSchemas 未捕获（版本漂移/流程变化）时不再回放，放行原跳转避免 NPE
                                if (lastSchemas != null) {
                                    XposedHelpers.setObjectField(arg0, "schemas", lastSchemas)
                                    XposedHelpers.setBooleanField(arg0, "close", true)
                                }
                                chain.proceed()
                            }

                            else -> chain.proceed()
                        }
                    }

                    "/bh5/leo-web-oral-pk/result.html" -> chain.proceed()

                    else -> {
                        lastSchemas = schemas.copyOf(schemas.size)
                        chain.proceed()
                    }
                }
            }
        }
    }

    private fun getSimulateCostTime(questionCnt: Int): Long {
        val interval = PK.quickModeInterval
        return questionCnt * interval.toLong()
    }

    private fun hookDataEncrypt(caller: Class<*>) {
        caller.allMethod("call").forEach { m ->
            m.also { it.isAccessible = true }.intercept("dataEncrypt_call") { chain ->
                val mode = PK.mode
                if (!Debug.debug && mode !in arrayOf(AutoAnswerMode.QUICK, AutoAnswerMode.STANDARD)) {
                    return@intercept chain.proceed()
                }
                val bean = chain.getArg(0)
                val base64 = XposedHelpers.getObjectField(bean!!, "base64").toString()
                if (base64.isBlank()) {
                    return@intercept chain.proceed()
                }
                val json =
                    kotlin.runCatching { JSONObject(Base64.decode(base64, 0).decodeToString()) }
                        .getOrNull()
                        ?: return@intercept chain.proceed()
                if (!json.has("pkIdStr")) {
                    return@intercept chain.proceed()
                }
                if (mode !in arrayOf(AutoAnswerMode.QUICK, AutoAnswerMode.STANDARD)) {
                    return@intercept chain.proceed()
                }
                runCatching {
                    val questions = json.getJSONArray("questions")
                    for (i in 0 until questions.length()) {
                        val question = questions.getJSONObject(i)
                        val answer = question.getString("userAnswer") ?: ""
                        val pathPoints = answer.pathPoints.toJSONArray()
                        val curTrueAnswer = question.optJSONObject("curTrueAnswer")
                        curTrueAnswer?.put("pathPoints", pathPoints)
                        if (question.has("script")) {
                            question.put("script", pathPoints.toString())
                        }
                    }
                    val questionCnt = json.getInt("questionCnt")
                    if (mode == AutoAnswerMode.QUICK) {
                        val appropriateCostTime = appropriateCostTime.get()
                        val costTime = if (PK.quickModeMustWin && appropriateCostTime > 0) {
                            appropriateCostTime
                        } else {
                            getSimulateCostTime(questionCnt).coerceAtLeast(questionCnt * 200L)
                        }
                        logI("originCostTime: ${json.get("costTime")}, costTime: $costTime")
                        json.put("costTime", costTime)
                    }
                    if (Debug.debug) {
                        thread {
                            val file = File(
                                currentApplication().externalCacheDir,
                                "${System.currentTimeMillis()}.json"
                            )
                            file.writeText(json.toString())
                        }
                    }
                    val newBase64 = Base64.encode(json.toString().toByteArray(), 0).decodeToString()
                    XposedHelpers.setObjectField(bean, "base64", newBase64)
                }.onFailure {
                    logI(it)
                }
                chain.proceed()
            }
        }
    }

    /** 等价旧 XposedBridge.invokeOriginalMethod：跳过所有 Hook 调用原方法。 */
    private fun invokeOriginal(loadUrl: Method, thisObject: Any?, args: Array<out Any?>): Any? {
        val invoker = XposedInit.self.getInvoker(loadUrl)
        invoker.setType(XposedInterface.Invoker.Type.ORIGIN)
        return invoker.invoke(thisObject, *args)
    }
}

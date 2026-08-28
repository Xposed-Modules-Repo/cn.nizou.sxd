package cn.nizou.sxd.hook

import cn.nizou.sxd.Classname
import cn.nizou.sxd.util.Packet
import cn.nizou.sxd.util.PacketTool
import cn.nizou.sxd.util.Practice
import cn.nizou.sxd.util.UserInfoStore
import cn.nizou.sxd.util.XposedHelpers
import cn.nizou.sxd.util.logI
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class RetrofitHook(
    self: XposedInterface,
    classLoader: ClassLoader
) : BaseHook(self, classLoader), InvocationHandler {

    override val name: String
        get() = "RetrofitHook"

    override fun startHook() {
        val retrofitClass = findClass(Classname.RETROFIT)
        // 2026-08-29：对**所有** Retrofit.create 加拦截器（不只 OralApiService）——
        // 用户信息/cookie 可能来自其它 service（pk/home、user 等）；identity 去重防重复代理。
        retrofitClass.findMethod("create", Class::class.java).intercept("retrofit_create") { chain ->
            val r = chain.proceed()
            chain.thisObject?.let(::addInterceptor)
            r
        }
    }

    private val patchedRetrofits = java.util.Collections.newSetFromMap(
        java.util.IdentityHashMap<Any, Boolean>()
    )

    private fun addInterceptor(retrofit: Any) {
        if (!patchedRetrofits.add(retrofit)) return
        logI("addInterceptor")
        val interceptorClass = findClass(Classname.INTERCEPTOR)
        // FIX: callFactory 本身即 OkHttpClient，直接读其 interceptors 字段，去掉中间 "a" 一步
        val callFactory = XposedHelpers.getObjectField(retrofit, "callFactory")
        val interceptors = XposedHelpers.getObjectField(callFactory, "interceptors") as List<*>
        val myInterceptor =
            Proxy.newProxyInstance(interceptorClass.classLoader, arrayOf(interceptorClass), this)
        XposedHelpers.setObjectField(callFactory, "interceptors", (interceptors + myInterceptor).toList())
    }

    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
        if (method.name != "intercept") {
            return if (args == null) {
                method.invoke(this)
            } else {
                method.invoke(this, *args)
            }
        }
        val chain = args!![0]
        return intercept(chain)
    }

    private fun intercept(chain: Any): Any? {
        val request = XposedHelpers.callMethod(chain, "request")!!
        val httpUrl = XposedHelpers.callMethod(request, "url")!!
        val method = XposedHelpers.callMethod(request, "method").toString()
        val fullPath = XposedHelpers.callMethod(httpUrl, "encodedPath")?.toString() ?: "/"

        // 1) 保持现有 isBackground 逻辑不破坏（自动上分）
        var req: Any = request
        if (Practice.autoHonor && fullPath.startsWith("/leo-math/android/exams") && method in arrayOf("POST", "PUT")) {
            req = buildIsBackground0(request)
        }

        // 2) 通用抓包 / 改包（开关默认关；改包失败安全回落放行原请求）
        if (Packet.capture || Packet.rewrite) {
            PacketTool.processRequest(req, fullPath, method, classLoader, Packet.capture, Packet.writeFile)
                ?.let { req = it }
        }

        // 3) proceed
        val response = XposedHelpers.callMethod(chain, "proceed", req)

        // 4) 响应抓包（capture 开启才用 peekBody 读，不消费真正的 body 流）
        if (Packet.capture) {
            PacketTool.captureResponse(response!!, fullPath, method, Packet.writeFile)
        }

        // 5) 用户信息 + Cookie 采集（用户信息卡片数据源，peekBody 不消费 body 流）
        runCatching {
            if (response != null) {
                val headers = XposedHelpers.callMethod(response, "headers")
                val setCookie = XposedHelpers.callMethod(headers, "get", "Set-Cookie") as? String
                UserInfoStore.updateCookie(setCookie)
                if (fullPath.contains("user") || fullPath.contains("home") || fullPath.contains("account") || fullPath.contains("profile") || fullPath.contains("pk/home")) {
                    val body = XposedHelpers.callMethod(response, "peekBody", 1024L * 1024L)
                    val text = XposedHelpers.callMethod(body, "string") as? String
                    UserInfoStore.updateFromJson(text)
                }
            }
        }.onFailure {
            // 用户信息采集属可选功能，失败静默，不打扰正常请求
        }

        return response
    }
    /** 把 exams 请求 query 加 isBackground=0。 */
    private fun buildIsBackground0(request: Any): Any {
        val url = XposedHelpers.callMethod(request, "url")!!
        val urlBuilder = XposedHelpers.callMethod(url, "newBuilder")!!
        XposedHelpers.callMethod(urlBuilder, "setQueryParameter", "isBackground", "0")
        val newUrl = XposedHelpers.callMethod(urlBuilder, "build")!!
        val newBuilder = XposedHelpers.callMethod(request, "newBuilder")!!
        XposedHelpers.callMethod(newBuilder, "url", newUrl)
        return XposedHelpers.callMethod(newBuilder, "build")!!
    }
}

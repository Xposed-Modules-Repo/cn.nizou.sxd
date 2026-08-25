package cn.nizou.sxd.hook

import cn.nizou.sxd.Classname
import cn.nizou.sxd.util.Practice
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
        val apiServiceClass = findClass(Classname.ORAL_API_SERVICE)
        retrofitClass.findMethod("create", Class::class.java).intercept("retrofit_create") { chain ->
            val r = chain.proceed()
            val arg0 = chain.getArg(0)
            if (arg0 == apiServiceClass) {
                chain.thisObject?.let(::addInterceptor)
            }
            r
        }
    }

    private fun addInterceptor(retrofit: Any) {
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
        val request = XposedHelpers.callMethod(chain, "request")
        val httpUrl = XposedHelpers.callMethod(request, "url")
        val method = XposedHelpers.callMethod(request, "method")
        val pathSegments = XposedHelpers.callMethod(httpUrl, "pathSegments") as List<*>
        val path = "/${pathSegments.take(4).joinToString("/") { it.toString() }}"

        if (!Practice.autoHonor || !path.startsWith("/leo-math/android/exams") || method !in arrayOf("POST", "PUT")) {
            return XposedHelpers.callMethod(chain, "proceed", request)
        }

        val url = XposedHelpers.callMethod(request, "url")
        val urlBuilder = XposedHelpers.callMethod(url, "newBuilder")
        XposedHelpers.callMethod(urlBuilder, "setQueryParameter", "isBackground", "0")
        val newUrl = XposedHelpers.callMethod(urlBuilder, "build")
        val newBuilder = XposedHelpers.callMethod(request, "newBuilder")
        XposedHelpers.callMethod(newBuilder, "url", newUrl)
        val newRequest = XposedHelpers.callMethod(newBuilder, "build")
        return XposedHelpers.callMethod(chain, "proceed", newRequest)
    }
}

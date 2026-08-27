package cn.nizou.sxd.api

import cn.nizou.sxd.util.XposedHelpers
import java.lang.reflect.Method
import java.lang.reflect.Proxy

object LegacyApiService {
    private lateinit var apiService: Any

    private lateinit var gson: Any

    private lateinit var coroutineContext: Any

    private lateinit var postSavedExpMethod: Method

    private lateinit var postSavedExpBodyType: Class<*>

    private lateinit var getCurrentUserExpMethod: Method

    private lateinit var coroutineClass: Class<*>

    fun init(apiService: Any, gson: Any) {
        this.apiService = apiService
        this.gson = gson
        postSavedExpMethod = apiService::class.java.declaredMethods.first {
            it.name == "postSavedExp" && it.parameterCount == 2
        }
        getCurrentUserExpMethod = apiService::class.java.declaredMethods.first {
            it.name == "getCurrentUserExp" && it.parameterCount == 1
        }
        postSavedExpBodyType = postSavedExpMethod.parameterTypes.first()
        coroutineClass = postSavedExpMethod.parameterTypes.last()
    }

    fun setup(coroutineContext: Any) {
        this.coroutineContext = coroutineContext
    }

    /**
     * 是否已由宿主 SettingHook 完成 init+setup。
     * 模块独立 App（模块本体）是独立进程，不注入宿主，LegacyApiService 永远未初始化，
     * 调用方据此给出明确错误而非卡「加载中」。
     */
    fun isReady(): Boolean =
        ::apiService.isInitialized &&
            ::coroutineContext.isInitialized &&
            ::coroutineClass.isInitialized

    fun postSavedExp(exp: Int, onResult: (Result<Any>) -> Unit) {
        val postSavedExp = Proxy.newProxyInstance(
            coroutineClass.classLoader,
            arrayOf(coroutineClass),
            ContinuationProxy(coroutineContext, onResult)
        )
        val emptyJson = """{"todayExercises":[{}]}"""
        val body = XposedHelpers.callMethod(gson, "fromJson", emptyJson, postSavedExpBodyType)
        val exercises = XposedHelpers.getObjectField(body, "todayExercises") as List<*>
        val exercise = exercises.first()
        XposedHelpers.setLongField(exercise, "finishTime", System.currentTimeMillis())
        XposedHelpers.setIntField(exercise, "obtainExp", exp)
        postSavedExpMethod.invoke(apiService, body, postSavedExp)
    }

    fun getCurrentUserExp(onResult: (Result<Any>) -> Unit) {
        val getCurrentUserExp = Proxy.newProxyInstance(
            coroutineClass.classLoader,
            arrayOf(coroutineClass),
            ContinuationProxy(coroutineContext, onResult)
        )
        getCurrentUserExpMethod.invoke(apiService, getCurrentUserExp)
    }
}
package cn.nizou.sxd.api

import java.lang.reflect.Method
import java.lang.reflect.Proxy

object OralApiService {

    private lateinit var apiService: Any

    private lateinit var getExamInfoMethod: Method

    private lateinit var uploadExamResultMethod: Method

    private lateinit var coroutineContext: Any

    private lateinit var coroutineClass: Class<*>

    fun init(apiService: Any) {
        this.apiService = apiService
        uploadExamResultMethod = apiService::class.java.declaredMethods.first {
            it.name == "uploadExamResult" && it.parameterCount == 3
        }
        getExamInfoMethod = apiService::class.java.declaredMethods.first {
            it.name == "getExamInfo" && it.parameterCount == 3
        }
        coroutineClass = uploadExamResultMethod.parameterTypes.last()
    }

    fun setup(coroutineContext: Any) {
        this.coroutineContext = coroutineContext
    }

    /** 是否已由宿主完成 init+setup（模块本体独立进程未注入宿主时为 false）。 */
    fun isReady(): Boolean =
        ::apiService.isInitialized &&
            ::coroutineContext.isInitialized &&
            ::coroutineClass.isInitialized

    fun getExamInfo(keyPointId: String, limit: Int, onResult: (Result<Any>) -> Unit) {
        val getExamInfoProxy = Proxy.newProxyInstance(
            coroutineClass.classLoader,
            arrayOf(coroutineClass),
            ContinuationProxy(coroutineContext, onResult)
        )
        getExamInfoMethod.invoke(apiService, keyPointId, limit.toString(), getExamInfoProxy)
    }

    fun uploadExamResult(examId: String, requestData: Any, onResult: (Result<Any>) -> Unit) {
        val uploadExamResultProxy = Proxy.newProxyInstance(
            coroutineClass.classLoader, arrayOf(coroutineClass), ContinuationProxy(coroutineContext, onResult)
        )
        uploadExamResultMethod.invoke(apiService, examId, requestData, uploadExamResultProxy)
    }
}
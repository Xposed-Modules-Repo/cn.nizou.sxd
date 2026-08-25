package cn.nizou.sxd.api

import cn.nizou.sxd.util.XposedHelpers
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

class ContinuationProxy(
    private val coroutineContext: Any,
    private val onResume: (Result<Any>) -> Unit
) : InvocationHandler {
    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
        when (method.name) {
            "getContext" -> {
                return coroutineContext
            }

            "resumeWith" -> {
                val result = args?.get(0)
                val ret = when {
                    result == null -> {
                        Result.failure(NullPointerException("result is null"))
                    }

                    result::class.java.name == "kotlin.Result\$Failure" -> {
                        Result.failure(
                            XposedHelpers.getObjectField(
                                result,
                                "exception"
                            ) as Throwable
                        )
                    }

                    result::class.java.name == "kotlin.Result" -> {
                        Result.success(XposedHelpers.getObjectField(result, "value")!!)
                    }

                    else -> {
                        Result.success(result)
                    }
                }
                onResume(ret)

                return null
            }
        }
        return null
    }
}
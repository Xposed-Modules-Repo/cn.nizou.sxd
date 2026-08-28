package cn.nizou.sxd.hook

import android.util.Base64
import cn.nizou.sxd.Classname
import cn.nizou.sxd.util.Simian
import cn.nizou.sxd.util.XposedHelpers
import cn.nizou.sxd.util.logI
import io.github.libxposed.api.XposedInterface
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simian 改题目 / 改题目数量 / 口算答案 / VIP 移植（libxposed API 102）。
 *
 * 原参考：Simian-master/app/src/main/java/com/log/yh/Hook.java。这里用现代
 * `self.hook(...).intercept(Chain)` 语义重写，prefs 在拦截时实时读取（UI 开关即改即生效）。
 *
 * 覆盖 4 个 hook 点：
 *  1. EncryptResult(String) 构造器 —— 解析 base64 JSON，mode=0 改所有题 answers[0]，
 *     mode=1 只保留最后一题并改 content（单题模式）。
 *  2. JsBridgeBean$a 构造器 —— recognize 识别结果改写。
 *  3. QuestionVO.getAnswers —— 口算练习自定义答案。
 *  4. UserVipVO.getVipSymbol —— 解锁 VIP。
 */
class SimianHook(
    self: XposedInterface,
    classLoader: ClassLoader
) : BaseHook(self, classLoader) {

    override val name: String
        get() = "SimianHook"

    override fun startHook() {
        hookEncryptResult()
        hookJsBridgeBeanA()
        hookQuestionVOAnswers()
        hookVip()
    }

    // ---- 1. EncryptResult(String) 构造器：改答案 / 单题改题目 ----
    private fun hookEncryptResult() {
        val encryptResultClass = findClass(Classname.ENCRYPT_RESULT)
        encryptResultClass.findConstructor(String::class.java)
            .intercept("simian_encrypt_result") { chain ->
                val encoded = chain.getArg(0) as? String
                if (encoded != null && (Simian.modifyAnswer || Simian.modifyTitle)) {
                    rewriteEncryptPayload(encoded)?.let { newEncoded ->
                        val args = chain.args.toTypedArray()
                        args[0] = newEncoded
                        return@intercept chain.proceed(args)
                    }
                }
                chain.proceed()
            }
    }

    /** 解析 base64 JSON，按模式改写 examVO.questions，返回新 base64；失败返回 null。 */
    private fun rewriteEncryptPayload(encoded: String): String? {
        return runCatching {
            val answer = Simian.answers
            val decode = String(Base64.decode(encoded.toByteArray(), 0))
            val json = JSONObject(decode)
            val examVO = json.getJSONObject("examVO")
            val questions = examVO.getJSONArray("questions")

            when {
                Simian.modifyTitle -> {
                    // 单题模式：只保留最后一题，改 content，answers[0] 改自定义答案
                    val last = questions.getJSONObject(questions.length() - 1)
                    last.put("content", Simian.title)
                    val answers = last.getJSONArray("answers")
                    answers.put(0, answer)
                    last.put("answers", answers)
                    examVO.put("questions", JSONArray().put(last))
                }

                Simian.modifyAnswer -> {
                    // 多题模式：所有题 answers[0] 改自定义答案
                    for (i in 0 until questions.length()) {
                        val question = questions.getJSONObject(i)
                        val answers = question.getJSONArray("answers")
                        answers.put(0, answer)
                        question.put("answers", answers)
                    }
                    examVO.put("questions", questions)
                }
            }

            json.put("examVO", examVO)
            val str = json.toString()
            String(Base64.encode(str.toByteArray(), 0))
        }.onFailure {
            logI(it)
        }.getOrNull()
    }

    // ---- 2. JsBridgeBean$a 构造器：recognize 识别结果改写 ----
    private fun hookJsBridgeBeanA() {
        // 新版(3.140+) JsBridgeBean 已迁移/重构（旧 common.webview.base.JsBridgeBean$a 消失），
        // 类不存在时优雅跳过，不影响 EncryptResult/QuestionVO/VIP hook。
        val jsBridgeBeanAClass =
            XposedHelpers.findClassIfExists(Classname.JS_BRIDGE_BEAN_A, classLoader)
                ?: return logI("SimianHook: JsBridgeBean\$a not found in host, skip jsbridge hook")
        val jsBridgeBaseClass =
            XposedHelpers.findClassIfExists(Classname.JS_BRIDGE_BASE, classLoader)
                ?: return logI("SimianHook: JsBridgeBase not found in host, skip jsbridge hook")
        jsBridgeBeanAClass.findConstructor(
            jsBridgeBaseClass,
            String::class.java,
            String::class.java
        ).intercept("simian_js_bridge_bean_a") { chain ->
            if (Simian.modifyAnswer || Simian.modifyTitle) {
                val result = chain.getArg(1) as? String
                if (result?.contains("recognize") == true) {
                    val num = "[null, \"${Simian.answers}\"]"
                    val encode = String(Base64.encode(num.toByteArray(), 0))
                    val args = chain.args.toTypedArray()
                    args[2] = encode
                    return@intercept chain.proceed(args)
                }
            }
            chain.proceed()
        }
    }

    // ---- 3. QuestionVO.getAnswers：口算练习自定义答案 ----
    private fun hookQuestionVOAnswers() {
        val questionVOClass = findClass(Classname.QUESTION_VO)
        questionVOClass.findMethod("getAnswers")
            .intercept("simian_question_answers") { chain ->
                val answer = Simian.practiceAnswer
                if (answer.isNotEmpty()) {
                    val r = chain.proceed()
                    val answers = r as? List<*>
                    if (answers != null && answers.isNotEmpty()) {
                        return@intercept List(answers.size) { answer }
                    }
                    return@intercept r
                }
                chain.proceed()
            }
    }

    // ---- 4. UserVipVO.getVipSymbol：解锁 VIP ----
    private fun hookVip() {
        val userVipVOClass = findClass(Classname.USER_VIP_VO)
        userVipVOClass.findMethod("getVipSymbol")
            .intercept("simian_vip") { chain ->
                if (Simian.vip) true else chain.proceed()
            }
    }
}

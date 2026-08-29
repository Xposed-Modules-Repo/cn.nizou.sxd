package cn.nizou.sxd.hook

import android.util.Base64
import cn.nizou.sxd.Classname
import cn.nizou.sxd.util.AnswerCache
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
    // 2026-08-29 修正：真机 Debug dump 实证（提交包 answers 被改写为 ["1"]）证明 3.140 题目加载的
    // EncryptResult 载荷是**明文 base64 JSON**（此前「加密二进制」结论只对 PK 提交响应成立）。
    // 恢复默认解析（改答案功能可用）；解析失败（真加密场景）时 rewriteEncryptPayload 内部只警告一次，不刷屏。
    private fun hookEncryptResult() {
        val encryptResultClass = findClass(Classname.ENCRYPT_RESULT)
        encryptResultClass.findConstructor(String::class.java)
            .intercept("simian_encrypt_result") { chain ->
                val encoded = chain.getArg(0) as? String
                if (encoded != null) {
                    // 无论改答案开关都缓存题目答案（EncryptResult 载荷 = examVO.questions 明文 base64 JSON）
                    cacheAnswers(encoded)
                    if (Simian.modifyAnswer || Simian.modifyTitle) {
                        rewriteEncryptPayload(encoded)?.let { newEncoded ->
                            val args = chain.args.toTypedArray()
                            args[0] = newEncoded
                            return@intercept chain.proceed(args)
                        }
                    }
                }
                chain.proceed()
            }
    }

    /** 从 EncryptResult 载荷解析题目答案缓存（examVO.questions[].answer），供秒结算绘制。失败静默。 */
    private fun cacheAnswers(encoded: String) {
        runCatching {
            val raw = encoded.trim()
            val trimmed = if (raw.startsWith("v1$")) raw.substring(3) else raw
            val json = JSONObject(String(Base64.decode(trimmed.toByteArray(), 0)))
            val examVO = json.optJSONObject("examVO") ?: return
            val questions = examVO.optJSONArray("questions") ?: return
            val arr = JSONArray()
            for (i in 0 until questions.length()) {
                val q = questions.getJSONObject(i)
                val answer = q.optString("answer").ifBlank {
                    runCatching {
                        q.optJSONArray("answers")?.takeIf { it.length() > 0 }?.getString(0).orEmpty()
                    }.getOrDefault("")
                }
                if (answer.isNotEmpty()) {
                    arr.put(JSONObject().put("content", q.optString("content")).put("answer", answer))
                }
            }
            if (arr.length() > 0) {
                AnswerCache.answers = arr.toString()
                logI("answers cached via EncryptResult: " + arr.length() + " questions")
            }
        }.onFailure {
            // 解析失败（真加密响应等）静默，不影响改答案流程
        }
    }

    /**
     * 解析 base64 JSON，按模式改写 examVO.questions，返回新 base64；失败返回 null。
     *
     * **2026-08-29 修正**：必须**保持 lenient 解析**，不得用严格 base64 字符校验——
     * 3.140 载荷可能带 `v1$` 前缀（AES 加密标记，见记忆 05.5），Android Base64 lenient
     * 解码会忽略 `$` 等非法字符，旧版正是靠它解析成功并改答案；严格校验会**杀掉原本能用
     * 的改答案功能**（用户反馈回归，已撤销）。仅剥离 `v1$` 前缀后直接解码：能解析则改写；
     * 解析失败（真加密二进制）只一次性警告，行为与旧版一致（不刷屏、不影响流程）。
     */
    private var encryptPayloadWarned = false

    private fun rewriteEncryptPayload(encoded: String): String? {
        return runCatching {
            val raw = encoded.trim()
            // 3.140 加密串可能带 v1$ 前缀：剥离后再 lenient 解码（容忍其余非法字符）
            val trimmed = if (raw.startsWith("v1$")) raw.substring(3) else raw
            val answer = Simian.answers
            val decode = String(Base64.decode(trimmed.toByteArray(), 0))
            val json = JSONObject(decode)
            val examVO = json.getJSONObject("examVO")
            val questions = examVO.getJSONArray("questions")

            when {
                Simian.modifyTitle -> {
                    // 改题目模式：数量 = 自定义题目数量（默认 1），每道题 content=自定义题目、answers[0]=自定义答案。
                    // 取最后一题作为模板（保留题目结构字段），深拷贝 N 份，避免共享引用。
                    val template = questions.getJSONObject(questions.length() - 1)
                    val count = if (Simian.customQuestionCount > 0) Simian.customQuestionCount else 1
                    val out = JSONArray()
                    for (i in 0 until count) {
                        val q = JSONObject(template.toString())
                        q.put("content", Simian.title)
                        val answers = q.getJSONArray("answers")
                        answers.put(0, answer)
                        q.put("answers", answers)
                        out.put(q)
                    }
                    examVO.put("questions", out)
                }

                Simian.modifyAnswer -> {
                    // 多题模式：先按自定义题目数量裁剪（>0 且题数多于 N 时取前 N 题），再所有题 answers[0] 改自定义答案
                    val target = if (Simian.customQuestionCount > 0 && questions.length() > Simian.customQuestionCount) {
                        val cut = JSONArray()
                        for (i in 0 until Simian.customQuestionCount) {
                            cut.put(questions.getJSONObject(i))
                        }
                        cut
                    } else {
                        questions
                    }
                    for (i in 0 until target.length()) {
                        val question = target.getJSONObject(i)
                        val answers = question.getJSONArray("answers")
                        answers.put(0, answer)
                        question.put("answers", answers)
                    }
                    examVO.put("questions", target)
                }
            }

            json.put("examVO", examVO)
            val str = json.toString()
            String(Base64.encode(str.toByteArray(), 0))
        }.onFailure {
            if (!encryptPayloadWarned) {
                encryptPayloadWarned = true
                logI(it)
            }
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

    // ---- 4. 解锁 VIP ----
    // 3.140 适配（2026-08-29）：UserVipVO.getVipSymbol 已删除（UserVipVO 改为 data class），
    // VIP 标志迁移到 VipRightInfoVO.getVipSymbol()（reverser_ws 反编译实证）。
    // 旧类仍存在时双保险：UserVipVO.getVipSymbol（老版本）也拦一下。
    private fun hookVip() {
        runCatching {
            findClass(Classname.VIP_RIGHT_INFO_VO).findMethod("getVipSymbol")
                .intercept("simian_vip_right") { chain ->
                    if (Simian.vip) true else chain.proceed()
                }
        }.onFailure {
            logI("VipRightInfoVO.getVipSymbol hook failed: ${it.message}")
        }
        runCatching {
            findClass(Classname.USER_VIP_VO).findMethod("getVipSymbol")
                .intercept("simian_vip") { chain ->
                    if (Simian.vip) true else chain.proceed()
                }
        }.onFailure {
            logI("UserVipVO.getVipSymbol not in host (3.140+ removed), ok")
        }
    }
}

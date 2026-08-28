package cn.nizou.sxd.util

import android.content.Context
import cn.nizou.sxd.MODULE_PREFS_NAME
import cn.nizou.sxd.entities.AutoAnswerMode

private val currentContext by lazy { currentApplication() }

private val modulePrefs by lazy {
    currentContext.getSharedPreferences(MODULE_PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * 供 Compose 设置 UI 持久化写入（新增，读仍走上方各 object 的 getter）。
 *
 * 注意：调用方传入的 `key` 参数是 `res.KEY_XXX`，它本身**已经是** prefs 键字符串
 * （如 "always_true_answer"，来自 `getString(R.string.key_always_true_answer)`）。
 * 不要再套一层 `res.keyValue(key)` —— 那会用该值再去 getIdentifier 查同名资源，
 * 而资源名是 `key_always_true_answer`（值才是 always_true_answer），查不到返回 0，
 * getString(0) 抛 NotFoundException 导致点击子菜单闪退（真机已复现）。
 * 因此这里直接用 `key` 作为 prefs 键。
 */
object SettingsPrefs {
    fun readBoolean(res: StringRes, key: String, def: Boolean): Boolean =
        modulePrefs.getBoolean(key, def)

    fun writeBoolean(res: StringRes, key: String, value: Boolean) {
        modulePrefs.edit().putBoolean(key, value).apply()
    }

    fun readString(res: StringRes, key: String, def: String): String =
        modulePrefs.getString(key, def) ?: def

    fun writeString(res: StringRes, key: String, value: String) {
        modulePrefs.edit().putString(key, value).apply()
    }

    fun readInt(key: String, def: Int): Int = modulePrefs.getInt(key, def)

    fun writeInt(key: String, value: Int) {
        modulePrefs.edit().putInt(key, value).apply()
    }
}

object Common {
    val alwaysTrue get() = modulePrefs.getBoolean(moduleStringRes.KEY_ALWAYS_TRUE_ANSWER, true)
    val doubleNicknameLength get() = modulePrefs.getBoolean(moduleStringRes.KEY_DOUBLE_NICKNAME_LENGTH, true)
    val removeRestrictionOnNickname get() = modulePrefs.getBoolean(moduleStringRes.KEY_REMOVE_RESTRICTION_ON_NICKNAME, false)
}

object Practice {
    val autoHonor get() = modulePrefs.getBoolean(moduleStringRes.KEY_AUTO_HONOR, false)
    val autoPractice
        get() = !autoHonor && modulePrefs.getBoolean(
            moduleStringRes.KEY_AUTO_PRACTICE,
            true
        )
    val autoPracticeQuick
        get() = autoPractice && modulePrefs.getBoolean(
            moduleStringRes.KEY_AUTO_PRACTICE_QUICK,
            false
        )
    val autoPracticeCyclic
        get() = autoPractice && modulePrefs.getBoolean(
            moduleStringRes.KEY_AUTO_PRACTICE_CYCLIC,
            false
        )
    val autoPracticeCyclicInterval: Int
        get() {
            return kotlin.runCatching {
                Integer.parseInt(
                    modulePrefs.getString(
                        moduleStringRes.KEY_AUTO_PRACTICE_CYCLIC_INTERVAL,
                        ""
                    )!!
                )
            }.getOrElse {
                1500
            }
        }
}

object PK {
    val mode: AutoAnswerMode
        get() {
            val index = runCatching {
                Integer.parseInt(modulePrefs.getString(moduleStringRes.KEY_AUTO_ANSWER_CONFIG, "")!!)
            }.getOrElse { 0 }
            return AutoAnswerMode.entries[index]
        }
    val customJs get() = modulePrefs.getString(moduleStringRes.KEY_CUSTOM_ANSWER_CONFIG, "")!!
    val quickModeMustWin
        get() = mode == AutoAnswerMode.QUICK && modulePrefs.getBoolean(
            moduleStringRes.KEY_QUICK_MODE_MUST_WIN, false
        )
    val quickModeInterval: Int
        get() {
            return kotlin.runCatching {
                Integer.parseInt(modulePrefs.getString(moduleStringRes.KEY_QUICK_MODE_INTERVAL, "")!!)
            }.getOrElse { 200 }
        }
    val pkCyclic
        get() = mode in arrayOf(
            AutoAnswerMode.STANDARD,
            AutoAnswerMode.QUICK
        ) && modulePrefs.getBoolean(moduleStringRes.KEY_PK_CYCLIC, false)
    val pkCyclicInterval: Int
        get() {
            return kotlin.runCatching {
                Integer.parseInt(modulePrefs.getString(moduleStringRes.KEY_PK_CYCLIC_INTERVAL, "")!!)
            }.getOrElse { 1500 }
        }
}

object Debug {
    val debug
        get() = modulePrefs.getBoolean(moduleStringRes.KEY_DEBUG, false)
}

/**
 * 抓包 / 改包开关（RetrofitHook 通用 Interceptor 用）。
 * - [capture]：抓包开关（默认关），开启后把经过该 Retrofit 的请求/响应写入 LogBuffer（可选写文件）。
 * - [rewrite]：改包开关（默认关），按 [rules] JSON 规则改请求 query/body；失败安全回落放行原请求。
 * - [writeFile]：是否把抓包日志追加写 externalCacheDir/packet_capture.log。
 * - [rules]：规则 JSON 数组，示例见 util/PacketTool.kt 头部注释。
 */
object Packet {
    val capture get() = modulePrefs.getBoolean(moduleStringRes.KEY_PACKET_CAPTURE, false)
    val rewrite get() = modulePrefs.getBoolean(moduleStringRes.KEY_PACKET_REWRITE, false)
    val writeFile get() = modulePrefs.getBoolean(moduleStringRes.KEY_PACKET_WRITE_FILE, false)
    val rules get() = modulePrefs.getString(moduleStringRes.KEY_REWRITE_RULES, "[]")!!
}

/**
 * 屏蔽「外挂检测 / 大朋友（家长监督）检测」开关（默认关）。
 * - [blockRisk]：开启后屏蔽命中外挂/风控/异常关键字弹窗（Dialog）与 H5 alert，见 hook/RiskDetectHook.kt。
 * - [blockSupervision]：开启后强制隐藏家长监督视图（com.fenbi.android.leo.imgsearch.sdk.check.helper.SupervisionHelper）。
 * 均仅在开关开启时生效，且 hook 内部 runCatching，失败不影响正常流程。
 */
object Risk {
    val blockRisk get() = modulePrefs.getBoolean(moduleStringRes.KEY_BLOCK_RISK_DETECT, false)
    val blockSupervision get() = modulePrefs.getBoolean(moduleStringRes.KEY_BLOCK_SUPERVISION, false)
}

/**
 * Simian 改题目/改答案/口算答案/VIP。UI 开关由 ui-copier 用 SettingsPrefs 写入同一批键。
 */
object Simian {
    /** 改答案：EncryptResult 多题模式（所有题 answers[0]）改自定义答案 */
    val modifyAnswer get() = modulePrefs.getBoolean(moduleStringRes.KEY_MODIFY_ANSWER, false)

    /** 改题目：EncryptResult 单题模式（只保留最后一题并改 content） */
    val modifyTitle get() = modulePrefs.getBoolean(moduleStringRes.KEY_MODIFY_TITLE, false)

    /** 模式：0=多题改答案，1=单题改题目+答案 */
    val mode: Int
        get() {
            return runCatching {
                Integer.parseInt(modulePrefs.getString(moduleStringRes.KEY_SIMIAN_MODE, "")!!)
            }.getOrElse { 0 }
        }

    /** 自定义答案（口算答案，EncryptResult 与 recognize 共用） */
    val answers get() = modulePrefs.getString(moduleStringRes.KEY_CUSTOM_ANSWERS, "")!!

    /** 单题模式的题目内容 */
    val title get() = modulePrefs.getString(moduleStringRes.KEY_CUSTOM_TITLE, "")!!

    /** 口算练习 QuestionVO.getAnswers 自定义答案 */
    val practiceAnswer get() = modulePrefs.getString(moduleStringRes.KEY_PRACTICE_ANSWER, "")!!

    /**
     * 自定义正确题数（自定义分数新方案，0=全对）。
     * WebViewHook.hookDataEncrypt 按此值写提交包 status/correctCnt：>0 时前 N 题对、其余错。
     */
    val customCorrectCount: Int
        get() = runCatching {
            Integer.parseInt(modulePrefs.getString("custom_correct_count", "")!!)
        }.getOrElse { 0 }

    /** 解锁 VIP */
    val vip get() = modulePrefs.getBoolean(moduleStringRes.KEY_VIP, false)
}
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
 * 键一律来自 StringRes 的 key 常量，保证与 hook 侧读写同一份 prefs。
 */
object SettingsPrefs {
    private fun key(res: StringRes, k: String) = res.keyValue(k)

    fun readBoolean(res: StringRes, key: String, def: Boolean): Boolean =
        modulePrefs.getBoolean(res.keyValue(key), def)

    fun writeBoolean(res: StringRes, key: String, value: Boolean) {
        modulePrefs.edit().putBoolean(res.keyValue(key), value).apply()
    }

    fun readString(res: StringRes, key: String, def: String): String =
        modulePrefs.getString(res.keyValue(key), def) ?: def

    fun writeString(res: StringRes, key: String, value: String) {
        modulePrefs.edit().putString(res.keyValue(key), value).apply()
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

    /** 解锁 VIP */
    val vip get() = modulePrefs.getBoolean(moduleStringRes.KEY_VIP, false)
}
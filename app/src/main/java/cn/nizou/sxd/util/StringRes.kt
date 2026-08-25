package cn.nizou.sxd.util

import android.content.res.Resources
import cn.nizou.sxd.R
import cn.nizou.sxd.XposedInit

class StringRes(private val resources: Resources) {
    val KEY_ALWAYS_TRUE_ANSWER = resources.getString(R.string.key_always_true_answer)
    val KEY_DOUBLE_NICKNAME_LENGTH = resources.getString(R.string.key_double_nickname_length)
    val KEY_REMOVE_RESTRICTION_ON_NICKNAME = resources.getString(R.string.key_remove_restriction_on_nickname)
    val KEY_AUTO_HONOR = resources.getString(R.string.key_auto_honor)
    val KEY_AUTO_PRACTICE = resources.getString(R.string.key_auto_practice)
    val KEY_AUTO_PRACTICE_QUICK = resources.getString(R.string.key_auto_practice_quick)
    val KEY_AUTO_PRACTICE_CYCLIC = resources.getString(R.string.key_auto_practice_cyclic)
    val KEY_AUTO_PRACTICE_CYCLIC_INTERVAL = resources.getString(R.string.key_auto_practice_cyclic_interval)
    val KEY_AUTO_ANSWER_CONFIG = resources.getString(R.string.key_auto_answer_config)
    val KEY_CUSTOM_ANSWER_CONFIG = resources.getString(R.string.key_custom_answer_config)
    val KEY_QUICK_MODE_MUST_WIN = resources.getString(R.string.key_quick_mode_must_win)
    val KEY_QUICK_MODE_INTERVAL = resources.getString(R.string.key_quick_mode_interval)
    val KEY_PK_CYCLIC = resources.getString(R.string.key_pk_cyclic)
    val KEY_PK_CYCLIC_INTERVAL = resources.getString(R.string.key_pk_cyclic_interval)
    val KEY_GITHUB = resources.getString(R.string.key_github)
    val KEY_VERSION = resources.getString(R.string.key_version)
    val KEY_GOTO_SETTINGS = resources.getString(R.string.key_goto_settings)
    val KEY_DEBUG = resources.getString(R.string.key_debug)

    /** 按 key 字符串取同名 R.string 的值（供 SettingsPrefs 通用读写） */
    fun keyValue(name: String): String = resources.getString(
        resources.getIdentifier(name, "string", R::class.java.packageName)
    )
}

val moduleStringRes by lazy {
    StringRes(XposedInit.moduleRes)
}
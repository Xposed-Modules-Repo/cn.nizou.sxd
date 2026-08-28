package cn.nizou.sxd.util

import android.content.Context
import cn.nizou.sxd.MODULE_PREFS_NAME
import org.json.JSONObject

/**
 * 用户信息 + Cookie 采集存储（用户信息卡片数据源）。
 *
 * 采集：hook/RetrofitHook.kt 在 okhttp 拦截器里捕获
 *  - 用户信息：含 userName/userId/avatarUrl 的 JSON 响应体；
 *  - Cookie：响应 Set-Cookie 头。
 * 持久化：模块 prefs（宿主进程可读），供注入面板/模块本体 UI 展示与复制。
 */
object UserInfoStore {
    private const val KEY_UID = "ui_uid"
    private const val KEY_NAME = "ui_name"
    private const val KEY_AVATAR = "ui_avatar"
    private const val KEY_COOKIE = "ui_cookie"

    private val prefs by lazy {
        currentApplication().getSharedPreferences(MODULE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    var userId: String
        get() = prefs.getString(KEY_UID, "") ?: ""
        set(v) { prefs.edit().putString(KEY_UID, v).apply() }

    var userName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(v) { prefs.edit().putString(KEY_NAME, v).apply() }

    var avatarUrl: String
        get() = prefs.getString(KEY_AVATAR, "") ?: ""
        set(v) { prefs.edit().putString(KEY_AVATAR, v).apply() }

    var cookie: String
        get() = prefs.getString(KEY_COOKIE, "") ?: ""
        set(v) { prefs.edit().putString(KEY_COOKIE, v).apply() }

    /** 从 JSON 响应体提取用户字段（幂等，无则跳过）。 */
    fun updateFromJson(body: String?) {
        if (body.isNullOrBlank()) return
        runCatching {
            val json = JSONObject(body)
            fun pick(vararg keys: String): String {
                var cur: JSONObject = json
                for (k in keys) {
                    val v = cur.opt(k)
                    if (v is JSONObject) cur = v
                    else return cur.optString(k, "")
                }
                return ""
            }
            val name = pick("userName").ifBlank { pick("baseUserInfoVO", "userName") }
                .ifBlank { pick("userInfo", "userName") }
            val uid = pick("userId").ifBlank { pick("baseUserInfoVO", "userId") }
                .ifBlank { pick("userInfo", "userId") }
            val avatar = pick("avatarUrl").ifBlank { pick("baseUserInfoVO", "avatarUrl") }
                .ifBlank { pick("userInfo", "avatarUrl") }
            if (name.isNotBlank() || uid.isNotBlank()) {
                if (name.isNotBlank()) userName = name
                if (uid.isNotBlank()) userId = uid
                if (avatar.isNotBlank()) avatarUrl = avatar
            }
        }
    }

    /** 合并 Set-Cookie（去重，保留最新值）。 */
    fun updateCookie(cookieHeader: String?) {
        if (cookieHeader.isNullOrBlank()) return
        runCatching {
            val incoming = cookieHeader.substringBefore(";").trim()
            if (incoming.isBlank()) return
            val key = incoming.substringBefore("=")
            if (key.isBlank()) return
            val current = cookie.split(";").map { it.trim() }.filter { it.isNotBlank() }
            val kept = current.filterNot { it.startsWith(key + "=") || it.startsWith(key + " ") || it == key }
            cookie = (kept + incoming).joinToString("; ")
        }
    }
}

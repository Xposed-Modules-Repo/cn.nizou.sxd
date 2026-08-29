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
            // 字段名多版本兼容：userName/nickName/nickname/name；avatarUrl/headUrl/avatar/headImg；userId/userid/id
            //（2026-08-29 真机抓包：/leo-profile/android/user-infos 返回 nickname(小写n) 而非 nickName）
            val name = pick("userName").ifBlank { pick("nickName") }.ifBlank { pick("nickname") }.ifBlank { pick("name") }
                .ifBlank { pick("baseUserInfoVO", "userName") }.ifBlank { pick("baseUserInfoVO", "nickName") }
                .ifBlank { pick("baseUserInfoVO", "nickname") }
                .ifBlank { pick("userInfo", "userName") }.ifBlank { pick("userInfo", "nickName") }
                .ifBlank { pick("userInfo", "nickname") }
            val uid = pick("userId").ifBlank { pick("userid") }.ifBlank { pick("id") }
                .ifBlank { pick("baseUserInfoVO", "userId") }.ifBlank { pick("baseUserInfoVO", "userid") }
                .ifBlank { pick("userInfo", "userId") }.ifBlank { pick("userInfo", "id") }
            val avatar = pick("avatarUrl").ifBlank { pick("headUrl") }.ifBlank { pick("avatar") }.ifBlank { pick("headImg") }
                .ifBlank { pick("baseUserInfoVO", "avatarUrl") }.ifBlank { pick("baseUserInfoVO", "headUrl") }
                .ifBlank { pick("userInfo", "avatarUrl") }.ifBlank { pick("userInfo", "headUrl") }
            if (name.isNotBlank() || uid.isNotBlank()) {
                // 2026-08-29 账号切换防混搭：若新响应带的是**不同账号**的 uid（宿主子账号多：
                // primary 511467407 / 1066052990 / 1151466346 等，不同页面会触发不同账号的接口），
                // 旧账号的 name/avatar 必须清掉，否则卡片出现「新 ID + 旧昵称/旧头像」的被顶掉现象。
                val curUid = userId
                if (uid.isNotBlank() && curUid.isNotBlank() && uid != curUid) {
                    userName = ""
                    avatarUrl = ""
                }
                if (name.isNotBlank()) userName = name
                if (uid.isNotBlank()) userId = uid
                if (avatar.isNotBlank()) avatarUrl = avatar
                logI("UserInfoStore updateFromJson: uid=$uid name=$name avatar=$avatar")
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

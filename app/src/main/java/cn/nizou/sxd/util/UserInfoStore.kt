package cn.nizou.sxd.util

import android.content.Context
import cn.nizou.sxd.MODULE_PREFS_NAME
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户信息 + Cookie 采集存储（**多子账号**，用户信息卡片数据源）。
 *
 * 采集：hook/RetrofitHook.kt 在 okhttp 拦截器里捕获
 *  - **账号列表**：`/accounts/android/current` 的 `subUserInfos.project2SubUserInfo.<project>.subUserIds`
 *    （宿主多子账号：primary 511467407 / 1066052990 / 1151466346 等，不同页面触发不同账号接口）；
 *  - **单个账号资料**：`/leo-profile/android/user-infos`（userId+nickname+avatarUrl）、
 *    `/leo-alchemy-account/.../user/info/v2`（name+avatarUrl）等任意含用户字段的响应；
 *  - Cookie：响应 Set-Cookie 头。
 *
 * 持久化：模块 prefs（宿主进程可读）。卡片按 [accounts] 渲染，数量随账号列表自适应；
 * 缺资料的账号显示「昵称待采集」占位（打开个人中心触发 user-infos 后补全）。
 */
object UserInfoStore {
    private const val KEY_UID = "ui_uid"
    private const val KEY_NAME = "ui_name"
    private const val KEY_AVATAR = "ui_avatar"
    private const val KEY_COOKIE = "ui_cookie"
    private const val KEY_SUB_IDS = "ui_sub_ids"   // JSON array of uid strings
    private const val KEY_PROFILES = "ui_profiles" // JSON object {uid: {"name":..,"avatar":..}}

    /** 单个账号展示资料。 */
    data class Account(val uid: String, val name: String, val avatar: String)

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

    /** 所有子账号 uid（含主账号/当前账号），按首次出现顺序。 */
    val subAccountIds: List<String>
        get() = runCatching {
            val raw = prefs.getString(KEY_SUB_IDS, "") ?: ""
            if (raw.isBlank()) emptyList()
            else {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())

    private fun saveSubIds(ids: List<String>) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        prefs.edit().putString(KEY_SUB_IDS, arr.toString()).apply()
    }

    /** 账号资料表 uid → (name, avatar)。 */
    val profiles: Map<String, Pair<String, String>>
        get() = runCatching {
            val raw = prefs.getString(KEY_PROFILES, "") ?: ""
            if (raw.isBlank()) emptyMap()
            else {
                val obj = JSONObject(raw)
                val out = HashMap<String, Pair<String, String>>()
                val it = obj.keys()
                while (it.hasNext()) {
                    val uid = it.next()
                    val p = obj.optJSONObject(uid) ?: continue
                    out[uid] = p.optString("name", "") to p.optString("avatar", "")
                }
                out
            }
        }.getOrDefault(emptyMap())

    private fun saveProfile(uid: String, name: String, avatar: String) {
        runCatching {
            val raw = prefs.getString(KEY_PROFILES, "") ?: ""
            val obj = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            val p = obj.optJSONObject(uid) ?: JSONObject()
            if (name.isNotBlank()) p.put("name", name)
            if (avatar.isNotBlank()) p.put("avatar", avatar)
            obj.put(uid, p)
            prefs.edit().putString(KEY_PROFILES, obj.toString()).apply()
        }
    }

    /** 展示用账号列表：按 [subAccountIds] 顺序；缺资料则 name/avatar 空占位。 */
    val accounts: List<Account>
        get() {
            val profs = profiles
            val ids = subAccountIds
            if (ids.isNotEmpty()) {
                return ids.map { uid ->
                    val p = profs[uid]
                    Account(uid, p?.first ?: "", p?.second ?: "")
                }
            }
            val cur = userId
            return if (cur.isNotBlank()) listOf(Account(cur, userName, avatarUrl)) else emptyList()
        }

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
            // 账号列表：subUserInfos.project2SubUserInfo.<project>.subUserIds（accounts/current 返回）
            collectSubAccounts(json)
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
                if (uid.isNotBlank()) {
                    userId = uid
                    addToSubIds(uid)
                    if (name.isNotBlank() || avatar.isNotBlank()) saveProfile(uid, name, avatar)
                }
                if (name.isNotBlank()) userName = name
                if (avatar.isNotBlank()) avatarUrl = avatar
                logI("UserInfoStore updateFromJson: uid=$uid name=$name avatar=$avatar")
            }
        }
    }

    /** 解析 accounts/current 的 subUserInfos，把全部子账号并入列表（不覆盖已有资料）。 */
    private fun collectSubAccounts(json: JSONObject) {
        runCatching {
            val subUserInfos = json.optJSONObject("subUserInfos") ?: return
            val projectMap = subUserInfos.optJSONObject("project2SubUserInfo") ?: return
            val ids = LinkedHashSet(subAccountIds)
            val keys = projectMap.keys()
            while (keys.hasNext()) {
                val project = projectMap.optJSONObject(keys.next()) ?: continue
                val arr = project.optJSONArray("subUserIds") ?: continue
                for (i in 0 until arr.length()) {
                    val subUid = arr.optString(i, "")
                    if (subUid.isNotBlank()) ids.add(subUid)
                }
            }
            if (ids.isNotEmpty()) saveSubIds(ids.toList())
        }
    }

    private fun addToSubIds(uid: String) {
        val ids = LinkedHashSet(subAccountIds)
        if (ids.add(uid)) saveSubIds(ids.toList())
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

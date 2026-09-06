package cn.nizou.sxd.hook

import cn.nizou.sxd.util.logI
import org.json.JSONObject

/**
 * 原生 PK 会话跟踪（AutoPK 原生链路，见 记忆/07 与 交接文档）。
 *
 * 挂在通用 Retrofit 拦截器（[RetrofitHook]）上，只读捕获本局 PK 的
 *   - match 请求 pointId / 响应 pkIdStr、对手昵称、题目数 N
 *   - submit 请求/响应的 costTime 与本局状态
 *   - history/detail 响应的对手与结果
 * 不做任何网络主动调用，不改请求/响应，纯加性、失败静默。
 *
 * 线程：模块 hook 层与宿主主线程都可能触碰；所有字段 @Volatile 保证可见性，
 * 不做跨字段一致性承诺（每次读取现用值即可）。
 */
object PkNativeSession {
    @Volatile private var pkIdStr: String? = null
    @Volatile private var pointId: String? = null
    @Volatile private var opponentName: String? = null
    @Volatile private var questionCount: Int = 0
    @Volatile private var submittedCostTime: Long = 0L
    @Volatile private var warnOnce = false

    /** 最近一局的对局 ID（result.html?pkIdStr= 同源）。 */
    val currentPkId: String? get() = pkIdStr
    val currentPointId: String? get() = pointId
    val currentOpponent: String? get() = opponentName
    /** 本局从 match 响应解析到的题目数 N（0 = 未取得）。 */
    val nativeQuestionCount: Int get() = questionCount
    val lastCostTime: Long get() = submittedCostTime

    /** 宿主匹配请求已携带的知识点（query pointId），在 proceed 前调用。 */
    fun onMatchRequest(pointIdValue: String?) {
        if (!pointIdValue.isNullOrBlank() && pointIdValue != pointId) {
            pointId = pointIdValue
            logI("AutoPK match request: pointId=" + pointIdValue)
        }
    }

    /**
     * 解析 match 响应文本（可能是明文 JSON，也可能是加密 octet-stream）。
     * 只有能解析出 pkIdStr 才算建立一局；新 pkIdStr 到来会覆盖旧局信息。
     */
    fun onMatchResponse(text: String?) {
        if (text.isNullOrBlank()) return
        runCatching {
            val json = JSONObject(text)
            val pk = json.optString("pkIdStr").ifBlank { null }
            if (pk == null) return
            val opp = runCatching {
                val o = json.optJSONObject("opponent")
                o?.optString("userName")
            }.getOrNull()
            val qs = json.optJSONArray("questions")
            val n = qs?.length() ?: 0
            val changed = pk != pkIdStr
            pkIdStr = pk
            if (!opp.isNullOrBlank()) opponentName = opp
            if (n > 0) questionCount = n
            if (changed) {
                logI("AutoPK match resolved: pkIdStr=" + pk + " pointId=" + pointId +
                    " opponent=" + (opponentName ?: "?") + " questions=" + n)
            }
        }.onFailure {
            if (!warnOnce) { warnOnce = true; logI("AutoPK match body not JSON/parseable (likely encrypted octet-stream): " + it.message) }
        }
    }

    /** submit（PUT）后的 costTime 记录。 */
    fun onSubmitCostTime(costTime: Long) {
        if (costTime <= 0) return
        submittedCostTime = costTime
        logI("AutoPK submit observed: costTime=" + costTime + " pkIdStr=" + (pkIdStr ?: "?"))
    }

    /** 离开 PK（home 重进或 submit 后新 loadUrl）时清理，避免旧局信息串局。 */
    fun reset() {
        pkIdStr = null
        pointId = null
        opponentName = null
        questionCount = 0
        submittedCostTime = 0L
    }
}

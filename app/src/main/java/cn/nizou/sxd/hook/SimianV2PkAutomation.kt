package cn.nizou.sxd.hook

import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import cn.nizou.sxd.util.Simian
import cn.nizou.sxd.util.logI
import org.json.JSONArray
import org.json.JSONObject

/** Direct SimianV2 WebApi scheduling model with the actual 3.140 dynamic pad module. */
internal object SimianV2PkAutomation {
    private const val PAD_MODULE_URL = "https://leo.fbcontent.cn/bh5/leo-web-oral-pk/assets/index-legacy.DMgv2yXx.js"
    private enum class Task { STROKE, HAPPY, CONTINUE, CONTINUE_PK }
    private val handler = Handler(Looper.getMainLooper())
    private val tasks = mutableMapOf<Task, Runnable>()
    private data class StrokeSession(val webView: WebView, val tasks: MutableSet<Runnable> = linkedSetOf())
    private var strokeSession: StrokeSession? = null
    private val points = listOf(PointF(146.8571f,498.5714f),PointF(146.8571f,516.2858f),PointF(146.8571f,544.4261f),PointF(148f,561.7143f),PointF(148f,584f),PointF(148f,610.8572f),PointF(148f,627.7143f),PointF(149.7143f,652.2858f),PointF(151.4286f,668f),PointF(153.1429f,675.7143f),PointF(156.8571f,684.5715f))

    /** One cancellable session owns every delayed stroke for the current exercise page. */
    fun scheduleStroke(webView: WebView, delay: Long) {
        // Only one PK exercise may own delayed strokes at a time, even if the host creates a new WebView.
        strokeSession?.let { active -> cancelStrokeSession(active.webView, "replaced by a new exercise page") }
        // 每局笔画次数 = 实际题数。模块改写题目集时（改题目/改答案+自定义题数）用改写后的 N；
        // 否则（纯自动笔画走宿主真实题目）优先用原生 match 捕获的题目数 N（PkNativeSession），兜底配置值。
        val rewrittenSet = Simian.customTitleEnabled || Simian.modifyAnswer
        val nativeN = PkNativeSession.nativeQuestionCount
        val countSource = when { rewrittenSet -> "rewrite"; nativeN > 0 -> "native-match"; else -> "config" }
        val total = when {
            rewrittenSet -> Simian.strokeSubmissionCount
            nativeN > 0 -> nativeN
            else -> Simian.strokeSubmissionCount
        }
        val session = StrokeSession(webView)
        strokeSession = session
        repeat(total) { index ->
            lateinit var task: Runnable
            task = Runnable {
                if (strokeSession !== session || !session.tasks.remove(task)) return@Runnable
                if (!webView.isAttachedToWindow) {
                    logI("SimianV2 笔画提交失败：WebView已经离开窗口")
                    return@Runnable
                }
                submitStrokeWithoutDrawing(webView, index + 1, total)
                if (session.tasks.isEmpty() && strokeSession === session) strokeSession = null
            }
            session.tasks += task
            handler.postDelayed(task, delay.coerceAtLeast(0L) + index * 2_400L)
        }
        logI("SimianV2 stroke session scheduled: $total (source=$countSource)")
    }

    /** Cancels the complete delayed-stroke sequence for this WebView. */
    fun cancelStrokeSession(webView: WebView, reason: String) {
        val session = strokeSession ?: return
        if (session.webView !== webView) return
        session.tasks.forEach(handler::removeCallbacks)
        val cancelled = session.tasks.size
        session.tasks.clear()
        strokeSession = null
        if (cancelled > 0) logI("SimianV2 stroke session cancelled: $cancelled ($reason)")
    }

    private fun submitStrokeWithoutDrawing(webView: WebView, index: Int, total: Int) {
        if (!webView.isAttachedToWindow) {
            logI("SimianV2 笔画提交失败：WebView已经离开窗口")
            return
        }
        val startTime = System.currentTimeMillis()
        val statusKey = "__autoOralStroke_${startTime}_${index}"
        val pointsJson = JSONArray().apply {
            points.forEachIndexed { pointIndex, point ->
                put(JSONObject().apply {
                    put("x", point.x.toDouble())
                    put("y", point.y.toDouble())
                    put("pressure", 0)
                    put("time", startTime + pointIndex * 8L)
                })
            }
        }
        val script = """
            (() => {
                const points = $pointsJson;
                const key = ${JSONObject.quote(statusKey)};
                const state = window[key] = { status: 'loading-module', pointCount: points.length, startedAt: Date.now() };
                const fail = error => { state.status = 'failed'; state.error = String(error); state.finishedAt = Date.now(); };
                System.import('$PAD_MODULE_URL')
                    .then(module => {
                        const deadline = Date.now() + 4000;
                        const waitForPad = () => {
                            const store = module.d?.();
                            // module.d() exposes Vue refs/proxies. Find the real SignaturePad instance K,
                            // not its wrapper: K owns _data, toData(), and EventTarget dispatchEvent().
                            const isRealPad = value => value &&
                                typeof value.dispatchEvent === 'function' &&
                                typeof value.toData === 'function' &&
                                ('_data' in value);
                            const rootPad = store?.pad;
                            const candidates = [
                                rootPad, rootPad?.value, rootPad?._value,
                                rootPad?.value?.value, rootPad?._value?.value,
                                store?.recognizeBoard?.pad, store?.recognizeBoard?.pad?.value,
                            ];
                            const pad = candidates.find(isRealPad);
                            if (!pad) {
                                state.diagnostic = candidates.map((item, i) => {
                                    if (!item) return i + ':null';
                                    let keys = [];
                                    try { keys = Object.keys(item).slice(0, 8); } catch (_) {}
                                    return i + ':' + (item.constructor?.name || typeof item) +
                                        ':dispatch=' + typeof item.dispatchEvent +
                                        ':toData=' + typeof item.toData +
                                        ':keys=' + keys.join(',');
                                }).join('|');
                                if (Date.now() < deadline) {
                                    state.status = 'waiting-pad';
                                    setTimeout(waitForPad, 100);
                                } else {
                                    fail('未找到真实画板 K（等待 4000ms）: ' + state.diagnostic);
                                }
                                return;
                            }
                            pad._data = [{
                                points: points, penColor: '#000', minWidth: 3, maxWidth: 3,
                                velocityFilterWeight: 0.7, compositeOperation: 'source-over'
                            }];
                            state.status = 'dispatching-end-stroke';
                            pad.dispatchEvent(new CustomEvent('endStroke', { detail: { synthetic: true } }));
                            state.status = 'submitted';
                            state.finishedAt = Date.now();
                        };
                        waitForPad();
                    })
                    .catch(fail);
                return JSON.stringify({ key: key, status: state.status, pointCount: state.pointCount });
            })();
        """.trimIndent()
        webView.post {
            if (!webView.isAttachedToWindow) {
                logI("SimianV2 笔画提交失败：WebView已经离开窗口")
                return@post
            }
            webView.evaluateJavascript(script) { result ->
                logI("SimianV2 笔画提交 $index/$total started: $result")
                observeStrokeStatus(webView, statusKey, index, total, 0)
            }
        }
    }

    /** Reads the asynchronous JS status until it reaches submitted/failed or times out. */
    private fun observeStrokeStatus(webView: WebView, statusKey: String, index: Int, total: Int, attempt: Int) {
        handler.postDelayed({
            if (!webView.isAttachedToWindow) {
                logI("SimianV2 笔画提交 $index/$total cancelled: WebView detached")
                return@postDelayed
            }
            val readScript = "JSON.stringify(window[${JSONObject.quote(statusKey)}] || { status: 'missing' })"
            webView.evaluateJavascript(readScript) { raw ->
                // evaluateJavascript wraps a JavaScript string as a JSON string; decode once first.
                val state = raw?.let { callback ->
                    runCatching { JSONArray("[$callback]").getString(0) }.getOrDefault(callback)
                } ?: "null"
                val terminal = state.contains("\"status\":\"submitted\"") ||
                    state.contains("\"status\":\"failed\"") ||
                    state.contains("\"status\":\"missing\"")
                if (terminal || attempt >= 14) {
                    logI("SimianV2 笔画提交 $index/$total final: $state")
                } else {
                    observeStrokeStatus(webView, statusKey, index, total, attempt + 1)
                }
            }
        }, 300L)
    }
    fun clickHappyAccept(webView: WebView, delay: Long = 3000L) = schedule(Task.HAPPY, webView, delay, "开心收下") { click(webView,"开心收下") }
    fun clickContinue(webView: WebView, delay: Long = 500L) = schedule(Task.CONTINUE, webView, delay, "继续") { click(webView,"继续") }
    fun clickContinuePk(webView: WebView, delay: Long = 2000L) = schedule(Task.CONTINUE_PK, webView, delay, "继续PK") { click(webView,"继续PK") }

    private fun schedule(kind: Task, webView: WebView, delay: Long, label: String, action: () -> Unit) {
        tasks.remove(kind)?.let(handler::removeCallbacks)
        lateinit var task: Runnable
        task = Runnable {
            if (tasks[kind] !== task) return@Runnable
            tasks.remove(kind)
            if (!webView.isAttachedToWindow) { logI("SimianV2 " + label + " cancelled: WebView detached"); return@Runnable }
            runCatching(action).onFailure { error -> logI("SimianV2 " + label + " failed: " + error.message) }
        }
        tasks[kind] = task
        handler.postDelayed(task, delay.coerceAtLeast(0L))
    }
    private fun click(webView: WebView, label: String) {
        val text = JSONObject.quote(label)
        val script = """(() => {
            const t=$text, visible=e=>{if(!e)return false;const s=getComputedStyle(e),r=e.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&r.width>0&&r.height>0}, textOf=e=>(e.textContent||'').replace(/\s+/g,'');
            const button=[...document.querySelectorAll('button,[role=button],.button,.btn,.retry,.modal-confirm,.bottom-content-button,.btn-confirm-wrap')].find(e=>visible(e)&&textOf(e)===t);
            if(button) button.click(); else console.log('SimianV2 missing '+t);
        })();""".trimIndent()
        evaluate(webView, script, "点击" + label)
    }
    private fun evaluate(webView: WebView, script: String, action: String) = webView.post {
        if (!webView.isAttachedToWindow) { logI("SimianV2 " + action + " failed: WebView detached"); return@post }
        webView.evaluateJavascript(script) { result -> logI("SimianV2 " + action + " result: " + result) }
    }
}

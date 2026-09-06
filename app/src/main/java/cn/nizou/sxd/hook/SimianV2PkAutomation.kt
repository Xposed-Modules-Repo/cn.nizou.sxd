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
        // Simian f2683bd “修复自动答题”：枚举页面实际加载的 /leo-web-oral-pk/assets/index-legacy.*.js，
        // 逐个 System.import 并校验 module.d 源码含 recognizeConfig+pad 且 recognizeConfig 已初始化，
        // 避免选到空或旧模块实例。
        val script = """
(() => {
    const points = $pointsJson;
    const status = window.__strokeSubmitStatus = { status: 'finding-module', pointCount: points.length };
    const unref = target => { if (target && typeof target === 'object' && 'value' in target) { return target.value; } return target; };
    const findWritingModule = async () => {
        const resourceUrls = performance.getEntriesByType('resource').map(item => item.name);
        const scriptUrls = Array.from(document.scripts).map(item => item.src).filter(Boolean);
        const candidates = Array.from(new Set([...resourceUrls, ...scriptUrls]))
            .filter(url => url.includes('/leo-web-oral-pk/assets/') && /index-legacy\.[^/]+\.js/.test(url));
        status.candidates = candidates;
        for (const moduleUrl of candidates) {
            try {
                const module = await System.import(moduleUrl);
                if (typeof module?.d !== 'function') continue;
                const exportSource = Function.prototype.toString.call(module.d);
                if (!exportSource.includes('recognizeConfig') || !exportSource.includes('pad')) continue;
                const store = module.d();
                const pad = unref(store?.pad);
                const recognizeConfig = unref(store?.recognizeConfig);
                if (!pad || typeof pad.dispatchEvent !== 'function' || typeof pad.toData !== 'function') continue;
                if (!recognizeConfig) continue;
                return { moduleUrl, store, pad, recognizeConfig };
            } catch (_) { /* 当前候选不是画板模块，继续检查 */ }
        }
        throw new Error('没有找到已初始化的画板模块');
    };
    if (typeof System === 'undefined' || typeof System.import !== 'function') { status.status = 'failed'; status.error = '当前页面不支持System.import'; return JSON.stringify(status); }
    findWritingModule().then(result => {
        const pad = result.pad; const config = result.recognizeConfig;
        status.moduleUrl = result.moduleUrl; status.keypointId = config.keypointId; status.expectedResult = config.answers;
        pad._data = [{ points: points, penColor: '#000', minWidth: 3, maxWidth: 3, velocityFilterWeight: 0.7, compositeOperation: 'source-over' }];
        if ('_isEmpty' in pad) { pad._isEmpty = false; }
        status.status = 'dispatching-end-stroke';
        pad.dispatchEvent(new CustomEvent('endStroke', { detail: { synthetic: true } }));
        status.status = 'waiting-recognition';
    }).catch(error => { status.status = 'failed'; status.error = String(error?.stack || error?.message || error); });
    return JSON.stringify(status);
})();
""".trimIndent()
        webView.post {
            if (!webView.isAttachedToWindow) { logI("SimianV2 笔画提交被取消：WebView detached"); return@post }
            webView.evaluateJavascript(script) { result ->
                logI("SimianV2 笔画提交 $index/$total result: " + result)
                // 延迟读最终状态（waiting-recognition / failed）
                handler.postDelayed({
                    if (!webView.isAttachedToWindow) return@postDelayed
                    webView.evaluateJavascript("JSON.stringify(window.__strokeSubmitStatus || { status: 'missing' })") { raw ->
                        logI("SimianV2 笔画提交 $index/$total final: " + (raw ?: "null"))
                    }
                }, 3500L)
            }
        }
    }    fun clickHappyAccept(webView: WebView, delay: Long = 3000L) = schedule(Task.HAPPY, webView, delay, "开心收下") { click(webView,"开心收下") }
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

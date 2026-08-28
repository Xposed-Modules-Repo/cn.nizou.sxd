package cn.nizou.sxd.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cn.nizou.sxd.MODULE_PREFS_NAME

/**
 * 实时日志悬浮窗（Dialog 方案，2026-08-29 重构）。
 *
 * 与旧版差异：
 *  - **无需 SYSTEM_ALERT_WINDOW 悬浮窗权限**：改用 Dialog 叠加在宿主 Activity 窗口上
 *    （Dialog 随 Activity 显示/隐藏，只在应用前台可见，不需要任何系统权限）；
 *  - **仅在小猿口算（宿主）内生效**：非宿主进程（模块本体独立 App）调用 setEnabled 返回 false；
 *  - **可拖动**：按住标题栏拖动，Dialog 窗口位置（x/y）跟随；
 *  - **可最小化为悬浮球**：点「最小化」收起为 48dp 圆形小球，点小球展开；
 *  - 内容仍来自 [LogBuffer] 内存环形缓冲，轮询刷新自动滚底。
 *
 * 前提：必须在宿主 Activity 前台时调用（注入面板/宿主内 UI 均满足）。
 */
object LogOverlayWindow {

    private const val REFRESH_MS = 500L

    /** 与 res/values/strings.xml key_log_overlay 的值保持一致 */
    const val PREFS_KEY_OVERLAY = "log_overlay_enabled"

    /** 宿主包名（小猿口算）。 */
    const val HOST_PACKAGE = "com.fenbi.android.leo"

    private var dialog: Dialog? = null
    private var minimized = false
    private var logText: TextView? = null
    private var refreshRunnable: Runnable? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    val isShowing get() = dialog?.isShowing == true

    /** 当前进程是否为宿主（小猿口算）进程。 */
    fun isHostProcess(context: Context): Boolean =
        context.applicationContext.packageName == HOST_PACKAGE

    /** 是否已持久化开启。 */
    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(MODULE_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREFS_KEY_OVERLAY, false)

    /**
     * 开关悬浮日志。
     * @return true=已达成期望状态；false=未生效（非宿主进程，或宿主内找不到 Activity）。
     */
    @Synchronized
    fun setEnabled(context: Context, enable: Boolean): Boolean {
        if (!isHostProcess(context)) return false
        if (enable) {
            val activity = findActivity(context) ?: return false
            context.applicationContext.getSharedPreferences(MODULE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREFS_KEY_OVERLAY, true).apply()
            show(activity)
        } else {
            hide()
            context.applicationContext.getSharedPreferences(MODULE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREFS_KEY_OVERLAY, false).apply()
        }
        return true
    }

    private fun findActivity(context: Context): Activity? {
        var c = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    private fun show(activity: Activity) {
        if (dialog != null) return
        val d = Dialog(activity)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setCancelable(false)
        d.setCanceledOnTouchOutside(false)

        val root = buildPanel(activity, d)
        d.setContentView(root)

        val lp = d.window!!.attributes
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = (activity.resources.displayMetrics.heightPixels * 0.55f).toInt()
        lp.gravity = Gravity.TOP
        lp.x = 0
        lp.y = dp(activity, 140)
        lp.format = PixelFormat.TRANSLUCENT
        // 不抢焦点、不拦截窗口外触摸：悬浮于宿主之上但不干扰操作
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        d.window!!.attributes = lp

        d.show()
        dialog = d
        minimized = false
        startRefreshing()
    }

    /** 构建完整面板（标题栏 + 日志区）。 */
    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanel(activity: Activity, d: Dialog): FrameLayout {
        val root = FrameLayout(activity)
        root.setBackgroundColor(0xE6111620.toInt())
        root.isClickable = true

        val titleBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF1F242E.toInt())
        }
        val title = TextView(activity).apply {
            text = "AutoOral 实时日志（可拖动）"
            setTextColor(0xFFE3E3E3.toInt())
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(16, 0, 8, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val minBtn = Button(activity).apply {
            text = "悬浮球"
            textSize = 12f
            isAllCaps = false
            setOnClickListener { toggleMinimize() }
        }
        val closeBtn = Button(activity).apply {
            text = "关闭"
            textSize = 12f
            isAllCaps = false
            setOnClickListener { hide() }
        }
        titleBar.addView(title)
        titleBar.addView(minBtn)
        titleBar.addView(closeBtn)

        // 拖动：按住标题栏移动 -> 更新 Dialog 窗口位置
        titleBar.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var lpX = 0
            private var lpY = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val w = d.window ?: return false
                return when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        lpX = w.attributes.x
                        lpY = w.attributes.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val attrs = w.attributes
                        attrs.x = lpX + (event.rawX - startX).toInt()
                        attrs.y = lpY + (event.rawY - startY).toInt()
                        w.attributes = attrs
                        true
                    }
                    else -> false
                }
            }
        })

        val scroll = ScrollView(activity).apply { setBackgroundColor(0x00000000) }
        val logView = TextView(activity).apply {
            text = LogBuffer.snapshotText()
            setTextColor(0xFF9CDCFE.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(12, 8, 12, 8)
        }
        scroll.addView(logView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        logText = logView

        root.addView(titleBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.TOP)
            .apply { topMargin = dp(activity, 44) })
        return root
    }

    /** 展开 <-> 悬浮球切换。 */
    @SuppressLint("ClickableViewAccessibility")
    fun toggleMinimize() {
        val d = dialog ?: return
        val activity = d.context as? Activity ?: return
        val w = d.window ?: return
        val attrs = w.attributes
        if (!minimized) {
            // 收起为悬浮球：48dp 圆形，仅显示日志条数/“L”
            minimized = true
            val ball = FrameLayout(activity).apply {
                setBackgroundColor(0xE61F242E.toInt())
                setOnClickListener { toggleMinimize() }
            }
            val label = TextView(activity).apply {
                text = "L"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            }
            ball.addView(label, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            ball.setOnTouchListener(object : View.OnTouchListener {
                private var startX = 0f
                private var startY = 0f
                private var lpX = 0
                private var lpY = 0
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    return when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.rawX; startY = event.rawY
                            lpX = w.attributes.x; lpY = w.attributes.y
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val a = w.attributes
                            a.x = lpX + (event.rawX - startX).toInt()
                            a.y = lpY + (event.rawY - startY).toInt()
                            w.attributes = a
                            true
                        }
                        else -> false
                    }
                }
            })
            d.setContentView(ball)
            val size = dp(activity, 48)
            attrs.width = size
            attrs.height = size
            w.attributes = attrs
        } else {
            minimized = false
            d.setContentView(buildPanel(activity, d))
            attrs.width = WindowManager.LayoutParams.MATCH_PARENT
            attrs.height = (activity.resources.displayMetrics.heightPixels * 0.55f).toInt()
            w.attributes = attrs
            logText = null
            startRefreshing()
        }
    }

    @Synchronized
    fun hide() {
        val d = dialog ?: return
        stopRefreshing()
        runCatching { if (d.isShowing) d.dismiss() }
        dialog = null
        minimized = false
        logText = null
    }

    /** 挂一个循环 Runnable 拉取 LogBuffer 快照刷新 UI，并自动滚到底部 */
    private fun startRefreshing() {
        stopRefreshing()
        val runnable = object : Runnable {
            override fun run() {
                val tv = logText ?: return
                tv.text = LogBuffer.snapshotText()
                (tv.parent as? ScrollView)?.post { (tv.parent as ScrollView).fullScroll(ScrollView.FOCUS_DOWN) }
                mainHandler.postDelayed(this, REFRESH_MS)
            }
        }
        refreshRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun stopRefreshing() {
        refreshRunnable?.let { mainHandler.removeCallbacks(it) }
        refreshRunnable = null
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

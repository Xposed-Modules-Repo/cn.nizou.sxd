package cn.nizou.sxd.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cn.nizou.sxd.MODULE_PREFS_NAME

/**
 * 实时日志悬浮窗（WindowManager + TYPE_APPLICATION_OVERLAY）。
 *
 * - 需要 SYSTEM_ALERT_WINDOW 悬浮窗权限（未授予时自动拉起系统授权页）。
 * - 悬浮窗显示 [LogBuffer] 内存环形缓冲的最近日志，[REFRESH_MS] 间隔轮询刷新、自动滚到底部。
 * - 顶部标题栏带「关闭」按钮，可随时 hide。
 * - 打开/关闭状态持久化到模块 prefs（键 = [PREFS_KEY_OVERLAY]）。
 *
 * 模块本体与宿主注入面板都在各自进程各自持有此单例，悬浮窗只显示**当前进程**收集的日志。
 */
object LogOverlayWindow {

    private const val REFRESH_MS = 500L

    /** 与 res/values/strings.xml key_log_overlay 的值保持一致 */
    const val PREFS_KEY_OVERLAY = "log_overlay_enabled"

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var logText: TextView? = null
    private var refreshRunnable: Runnable? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    val isShowing get() = overlayView != null

    /** 悬浮窗权限是否已授予 */
    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context.applicationContext)

    /** 拉起系统悬浮窗权限授权页 */
    fun requestOverlayPermission(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${appContext.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
            .onFailure {
                // 兜底：直接打开应用详情设置
                runCatching {
                    appContext.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${appContext.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
    }

    /** 是否已持久化开启 */
    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(MODULE_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREFS_KEY_OVERLAY, false)

    /**
     * 开关悬浮窗。返回 true 表示期望状态已达成（已显示/已隐藏），
     * false 表示需要先授权悬浮窗权限（已拉起授权页）。
     */
    @Synchronized
    fun setEnabled(context: Context, enable: Boolean): Boolean {
        val appContext = context.applicationContext
        if (enable) {
            if (!canDrawOverlays(appContext)) {
                // 未授权：拉起授权页，不置为开启（UI 开关回弹为关）
                requestOverlayPermission(appContext)
                return false
            }
            appContext.getSharedPreferences(MODULE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREFS_KEY_OVERLAY, true).apply()
            show(appContext)
        } else {
            hide()
            appContext.getSharedPreferences(MODULE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREFS_KEY_OVERLAY, false).apply()
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show(context: Context) {
        if (overlayView != null) return
        val appContext = context.applicationContext
        if (!canDrawOverlays(appContext)) return

        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val root = FrameLayout(appContext)
        root.setBackgroundColor(0xE6111620.toInt()) // 半透明深色底
        root.isClickable = true

        // 标题栏：标题 + 关闭按钮
        val titleBar = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF1F242E.toInt())
        }
        val title = TextView(appContext).apply {
            text = "AutoOral 实时日志"
            setTextColor(0xFFE3E3E3.toInt())
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(16, 0, 16, 0)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        val closeBtn = Button(appContext).apply {
            text = "关闭"
            textSize = 12f
            isAllCaps = false
            setOnClickListener { hide() }
        }
        titleBar.addView(title)
        titleBar.addView(closeBtn)

        // 日志滚动区
        val scroll = ScrollView(appContext).apply {
            setBackgroundColor(0x00000000)
        }
        val logView = TextView(appContext).apply {
            text = LogBuffer.snapshotText()
            setTextColor(0xFF9CDCFE.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(12, 8, 12, 8)
        }
        scroll.addView(
            logView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            titleBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.TOP
            ).apply { topMargin = dp(appContext, 44) }
        )

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (appContext.resources.displayMetrics.heightPixels * 0.6f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
        }

        wm.addView(root, lp)

        windowManager = wm
        overlayView = root
        logText = logView

        startRefreshing(logView, scroll)
    }

    @Synchronized
    fun hide() {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        stopRefreshing()
        runCatching { wm.removeView(view) }
        windowManager = null
        overlayView = null
        logText = null
    }

    /** 挂一个循环 Runnable 拉取 LogBuffer 快照刷新 UI，并自动滚到底部 */
    private fun startRefreshing(logView: TextView, scroll: ScrollView) {
        stopRefreshing()
        val runnable = object : Runnable {
            override fun run() {
                logView.text = LogBuffer.snapshotText()
                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
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

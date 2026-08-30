package cn.nizou.sxd.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import cn.nizou.sxd.util.logI

/**
 * 配置导入/导出的透明承载 Activity（对齐 WeKit `TransparentActivity`）。
 *
 * 注入面板跑在宿主的 [androidx.activity.ComponentDialog]（非 Activity，没有
 * ActivityResultRegistry），无法直接用 `rememberLauncherForActivityResult` 启动安卓官方
 * 文件选取工具（SAF）。WeKit 的成熟做法是：点导出/导入时先拉起一个**本模块的透明
 * Activity**，在它的 onCreate 里 `registerForActivityResult(CreateDocument/OpenDocument)`
 * 立即启动 SAF；结果回调、读写文件、toast 都在这个 Activity 里完成，最后 finish()。
 *
 * 本 Activity 是模块组件（模块进程），因此配置读写走 [cn.nizou.sxd.util.ConfigTransfer]
 * 的 root 路径（宿主 prefs 在宿主私有目录，模块进程需 su）。
 *
 * 安全：`pendingAction` 为静态单槽，用 [EXTRA_VALID] 校验调用来源（对齐 wekit 的
 * launch 约定并加固：外部 App 直接启动时因缺少标记直接 finish）。
 */
class ConfigTransferActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!intent.getBooleanExtra(EXTRA_VALID, false)) {
            logI("ConfigTransferActivity: rejected launch without valid marker")
            finish()
            return
        }

        window.apply {
            requestFeature(Window.FEATURE_NO_TITLE)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowInsetsControllerCompat(this, decorView).isAppearanceLightStatusBars = true
        }
        setTheme(android.R.style.Theme_Translucent_NoTitleBar)

        val action = pendingAction ?: run { finish(); return }
        pendingAction = null
        action(this)
    }

    companion object {
        private const val EXTRA_VALID = "cn.nizou.sxd.config_transfer_valid"

        @Volatile
        private var pendingAction: (ComponentActivity.() -> Unit)? = null

        /** 拉起透明承载 Activity 并在其生命周期内执行 [action]（注册 SAF launcher 等）。 */
        fun launch(context: Context, action: ComponentActivity.() -> Unit) {
            pendingAction = action
            context.startActivity(
                Intent(context, ConfigTransferActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(EXTRA_VALID, true)
                }
            )
        }
    }
}

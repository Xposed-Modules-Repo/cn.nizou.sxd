package cn.nizou.sxd.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import cn.nizou.sxd.BuildConfig
import cn.nizou.sxd.util.ConfigActions
import cn.nizou.sxd.util.logI

/**
 * 配置导入/导出的透明承载 Activity（对齐 WeKit `TransparentActivity`）。
 *
 * 注入面板跑在宿主的 ComponentDialog（非 Activity，没有 ActivityResultRegistry），无法
 * 直接起安卓官方文件选取工具（SAF）。解法：点导出/导入时先拉起**本模块的透明 Activity**，
 * 在它的 onCreate 里 `registerForActivityResult(CreateDocument/OpenDocument)` 立即启动 SAF。
 *
 * ⚠️ 跨进程要点（1.7.19 修复，真机闪退根因）：
 * 1. 本 Activity 是模块组件，从注入面板（宿主进程）启动时跑在**模块进程**——不能把
 *    pendingAction 闭包塞静态字段（进程级，跨进程读不到），改为 Intent extra 传**操作类型**；
 * 2. `Intent(context, cls)` 的 ComponentName 包名取自 context.getPackageName()，注入面板的
 *    context 是宿主 context（包名 com.fenbi.android.leo）→ 与模块类名不匹配 → 宿主
 *    ActivityNotFoundException 闪退。必须显式 `component = ComponentName(模块包, 类名)`。
 *
 * 安全：[EXTRA_VALID] 校验调用来源（外部 App 直接启动时缺标记直接 finish）。
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

        // 模块进程内执行：registerForActivityResult 必须在本 onCreate（STARTED 前）注册。
        when (intent.getStringExtra(EXTRA_OP)) {
            OP_EXPORT -> ConfigActions.runExport(this)
            OP_IMPORT -> ConfigActions.runImport(this)
            else -> {
                logI("ConfigTransferActivity: unknown op, finish")
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_VALID = "cn.nizou.sxd.config_transfer_valid"
        private const val EXTRA_OP = "cn.nizou.sxd.config_transfer_op"
        private const val OP_EXPORT = "export"
        private const val OP_IMPORT = "import"

        fun launchExport(context: Context) = launch(context, OP_EXPORT)

        fun launchImport(context: Context) = launch(context, OP_IMPORT)

        /**
         * 显式 component 启动（包名固定为模块自身，不能取 context.getPackageName()——
         * 注入面板场景 context 是宿主包名，会 ActivityNotFoundException 闪退）。
         */
        private fun launch(context: Context, op: String) {
            val intent = Intent().apply {
                component = ComponentName(BuildConfig.APPLICATION_ID, ConfigTransferActivity::class.java.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_VALID, true)
                putExtra(EXTRA_OP, op)
            }
            runCatching { context.startActivity(intent) }.onFailure { e ->
                logI("ConfigTransferActivity: launch failed: $e")
                runCatching {
                    Toast.makeText(
                        context.applicationContext,
                        "打开文件选择器失败：${e.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
}

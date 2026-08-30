package cn.nizou.sxd.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import cn.nizou.sxd.ui.ConfigTransferActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * 配置导出 / 导入动作（对齐 WeKit `SettingsConfigActions` 的 SAF + JSON 语义）。
 *
 * 两条路径（按进程分流）：
 * 1. **宿主进程（注入面板）**：ComponentDialog 无 ActivityResultRegistry，改用宿主
 *    SettingsActivity.startActivityForResult 发起 SAF（[HostResultBus] 接 onActivityResult），
 *    回调在宿主进程内**直读写 modulePrefs（内存权威）**——配置一定存在，不需要 root、
 *    不需要磁盘文件（用户反馈「没有配置文件」由此修复）。
 * 2. **模块进程（模块本体 / 透明 Activity）**：[ConfigTransferActivity] 承载 SAF（模块
 *    进程），配置源 = 宿主 prefs 磁盘文件，root 读写。
 */
object ConfigActions {

    /** 入口：导出配置（系统文件选择器选保存位置）。 */
    fun export(context: Context) {
        val activity = findActivity(context)
        if (ConfigTransfer.isHostProcess()) {
            // 借壳设置面板（HostSettingsActivity 是宿主进程内 ComponentActivity，有
            // ActivityResultRegistry）→ 用 registerForActivityResult（结果直回面板，不 finish）；
            // 旧 ComponentDialog 面板 → 宿主 SettingsActivity.startActivityForResult + HostResultBus。
            if (activity is ComponentActivity) {
                runExport(activity, finishOnDone = false)
            } else {
                exportInHostProcess(context)
            }
        } else {
            ConfigTransferActivity.launchExport(context)
        }
    }

    /** 入口：导入配置（系统文件选择器选 JSON 文件）。 */
    fun importFromDocument(context: Context) {
        val activity = findActivity(context)
        if (ConfigTransfer.isHostProcess()) {
            if (activity is ComponentActivity) {
                runImport(activity, finishOnDone = false)
            } else {
                importInHostProcess(context)
            }
        } else {
            ConfigTransferActivity.launchImport(context)
        }
    }

    // ------------------------------------------------------------- 宿主进程（注入面板）

    private fun exportInHostProcess(context: Context) {
        val activity = findActivity(context)
        if (activity == null) {
            toast(context, "导出失败：未找到宿主 Activity")
            return
        }
        HostResultBus.register(HostResultBus.REQ_EXPORT_CONFIG) { resultCode, data ->
            val uri = data?.data
            if (resultCode != Activity.RESULT_OK || uri == null) return@register
            thread {
                val result = runCatching {
                    // 宿主进程：读内存 prefs（权威，等价「配置已保存到 data」）
                    val exportJson = ConfigTransfer.exportJson()
                    context.contentResolver
                        .openOutputStream(uri, "w")!!
                        .use { it.writer().use { w -> w.write(exportJson) } }
                }
                mainHandler.post {
                    toast(context, if (result.isSuccess) "配置已导出" else "导出失败：${errorMessage(result)}")
                }
            }
        }
        activity.startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, defaultExportFileName())
            },
            HostResultBus.REQ_EXPORT_CONFIG,
        )
    }

    private fun importInHostProcess(context: Context) {
        val activity = findActivity(context)
        if (activity == null) {
            toast(context, "导入失败：未找到宿主 Activity")
            return
        }
        HostResultBus.register(HostResultBus.REQ_IMPORT_CONFIG) { resultCode, data ->
            val uri = data?.data
            if (resultCode != Activity.RESULT_OK || uri == null) return@register
            thread {
                val result = runCatching {
                    val jsonText = context.contentResolver
                        .openInputStream(uri)
                        ?.use { it.reader().readText() }
                        ?: throw IllegalStateException("无法读取所选文件")
                    val error = ConfigTransfer.importJson(jsonText) // 宿主进程：modulePrefs commit()
                    if (error != null) throw IllegalStateException(error)
                }
                mainHandler.post {
                    toast(
                        context,
                        if (result.isSuccess) "导入成功，重启小猿口算后生效" else "导入失败：${errorMessage(result)}",
                    )
                }
            }
        }
        activity.startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            },
            HostResultBus.REQ_IMPORT_CONFIG,
        )
    }

    // ------------------------------------------------------------- 模块进程（透明 Activity）

    /**
     * 在 [ConfigTransferActivity]（模块进程，onCreate 内调用）执行导出流程：
     * 注册 CreateDocument launcher → 用户选位置 → 后台导出 JSON → toast → finish。
     * 借壳设置面板（宿主进程 ComponentActivity）也走本方法，但 finishOnDone=false 保持面板打开。
     */
    fun runExport(activity: ComponentActivity, finishOnDone: Boolean = true) {
        val exportLauncher = activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri == null) {
                if (finishOnDone) activity.finish()
                return@registerForActivityResult
            }
            activity.lifecycleScope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val exportJson = ConfigTransfer.exportJson()
                    activity.contentResolver
                        .openOutputStream(uri, "w")!!
                        .use { stream ->
                            stream.writer().use { it.write(exportJson) }
                        }
                }
                withContext(Dispatchers.Main) {
                    toast(
                        activity,
                        if (result.isSuccess) "配置已导出" else "导出失败：${errorMessage(result)}",
                    )
                    if (finishOnDone) activity.finish()
                }
            }
        }
        exportLauncher.launch(defaultExportFileName())
    }

    /**
     * 在 [ConfigTransferActivity]（模块进程，onCreate 内调用）执行导入流程：
     * 注册 OpenDocument launcher → 用户选文件 → 后台全量覆盖宿主 prefs → toast → finish。
     * 借壳设置面板（宿主进程 ComponentActivity）也走本方法，但 finishOnDone=false 保持面板打开。
     */
    fun runImport(activity: ComponentActivity, finishOnDone: Boolean = true) {
        val importLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) {
                if (finishOnDone) activity.finish()
                return@registerForActivityResult
            }
            activity.lifecycleScope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val jsonText = activity.contentResolver
                        .openInputStream(uri)
                        ?.use { it.reader().readText() }
                        ?: throw IllegalStateException("无法读取所选文件")
                    val error = ConfigTransfer.importJson(jsonText)
                    if (error != null) throw IllegalStateException(error)
                }
                withContext(Dispatchers.Main) {
                    toast(
                        activity,
                        if (result.isSuccess) "导入成功，重启小猿口算后生效" else "导入失败：${errorMessage(result)}",
                    )
                    if (finishOnDone) activity.finish()
                }
            }
        }
        importLauncher.launch(arrayOf("application/json"))
    }

    // ------------------------------------------------------------------ 工具

    /** 沿 context 包装链找宿主 Activity（ModuleContextWrapper → 宿主 SettingsActivity）。 */
    private fun findActivity(context: Context): Activity? {
        var cur: Context? = context
        while (cur != null) {
            if (cur is Activity) return cur
            cur = (cur as? ContextWrapper)?.baseContext
        }
        return null
    }

    private fun toast(context: Context, message: String) {
        runCatching {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun errorMessage(result: Result<*>): String =
        result.exceptionOrNull()?.message ?: "未知错误"

    /** 默认导出文件名：nizou_config_20260830.json。 */
    private fun defaultExportFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        return "nizou_config_$stamp.json"
    }
}

package cn.nizou.sxd.util

import android.content.Context
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

/**
 * 配置导出 / 导入动作（对齐 WeKit `SettingsConfigActions`）。
 *
 * 入口（注入面板设置页 / 模块本体）只负责拉起 [ConfigTransferActivity]：
 * - 导出 = `CreateDocument("application/json")`，导入 = `OpenDocument(["application/json"])`；
 * - SAF 注册与 IO 在透明 Activity（**模块进程**）内执行，配置源走 [ConfigTransfer] 的
 *   root 路径（宿主 prefs 在宿主私有目录，模块进程需 su）；
 * - IO 走 Dispatchers.IO，toast 回主线程，完成后 finish()。
 */
object ConfigActions {

    /** 入口：导出配置（系统文件选择器选保存位置）。 */
    fun export(context: Context) {
        ConfigTransferActivity.launchExport(context)
    }

    /** 入口：导入配置（系统文件选择器选 JSON 文件）。 */
    fun importFromDocument(context: Context) {
        ConfigTransferActivity.launchImport(context)
    }

    /**
     * 在 [ConfigTransferActivity]（模块进程，onCreate 内调用）执行导出流程：
     * 注册 CreateDocument launcher → 用户选位置 → 后台导出 JSON → toast → finish。
     */
    fun runExport(activity: ComponentActivity) {
        val exportLauncher = activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri == null) {
                activity.finish()
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
                    Toast.makeText(
                        activity.applicationContext,
                        if (result.isSuccess) "配置已导出" else "导出失败：${errorMessage(result)}",
                        Toast.LENGTH_LONG,
                    ).show()
                    activity.finish()
                }
            }
        }
        exportLauncher.launch(defaultExportFileName())
    }

    /**
     * 在 [ConfigTransferActivity]（模块进程，onCreate 内调用）执行导入流程：
     * 注册 OpenDocument launcher → 用户选文件 → 后台全量覆盖宿主 prefs → toast → finish。
     */
    fun runImport(activity: ComponentActivity) {
        val importLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) {
                activity.finish()
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
                    Toast.makeText(
                        activity.applicationContext,
                        if (result.isSuccess) {
                            "导入成功，重启小猿口算后生效"
                        } else {
                            "导入失败：${errorMessage(result)}"
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                    activity.finish()
                }
            }
        }
        importLauncher.launch(arrayOf("application/json"))
    }

    private fun errorMessage(result: Result<*>): String =
        result.exceptionOrNull()?.message ?: "未知错误"

    /** 默认导出文件名：nizou_config_20260830.json。 */
    private fun defaultExportFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        return "nizou_config_$stamp.json"
    }
}

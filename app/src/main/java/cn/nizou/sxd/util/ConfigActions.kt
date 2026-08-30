package cn.nizou.sxd.util

import android.content.Context
import android.widget.Toast
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
 * 配置导出 / 导入动作（对齐 WeKit `SettingsConfigActions`）：
 *  - 统一经 [ConfigTransferActivity]（透明 Activity）承载 SAF；
 *  - 导出 = `CreateDocument("application/json")`，导入 = `OpenDocument(["application/json"])`；
 *  - IO 走 Dispatchers.IO，toast 回主线程，完成后 finish()。
 *
 * 两个入口（注入面板设置页 / 模块本体）都调用这里，配置源统一走 [ConfigTransfer]
 * （宿主 prefs；TransparentActivity 在模块进程 → root 读写）。
 */
object ConfigActions {

    /** 导出配置：把全部设置写成 JSON 文件（系统文件选择器选位置）。 */
    fun export(platformContext: Context) {
        ConfigTransferActivity.launch(platformContext) {
            val exportLauncher = registerForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                if (uri == null) {
                    finish()
                    return@registerForActivityResult
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = runCatching {
                        val exportJson = ConfigTransfer.exportJson()
                        platformContext.contentResolver
                            .openOutputStream(uri, "w")!!
                            .use { stream ->
                                stream.writer().use { it.write(exportJson) }
                            }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            platformContext.applicationContext,
                            if (result.isSuccess) "配置已导出" else "导出失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    }
                }
            }
            exportLauncher.launch(defaultExportFileName())
        }
    }

    /** 导入配置：从 JSON 文件恢复（全量覆盖当前设置，需重启小猿口算生效）。 */
    fun importFromDocument(platformContext: Context) {
        ConfigTransferActivity.launch(platformContext) {
            val importLauncher = registerForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri == null) {
                    finish()
                    return@registerForActivityResult
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = runCatching {
                        val jsonText = platformContext.contentResolver
                            .openInputStream(uri)
                            ?.use { it.reader().readText() }
                            ?: throw IllegalStateException("无法读取所选文件")
                        val error = ConfigTransfer.importJson(jsonText)
                        if (error != null) throw IllegalStateException(error)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            platformContext.applicationContext,
                            if (result.isSuccess) {
                                "导入成功，重启小猿口算后生效"
                            } else {
                                "导入失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    }
                }
            }
            importLauncher.launch(arrayOf("application/json"))
        }
    }

    /** 默认导出文件名：nizou_config_20260830.json。 */
    private fun defaultExportFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        return "nizou_config_$stamp.json"
    }
}

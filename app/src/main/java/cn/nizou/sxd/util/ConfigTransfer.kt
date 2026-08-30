package cn.nizou.sxd.util

import android.content.Context
import android.util.Xml
import cn.nizou.sxd.HOST_PACKAGE_NAME
import cn.nizou.sxd.MODULE_PREFS_NAME
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * 配置导出 / 导入核心（对齐 WeKit `SettingsConfigActions` 的 JSON 语义）。
 *
 * 本模块的「真实配置」存在**宿主私有目录**：
 *   /data/data/<HOST_PACKAGE_NAME>/shared_prefs/<MODULE_PREFS_NAME>.xml
 * （注入面板在宿主进程读写宿主 prefs；hook 层同样读宿主 prefs）。
 * 而 [ConfigTransferActivity] / 模块本体跑在**模块进程**，模块自己的 prefs 与真实配置
 * 无关 —— 必须经 root 读/写宿主 prefs 文件（沿用 HookStatusCard root 直读先例）。
 *
 * JSON 格式（扁平，对齐 wekit）：`{"key": value, ...}`，值类型由 JSON 值类型表达
 * （boolean / number / string / array-of-string），导入按 wekit 同款规则回写。
 */
object ConfigTransfer {

    private const val HOST_PREFS_FILE =
        "/data/data/$HOST_PACKAGE_NAME/shared_prefs/$MODULE_PREFS_NAME.xml"

    private val json = Json { ignoreUnknownKeys = true }

    /** 当前进程是否为宿主进程（注入面板场景；此时可直接读写宿主 prefs，免 root）。 */
    fun isHostProcess(): Boolean =
        runCatching { currentApplication()?.packageName == HOST_PACKAGE_NAME }
            .getOrDefault(false)

    /**
     * 导出：读真实配置 → 扁平 JSON 文本。
     * **账号类键（`ui_` 前缀：名字/cookie/uid/头像/子账号等，UserInfoStore 数据）不导出**，
     * 避免配置文件泄露账号隐私（用户 2026-08-30 要求）。
     * @throws Exception 读取/序列化失败（调用方负责 toast 与 finish）。
     */
    fun exportJson(): String = buildJsonObject {
        for ((key, value) in readPrefsMap()) {
            if (key.startsWith("ui_")) continue // 账号数据不入配置
            when (value) {
                is Boolean -> put(key, value)
                is Int -> put(key, value)
                is Long -> put(key, value)
                is Float -> put(key, value)
                is String -> put(key, value)
                is Set<*> -> put(
                    key,
                    buildJsonArray {
                        @Suppress("UNCHECKED_CAST")
                        (value as Set<String>).forEach { add(JsonPrimitive(it)) }
                    }
                )
                else -> put(key, JsonNull)
            }
        }
    }.let { json.encodeToString(it) }

    /**
     * 导入：JSON 文本 → **全量覆盖**真实配置（导出→导入可完整还原）。
     * @return null=成功；否则为面向用户的错误信息。
     */
    fun importJson(jsonText: String): String? {
        val parsed = runCatching {
            json.parseToJsonElement(jsonText).jsonObject
        }.getOrElse { return "JSON 文件格式错误，无法解析" }

        val map = mutableMapOf<String, Any>()
        for ((key, element) in parsed) {
            if (key.startsWith("ui_")) continue // 账号数据不入配置（导入也忽略）
            when (element) {
                is JsonNull -> Unit // 全量覆盖语义下无需单独删除
                is JsonPrimitive -> when {
                    element.isString -> map[key] = element.content
                    element.booleanOrNull != null &&
                        (element.content == "true" || element.content == "false") ->
                        map[key] = element.boolean

                    element.longOrNull != null && element.intOrNull == null ->
                        map[key] = element.long

                    element.intOrNull != null -> map[key] = element.int
                    element.floatOrNull != null -> map[key] = element.float
                }
                is JsonArray -> map[key] = element.mapNotNull {
                    runCatching { it.jsonPrimitive.content }.getOrNull()
                }.toSet()
                else -> Unit
            }
        }
        return writePrefsMap(map)
    }

    // ------------------------------------------------------------------ 读真实配置

    private fun readPrefsMap(): Map<String, Any> = if (isHostProcess()) {
        val prefs = currentApplication()
            .getSharedPreferences(MODULE_PREFS_NAME, Context.MODE_PRIVATE)
        // SharedPreferences.all 的取值均非 null（本项目只存 boolean/string/int），
        // 星投影 → Map<String, Any> 属于安全收窄。
        @Suppress("UNCHECKED_CAST")
        prefs.all as Map<String, Any>
    } else {
        val res = su("cat $HOST_PREFS_FILE")
        val xml = res.output
        if (xml == null) {
            val err = res.error ?: "未知错误"
            throw IllegalStateException(
                if (err.contains("No such file") || err.contains("not found")) {
                    "宿主配置文件不存在（请先打开一次小猿口算，让模块写入配置）"
                } else {
                    "无法读取宿主配置：$err"
                }
            )
        }
        parsePrefsXml(xml)
    }

    /** 解析 SharedPreferences XML（<map> 下的 boolean/int/long/float/string/set）。 */
    private fun parsePrefsXml(xml: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name != "map") {
                val name = parser.getAttributeValue(null, "name")
                if (name != null) {
                    when (parser.name) {
                        "boolean" -> result[name] =
                            parser.getAttributeValue(null, "value").toBoolean()

                        "int" -> result[name] =
                            parser.getAttributeValue(null, "value").toInt()

                        "long" -> result[name] =
                            parser.getAttributeValue(null, "value").toLong()

                        "float" -> result[name] =
                            parser.getAttributeValue(null, "value").toFloat()

                        "string" -> result[name] = parser.nextText()

                        "set" -> {
                            val set = mutableSetOf<String>()
                            var inner = parser.next()
                            while (inner != XmlPullParser.END_TAG) {
                                if (inner == XmlPullParser.START_TAG && parser.name == "string") {
                                    set.add(parser.nextText())
                                }
                                inner = parser.next()
                            }
                            result[name] = set
                        }
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    // ------------------------------------------------------------------ 写真实配置

    /**
     * 全量覆盖真实配置。
     * @return null=成功；否则错误信息。
     */
    private fun writePrefsMap(map: Map<String, Any>): String? = if (isHostProcess()) {
        val editor = currentApplication()
            .getSharedPreferences(MODULE_PREFS_NAME, Context.MODE_PRIVATE).edit().clear()
        for ((key, value) in map) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
            }
        }
        if (editor.commit()) null else "写入宿主配置失败"
    } else {
        writeHostPrefsViaRoot(map)
    }

    /**
     * 模块进程：root 写宿主 prefs 文件。
     * 1) force-stop 宿主（其内存缓存可能把新文件覆盖回去；也保证下次启动读到新配置）；
     * 2) `su -c "cat > 文件"` 覆盖写入（文件存在则 inode 保留，owner/SELinux 不变）；
     * 3) 文件原本不存在时兜底 chown 到宿主 uid。
     */
    private fun writeHostPrefsViaRoot(map: Map<String, Any>): String? {
        val existed = su("cat $HOST_PREFS_FILE").output != null
        // force-stop 宿主，避免其 SharedPreferences 内存缓存稍后落盘覆盖新文件
        su("am force-stop $HOST_PACKAGE_NAME")
        val xml = buildPrefsXml(map)
        val write = suWrite(HOST_PREFS_FILE, xml)
        if (write.output == null) {
            return "写入宿主配置文件失败：${write.error ?: "root 不可用"}"
        }
        if (!existed) {
            val uid = hostUid()
            if (uid != null) {
                su("chown $uid:$uid $HOST_PREFS_FILE")
            } else {
                logI("ConfigTransfer: file was missing and host uid not resolved; " +
                    "SELinux/owner may need manual fix")
            }
        }
        return null
    }

    /** 构建 SharedPreferences 兼容的 XML。 */
    private fun buildPrefsXml(map: Map<String, Any>): String {
        val sb = StringBuilder()
        sb.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n")
        for ((key, value) in map) {
            when (value) {
                is Boolean -> sb.append(
                    "    <boolean name=\"${attr(key)}\" value=\"$value\" />\n"
                )

                is Int -> sb.append("    <int name=\"${attr(key)}\" value=\"$value\" />\n")
                is Long -> sb.append("    <long name=\"${attr(key)}\" value=\"$value\" />\n")
                is Float -> sb.append("    <float name=\"${attr(key)}\" value=\"$value\" />\n")
                is String -> sb.append(
                    "    <string name=\"${attr(key)}\">${text(value)}</string>\n"
                )

                is Set<*> -> {
                    sb.append("    <set name=\"${attr(key)}\">\n")
                    @Suppress("UNCHECKED_CAST")
                    (value as Set<String>).forEach {
                        sb.append("        <string>${text(it)}</string>\n")
                    }
                    sb.append("    </set>\n")
                }
            }
        }
        sb.append("</map>\n")
        return sb.toString()
    }

    /** XML 属性值转义（& < > " '）。 */
    private fun attr(s: String): String = s
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "&apos;")

    /** XML 文本节点转义（& < >）。 */
    private fun text(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    // ------------------------------------------------------------------ root 执行

    /** su 执行结果：成功时 [output] 非空；失败时 [error] 含面向用户的排查信息。 */
    private class SuResult(val output: String?, val error: String?)

    /**
     * 执行 `su -c <command>`。超时放宽到 15s：Magisk/KernelSU 首次授权会弹窗，
     * 8s 常不够用户点击允许。失败时把 stderr（redirectErrorStream 合并）带回，供
     * toast 区分「文件不存在 / su 未授权 / 命令超时」，不再笼统报「无 root 权限」。
     */
    private fun su(command: String, timeoutMs: Long = 15000): SuResult = try {
        val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            p.destroy()
            SuResult(null, "su 命令超时（${timeoutMs / 1000}s）——首次调用需在授权弹窗中允许，请重试")
        } else {
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.destroy()
            if (p.exitValue() == 0) SuResult(out, null)
            else SuResult(null, out.trim().ifEmpty { "su 退出码 ${p.exitValue()}" })
        }
    } catch (e: Exception) {
        SuResult(null, "su 执行异常：${e.message ?: e.javaClass.simpleName}")
    }

    /** `su -c "cat > 文件"`，内容走 stdin。 */
    private fun suWrite(file: String, content: String): SuResult = try {
        val p = ProcessBuilder("su", "-c", "cat > $file").redirectErrorStream(true).start()
        p.outputStream.writer().use { it.write(content) }
        if (!p.waitFor(15000, TimeUnit.MILLISECONDS)) {
            p.destroy()
            SuResult(null, "su 写入超时（15s）")
        } else {
            val err = p.inputStream.bufferedReader().use { it.readText() }
            p.destroy()
            if (p.exitValue() == 0) SuResult("ok", null)
            else SuResult(null, err.trim().ifEmpty { "su 退出码 ${p.exitValue()}" })
        }
    } catch (e: Exception) {
        SuResult(null, "su 写入异常：${e.message ?: e.javaClass.simpleName}")
    }

    /** 宿主应用 uid（dumpsys package 的 userId=），失败返回 null。 */
    private fun hostUid(): String? {
        val dump = su("dumpsys package $HOST_PACKAGE_NAME").output ?: return null
        val m = Regex("""userId=(\d+)""").find(dump) ?: return null
        return m.groupValues[1]
    }
}

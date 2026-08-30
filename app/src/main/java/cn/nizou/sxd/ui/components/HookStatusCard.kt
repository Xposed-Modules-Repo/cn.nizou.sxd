package cn.nizou.sxd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.MODULE_PREFS_NAME
import cn.nizou.sxd.XposedInit
import cn.nizou.sxd.util.currentApplication
import cn.nizou.sxd.util.logI
import java.util.concurrent.TimeUnit

/**
 * 激活检测卡片（真实状态，不硬编码）。
 *
 * 判定逻辑（三路并取，任一为 true 即已激活）：
 * 1. **本进程本地 prefs**（`hook_active`）：宿主导入面板由宿主进程写入；模块本体手动标记也写这里。
 * 2. **libxposed 跨进程 RemotePreferences**：宿主进程在 onPackageReady 注入成功后写入；
 *    需本进程被框架注入（`XposedInit.self` 已初始化）才能读。
 * 3. **root 直读宿主 shared_prefs**（新增，修复模块本体永远未激活）：模块本体是独立进程，
 *    未注入宿主、`XposedInit.self` 未初始化、也读不到宿主 App 私有目录——在 root 环境下
 *    `su -c cat /data/data/com.fenbi.android.leo/shared_prefs/auto_oral_calculation.xml`
 *    直接读宿主 attach 时写入的 hook_active，真实反映注入状态。
 *
 * **点击兜底**：仍检测不到（root 不可用/路径变化）时，点击卡片弹确认框，手动标记本地已激活
 * （仅本进程本地生效，卸载/清数据后需重新标记）。
 */
@Composable
fun HookStatusCard(modifier: Modifier = Modifier) {
    var activated by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }

    fun readStatus(): Boolean {
        // 1) 本进程本地 prefs（宿主导入面板写入 / 模块本体手动标记）
        val local = runCatching {
            currentApplication()?.getSharedPreferences(
                MODULE_PREFS_NAME, android.content.Context.MODE_PRIVATE
            )?.getBoolean("hook_active", false)
        }.getOrNull() ?: false
        // 2) RemotePreferences（需框架注入本进程；模块本体未注入时 self 未初始化 → 判空）
        val remote = runCatching {
            if (::XposedInit.self.isInitialized) {
                XposedInit.self.getRemotePreferences(MODULE_PREFS_NAME)
                    .getBoolean("hook_active", false)
            } else {
                false
            }
        }.getOrNull() ?: false
        // 3) root 直读宿主 shared_prefs（真实注入状态，模块本体进程 root 可用）
        val hostPrefs = readHostHookActive()
        logI("HookStatus: local=$local remote=$remote hostPrefs=$hostPrefs")
        return local || remote || hostPrefs
    }

    fun manualActivate() {
        runCatching {
            currentApplication()?.getSharedPreferences(
                MODULE_PREFS_NAME, android.content.Context.MODE_PRIVATE
            )?.edit()?.putBoolean("hook_active", true)?.apply()
        }.onFailure { logI("manual activate failed: ${it.message}") }
        activated = true
        manual = true
        checked = true
    }

    LaunchedEffect(Unit) {
        activated = readStatus()
        checked = true
    }

    val containerColor = if (activated) Color(0xFF2E7D32) else Color(0xFFC62828)
    val title = when {
        activated && manual -> "已激活（手动标记）"
        activated -> "已激活"
        else -> "未激活"
    }
    val desc = when {
        activated && manual -> "本进程已手动标记；请确认模块确已在 LSPosed 作用域生效"
        activated -> "模块已注入小猿口算，功能已生效"
        !checked -> "检测中…"
        else -> "点击卡片手动标记激活；或确认 LSPosed 作用域已勾选「小猿口算」并重启"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(enabled = checked && !activated) { showManualDialog = true },
        color = containerColor,
        contentColor = Color.White,
        shape = RoundedCornerShape(CornerRadius)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }

    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("手动标记为已激活？") },
            text = {
                Text(
                    "检测不到模块注入状态。若你确认模块已在小猿口算内生效（注入菜单显示已激活），" +
                        "可手动标记；该标记仅本机本进程生效，卸载/清除数据后需重新标记。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showManualDialog = false
                    manualActivate()
                }) { Text("确认激活") }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) { Text("取消") }
            }
        )
    }
}

/** root 直读宿主 shared_prefs 的 hook_active（模块本体独立进程验证真实注入状态）。 */
private fun readHostHookActive(): Boolean {
    return runCatching {
        val p = ProcessBuilder(
            "su", "-c",
            "cat /data/data/com.fenbi.android.leo/shared_prefs/auto_oral_calculation.xml"
        ).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor(3, TimeUnit.SECONDS)
        p.destroy()
        text.contains("name=\"hook_active\"") && text.contains("value=\"true\"")
    }.getOrDefault(false)
}

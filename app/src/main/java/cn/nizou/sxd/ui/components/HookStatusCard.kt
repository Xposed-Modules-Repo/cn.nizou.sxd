package cn.nizou.sxd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import cn.nizou.sxd.util.HookStatus

/**
 * 激活检测卡片（真实状态，不硬编码）。
 *
 * 判定逻辑（libxposed 跨进程 RemotePreferences）：
 * 宿主 com.fenbi.android.leo 进程在 onPackageReady 注入成功后，用 XposedModule
 * getRemotePreferences(MODULE_PREFS_NAME) 写入 hook_active=true；模块独立设置页/主界面
 * 进程读取同一份 RemotePreferences，从而真实反映「模块是否已成功注入宿主」。
 *
 * 2026-08-29：去掉此前「强制已激活」的硬编码——用户要求显示真实激活状态。
 */
@Composable
fun HookStatusCard(modifier: Modifier = Modifier) {
    var activated by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }

    // 读取激活标记：优先宿主进程本地 prefs（宿主导入面板可读，写入可靠）；
    // 拿不到(模块独立进程)再读 RemotePreferences；最后兜底本进程静态标记。
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val local = runCatching {
            cn.nizou.sxd.util.currentApplication()?.getSharedPreferences(
                MODULE_PREFS_NAME, android.content.Context.MODE_PRIVATE
            )?.getBoolean("hook_active", false)
        }.getOrNull() ?: false
        val remote = runCatching {
            val prefs = XposedInit.self.getRemotePreferences(MODULE_PREFS_NAME)
            HookStatus.isActivated(prefs)
        }.getOrNull() ?: false
        activated = local || remote
        checked = true
    }

    val containerColor = if (activated) Color(0xFF2E7D32) else Color(0xFFC62828)
    val title = if (activated) "已激活" else "未激活"
    val desc = when {
        activated -> "模块已注入小猿口算，功能已生效"
        !checked -> "检测中…"
        else -> "请确认已在 LSPosed 作用域内勾选「小猿口算」并重启"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
}
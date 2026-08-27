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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.MODULE_PREFS_NAME
import cn.nizou.sxd.XposedInit
import cn.nizou.sxd.util.HookStatus

/**
 * 激活检测卡片（照抄 WeKit 独立模块首页的绿色「已激活」/红色「未激活」卡片）。
 *
 * - 绿色 `已激活`：模块已注入宿主 `com.fenbi.android.leo`（经跨进程 RemotePreferences 判定）。
 * - 红色 `未激活`：未检测到激活（请确认 LSPosed 作用域已勾选小猿口算并重启）。
 */
@Composable
fun HookStatusCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 模块进程的 RemotePreferences（经 self 获取）。onModuleLoaded 已置 self。
    val prefsRemote = try {
        XposedInit.self.getRemotePreferences(MODULE_PREFS_NAME)
    } catch (_: Throwable) {
        null
    }
    val activated = HookStatus.isActivated(prefsRemote)

    val containerColor = if (activated) Color(0xFF2E7D32) else Color(0xFFC62828)
    val title = if (activated) "已激活" else "未激活"
    val desc = when {
        activated -> "模块已注入小猿口算，功能已生效"
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

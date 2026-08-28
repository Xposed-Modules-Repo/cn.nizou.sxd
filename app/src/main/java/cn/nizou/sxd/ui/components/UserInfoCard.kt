package cn.nizou.sxd.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.nizou.sxd.util.UserInfoStore
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Content_copy

/**
 * 用户信息卡片：展示昵称/ID/Cookie，点击复制 Cookie（对齐 wekit 用户信息展示语义）。
 * 数据来自 [UserInfoStore]（RetrofitHook 从宿主请求捕获，注入菜单/模块本体共用）。
 */
@Composable
fun UserInfoCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uid = UserInfoStore.userId
    val name = UserInfoStore.userName
    val cookie = UserInfoStore.cookie
    if (name.isBlank() && uid.isBlank() && cookie.isBlank()) return

    BaseItemContainer(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (cookie.isNotBlank()) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("cookie", cookie))
                        Toast.makeText(context, "Cookie 已复制（${cookie.length} 字符）", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "暂无 Cookie 可复制", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像：不引第三方网络图片库，用昵称首字符占位（后续可换 Coil）。
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.firstOrNull()?.toString() ?: "?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (name.isNotBlank()) name else "未登录",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (uid.isNotBlank()) "ID: $uid" else "ID: —",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cookie.isNotBlank()) {
                    Text(
                        text = "Cookie: ${cookie.take(60)}…",
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Cookie: 暂无（打开小猿口算后自动采集）",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = MaterialSymbols.Outlined.Content_copy,
                contentDescription = "复制Cookie",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

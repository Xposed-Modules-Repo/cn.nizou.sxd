package cn.nizou.sxd.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.nizou.sxd.util.UserInfoStore
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Content_copy
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 用户信息卡片：昵称/头像/ID/Cookie，点击复制 Cookie。
 * 数据：RetrofitHook 捕获的用户 JSON + Set-Cookie（UserInfoStore），叠加 WebView CookieManager
 * （https://xyks.yuanfudao.com）实时 Cookie；头像按 avatarUrl 下载。空数据也显示占位提示。
 */
@Composable
fun UserInfoCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uid = UserInfoStore.userId
    val name = UserInfoStore.userName
    val avatarUrl = UserInfoStore.avatarUrl
    val storedCookie = UserInfoStore.cookie
    // 叠加 WebView Cookie（宿主内可读，登录态主来源）
    val webCookie = remember {
        runCatching { CookieManager.getInstance().getCookie("https://xyks.yuanfudao.com") }
            .getOrDefault("") ?: ""
    }
    val fullCookie = listOf(storedCookie, webCookie).filter { it.isNotBlank() }.joinToString("; ")

    var avatar by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(avatarUrl) {
        if (avatarUrl.isNotBlank()) {
            avatar = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = (URL(avatarUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4000
                        readTimeout = 4000
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)")
                    }
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    BaseItemContainer(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (fullCookie.isNotBlank()) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("cookie", fullCookie))
                        Toast.makeText(context, "Cookie 已复制（${fullCookie.length} 字符）", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "暂无 Cookie 可复制（请先登录小猿口算）", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = avatar
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "头像",
                        modifier = Modifier.size(46.dp).clip(CircleShape),
                    )
                } else {
                    Text(
                        text = name.firstOrNull()?.toString() ?: "?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (name.isNotBlank()) name else "未采集到用户信息",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (uid.isNotBlank()) "ID: $uid" else "ID: —",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (fullCookie.isNotBlank())
                        "Cookie: ${fullCookie.take(60)}…（点击复制）"
                    else
                        "Cookie: 暂无（打开小猿口算后自动采集，点击刷新）",
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

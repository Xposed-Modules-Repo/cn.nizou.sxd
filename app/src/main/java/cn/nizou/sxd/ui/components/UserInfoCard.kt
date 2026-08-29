package cn.nizou.sxd.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.composables.icons.materialsymbols.outlined.Refresh
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 用户信息卡片组（**多子账号**）。
 *
 * 数据源：RetrofitHook 从接口响应动态提取——
 *  - 账号列表：`/accounts/android/current` 的 `subUserInfos`（全部子账号 id）；
 *  - 单个账号资料：`/leo-profile/android/user-infos`（nickname+avatarUrl）等任意含用户字段的响应；
 *  - Cookie：Set-Cookie 头 + WebView CookieManager（https://xyks.yuanfudao.com）。
 *
 * 行为：
 *  - **卡片数量随账号数自适应**（每账号一张卡，缺资料显示「昵称待采集」占位）；
 *  - 每 5 秒自动刷新；点击卡片/刷新图标立即刷新（重读 prefs + 重新下载头像）；
 *  - 底部 Cookie 行点击复制。
 */
@Composable
fun UserInfoCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // 刷新触发器：点击卡片或每 5s 自动 +1 → 触发重组 → 重读所有数据源。
    var refreshTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            refreshTick++
        }
    }

    val accounts = UserInfoStore.accounts
    val storedCookie = UserInfoStore.cookie
    val webCookie = runCatching {
        CookieManager.getInstance().getCookie("https://xyks.yuanfudao.com")
    }.getOrDefault("") ?: ""
    val fullCookie = listOf(storedCookie, webCookie).filter { it.isNotBlank() }.joinToString("; ")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (accounts.isEmpty()) {
            BaseItemContainer(Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            refreshTick++
                            Toast.makeText(context, "已刷新用户信息", Toast.LENGTH_SHORT).show()
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "未采集到用户信息（点击刷新）",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        } else {
            accounts.forEach { account ->
                AccountRow(
                    account = account,
                    refreshTick = refreshTick,
                    onRefresh = {
                        refreshTick++
                        Toast.makeText(context, "已刷新用户信息", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        // Cookie 行（全局一条，点击复制）
        BaseItemContainer(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Cookie", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (fullCookie.isNotBlank())
                            fullCookie.take(70) + "…"
                        else
                            "暂无（打开小猿口算后自动采集）",
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
                    modifier = Modifier
                        .size(22.dp)
                        .clickable {
                            if (fullCookie.isNotBlank()) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("cookie", fullCookie))
                                Toast.makeText(context, "Cookie 已复制（${fullCookie.length} 字符）", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "暂无 Cookie 可复制（请先登录小猿口算）", Toast.LENGTH_SHORT).show()
                            }
                        },
                )
            }
        }
    }
}

/** 单个账号卡片：头像 + 昵称/ID；点击整卡或刷新图标立即刷新全部。 */
@Composable
private fun AccountRow(
    account: UserInfoStore.Account,
    refreshTick: Int,
    onRefresh: () -> Unit,
) {
    var avatar by remember(account.uid) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(account.avatar, refreshTick) {
        if (account.avatar.isNotBlank()) {
            avatar = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = (URL(account.avatar).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4000
                        readTimeout = 4000
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)")
                    }
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    BaseItemContainer(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRefresh)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = avatar
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "头像",
                        modifier = Modifier.size(42.dp).clip(CircleShape),
                    )
                } else {
                    Text(
                        text = account.name.firstOrNull()?.toString() ?: "?",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (account.name.isNotBlank()) account.name else "昵称待采集（打开个人中心）",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "ID: ${account.uid}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = MaterialSymbols.Outlined.Refresh,
                contentDescription = "刷新",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onRefresh),
            )
        }
    }
}

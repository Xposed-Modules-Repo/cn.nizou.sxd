package cn.nizou.sxd.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back

/**
 * 共享列表 Scaffold（照抄 WeKit M3ListScaffold 的骨架，适配 material3 1.2.x）：
 * `LargeTopAppBar`（大标题，随滚动折叠）+ `LazyColumn` + 内部用 `SegmentedColumn` 分组。
 *
 * 说明：WeKit 用的是 material3 1.3+ 的 `LargeFlexibleTopAppBar`（Expressive API），
 * 本项目 pinned compose-bom 2024.06.00（material3 1.2.1）没有该组件，故退而用同族的
 * `LargeTopAppBar` + `exitUntilCollapsedScrollBehavior`，外观（大标题折叠 + 圆角分组卡片）
 * 与 WeKit 一致。各详情页与主菜单页共用本 Scaffold。
 *
 * @param title 大标题文案
 * @param navigationIcon 顶部导航图标槽（一般传 [M3BackButton]）
 * @param content LazyColumn 内容（SegmentedColumn 分组）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3ListScaffold(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    // 底部预留给悬浮底栏(FloatingBottomBar)的空间，避免列表滑到底部被其遮挡。
    bottomInset: Dp = 96.dp,
    content: LazyListScope.() -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = { navigationIcon?.invoke() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                // 仅底部追加预留给悬浮底栏的空间，避免列表滑到底部被其遮挡。
                bottom = innerPadding.calculateBottomPadding() + bottomInset,
            ),
            content = content
        )
    }
}

/**
 * 顶部返回按钮（MaterialSymbols Arrow_back 图标；视觉更重的带底色圆钮见
 * [ExpressiveBackButton]）。
 */
@Composable
fun M3BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = MaterialSymbols.Outlined.Arrow_back,
            contentDescription = "返回",
        )
    }
}
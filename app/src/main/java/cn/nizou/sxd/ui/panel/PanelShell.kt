package cn.nizou.sxd.ui.panel

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 注入面板的 rail 侧菜单外壳（照抄 WeKit `PanelShell` 的结构，去掉图标库依赖）。
 *
 * 布局 = 左侧 64dp rail 导航 + 右侧（48dp 标题栏 / 分隔线 / 内容区）。
 * rail 用 LazyColumn 承载全部目标，选中项带一个跟随滑动的 `secondaryContainer` 指示条
 * （`Animatable.translationY` 平滑追位，滚动中直接 snap，滚动结束 tween 追位）。
 *
 * 说明：
 *  - WeKit 用 MaterialSymbols 图标；本项目刻意不引入图标库（M3BackButton 用文本 chevron），
 *    故 [PanelRailItem.icon] 收敛为任意可组合内容（通常一个文本字符/emoji）。
 *  - 砍掉 WeKit 的 `PanelAction`/`PanelActionSearch`/各 overlay 弹层——设置面板不需要它们。
 */
data class PanelRailItem<T>(
    val destination: T,
    val label: String,
    val icon: @Composable () -> Unit,
)

/**
 * @param railItems 侧栏全部导航目标（永不隐藏，selected 必须是其中之一）。
 * @param selected 当前选中的目标。
 * @param title 标题栏左侧标题（通常传当前选中项的名字）。
 * @param onSelect 点击 rail 项切换目标。
 * @param onDismiss 关闭整个面板（标题栏关闭按钮/点外/系统返回根层触发）。
 * @param onBack 系统返回键行为，默认同 [onDismiss]。
 * @param content 右侧内容区。
 */
@Composable
fun <T> PanelShell(
    railItems: List<PanelRailItem<T>>,
    selected: T,
    title: String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit = onDismiss,
    content: @Composable () -> Unit,
) {
    BackHandler { onBack() }

    val railListState = rememberLazyListState()
    val selectedRailIndex = railItems.indexOfFirst { it.destination == selected }
    val selectedRailOffset by remember(railListState, selectedRailIndex) {
        derivedStateOf {
            railListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == selectedRailIndex }
                ?.offset
        }
    }
    val indicatorOffset = remember { Animatable(0f) }
    var indicatorPositioned by remember { mutableStateOf(false) }
    LaunchedEffect(selectedRailOffset, railListState.isScrollInProgress) {
        val target = selectedRailOffset?.toFloat() ?: return@LaunchedEffect
        if (!indicatorPositioned || railListState.isScrollInProgress) {
            indicatorOffset.snapTo(target)
            indicatorPositioned = true
        } else {
            indicatorOffset.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            // ---- 左侧 rail 侧菜单（64dp） ----
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)),
            ) {
                if (selectedRailOffset != null) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .graphicsLayer { translationY = indicatorOffset.value }
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    )
                }
                LazyColumn(
                    state = railListState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(railItems) { item ->
                        val isSelected = item.destination == selected
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clickable { onSelect(item.destination) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier.size(22.dp),
                                    contentAlignment = Alignment.Center,
                                ) { item.icon() }
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ---- 右侧：标题栏 + 内容区 ----
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onDismiss) { Text("✕") }
                }
                HorizontalDivider()
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}

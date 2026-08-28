package cn.nizou.sxd.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * 颜色选择行（照抄 WeKit `ColorPickerWidget`）：hex 字符串行 + 圆形色块（棋盘格底 +
 * 当前色 + 描边），点击整行弹出自绘 HSV 取色对话框。
 *
 * 回答「wekit 用 miuix 还是自绘」：**自绘**——wekit 的取色器在
 * `ui/content/WeColorField.kt` 里手绘（Hue/Saturation/Value/Alpha 四滑杆 +
 * 渐变轨道 + 棋盘格 + HEX 输入框），非 miuix 组件。本移植保留全部自绘逻辑，
 * 仅把 `showComposeDialog`/`AlertDialogContent`/`DefaultColumn`/`stringResource`
 * 换成项目内联 `AlertDialog` + `Column` + 中文字面量。
 *
 * @param title 主标题
 * @param value 当前色值字符串（`#RRGGBB` / `#AARRGGBB`，`toColorInt` 可解析即可）；
 *              确认后回调写入大写 `#AARRGGBB`
 * @param onValueChange 取色确认回调
 * @param modifier 布局修饰符
 * @param icon 可选前置图标
 * @param enabled 是否可交互
 */
@Composable
fun ColorPickerWidget(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val color = runCatching { value.toColorInt() }.getOrNull()
    var showDialog by remember { mutableStateOf(false) }

    BaseWidget(
        modifier = modifier,
        icon = icon,
        title = title,
        description = value,
        enabled = enabled,
        onClick = { if (enabled) showDialog = true },
        trailingContent = {
            Box(
                Modifier
                    .padding(4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .checkerboard(4.dp)
                    .background(color?.let(::ComposeColor) ?: ComposeColor.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .semantics { contentDescription = "取色" }
            )
        },
    )

    if (showDialog) {
        ColorPickerDialog(
            initial = color ?: AndroidColor.BLACK,
            onDismiss = { showDialog = false },
            onConfirm = { picked ->
                onValueChange(formatArgbHex(picked))
                showDialog = false
            },
        )
    }
}

/**
 * HSV 取色对话框（照抄 WeKit `WeColorPickerDialog`，改用 material3 [AlertDialog]）。
 * 工作在 HSV 而非打包 ARGB：把明度/饱和度拖到 0 不会丢失色相。
 */
@Composable
private fun ColorPickerDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initialHsv = remember(initial) { FloatArray(3).also { AndroidColor.colorToHSV(initial, it) } }

    var hue by remember { mutableFloatStateOf(initialHsv[0] / 360f) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember { mutableFloatStateOf(AndroidColor.alpha(initial) / 255f) }
    var hexText by remember { mutableStateOf(formatArgbHex(initial)) }

    val current = argbOf(hue, saturation, brightness, alpha)

    /** 应用一次滑杆编辑并刷新 HEX 输入框。 */
    fun onSliderChange(apply: () -> Unit) {
        apply()
        hexText = formatArgbHex(argbOf(hue, saturation, brightness, alpha))
    }

    val hexError = runCatching { hexText.toColorInt() }.isFailure

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("取色") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .checkerboard()
                        .background(ComposeColor(current))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                )

                ColorSlider(
                    label = "色相",
                    value = hue,
                    onValueChange = { next -> onSliderChange { hue = next } },
                    trackBrush = Brush.horizontalGradient(
                        List(HUE_STOPS + 1) {
                            ComposeColor(argbOf(it / HUE_STOPS.toFloat(), 1f, 1f, 1f))
                        }
                    ),
                )
                ColorSlider(
                    label = "饱和度",
                    value = saturation,
                    onValueChange = { next -> onSliderChange { saturation = next } },
                    trackBrush = Brush.horizontalGradient(
                        listOf(
                            ComposeColor(argbOf(hue, 0f, brightness, 1f)),
                            ComposeColor(argbOf(hue, 1f, brightness, 1f)),
                        )
                    ),
                )
                ColorSlider(
                    label = "明度",
                    value = brightness,
                    onValueChange = { next -> onSliderChange { brightness = next } },
                    trackBrush = Brush.horizontalGradient(
                        listOf(
                            ComposeColor(argbOf(hue, saturation, 0f, 1f)),
                            ComposeColor(argbOf(hue, saturation, 1f, 1f)),
                        )
                    ),
                )
                ColorSlider(
                    label = "透明度",
                    value = alpha,
                    onValueChange = { next -> onSliderChange { alpha = next } },
                    trackBrush = Brush.horizontalGradient(
                        listOf(
                            ComposeColor(argbOf(hue, saturation, brightness, 0f)),
                            ComposeColor(argbOf(hue, saturation, brightness, 1f)),
                        )
                    ),
                    checkerboard = true,
                )

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { next ->
                        hexText = next
                        runCatching { next.toColorInt() }.getOrNull()?.let { parsed ->
                            val hsv = FloatArray(3).also { AndroidColor.colorToHSV(parsed, it) }
                            hue = hsv[0] / 360f
                            saturation = hsv[1]
                            brightness = hsv[2]
                            alpha = AndroidColor.alpha(parsed) / 255f
                        }
                    },
                    label = { Text("色值 (HEX)") },
                    singleLine = true,
                    isError = hexError,
                    supportingText = if (hexError) {
                        { Text("无效的颜色值") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            Button(onClick = { onConfirm(current) }) {
                Text("确定")
            }
        },
    )
}

/**
 * 0..1 滑杆（照抄 WeKit `ColorSlider`）。手绘而非 M3 Slider 覆写轨道：拇指要压在任意
 * 渐变色上，且按到轨道任意位置都应直接跳值（不等拖拽 slop）。
 */
@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    trackBrush: Brush,
    modifier: Modifier = Modifier,
    checkerboard: Boolean = false,
) {
    var widthPx by remember { mutableStateOf(0) }
    val outline = MaterialTheme.colorScheme.outline
    val pill = RoundedCornerShape(percent = 50)

    Column(modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(SLIDER_HEIGHT)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(widthPx) {
                    val thumbRadius = THUMB_RADIUS.toPx()
                    val span = widthPx - thumbRadius * 2

                    fun fractionAt(x: Float) =
                        if (span <= 0f) 0f else ((x - thumbRadius) / span).coerceIn(0f, 1f)

                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            onValueChange(fractionAt(down.position.x))
                            drag(down.id) { change ->
                                onValueChange(fractionAt(change.position.x))
                                change.consume()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TRACK_HEIGHT)
                    .clip(pill)
                    .then(if (checkerboard) Modifier.checkerboard(4.dp) else Modifier)
                    .background(trackBrush)
                    .border(1.dp, outline, pill)
            )
            Canvas(Modifier.matchParentSize()) {
                val thumbRadius = THUMB_RADIUS.toPx()
                val centerX = thumbRadius + value.coerceIn(0f, 1f) * (size.width - thumbRadius * 2)
                val center = Offset(centerX, size.height / 2f)
                drawCircle(ComposeColor.White, thumbRadius, center)
                drawCircle(outline, thumbRadius, center, style = Stroke(1.dp.toPx()))
            }
        }
    }
}

/** 在自身内容后面画一个指示透明度的棋盘格（照抄 WeKit）。 */
fun Modifier.checkerboard(cell: Dp = CHECKER_CELL): Modifier =
    drawBehind { drawCheckerboard(cell.toPx(), size) }

private fun DrawScope.drawCheckerboard(cellPx: Float, area: Size) {
    if (cellPx <= 0f) return
    drawRect(CHECKER_LIGHT, size = area)
    val columns = ceil(area.width / cellPx).toInt()
    val rows = ceil(area.height / cellPx).toInt()
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if ((row + column) % 2 == 0) continue
            val left = column * cellPx
            val top = row * cellPx
            drawRect(
                color = CHECKER_DARK,
                topLeft = Offset(left, top),
                size = Size(min(cellPx, area.width - left), min(cellPx, area.height - top)),
            )
        }
    }
}

/** 归一化 HSVA 分量打包为 ARGB int（照抄 WeKit）。 */
private fun argbOf(hue: Float, saturation: Float, brightness: Float, alpha: Float): Int =
    AndroidColor.HSVToColor(
        (alpha.coerceIn(0f, 1f) * 255f).roundToInt(),
        floatArrayOf(
            hue.coerceIn(0f, 1f) * 360f,
            saturation.coerceIn(0f, 1f),
            brightness.coerceIn(0f, 1f),
        ),
    )

/** 模块统一色值字符串（照抄 WeKit）：大写 `#AARRGGBB`。 */
fun formatArgbHex(argb: Int): String = "#%08X".format(argb)

private const val HUE_STOPS = 12
private val SLIDER_HEIGHT = 32.dp
private val TRACK_HEIGHT = 16.dp
private val THUMB_RADIUS = 10.dp
private val CHECKER_CELL = 6.dp
private val CHECKER_LIGHT = ComposeColor(0xFFF2F2F2)
private val CHECKER_DARK = ComposeColor(0xFFC8C8C8)

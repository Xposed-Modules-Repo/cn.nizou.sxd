// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2025-2026 InstallerX Revived contributors
//
// Portions of this file are derived from weishu/KernelSU
// (https://github.com/tiann/KernelSU)
// Copyright (C) KernelSU contributors
// Licensed under GPL-3.0
// 移植自 WeKit (dev.ujhhgtg.wekit.ui.content.animation.DampedDragAnimation)
package cn.nizou.sxd.ui.content.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import cn.nizou.sxd.ui.content.inspectDragGestures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val canDrag: (Offset) -> Boolean = { true },
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDragCancelled: DampedDragAnimation.() -> Unit = onDragStopped,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    val onTap: DampedDragAnimation.() -> Unit = {},
    val onLongPress: DampedDragAnimation.() -> Boolean = { false },
) {

    // 回位弹簧：阻尼 <1（欠阻尼）产生轻微过冲 = 「q弹」手感（wekit 原版 1f 临界阻尼不过冲）。
    // 拖动跟手走同步 snapToValue，不受此 spec 影响。
    private val valueAnimationSpec =
        spring(dampingRatio = 0.7f, stiffness = 500f, visibilityThreshold = visibilityThreshold)
    private val pressProgressAnimationSpec =
        spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec =
        spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec =
        spring(0.7f, 250f, 0.001f)

    // 指示器位置：普通 Compose 状态，拖动时**同步写**（零延迟跟手，无协程/无 spring
    // 追赶——wekit 原版每次 move 启动 animateTo 导致指示器永远落后手指，表现为不跟手/越拖越慢）；
    // 松手/点击才用 animate 协程做 spring 回位动画。
    private var valueState by mutableFloatStateOf(initialValue)
    private var targetValueState by mutableFloatStateOf(initialValue)

    // 拖动速度（pill 速度形变用）：同步在 snapToValue 里计算（位移/时间，轻平滑），
    // 不用原版 VelocityTracker（每帧 System.currentTimeMillis + launch animateTo 协程堆积）。
    private var lastSnapValue = initialValue
    private var lastSnapTimeMs = 0L
    private var velocityState by mutableFloatStateOf(0f)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val scaleXAnimation =
        Animatable(initialScale, 0.001f)
    private val scaleYAnimation =
        Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()

    val value: Float get() = valueState
    val targetValue: Float get() = targetValueState
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    // 拖动速度（tab/ms），驱动 pill 速度形变（layerBlock）；同步计算无协程
    val velocity: Float get() = velocityState

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        val touchSlopSquared = viewConfiguration.touchSlop.let { it * it }
        val longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis
        var downPosition = Offset.Zero
        var movedBeyondTouchSlop = false
        var longPressTriggered = false
        var longPressConsumed = false
        var longPressJob: Job? = null

        inspectDragGestures(
            consumeOnDrag = true, // 防下层 HorizontalPager scrollable 抢手势（页面不乱动）
            onDragStart = { down ->
                downPosition = down.position
                movedBeyondTouchSlop = false
                longPressTriggered = false
                longPressConsumed = false
                onDragStarted(down.position)
                press()
                longPressJob = animationScope.launch {
                    delay(longPressTimeoutMillis)
                    longPressTriggered = true
                    if (onLongPress()) {
                        longPressConsumed = true
                        onDragCancelled()
                        release()
                    }
                }
            },
            onDragEnd = {
                longPressJob?.cancel()
                longPressJob = null
                if (!longPressConsumed) {
                    onDragStopped()
                    release()
                    if (!longPressTriggered && !movedBeyondTouchSlop) {
                        onTap()
                    }
                }
            },
            onDragCancel = {
                longPressJob?.cancel()
                longPressJob = null
                if (!longPressConsumed) {
                    onDragCancelled()
                    release()
                }
            }
        ) { change, dragAmount ->
            if (longPressConsumed) return@inspectDragGestures

            val position = change.position
            val previousPosition = change.previousPosition

            if (!movedBeyondTouchSlop) {
                val displacement = position - downPosition
                movedBeyondTouchSlop = displacement.x * displacement.x +
                    displacement.y * displacement.y > touchSlopSquared
                if (movedBeyondTouchSlop) {
                    longPressJob?.cancel()
                    longPressJob = null
                }
            }

            val isInside = canDrag(position)
            val wasInside = canDrag(previousPosition)

            if (isInside && wasInside) {
                onDrag(size, dragAmount)
            }
        }
    }

    fun press() {
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueState }
                    .filter { abs(it - targetValueState) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    /** 拖动跟手：**同步**写值（普通状态直接赋值，零延迟无协程），并同步计算拖动速度。 */
    fun snapToValue(value: Float) {
        val target = value.coerceIn(valueRange)
        val now = System.currentTimeMillis()
        val dt = (now - lastSnapTimeMs).coerceIn(1L, 100L)
        if (lastSnapTimeMs > 0L) {
            // 位移/时间 = tab/ms，轻平滑防抖
            val raw = (target - lastSnapValue) / dt
            velocityState = velocityState * 0.6f + raw * 0.4f
        }
        lastSnapValue = target
        lastSnapTimeMs = now
        targetValueState = target
        valueState = target
    }

    /** 松手/点击回位：spring 动画到目标（含按压缩放效果），速度随动画自然衰减。 */
    fun animateToValue(value: Float) {
        val target = value.coerceIn(valueRange)
        targetValueState = target
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                animate(
                    initialValue = valueState,
                    targetValue = target,
                    // ★ 拖动末速度作为初速度（velocityState 单位 tab/ms → *1000 转 tab/s）：
                    // wekit 用 Animatable.animateTo 会继承当前速度，弹簧带速度冲过头再回弹 = q弹；
                    // 之前未传 initialVelocity（默认 0，静止起步）所以弹不起来。
                    initialVelocity = velocityState * 1000f,
                    animationSpec = valueAnimationSpec,
                ) { v, _ ->
                    valueState = v
                    velocityState *= 0.9f // 回位动画中速度指数衰减（形变平滑收尾）
                }
                velocityState = 0f
                release()
            }
        }
    }

    /** 兼容保留（拖动跟手一律走 [snapToValue]）。 */
    fun updateValue(value: Float) = animateToValue(value)
}

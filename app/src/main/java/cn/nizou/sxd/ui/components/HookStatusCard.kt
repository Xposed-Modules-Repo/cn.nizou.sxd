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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.AutoOralApplication
import cn.nizou.sxd.HOST_PACKAGE_NAME
import io.github.libxposed.service.XposedService

/**
 * WeKit-equivalent activation detection.
 * Module process: XposedService scope is authoritative.
 * Injected host settings: the live XposedModule reflection supplies the actual LSPosed/npatch identity.
 */
@Composable
fun HookStatusCard(modifier: Modifier = Modifier) {
    var service by remember { mutableStateOf<XposedService?>(AutoOralApplication.service) }
    DisposableEffect(Unit) {
        val listener: (XposedService?) -> Unit = { current -> service = current }
        AutoOralApplication.addServiceListener(listener)
        onDispose { AutoOralApplication.removeServiceListener(listener) }
    }

    val serviceState = service?.let { current ->
        runCatching {
            val active = current.scope.contains(HOST_PACKAGE_NAME)
            ActivationState(
                active = active,
                details = current.frameworkName + " " + current.frameworkVersion +
                    " · code " + current.frameworkVersionCode + " · API " + current.apiVersion,
            )
        }.getOrNull()
    }
    val injectedState = remember { readInjectedFrameworkState() }
    val state = serviceState ?: injectedState ?: ActivationState(false, "未检测到 LSPosed / npatch 服务")

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = if (state.active) Color(0xFF2E7D32) else Color(0xFFC62828),
        contentColor = Color.White,
        shape = RoundedCornerShape(CornerRadius),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (state.active) "已激活" else "未激活", style = MaterialTheme.typography.titleMedium)
                Text(state.details, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .9f))
            }
            Box(Modifier.padding(start = 12.dp).size(14.dp).clip(CircleShape).background(Color.White))
        }
    }
}

private data class ActivationState(val active: Boolean, val details: String)

/** Reflection preserves standalone launcher safety: libxposed API is compileOnly. */
private fun readInjectedFrameworkState(): ActivationState? = runCatching {
    val companion = Class.forName("cn.nizou.sxd.XposedInit" + '$' + "Companion")
    val self = companion.getField("self").get(null) ?: return null
    val type = self.javaClass
    val name = type.methods.first { it.name == "getFrameworkName" && it.parameterCount == 0 }.invoke(self).toString()
    val version = type.methods.first { it.name == "getFrameworkVersion" && it.parameterCount == 0 }.invoke(self).toString()
    val code = type.methods.first { it.name == "getFrameworkVersionCode" && it.parameterCount == 0 }.invoke(self)
    val api = type.methods.first { it.name == "getApiVersion" && it.parameterCount == 0 }.invoke(self)
    ActivationState(true, "$name $version · code $code · API $api")
}.getOrNull()

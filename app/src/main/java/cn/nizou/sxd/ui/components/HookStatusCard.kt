package cn.nizou.sxd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.BuildConfig
import cn.nizou.sxd.util.readInjectedModuleSelf
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Check_circle

/**
 * WeKit injected SettingsActivity home-card counterpart.
 *
 * The card deliberately uses the host process' live XposedInit instance, exactly
 * as an injected menu should: the menu exists only after the host injection has
 * completed. It is not the standalone module-process XposedService detector.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HookStatusCard(modifier: Modifier = Modifier) {
    val environment = remember { readInjectedLoadingEnvironment() }
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val hookBridgeName = environment?.hookBridgeName ?: "未提供"

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Check_circle,
                    contentDescription = "模块已激活",
                )
            },
            supportingContent = {
                Text(
                    text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            trailingContent = {
                StatusTag(
                    label = hookBridgeName,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = contentColor,
                leadingContentColor = contentColor,
                trailingContentColor = contentColor,
                supportingContentColor = contentColor.copy(alpha = 0.7f),
            ),
            headlineContent = {
                Text(
                    text = "模块已激活",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusTag(label: String, backgroundColor: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .background(color = backgroundColor, shape = RoundedCornerShape(4.dp)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmallEmphasized,
            color = contentColor,
        )
    }
}

data class InjectedLoadingEnvironment(
    val loaderName: String,
    val hookBridgeName: String,
)

/** Mirrors WeKit StartupInfo.loaderService + hookBridge for the libxposed entry. */
fun readInjectedLoadingEnvironment(): InjectedLoadingEnvironment? = runCatching {
    val self = readInjectedModuleSelf() ?: return null
    val type = self.javaClass
    val frameworkName = type.methods.first { it.name == "getFrameworkName" && it.parameterCount == 0 }.invoke(self)
    val apiVersion = type.methods.first { it.name == "getApiVersion" && it.parameterCount == 0 }.invoke(self)
    InjectedLoadingEnvironment(
        loaderName = "libxposed API $apiVersion",
        // WeKit LxpHookImpl exposes loaderName as hookBridgeName too.
        hookBridgeName = "libxposed API $apiVersion",
    )
}.getOrNull()


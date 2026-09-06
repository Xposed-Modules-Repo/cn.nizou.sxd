package cn.nizou.sxd.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.nizou.sxd.ui.components.DropDownMenuWidget
import cn.nizou.sxd.ui.components.DropdownOption
import cn.nizou.sxd.ui.components.M3BackButton
import cn.nizou.sxd.ui.components.M3ListScaffold
import cn.nizou.sxd.ui.components.SegmentedColumn
import cn.nizou.sxd.util.ProvinceRegionPrefs

/** Miuix/M3 entry for the host paper provinceId override. */
@Composable
fun RegionScreen(onBack: () -> Unit) {
    var province by remember { mutableStateOf(ProvinceRegionPrefs.selectedName) }
    M3ListScaffold(
        title = "改地区",
        navigationIcon = { M3BackButton(onClick = onBack) },
    ) {
        item {
            SegmentedColumn(title = "试卷地区") {
                DropDownMenuWidget(
                    title = "省级地区",
                    description = "当前：" + province + "。请求 /leo-exam/android/paper/list 时改写 provinceId；全国不改写。",
                    value = province,
                    options = ProvinceRegionPrefs.provinces.map { DropdownOption(it.name, it.name) },
                    onValueChange = { selected ->
                        province = selected
                        ProvinceRegionPrefs.select(selected)
                    },
                )
            }
        }
        item { Box(Modifier.padding(24.dp)) {} }
    }
}

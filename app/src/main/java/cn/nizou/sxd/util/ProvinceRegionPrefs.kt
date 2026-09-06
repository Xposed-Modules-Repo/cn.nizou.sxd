package cn.nizou.sxd.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Province override for the host paper recommendation flow.
 * The host exposes province filters through /leo-exam/android/paper/selectors and uses
 * the chosen FilterItem.id as provinceId in /leo-exam/android/paper/list.
 */
object ProvinceRegionPrefs {
    const val KEY_SELECTED_PROVINCE = "province_region_selected"
    private const val KEY_DISCOVERED_IDS = "province_region_discovered_ids"
    const val NATIONAL = "全国（不改写）"

    data class Province(val name: String, val fallbackId: Int?)

    /** All provincial-level regions; fallback IDs use GB/T 2260 province codes. */
    val provinces = listOf(
        Province(NATIONAL, null),
        Province("北京市", 110000), Province("天津市", 120000), Province("河北省", 130000),
        Province("山西省", 140000), Province("内蒙古自治区", 150000), Province("辽宁省", 210000),
        Province("吉林省", 220000), Province("黑龙江省", 230000), Province("上海市", 310000),
        Province("江苏省", 320000), Province("浙江省", 330000), Province("安徽省", 340000),
        Province("福建省", 350000), Province("江西省", 360000), Province("山东省", 370000),
        Province("河南省", 410000), Province("湖北省", 420000), Province("湖南省", 430000),
        Province("广东省", 440000), Province("广西壮族自治区", 450000), Province("海南省", 460000),
        Province("重庆市", 500000), Province("四川省", 510000), Province("贵州省", 520000),
        Province("云南省", 530000), Province("西藏自治区", 540000), Province("陕西省", 610000),
        Province("甘肃省", 620000), Province("青海省", 630000), Province("宁夏回族自治区", 640000),
        Province("新疆维吾尔自治区", 650000), Province("台湾省", 710000), Province("香港特别行政区", 810000),
        Province("澳门特别行政区", 820000),
    )

    val selectedName: String
        get() = SettingsPrefs.readString(KEY_SELECTED_PROVINCE, NATIONAL)
            .takeIf { selected -> provinces.any { it.name == selected } } ?: NATIONAL
    val enabled: Boolean get() = selectedName != NATIONAL

    fun select(name: String) {
        if (provinces.any { it.name == name }) SettingsPrefs.writeString(KEY_SELECTED_PROVINCE, name)
    }

    /** Uses the live FilterItem ID captured from the host first; GB/T code is an offline fallback. */
    fun selectedProvinceId(): Int? {
        val province = provinces.firstOrNull { it.name == selectedName } ?: return null
        if (province.fallbackId == null) return null
        return discoveredIds()[province.name]?.takeIf { it > 0 } ?: province.fallbackId
    }

    /** Captures the host's canonical FilterItem IDs from the selector response. */
    fun captureSelectorMapping(payload: String?) {
        if (payload.isNullOrBlank()) return
        val captured = linkedMapOf<String, Int>()
        fun visit(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val name = node.optString("name")
                    val id = when {
                        node.has("provinceId") -> node.optInt("provinceId", 0)
                        else -> node.optInt("id", 0)
                    }
                    if (id > 0 && provinces.any { it.name == name }) captured[name] = id
                    val keys = node.keys()
                    while (keys.hasNext()) visit(node.opt(keys.next()))
                }
                is JSONArray -> for (index in 0 until node.length()) visit(node.opt(index))
            }
        }
        runCatching { visit(JSONObject(payload)) }.onFailure {
            runCatching { visit(JSONArray(payload)) }
        }
        if (captured.isEmpty()) return
        val merged = JSONObject(SettingsPrefs.readString(KEY_DISCOVERED_IDS, "{}"))
        captured.forEach { (name, id) -> merged.put(name, id) }
        SettingsPrefs.writeString(KEY_DISCOVERED_IDS, merged.toString())
        logI("province selector mapped: " + captured.entries.joinToString { it.key + "=" + it.value })
    }

    private fun discoveredIds(): Map<String, Int> = runCatching {
        val json = JSONObject(SettingsPrefs.readString(KEY_DISCOVERED_IDS, "{}"))
        buildMap {
            provinces.forEach { province ->
                json.optInt(province.name, 0).takeIf { it > 0 }?.let { put(province.name, it) }
            }
        }
    }.getOrDefault(emptyMap())
}

package cn.nizou.sxd.hook

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import cn.nizou.sxd.util.Risk
import cn.nizou.sxd.util.logI
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.HookHandle

/**
 * 屏蔽「外挂检测 / 大朋友（家长监督）检测」弹窗。
 *
 * 依据逆向（记忆 02/07 + reverser_ws smali）：
 * - 家长监督视图：`com.fenbi.android.leo.imgsearch.sdk.check.helper.SupervisionHelper`
 *   （j(Boolean)/k() 控制 supervisionView 显隐，LiveEventBus 事件
 *   "live_event_close_parent_supervision" + 配置 de.a.h() 判定家长模式）。
 * - 外挂/风控/大朋友检测弹窗文本未在宿主 smali 命中（字符串在资源/服务端下发），
 *   多为网络返回后弹 Dialog / H5 alert。故此处用「Dialog 内容关键字过滤」的通用兜底：
 *   命中风险关键字（外挂/检测/风控/异常/作弊/家长/大朋友/监管/风险）即 dismiss。
 *
 * 全部仅当 prefs 开关开启时生效，且内部 runCatching，失败安全放行不干扰正常流程。
 * 具体触发类需真机开抓包（RetrofitHook+PacketTool）进一步确认。
 */
class RiskDetectHook(
    self: XposedInterface,
    classLoader: ClassLoader
) : BaseHook(self, classLoader) {

    override val name: String
        get() = "RiskDetectHook"

    // 2026-08-29：收紧关键词——只匹配**明确针对第三方外挂/作弊**的提示，避免误杀
    // 「退出登录/切换账号」等含"检测"/"风险"/"异常"通用词的确认弹窗（真机反馈：用模块后
    // 无法退出/切换账号，即 RiskDetectHook 把账号确认框/popup 给 dismiss 了）。
    private val riskKeywords = arrayOf(
        "外挂", "作弊", "开挂", "第三方", "非法", "模拟器", "脚本", "辅助工具",
        "封禁", "封号", "封停", "违规操作", "异常操作", "请勿使用", "禁止使用", "检测到作弊"
    )

    private var supervisionHandle: HookHandle? = null

    override fun startHook() {
        hookSupervisionHelper()
        hookRiskDialog()
        hookRiskToast()
    }

    /** 家长监督视图强制隐藏：j(show) 短路返回、k() 短路返回。 */
    private fun hookSupervisionHelper() {
        runCatching {
            val supClass = findClass("com.fenbi.android.leo.imgsearch.sdk.check.helper.SupervisionHelper")
            supervisionHandle = supClass.findMethod("j", Boolean::class.javaPrimitiveType!!)
                .intercept("supervision_hide") { chain ->
                    if (Risk.blockSupervision) null else chain.proceed()
                }
        }.onFailure {
            logI("supervision helper hook skipped: ${it.message}")
        }
    }

    /** 通用风险弹窗屏蔽：Dialog.show() 后按内容关键字 dismiss（消除可见闪烁，稍后置空）。 */
    private fun hookRiskDialog() {
        runCatching {
            val dialogClass = findClass("android.app.Dialog")
            dialogClass.findMethod("show").intercept("risk_dialog_show") { chain ->
                val r = chain.proceed()
                if (Risk.blockRisk) {
                    val dialog = chain.thisObject
                    if (dialog is Dialog && containsRisk(dialog)) {
                        runCatching { dialog.dismiss() }
                    }
                }
                r
            }
        }.onFailure {
            logI("risk dialog hook skipped: ${it.message}")
        }
    }

    /** 外挂行为检测 Toast 屏蔽：命中风险关键字即 cancel。 */
    private fun hookRiskToast() {
        runCatching {
            val toastClass = findClass("android.widget.Toast")
            toastClass.findMethod("show").intercept("risk_toast_show") { chain ->
                val r = chain.proceed()
                if (Risk.blockRisk) {
                    val toast = chain.thisObject
                    if (toast is Toast) {
                        val tv = toast.view?.let { v ->
                            if (v is TextView) v.text else collectText(v)
                        }
                        val text = tv?.toString().orEmpty()
                        if (riskKeywords.any { text.contains(it) }) {
                            runCatching { toast.cancel() }
                            logI("risk toast suppressed: ${text.take(60)}")
                        }
                    }
                }
                r
            }
        }.onFailure {
            logI("risk toast hook skipped: ${it.message}")
        }
    }

    private fun containsRisk(dialog: Dialog): Boolean {
        val decor = dialog.window?.decorView ?: return false
        val text = collectText(decor)
        if (text.isBlank()) return false
        val hit = riskKeywords.any { text.contains(it) }
        if (hit) logI("risk dialog suppressed: ${text.take(80)}")
        return hit
    }

    private fun collectText(root: View): String {
        val sb = StringBuilder()
        fun walk(v: View?) {
            if (v == null) return
            if (v is TextView) sb.append(v.text).append(' ')
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return sb.toString()
    }
}
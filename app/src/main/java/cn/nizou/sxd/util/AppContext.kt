package cn.nizou.sxd.util

import android.app.Application

/**
 * 现代 libxposed 不注入 AndroidAppHelper，因此在宿主进程用反射取当前 Application。
 * 仅用于获取宿主 Context 以读写宿主 SharedPreferences（模块设置注入在宿主进程）。
 */
fun currentApplication(): Application {
    val at = Class.forName("android.app.ActivityThread")
    return at.getMethod("currentApplication").invoke(null) as Application
}

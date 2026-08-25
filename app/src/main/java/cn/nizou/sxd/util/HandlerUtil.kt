package cn.nizou.sxd.util

import android.os.Handler
import android.os.Looper

val mainHandler by lazy {
    Handler(Looper.getMainLooper())
}
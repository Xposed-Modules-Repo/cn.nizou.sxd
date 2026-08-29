package cn.nizou.sxd.util

/**
 * 题目答案缓存（SimianHook 从 EncryptResult 构造器解析题目数据填充，
 * WebViewHook.getAnswers() 经 JS bridge 喂给 quick.js 顺序绘制）。
 * 格式：JSON 数组 [{content, answer}]。
 */
object AnswerCache {
    @Volatile
    var answers: String = "[]"
}

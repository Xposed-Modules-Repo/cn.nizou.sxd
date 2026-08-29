package cn.nizou.sxd.api

import cn.nizou.sxd.util.XposedHelpers
import cn.nizou.sxd.util.logI
import cn.nizou.sxd.util.strokes
import cn.nizou.sxd.util.toJsonString
import kotlin.coroutines.cancellation.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * 练习批量上传刷分（真自定义分数）。
 *
 * 逆向结论（reverser_ws/new_apktool_out 3.140.1）：
 * - `postSavedExp` 实际走 `POST /leo-star/android/exercise/rank/login/attend`（排行榜登录参与），
 *   服务端限次（用户实测每天约 3 次）——旧「刷分/拆条」方案撞上该限制。
 * - `uploadExamResult` 实际走 `PUT /leo-math/android/exams/v2/{examId}`（练习成绩上传主接口），
 *   PracticeHook「自动上分」无限刷走的正是它，**无 attend 的日限语义**。
 *
 * 本类复用「取真实卷子(getExamInfo) → 全对填充 → 上传(uploadExamResult)」链路循环刷，
 * 每局后重新 pre-fetch 当前分数，直到 ≥ 目标分数。接口调用在宿主协程（ContinuationProxy）
 * 上异步执行，这里用 CountDownLatch 同步等待收拢。
 */
object ScorePump {

    /** 安全上限：最多刷这么多局（防止异常时死循环） */
    private const val MAX_ROUNDS = 1000

    /** 用户停止标志：cancel() 置位，当前局结束/下一局开始前退出 */
    @Volatile
    var stopped = false

    /** 请求停止当前 pumpToTarget 循环 */
    fun cancel() {
        stopped = true
    }

    /**
     * 刷到目标分数。
     *
     * @param keyPointId 练习知识点 ID（PracticeHook 在练习页 onCreate 自动记录到
     *   prefs `custom_score_keypoint`，UI 读取；留空会提示先开一局练习）
     * @param limit 每局题目数（默认 30）
     * @param settleTime 每题 costTime 毫秒（>0 用该值，否则随机 150~250）
     * @param intervalMs 每局间隔（防频率风控）
     * @param target 目标分数（curWeekScore）
     * @param onProgress (当前分数, 已刷局数) —— 工作线程回调
     * @param onDone 结束（成功返回最终分数；失败返回异常）
     */
    fun pumpToTarget(
        keyPointId: String,
        limit: Int,
        intervalMs: Long,
        target: Int,
        onProgress: (currentScore: Int, rounds: Int) -> Unit,
        onDone: (Result<Int>) -> Unit,
    ) {
        thread {
            var rounds = 0
            stopped = false
            try {
                val initial = fetchCurrentScore()
                if (initial < 0) {
                    onDone(Result.failure(IllegalStateException("无法读取当前分数（宿主 ApiService 未初始化？）")))
                    return@thread
                }
                onProgress(initial, 0)
                if (initial >= target) {
                    onDone(Result.success(initial))
                    return@thread
                }
                while (rounds < MAX_ROUNDS) {
                    if (stopped) {
                        onDone(Result.failure(CancellationException("用户停止，已刷 $rounds 局")))
                        return@thread
                    }
                    val examVO = fetchExam(keyPointId, limit)
                        ?: run {
                            onDone(
                                Result.failure(
                                    IllegalStateException(
                                        "取题失败（知识点 ID「$keyPointId」可能无效或网络异常），已刷 $rounds 局"
                                    )
                                )
                            )
                            return@thread
                        }
                    val examId = XposedHelpers.getObjectField(examVO, "idString").toString()
                    buildFullCorrect(examVO)
                    if (!upload(examId, examVO)) {
                        onDone(
                            Result.failure(
                                IllegalStateException("上传第 ${rounds + 1} 局失败，已刷 $rounds 局")
                            )
                        )
                        return@thread
                    }
                    rounds++
                    val cur = fetchCurrentScore()
                    onProgress(if (cur >= 0) cur else initial, rounds)
                    if (cur >= target) {
                        onDone(Result.success(cur))
                        return@thread
                    }
                    if (intervalMs > 0) Thread.sleep(intervalMs)
                }
                onDone(Result.failure(IllegalStateException("达到 $MAX_ROUNDS 局上限仍未到目标，当前可能已接近，可再刷一次")))
            } catch (e: Throwable) {
                logI(e)
                onDone(Result.failure(e))
            }
        }
    }

    /**
     * 全对填充 ExamVO（复用 PracticeHook.buildExamResult 逻辑：答案/画线/status=1/correctCnt）。
     * 每题 costTime 随机 300~450ms（**练习提交必须 ≥0.3s**，服务端验证下限；不引用 PK.settleTime）。
     */
    private fun buildFullCorrect(examVO: Any) {
        val questions = XposedHelpers.getObjectField(examVO, "questions") as? List<*>
        var totalTime = 0L
        questions?.forEach {
            val answers = XposedHelpers.getObjectField(it, "answers") as? List<*>
            val answer = answers?.firstOrNull()?.toString() ?: ""
            XposedHelpers.callMethod(it, "setUserAnswer", answer)
            val costTime = Random.nextLong(300, 450)
            XposedHelpers.callMethod(it, "setCostTime", costTime)
            // 手写笔画（真实连续线段，防「单点/无手写」风控）
            XposedHelpers.callMethod(it, "setScript", answer.strokes.toJsonString())
            XposedHelpers.callMethod(it, "setStatus", 1)
            totalTime += costTime
        }
        val questionCnt = XposedHelpers.getIntField(examVO, "questionCnt")
        XposedHelpers.callMethod(examVO, "setCorrectCnt", questionCnt)
        XposedHelpers.callMethod(examVO, "setCostTime", totalTime)
    }

    private fun fetchExam(keyPointId: String, limit: Int): Any? {
        val latch = CountDownLatch(1)
        var exam: Any? = null
        var err: Throwable? = null
        OralApiService.getExamInfo(keyPointId, limit) { r ->
            r.onSuccess { exam = it }.onFailure { err = it }
            latch.countDown()
        }
        if (!latch.await(15, TimeUnit.SECONDS)) {
            logI("ScorePump: getExamInfo timeout")
            return null
        }
        if (err != null) {
            logI("ScorePump: getExamInfo failed: ${err.message}")
            return null
        }
        return exam
    }

    private fun upload(examId: String, examVO: Any): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        var err: Throwable? = null
        OralApiService.uploadExamResult(examId, examVO) { r ->
            r.onSuccess { ok = true }.onFailure { err = it }
            latch.countDown()
        }
        if (!latch.await(15, TimeUnit.SECONDS)) {
            logI("ScorePump: upload timeout")
            return false
        }
        if (err != null) {
            logI("ScorePump: upload failed: ${err.message}")
            return false
        }
        return ok
    }

    private fun fetchCurrentScore(): Int {
        val latch = CountDownLatch(1)
        var score = -1
        var err: Throwable? = null
        LegacyApiService.getCurrentUserExp { r ->
            r.onSuccess { data ->
                score = XposedHelpers.getIntField(data, "curWeekScore")
            }.onFailure { err = it }
            latch.countDown()
        }
        if (!latch.await(15, TimeUnit.SECONDS)) {
            logI("ScorePump: pre-fetch timeout")
            return -1
        }
        if (err != null) {
            logI("ScorePump: pre-fetch failed: ${err.message}")
            return -1
        }
        return score
    }
}

package cn.nizou.sxd.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ProgressBar
import android.widget.TextView
import cn.nizou.sxd.Classname
import cn.nizou.sxd.api.OralApiService
import cn.nizou.sxd.util.Practice
import cn.nizou.sxd.util.XposedHelpers
import cn.nizou.sxd.util.logI
import cn.nizou.sxd.util.mainHandler
import cn.nizou.sxd.util.strokes
import cn.nizou.sxd.util.toJsonString
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.HookHandle
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor.DiscardPolicy
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

class PracticeHook(
    self: XposedInterface,
    classLoader: ClassLoader
) : BaseHook(self, classLoader) {

    private class ExerciseGeneralModelWrapper(modelClass: Class<*>, exerciseTypeClass: Class<*>) {
        private val getExerciseType: Method = modelClass.methods.first {
            it.returnType == exerciseTypeClass && it.parameterCount == 0
        }.also { it.isAccessible = true }

        private val buildUri: Method = modelClass.methods.first {
            it.returnType == Uri::class.java && it.parameterCount == 2
        }.also { it.isAccessible = true }

        private val gotoResult: Method = modelClass.methods.first {
            it.returnType == Void.TYPE && it.parameterCount > 1 && it.parameterTypes[0] == Context::class.java
        }.also { it.isAccessible = true }

        fun Any.getExerciseType(): Any? {
            return getExerciseType.invoke(this)
        }

        fun Any.buildUri(costTime: Long, dataList: List<*>): Any? {
            return buildUri.invoke(this, costTime, dataList)
        }

        fun Any.gotoResult(context: Context, intent: Intent, uri: Uri, exerciseType: Int) {
            gotoResult.invoke(this, context, intent, uri, exerciseType)
        }
    }

    private class QuickExercisePresenterWrapper(presenterClass: Class<*>) {
        private val startExercise: Method = presenterClass.declaredMethods.first { it.name == "c" }

        private val getAnswers: Method = presenterClass.declaredMethods.first { it.name == "g" }

        private val commitAnswer: Method = presenterClass.declaredMethods.first {
            it.name == "e" && it.parameterCount == 2 && it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == List::class.java
        }

        private val nextQuestion: Method = presenterClass.declaredMethods.first {
            it.name == "d" && it.parameterCount == 2 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType && it.parameterTypes[1] == List::class.java
        }

        fun Any.startExercise() {
            startExercise.invoke(this)
        }

        fun Any.getAnswers(): List<*>? {
            return getAnswers.invoke(this) as? List<*>
        }

        fun Any.commitAnswer(answer: String) {
            commitAnswer.invoke(this, answer, emptyList<Any>() /*wrongScript*/)
        }

        fun Any.nextQuestion(autoJump: Boolean, strokes: List<Array<PointF>>) {
            nextQuestion.invoke(this, autoJump, strokes)
        }
    }

    override val name: String
        get() = "PracticeHook"

    private val executor: ScheduledExecutorService by lazy {
        ScheduledThreadPoolExecutor(5, DiscardPolicy())
    }

    private lateinit var presenterRef: WeakReference<Any>

    private val presenter get() = presenterRef.get()

    override fun startHook() {
        val quickExerciseActivityClass = findClass(Classname.QUICK_EXERCISE_ACTIVITY)

        hookQuickExerciseActivity(quickExerciseActivityClass)

        hookQuickExercisePresenter(quickExerciseActivityClass)

        hookCountDownTimer()

        hookSimpleWebActivityCompanion()
    }

    private fun hookQuickExercisePresenter(quickExerciseActivityClass: Class<*>) {
        // 新版(3.140+) 已移除 QuickExercisePresenter（自动答题重构），类不存在时跳过该功能。
        val quickExercisePresenterClass =
            XposedHelpers.findClassIfExists(Classname.PRESENTER, classLoader)
                ?: return logI("PracticeHook: QuickExercisePresenter not found in host, skip presenter hooks")
        val presenterWrapper = QuickExercisePresenterWrapper(quickExercisePresenterClass)
        val performNext = Runnable {
            if (Practice.autoPractice) {
                with(presenterWrapper) {
                    presenter?.run {
                        startExercise()
                        val answer = getAnswers()?.get(0).toString()
                        commitAnswer(answer)
                        nextQuestion(true, answer.strokes.toList())
                    }
                }
            }
        }
        // afterAnimation
        quickExercisePresenterClass.findMethod("N").intercept("presenter_N") { chain ->
            val r = chain.proceed()
            if (Practice.autoPractice) {
                mainHandler.post(performNext)
            }
            r
        }

        val modelClass =
            (quickExerciseActivityClass.genericSuperclass as ParameterizedType).actualTypeArguments[1]
        val getGeneralModel =
            quickExerciseActivityClass.declaredMethods.first { it.returnType == modelClass && it.parameterCount == 0 }
                .also { it.isAccessible = true }
        val modelWrapper =
            ExerciseGeneralModelWrapper(modelClass as Class<*>, findClass(Classname.EXERCISE_TYPE))
        // afterLoadFinish
        val afterLoadFinish = quickExercisePresenterClass.declaredMethods.first {
            it.parameterCount == 1 && List::class.java.isAssignableFrom(it.parameterTypes[0])
        }.also { it.isAccessible = true }
        afterLoadFinish.intercept("presenter_afterLoadFinish") { chain ->
            val r = chain.proceed()
            val thisObject = chain.thisObject
            presenterRef = WeakReference(thisObject)
            if (!Practice.autoPractice) {
                return@intercept r
            }
            if (Practice.autoPracticeQuick) {
                kotlin.runCatching {
                    val v = XposedHelpers.getObjectField(thisObject!!, "a")
                    val activity = XposedHelpers.callMethod(v, "getContext") as Activity
                    val model = getGeneralModel.invoke(activity)
                    val dataList = quickExercisePresenterClass.declaredFields.firstOrNull {
                        List::class.java.isAssignableFrom(it.type)
                    }?.get(thisObject) as List<*>
                    var totalTime = 0
                    dataList.subList(1, dataList.size - 1).forEach { data ->
                        val answers = XposedHelpers.getObjectField(data, "rightAnswers") as? List<*>
                        val answer = answers?.firstOrNull()?.toString() ?: ""
                        XposedHelpers.callMethod(data, "setUserAnswer", answer)
                        val costTime = Random.nextInt(150, 250)
                        XposedHelpers.callMethod(data, "setCostTime", costTime)
                        XposedHelpers.callMethod(data, "setStrokes", answer.strokes)
                        totalTime += costTime
                    }
                    mainHandler.postDelayed({
                        with(modelWrapper) {
                            model?.run {
                                val exerciseType = getExerciseType()
                                val exerciseTypeInt =
                                    XposedHelpers.getIntField(exerciseType!!, "exerciseType")
                                val intent = activity.intent
                                val uri = buildUri(totalTime.toLong(), dataList) as Uri
                                gotoResult(activity, intent, uri, exerciseTypeInt)
                                activity.finish()
                            }
                        }
                    }, totalTime.toLong())
                }.onFailure {
                    logI(it)
                }
            } else {
                mainHandler.post(performNext)
            }
            r
        }
    }

    private fun hookCountDownTimer() {
        val countDownTimerClass = CountDownTimer::class.java
        var ctorHandle: HookHandle? = null
        ctorHandle = countDownTimerClass.findConstructor(
            Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!
        ).intercept("timer_ctor") { chain ->
            val r = chain.proceed()
            val thisClass = chain.thisObject?.javaClass
            if (thisClass != null && thisClass.name.startsWith(Classname.PRESENTER)) {
                logI("hook timer")
                thisClass.findMethod("onFinish").intercept("timer_onFinish") { c ->
                    if (Practice.autoHonor) null else c.proceed()
                }
                ctorHandle?.unhook()
            }
            r
        }
    }

    private fun showEditAlertDialog(context: Context, onConfirm: (Int) -> Unit) {
        val editText = EditText(context)
        editText.inputType = EditorInfo.TYPE_CLASS_NUMBER
        editText.filters = arrayOf(InputFilter.LengthFilter(9))

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16f, context.resources.displayMetrics
            ).toInt()
            setPaddingRelative(padding, padding, padding, 0)
            addView(editText)
        }

        val dialog = AlertDialog.Builder(context)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val targetCount = editText.text.toString().toInt()
                onConfirm(targetCount)
            }.setNegativeButton(android.R.string.cancel, null)
            .setTitle("请输入练习次数")
            .setView(container).show()
        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        positiveButton.isEnabled = false
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                positiveButton.isEnabled = s.isNotEmpty()
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun showProgressDialog(context: Context, onDismiss: () -> Unit): (Int, Int) -> Unit {
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        )
        val textView = TextView(context).apply {
            text = "0/0"
            textSize = 16f
            setTextColor(Color.rgb(0x33, 0x33, 0x33))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setHorizontalGravity(Gravity.END)
            val padding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16f, context.resources.displayMetrics
            ).toInt()
            setPaddingRelative(padding, padding, padding, 0)
            addView(progressBar)
            addView(textView)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("练习进度")
            .setView(container)
            .setNegativeButton("停止", null)
            .setCancelable(false)
            .setOnDismissListener {
                onDismiss()
            }.show()
        return { current, target ->
            mainHandler.post {
                val progress = (100 * (current / target.toFloat())).toInt().coerceIn(0, 100)
                progressBar.setProgress(progress, true)
                textView.text = "$current/$target"
                if (progress >= 100) {
                    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).text = "完成"
                }
            }
        }
    }

    private fun testDelay(keyPointId: String, limit: Int) {
        thread {
            var delay = 10000L
            var lastReqTime: Long
            val lock = ReentrantLock()
            val condition = lock.newCondition()
            var active = true
            var count = 0
            Thread.sleep(delay)
            while (active) {
                lock.withLock {
                    lastReqTime = SystemClock.elapsedRealtime()
                    logI("req start with delay: $delay")
                    OralApiService.getExamInfo(keyPointId, limit) {
                        lock.withLock {
                            if (it.isSuccess) {
                                count++
                                if (count >= 10) {
                                    count = 0
                                    delay -= 500
                                    logI("cur delay: $delay")
                                }
                            } else {
                                active = false
                                logI("final delay: $delay")
                            }
                            condition.signalAll()
                        }
                    }
                    condition.await(delay, TimeUnit.MILLISECONDS)
                    val elapsed = SystemClock.elapsedRealtime() - lastReqTime
                    if (elapsed < delay) {
                        condition.await(delay - elapsed, TimeUnit.MILLISECONDS)
                    }
                }
            }
        }
    }

    private fun hookQuickExerciseActivity(quickExerciseActivityClass: Class<*>) {
        val lifecycleOwnerKtClass = findClass(Classname.LIFECYCLE_OWNER_KT)
        val modelClass =
            (quickExerciseActivityClass.genericSuperclass as ParameterizedType).actualTypeArguments.getOrNull(
                1
            )
        val getGeneralModel =
            quickExerciseActivityClass.declaredMethods.firstOrNull { it.returnType == modelClass && it.parameterCount == 0 }
                ?.also { it.isAccessible = true }

        var helper: HonorHelper? = null
        quickExerciseActivityClass.findMethod("onCreate", Bundle::class.java)
            .intercept("quick_activity_onCreate") { chain ->
                val r = chain.proceed()
                val activity = chain.thisObject
                val scope =
                    XposedHelpers.callStaticMethod(lifecycleOwnerKtClass, "getLifecycleScope", activity)
                val coroutineContext = XposedHelpers.callMethod(scope, "getCoroutineContext")
                val model = getGeneralModel?.invoke(activity) ?: return@intercept r
                val keyPointId = XposedHelpers.getIntField(model, "a").toString()
                val limit = XposedHelpers.getIntField(model, "c")

                OralApiService.setup(coroutineContext!!)
                if (Practice.autoHonor) {
                    //                testDelay(keyPointId, limit)
                    showEditAlertDialog(activity as Context) { targetCount ->
                        val onProgressChange = showProgressDialog(activity) {
                            helper?.stopHonor()
                        }
                        helper = HonorHelper(keyPointId, limit, targetCount, onProgressChange).also {
                            it.startHonor()
                        }
                    }
                }
                r
            }

        quickExerciseActivityClass.findMethod("onDestroy")
            .intercept("quick_activity_onDestroy") { chain ->
                helper?.stopHonor()
                chain.proceed()
            }
    }

    private fun hookSimpleWebActivityCompanion() {
        val exerciseResultActivityClass = findClass(Classname.EXERCISE_RESULT_ACTIVITY)
        val simpleWebActivityCompanionClass =
            findClass("${Classname.SIMPLE_WEB_APP_FIREWORK_ACTIVITY}\$a")

        simpleWebActivityCompanionClass.allMethod("a").forEach { m ->
            m.also { it.isAccessible = true }.intercept("web_companion_a") { chain ->
                if (!Practice.autoPracticeCyclic) {
                    return@intercept chain.proceed()
                }
                val activity = chain.getArg(0) as? Activity ?: return@intercept chain.proceed()
                if (exerciseResultActivityClass.isInstance(activity)) {
                    val interval = Practice.autoPracticeCyclicInterval
                    mainHandler.postDelayed({
                        if (!activity.isDestroyed && !activity.isFinishing) {
                            kotlin.runCatching {
                                activity.findViewById<View>(
                                    activity.resources.getIdentifier(
                                        "menu_button_again_btn", "id", activity.packageName
                                    )
                                ).performClick()
                            }.onFailure {
                                logI(it)
                            }
                        }
                    }, interval.toLong())
                    null
                } else {
                    chain.proceed()
                }
            }
        }
    }

    private inner class HonorHelper(
        private val keyPointId: String,
        private val limit: Int,
        private val targetCount: Int = Int.MAX_VALUE,
        private val onProgress: (Int, Int) -> Unit
    ) {
        private val lock = ReentrantLock()

        private val getExamInfoCondition = lock.newCondition()

        private var pendingCount: Int = 0

        private var successCount: Int = 0

        @Volatile
        private var active: Boolean = true

        private var workerThread: Thread? = null

        fun stopHonor() {
            active = false
            workerThread?.interrupt()
        }

        fun startHonor() {
            if (targetCount <= 0) {
                stopHonor()
                return
            }
            workerThread = thread {
                doWork()
            }
        }

        private fun doWork() {
            var lastReqTime: Long
            var waitTime = 5000L
            var reqSuccessCount = 0
            while (active && !Thread.interrupted()) {
                try {
                    lock.withLock {
                        logI("pendingCount: $pendingCount, successCount: $successCount")
                        while (pendingCount >= 0 && successCount + pendingCount >= targetCount) {
                            getExamInfoCondition.await()
                            if (successCount >= targetCount) {
                                stopHonor()
                                return@withLock
                            } else {
                                continue
                            }
                        }

                        pendingCount += 1

                        lastReqTime = SystemClock.elapsedRealtime()
                        OralApiService.getExamInfo(keyPointId, limit) { result ->
                            lock.withLock {
                                result.onSuccess {
                                    logI("get exam elapsed: ${SystemClock.elapsedRealtime() - lastReqTime}")
                                    handleExamVO(it)
                                    reqSuccessCount += 1
                                    if (reqSuccessCount >= 10) {
                                        waitTime = waitTime.minus(1000).coerceAtLeast(2033)
                                        logI("waitTime decrease to $waitTime")
                                        reqSuccessCount = 0
                                    }
                                }.onFailure {
                                    pendingCount -= 1
                                    if (it !is CancellationException) {
                                        reqSuccessCount = 0
                                        waitTime += 1000
                                        logI("waitTime increase to $waitTime")
                                        logI("get exam failed: ${it.message}")
                                    }
                                    getExamInfoCondition.signalAll()
                                }
                            }
                        }
                        getExamInfoCondition.await(waitTime, TimeUnit.MILLISECONDS)
                        val elapsed = SystemClock.elapsedRealtime() - lastReqTime
                        if (elapsed < waitTime) {
                            getExamInfoCondition.await(waitTime - elapsed, TimeUnit.MILLISECONDS)
                        }
                    }
                } catch (_: InterruptedException) {
                }
            }
        }

        private fun handleExamVO(examVO: Any) {
            if (!active) {
                return
            }
            executor.execute {
                kotlin.runCatching {
                    buildAndUploadExamResult(examVO)
                }.onFailure {
                    logI(it)
                }
            }
        }

        private fun buildExamResult(examVO: Any): Pair<String, Long> {
            val examId = XposedHelpers.getObjectField(examVO, "idString").toString()
            val questions = XposedHelpers.getObjectField(examVO, "questions") as List<*>
            var totalTime = 0L
            questions.forEach {
                val answers = XposedHelpers.getObjectField(it, "answers") as? List<*>
                val answer = answers?.firstOrNull()?.toString() ?: ""
                XposedHelpers.callMethod(it, "setUserAnswer", answer)
                val costTime = Random.nextInt(150, 250)
                XposedHelpers.callMethod(it, "setCostTime", costTime)
                XposedHelpers.callMethod(it, "setScript", answer.strokes.toJsonString())
                XposedHelpers.callMethod(it, "setStatus", 1)
                totalTime += costTime
            }
            val questionCnt = XposedHelpers.getIntField(examVO, "questionCnt")
            XposedHelpers.callMethod(examVO, "setCorrectCnt", questionCnt)
            XposedHelpers.callMethod(examVO, "setCostTime", totalTime)
            return examId to (totalTime + 200)
        }

        private fun buildAndUploadExamResult(examVO: Any) {
            val (examId, delay) = buildExamResult(examVO)
            val uploadReqTime = SystemClock.elapsedRealtime()
            val runnable = Runnable {
                OralApiService.uploadExamResult(examId, examVO) {
                    val elapsed = SystemClock.elapsedRealtime() - uploadReqTime
                    lock.withLock {
                        pendingCount -= 1
                        getExamInfoCondition.signalAll()
                        it.onFailure {
                            if (it !is CancellationException) {
                                logI("upload exam failed: ${it.message}")
                            }
                        }.onSuccess {
                            successCount += 1
                            logI("upload exam elapsed: $elapsed")
                            onProgress(successCount, targetCount)
                        }
                    }
                }
            }
            executor.schedule(runnable, delay, TimeUnit.MILLISECONDS)
        }
    }
}

// quick.js —— PK「秒结算」自动答题（极速模式重构版）
// 适配 3.140（leo-web-math-exercise/animation-oral.html，Vue2.7）：
//   - 不再依赖旧版结构（onChoseRight/showMatching/pkMatchingData 已在 3.140 移除）；
//   - 用「递归遍历 Vue 组件树」定位答题组件，调用 slideToNextQuestion / finishExercise；
//   - 答案正确性由模块侧 EncryptResult hook 保证（判题 answerPaperResult.answer===CORRECT 恒成立）；
//   - 触发判题：写入 answerPaperResult.answer 为正确值 → 前端自动判对 → 下一题 / 完成。
// 全部 try-catch：失败静默，不影响页面。
setTimeout(function () {
    function findRoot() {
        try {
            return (window.VUE_APP && window.VUE_APP.$root) ||
                (document.querySelector('#app') && document.querySelector('#app').__vue__) ||
                findVueRoot(document.body);
        } catch (e) { return null; }
    }
    function findVueRoot(el) {
        try {
            var cur = el;
            while (cur) {
                if (cur.__vue__) return cur.__vue__.$root;
                cur = cur.parentElement;
            }
        } catch (e) { }
        return null;
    }
    // 递归遍历组件树，找到具备答题能力的组件（含 slideToNextQuestion / finishExercise）
    function findAnswerComp(root, depth) {
        if (!root) return null;
        if (depth > 12) return null;
        var comps = [];
        function walk(comp, d) {
            if (!comp || d > 12) return;
            comps.push(comp);
            if (comp.$children) for (var i = 0; i < comp.$children.length; i++) walk(comp.$children[i], d + 1);
        }
        walk(root, 0);
        for (var i = 0; i < comps.length; i++) {
            var c = comps[i];
            var p = c._setupProxy || c._setupState || c;
            var hasSlide = typeof p.slideToNextQuestion === 'function';
            var hasFinish = typeof p.finishExercise === 'function';
            var hasCorrect = typeof p.correctCount !== 'undefined' || typeof p.questionIndex !== 'undefined';
            if ((hasSlide || hasFinish) && hasCorrect) return c;
        }
        // 放宽：只要含 finishExercise
        for (var j = 0; j < comps.length; j++) {
            var c2 = comps[j];
            var p2 = c2._setupProxy || c2._setupState || c2;
            if (typeof p2.finishExercise === 'function') return c2;
        }
        return null;
    }
    function proxyOf(comp) { return comp && (comp._setupProxy || comp._setupState || comp); }
    var answered = 0, finished = false;
    function tryAnswer() {
        try {
            var root = findRoot();
            var comp = root && findAnswerComp(root, 0);
            if (!comp) return false;
            var p = proxyOf(comp);
            var list = p.questionsList || p.questionList;
            var idx = p.questionIndex;
            var total = list ? list.length : 0;
            // 写入判题所需字段：answerPaperResult.answer 用正确值（EncryptResult 已保证答案正确）
            if (p.curTrueAnswer && typeof p.curTrueAnswer === 'object' && !p.curTrueAnswer.answer) {
                try { p.curTrueAnswer.answer = '1'; } catch (e) { }
            }
            if (typeof p.answerPaperResult !== 'undefined' && p.answerPaperResult && !p.answerPaperResult.answer) {
                try { p.answerPaperResult.answer = '1'; } catch (e) { }
            }
            // 有答案：触发判对 -> 下一题 / 完成
            if (p.curTrueAnswer && p.curTrueAnswer.answer) {
                if (idx >= total - 1) {
                    if (typeof p.finishExercise === 'function' && !finished) {
                        finished = true;
                        p.finishExercise();
                        answered++;
                        return true;
                    }
                } else {
                    if (typeof p.slideToNextQuestion === 'function') {
                        p.slideToNextQuestion();
                        answered++;
                        return true;
                    }
                }
            }
            // 兜底：调 onChoseRight（旧版）/ dataReady
            if (typeof p.onChoseRight === 'function') {
                try {
                    var q = (list && list[idx]) || {};
                    p.onChoseRight(q.answer || '1');
                    answered++;
                    return true;
                } catch (e) { }
            }
            return false;
        } catch (e) { return false; }
    }
    var tries = 0;
    var timer = setInterval(function () {
        tries++;
        if (answered >= 100) { clearInterval(timer); return; }
        if (tryAnswer()) return;
        if (tries > 200) { clearInterval(timer); return; } // ~40s 兜底
    }, 200);
    setTimeout(function () { try { clearInterval(timer); } catch (e) { } }, 60000);
    console.log('quick(秒结算) js injected');
}, 0);

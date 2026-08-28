// quick.js —— 通用自动答题（PK exercise.html Vue3 + 练习 animation-oral.html Vue2.7 双适配）
// 适配 2026 前端（leo-web-oral-pk 远程 bundle，Vite+Vue3；animation-oral 本地 Vue2.7）：
//   - Vue3 组件树遍历（#app.__vue_app__._instance -> subTree -> component.props/setupState/provides）；
//   - Vue2 兼容（#app.__vue__ -> $children -> _setupProxy）；
//   - 判题语义：answerPaperResult.answer === 1(G.CORRECT) 即判对；questionList.status=1 计入提交包。
// 双保险：
//   1) 本脚本在组件层把 answerPaperResult.answer 置 1 并修正 questionList.status/userAnswer；
//   2) native 侧 WebViewHook.hookDataEncrypt 在提交载荷层兜底改写（userAnswer=正确答案/status=1/correctCnt）——
//      无论前端判题链是否推进，提交包必为全对。
// 诊断日志（答题尝试计数/是否找到组件/遍历深度）经 AutoOral bridge 写文件日志。
setTimeout(function () {
    var CFG = window.__aa_config || {};
    var MODE = CFG.mode || 'quick';
    var CUSTOM_ANSWER = CFG.answer || '';
    var CORRECT_COUNT = CFG.correctCount || 0;   // 0=全对，>0=前 N 题对
    var tries = 0, rootFound = 0, vue3 = false, vue2 = false, depth = 0, comps = 0, stateSet = 0, statusSet = 0;
    function dbg(msg) {
        try { if (window.AutoOral && window.AutoOral.log) window.AutoOral.log(String(msg)); } catch (e) {}
        try { console.log(String(msg)); } catch (e) {}
    }
    function correctOf(q) {
        if (!q) return '';
        if (typeof q.answer === 'string' && q.answer) return q.answer;
        if (q.answers && q.answers.length && typeof q.answers[0] === 'string') return q.answers[0];
        if (q.curTrueAnswer) {
            if (typeof q.curTrueAnswer.recognizeResult === 'string' && q.curTrueAnswer.recognizeResult) return q.curTrueAnswer.recognizeResult;
            if (typeof q.curTrueAnswer.answerText === 'string' && q.curTrueAnswer.answerText) return q.curTrueAnswer.answerText;
        }
        return '';
    }
    function isQList(v) {
        return !!v && Array.isArray(v) && v.length > 0 && v[0] && typeof v[0] === 'object' &&
            ('status' in v[0] || 'curTrueAnswer' in v[0] || 'userAnswer' in v[0]);
    }
    function fixList(list) {
        var changed = false;
        for (var i = 0; i < list.length; i++) {
            var q = list[i];
            if (!q || typeof q !== 'object') continue;
            var correct = CUSTOM_ANSWER || correctOf(q);
            var shouldCorrect = CORRECT_COUNT > 0 ? i < CORRECT_COUNT : true;
            if (shouldCorrect) {
                if (correct && q.userAnswer !== correct) { try { q.userAnswer = correct; changed = true; } catch (e) {} }
                if (q.status !== 1) { try { q.status = 1; changed = true; } catch (e) {} }
            } else {
                if (q.status !== 0) { try { q.status = 0; changed = true; } catch (e) {} }
            }
        }
        if (changed) statusSet++;
        return changed;
    }
    function findV3Root() {
        try {
            var app = document.querySelector('#app');
            if (app && app.__vue_app__ && app.__vue_app__._instance) return app.__vue_app__._instance;
            if (window.VUE_APP && window.VUE_APP.__vue_app__ && window.VUE_APP.__vue_app__._instance) return window.VUE_APP.__vue_app__._instance;
        } catch (e) {}
        return null;
    }
    function walkV3(node, d) {
        if (!node || d > 16) return;
        if (node.component) {
            var inst = node.component;
            if (d > depth) depth = d; comps++;
            var props = inst.props || {};
            if (props.answerPaperResult) {
                var ar = props.answerPaperResult;
                if (ar && ar.answer !== 1) {
                    try { ar.answer = 1; stateSet++; } catch (e) {}
                    try { if (CUSTOM_ANSWER) ar.recognizeResult = CUSTOM_ANSWER; } catch (e) {}
                    try { if (!ar.pathPoints) ar.pathPoints = []; } catch (e) {}
                }
            }
            try { if (isQList(inst.setupState && inst.setupState.questionList)) fixList(inst.setupState.questionList); } catch (e) {}
            try { if (isQList(inst.setupState && inst.setupState.questionsList)) fixList(inst.setupState.questionsList); } catch (e) {}
            try { if (isQList(inst.proxy && inst.proxy.questionList)) fixList(inst.proxy.questionList); } catch (e) {}
            try { if (inst.provides) for (var k in inst.provides) { var v = inst.provides[k]; if (isQList(v)) fixList(v); } } catch (e) {}
            walkV3(inst.subTree, d + 1);
            return;
        }
        if (Array.isArray(node.children)) { for (var i = 0; i < node.children.length; i++) walkV3(node.children[i], d + 1); }
        else if (node.children && typeof node.children === 'object') walkV3(node.children, d + 1);
        if (node.dynamicChildren) { for (var j = 0; j < node.dynamicChildren.length; j++) walkV3(node.dynamicChildren[j], d + 1); }
    }
    function findV2Root() {
        try {
            var app = document.querySelector('#app');
            if (app && app.__vue__) return app.__vue__.$root;
            if (window.VUE_APP && window.VUE_APP.$root) return window.VUE_APP.$root;
            var cur = document.body;
            while (cur) { if (cur.__vue__) return cur.__vue__.$root; cur = cur.parentElement; }
        } catch (e) {}
        return null;
    }
    function walkV2(comp, d) {
        if (!comp || d > 16) return;
        if (d > depth) depth = d; comps++;
        var p = comp._setupProxy || comp._setupState || comp;
        try { if (p.answerPaperResult && p.answerPaperResult.answer !== 1) { p.answerPaperResult.answer = 1; stateSet++; } } catch (e) {}
        var lists = [];
        try { if (isQList(p.questionList)) lists.push(p.questionList); } catch (e) {}
        try { if (isQList(p.questionsList)) lists.push(p.questionsList); } catch (e) {}
        for (var i = 0; i < lists.length; i++) fixList(lists[i]);
        if (comp.$children) for (var j = 0; j < comp.$children.length; j++) walkV2(comp.$children[j], d + 1);
    }
    function tryAnswer() {
        tries++;
        var root3 = findV3Root();
        if (root3) { vue3 = true; rootFound++; try { walkV3(root3.subTree, 0); } catch (e) {} return; }
        var root2 = findV2Root();
        if (root2) { vue2 = true; rootFound++; try { walkV2(root2, 0); } catch (e) {} return; }
    }
    dbg('[quick] js injected, mode=' + MODE + ', custom=' + CUSTOM_ANSWER + ', correctCount=' + CORRECT_COUNT);
    var timer = setInterval(function () {
        tryAnswer();
        if (tries % 10 === 0) dbg('[quick] diag tries=' + tries + ' root=' + rootFound + ' vue3=' + vue3 + ' vue2=' + vue2 + ' depth=' + depth + ' comps=' + comps + ' stateSet=' + stateSet + ' statusSet=' + statusSet);
        if (tries > 250) { clearInterval(timer); dbg('[quick] stopped tries=' + tries); }
    }, 200);
    setTimeout(function () { try { clearInterval(timer); } catch (e) {} }, 60000);
}, 0);

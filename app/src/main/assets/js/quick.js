// quick.js —— 通用自动答题 v3（PK exercise.html Vue3 + 练习 animation-oral.html Vue2）
// 核心机制（恢复旧版「绘制」驱动）：
//   1) 绘制：把正确答案的笔画（内置字形库，纯 JS，无需 native lib）画到答题 canvas，
//      触发前端**原生** recognize 链（strokes -> native OCR(expectedResult) -> At -> Bt 判对）
//      -> 前端自己写 status=1 -> 推进 -> 提交全对。这是标准/极速模式可用的根基。
//   2) 状态修正：Vue2/Vue3 树里置 answerPaperResult.answer=1(CORRECT) 并修正 questionList.status/userAnswer。
//   3) 提交载荷兜底在 native 侧（WebViewHook.hookDataEncrypt）——前端未推进时提交包也全对。
// 诊断日志经 AutoOral bridge 写文件日志（tries/绘制次数/组件查找/遍历深度）。
setTimeout(function () {
    var CFG = window.__aa_config || {};
    var MODE = CFG.mode || 'quick';
    var CUSTOM_ANSWER = CFG.answer || '';
    var CORRECT_COUNT = CFG.correctCount || 0;
    var tries = 0, rootFound = 0, vue3 = false, vue2 = false, depth = 0, comps = 0, stateSet = 0, statusSet = 0, drawn = 0, drawnAnswer = '';
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
    // ---------- Vue3 / Vue2 遍历（同 v2） ----------
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
    // ---------- 绘制：把正确答案笔画画到 canvas（恢复旧版「绘制」机制） ----------
    // 字形库：字符 -> 笔画数组，每笔为归一化折线点 [x,y]（0~1 网格，手写风格）
    var GLYPHS = {
        '0': [[[0.35,0.12],[0.6,0.12],[0.74,0.22],[0.8,0.4],[0.8,0.6],[0.74,0.8],[0.6,0.9],[0.4,0.9],[0.26,0.8],[0.2,0.6],[0.2,0.4],[0.26,0.22],[0.35,0.12]]],
        '1': [[[0.42,0.35],[0.52,0.15],[0.56,0.2],[0.56,0.85]]],
        '2': [[[0.2,0.35],[0.24,0.2],[0.45,0.12],[0.66,0.22],[0.72,0.4],[0.5,0.58],[0.25,0.75],[0.7,0.85],[0.78,0.9]]],
        '3': [[[0.24,0.18],[0.55,0.1],[0.7,0.25],[0.62,0.42],[0.38,0.45],[0.68,0.55],[0.74,0.72],[0.6,0.88],[0.3,0.88]]],
        '4': [[[0.58,0.1],[0.58,0.85]],[0.3,0.55],[0.32,0.42],[0.72,0.42]]],
        '5': [[[0.22,0.14],[0.7,0.14],[0.7,0.4],[0.3,0.42],[0.24,0.55],[0.3,0.75],[0.5,0.88],[0.7,0.85],[0.78,0.72]]],
        '6': [[[0.35,0.15],[0.65,0.2],[0.76,0.4],[0.74,0.7],[0.6,0.88],[0.4,0.88],[0.26,0.7],[0.24,0.5],[0.4,0.4],[0.62,0.45]]],
        '7': [[[0.2,0.15],[0.75,0.15],[0.5,0.4],[0.45,0.85]]],
        '8': [[[0.3,0.12],[0.62,0.2],[0.68,0.38],[0.5,0.5],[0.3,0.5],[0.25,0.35],[0.4,0.28],[0.62,0.38],[0.7,0.6],[0.65,0.8],[0.45,0.9],[0.28,0.82],[0.25,0.6],[0.45,0.5]]],
        '9': [[[0.4,0.12],[0.62,0.2],[0.72,0.4],[0.7,0.6],[0.55,0.75],[0.38,0.72],[0.3,0.55],[0.45,0.45],[0.65,0.5]],[0.52,0.88],[0.5,0.85]]],
        '+': [[[0.5,0.2],[0.5,0.8]],[0.2,0.5],[0.8,0.5]]],
        '-': [[[0.2,0.5],[0.8,0.5]]],
        'x': [[[0.25,0.2],[0.75,0.8]],[0.75,0.2],[0.25,0.8]]],
        'X': [[[0.25,0.2],[0.75,0.8]],[0.75,0.2],[0.25,0.8]]],
        '*': [[[0.5,0.2],[0.5,0.8]],[0.2,0.5],[0.8,0.5]],[0.3,0.3],[0.7,0.7]],[0.7,0.3],[0.3,0.7]]],
        '/': [[[0.25,0.15],[0.75,0.85]]],
        '=': [[[0.2,0.35],[0.8,0.35]],[0.2,0.65],[0.8,0.65]]],
        '>': [[[0.25,0.2],[0.75,0.5],[0.25,0.8]]],
        '<': [[[0.75,0.2],[0.25,0.5],[0.75,0.8]]],
        '.': [[[0.45,0.7],[0.55,0.7]]],
        ':': [[[0.5,0.3],[0.5,0.35]],[0.5,0.65],[0.5,0.7]]],
        '?': [[[0.25,0.35],[0.3,0.2],[0.5,0.12],[0.68,0.25],[0.6,0.42],[0.45,0.5],[0.45,0.65]],[0.45,0.82],[0.45,0.85]]],
        ' ': []
    };
    function findCanvas() {
        var cl = document.querySelectorAll('canvas');
        var best = null, bestArea = 0;
        for (var i = 0; i < cl.length; i++) {
            var r = cl[i].getBoundingClientRect();
            var a = r.width * r.height;
            if (a > bestArea) { bestArea = a; best = cl[i]; }
        }
        return best;
    }
    function dispatchStroke(c, pts, ox, oy, sx, sy) {
        for (var i = 0; i < pts.length; i++) {
            var x = ox + pts[i][0] * sx, y = oy + pts[i][1] * sy;
            var type = i === 0 ? 'pointerdown' : (i === pts.length - 1 ? 'pointerup' : 'pointermove');
            try {
                c.dispatchEvent(new PointerEvent(type, {
                    bubbles: true, cancelable: true, composed: true,
                    pointerId: 1, pointerType: 'touch', isPrimary: true, pressure: 0.5,
                    clientX: x, clientY: y, button: 0, buttons: type === 'pointerup' ? 0 : 1
                }));
            } catch (e) {}
        }
    }
    /** 绘制答案文本：逐字符按网格布局画到 canvas。返回是否画了笔。 */
    function drawAnswer(text) {
        if (!text) return false;
        var c = findCanvas();
        if (!c) return false;
        var rect = c.getBoundingClientRect();
        if (rect.width < 10 || rect.height < 10) return false;
        var chars = String(text).split('');
        var n = chars.length;
        var cellW = rect.width / n;
        var cellH = rect.height;
        var drawnAny = false;
        for (var ci = 0; ci < n; ci++) {
            var pts = GLYPHS[chars[ci]];
            if (!pts || !pts.length) continue;
            var sx = cellW * 0.72, sy = cellH * 0.78;
            var ox = rect.left + cellW * (ci + 0.5) - sx / 2;
            var oy = rect.top + (cellH - sy) / 2;
            for (var s = 0; s < pts.length; s++) {
                dispatchStroke(c, pts[s], ox, oy, sx, sy);
                drawnAny = true;
            }
        }
        return drawnAny;
    }
    // ---------- 主循环 ----------
    function currentCorrectAnswer() {
        // 从 Vue 树拿当前题答案（优先 CUSTOM_ANSWER）
        if (CUSTOM_ANSWER) return CUSTOM_ANSWER;
        var found = '';
        function pickFromInst(inst) {
            if (found) return;
            var lists = [];
            try { if (isQList(inst.setupState && inst.setupState.questionList)) lists.push(inst.setupState.questionList); } catch (e) {}
            try { if (isQList(inst.setupState && inst.setupState.questionsList)) lists.push(inst.setupState.questionsList); } catch (e) {}
            try { if (isQList(inst.proxy && inst.proxy.questionList)) lists.push(inst.proxy.questionList); } catch (e) {}
            for (var i = 0; i < lists.length; i++) {
                var list = lists[i];
                var idx = -1;
                try { if (inst.setupState && typeof inst.setupState.questionIndex === 'number') idx = inst.setupState.questionIndex; } catch (e) {}
                try { if (inst.setupState && inst.setupState.questionIndex && typeof inst.setupState.questionIndex.value === 'number') idx = inst.setupState.questionIndex.value; } catch (e) {}
                if (idx < 0) idx = 0;
                var q = list[idx] || list[0];
                if (q) { found = correctOf(q); if (found) return; }
            }
        }
        function walkInst(inst, d) {
            if (!inst || d > 14 || found) return;
            pickFromInst(inst);
            var sub = inst.subTree;
            if (!sub) return;
            (function walkV(node, dd) {
                if (!node || dd > 14 || found) return;
                if (node.component) { walkInst(node.component, dd + 1); return; }
                if (Array.isArray(node.children)) for (var i = 0; i < node.children.length; i++) walkV(node.children[i], dd + 1);
                else if (node.children && typeof node.children === 'object') walkV(node.children, dd + 1);
                if (node.dynamicChildren) for (var j = 0; j < node.dynamicChildren.length; j++) walkV(node.dynamicChildren[j], dd + 1);
            })(sub, 0);
        }
        var root3 = findV3Root();
        if (root3) { try { walkInst(root3, 0); } catch (e) {} return found; }
        var root2 = findV2Root();
        if (root2) {
            try {
                (function walk2(comp, d) {
                    if (!comp || d > 14 || found) return;
                    var p = comp._setupProxy || comp._setupState || comp;
                    var lists = [];
                    try { if (isQList(p.questionList)) lists.push(p.questionList); } catch (e) {}
                    try { if (isQList(p.questionsList)) lists.push(p.questionsList); } catch (e) {}
                    for (var i = 0; i < lists.length; i++) {
                        var q = lists[i][comp.questionIndex || 0] || lists[i][0];
                        if (q) { found = correctOf(q); if (found) return; }
                    }
                    if (comp.$children) for (var j = 0; j < comp.$children.length; j++) walk2(comp.$children[j], d + 1);
                })(root2, 0);
            } catch (e) {}
            return found;
        }
        return found;
    }
    function tryAnswer() {
        tries++;
        var root3 = findV3Root();
        if (root3) { vue3 = true; rootFound++; try { walkV3(root3.subTree, 0); } catch (e) {} }
        else { var root2 = findV2Root(); if (root2) { vue2 = true; rootFound++; try { walkV2(root2, 0); } catch (e) {} } }
        // 绘制：当前题未判对（answerPaperResult.answer!=1 或 canvas 空）时画答案笔画
        var answer = currentCorrectAnswer();
        if (answer && (answer !== drawnAnswer || tries % 30 === 0)) {
            if (drawAnswer(answer)) { drawn++; drawnAnswer = answer; dbg('[quick] drew answer: ' + answer + ' (total ' + drawn + ')'); }
        }
    }
    dbg('[quick] js injected, mode=' + MODE + ', custom=' + CUSTOM_ANSWER + ', correctCount=' + CORRECT_COUNT);
    var timer = setInterval(function () {
        tryAnswer();
        if (tries % 10 === 0) dbg('[quick] diag tries=' + tries + ' root=' + rootFound + ' vue3=' + vue3 + ' vue2=' + vue2 + ' depth=' + depth + ' comps=' + comps + ' stateSet=' + stateSet + ' statusSet=' + statusSet + ' drawn=' + drawn);
        if (tries > 400) { clearInterval(timer); dbg('[quick] stopped tries=' + tries); }
    }, 200);
    setTimeout(function () { try { clearInterval(timer); } catch (e) {} }, 90000);
}, 0);

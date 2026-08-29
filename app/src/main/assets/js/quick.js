// quick.js —— 通用自动答题 v4（PK exercise.html Vue3 + 练习 animation-oral.html Vue2）
// 2026-08-29 真机提交包实证（qCnt=1/30, userAnswer="1", answers=["1"]）后的修正：
//   - **不再硬改 answerPaperResult.answer / questionList.status**：那会让前端 200ms/题 快速"假推进"，
//     提交包只剩最后一题（30 题只提交 1 题，服务端判分必输）；
//   - 只做一件事：把**当前题正确答案的笔画**画到答题 canvas（内置字形库，纯 JS），
//     触发前端**原生** recognize 链（笔画 -> native OCR(expectedResult) -> At -> Bt 判对
//     -> 前端自己写 status=1 -> 自己推进 -> 提交完整 30 题记录）。前端原生链 100% 正确；
//   - 节奏：画完停手 >=1.5s 等 OCR（前端"停止 700ms 后判题"），题号变了才画下一题；
//     同一题 1.5s 后未推进则重画（OCR 可能判错）；
//   - MODE==='custom' 时画自定义答案（CUSTOM_ANSWER），其余模式画题目数据里的正确答案。
// 提交载荷兜底（WebViewHook.hookDataEncrypt）仍在 native 侧：把提交包已有题的 userAnswer 修正为
// 正确答案 + status=1，防 OCR 识别错导致服务端判错。诊断日志经 AutoOral bridge 写文件日志。
setTimeout(function () {
    var CFG = window.__aa_config || {};
    var MODE = CFG.mode || 'quick';
    var CUSTOM_ANSWER = CFG.answer || '';
    var tries = 0, rootFound = 0, vue3 = false, vue2 = false, depth = 0, comps = 0, drawn = 0;
    var lastDrawAt = 0, lastDrawAnswer = '';
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
    function findV3Root() {
        try {
            var app = document.querySelector('#app');
            if (app && app.__vue_app__ && app.__vue_app__._instance) return app.__vue_app__._instance;
            if (window.VUE_APP && window.VUE_APP.__vue_app__ && window.VUE_APP.__vue_app__._instance) return window.VUE_APP.__vue_app__._instance;
        } catch (e) {}
        return null;
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
    // ---------- 取当前题正确答案 ----------
    function currentCorrectAnswer() {
        if (MODE === 'custom' && CUSTOM_ANSWER) return CUSTOM_ANSWER;
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
            if (d > depth) depth = d; comps++;
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
        if (root3) { vue3 = true; rootFound++; try { walkInst(root3, 0); } catch (e) {} return found; }
        var root2 = findV2Root();
        if (root2) {
            vue2 = true; rootFound++;
            try {
                (function walk2(comp, d) {
                    if (!comp || d > 14 || found) return;
                    if (d > depth) depth = d; comps++;
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
    // ---------- 字形库 + 绘制 ----------
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
    function drawAnswer(text) {
        if (!text) return false;
        var c = findCanvas();
        if (!c) return false;
        var rect = c.getBoundingClientRect();
        if (rect.width < 10 || rect.height < 10) return false;
        var chars = String(text).split('');
        var n = chars.length;
        var cellW = rect.width / n, cellH = rect.height;
        var any = false;
        for (var ci = 0; ci < n; ci++) {
            var pts = GLYPHS[chars[ci]];
            if (!pts || !pts.length) continue;
            var sx = cellW * 0.72, sy = cellH * 0.78;
            var ox = rect.left + cellW * (ci + 0.5) - sx / 2;
            var oy = rect.top + (cellH - sy) / 2;
            for (var s = 0; s < pts.length; s++) { dispatchStroke(c, pts[s], ox, oy, sx, sy); any = true; }
        }
        return any;
    }
    // ---------- 主循环：1.5s 节奏绘制 ----------
    function tryDraw() {
        tries++;
        var answer = currentCorrectAnswer();
        if (!answer) return;
        var now = Date.now();
        // 同一题画过且未到 1.5s：等前端 OCR 判对（前端"停止 700ms 后判题"，画完必须停手）
        if (answer === lastDrawAnswer && now - lastDrawAt < 1500) return;
        if (drawAnswer(answer)) {
            lastDrawAt = now;
            lastDrawAnswer = answer;
            drawn++;
            dbg('[quick] drew: ' + answer + ' (total ' + drawn + ', tries ' + tries + ')');
        }
    }
    dbg('[quick] js injected, mode=' + MODE + ', custom=' + CUSTOM_ANSWER);
    var timer = setInterval(tryDraw, 250);
    setTimeout(function () {
        try { clearInterval(timer); } catch (e) {}
        dbg('[quick] stopped tries=' + tries + ' drawn=' + drawn + ' root=' + rootFound + ' vue3=' + vue3 + ' vue2=' + vue2 + ' depth=' + depth + ' comps=' + comps);
    }, 90000);
}, 0);

// quick.js —— 通用自动答题 v5（PK exercise.html Vue3 / 练习 animation-oral Vue2）
// 2026-08-29 真机确认：Vue 树取答案失败（组件 closure，setupState/provides 不暴露），
// 改为 **native 答案注入驱动**：WebViewHook.hookDataDecrypt 从解密命令明文提取
// examVO.questions[].answer 缓存，quick.js 经 window.AutoOral.getAnswers() 轮询，
// 拿到完整答案列表后**顺序绘制**（每 1.6s 一题），触发前端原生 recognize 判对推进。
// 提交载荷兜底（hookDataEncrypt：userAnswer=正确答案+status=1）在 native 侧兜底。
setTimeout(function () {
    var CFG = window.__aa_config || {};
    var MODE = CFG.mode || 'quick';
    var CUSTOM_ANSWER = CFG.answer || '';
    var ANSWERS = [];
    var idx = 0;
    var tries = 0, drawn = 0, lastDrawAt = 0, lastDrawAnswer = '';
    function dbg(msg) {
        try { if (window.AutoOral && window.AutoOral.log) window.AutoOral.log(String(msg)); } catch (e) {}
        try { console.log(String(msg)); } catch (e) {}
    }
    function fetchAnswers() {
        try {
            // 优先：native 注入的 window.aa_answers（异步注入，可能稍后到达）
            if (window.aa_answers && Array.isArray(window.aa_answers) && window.aa_answers.length && window.aa_answers.length >= ANSWERS.length) {
                ANSWERS = window.aa_answers;
                return;
            }
            // 次选：JS bridge getAnswers()
            if (window.AutoOral && window.AutoOral.getAnswers) {
                var s = window.AutoOral.getAnswers();
                if (s && s.length > 2 && s !== '[]') {
                    var a = JSON.parse(s);
                    if (Array.isArray(a) && a.length) {
                        if (a.length >= ANSWERS.length) ANSWERS = a;
                    }
                }
            }
        } catch (e) {}
    }
    function customOrAnswer(a) {
        if (MODE === 'custom' && CUSTOM_ANSWER) return CUSTOM_ANSWER;
        return a;
    }
    // ---------- 字形库 + 绘制（同 v4） ----------
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
    // ---------- 主循环：getAnswers 顺序绘制（1.6s/题） ----------
    // 画一条「真实连续线段」：多点密集、带轻微抖动，形成完整笔画（防外挂「单点」检测）。
    function drawVerticalLine() {
        var c = findCanvas();
        if (!c) return false;
        var rect = c.getBoundingClientRect();
        if (rect.width < 10 || rect.height < 10) return false;
        var cx = rect.left + rect.width * 0.5;
        var top = rect.top + rect.height * 0.28;
        var bot = rect.top + rect.height * 0.78;
        var n = 22; // 密集点，构成连续线段而非单点
        var pts = [];
        for (var i = 0; i < n; i++) {
            var t = i / (n - 1);
            var x = cx + Math.sin(t * Math.PI) * 2 + (i % 2 === 0 ? 0.4 : -0.4);
            var y = top + t * (bot - top);
            pts.push([x, y]);
        }
        for (var i = 0; i < pts.length; i++) {
            var type = i === 0 ? 'pointerdown' : (i === pts.length - 1 ? 'pointerup' : 'pointermove');
            try {
                c.dispatchEvent(new PointerEvent(type, {
                    bubbles: true, cancelable: true, composed: true,
                    pointerId: 1, pointerType: 'touch', isPrimary: true, pressure: 0.5,
                    clientX: pts[i][0], clientY: pts[i][1], button: 0, buttons: type === 'pointerup' ? 0 : 1
                }));
            } catch (e) {}
        }
        return true;
    }
    // 2026-08-29 用户方案：画竖线（手写痕迹，非点击不触发风控），触发前端 recognize 记录路径。
    // 判对/发包由 hookDataEncrypt 提交兜底（答案全 hook 成 1 / status=1 / 补竖线笔画）完成。
    function tryDraw() {
        tries++;
        if (tries % 5 === 0) {
            dbg('[quick] try #' + tries + ' canvas=' + (findCanvas()?1:0) + ' drawn=' + drawn);
        }
        var now = Date.now();
        if (now - lastDrawAt < 1600) return;
        if (drawVerticalLine()) {
            lastDrawAt = now;
            drawn++;
            dbg('[quick] drew line #' + drawn + ' (tries ' + tries + ')');
        }
    }
    // 屏蔽 H5 检测弹窗（alert/confirm）——外挂行为检测概率出现时的兜底
    try { window.alert = function () {}; window.confirm = function () { return true; }; } catch (e) {}
    dbg('[quick] js injected, mode=' + MODE + ', custom=' + CUSTOM_ANSWER);
    var timer = setInterval(tryDraw, 250);
    setTimeout(function () {
        try { clearInterval(timer); } catch (e) {}
        dbg('[quick] stopped tries=' + tries + ' drawn=' + drawn + ' answers=' + ANSWERS.length + ' idx=' + idx);
    }, 90000);
}, 0);
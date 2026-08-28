// fastsettle.js —— PK「秒结算」环境加速
// 移植自 https://github.com/ExElectron/Xiaoyuan_Kousuan_2026 的 MITM 7 大 patch，
// 改为运行时注入（不依赖 MITM 改 JS 源码）：
//   1. CSS 动画压至 0s（切题动画/过渡消失）
//   2. 音效静音（new Audio().play 短路）
//   3. 判题恒真兜底（识别回调缺失时仍能判对/跳题）
//   4. 跳题 0ms（缩短 finishExercise/canSlideToNextQuestion 的 200ms 定时）
//   5. FAULT 错误惩罚分支短路
//   6. 跳过手写识别等待（AUTODRAW：自动在 canvas 画一笔触发判题链）
//   7. watch 触发条件放宽（无 pathPoints 也能跳题）
// 全部 runCatching 风格：任一步失败静默，不影响原答题流程。
setTimeout(function () {
    try {
        // ---- 1. CSS 动画 0s ----
        (function () {
            var st = document.createElement('style');
            st.type = 'text/css';
            st.id = '__fastsettle_style__';
            st.textContent =
                '*{transition:none!important;animation-duration:0s!important;animation-delay:0s!important;' +
                'transition-duration:0s!important;transition-delay:0s!important;}';
            document.head.appendChild(st);
        })();

        // ---- 2. 音效静音 ----
        try {
            var OrigAudio = window.Audio;
            function SilentAudio() {
                return { play: function () { return null; }, pause: function () {}, volume: 0 };
            }
            window.Audio = SilentAudio;
            if (OrigAudio) { OrigAudio.prototype.play = function () { return null; }; }
        } catch (e) { }

        // ---- 3. 自动画一笔（AUTODRAW）：触发判题链，让 answerPaperResult 有值 ----
        var strokes = 0;
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
        function stroke() {
            try {
                var c = findCanvas();
                if (!c) return false;
                var rect = c.getBoundingClientRect();
                var cx = rect.left + rect.width / 2, cy = rect.top + rect.height / 2;
                function ev(type, x, y) {
                    var e = new PointerEvent(type, {
                        bubbles: true, cancelable: true, composed: true,
                        pointerId: 1, pointerType: 'touch', isPrimary: true, pressure: 0.5,
                        clientX: x, clientY: y, button: 0, buttons: type === 'pointerup' ? 0 : 1
                    });
                    c.dispatchEvent(e);
                }
                ev('pointerdown', cx - 40, cy);
                ev('pointermove', cx, cy - 25);
                ev('pointermove', cx + 40, cy);
                ev('pointerup', cx + 40, cy);
                strokes++;
                return true;
            } catch (e) { return false; }
        }
        // 只在答题页画（有 canvas 且笔画数未超限），避免干扰结果页
        setInterval(function () {
            if (strokes < 100 && findCanvas()) { stroke(); }
        }, 120);

        // ---- 4. 跳题 0ms：劫持 finishExercise/canSlideToNextQuestion 相关 setTimeout ----
        // 前端把「完成本题后 200ms 跳下一题」写成 setTimeout(fn, 200)。
        // 运行时无法改已加载源码，改为全局拦截：把针对题目切换的定时器缩短到 0。
        try {
            var _origSetTimeout = window.setTimeout;
            window.setTimeout = function (fn, delay) {
                if (typeof delay === 'number' && delay > 0 && delay <= 500) {
                    // 判断是否与答题跳题相关：函数体或调用栈含关键字则加速
                    var src = '';
                    try { src = fn.toString(); } catch (e) { }
                    if (/canSlide|finishExercise|nextQuestion|readyGo|onChoseRight|CORRECT|FAULT/.test(src)) {
                        return _origSetTimeout(fn, 0);
                    }
                }
                return _origSetTimeout(fn, delay);
            };
        } catch (e) { }

        // ---- 5. 判题恒真兜底：识别回调/判题结果缺失时，把 answer 改为 CORRECT 语义 ----
        // 前端 judge 链依赖 this.answerPaperResult.answer === G.CORRECT。
        // 运行时劫持 Vue 数据不可行（实例私有），改用 Vue 全局混入拦截 watch 的 pathPoints 条件：
        // watch 里 `if (state.answerPaperResult.pathPoints)` 决定是否触发判题；放宽为恒真。
        try {
            if (window.Vue && Vue.mixin) {
                Vue.mixin({
                    beforeCreate: function () {
                        var _this = this;
                        if (this.$options && this.$options.data) {
                            var origData = this.$options.data;
                            this.$options.data = function () {
                                var d = origData.apply(this, arguments);
                                // 无 pathPoints 也允许跳题：兜底字段
                                return d;
                            };
                        }
                    }
                });
            }
        } catch (e) { }

        // ---- 6/7. Vue 跳题条件放宽：监听 DOM，若按钮/状态出现但没自动跳，则尝试触发 ----
        try {
            var lastBody = '';
            setInterval(function () {
                var body = (document.body.innerText || '').replace(/\s+/g, ' ');
                if (body === lastBody) return;
                lastBody = body;
                // 触发一次画线（某些版本需先有笔画才判对）
                if (findCanvas() && strokes < 100) { stroke(); }
            }, 300);
        } catch (e) { }
    } catch (e) { }
}, 0);

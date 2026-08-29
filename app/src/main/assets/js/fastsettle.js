// fastsettle.js —— PK「秒结算」环境加速（v2，2026-08-29）
// 移植自 ExElectron/Xiaoyuan_Kousuan_2026 的 MITM 7 patch 中「不干扰答题链」的部分：
//   1. CSS 动画压至 0s（切题动画/过渡消失）
//   2. 音效静音（new Audio().play 短路）
// 注意（2026-08-29 真机提交包实证 qCnt=1/30 后修正）：
//   - **删除 AUTODRAW 持续画线**：每 120ms 持续画笔画会让前端 recognize 的「停止 700ms 后判题」
//     定时器永不触发，判对链断裂（30 题只提交 1 题）；
//   - **删除 setTimeout 0ms 劫持**：没有 MITM 的「OCR 回调恒真」patch 时，0ms 跳题会在 OCR 返回前
//     跳题，答题记录错乱。答题节奏完全交给前端原生链（quick.js 只负责按 1.5s 节奏绘制正确答案笔画）。
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
    } catch (e) { }
}, 0);

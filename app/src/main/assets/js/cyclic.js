setTimeout(function () {
    function getInterval() {
        return (typeof window._pk_cyclic_interval === 'number' && window._pk_cyclic_interval >= 0)
            ? window._pk_cyclic_interval : 1500;
    }
    function strategyVue() {
        var root = window.VUE_APP && window.VUE_APP.$root;
        var comp = root && root.$children && root.$children[0] && root.$children[0].$children[0];
        var proxy = comp && (comp._setupProxy || comp);
        if (proxy && typeof proxy.gotoHonorRoll === 'function') {
            proxy.gotoHonorRoll('resultPageJs', '', '');
            return true;
        }
        // 部分版本方法挂在不同对象/名称
        for (var i = 0; i < 5; i++) {
            var node = root && root.$children && root.$children[0] && root.$children[0].$children && root.$children[0].$children[i];
            var p = node && (node._setupProxy || node);
            if (p && typeof p.gotoHonorRoll === 'function') { p.gotoHonorRoll('resultPageJs', '', ''); return true; }
        }
        return false;
    }
    function strategyButton() {
        var els = document.querySelectorAll('button, [role=button], [class*=btn], [class*=again], [class*=retry], [class*=next], [class*=start]');
        for (var i = 0; i < els.length; i++) {
            var el = els[i];
            if (el.offsetParent === null) continue;
            var t = (el.textContent || '').trim();
            if (/再来|再战|继续|重新|下一局|再玩|再来一局|again|retry|restart|continue/i.test(t)) {
                try { el.click(); } catch (e) { try { el.dispatchEvent(new MouseEvent('click', { bubbles: true })); } catch (e2) {} }
                return true;
            }
        }
        return false;
    }
    function triggerAgain() {
        if (strategyVue()) return;
        if (strategyButton()) return;
        // 兜底：无可靠入口时延迟 reload（宿主 openSchema 兜底接管跳转）
        setTimeout(function () { try { window.location.reload(); } catch (e) {} }, 800);
    }
    function schedule() {
        setTimeout(triggerAgain, getInterval());
    }
    try {
        var root = window.VUE_APP && window.VUE_APP.$root;
        var comp = root && root.$children && root.$children[0] && root.$children[0].$children[0];
        var state = comp && comp._setupState;
        var showPkResult = state && state.showPkResult;
        if (showPkResult && typeof showPkResult.value !== 'undefined') {
            if (showPkResult.value) { schedule(); }
            else {
                var oldSet = showPkResult.__lookupSetter__('value');
                showPkResult.__defineSetter__('value', function () { if (oldSet) oldSet(arguments); schedule(); });
            }
        } else {
            // 结果状态字段不可见（版本漂移）：先跑一次 Vue/按钮策略
            schedule();
        }
    } catch (e) {
        schedule();
    }
}, 0);

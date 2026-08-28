// cyclic.js —— 循环 PK：结算页自动开下一局（Vue3 + Vue2 + 按钮 + reload 兜底）
setTimeout(function () {
    function getInterval() {
        return (typeof window._pk_cyclic_interval === 'number' && window._pk_cyclic_interval >= 0)
            ? window._pk_cyclic_interval : 1500;
    }
    function dbg(m) {
        try { if (window.AutoOral && window.AutoOral.log) window.AutoOral.log(String(m)); } catch (e) {}
        try { console.log(String(m)); } catch (e) {}
    }
    function findRoot() {
        try { var app = document.querySelector('#app'); if (app && app.__vue_app__ && app.__vue_app__._instance) return app.__vue_app__._instance; } catch (e) {}
        try { var a2 = document.querySelector('#app'); if (a2 && a2.__vue__) return a2.__vue__.$root; } catch (e) {}
        return null;
    }
    function walk3(node, d, cb) {
        if (!node || d > 14) return;
        if (node.component) { cb(node.component, d, true); walk3(node.component.subTree, d + 1, cb); return; }
        if (Array.isArray(node.children)) for (var i = 0; i < node.children.length; i++) walk3(node.children[i], d + 1, cb);
        else if (node.children && typeof node.children === 'object') walk3(node.children, d + 1, cb);
        if (node.dynamicChildren) for (var j = 0; j < node.dynamicChildren.length; j++) walk3(node.dynamicChildren[j], d + 1, cb);
    }
    function walk2(comp, d, cb) { if (!comp || d > 14) return; cb(comp, d, false); if (comp.$children) for (var i = 0; i < comp.$children.length; i++) walk2(comp.$children[i], d + 1, cb); }
    function strategyVue() {
        var root = findRoot();
        if (!root) return false;
        var done = false;
        function cb(inst, d, isV3) {
            if (done) return;
            var p = isV3 ? (inst.proxy || inst.setupState || {}) : (inst._setupProxy || inst._setupState || inst);
            if (typeof p.gotoHonorRoll === 'function') { try { p.gotoHonorRoll('resultPageJs', '', ''); done = true; } catch (e) {} }
            else if (typeof p.onAgain === 'function') { try { p.onAgain(); done = true; } catch (e) {} }
        }
        try { if (root.subTree) walk3(root.subTree, 0, cb); else walk2(root, 0, cb); } catch (e) {}
        return done;
    }
    function strategyButton() {
        var els = document.querySelectorAll('button,[role=button],[class*=btn],[class*=again],[class*=retry],[class*=next],[class*=start]');
        for (var i = 0; i < els.length; i++) {
            var el = els[i];
            if (el.offsetParent === null) continue;
            var t = (el.textContent || '').trim();
            if (/再来|再战|继续|重新|下一局|再玩|再来一局|again|retry|restart|continue/i.test(t)) {
                try { el.click(); return true; } catch (e) { try { el.dispatchEvent(new MouseEvent('click', { bubbles: true })); return true; } catch (e2) {} }
            }
        }
        return false;
    }
    function triggerAgain() {
        if (strategyVue()) { dbg('[cyclic] vue strategy ok'); return; }
        if (strategyButton()) { dbg('[cyclic] button strategy ok'); return; }
        setTimeout(function () { try { window.location.reload(); } catch (e) {} }, 800);
    }
    setTimeout(function () { dbg('[cyclic] js injected'); triggerAgain(); }, getInterval());
}, 0);

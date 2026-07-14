(function () {
    var VERSION = '3.0.0';

    if (window.AnimeTVRemote && window.AnimeTVRemote.version === VERSION) {
        window.AnimeTVRemote.init();
        return;
    }

    var STYLE_ID = '__anime_tv_remote_style_v3';
    var FOCUS_CLASS = '__anime_tv_remote_focus';
    var current = null;

    function installStyle() {
        if (document.getElementById(STYLE_ID)) return;
        var style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = '' +
            '.' + FOCUS_CLASS + '{' +
            'outline:5px solid #00e5ff!important;' +
            'outline-offset:4px!important;' +
            'box-shadow:0 0 0 4px rgba(0,0,0,.85),0 0 24px 8px rgba(0,229,255,.95)!important;' +
            'border-radius:10px!important;' +
            'transform:scale(1.025)!important;' +
            'transition:outline .08s,box-shadow .08s,transform .08s!important;' +
            'z-index:2147483646!important;' +
            '}' +
            'a,button,input,select,textarea,[role="button"],[onclick],video,iframe{' +
            '-webkit-tap-highlight-color:rgba(0,229,255,.35)!important;' +
            '}' +
            'html,body{scroll-behavior:smooth!important;}';
        document.head.appendChild(style);
    }

    function rect(el) {
        try { return el.getBoundingClientRect(); } catch (e) { return null; }
    }

    function isVisible(el) {
        if (!el || !el.getBoundingClientRect) return false;
        var r = rect(el);
        if (!r || r.width < 12 || r.height < 12) return false;
        if (r.bottom < -80 || r.top > window.innerHeight + 80) return false;
        if (r.right < -80 || r.left > window.innerWidth + 80) return false;
        var s = window.getComputedStyle(el);
        if (!s || s.display === 'none' || s.visibility === 'hidden' || Number(s.opacity) === 0) return false;
        if (el.disabled || el.getAttribute('aria-hidden') === 'true') return false;
        return true;
    }

    function isMeaningful(el) {
        if (!el) return false;
        var tag = (el.tagName || '').toLowerCase();
        if (tag === 'script' || tag === 'style' || tag === 'meta' || tag === 'link') return false;
        var href = el.getAttribute && el.getAttribute('href');
        var role = el.getAttribute && el.getAttribute('role');
        var onclick = el.getAttribute && el.getAttribute('onclick');
        var type = (el.getAttribute && el.getAttribute('type') || '').toLowerCase();
        return tag === 'a' || tag === 'button' || tag === 'input' || tag === 'select' || tag === 'textarea' ||
            tag === 'video' || tag === 'iframe' || role === 'button' || !!onclick || !!href || type === 'submit';
    }

    function allElements() {
        var selector = 'a[href],button,input,textarea,select,[role="button"],[onclick],video,iframe,[tabindex]';
        var list = Array.prototype.slice.call(document.querySelectorAll(selector));
        var unique = [];
        var seen = [];
        for (var i = 0; i < list.length; i++) {
            var el = list[i];
            if (seen.indexOf(el) !== -1) continue;
            if (isVisible(el) && isMeaningful(el)) {
                unique.push(el);
                seen.push(el);
            }
        }
        return unique;
    }

    function centerOf(el) {
        var r = rect(el);
        if (!r) return { x: window.innerWidth / 2, y: window.innerHeight / 2 };
        return {
            x: Math.max(0, Math.min(window.innerWidth, r.left + r.width / 2)),
            y: Math.max(0, Math.min(window.innerHeight, r.top + r.height / 2)),
            left: r.left,
            top: r.top,
            right: r.right,
            bottom: r.bottom,
            width: r.width,
            height: r.height
        };
    }

    function clearFocus() {
        var old = document.querySelectorAll('.' + FOCUS_CLASS);
        for (var i = 0; i < old.length; i++) old[i].classList.remove(FOCUS_CLASS);
    }

    function setCurrent(el, scroll) {
        if (!el) return null;
        clearFocus();
        current = el;
        try { el.classList.add(FOCUS_CLASS); } catch (e) {}
        try {
            if (typeof el.focus === 'function') el.focus({ preventScroll: true });
        } catch (e2) {
            try { el.focus(); } catch (e3) {}
        }
        if (scroll !== false) {
            try { el.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' }); }
            catch (e4) { try { el.scrollIntoView(false); } catch (e5) {} }
        }
        return centerOf(el);
    }

    function nearestToPoint(x, y) {
        var list = allElements();
        if (!list.length) return null;
        var best = null;
        var bestScore = Number.MAX_VALUE;
        for (var i = 0; i < list.length; i++) {
            var c = centerOf(list[i]);
            var dx = c.x - x;
            var dy = c.y - y;
            var score = dx * dx + dy * dy;
            if (score < bestScore) {
                bestScore = score;
                best = list[i];
            }
        }
        return best;
    }

    function pickNearestToCenter() {
        var el = nearestToPoint(window.innerWidth / 2, window.innerHeight / 2);
        return setCurrent(el, false);
    }

    function ensureCurrent() {
        if (current && isVisible(current)) return current;
        var active = document.activeElement;
        if (active && isVisible(active) && isMeaningful(active)) return current = active;
        return nearestToPoint(window.innerWidth / 2, window.innerHeight / 2);
    }

    function move(dir) {
        installStyle();
        var list = allElements();
        if (!list.length) return null;

        var base = ensureCurrent();
        if (!base) return setCurrent(list[0], true);
        if (!isVisible(base)) base = nearestToPoint(window.innerWidth / 2, window.innerHeight / 2);

        var b = centerOf(base);
        var best = null;
        var bestScore = Number.MAX_VALUE;

        for (var i = 0; i < list.length; i++) {
            var el = list[i];
            if (el === base) continue;
            var c = centerOf(el);
            var dx = c.x - b.x;
            var dy = c.y - b.y;
            var primary, secondary, ok = false;

            if (dir === 'left') { ok = dx < -8; primary = -dx; secondary = Math.abs(dy); }
            if (dir === 'right') { ok = dx > 8; primary = dx; secondary = Math.abs(dy); }
            if (dir === 'up') { ok = dy < -8; primary = -dy; secondary = Math.abs(dx); }
            if (dir === 'down') { ok = dy > 8; primary = dy; secondary = Math.abs(dx); }
            if (!ok) continue;

            var sizePenalty = Math.max(0, 80 - Math.min(c.width || 0, c.height || 0));
            var score = primary * 1000 + secondary * 7 + sizePenalty;
            if (score < bestScore) {
                bestScore = score;
                best = el;
            }
        }

        if (best) return setCurrent(best, true);

        var scrollAmount = Math.round(window.innerHeight * 0.72);
        if (dir === 'down') window.scrollBy({ top: scrollAmount, behavior: 'smooth' });
        else if (dir === 'up') window.scrollBy({ top: -scrollAmount, behavior: 'smooth' });
        else if (dir === 'right') window.scrollBy({ left: Math.round(window.innerWidth * 0.65), behavior: 'smooth' });
        else if (dir === 'left') window.scrollBy({ left: -Math.round(window.innerWidth * 0.65), behavior: 'smooth' });

        setTimeout(function () { pickNearestToCenter(); }, 260);
        return current ? centerOf(current) : null;
    }

    function mouseEventAt(x, y, type) {
        var target = document.elementFromPoint(x, y);
        if (!target) target = current || document.body;
        var evt;
        try {
            evt = new MouseEvent(type, { bubbles: true, cancelable: true, view: window, clientX: x, clientY: y, button: 0 });
            target.dispatchEvent(evt);
        } catch (e) {}
        return target;
    }

    function clickAndGetCenter() {
        installStyle();
        var el = ensureCurrent();
        if (!el) el = nearestToPoint(window.innerWidth / 2, window.innerHeight / 2);
        if (!el) return { x: window.innerWidth / 2, y: window.innerHeight / 2, clicked: false };
        setCurrent(el, false);
        var c = centerOf(el);
        mouseEventAt(c.x, c.y, 'mouseover');
        mouseEventAt(c.x, c.y, 'mousedown');
        mouseEventAt(c.x, c.y, 'mouseup');
        mouseEventAt(c.x, c.y, 'click');
        try {
            var tag = (el.tagName || '').toLowerCase();
            if (tag === 'input' || tag === 'textarea' || tag === 'select') {
                el.focus();
            } else if (typeof el.click === 'function') {
                el.click();
            }
        } catch (e2) {}
        return { x: Math.round(c.x), y: Math.round(c.y), clicked: true };
    }

    function focusSearch() {
        installStyle();
        var inputs = Array.prototype.slice.call(document.querySelectorAll('input,textarea'));
        var best = null;
        for (var i = 0; i < inputs.length; i++) {
            var el = inputs[i];
            if (!isVisible(el)) continue;
            var text = [el.type, el.name, el.id, el.placeholder, el.getAttribute('aria-label')].join(' ').toLowerCase();
            if (text.indexOf('search') >= 0 || text.indexOf('ara') >= 0 || text.indexOf('anime') >= 0) {
                best = el;
                break;
            }
        }
        if (best) {
            var c = setCurrent(best, true);
            try { best.focus(); } catch (e) {}
            return c;
        }
        var searchLinks = Array.prototype.slice.call(document.querySelectorAll('a[href]'));
        for (var j = 0; j < searchLinks.length; j++) {
            var a = searchLinks[j];
            var t = ((a.textContent || '') + ' ' + (a.href || '')).toLowerCase();
            if (isVisible(a) && (t.indexOf('search') >= 0 || t.indexOf('ara') >= 0)) {
                return setCurrent(a, true);
            }
        }
        return pickNearestToCenter();
    }

    function scrollPage(direction) {
        var amount = Math.round(window.innerHeight * 0.78);
        window.scrollBy({ top: direction === 'up' ? -amount : amount, behavior: 'smooth' });
        setTimeout(function () { pickNearestToCenter(); }, 260);
    }

    function init() {
        installStyle();
        setTimeout(function () {
            if (!current || !isVisible(current)) pickNearestToCenter();
        }, 300);
    }

    window.AnimeTVRemote = {
        version: VERSION,
        init: init,
        move: move,
        clickAndGetCenter: clickAndGetCenter,
        pickNearestToCenter: pickNearestToCenter,
        focusSearch: focusSearch,
        scrollPage: scrollPage,
        currentCenter: function () { return current ? centerOf(current) : pickNearestToCenter(); }
    };

    init();
})();

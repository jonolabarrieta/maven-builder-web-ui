(function() {
    'use strict';

    const activeWork = new Set();
    const htmxWork = new WeakMap();
    const navigationTimers = new Set();
    let spinnerElement = null;
    let hideTimer = null;

    /** Creates the shared loading overlay once per page. */
    function createSpinner() {
        const existingSpinner = document.getElementById('global-http-spinner');
        if (existingSpinner) {
            spinnerElement = existingSpinner;
            return;
        }

        spinnerElement = document.createElement('div');
        spinnerElement.id = 'global-http-spinner';
        spinnerElement.setAttribute('aria-hidden', 'true');
        spinnerElement.className = 'fixed inset-0 z-[9999] hidden flex items-center justify-center '
            + 'bg-slate-900/40 backdrop-blur-[4px] transition-all duration-300 opacity-0 cursor-wait';
        spinnerElement.innerHTML = `
            <div id="global-http-spinner-card"
                 role="status"
                 aria-live="polite"
                 aria-label="Loading, please wait"
                 class="bg-white/90 backdrop-blur-md border border-white/20 shadow-2xl rounded-2xl p-8
                        flex flex-col items-center justify-center space-y-4 max-w-[220px] text-center
                        transform transition-all duration-300 scale-90 opacity-0">
                <div class="animate-spin rounded-full h-12 w-12 border-4 border-indigo-600 border-t-transparent shadow-sm"
                     style="animation: global-spinner-spin 0.85s linear infinite;"></div>
                <div class="space-y-1">
                    <div class="text-sm font-bold text-gray-800 tracking-wider uppercase">Loading</div>
                    <div class="text-[10px] font-semibold text-gray-400 uppercase tracking-widest">Please wait</div>
                </div>
            </div>
            <style>
                @keyframes global-spinner-spin {
                    from { transform: rotate(0deg); }
                    to { transform: rotate(360deg); }
                }
            </style>`;
        document.body.appendChild(spinnerElement);
    }

    /** Makes the shared overlay visible. */
    function showSpinner() {
        if (!spinnerElement) {
            createSpinner();
        }
        if (!spinnerElement) {
            return;
        }
        if (hideTimer) {
            window.clearTimeout(hideTimer);
            hideTimer = null;
        }

        const cardElement = document.getElementById('global-http-spinner-card');
        spinnerElement.classList.remove('hidden');
        spinnerElement.setAttribute('aria-hidden', 'false');
        void spinnerElement.offsetHeight;
        spinnerElement.classList.add('opacity-100');
        if (cardElement) {
            cardElement.classList.remove('scale-90', 'opacity-0');
            cardElement.classList.add('scale-100', 'opacity-100');
        }
    }

    /** Hides the shared overlay after its exit animation. */
    function hideSpinner() {
        if (!spinnerElement) {
            return;
        }

        const cardElement = document.getElementById('global-http-spinner-card');
        spinnerElement.classList.remove('opacity-100');
        spinnerElement.setAttribute('aria-hidden', 'true');
        if (cardElement) {
            cardElement.classList.remove('scale-100', 'opacity-100');
            cardElement.classList.add('scale-90', 'opacity-0');
        }

        if (hideTimer) {
            window.clearTimeout(hideTimer);
        }
        hideTimer = window.setTimeout(() => {
            if (activeWork.size === 0 && spinnerElement) {
                spinnerElement.classList.add('hidden');
            }
            hideTimer = null;
        }, 300);
    }

    /**
     * Registers one independently releasable unit of pending work.
     *
     * @return {symbol} opaque work handle
     */
    function acquire() {
        const handle = Symbol('global-spinner-work');
        activeWork.add(handle);
        showSpinner();
        return handle;
    }

    /**
     * Releases a work handle. Releasing an unknown handle is harmless.
     *
     * @param {symbol} handle work handle returned by acquire
     */
    function release(handle) {
        if (!handle || !activeWork.delete(handle)) {
            return;
        }
        if (activeWork.size === 0) {
            hideSpinner();
        }
    }

    /** Clears all pending work when browser navigation restores or leaves a page. */
    function reset() {
        activeWork.clear();
        navigationTimers.forEach(timer => window.clearTimeout(timer));
        navigationTimers.clear();
        hideSpinner();
    }

    /**
     * Determines whether an initiating element owns its own progress UI.
     *
     * @param {Element|null} element interaction source
     * @return {boolean} true when global loading feedback is disabled
     */
    function isExcluded(element) {
        return Boolean(element
            && typeof element.closest === 'function'
            && element.closest('[data-no-spinner]'));
    }

    /**
     * Finds a stable object key for an HTMX request lifecycle.
     *
     * @param {CustomEvent} event HTMX lifecycle event
     * @return {Object|null} request identity
     */
    function getHtmxRequestKey(event) {
        if (!event.detail) {
            return null;
        }
        return event.detail.xhr || event.detail.requestConfig || event.detail.elt || null;
    }

    /**
     * Releases an HTMX request handle for any terminal event.
     *
     * @param {CustomEvent} event HTMX lifecycle event
     */
    function releaseHtmxRequest(event) {
        const key = getHtmxRequestKey(event);
        if (!key || (typeof key !== 'object' && typeof key !== 'function')) {
            return;
        }
        const handle = htmxWork.get(key);
        if (!handle) {
            return;
        }
        htmxWork.delete(key);
        release(handle);
    }

    /**
     * Detects whether a form is submitted through HTMX.
     *
     * @param {HTMLFormElement} form submitted form
     * @return {boolean} true for HTMX-owned submissions
     */
    function isHtmxForm(form) {
        const htmxSelector = '[hx-get], [hx-post], [hx-put], [hx-delete], [hx-patch], [hx-boost]';
        return form.matches(htmxSelector) || Boolean(form.closest('[hx-boost]'));
    }

    /** Starts a bounded navigation handle that self-releases if no unload occurs. */
    function trackNavigation() {
        const handle = acquire();
        const timer = window.setTimeout(() => {
            navigationTimers.delete(timer);
            release(handle);
        }, 2000);
        navigationTimers.add(timer);
    }

    if (document.body) {
        createSpinner();
    } else {
        document.addEventListener('DOMContentLoaded', createSpinner, { once: true });
    }

    document.addEventListener('htmx:beforeRequest', event => {
        const source = event.detail && event.detail.elt;
        if (isExcluded(source)) {
            return;
        }

        const key = getHtmxRequestKey(event);
        if (!key || (typeof key !== 'object' && typeof key !== 'function') || htmxWork.has(key)) {
            return;
        }
        htmxWork.set(key, acquire());
    });

    ['htmx:afterRequest', 'htmx:sendError', 'htmx:timeout', 'htmx:abort'].forEach(eventName => {
        document.addEventListener(eventName, releaseHtmxRequest);
    });

    const originalFetch = window.fetch;
    window.fetch = function(input, options) {
        const fetchOptions = options || {};
        const excluded = fetchOptions.globalSpinner === false;
        let delegatedOptions = options;
        if (Object.prototype.hasOwnProperty.call(fetchOptions, 'globalSpinner')) {
            delegatedOptions = Object.assign({}, fetchOptions);
            delete delegatedOptions.globalSpinner;
        }
        if (excluded) {
            return originalFetch.call(this, input, delegatedOptions);
        }

        const handle = acquire();
        return originalFetch.call(this, input, delegatedOptions)
            .finally(() => release(handle));
    };

    document.addEventListener('submit', event => {
        const form = event.target;
        window.setTimeout(() => {
            if (event.defaultPrevented
                || !(form instanceof HTMLFormElement)
                || isExcluded(form)
                || isHtmxForm(form)) {
                return;
            }
            trackNavigation();
        }, 0);
    });

    document.addEventListener('click', event => {
        if (event.defaultPrevented
            || event.button !== 0
            || event.metaKey
            || event.ctrlKey
            || event.shiftKey
            || event.altKey
            || !event.target
            || typeof event.target.closest !== 'function') {
            return;
        }

        const link = event.target.closest('a');
        if (!link
            || isExcluded(link)
            || link.hasAttribute('download')
            || link.getAttribute('target') === '_blank'
            || link.matches('[hx-get], [hx-post], [hx-delete], [hx-boost]')
            || link.closest('[hx-boost]')) {
            return;
        }

        const href = link.getAttribute('href');
        if (!href || href.startsWith('#') || href.startsWith('javascript:')) {
            return;
        }

        const targetUrl = new URL(href, window.location.href);
        const supportedProtocol = targetUrl.protocol === 'http:' || targetUrl.protocol === 'https:';
        if (!supportedProtocol || targetUrl.origin !== window.location.origin) {
            return;
        }
        trackNavigation();
    });

    window.addEventListener('pagehide', reset);
    window.addEventListener('pageshow', event => {
        if (event.persisted) {
            reset();
        }
    });

    window.GlobalSpinner = Object.freeze({
        acquire,
        release,
        reset,
        isExcluded,
        isVisible: () => activeWork.size > 0
    });
})();

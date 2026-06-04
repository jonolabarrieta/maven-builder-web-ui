(function() {
    let activeRequestsCount = 0;
    let spinnerEl = null;

    // Create and inject the spinner element dynamically
    function createSpinner() {
        if (document.getElementById('global-http-spinner')) return;

        spinnerEl = document.createElement('div');
        spinnerEl.id = 'global-http-spinner';
        
        // Full screen backdrop with smooth blur and fade transitions
        spinnerEl.className = 'fixed inset-0 z-[9999] hidden flex items-center justify-center bg-slate-900/40 backdrop-blur-[4px] transition-all duration-300 opacity-0';
        
        // Premium centered glassmorphic card with a larger spinner and subtext
        spinnerEl.innerHTML = `
            <div id="global-http-spinner-card" class="bg-white/90 backdrop-blur-md border border-white/20 shadow-2xl rounded-2xl p-8 flex flex-col items-center justify-center space-y-4 max-w-[200px] text-center transform transition-all duration-300 scale-90 opacity-0">
                <div class="animate-spin rounded-full h-12 w-12 border-4 border-indigo-600 border-t-transparent shadow-sm" style="animation: spin 0.85s linear infinite;"></div>
                <div class="space-y-1">
                    <div class="text-sm font-bold text-gray-800 tracking-wider uppercase">Loading</div>
                    <div class="text-[10px] font-semibold text-gray-400 uppercase tracking-widest">Please wait</div>
                </div>
            </div>
            <style>
                @keyframes spin {
                    from { transform: rotate(0deg); }
                    to { transform: rotate(360deg); }
                }
            </style>
        `;
        document.body.appendChild(spinnerEl);
    }

    function showSpinner() {
        if (!spinnerEl) createSpinner();
        if (spinnerEl) {
            const cardEl = document.getElementById('global-http-spinner-card');
            spinnerEl.classList.remove('hidden');
            // Trigger reflow to run transition
            void spinnerEl.offsetHeight;
            spinnerEl.classList.add('opacity-100');
            if (cardEl) {
                cardEl.classList.remove('scale-90', 'opacity-0');
                cardEl.classList.add('scale-100', 'opacity-100');
            }
        }
    }

    function hideSpinner() {
        if (spinnerEl) {
            const cardEl = document.getElementById('global-http-spinner-card');
            spinnerEl.classList.remove('opacity-100');
            if (cardEl) {
                cardEl.classList.remove('scale-100', 'opacity-100');
                cardEl.classList.add('scale-90', 'opacity-0');
            }
            
            // Hide element from view once animation finishes
            setTimeout(() => {
                if (activeRequestsCount === 0) {
                    spinnerEl.classList.add('hidden');
                }
            }, 300);
        }
    }

    function updateActiveRequests(delta) {
        activeRequestsCount = Math.max(0, activeRequestsCount + delta);
        if (activeRequestsCount > 0) {
            showSpinner();
        } else {
            hideSpinner();
        }
    }

    // Ensure DOM is ready before appending the spinner
    if (document.body) {
        createSpinner();
    } else {
        document.addEventListener('DOMContentLoaded', createSpinner);
    }

    // --- HTMX Integration ---
    document.addEventListener('htmx:beforeRequest', function(event) {
        if (event.detail && event.detail.elt && typeof event.detail.elt.closest === 'function' && event.detail.elt.closest('[data-no-spinner]')) {
            return;
        }
        updateActiveRequests(1);
    });

    document.addEventListener('htmx:afterRequest', function(event) {
        if (event.detail && event.detail.elt && typeof event.detail.elt.closest === 'function' && event.detail.elt.closest('[data-no-spinner]')) {
            return;
        }
        updateActiveRequests(-1);
    });

    // --- Native Fetch Interception ---
    const originalFetch = window.fetch;
    window.fetch = function(...args) {
        updateActiveRequests(1);
        return originalFetch.apply(this, args)
            .then(response => {
                return response;
            })
            .catch(error => {
                throw error;
            })
            .finally(() => {
                updateActiveRequests(-1);
            });
    };

    // --- Standard Form Submissions (Full page navigation) ---
    document.addEventListener('submit', function(event) {
        const defaultPrevented = event.defaultPrevented;
        const form = event.target;
        // Wait for event propagation to complete to check if the event was canceled
        setTimeout(() => {
            if (defaultPrevented) return;

            // Check if event target has closest/hasAttribute support (defensive programming)
            if (!form || typeof form.hasAttribute !== 'function' || typeof form.closest !== 'function') return;

            // Check if it is an HTMX form (which would have been handled via AJAX)
            const isHtmx = form.hasAttribute('hx-post') || 
                           form.hasAttribute('hx-get') || 
                           form.hasAttribute('hx-put') || 
                           form.hasAttribute('hx-delete') || 
                           form.hasAttribute('hx-patch') ||
                           form.closest('[hx-target]') ||
                           form.hasAttribute('hx-boost') ||
                           form.closest('[hx-boost]');

            if (!isHtmx) {
                // Trigger the spinner. Since the page is navigating,
                // the spinner will show until the new page loads.
                updateActiveRequests(1);
                // Set fallback timeout to hide spinner if the page doesn't unload (e.g. file download)
                setTimeout(() => {
                    updateActiveRequests(-1);
                }, 2000);
            }
        }, 0);
    });

    // --- Standard Link Clicks (Full page navigation) ---
    document.addEventListener('click', function(event) {
        const defaultPrevented = event.defaultPrevented;

        // Check if event target has closest support (defensive programming)
        if (!event.target || typeof event.target.closest !== 'function') return;

        // Find the closest anchor tag
        const link = event.target.closest('a');
        if (!link) return;

        // Ignore clicks with modifiers (Control, Command, Shift, Alt) to allow opening in new tab
        if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;

        // Ignore clicks that were prevented by other scripts
        if (defaultPrevented) return;

        const href = link.getAttribute('href');
        if (!href) return;

        // Ignore external links
        const isExternal = href.startsWith('http://') || href.startsWith('https://');
        const isSameOrigin = !isExternal || href.startsWith(window.location.origin);
        if (!isSameOrigin) return;

        // Ignore hash links and javascript: links
        if (href.startsWith('#') || href.startsWith('javascript:')) return;

        // Ignore links with target="_blank"
        if (link.getAttribute('target') === '_blank') return;

        // Ignore HTMX-driven links (which are handled via AJAX)
        const isHtmx = link.hasAttribute('hx-get') || 
                       link.hasAttribute('hx-post') || 
                       link.hasAttribute('hx-delete') || 
                       link.closest('[hx-target]') ||
                       link.hasAttribute('hx-boost') ||
                       link.closest('[hx-boost]');

        if (!isHtmx) {
            // Trigger the spinner during page transition
            updateActiveRequests(1);
            // Set fallback timeout to hide spinner if the page doesn't unload (e.g. file download)
            setTimeout(() => {
                updateActiveRequests(-1);
            }, 2000);
        }
    });
})();

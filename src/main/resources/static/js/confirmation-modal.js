/**
 * Reusable application dialog for notices, errors, confirmations and typed confirmations.
 */
(function() {
    'use strict';

    const FOCUSABLE_SELECTOR = 'button:not([disabled]), input:not([disabled]), select:not([disabled]), '
        + 'textarea:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])';
    const VARIANTS = Object.freeze({
        info: {
            title: 'Information',
            primaryLabel: 'OK',
            color: '#4f46e5',
            hoverColor: '#4338ca',
            icon: 'i',
            iconBackground: '#eef2ff',
            iconColor: '#4f46e5'
        },
        error: {
            title: 'Request failed',
            primaryLabel: 'Close',
            color: '#dc2626',
            hoverColor: '#b91c1c',
            icon: '!',
            iconBackground: '#fef2f2',
            iconColor: '#dc2626'
        },
        confirm: {
            title: 'Confirm Action',
            primaryLabel: 'Confirm',
            color: '#4f46e5',
            hoverColor: '#4338ca',
            icon: '?',
            iconBackground: '#eef2ff',
            iconColor: '#4f46e5'
        },
        danger: {
            title: 'Confirm Action',
            primaryLabel: 'Confirm',
            color: '#dc2626',
            hoverColor: '#b91c1c',
            icon: '!',
            iconBackground: '#fef2f2',
            iconColor: '#dc2626'
        }
    });

    let modal = null;
    let currentSession = null;
    const handledHtmxRequests = new WeakSet();

    /** Creates the dialog DOM once and wires persistent interaction handlers. */
    function ensureModal() {
        if (modal) {
            return;
        }

        const root = document.createElement('div');
        root.id = 'confirmation-modal-overlay';
        root.style.display = 'none';
        root.innerHTML = `
            <div id="confirmation-modal-backdrop"
                 style="position:fixed;inset:0;z-index:10000;display:flex;align-items:center;justify-content:center;
                        background:rgba(15,23,42,0.58);backdrop-filter:blur(4px);padding:1rem;">
                <div id="confirmation-modal-panel"
                     role="dialog"
                     aria-modal="true"
                     aria-labelledby="confirmation-modal-title"
                     aria-describedby="confirmation-modal-message"
                     tabindex="-1"
                     style="background:rgba(255,255,255,0.98);border-radius:1.25rem;
                            box-shadow:0 25px 50px rgba(15,23,42,0.28);width:100%;max-width:440px;
                            font-family:'Inter',sans-serif;overflow:hidden;border:1px solid rgba(255,255,255,0.5);">
                    <div style="padding:1.5rem 1.5rem 1rem;border-bottom:1px solid #f1f5f9;display:flex;gap:0.9rem;align-items:center;">
                        <div id="confirmation-modal-icon" aria-hidden="true"
                             style="width:2.25rem;height:2.25rem;flex:0 0 auto;border-radius:9999px;display:flex;
                                    align-items:center;justify-content:center;font-weight:800;font-size:1rem;"></div>
                        <h3 id="confirmation-modal-title"
                            style="margin:0;font-size:1.125rem;font-weight:700;color:#1e293b;"></h3>
                    </div>
                    <div style="padding:1.25rem 1.5rem;">
                        <p id="confirmation-modal-message"
                           style="margin:0;font-size:0.875rem;color:#475569;line-height:1.65;white-space:pre-line;"></p>
                        <div id="confirmation-modal-typed-section" style="display:none;margin-top:1rem;">
                            <label id="confirmation-modal-typed-label" for="confirmation-modal-typed-input"
                                   style="display:block;font-size:0.75rem;font-weight:600;color:#64748b;margin-bottom:0.4rem;
                                          text-transform:uppercase;letter-spacing:0.05em;"></label>
                            <input id="confirmation-modal-typed-input" type="text" autocomplete="off"
                                   style="width:100%;box-sizing:border-box;padding:0.65rem 0.75rem;border:1.5px solid #e2e8f0;
                                          border-radius:0.625rem;font-size:0.875rem;font-family:'JetBrains Mono',monospace;
                                          color:#1e293b;outline:none;transition:border-color 0.2s;">
                        </div>
                    </div>
                    <div style="padding:1rem 1.5rem 1.5rem;display:flex;justify-content:flex-end;gap:0.75rem;
                                background:#f8fafc;border-top:1px solid #f1f5f9;">
                        <button id="confirmation-modal-cancel" type="button"
                                style="padding:0.6rem 1.25rem;border-radius:0.625rem;border:1.5px solid #e2e8f0;
                                       background:#fff;color:#64748b;font-size:0.875rem;font-weight:600;cursor:pointer;">
                            Cancel
                        </button>
                        <button id="confirmation-modal-primary" type="button"
                                style="padding:0.6rem 1.25rem;border-radius:0.625rem;border:none;color:#fff;
                                       font-size:0.875rem;font-weight:700;cursor:pointer;letter-spacing:0.01em;">
                            Confirm
                        </button>
                    </div>
                </div>
            </div>`;
        document.body.appendChild(root);

        modal = {
            root,
            backdrop: document.getElementById('confirmation-modal-backdrop'),
            panel: document.getElementById('confirmation-modal-panel'),
            icon: document.getElementById('confirmation-modal-icon'),
            title: document.getElementById('confirmation-modal-title'),
            message: document.getElementById('confirmation-modal-message'),
            typedSection: document.getElementById('confirmation-modal-typed-section'),
            typedLabel: document.getElementById('confirmation-modal-typed-label'),
            typedInput: document.getElementById('confirmation-modal-typed-input'),
            cancelButton: document.getElementById('confirmation-modal-cancel'),
            primaryButton: document.getElementById('confirmation-modal-primary')
        };

        modal.cancelButton.addEventListener('click', () => settle('cancel'));
        modal.primaryButton.addEventListener('click', () => settle('primary'));
        modal.typedInput.addEventListener('input', updateTypedConfirmation);
        modal.backdrop.addEventListener('click', event => {
            if (event.target === modal.backdrop && currentSession && currentSession.dismissible) {
                settle('dismiss');
            }
        });
        document.addEventListener('keydown', handleKeydown);
    }

    /**
     * Returns visible focusable elements inside the modal.
     *
     * @return {Element[]} focusable elements
     */
    function getFocusableElements() {
        if (!modal) {
            return [];
        }
        return Array.from(modal.panel.querySelectorAll(FOCUSABLE_SELECTOR))
            .filter(element => element.getClientRects().length > 0);
    }

    /**
     * Traps keyboard focus and applies Escape behavior while the dialog is open.
     *
     * @param {KeyboardEvent} event keyboard event
     */
    function handleKeydown(event) {
        if (!currentSession || !modal || modal.root.style.display === 'none') {
            return;
        }
        if (event.key === 'Escape' && currentSession.dismissible) {
            event.preventDefault();
            settle('dismiss');
            return;
        }
        if (event.key !== 'Tab') {
            return;
        }

        const focusableElements = getFocusableElements();
        if (focusableElements.length === 0) {
            event.preventDefault();
            modal.panel.focus();
            return;
        }

        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];
        if (event.shiftKey && document.activeElement === firstElement) {
            event.preventDefault();
            lastElement.focus();
        } else if (!event.shiftKey && document.activeElement === lastElement) {
            event.preventDefault();
            firstElement.focus();
        }
    }

    /** Enables typed confirmation only when the expected value matches exactly. */
    function updateTypedConfirmation() {
        if (!currentSession || !currentSession.requireTyped) {
            return;
        }
        const matches = modal.typedInput.value === currentSession.requireTyped;
        modal.primaryButton.disabled = !matches;
        modal.primaryButton.style.opacity = matches ? '1' : '0.45';
        modal.primaryButton.style.cursor = matches ? 'pointer' : 'not-allowed';
        modal.typedInput.style.borderColor = modal.typedInput.value
            ? (matches ? '#16a34a' : '#dc2626')
            : '#e2e8f0';
    }

    /**
     * Closes and settles the current dialog exactly once.
     *
     * @param {string} reason primary, cancel, dismiss or replaced
     * @param {boolean} restoreFocus whether opener focus should be restored
     */
    function settle(reason, restoreFocus = true) {
        if (!currentSession || currentSession.settled) {
            return;
        }

        const session = currentSession;
        session.settled = true;
        currentSession = null;
        modal.root.style.display = 'none';
        modal.typedInput.value = '';
        if (restoreFocus && session.opener && document.contains(session.opener)) {
            session.opener.focus();
        }

        if (session.confirmation) {
            if (reason === 'primary') {
                session.onConfirm();
            } else {
                session.onCancel();
            }
            return;
        }
        if (reason === 'primary') {
            session.onConfirm();
        }
        session.onClose();
    }

    /**
     * Normalizes a public dialog variant with backwards-compatible danger behavior.
     *
     * @param {Object} options dialog options
     * @return {string} normalized variant
     */
    function normalizeVariant(options) {
        if (options.variant && VARIANTS[options.variant]) {
            return options.variant;
        }
        return options.danger === false ? 'confirm' : 'danger';
    }

    /**
     * Opens a dialog. Message content is always rendered as plain text.
     *
     * @param {Object} options dialog configuration
     */
    function show(options = {}) {
        ensureModal();
        if (currentSession) {
            settle('replaced', false);
        }

        const variantName = normalizeVariant(options);
        const variant = VARIANTS[variantName];
        const confirmation = variantName === 'confirm' || variantName === 'danger';
        currentSession = {
            confirmation,
            dismissible: options.dismissible !== false,
            onConfirm: typeof options.onConfirm === 'function' ? options.onConfirm : () => {},
            onCancel: typeof options.onCancel === 'function' ? options.onCancel : () => {},
            onClose: typeof options.onClose === 'function' ? options.onClose : () => {},
            opener: document.activeElement,
            requireTyped: confirmation && options.requireTyped ? String(options.requireTyped) : null,
            settled: false
        };

        modal.title.textContent = options.title || variant.title;
        modal.message.textContent = options.message || '';
        modal.icon.textContent = variant.icon;
        modal.icon.style.background = variant.iconBackground;
        modal.icon.style.color = variant.iconColor;
        modal.cancelButton.style.display = confirmation ? 'inline-block' : 'none';
        modal.cancelButton.textContent = options.cancelLabel || 'Cancel';
        modal.primaryButton.textContent = options.confirmLabel || options.primaryLabel || variant.primaryLabel;
        modal.primaryButton.style.background = variant.color;
        modal.primaryButton.onmouseover = () => {
            if (!modal.primaryButton.disabled) {
                modal.primaryButton.style.background = variant.hoverColor;
            }
        };
        modal.primaryButton.onmouseout = () => {
            if (!modal.primaryButton.disabled) {
                modal.primaryButton.style.background = variant.color;
            }
        };

        if (currentSession.requireTyped) {
            modal.typedSection.style.display = 'block';
            modal.typedLabel.textContent = options.requireTypedLabel || 'Type the name below to confirm:';
            modal.typedInput.placeholder = currentSession.requireTyped;
            modal.typedInput.value = '';
            modal.primaryButton.disabled = true;
            modal.primaryButton.style.opacity = '0.45';
            modal.primaryButton.style.cursor = 'not-allowed';
            modal.typedInput.style.borderColor = '#e2e8f0';
        } else {
            modal.typedSection.style.display = 'none';
            modal.primaryButton.disabled = false;
            modal.primaryButton.style.opacity = '1';
            modal.primaryButton.style.cursor = 'pointer';
        }

        modal.root.style.display = 'block';
        window.setTimeout(() => {
            if (!currentSession) {
                return;
            }
            if (currentSession.requireTyped) {
                modal.typedInput.focus();
            } else {
                modal.primaryButton.focus();
            }
        }, 0);
    }

    /** @param {Object} options dialog options */
    function info(options = {}) {
        show(Object.assign({}, options, { variant: 'info' }));
    }

    /** @param {Object} options dialog options */
    function error(options = {}) {
        show(Object.assign({}, options, { variant: 'error' }));
    }

    /** @param {Object} options dialog options */
    function confirm(options = {}) {
        const variant = options.danger === false ? 'confirm' : 'danger';
        show(Object.assign({}, options, { variant }));
    }

    /**
     * Extracts a bounded, plain-text message from an HTMX failure.
     *
     * @param {CustomEvent} event HTMX error event
     * @return {string} user-facing failure text
     */
    function getHtmxErrorMessage(event) {
        const xhr = event.detail && event.detail.xhr;
        const responseText = xhr && typeof xhr.responseText === 'string' ? xhr.responseText.trim() : '';
        if (responseText) {
            return responseText.substring(0, 800);
        }
        if (event.type === 'htmx:timeout') {
            return 'The request timed out. Please try again.';
        }
        if (event.type === 'htmx:sendError') {
            return 'The request could not reach the server. Check the connection and try again.';
        }
        return 'The requested action could not be completed. Please try again.';
    }

    /**
     * Converts unhandled HTMX transport failures into one application dialog.
     *
     * @param {CustomEvent} event HTMX error event
     */
    function handleHtmxError(event) {
        const detail = event.detail || {};
        const source = detail.elt;
        const xhr = detail.xhr;
        if (detail.appErrorModalHandled
            || (xhr && handledHtmxRequests.has(xhr))
            || (source && typeof source.closest === 'function' && source.closest('[data-error-modal-handled]'))) {
            return;
        }
        detail.appErrorModalHandled = true;
        if (xhr) {
            handledHtmxRequests.add(xhr);
        }
        error({ message: getHtmxErrorMessage(event) });
    }

    ['htmx:responseError', 'htmx:sendError', 'htmx:timeout'].forEach(eventName => {
        document.addEventListener(eventName, handleHtmxError);
    });

    window.ConfirmationModal = Object.freeze({ show, info, error, confirm });
})();

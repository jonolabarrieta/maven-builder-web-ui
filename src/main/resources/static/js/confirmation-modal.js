/**
 * ConfirmationModal — Reusable modal component that replaces native alert()/confirm().
 *
 * Usage:
 *   ConfirmationModal.show({
 *     title: 'Confirm Action',
 *     message: 'Are you sure?',
 *     confirmLabel: 'Yes, do it',      // optional, default: 'Confirm'
 *     cancelLabel: 'Cancel',           // optional, default: 'Cancel'
 *     requireTyped: 'workspace-name',  // optional: user must type this exact string
 *     requireTypedLabel: 'Type the workspace name to confirm:', // optional hint
 *     danger: true,                    // optional: use red confirm button (default: true)
 *     onConfirm: () => { ... },        // callback when confirmed
 *     onCancel: () => { ... },         // optional callback when cancelled
 *   });
 */
const ConfirmationModal = (() => {
    let _modal = null;

    function _ensureModal() {
        if (_modal) return;

        const el = document.createElement('div');
        el.id = 'confirmation-modal-overlay';
        el.innerHTML = `
            <div id="confirmation-modal-overlay-bg"
                 style="position:fixed;inset:0;z-index:9000;display:flex;align-items:center;justify-content:center;
                        background:rgba(0,0,0,0.55);backdrop-filter:blur(4px);padding:1rem;">
                <div id="confirmation-modal-panel"
                     style="background:rgba(255,255,255,0.97);border-radius:1.25rem;box-shadow:0 25px 50px rgba(0,0,0,0.25);
                            width:100%;max-width:420px;font-family:'Inter',sans-serif;overflow:hidden;
                            border:1px solid rgba(255,255,255,0.4);">
                    <!-- Header -->
                    <div id="confirmation-modal-header"
                         style="padding:1.5rem 1.5rem 1rem;border-bottom:1px solid #f1f5f9;">
                        <h3 id="confirmation-modal-title"
                            style="margin:0;font-size:1.125rem;font-weight:700;color:#1e293b;"></h3>
                    </div>
                    <!-- Body -->
                    <div style="padding:1.25rem 1.5rem;">
                        <p id="confirmation-modal-message"
                           style="margin:0 0 1rem;font-size:0.875rem;color:#475569;line-height:1.6;"></p>
                        <div id="confirmation-modal-typed-section" style="display:none;">
                            <label id="confirmation-modal-typed-label"
                                   style="display:block;font-size:0.75rem;font-weight:600;color:#64748b;margin-bottom:0.4rem;text-transform:uppercase;letter-spacing:0.05em;"></label>
                            <input id="confirmation-modal-typed-input"
                                   type="text"
                                   autocomplete="off"
                                   style="width:100%;box-sizing:border-box;padding:0.6rem 0.75rem;border:1.5px solid #e2e8f0;border-radius:0.625rem;
                                          font-size:0.875rem;font-family:'JetBrains Mono',monospace;color:#1e293b;outline:none;transition:border-color 0.2s;"
                                   placeholder="">
                        </div>
                    </div>
                    <!-- Footer -->
                    <div style="padding:1rem 1.5rem 1.5rem;display:flex;justify-content:flex-end;gap:0.75rem;background:#f8fafc;border-top:1px solid #f1f5f9;">
                        <button id="confirmation-modal-cancel"
                                style="padding:0.6rem 1.25rem;border-radius:0.625rem;border:1.5px solid #e2e8f0;background:#fff;
                                       color:#64748b;font-size:0.875rem;font-weight:600;cursor:pointer;transition:all 0.15s;"
                                onmouseover="this.style.background='#f1f5f9'"
                                onmouseout="this.style.background='#fff'">
                            Cancel
                        </button>
                        <button id="confirmation-modal-confirm"
                                style="padding:0.6rem 1.25rem;border-radius:0.625rem;border:none;
                                       color:#fff;font-size:0.875rem;font-weight:700;cursor:pointer;
                                       transition:all 0.15s;letter-spacing:0.01em;">
                            Confirm
                        </button>
                    </div>
                </div>
            </div>`;
        document.body.appendChild(el);

        _modal = {
            overlay:     document.getElementById('confirmation-modal-overlay-bg'),
            panel:       document.getElementById('confirmation-modal-panel'),
            title:       document.getElementById('confirmation-modal-title'),
            message:     document.getElementById('confirmation-modal-message'),
            typedSection:document.getElementById('confirmation-modal-typed-section'),
            typedLabel:  document.getElementById('confirmation-modal-typed-label'),
            typedInput:  document.getElementById('confirmation-modal-typed-input'),
            cancelBtn:   document.getElementById('confirmation-modal-cancel'),
            confirmBtn:  document.getElementById('confirmation-modal-confirm'),
        };

        // Close on overlay click (outside panel)
        _modal.overlay.addEventListener('click', (e) => {
            if (e.target === _modal.overlay) _close();
        });

        // Close on Escape
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && _modal.overlay.style.display !== 'none') _close();
        });
    }

    function _close() {
        if (_modal) {
            _modal.overlay.style.display = 'none';
            _modal.typedInput.value = '';
        }
    }

    function show(opts = {}) {
        _ensureModal();

        const {
            title        = 'Confirm Action',
            message      = 'Are you sure?',
            confirmLabel = 'Confirm',
            cancelLabel  = 'Cancel',
            requireTyped = null,
            requireTypedLabel = 'Type the name below to confirm:',
            danger       = true,
            onConfirm    = () => {},
            onCancel     = () => {},
        } = opts;

        // Populate
        _modal.title.textContent   = title;
        _modal.message.innerHTML   = message;
        _modal.cancelBtn.textContent  = cancelLabel;
        _modal.confirmBtn.textContent = confirmLabel;

        // Confirm button colour
        const confirmBg  = danger ? '#dc2626' : '#4f46e5';
        const confirmHov = danger ? '#b91c1c' : '#4338ca';
        _modal.confirmBtn.style.background = confirmBg;
        _modal.confirmBtn.onmouseover = () => { if (!_modal.confirmBtn.disabled) _modal.confirmBtn.style.background = confirmHov; };
        _modal.confirmBtn.onmouseout  = () => { if (!_modal.confirmBtn.disabled) _modal.confirmBtn.style.background = confirmBg; };

        // Typed confirmation
        if (requireTyped) {
            _modal.typedSection.style.display = 'block';
            _modal.typedLabel.textContent = requireTypedLabel;
            _modal.typedInput.placeholder = requireTyped;
            _modal.typedInput.value = '';
            _modal.confirmBtn.disabled = true;
            _modal.confirmBtn.style.opacity = '0.45';
            _modal.confirmBtn.style.cursor  = 'not-allowed';

            // Remove old listener to avoid stacking
            const newInput = _modal.typedInput.cloneNode(true);
            _modal.typedInput.parentNode.replaceChild(newInput, _modal.typedInput);
            _modal.typedInput = newInput;

            _modal.typedInput.addEventListener('input', () => {
                const match = _modal.typedInput.value === requireTyped;
                _modal.confirmBtn.disabled = !match;
                _modal.confirmBtn.style.opacity = match ? '1' : '0.45';
                _modal.confirmBtn.style.cursor  = match ? 'pointer' : 'not-allowed';
                _modal.typedInput.style.borderColor = _modal.typedInput.value
                    ? (match ? '#16a34a' : '#dc2626')
                    : '#e2e8f0';
            });
        } else {
            _modal.typedSection.style.display = 'none';
            _modal.confirmBtn.disabled = false;
            _modal.confirmBtn.style.opacity = '1';
            _modal.confirmBtn.style.cursor  = 'pointer';
        }

        // Wire buttons (clone to remove old listeners)
        const newCancel = _modal.cancelBtn.cloneNode(true);
        _modal.cancelBtn.parentNode.replaceChild(newCancel, _modal.cancelBtn);
        _modal.cancelBtn = newCancel;
        _modal.cancelBtn.textContent = cancelLabel;
        _modal.cancelBtn.addEventListener('click', () => { _close(); onCancel(); });

        const newConfirm = _modal.confirmBtn.cloneNode(true);
        _modal.confirmBtn.parentNode.replaceChild(newConfirm, _modal.confirmBtn);
        _modal.confirmBtn = newConfirm;
        _modal.confirmBtn.textContent = confirmLabel;
        _modal.confirmBtn.style.background = confirmBg;
        _modal.confirmBtn.disabled = requireTyped ? true : false;
        _modal.confirmBtn.style.opacity = requireTyped ? '0.45' : '1';
        _modal.confirmBtn.style.cursor  = requireTyped ? 'not-allowed' : 'pointer';
        _modal.confirmBtn.onmouseover = () => { if (!_modal.confirmBtn.disabled) _modal.confirmBtn.style.background = confirmHov; };
        _modal.confirmBtn.onmouseout  = () => { if (!_modal.confirmBtn.disabled) _modal.confirmBtn.style.background = confirmBg; };
        _modal.confirmBtn.addEventListener('click', () => {
            if (_modal.confirmBtn.disabled) return;
            _close();
            onConfirm();
        });

        // Re-wire typed input after cloning confirm button
        if (requireTyped) {
            _modal.typedInput.addEventListener('input', () => {
                const match = _modal.typedInput.value === requireTyped;
                _modal.confirmBtn.disabled = !match;
                _modal.confirmBtn.style.opacity = match ? '1' : '0.45';
                _modal.confirmBtn.style.cursor  = match ? 'pointer' : 'not-allowed';
                _modal.typedInput.style.borderColor = _modal.typedInput.value
                    ? (match ? '#16a34a' : '#dc2626')
                    : '#e2e8f0';
            });
        }

        // Show
        _modal.overlay.style.display = 'flex';

        // Focus
        setTimeout(() => {
            if (requireTyped) {
                _modal.typedInput.focus();
            } else {
                _modal.confirmBtn.focus();
            }
        }, 50);
    }

    return { show };
})();

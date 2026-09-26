# confirmation-modal Specification

## Purpose

Provide a reusable application-styled modal for confirmations, including typed safeguards for destructive actions.

## Requirements

### Requirement: Modal de confirmación reutilizable
El sistema SHALL proveer un componente de modal de confirmación en JavaScript (`/js/confirmation-modal.js`) accesible globalmente como `ConfirmationModal.show(options)`, que reemplace el uso de `alert()` y `confirm()` del navegador en cualquier parte de la aplicación.

#### Scenario: Abrir modal con confirmación simple
- **WHEN** se llama a `ConfirmationModal.show({ title, message, onConfirm })` desde cualquier parte de la aplicación
- **THEN** aparece un modal centrado y con overlay oscuro, mostrando el título, el mensaje y un botón de confirmación activo, junto a un botón de cancelar.

#### Scenario: Confirmar acción simple
- **WHEN** el usuario hace click en el botón de confirmación del modal
- **THEN** se ejecuta el callback `onConfirm`, el modal se cierra y el overlay desaparece.

#### Scenario: Cancelar modal
- **WHEN** el usuario hace click en el botón de cancelar o en el overlay oscuro fuera del modal
- **THEN** el modal se cierra sin ejecutar ninguna acción.

#### Scenario: Modal con texto tipado obligatorio
- **WHEN** se llama a `ConfirmationModal.show({ ..., requireTyped: "<texto>" })` y el modal está abierto
- **THEN** el modal muestra un campo de texto input y el botón de confirmar está deshabilitado hasta que el usuario escriba exactamente `<texto>` en el campo.

#### Scenario: Activación del botón al coincidir el texto
- **WHEN** el usuario escribe en el campo de texto y el valor coincide exactamente con `requireTyped`
- **THEN** el botón de confirmar se habilita y el usuario puede ejecutar la acción.

#### Scenario: Estilo del modal
- **WHEN** el modal está abierto
- **THEN** el modal usa el tema Indigo/glassmorphism del proyecto (fondo oscuro semitransparente para el overlay, panel con fondo glass, bordes redondeados, tipografía Inter), y el botón de confirmar usa color rojo para acciones destructivas.

### Requirement: Application-Owned Dialog Coverage
The system SHALL use application-rendered HTML/CSS dialogs for user acknowledgement and confirmation throughout the frontend and SHALL NOT invoke native browser `alert()`, `confirm()`, or `prompt()` dialogs.

#### Scenario: Validation message requires acknowledgement
- **WHEN** a user action cannot proceed because required input or selection is missing
- **THEN** the system SHALL present an informational application dialog with a single acknowledgement action.

#### Scenario: Request fails
- **WHEN** an asynchronous user action fails or returns a non-success response
- **THEN** the system SHALL present an application error dialog with an actionable, non-empty message.

#### Scenario: Action requires confirmation
- **WHEN** an action is destructive or otherwise requires explicit confirmation
- **THEN** the system SHALL present an application confirmation dialog with distinct confirm and cancel actions.

### Requirement: Dialog Interaction Modes
The reusable dialog SHALL support informational, error, standard-confirmation, and typed-confirmation modes while preserving the Indigo/glass visual language of the application.

#### Scenario: Informational or error dialog
- **WHEN** an informational or error dialog opens
- **THEN** the dialog SHALL show its title, message, visual severity, and one acknowledgement action without a misleading cancel action.

#### Scenario: Standard confirmation accepted
- **WHEN** the user activates the confirm action in a standard-confirmation dialog
- **THEN** the dialog SHALL close and the configured operation SHALL run exactly once.

#### Scenario: Confirmation cancelled
- **WHEN** the user activates cancel, presses Escape, or dismisses a cancelable confirmation through its backdrop
- **THEN** the dialog SHALL close without running the configured operation.

#### Scenario: Typed confirmation opened
- **WHEN** a typed-confirmation dialog opens with required text
- **THEN** its confirm action SHALL remain disabled until the entered text exactly matches the required text.

#### Scenario: Asynchronous failure has contextual loading UI
- **WHEN** an operation fails while a contextual loading indicator is active
- **THEN** the contextual loading indicator SHALL be cleared before the error dialog becomes interactive.
- **AND** the underlying form or controls SHALL return to a usable state.

### Requirement: Accessible and Safe Dialog Behavior
Every application dialog SHALL expose modal semantics, manage keyboard focus, and render externally derived message content as text rather than executable markup.

#### Scenario: Dialog opens
- **WHEN** a dialog becomes visible
- **THEN** focus SHALL move inside the dialog, keyboard focus SHALL remain within it, and its title and description SHALL be programmatically associated with the modal.

#### Scenario: Dialog closes
- **WHEN** a dialog closes by any supported action
- **THEN** focus SHALL return to the control that opened it when that control still exists.

#### Scenario: Dynamic message is rendered
- **WHEN** a dialog message includes a workspace name, project name, path, branch, server response, or network error
- **THEN** the dynamic value SHALL be displayed as text and SHALL NOT be interpreted as HTML.

#### Scenario: Multiple dialog requests occur
- **WHEN** code requests a new dialog while another application dialog is open
- **THEN** the component SHALL resolve the current dialog deterministically before presenting another, without stacking inaccessible overlays or callbacks.

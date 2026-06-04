## ADDED Requirements

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

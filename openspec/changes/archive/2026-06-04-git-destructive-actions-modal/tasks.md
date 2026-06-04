## 1. Modal de Confirmación Reutilizable (Frontend)

- [x] 1.1 Crear `/src/main/resources/static/js/confirmation-modal.js` con la API global `ConfirmationModal.show(options)` (title, message, requireTyped, onConfirm, onCancel)
- [x] 1.2 Implementar el HTML del modal dinámicamente en JS: overlay oscuro, panel glassmorphism, título, mensaje, campo de texto condicional (`requireTyped`), botón cancelar y botón confirmar (rojo, deshabilitado hasta texto correcto)
- [x] 1.3 Implementar lógica de validación en tiempo real: habilitar/deshabilitar botón confirmar según `input.value === requireTyped`
- [x] 1.4 Implementar cierre del modal al hacer click en overlay o botón cancelar
- [x] 1.5 Añadir el script `<script src="/js/confirmation-modal.js">` en `workspace-detail.html` e `index.html`

## 2. Backend — Unstage

- [x] 2.1 Añadir método `unstageProject(Long workspaceId, Long projectId)` en el `GitService` que ejecute `git restore --staged .` en el directorio del proyecto
- [x] 2.2 Añadir endpoint `POST /workspaces/{workspaceId}/projects/{projectId}/git/unstage` en el controlador Git, que llame al service y retorne el fragmento de estado actualizado del proyecto

## 3. Backend — Discard Changes

- [x] 3.1 Añadir método `discardChanges(Long workspaceId, Long projectId)` en el `GitService` que ejecute `git checkout -- .` en el directorio del proyecto
- [x] 3.2 Añadir endpoint `POST /workspaces/{workspaceId}/projects/{projectId}/git/discard` en el controlador Git, que llame al service y retorne el fragmento de estado actualizado del proyecto

## 4. Frontend — Botones Unstage y Discard Changes en tarjeta de proyecto

- [x] 4.1 En `workspace-detail.html`, añadir un grupo de botones destructivos a la izquierda del área de acciones de cada proyecto (separado del grupo principal con un divisor o gap)
- [x] 4.2 Añadir botón "Unstage" (color amber) con `onclick` que llame a `ConfirmationModal.show(...)` con mensaje de confirmación simple; al confirmar, disparar `htmx.ajax('POST', url_unstage, {target: '#project-row-{id}', swap: 'outerHTML'})`
- [x] 4.3 Añadir botón "Discard Changes" (color rojo) con `onclick` que llame a `ConfirmationModal.show(...)` con `requireTyped: workspaceName`; al confirmar, disparar `htmx.ajax('POST', url_discard, ...)`
- [x] 4.4 Asegurarse de que los nombres de workspace se pasan correctamente a Thymeleaf como atributos `data-*` en los botones para usarlos en el modal

## 5. Frontend — Delete Workspace con modal de confirmación

- [x] 5.1 En `index.html` (o donde esté el botón de borrar workspace), reemplazar el `confirm()` nativo por una llamada a `ConfirmationModal.show(...)` con `requireTyped: workspaceName`
- [x] 5.2 Al confirmar, disparar la petición HTMX o `fetch()` existente de borrado del workspace
- [x] 5.3 Verificar que el flujo de redirección tras borrado sigue funcionando correctamente

## 6. Frontend — Botón Copiar Ruta muestra basename

- [x] 6.1 En `workspace-detail.html`, localizar el elemento donde se muestra la ruta del proyecto para el botón copiar
- [x] 6.2 Cambiar el texto visible usando Thymeleaf: `th:text="${#strings.substringAfterLast(project.path, '/')}"` (o equivalente) manteniendo `data-path="${project.path}"` para el clipboard
- [x] 6.3 Verificar que la funcionalidad de copiar al portapapeles sigue usando la ruta absoluta completa

## 7. Verificación y pruebas manuales

- [x] 7.1 Probar modal de confirmación simple (Unstage): abrir, cancelar, confirmar → verificar estado Git actualizado
- [x] 7.2 Probar modal con texto requerido (Discard Changes): botón deshabilitado hasta texto correcto, confirmar → verificar estado Git
- [x] 7.3 Probar modal con texto requerido (Delete Workspace): flujo completo de borrado
- [x] 7.4 Verificar botón copiar ruta: muestra solo basename, copia ruta absoluta completa
- [x] 7.5 Verificar que el spinner global (`global-spinner.js`) se activa durante las peticiones HTMX de unstage/discard

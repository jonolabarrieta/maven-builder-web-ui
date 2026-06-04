## Why

Los proyectos en el workspace tienen operaciones Git destructivas (unstage y discard changes) que actualmente carecen de confirmación visual, exponiéndose a pérdidas de datos accidentales. Además, el botón para copiar la ruta muestra la ruta completa en lugar del nombre de carpeta, haciendo el UI más verboso de lo necesario. Se consolidan estas mejoras UX en un único cambio para introducir un sistema de confirmación reutilizable y refinamientos visuales en las tarjetas de proyecto.

## What Changes

- **Nuevo componente modal de confirmación** reutilizable (reemplaza el `alert()` nativo de JS) con soporte para mensaje personalizado y texto de confirmación tipado cuando la operación es de alto riesgo.
- **Botón Unstage** añadido al área de acciones Git de cada proyecto, con confirmación mediante modal.
- **Botón Discard Changes** añadido al área de acciones Git, con modal que requiere escribir el nombre del workspace para confirmar (operación destructiva).
- **Borrar Workspace**: el botón de eliminación de workspace existente pasa a usar el mismo modal de confirmación reutilizable, requiriendo también que se escriba el nombre del workspace.
- **Botón Copiar Ruta**: muestra únicamente el nombre de la carpeta (basename) en lugar de la ruta completa, manteniendo la ruta completa en el portapapeles.
- Los botones Unstage y Discard Changes se colocan en una zona diferenciada a la izquierda del área de acciones, separados visualmente de las acciones de build.

## Capabilities

### New Capabilities
- `confirmation-modal`: Modal de confirmación reutilizable con soporte para texto tipado de verificación (usado en unstage, discard changes y delete workspace).
- `git-unstage`: Acción para hacer unstage de todos los cambios de un proyecto con confirmación modal.
- `git-discard-changes`: Acción para descartar todos los cambios de un proyecto con confirmación modal que requiere escribir el nombre del workspace.

### Modified Capabilities
- `project-local-shortcuts`: El botón de copiar ruta ahora muestra el basename (nombre de carpeta) en lugar de la ruta completa.

## Impact

- **Frontend**: `workspace-detail.html` (Thymeleaf) — nuevas acciones Git, disposición de botones, modal component inline o fragment Thymeleaf.
- **Backend**: Nuevos endpoints `POST /workspaces/{id}/projects/{projectId}/git/unstage` y `POST /workspaces/{id}/projects/{projectId}/git/discard` en el controlador Git existente.
- **JS**: Lógica del modal de confirmación reutilizable (`/js/confirmation-modal.js` o inline).
- **Delete Workspace**: integrar modal de confirmación en el flujo existente de borrado de workspace.

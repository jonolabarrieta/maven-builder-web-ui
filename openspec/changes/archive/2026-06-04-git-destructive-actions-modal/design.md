## Context

La aplicación Maven Builder Web UI gestiona workspaces con proyectos Maven. Cada proyecto tiene acciones de Git (pull, fetch, etc.) gestionadas mediante botones en `workspace-detail.html`. Actualmente, operaciones destructivas como unstage y discard changes no existen, y el borrado de workspace usa el `confirm()` nativo del navegador. El botón de copiar ruta muestra la ruta completa en lugar del nombre de carpeta.

El stack es Spring Boot 3.2.5 + Thymeleaf + HTMX + TailwindCSS. La interactividad asíncrona se realiza con HTMX.

## Goals / Non-Goals

**Goals:**
- Introducir un modal de confirmación reutilizable en JS puro (sin librerías externas) que reemplace el `alert()`/`confirm()` nativo.
- Añadir botones Unstage y Discard Changes en el área de acciones de cada proyecto, separados a la izquierda.
- Hacer que Discard Changes y Delete Workspace requieran escribir el nombre del workspace para confirmar.
- Hacer que el botón de copiar ruta muestre solo el basename de la carpeta (no la ruta completa).
- Añadir endpoints backend para unstage y discard changes.

**Non-Goals:**
- Refactorizar toda la capa de Git service existente más allá de añadir los dos nuevos métodos.
- Implementar deshacer (undo) tras discard.
- Soportar unstage/discard selectivo por archivo (solo operación completa de proyecto).

## Decisions

### D1: Modal de confirmación como módulo JS vanilla reutilizable

**Decisión**: Implementar el modal como un fichero `/js/confirmation-modal.js` que expone una API global `ConfirmationModal.show({ title, message, confirmText, requireTyped, onConfirm })`.

**Alternativas consideradas**:
- *Inline en la plantilla Thymeleaf*: Más sencillo pero no reutilizable entre `index.html` y `workspace-detail.html`.
- *Fragment Thymeleaf*: Requiere incluirlo en todas las páginas y complica la interacción JS.
- *Librería externa (SweetAlert2)*: Añade dependencia externa; preferimos no depender de CDN adicional.

**Rationale**: Un módulo JS propio garantiza reutilización entre páginas, cero dependencias extra y control total sobre el estilo Indigo/glassmorphism del proyecto.

### D2: Botones Unstage/Discard a la izquierda, separados visualmente

**Decisión**: En la fila de acciones de cada proyecto, añadir un grupo a la izquierda con Unstage (naranja/amber) y Discard (rojo), separados del grupo principal de acciones (build, Git pull, etc.) por un divisor o espacio.

**Alternativas consideradas**:
- *Menú desplegable (dropdown)*: Oculta las acciones, reduce descubribilidad.
- *Íconos-only sin texto*: Dificulta la comprensión de acciones destructivas.

**Rationale**: Separación visual clara entre acciones seguras y destructivas. Color rojo/amber refuerza la naturaleza de la acción.

### D3: Confirmación tipada del nombre de workspace para operaciones de alto riesgo

**Decisión**: Discard Changes y Delete Workspace abren el modal con `requireTyped: <nombre_workspace>` — el botón de confirmar solo se activa cuando el usuario escribe exactamente el nombre del workspace.

**Alternativas consideradas**:
- *Doble click de confirmación*: Más rápido pero no impide confirmaciones accidentales.
- *Checkbox "Entiendo las consecuencias"*: Menos fricción que text-input; insuficiente para operaciones irreversibles.

**Rationale**: Patrón establecido (GitHub, Vercel) para operaciones destructivas de alto impacto. Unstage es reversible → solo confirmación simple.

### D4: Basename en botón copiar ruta

**Decisión**: El texto visible del botón cambia a `path.substring(path.lastIndexOf('/') + 1)` (o equivalente Thymeleaf). La ruta completa sigue siendo lo que se copia al portapapeles.

**Rationale**: Ahorra espacio en la UI, el workspace ya provee contexto sobre qué proyecto es.

## Risks / Trade-offs

- **Dependencia de nombre exacto del workspace para confirmación**: Si el nombre contiene caracteres especiales o espacios, el usuario debe teclearlos exactamente. Mitigación: mostrar el nombre esperado con `font-mono` para claridad.
- **HTMX + modal asíncrono**: Las acciones Git van a HTMX. El modal JS intercepta el click, espera confirmación, y luego dispara programáticamente el request HTMX. Hay que asegurarse de que `htmx.trigger()` o un `fetch()` manual funciona correctamente. Mitigación: usar `htmx.ajax()` o un `<form>` oculto que se envía tras confirmar.
- **Sin rollback para Discard**: La operación `git checkout -- .` es irreversible. El texto tipado de confirmación es la única salvaguarda. Documentar claramente en el modal.

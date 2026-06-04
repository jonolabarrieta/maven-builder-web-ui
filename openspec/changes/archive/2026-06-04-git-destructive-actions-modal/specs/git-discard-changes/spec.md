## ADDED Requirements

### Requirement: Botón Discard Changes en tarjeta de proyecto
El sistema SHALL mostrar un botón "Discard Changes" en el área de acciones de cada proyecto dentro de la vista de detalle del workspace, junto al botón Unstage, en el grupo de acciones destructivas a la izquierda.

#### Scenario: Botón Discard Changes visible en tarjeta
- **WHEN** el usuario visualiza la lista de proyectos de un workspace
- **THEN** cada proyecto muestra un botón "Discard Changes" de color rojo en el lado izquierdo del área de acciones, junto al botón Unstage.

#### Scenario: Click en Discard Changes abre modal de confirmación con texto requerido
- **WHEN** el usuario hace click en el botón "Discard Changes" de un proyecto
- **THEN** se abre el modal de confirmación mostrando el nombre del workspace, un mensaje de advertencia sobre la irreversibilidad de la operación, un campo de texto donde el usuario debe escribir exactamente el nombre del workspace, y el botón de confirmar está deshabilitado hasta que el texto coincida.

#### Scenario: Botón confirmar se activa al escribir el nombre correcto
- **WHEN** el usuario escribe exactamente el nombre del workspace en el campo de texto del modal
- **THEN** el botón de confirmar se habilita.

#### Scenario: Confirmar discard ejecuta la operación
- **WHEN** el usuario confirma la acción en el modal con el texto correcto
- **THEN** se envía una petición `POST /workspaces/{workspaceId}/projects/{projectId}/git/discard` al backend, la tarjeta de proyecto se actualiza y el modal se cierra.

#### Scenario: Backend ejecuta git checkout
- **WHEN** el backend recibe `POST /workspaces/{workspaceId}/projects/{projectId}/git/discard`
- **THEN** ejecuta el comando equivalente a `git checkout -- .` en el directorio del proyecto y devuelve el estado actualizado.

### Requirement: Borrar Workspace usa modal de confirmación con texto requerido
El sistema SHALL reemplazar el `confirm()` nativo del navegador en la acción de borrar workspace por el modal de confirmación reutilizable, requiriendo que el usuario escriba exactamente el nombre del workspace para confirmar el borrado.

#### Scenario: Click en borrar workspace abre modal con texto requerido
- **WHEN** el usuario hace click en el botón de borrar workspace
- **THEN** se abre el modal de confirmación mostrando el nombre del workspace y un campo de texto donde el usuario debe escribir exactamente el nombre del workspace; el botón de confirmar está deshabilitado hasta que el texto coincida.

#### Scenario: Confirmar borrado cuando el texto coincide
- **WHEN** el usuario escribe el nombre correcto del workspace y hace click en confirmar
- **THEN** se ejecuta la petición de borrado del workspace y se redirige al usuario a la lista de workspaces.

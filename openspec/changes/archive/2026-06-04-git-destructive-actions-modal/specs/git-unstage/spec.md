## ADDED Requirements

### Requirement: Botón Unstage en tarjeta de proyecto
El sistema SHALL mostrar un botón "Unstage" en el área de acciones de cada proyecto dentro de la vista de detalle del workspace, colocado en un grupo de acciones destructivas separado visualmente a la izquierda del resto de acciones.

#### Scenario: Botón Unstage visible en tarjeta
- **WHEN** el usuario visualiza la lista de proyectos de un workspace
- **THEN** cada proyecto muestra un botón "Unstage" de color amber/naranja en el lado izquierdo del área de acciones.

#### Scenario: Click en Unstage abre modal de confirmación
- **WHEN** el usuario hace click en el botón "Unstage" de un proyecto
- **THEN** se abre el modal de confirmación con título "Unstage cambios", un mensaje indicando que se eliminarán todos los ficheros del staging area del proyecto, y un botón de confirmar activo (sin texto tipado requerido).

#### Scenario: Confirmar unstage ejecuta la operación
- **WHEN** el usuario confirma la acción en el modal
- **THEN** se envía una petición `POST /workspaces/{workspaceId}/projects/{projectId}/git/unstage` al backend, la tarjeta de proyecto se actualiza reflejando el nuevo estado Git, y el modal se cierra.

#### Scenario: Backend ejecuta git reset HEAD
- **WHEN** el backend recibe `POST /workspaces/{workspaceId}/projects/{projectId}/git/unstage`
- **THEN** ejecuta el comando equivalente a `git reset HEAD` (o `git restore --staged .`) en el directorio del proyecto y devuelve el estado actualizado.

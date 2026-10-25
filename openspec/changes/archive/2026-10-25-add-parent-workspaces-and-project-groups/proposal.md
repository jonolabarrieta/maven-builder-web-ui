## Why

Los workspaces mantienen listas independientes de proyectos, por lo que reutilizar una colección común exige duplicarla y mantenerla en varios sitios. Hace falta compartir workspaces padre y organizar proyectos en grupos locales que permitan operar sobre una colección o sobre un proyecto concreto.

## What Changes

- Crear workspaces padre reutilizables y una pestaña global «Workspaces padre» para administrarlos y conocer dónde se utilizan.
- Importar un padre en varios workspaces mediante referencia compartida: cada importación aparece como un grupo, y los cambios del padre se reflejan al consultar o refrescar sus consumidores.
- Crear, renombrar, ordenar y eliminar grupos locales; mover proyectos locales entre grupos y dejarlos sin agrupar.
- Mostrar grupos plegables con sus proyectos y acciones individuales. Conservar selección y plegado durante actualizaciones HTMX y de Git.
- Ejecutar build y operaciones Git sobre el grupo completo o sobre proyectos seleccionados dentro de él, validando siempre el alcance solicitado.
- Conservar el orden de ejecución, proyectos no Maven, confirmaciones, logs, historial y recuperación de builds; evitar ejecutar varias veces el mismo repositorio en una operación Git.
- Mantener la compatibilidad con workspaces existentes e incorporar grupos y padres al intercambio de configuración.

## Capabilities

### New Capabilities

- `parent-workspaces`: Catálogo, administración y referencias reutilizables de workspaces padre.
- `workspace-project-groups`: Grupos locales e importados, pertenencia, orden, plegado y configuración local.
- `workspace-group-actions`: Acciones colectivas e individuales con resolución y validación de alcance.

### Modified Capabilities

- `workspace-project-selection`: Selección estable de grupos y proyectos, incluidos miembros de grupos plegados.
- `workspace-build-actions`: Planes de build con contexto del workspace consumidor y límites de grupo.
- `workspace-import-export`: Exportación e importación de configuración agrupada y padres con compatibilidad con archivos anteriores.

## Impact

Afecta a persistencia JPA/H2, WorkspaceService, resolución de proyectos, BuildService y continuaciones, WorkspaceController y nuevos controladores de padres/grupos. Cambian index.html, workspace-detail.html, fragmentos de proyectos y diálogos, con nuevas vistas de administración y pruebas de servicio, controladores y frontend. El cambio completado `add-manual-projects-and-build-continuation` sigue activo: su comportamiento implementado se conserva y debe reconciliarse al archivar las especificaciones. Sin nuevas dependencias de ejecución.

## 1. Persistencia y compatibilidad

- [x] 1.1 Añadir tipo NORMAL/PARENT a Workspace y migrar registros existentes a NORMAL con prueba sobre H2 persistente.
- [x] 1.2 Crear entidad/repositorio de grupos LOCAL/IMPORTED con consumidor, nombre, referencia al padre y restricción de una importación por padre y consumidor.
- [x] 1.3 Añadir entradas ordenadas de bloques y pertenencia de raíces a grupos locales, con restricciones que impidan módulos independientes o grupos anidados.
- [x] 1.4 Añadir overrides de habilitación para miembros importados y eliminar únicamente los overrides de referencias o miembros retirados.
- [x] 1.5 Migrar raíces existentes a entradas sin agrupar y verificar que se conservan orden efectivo, módulos, enabled, exclusiones y carpetas no Maven tras reiniciar.

## 2. Servicios de padres y grupos

- [x] 2.1 Implementar listar, crear, editar y consultar consumidores de padres reutilizando descubrimiento y registro manual actuales.
- [x] 2.2 Implementar importar/retirar padre con validación de tipo, autoimportación, importación repetida y ausencia de anidamiento.
- [x] 2.3 Implementar eliminación protegida de padres y ajustar WorkspaceService.deleteWorkspace para que borrar consumidores conserve padres y proyectos compartidos.
- [x] 2.4 Implementar crear, renombrar y eliminar grupos locales conservando los proyectos en la posición del grupo retirado.
- [x] 2.5 Implementar mover raíces y reordenar bloques/miembros con validación completa antes de persistir; conservar subárboles Maven.
- [x] 2.6 Adaptar WorkspaceService.duplicateWorkspace para copiar estructura local/overrides y mantener referencias; duplicar padres sin consumidores.
- [x] 2.7 Implementar resolución por lotes de la composición efectiva con contexto consumidor, origen, grupo, rutas, enabled y orden.
- [x] 2.8 Adaptar refresh e indicadores de hijos a la composición; separar refresco del padre de actualización de proyección del consumidor y conservar proyectos manuales.
- [x] 2.9 Adaptar cálculo de dependencias al orden por bloques y reportar conflictos conservando la secuencia anterior.

## 3. Planes de acciones y builds

- [x] 3.1 Crear resolver de alcance WORKSPACE/GROUP/PROJECT que valide todos los miembros y referencias antes de devolver un snapshot ejecutable; probar rechazos sin efectos parciales.
- [x] 3.2 Adaptar BuildService para aceptar contexto consumidor y enabled efectivo, conservar perfil activo y usar Java del consumidor en proyectos importados.
- [x] 3.3 Integrar Build All y build de grupo con orden efectivo, exclusión explícita de no Maven/no disponibles y deduplicación por carpeta canónica.
- [x] 3.4 Adaptar selección ONLY/FROM/THROUGH a límites de grupo y mantener rangos de workspace para entradas sin agrupar.
- [x] 3.5 Incorporar consumidor/grupo al plan de BuildContinuationRegistry y validar pertenencia al continuar sin recalcular orden; conservar retry, skip, uso único e historial previo.
- [x] 3.6 Enrutar logs, resúmenes, historial y notificaciones al consumidor e identificar grupo/origen cuando corresponda, incluidos planes sin objetivos elegibles.
- [x] 3.7 Resolver y deduplicar raíces Git canónicas, incluidos worktrees, para fetch/pull y asociar resultados a todas las filas afectadas.
- [x] 3.8 Integrar checkout/crear rama sobre rutas resueltas y validar ámbito completo; eliminar reconstrucción de rutas importadas desde la base del consumidor.
- [x] 3.9 Integrar stash/discard/unstage con deduplicación y confirmaciones existentes; conservar includeUntracked y explicar repositorios compartidos.
- [x] 3.10 Implementar habilitación colectiva y overrides individuales, con herencia del padre para nuevos miembros y sin escritura de configuración del origen desde el consumidor.

## 4. Controladores y navegación

- [x] 4.1 Añadir controlador de padres con GET de catálogo/editor/consumidores y POST de crear/editar/eliminar/duplicar; probar errores y protección de padres referenciados.
- [x] 4.2 Añadir controlador de grupos con POST de crear/renombrar/eliminar/importar/retirar padre y endpoints de mover raíces y reordenar bloques/miembros; probar pertenencia.
- [x] 4.3 Añadir endpoints de acción/refresco/habilitación de grupo y validar el alcance antes de delegar en servicios.
- [x] 4.4 Adaptar WorkspaceController.index, createWorkspace, updateWorkspace, deleteWorkspace y duplicateWorkspace para tipos y navegación coherente.
- [x] 4.5 Adaptar viewWorkspace, addProjectTableModel, getGitStatusTable, refreshWorkspace, calculateOrder y updateOrder a composición, identidades contextuales y orden por bloques.
- [x] 4.6 Adaptar buildAll, bulkAction, bulkCheckout, buildProject, buildProjectRange y continueBuild para resolver contexto consumidor y alcance de grupo; conservar rutas legacy válidas.
- [x] 4.7 Adaptar toggleEnabled, deleteProject, showExplorer y addProjects para distinguir propiedad local/importada y rechazar cambios estructurales de importados desde el consumidor.
- [x] 4.8 Propagar contexto en fetchProject, pullProject, unstageProject, discardProjectChanges, getBranches, fetchAndReloadBranches, checkoutBranch y createBranch; probar IDs ajenos y referencias retiradas.
- [x] 4.9 Propagar contexto en getProjectChildren, getProjectProperties, getM2Info, deleteM2Version, deleteM2Project, openVsCode, openExplorer y openTerminal para acciones individuales importadas sin expandir su ámbito.

## 5. Vistas e interacción

- [x] 5.1 Actualizar index.html con pestañas Workspaces/Workspaces padre y añadir vista del catálogo de padres con formularios CRUD, lista de consumidores y estados vacíos/errores.
- [x] 5.2 Adaptar vista de edición del padre para proyectos, explorador y orden, mostrando consumidores afectados y manteniendo registro explícito de no Maven.
- [x] 5.3 Añadir a workspace-detail.html y sus diálogos crear grupo, renombrar grupo e importar padre, con acciones claras para retirar importaciones y desagrupar proyectos.
- [x] 5.4 Extraer/adaptar fragmentos de cabecera de grupo y filas en workspace-detail.html para origen, miembros, acciones individuales, controles colectivos y plegado accesible.
- [x] 5.5 Implementar reordenación de bloques y de miembros locales en la vista, con persistencia y restauración tras errores sin permitir edición de orden importado.
- [x] 5.6 Adaptar selección de página, Select All, estados mixtos y payloads a identidades contextuales; incluir miembros plegados y reconciliar altas/bajas tras HTMX.
- [x] 5.7 Persistir plegado por workspace en el navegador y restaurarlo tras HTMX, Git y reapertura; comprobar teclado, aria-expanded y foco.
- [x] 5.8 Adaptar project-children.html, branch-selector.html, project-properties.html, m2-info.html, m2-favorites.html y explorer.html donde sus enlaces/formularios necesiten conservar consumidor/grupo.
- [x] 5.9 Adaptar app-dialogs.js, confirmaciones de acciones colectivas y build-recovery.js para contexto de grupo, elegibilidad y feedback del consumidor, reutilizando spinner y controles existentes.

## 6. Importación y exportación

- [x] 6.1 Fijar y documentar marcador de versión, esquema estructurado, IDs de archivo, validación y ejemplos de configuración agrupada; conservar lectores legacy y NonMavenProject.
- [x] 6.2 Implementar serialización versionada de bloques, miembros, overrides y padres embebidos sin IDs de BD ni metadatos derivados.
- [x] 6.3 Implementar parsing y validación estructural previa, seguido de importación transaccional y reconstrucción de nuevas identidades; probar rollback completo y colisiones de nombres.
- [x] 6.4 Adaptar exportWorkspacePaths y exportOrder al orden efectivo y deduplicación por carpeta; mantener salida de solo rutas sin marcadores.
- [x] 6.5 Adaptar WorkspaceController.exportWorkspace, exportWorkspacePaths, exportOrder e importWorkspace y los diálogos de index.html/workspace-detail.html al nuevo formato y avisos.
- [x] 6.6 Añadir round-trip de grupos locales/importados, tipos mixtos, overrides, orden y rutas no disponibles, además de regresión de archivos legacy.

## 7. Verificación integrada y documentación

- [x] 7.1 Añadir pruebas de persistencia/servicio para padre compartido en dos consumidores, propagación tras refresco, eliminación protegida, duplicación y reinicio con grupos.
- [x] 7.2 Añadir pruebas de planes/builds para Java del consumidor, scopes manipulados, módulos, deduplicación, rangos acotados y continuaciones con grupo retirado o reordenado.
- [x] 7.3 Añadir pruebas de Git por grupo con dos módulos del mismo repositorio y con repositorios distintos/worktrees, comprobando una ejecución por raíz y feedback contextual.
- [x] 7.4 Extender regresiones frontend de selección/refresco para grupos plegados, estados mixtos, altas/bajas del padre, confirmaciones y conservación de foco/contexto.
- [x] 7.5 Ejecutar las suites Java y regresiones frontend pertinentes; realizar recorrido manual padre → importar en dos workspaces → modificar padre → refrescar → acción de grupo e individual.
- [x] 7.6 Actualizar README y documentación de workspaces con referencias compartidas, grupos, límites de acciones, import/export y procedimiento de migración/restauración.
- [x] 7.7 Verificar consistencia final de los deltas con add-manual-projects-and-build-continuation y ejecutar openspec validate add-parent-workspaces-and-project-groups --strict antes de archivar.

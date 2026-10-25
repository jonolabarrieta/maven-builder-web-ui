## Context

Workspace tiene una relación OneToMany con MavenProject, con cascade y orphanRemoval; un proyecto pertenece a un solo workspace. La jerarquía actual se deriva de rutas y módulos Maven, no de grupos de usuario. WorkspaceController, BuildService, selección frontend, historial y continuaciones presuponen esa propiedad única. El checkout colectivo reconstruye rutas desde el workspace propietario y debe adaptarse para proyectos importados y externos.

El cambio activo completado `add-manual-projects-and-build-continuation` ya incorpora carpetas no Maven y recuperación de builds. Este diseño parte de ese código, aunque sus deltas todavía no estén archivados. Se conserva Java 21, Spring Boot, JPA/H2 y Thymeleaf/HTMX.

## Goals / Non-Goals

**Goals:** reutilizar colecciones compartidas; organizar proyectos locales; plegar grupos; operar sobre un grupo o un miembro; mantener compatibilidad, alcance explícito y recuperación de builds.

**Non-Goals:** anidar padres o grupos, clonar repositorios, sincronizar archivos entre máquinas, introducir nuevos sistemas de build o ampliar el descubrimiento de la pestaña Git independiente. Los grupos son organización de la aplicación, no módulos Maven.

## Decisions

### 1. Padre propietario y referencia compartida

Añadir un tipo de Workspace, NORMAL o PARENT, con NORMAL como valor para los registros existentes. Un padre utiliza la gestión actual de proyectos, rutas, exclusiones y metadatos; cada proyecto conserva su propietario. Un padre no importa padres ni contiene grupos en esta primera versión. La pestaña global «Workspaces padre» separa el catálogo de la lista habitual de workspaces y enlaza su editor y consumidores.

Importar significa crear un WorkspaceProjectGroup de tipo IMPORTED que referencia al padre. Se permite importar un mismo padre en distintos consumidores, una vez por consumidor. Las lecturas resuelven los proyectos actuales del padre; se reflejan los cambios al abrir o refrescar, sin propagar copias. No se requieren WebSockets de sincronización estructural.

Alternativas: copiar proyectos rompe el mantenimiento central; convertir MavenProject.workspace en ManyToMany mezcla propiedad, borrado y configuración. Una referencia al padre mantiene una única fuente y permite retirar una importación sin borrar proyectos.

La eliminación de un padre con consumidores se bloquea y muestra sus nombres. Primero se retiran las referencias. Borrar un consumidor elimina sus grupos y proyectos locales, preservando padres y sus proyectos. No hay conversión implícita de workspaces normales ni cambio de tipo de padres referenciados.

### 2. Grupos locales y orden de bloques

WorkspaceProjectGroup pertenece a un consumidor y tiene nombre, tipo y posición. Un grupo LOCAL admite miembros propios del consumidor; un IMPORTED contiene la proyección del padre y no admite edición estructural desde el consumidor. Agrupar un proyecto raíz incluye su subárbol Maven; no se separan sus módulos en otros grupos.

El modelo de orden mantiene posiciones de grupos y raíces sin agrupar como bloques en una única secuencia; el orden interno de un grupo local es independiente y el importado sigue al padre. Se implementa como Workspace.blocks, una colección JPA ordenada de claves p:<raíz> y g:<grupo>, evitando posiciones ambiguas entre tablas. Los workspaces antiguos con layout vacío se resuelven desde executionOrder sin reescritura masiva; las claves se persisten al organizar o reordenar. La cola efectiva aplana estos bloques y conserva el recorrido actual de módulos de cada raíz. Recalcular dependencias debe respetar los límites de bloque; si el orden requerido cruza límites incompatibles, se informa y se conserva el orden anterior, sin desagrupar silenciosamente.

Una raíz pertenece como máximo a un grupo local. Eliminar un grupo local devuelve sus miembros a posiciones contiguas sin agrupar en el lugar del bloque. Retirar un importado solo elimina la referencia. Se conserva la acción explícita de borrar un proyecto propio con su comportamiento actual de exclusiones.

### 3. Configuración del consumidor y proyectos efectivos

Un resolver entrega un snapshot de entradas efectivas con consumerWorkspaceId, groupId opcional, sourceWorkspaceId, projectId, ruta absoluta, orden y estado habilitado efectivo. Todas las acciones que acepten grupos o miembros importados usan este contexto; no se cambia project.workspace para simular pertenencia.

Los estados de habilitación propios del consumidor se almacenan como overrides de grupo/proyecto importado. El valor inicial proviene del padre; un override explícito prevalece. Activar/desactivar un grupo aplica esos valores a sus miembros actuales sin cambiar el padre. Los miembros añadidos después heredan el estado del padre. Los grupos locales actualizan el enabled existente de sus proyectos. Las bajas del padre invalidan sus overrides y selección; refrescar el consumidor no modifica membresías, orden ni rutas del padre. El refresco explícito del padre se realiza en su editor y los consumidores actualizan después su proyección y referencias Git.

Java se resuelve desde el workspace consumidor y después desde la configuración global. El perfil activo mantiene su funcionamiento actual. La edición estructural y rutas de un miembro importado se realiza en el padre; los botones de propiedades, módulos, VS Code, explorador y terminal siguen disponibles individualmente donde sean aplicables.

### 4. Un plan de ejecución explícito por alcance

Un servicio de alcance valida primero consumidor, grupo y todos los miembros solicitados; rechaza toda la solicitud si algún ID está fuera del alcance. No confía en IDs ocultos del formulario. Resuelve WORKSPACE, GROUP o PROJECT antes de delegar en los ejecutores actuales. Incluye habilitación efectiva y disponibilidad; los filtros de elegibilidad se mantienen por operación.

La cabecera de grupo ofrece build, fetch, pull, checkout/crear rama, stash, descartar cambios, unstage y activar/desactivar miembros. Refresh de grupo actualiza su proyección y metadatos sin reescribir el padre. Los atajos a carpetas y datos Maven siguen siendo de cada miembro, pues no existe una carpeta o POM del grupo.

Build usa el orden efectivo, solo proyectos Maven habilitados y disponibles, y elimina repeticiones de una misma carpeta canónica conservando la primera aparición. Si se ejecuta desde un grupo, sus rangos FROM/THROUGH quedan dentro de ese grupo; fuera de un grupo conservan alcance de workspace. ONLY incluye un proyecto. Un build global sigue el orden de bloques. No se amplía una acción de grupo para satisfacer dependencias externas: se informa cuando corresponde.

Git deduplica por raíz canónica de repositorio, incluyendo worktrees, antes de ejecutar. Un repositorio compartido por varios proyectos puede cambiar archivos visibles desde otras filas o workspaces; el diálogo identifica repositorios afectados. La selección no autoriza otros repositorios. Se mantienen las opciones y confirmaciones existentes, incluido stash con archivos sin seguimiento.

BuildService recibe contexto explícito del consumidor para Java, historial y notificaciones, en lugar de inferirlo del primer propietario. Una continuación captura la cola resuelta, rutas, grupo y consumidor originales; retry/skip no vuelve a resolver la selección actual. Si se elimina el grupo/importación o un miembro pendiente deja de ser accesible, se rechaza la continuación. Los cambios de orden posteriores no alteran la cola capturada.

### 5. Presentación y selección

index.html incorpora navegación entre Workspaces y Workspaces padre; la segunda pestaña ofrece crear, editar y eliminar padres, lista de proyectos y consumidores. workspace-detail.html añade crear grupo e importar padre mediante diálogos y reutiliza fragmentos de fila para proyectos propios e importados. La cabecera del grupo identifica origen, número de miembros, selección, plegado y acciones. El formulario de administración identifica cuándo los cambios afectan a consumidores.

El estado frontend pertenece a la página: selection conserva identidades de entradas y collapsedGroupIds conserva plegado, con persistencia de plegado por workspace en almacenamiento local del navegador. Los identificadores incluyen grupo y origen para distinguir proyecciones. Seleccionar grupo selecciona sus raíces actuales; Select All incluye raíces dentro de grupos plegados y fuera de ellos. Plegar no altera selección, enabled ni ejecución. Actualizaciones HTMX/Git reconcilian los IDs existentes y eliminan los retirados, sin seleccionar automáticamente miembros recién añadidos.

Se mantienen controles de teclado, aria-expanded/aria-controls, estado mixto y etiquetas accesibles. Los fragmentos de hijos, propiedades, ramas y diálogos mantienen el contexto consumidor/grupo. Se usan spinner y diálogos existentes y se conserva envío normal de formularios donde ya existe.

### 6. Formato de intercambio compatible y autosuficiente

El export completo de configuraciones con grupos usa un formato de texto versionado con un payload estructurado y delimitación inequívoca. Incluye configuración, tipos de proyecto, orden de bloques/miembros, overrides y las definiciones de padres referenciados una sola vez mediante IDs de archivo. No serializa entidades JPA, IDs de base de datos ni metadatos derivados. El marcador es MvnBuilderWorkspace: 2 seguido de JSON con version, workspace y parents. El esquema, las claves de archivo y un ejemplo se documentan en docs/workspace-groups.md; se registran sus DTOs para reflexión en compilaciones nativas.

Importar crea un nuevo consumidor y nuevos padres incluidos en el archivo, reconstruyendo referencias mediante un mapa de IDs de archivo a IDs nuevos. Nunca enlaza o sobrescribe un padre existente por coincidencia de nombre o ID. Dos referencias del archivo al mismo padre comparten la misma nueva definición donde el modelo lo permite. Un archivo estructuralmente inválido se rechaza sin persistencia parcial; rutas no disponibles siguen el tratamiento de avisos existente.

El lector conserva archivos legacy, `NonMavenProject:` y listas de rutas. Workspaces sin grupos pueden conservar el export legacy. Exportar solo rutas aplana el orden efectivo y escribe cada ruta canónica una vez, sin cabeceras ni marcadores. Duplicar un consumidor copia grupos locales, orden y overrides, y mantiene referencias a los mismos padres; duplicar un padre copia sus proyectos sin consumidores.

Alternativa: exportar solo IDs haría depender el archivo de una base de datos concreta y perdería portabilidad.

## Risks / Trade-offs

- [Ediciones compartidas inesperadas] → origen visible y edición estructural exclusivamente en el padre; sobrescrituras locales solo de habilitación y presentación.
- [Un mismo repositorio aparece en varios sitios] → deduplicación canónica y contexto explícito en confirmaciones y resultados.
- [Orden por grupos frente a dependencias cruzadas] → orden determinista, diagnóstico de conflicto y ninguna modificación silenciosa de grupos.
- [Consultas N+1 o refrescos costosos] → cargar grupos, padres y miembros por lotes y reutilizar snapshots para filas, selección y acciones.
- [OrphanRemoval elimina datos compartidos] → los proyectos siguen siendo propiedad del padre; eliminar una referencia nunca propaga borrado al origen.
- [Cambios durante una ejecución] → snapshot de plan y validación de acceso al continuar; cambios del padre solo afectan planes nuevos.
- [Deltas activos sin archivar] → reconciliar import/export y build con `add-manual-projects-and-build-continuation` antes de archivar este cambio.

## Migration Plan

1. Añadir tablas/campos de forma aditiva, asignar tipo NORMAL a workspaces existentes y crear entradas sin agrupar conservando orden, enabled, tipos y jerarquía actuales.
2. Añadir resolver y adaptar lecturas/acciones antes de exponer importaciones; verificar que un workspace legacy produce la misma cola.
3. Incorporar vistas de administración, grupos, selección y formato versionado; ejecutar pruebas con H2 persistente y reinicio.
4. Hacer copia de la base de datos antes de la migración. Para volver a una versión anterior después de usar grupos, restaurar esa copia; el código anterior no interpreta referencias ni orden de bloques.

## Open Questions

- Sin decisiones funcionales pendientes: el usuario ha confirmado referencia compartida, con cambios del padre reflejados en todos sus consumidores. El esquema concreto del archivo versionado queda documentado en docs/workspace-groups.md.

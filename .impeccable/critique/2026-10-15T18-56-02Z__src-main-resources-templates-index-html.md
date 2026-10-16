---
target: revisa la web de este proyecto
total_score: 21
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 4
target_identity: "file:/mnt/datos/code/documentos/proyectos/mvnBuilder/src/main/resources/templates/index.html"
target_fingerprint: "sha256:19aebaed150cb3c975a781b5805a54d2071f58f88a3811ff5381a6775a64a118"
target_path: /mnt/datos/code/documentos/proyectos/mvnBuilder/src/main/resources/templates/index.html
timestamp: 2026-10-15T18-56-02Z
slug: src-main-resources-templates-index-html
---
Method: dual-agent (A: `/root/design_assessment` · B: `/root/browser_detector`)

## Crítica de MvnBuilder

**Salud de diseño: 21/40 — aceptable, con fricciones importantes.** Revisé el inicio y Configuración renderizados, y la vista de workspace mediante su plantilla. La captura de detalle disponible es de mayo y puede no representar su estado actual.

| # | Heurística | Puntuación | Evidencia principal |
|---|---|---:|---|
| 1 | Estado del sistema | 2/4 | Hay spinner, logs y resumen de build; crear o duplicar un workspace no confirma claramente el resultado. |
| 2 | Lenguaje del usuario | 3/4 | La terminología Maven/Git es precisa; «M2» y «skip initial scan» carecen de explicación. |
| 3 | Control y salida | 2/4 | Se puede cerrar el modal con la X, pero Escape no lo cierra y falta «Cancelar». |
| 4 | Consistencia | 2/4 | Se mezclan inglés y español, y «Configuration» y «Settings» designan ámbitos distintos sin aclararlos. |
| 5 | Prevención de errores | 2/4 | El borrado de workspaces tiene confirmación fuerte; «Clear M2» no identifica la carpeta en su aviso. |
| 6 | Reconocer antes que recordar | 2/4 | Hay acciones explícitas, pero iconos pequeños para duplicar/borrar y rutas truncadas. |
| 7 | Eficiencia | 3/4 | Lotes, perfiles y ordenación ayudan al usuario experto; no aparece una alternativa de teclado para reordenar. |
| 8 | Jerarquía y simplicidad | 2/4 | El panel M2 precede a Workspaces; la vista de detalle concentra muchas acciones a la vez. |
| 9 | Recuperación de errores | 2/4 | Hay errores y reintento de build, pero la importación fallida devuelve al inicio sin conservar contexto suficiente. |
| 10 | Ayuda | 1/4 | No hay ayuda visible para M2, perfiles o el formato de importación. |
| **Total** | | **21/40** | **Aceptable** |

### Veredicto

El contenido sí pertenece a MvnBuilder: workspaces, builds, Git, JDK y repositorio M2. La presentación visual —Inter, índigo, tarjetas blancas y el icono «M»— podría servir a muchos paneles administrativos. La mayor oportunidad es convertir la secuencia real de trabajo, **elegir workspace → seleccionar alcance → ejecutar build → revisar resultado**, en la jerarquía de la interfaz.

El detector encontró **16 advertencias** en las tres plantillas: 3 por Inter, 3 por jerarquía tipográfica, 1 por salto de encabezado, 8 por tarjetas anidadas y 1 por un punto pulsante. Las advertencias tipográficas del inicio y Configuración son en gran parte falsos positivos del análisis estático: en el navegador, Tailwind sí da tamaños distintos a los encabezados. Las 7 tarjetas anidadas de Configuración incluyen estados condicionales que no aparecen simultáneamente. El salto de `h1` a `h3` sí merece corregirse cuando hay workspaces. No se pudo mostrar una superposición visual del detector: el navegador bloqueó la inyección de scripts.

### Lo que funciona

- El estado vacío de Workspaces explica el siguiente paso y ofrece «Create Workspace».
- El formulario de creación da un ejemplo de nombre y permite elegir la carpeta con «Browse».
- La vista de detalle contempla logs en vivo, finalización del build y reintento desde el proyecto fallido.

### Problemas prioritarios

1. **[P1] M2 desplaza la tarea principal.** El panel «M2 Global Favorites» aparece antes del título Workspaces incluso sin favoritos. En la página renderizada a 1265 × 704, el botón del estado vacío queda fuera de la primera pantalla. Mover M2 después de los workspaces o plegarlo cuando esté vacío; mostrar primero el título y la acción de crear. [Inicio](/mnt/datos/code/documentos/proyectos/mvnBuilder/src/main/resources/templates/index.html:75). **Comando:** `$impeccable layout`.
2. **[P1] El detalle exige decidir entre demasiadas acciones y alcances.** La cabecera, la barra de selección y cada fila repiten acciones de build y Git. Agrupar Export y acciones secundarias; revelar la barra de lote cuando haya selección; nombrar explícitamente «Build All», «Build Selected» y «Build This Module». [Detalle](/mnt/datos/code/documentos/proyectos/mvnBuilder/src/main/resources/templates/workspace-detail.html:49). **Comando:** `$impeccable distill`.
3. **[P1] El modal «New Workspace» no ofrece una interacción accesible completa.** En el navegador, Escape no lo cerró y el foco permaneció en el botón que lo abrió. El contenedor no declara `dialog`, los campos no enlazan sus etiquetas y la X no tiene nombre accesible. Añadir semántica de diálogo, gestión y devolución del foco, Escape, «Cancelar» y etiquetas asociadas. [Modal](/mnt/datos/code/documentos/proyectos/mvnBuilder/src/main/resources/templates/index.html:169). **Comando:** `$impeccable audit`.
4. **[P1] «Clear M2» comunica el riesgo sin identificar el objetivo.** El aviso solo dice que borrará *entire folder*; no muestra el grupo ni la ruta concreta y convive con un botón para quitar el favorito. Mostrar la carpeta exacta en la confirmación y distinguir claramente «quitar favorito» de «borrar archivos locales». [Favoritos M2](/mnt/datos/code/documentos/proyectos/mvnBuilder/src/main/resources/templates/fragments/m2-favorites.html:10). **Comando:** `$impeccable harden`.
5. **[P2] Falta continuidad tras algunas acciones y el idioma cambia dentro del flujo.** Crear o duplicar no da una confirmación contextual clara; un fallo de importación vuelve al inicio; las pantallas alternan inglés y español. Conservar el contexto del error, indicar cómo resolverlo y fijar una estrategia de idioma consistente. [Inicio](/mnt/datos/code/documentos/proyectos/mvnBuilder/src/main/resources/templates/index.html:223), [Configuración](/mnt/datos/code/documentos/proyectos/mvnBuilder/src/main/resources/templates/settings.html:166). **Comando:** `$impeccable clarify`.

### Carga cognitiva y personas

El inicio tiene carga **moderada**: el panel M2 vacío compite con la tarea principal. El detalle tiene carga **alta**: hay más de cuatro opciones visibles en varios puntos, y el usuario debe recordar si está actuando sobre todo el workspace, una selección o un módulo. El momento de mayor incertidumbre es anterior al build; los logs y el resumen posterior sí aportan tranquilidad.

- **Jordan, usuario nuevo:** encuentra M2 antes de crear su primer workspace y no recibe explicación de «M2», «skip initial scan» o el archivo de importación.
- **Alex, usuario experto:** dispone de lotes y perfiles, pero debe buscar la acción correcta entre varias barras y alcances de build.
- **Sam, usuario de teclado o lector de pantalla:** no puede cerrar «New Workspace» con Escape; el foco y las etiquetas del formulario no comunican bien el diálogo.

### Observaciones menores y preguntas

El encabezado de Configuración necesita un `h1`; las rutas truncadas deberían poder leerse completas sin depender de `title`; conviene medir el contraste del texto gris claro antes de cambiar colores. La vista de detalle renderizada y los estados poblados quedan pendientes de verificación visual.

- ¿M2 debe ser tarea principal al abrir el producto o una herramienta bajo mantenimiento?
- ¿Qué alcance debe tener el Build más visible: workspace, selección o módulo?
- ¿El idioma de la UI debe ser inglés, español o configurable?

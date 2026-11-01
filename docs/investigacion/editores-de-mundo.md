# Investigación: qué falla en WorldEdit y FAWE, y cómo puede ganarles un editor de mundo nuevo

**Fecha:** 2026-11-01 · **Para:** el dueño de la fábrica · **Estado:** insumo para la spec del plugin nuevo (nada implementado)

Objetivo: diseñar un plugin de Paper (1.21.4+) que haga lo que hace WorldEdit, mucho mejor y sin mod cliente. Fuentes:
issues de GitHub de [EngineHub/WorldEdit](https://github.com/EngineHub/WorldEdit/issues) y
[IntellectualSites/FastAsyncWorldEdit](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues) ordenados por
comentarios, documentación oficial (WorldEdit, FAWE, CoreProtect, Axiom, Sponge Schematic), páginas de Modrinth y
Hangar, y artículos de terceros marcados como tales.

**Límites de la investigación.**
- **Reddit no se pudo leer:** la herramienta no puede abrir reddit.com y ninguna búsqueda (r/admincraft, r/Minecraft,
  r/MinecraftBuilds) devolvió hilos. Lo que aquí parece «opinión de usuarios» sale de issues de GitHub, foros de hosting
  (Aternos) y artículos.
- **SpigotMC dio 403** (reseñas de FAWE, también en el espejo speedgot). Una reseña de FAWE se cita solo por el extracto
  que mostró el buscador, marcada así.
- Las cifras (descargas, issues abiertos) son del día de la consulta y cambian.
- La documentación de configuración de FAWE en GitBook no muestra el `config.yml` completo: las claves exactas de límites e
  historial no se pudieron verificar y no se inventan aquí.

## 0. Cifras de contexto

| Herramienta | Descargas | Licencia | Plataformas | Fuente |
|---|---|---|---|---|
| WorldEdit | 12,2 M (Modrinth), 5 k seguidores | GPL-3.0-only | Bukkit, Paper, Folia, Fabric, Forge, NeoForge… | [Modrinth](https://modrinth.com/plugin/worldedit) |
| FastAsyncWorldEdit | 635,4 k (Modrinth); 30.457 en Hangar | GPL-3.0-only | Bukkit, Paper, Purpur, Spigot (Folia no listado en Modrinth) | [Modrinth](https://modrinth.com/plugin/fastasyncworldedit), [Hangar](https://hangar.papermc.io/IntellectualSites/FastAsyncWorldEdit) |
| Axiom (mod) | ~14,6 M | ARR (propietaria) + EULA | Fabric | [Modrinth](https://modrinth.com/mod/axiom) |
| Litematica (mod) | ~28,9 M | LGPL-3.0-only | Fabric, Forge… (solo cliente) | [Modrinth](https://modrinth.com/mod/litematica) |
| WorldEdit CUI (mod) | ~827 k | — | Fabric/Quilt | [Modrinth](https://modrinth.com/mod/worldedit-cui) |
| FastAsyncVoxelSniper | ~33 k | GPL-3.0-only | Paper, Spigot, Purpur | [Modrinth](https://modrinth.com/plugin/fastasyncvoxelsniper) |
| goPaintAdvanced | ~8,4 k | GPL-3.0-only | Paper, Folia | [Modrinth](https://modrinth.com/plugin/gopaintadvanced) |
| WorldEditSUI | ~9,5 k (Hangar) | sin especificar | Paper | [Hangar](https://hangar.papermc.io/kennytv/WorldEditSUI) |

Issues abiertos el día de la consulta: **147** en WorldEdit y **99** en FAWE
([WorldEdit](https://github.com/EngineHub/WorldEdit/issues?q=is%3Aissue+is%3Aopen+sort%3Acomments-desc),
[FAWE](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues?q=is%3Aissue+is%3Aopen+sort%3Acomments-desc)).

## 1. Qué se le critica a WorldEdit y a FAWE

### 1.1 Rendimiento: lag y caídas con ediciones grandes
- **WorldEdit trabaja en el hilo principal.** El issue [#568](https://github.com/EngineHub/WorldEdit/issues/568)
  («Chunk up operations and make some asynchronous») está abierto **desde 2012**. sk89q explicó que el servidor no espera
  a los plugins y que dos hilos podrían cambiar el mismo bloque a la vez; otro participante resumió que el problema real es
  mucho trabajo en un solo método del hilo principal, que retrasa los paquetes y congela el servidor, y propuso repartir
  el trabajo por tick. En 2024 se agregó a «Planned Improvements»; sigue sin PR ni asignado.
- **La caída típica:** la [FAQ de WorldEdit](https://worldedit.enginehub.org/en/latest/faq/) dice que las ediciones de
  más de 1 M de bloques fallan por el *watchdog* o por quedarse sin memoria. Su receta es subir `timeout-time` de
  `spigot.yml` y dar **~1 GB + 2 GB por cada 10 M de bloques** (50 M → 11 GB). Añade que WorldEdit no intenta mantener
  conectados a los clientes mientras edita: aunque el servidor termine, el jugador puede quedar desconectado.
- Un reporte de Spigot dice que las operaciones grandes pueden superar el tiempo límite del servidor
  ([SPIGOT-2194](https://hub.spigotmc.org/jira/si/jira.issueviews:issue-html/SPIGOT-2194/SPIGOT-2194.html)); en Aternos,
  un usuario ve que la última línea del log es su `//paste` y el servidor se apaga sin explicación
  ([Aternos](https://board.aternos.org/thread/103289-worldedit-paste-crashing/)).
- **Comandos concretos que tumban:** «Some //deform commands cause servers to crash»
  ([#2812](https://github.com/EngineHub/WorldEdit/issues/2812)).
- **FAWE resuelve parte, pero no todo:**
  - Se presenta como asíncrono, con modos de colocación por bloques, por chunks (el de fábrica) y por mundo
    ([Modrinth](https://modrinth.com/plugin/fastasyncworldedit)).
  - La cola tiene `target-size` (64) y `max-wait-ms` (1000), que también sirve de tiempo límite para cargar chunks
    ([configuración](https://intellectualsites.gitbook.io/fastasyncworldedit/customization/configuration.md)).
  - **Luz:** al copiar y pegar un bloque que emite luz en Spigot 1.21.10, la memoria del reiluminador crece sin límite
    (un mapa `visited` que llegó a ~800×800 bloques), el GC dispara la CPU y el servidor deja de funcionar; en Paper no se
    reproduce porque usa otro reiluminador ([#3372](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/3372)).
  - Posible carrera con el envío de paquetes: «FAWE potentially writing to ChunkSection PalettedContainer whilst data is
    read for packet sending» ([#2729](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/2729)).
  - Bloques fantasma tras un relleno desde la API, reproducido de Paper 1.20.4 a 1.21.11; un mantenedor sospecha de
    ediciones superpuestas en la misma zona y sugiere bloquear la zona mientras dura una
    ([#3136](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/3136)).
  - Un fork para Folia documenta que toda edición que escribía *tile entities* (cofres, carteles, el `//undo` de esa
    edición) colgaba Paper hasta que el *watchdog* lo mataba ([castledking/FastAsyncWorldEdit-Folia](https://github.com/castledking/FastAsyncWorldEdit-Folia/releases)).
  - Una reseña de la 2.15.2 dice que las ediciones grandes antes funcionaban y ahora dan mucho lag (solo el extracto del
    buscador; la página de [reseñas](https://speedgot.ajg0702.us/resources/13932/reviews) dio 403).

**Lección:** el problema no es «sync contra async» sino **cuánto trabajo cae en un tick y cuánta memoria se acumula**.
Lo seguro en Paper es leer/calcular fuera del hilo principal y **aplicar en tandas con presupuesto de tiempo por tick**,
cargando chunks de forma asíncrona y sin escribir en secciones que se están enviando (propuesta propia, a validar en el plan).

### 1.2 Deshacer e historial
- **WorldEdit guarda 15 acciones** por defecto, en la sesión. Se puede deshacer a otro jugador con
  `//undo <n> <jugador>` mientras esté conectado y **hasta 10 minutos después de salir**
  ([historial](https://worldedit.enginehub.org/en/latest/usage/general/history/)). La página no dice nada de reinicios: es
  memoria de sesión, así que no sobrevive (inferencia; no hay frase explícita).
- **Solo cambios directos:** «WorldEdit only records direct changes»; el agua que fluye y los bloques adosados que se caen
  (puertas, carteles, hierba alta) no quedan en el historial ([historial](https://worldedit.enginehub.org/en/latest/usage/general/history/)).
- **FAWE:** historial ilimitado en disco por mundo, con `//inspect` y `//history rollback`
  ([Modrinth](https://modrinth.com/plugin/fastasyncworldedit)); un registro de cambios viejo dice que las búsquedas de
  rollback pasaron a SQLite y que hay un límite de tamaño de historial por jugador; `//fast` se salta el registro y esas
  ediciones no se pueden revertir (extractos del buscador sobre el [README de un espejo](https://git.telesphoreo.me/Plexus/Plex-FAWE/blame/branch/main/README.md)).
- **Bugs del deshacer de FAWE:** en el gestor de errores de CubeKrowd, `//undo` a veces solo restaura un chunk y FAWE dejó
  de funcionar para un jugador sin errores en el log ([CubeKrowd #169](https://gitlab.com/cubekrowd/bugtracker/-/issues/169)).
- **Restauración de copias:** la restauración desde *snapshots* no incluye entidades ni biomas
  ([#1749](https://github.com/EngineHub/WorldEdit/issues/1749)).
- **Integración con CoreProtect:** en el fork de Folia, un cambio de nombre hizo que CoreProtect dejara de registrar las
  ediciones de WorldEdit, y por tanto no se podían revertir ([releases](https://github.com/castledking/FastAsyncWorldEdit-Folia/releases)).

### 1.3 Curva de aprendizaje
- **Sintaxis de símbolos:** patrones aleatorios con pesos **relativos** (`50%red_wool,10%red_stained_glass` es 5:1, no
  porcentajes), `#clipboard@[2,0,1]`, `^` para cambiar solo tipo o estado (`^acacia_stairs`, `^[waterlogged=false]`),
  estados `[...]` y NBT `{...}` ([patrones](https://worldedit.enginehub.org/en/latest/usage/general/patterns/)).
- **Máscaras de FAWE:** `#existing`, `#solid`, `%50` (probabilidad), `$desert` (bioma), `=expresión`, `!` (negación),
  `>`/`<` (encima/debajo), `#region`, `#offset[0][5][3]` ([máscaras](https://intellectualsites.gitbook.io/fastasyncworldedit/command-utilities/masks.md)).
  El mismo símbolo `%` significa «peso» en un patrón y «probabilidad» en una máscara.
- **Expresiones con tiempo límite:** `calculationTimeout` (100 ms) y `maxCalculationTimeout` (300, ajustable con
  `//timeout`) ([configuración](https://worldedit.enginehub.org/en/latest/config/)).
- **Prefijos `//` y `/`:** unos comandos llevan una barra y otros dos; los alias cortos chocan con otros plugins (ver §5).
- Mensajes con diseño viejo: FAWE tiene abiertos «Update outdated chat messages design»
  ([#3494](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/3494)) y «Add MiniMessage support»
  ([#1725](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/1725)); y errores al sugerir argumentos
  ([#2425](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/2425),
  [#2494](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/2494)).

### 1.4 Selección
- **Hacha de madera** (`wandItem`, cambiable) y sin vista previa en el servidor
  ([configuración](https://worldedit.enginehub.org/en/latest/config/)).
- **Ver la selección exige un mod cliente:** WorldEdit CUI (Fabric/Quilt), «para prevenir errores accidentales»
  ([Modrinth](https://modrinth.com/mod/worldedit-cui)). Existe la alternativa de servidor **WorldEditSUI**, con
  partículas (cuboide, esfera, cilindro, polígonos, portapapeles, regiones de WorldGuard), pero advierte de CPU alta y lag
  con selecciones muy grandes y la limita a 10 M de bloques por defecto
  ([Hangar](https://hangar.papermc.io/kennytv/WorldEditSUI)).
- Errores de selección compleja: puntería de `//hpos` con escala de jugador pequeña
  ([#2642](https://github.com/EngineHub/WorldEdit/issues/2642)); regiones convexas que no aceptan vértices o no se crean
  ([#1438](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/1438), [#2805](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/2805)).
- **Bedrock:** WorldEdit no soporta Bedrock ([Modrinth](https://modrinth.com/plugin/worldedit)). No se encontró una fuente
  fiable sobre usar la varita desde Geyser: queda **sin verificar** (la suite de la fábrica ya tiene un jugador de Bedrock,
  spec 013, y puede comprobarlo).

### 1.5 Esquemas
- WorldEdit 7 (1.13) escribe solo **Sponge `.schem`**; el `.schematic` de MCEdit se importa pero ya no se escribe
  ([portapapeles](https://worldedit.enginehub.org/en/latest/usage/clipboard/)).
- Carpeta `plugins/WorldEdit/schematics`; compartir carpetas entre instalaciones exige `allow-symbolic-links`;
  `//schem share` sube a EngineHub Paste y **se borra al mes** ([portapapeles](https://worldedit.enginehub.org/en/latest/usage/clipboard/)).
- Peticiones abiertas: soporte de `.litematic` ([#1287](https://github.com/EngineHub/WorldEdit/issues/1287)) y pegar uno
  al azar de un conjunto ([#2063](https://github.com/EngineHub/WorldEdit/issues/2063)).
- **Pegado incompleto** (lo más comentado de WorldEdit, 18): bloques que faltan o quedan mal conectados
  ([#1396](https://github.com/EngineHub/WorldEdit/issues/1396)); pistones extendidos que no se reemplazan
  ([#2956](https://github.com/EngineHub/WorldEdit/issues/2956)). En FAWE: entidades que no se pegan
  ([#3426](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/3426)), pasajeros
  ([#2427](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/2427)), *block entities* mal copiados
  ([#1548](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/1548)), NBT que no se guarda
  ([#3299](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/3299)).

### 1.6 Permisos y límites por jugador
- **WorldEdit trae sin límite:** `defaultChangeLimit` y `maxChangeLimit` valen **-1**; `maxRadius` también -1. Solo los
  puntos de polígono tienen tope (20) ([configuración](https://worldedit.enginehub.org/en/latest/config/)). Es decir: quien
  tenga el comando puede pedir una edición de cualquier tamaño y tumbar el servidor (§1.1). Un hosting recomienda poner
  `maxChangeLimit=50000` a mano ([Mado Hosting](https://www.mado-hosting.com/en/minecraft-mods/worldedit), secundaria).
- **FAWE:** grupos de límites por permiso `fawe.limit.<grupo>` (el grupo `unlimited` lo quita todo), `fawe.bypass` se
  salta límites y restricciones, `fawe.admin` con `/wea` ([permisos](https://intellectualsites.gitbook.io/fastasyncworldedit/customization/permissions.md)).
  Límites por jugador de entidades, *tiles*, memoria y cambios ([Modrinth](https://modrinth.com/plugin/fastasyncworldedit)).
- **«Use Inventory» de FAWE no funciona como se espera** ([#2534](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/2534)):
  el modo supervivencia (gastar bloques del inventario) es frágil.

### 1.7 Protección de regiones
- FAWE restringe a las regiones donde el jugador tiene permiso con `region-restrictions: true` y nodos por protector:
  `fawe.worldguard`, `fawe.worldguard.member`, `fawe.plotsquared(.member/.admin)`, `fawe.griefprevention`, `fawe.towny`
  ([permisos](https://intellectualsites.gitbook.io/fastasyncworldedit/customization/permissions.md)).
- **Bug de escape:** el PR [#1948](https://github.com/IntellectualSites/FastAsyncWorldEdit/pull/1948) corrigió que algunos
  métodos con coordenadas directas en `FaweRegionExtent` no pasaban por la comprobación de la región. Es decir, la
  restricción dependía de que cada camino de código la recordara.
- FAWE «no escribe en el *extent* de la sesión sino en el mundo» ([#2121](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/2121)):
  otro camino por el que una capa de filtrado se puede saltar.

### 1.8 Folia
- WorldEdit lista Folia ([Modrinth](https://modrinth.com/plugin/worldedit)). FAWE **no**: el issue
  [#2297](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/2297) sigue abierto desde 2023; los mantenedores
  dicen que el código no es estable, que les falta tiempo y que otros PR de Folia no son seguros. Hangar decía «Supports
  Folia» pero las builds de Folia solo eran para 1.20.4 y sin soporte
  ([Hangar](https://hangar.papermc.io/IntellectualSites/FastAsyncWorldEdit)).

### 1.9 Corrupción y datos perdidos
- FAWE «WNA corrupt tiles»: su acceso nativo al mundo sincroniza mal las *tile entities*; abierto, reabierto en 2024 porque
  «todavía no funciona bien» ([#1371](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/1371)).
- Paleta de bloques limitada a 65.535 estados por usar `char[]` ([#1938](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/1938)).
- Cuelgues aleatorios por un `NullPointerException` interno ([#2406](https://github.com/IntellectualSites/FastAsyncWorldEdit/issues/2406)).
- `//chunk` y `//regen` con semilla equivocada ([#2398](https://github.com/EngineHub/WorldEdit/issues/2398)).

## 2. Qué hacen bien otras herramientas y qué tomar

| Herramienta | Qué resuelve | ¿Se puede en un plugin sin mod cliente? |
|---|---|---|
| **FAWE** | Async por chunks, historial ilimitado en disco por mundo, `//inspect` y `//history rollback`, límites por grupo de permiso, restricción a regiones de WorldGuard/PlotSquared/GriefPrevention/Towny ([Modrinth](https://modrinth.com/plugin/fastasyncworldedit), [permisos](https://intellectualsites.gitbook.io/fastasyncworldedit/customization/permissions.md)) | **Sí, todo.** Tomar la idea (historial en disco consultable por jugador, tiempo y zona; límites por permiso) sin copiar código. Evitar su escritura nativa con NMS, origen de los bugs de *tiles* y luz. |
| **Axiom** (Fabric, propietario) | Herramientas visuales: selección mágica, caja, a mano alzada y lazo; pintores (ruido, bioma, degradado); dibujo, esculpido, texto, caminos; *gizmos* con flechas de eje, anillos de rotación y nodos de escala; historial que muestra cada paso y cuántos bloques tocó, con clic para volver ([docs](https://axiomdocs.moulberry.com/print.html)). La persistencia del historial y el multijugador son de pago ([Modrinth](https://modrinth.com/mod/axiom)). El plugin de servidor exige OP o `axiom.*` ([AxiomPaperPlugin](https://github.com/Moulberry/AxiomPaperPlugin)) | **Parcial.** Gizmos y vista previa: con *block displays* (escalados, brillantes) y bloques falsos enviados solo al jugador. El historial con «cuántos bloques» y clic para volver: un menú de inventario o mensajes clicables. Que nuestro historial persistente sea **gratis** es un diferenciador directo. |
| **Arceon** (Arcaniax, de pago vía Patreon) | Plugin de servidor: *loft* (formas orgánicas, tela, curvas a partir de un contorno), techos, cuerdas, caminos, ríos, arcos, texto, máscaras y patrones de ruido (voronoi, celdas, fractal) ([wiki](https://github.com/ArcaniaxGit/Arceon-1.14/wiki/Config), [creatorskingdom](https://creatorskingdom.odoo.com/blog/creative-server-2)). Su config pone un tope a los *lofts* (`max-frames`/`max-points` 50) con permiso para saltarlo y dibuja la selección con partículas `FLAME` | **Sí** (ya es un plugin). Tomar: generadores de alto nivel («techo», «camino») y topes por herramienta. Documentación pública escasa: solo nombres de herramientas y la config. |
| **VoxelSniper / FAVS** | Edición a larga distancia con pinceles para terreno; FAVS se engancha a FAWE para ir async ([Modrinth](https://modrinth.com/plugin/fastasyncvoxelsniper)). `/b` es el alias de `/brush` ([referencia](https://github.com/Aquafina-water-bottle/VoxelSniper/wiki/Command-Reference)) | **Sí.** Pinceles por rayo desde la mirada; para después del núcleo. |
| **goBrush / goPaint** | goBrush: terreno con imágenes de mapa de alturas (18 incluidas), GUI, deshacer vía WorldEdit ([9minecraft](https://www.9minecraft.net/?p=411573), espejo). goPaintAdvanced: pincel «Feather» que pinta conservando la rotación, menú para ajustar, exportar e importar pinceles; Paper y Folia ([Modrinth](https://modrinth.com/plugin/gopaintadvanced)) | **Sí.** Tomar: **configurar el pincel en un menú** en vez de argumentos, y pinceles exportables. |
| **Litematica** (solo cliente) | Esquemas como «ghost blocks» para construir encima, verificador y lista de materiales ([Modrinth](https://modrinth.com/mod/litematica)) | **Parcial.** Holograma de un pegado: *block displays* o bloques falsos solo para el jugador antes de confirmar; lista de materiales sí; verificador (diferencias con el mundo) sí. |
| **Amulet / MCEdit** | Editor externo del mundo; Amulet se presenta como sucesor de MCEdit, que no se actualiza hace años; un artículo dice que Amulet no deja desactivar los puntos de deshacer automáticos, que tardan ([premiumminecraft](https://premiumminecraft.com/?p=12561), secundaria). Su licencia actual no se pudo confirmar | **No** (es fuera del juego). Tomar solo la idea de que el deshacer puede ser caro: hacerlo **asíncrono y opcional por operación**. |
| **BuildPaste** | Biblioteca en línea de construcciones para pegar al instante; `/upload <nombre>`; por defecto solo OP pega ([wiki](https://buildpaste.net/wiki), [Modrinth](https://modrinth.com/plugin/buildpaste)) | **Sí**, pero depende de un servicio externo: para después y con permiso del dueño. |
| **WorldEdit CUI** | Ver la selección | Ya existe el equivalente de servidor (WorldEditSUI, partículas). Nosotros: contorno con *block displays* o partículas, de fábrica. |
| **UltimatePlayerWorldEdit** (de pago) | Vista previa de `/set`, `/replace`, `/paste`, `/walls` con bloques falsos del lado del cliente, sin tocar el servidor hasta confirmar (según el extracto del buscador; la página dio 403: [speedgot](https://speedgot.ajg0702.us/resources/121278)) | **Sí**: demuestra que la vista previa con bloques falsos es viable en un plugin. |
| **CoreProtect** | Registro con `/co i`, búsqueda y rollback por usuario (`u:`), tiempo (`t:5d2h`), radio (`r:`, incluso `r:#worldedit`), acción (`a:`), incluir/excluir (`i:`/`e:`), **`#preview`** antes de aplicar, `restore` para deshacer un rollback y `purge` de datos viejos ([comandos](https://docs.coreprotect.net/commands/)) | **Sí.** Tomar la gramática `u:/t:/r:` para el historial y **vista previa del rollback**. Registrar nuestras ediciones de forma que CoreProtect las vea (el fork de Folia lo rompió una vez). |
| **TinyBuilds** | Copias escaladas de construcciones con *block displays* ([modpackindex](https://www.modpackindex.com/mod/163869/tinybuilds)) | **Sí**: prueba que un esquema se puede mostrar en miniatura con *displays*. |

### Vista previa sin mod cliente: tres técnicas (propuesta propia, a probar en la compuerta 2)
1. **Partículas** (como WorldEditSUI y Arceon): baratas para contornos, pero se ven mal de lejos y cuestan CPU con
   selecciones enormes ([WorldEditSUI](https://hangar.papermc.io/kennytv/WorldEditSUI)).
2. **Block displays** (1.19.4+): aristas delgadas con escala, brillo y color; un holograma del pegado en miniatura o a
   tamaño real (TinyBuilds). Son entidades: poner tope por jugador.
3. **Bloques falsos solo para el jugador** (`Player#sendBlockChange`/`sendMultiBlockChange`): la vista previa exacta de
   un `set` o `paste` antes de confirmar (UltimatePlayerWorldEdit). Se pierden al recargar el chunk: reenviar o expirar.

## 3. Dolor → propuesta para nuestro plugin

| # | Dolor (fuente §1) | Propuesta | Prioridad | Costo |
|---|---|---|---|---|
| 1 | Ediciones grandes congelan o tumban el servidor (#568, FAQ) | **Motor por tandas**: calcular fuera del hilo principal, aplicar con presupuesto de ms por tick (configurable), carga async de chunks, barra de progreso (boss bar) y `cancel` | Núcleo | Alto |
| 2 | Sin límites de fábrica (`-1`) | **Límites seguros por permiso** (`<plugin>.limit.<grupo>`: bloques por operación, operaciones simultáneas, radio, volumen de selección), con valores bajos de fábrica y aviso si se excede | Núcleo | Bajo |
| 3 | No se ve la selección sin mod | **Contorno con block displays** (o partículas, configurable), visible solo para el dueño, con tope de tamaño | Núcleo | Medio |
| 4 | Se aplica a ciegas | **Vista previa antes de confirmar** de `set`/`replace`/`paste` con bloques falsos solo para el jugador; `confirm` aplica, `cancel` descarta, expira sola | Núcleo (al menos `paste`) | Medio |
| 5 | Deshacer de 15 pasos, en memoria, se pierde | **Historial persistente en disco** por jugador (comprimido, con días de retención y MB máximos), `undo`/`redo` tras reinicio | Núcleo | Medio |
| 6 | Sintaxis `^`, `%`, `#`, `=` | **Máscaras y patrones legibles** (`50% stone, 50% andesite` con porcentajes reales; `only stone`, `not air`, `above grass`), con autocompletado; aceptar también la forma corta de WorldEdit | Núcleo (subconjunto) | Medio |
| 7 | Restricción a regiones con escapes (PR #1948, #2121) | **Una sola puerta de escritura**: toda colocación pasa por un único punto que consulta la protección; integración WorldGuard como dependencia opcional | Núcleo (la puerta) / después (integraciones) | Medio |
| 8 | Ediciones superpuestas dan bloques fantasma (#3136) | **Bloqueo por zona**: no se aceptan dos ediciones que se cruzan; la segunda espera en cola | Núcleo | Bajo |
| 9 | Rollback de otro jugador solo 10 min tras salir | **Rollback por jugador, tiempo y zona** (`u:`, `t:`, `r:` al estilo CoreProtect) con `#preview` | Después | Medio |
| 10 | Esquemas `.schem` | **Importar y exportar Sponge Schematic v3** (y leer v2); carpetas por jugador; metadatos (autor, fecha) | Después (2.ª spec) | Medio |
| 11 | Pegado pierde entidades, *tiles*, conexiones (#1396, #3426, #1548) | Copiar *tile entities* y entidades con la API de Paper; pruebas de ida y vuelta en la compuerta 2 | Después | Alto |
| 12 | Luz y biomas rotos, NMS (#3372, #1371) | **Solo API de Paper**, nada de NMS: más lento, pero sin corrupción; la luz la recalcula Paper | Núcleo (decisión) | Bajo |
| 13 | Herramientas visuales de Axiom requieren mod de pago | Gizmos con *block displays* para mover/rotar el portapapeles; historial clicable con «N bloques» | Después | Alto |
| 14 | Pinceles con argumentos largos | Pinceles configurables en menú, exportables (goPaint); pincel por rayo (VoxelSniper) | Después | Medio |
| 15 | Folia sin soporte en FAWE (#2297) | Diseñar el motor por región desde el inicio (planificador de Paper por región); verificar Folia más tarde | Después | Alto |
| 16 | Mensajes viejos, sin MiniMessage (#3494, #1725) | Mensajes MiniMessage en `messages.yml`, en inglés (ADR 0005) | Núcleo | Bajo |
| 17 | Bedrock sin verificar | Varita y comandos usables por un jugador de Geyser (la suite ya lo tiene, spec 013) | Después | Bajo |
| 18 | Modo supervivencia frágil (#2534) | Consumir bloques del inventario como opción de límite | Después | Medio |

**Núcleo sugerido para la primera spec (una sesión):** filas 1, 2, 3, 4 (solo `paste` o `set`), 5, 6 (subconjunto: bloque,
lista con porcentajes, `not`), 7 (la puerta única, sin integraciones), 8, 12 y 16. Operaciones mínimas: `pos1/pos2`,
`set`, `replace`, `copy`, `paste`, `undo`, `redo`, `cancel`. Si no cabe, sacar primero la vista previa de `set`
(dejar la de `paste`) y luego `replace`.

## 4. Riesgos legales y de marca

- **Nombre:** no usar «WorldEdit», «FAWE» ni «Edit» pegado a esas marcas en el nombre, el comando ni la descripción
  principal. Decir «compatible con esquemas `.schem`» es descriptivo; «el nuevo WorldEdit» no. No se encontró una
  política de marca pública de EngineHub: el criterio es prudencia, no una regla citada.
- **Minecraft:** las pautas de uso no dejan usar «Minecraft» como nombre principal o dominante; se permite en un título
  secundario con la aclaración de que no es oficial ([copia de las pautas](https://git.gay/Maven/mcemoji/src/branch/main/LICENSE.MD),
  [resumen de terceros](https://conductatlas.com/platform/minecraft/minecraft-usage-guidelines/provision/CA-P-025283/no-minecraft-name-as-primary-or-dominant-title/);
  la página oficial no se pudo abrir).
- **Licencias:** WorldEdit es **GPLv3** sin excepciones propias ([LICENSE.txt](https://github.com/EngineHub/WorldEdit/blob/master/LICENSE.txt));
  FAWE, FAVS y goPaintAdvanced son GPL-3.0-only ([Modrinth](https://modrinth.com/plugin/fastasyncworldedit)). Copiar
  código obligaría a publicar el plugin bajo GPLv3. **Regla:** no leer ni copiar su código para implementar; las ideas
  y la sintaxis no se copian como texto, se rediseñan. Axiom es propietario (ARR + EULA): ni su código ni sus recursos.
  Litematica es LGPL-3.0, pero su formato `.litematic` no hace falta en el núcleo.
- **No depender de la API de WorldEdit** en el núcleo: enlazar con una API GPL tiene sus propias implicaciones; si se
  integra más tarde, que sea opcional y por reflexión o módulo separado (a decidir en el ADR de la spec).
- **Formato `.schem`:** la especificación de Sponge es pública
  ([repositorio](https://github.com/SpongePowered/Schematic-Specification)); v3 (2021) define `Schematic` con `Version`,
  `DataVersion`, `Width/Height/Length`, `Offset`, `Metadata` (Name, Author, Date, RequiredMods), `Blocks` (`Palette`,
  `Data` en *varint* con índice `x + z*W + y*W*L`, `BlockEntities`), `Biomes` y `Entities`; NBT comprimido con GZip y
  extensión recomendada `.schem` ([v3](https://raw.githubusercontent.com/SpongePowered/Schematic-Specification/master/versions/schematic-3.md)).
  El documento no declara licencia propia: implementar un formato desde su especificación es independiente del código de
  WorldEdit, pero conviene revisar la licencia del repositorio antes de copiar texto de la especificación a nuestra
  documentación.
- **Servicios externos** (subir esquemas, como `//schem share` o BuildPaste): solo con aprobación del dueño, igual que
  los plugins de terceros de la spec 012.

## 5. Ideas de nombre (en inglés)

Comprobación: búsqueda en la API de Modrinth (`/v2/search?query=…`), en la API de Hangar y en el buscador web. SpigotMC
y CurseForge no se pudieron consultar directamente (SpigotMC da 403); se buscó con el buscador web.

| Nombre | Por qué | Resultado de la búsqueda |
|---|---|---|
| **Blockwright** (candidato del dueño) | «-wright» = artesano que construye (como *shipwright*): suena a oficio | 0 resultados en [Modrinth](https://api.modrinth.com/v2/search?query=blockwright&limit=10) y Hangar; nada en SpigotMC/CurseForge según el buscador. **Pero ya existen dos herramientas de Minecraft con ese nombre:** una app de escritorio para ver y editar esquemas y mundos (v1.0.0, [GitHub](https://github.com/matheussartori/blockwright-releases/releases)) y un servidor MCP que construye en un servidor Java por RCON con deshacer ([glama.ai](https://glama.ai/mcp/servers/ThatHunky/blockwright/tree)). Mismo nicho (editar mundos): **riesgo de confusión medio** |
| **Voxloom** | «voxel» + «loom» (telar): tejer el mundo bloque a bloque | 0 en Modrinth y Hangar; nada en el buscador |
| **Blockshaper** | Dice lo que hace, sin marca ajena | 0 en Modrinth; existe un mod «Shaper» (nombre parecido, no igual) |
| **Chiselworks** | Taller de cincel: precisión | 0 en Modrinth; ojo, «Chisel» es un mod conocido, puede sonar a extensión suya |
| **Groundwork** | «Trabajo de base»: preparar terreno y estructura | 0 en Modrinth; palabra común en inglés, difícil de posicionar en búsquedas |
| **Terraloom** | Telar del terreno | 0 en Modrinth, pero **existe un modpack «Terraloom»** ([modpackindex](https://www.modpackindex.com/modpack/106822/terraloom)): descartar |
| ~~Stratum~~ | — | Varios proyectos en Modrinth con ese nombre: descartado |
| ~~Plinth~~ | — | Una biblioteca de mods en Modrinth: descartado |

**Alias `/b`:** choca con **VoxelSniper/FAVS**, donde `/b` es `/brush`
([referencia](https://github.com/Aquafina-water-bottle/VoxelSniper/wiki/Command-Reference)), y los alias de una letra
tienen historial de choques (VoxelSniper `/p` contra mcMMO, `/v` de Essentials:
[issue](https://github.com/tvpt/voxelsniper/issues/6), [foro](https://www.planetminecraft.com/forums/help/javaedition/voxelsniper-not-working-376700/)).
Recomendación: comando principal largo (`/blockwright` o el que se elija) y un alias corto **configurable** en
`config.yml`, que no se registre si otro plugin ya tiene esa etiqueta (Paper permite `plugin:comando` como salida de
emergencia); por defecto, un alias de 2–3 letras menos disputado que `/b`.

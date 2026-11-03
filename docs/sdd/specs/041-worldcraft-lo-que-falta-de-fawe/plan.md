# Plan 041 — Lo que le falta de FAWE 2.16.2 (Worldcraft 0.16.0)

Spec: [spec.md](spec.md) · Respuestas del dueño: grupos a, b, d y e; el historial a otra spec; solo `//fixlighting` por
el puente NMS ([ADR 0009](../../../adr/0009-nms-aislado.md), ampliado).

Los nombres, argumentos y valores de fábrica salen de las anotaciones `@Command`/`@Arg`/`@Switch` del bytecode de FAWE
2.16.2 (`javap -v`, lección 029), solo como dato; todo se escribe desde cero.

## 1. Diseño

### 1.1 Pinceles nuevos (`ToolKind`, `ToolParser`, `ToolActions`)
`LINE`, `SPLINE`, `SURFACESPLINE`, `SWEEP`, `CATENARY`, `CIRCLE`, `PULL`, `RECURSIVE`, `SHATTER`, `STENCIL`, `IMAGE`,
`HEIGHT`, `POPULATE` (`populateschematic`, alias `populateschem`, `popschem`, `pschem`, `ps`), `COPYPASTE` (`cp`,
`copypasta`) y la herramienta `INSPECT`, `FEATUREPLACER`, `STRUCTUREPLACER`. Alias de FAWE (`l`, `spl`, `curve`, `sw`,
`vaesweep`, `cat`, `gravityline`, `saggedline`, `sspline`, `sspl`, `recurse`, `r`, `partition`, `split`, `heightmap` ya
existe: `height` es otro pincel).
- **Puntos por clic** (line, spline, surfacespline, sweep, catenary): `ToolActions` guarda en memoria, por jugador, los
  puntos del pincel de la mano (se olvidan al cambiar de pincel o desconectarse). line/catenary: el segundo clic traza;
  spline/sweep: un clic en el mismo bloque que el último cierra.
- **Clic izquierdo**: hoy un pincel de construcción no hace nada con él. `copypaste` (copiar) y `/wc secondary` lo usan.
- `savebrush`/`listbrush`/`loadbrush` son palabras de `/wc brush` (no pinceles): el ítem guarda el texto `encode()` del
  `ToolSpec` en `brushes/<uuid>/<nombre>.txt`.

### 1.2 Cálculo (Java puro, con pruebas) — `tool/LineMath`, `tool/BrushMath`
- `LineMath.catenary(a, b, factor, sagDir, grosor)`: la curva `y = a·cosh(x/a)` entre dos puntos con largo `factor·d`,
  `a` por bisección de `2a·sinh(d/2a) = sqrt(L² − h²)`; muestreada a medio bloque con bolas como `Line`.
- `LineMath.circle(cx, cy, cz, normal, r, fill)`: dos vectores perpendiculares a la mirada; el anillo (o el disco) con
  la regla de la esfera de la 028 (r + ½).
- `LineMath.flat`, `LineMath.shell`: la línea a una altura; la cáscara de un conjunto (con un vecino de las 6 caras fuera).
- `LineMath.sweepPlacements(curva, largo, copias)`: los puntos de la curva (por largo de arco) donde va cada copia y
  hacia dónde gira (el giro de 90° más cercano a la tangente en XZ).
- `BrushMath.pull(foto, centro, r, dirección, pasadas)`, `BrushMath.shatter(foto, centro, r, n, semilla)` (Voronoi: borde
  = un sólido con un vecino de las 6 caras con otra semilla más cercana), `BrushMath.stencil(...)`, `BrushMath.surface(...)`
  (la cima de cada columna para `surfacespline`, `image`, `stencil`, `populateschematic`), `BrushMath.fall(...)` (para
  `//fall`, como `gravity` pero por columna de la selección), `BrushMath.cone(side)` (la imagen de fábrica).
- `edit/TransformSource` (H3 CA3): envuelve una fuente; recorre la de adentro y por cada punto da el (o los) transformados:
  `offset`, `rotate` (90/180/270 en Y alrededor del centro de la caja), `flip`, `spread` (desplazamiento al azar con la
  semilla de la obra, por posición), `scale` (cubo de n). `target(x, y, z, actual)` pregunta a la de adentro por su punto
  de origen. `bounds()` = la caja transformada (spread la agranda n). Las posiciones salen sin orden de chunks: el
  trabajo ya espera cada chunk (EditJob).
- `edit/SourceMaskedSource` (H3 CA2, CA4): deja (KEEP) lo que la fuente pondría y no cumple la máscara (por estado).
- `edit/Transforms.parse(texto)`: `#offset[1][2][3]` o `#offset:1,2,3`, `#rotate[90]`, `#flip[x]`, `#spread[3]`,
  `#scale[2]`; errores con «did you mean».

### 1.3 Global y pincel de la mano
- `Session`: `gsmask` (Mask), `gtransform` (Transforms), `texture` (BlockColors o null), `bypass` (wea), `clipboards`
  (lista de `schem loadall`), `loadedFile` (para `schem move`).
- `Edits.request`: después de `gmask`, `gsmask` y `gtransform` envuelven la fuente (y la vista previa); los límites y el
  evento ven la caja transformada. `wea`: si el evento se cancela y la sesión tiene `bypass` (y el permiso), sigue.
- `Parser.texture` (ThreadLocal): `WorldcraftCommand.onCommand` y `Tools.onInteract` lo ponen con la textura de la
  sesión mientras se lee el patrón; `Parser.colors()` devuelve la textura si hay. Así los patrones por color y la imagen
  eligen entre esos bloques sin pasar la sesión por todo el código.
- En el ítem (`ToolSpec`): `smask`, `transform`, `secondary` (Base64), `target` (`block|forward|height|face`), `toffset`,
  `scroll` (acción) y `scroll_values` (Base64, lista con `|`). `ToolActions.target` usa `target` y `toffset`; `request`
  aplica `smask` y `transform` del pincel.
- `/wc scroll`: `Tools` escucha `PlayerItemHeldEvent`: si la casilla nueva es la de al lado (±1, con la vuelta 8↔0) y el
  ítem de la vieja es un pincel con `scroll`, cancela y cambia el valor.

### 1.4 Comandos nuevos de `/wc` (`command/FaweCommands`)
`gtexture`, `gsmask`, `gtransform`, `smask`, `transform`, `target`, `targetoffset`, `scroll`, `primary`, `secondary`
(`worldcraft.use`); `place` (en `WorldcraftCommand.paste` con `untransformed`); `fall`, `fixblocks` (→ `update`),
`nbtinfo`, `registry`, `wer` (`worldcraft.use`); `wea` (`worldcraft.bypass`); `fixlighting` (`worldcraft.use`);
`debugpaste` (`worldcraft.admin`). `schem loadall|move` dentro de `/wc schem`. Ayuda, autocompletado y `slash-commands.yml`
(`//gtexture`, `//gsmask`, `/globalsourcemask`, `//gtransform`, `/smask`, `/sourcemask`, `/transform`, `/target`,
`/tar`, `/targetoffset`, `/to`, `/scroll`, `/primary`, `/secondary`, `//place -a -o -s -n -e -b -x`, `//fall -m`,
`//fixblocks`, `//updateblocks`, `//fixconnect`, `//nbtinfo`, `//nbt`, `//wea`, `//worldeditanywhere`, `//wer`,
`//worldeditregion`, `//registry`, `//fixlighting`, `worldedit debugpaste`).
- `nbtinfo`: `TileNbt` (los bytes del bloque con datos) → `Nbt` → `TextNbt` (SNBT corto, cortado a 30 líneas).
- `registry`: tabla fija nombre → `Registry` de Bukkit (`Registry.MATERIAL` para block/item con `isBlock`/`isItem`,
  `BIOME`, `ENTITY_TYPE`, `ENCHANTMENT`, `EFFECT`, `SOUNDS`, `STRUCTURE`, `PARTICLE_TYPE`, `POTION`, `GAME_EVENT`) y
  `feature` del puente NMS.
- `wer`: dispara `WorldcraftAllowedRegionEvent(player, location)`; el primero que pone una caja gana; se selecciona.
- `tool inspect`: `HistoryStore.all()` filtra por mundo y caja que contiene el bloque; lee los diarios en el hilo de E/S
  y busca el punto; ordena por hora.
- `debugpaste`: el texto de `report` + las últimas 200 líneas de `logs/latest.log` + `config.yml`, `messages.yml`,
  `slash-commands.yml` (si está), `item-uses.yml`.

### 1.5 La luz (`paper/Nms.relight`, ADR 0009 ampliado)
Por reflexión: `ServerLevel.getChunkSource().getLightEngine()` es un `ThreadedLevelLightEngine` con
`relightChunks(Set<ChunkPos>, Consumer<ChunkPos>, IntConsumer)` (el de `/paper fixlight`). El puente no cambia ningún
bloque: le pide al juego que recalcule la luz de esos chunks y avisa al terminar. Apagado aparte (`lightProblem`): si
falta solo eso, `feature` y `update` siguen.

## 2. Contrato de configuración
```yaml
tools:
  max_target_offset: 16            # how far /wc targetoffset can move where a brush lands (0..64)
  max_spline_points: 64            # points a line, spline or sweep brush keeps (2..256)
  fixlighting_max_chunks: 64       # chunks /wc fixlighting relights at once (1..1024)
```
Las claves nuevas tienen valor de fábrica: un `config.yml` viejo funciona igual (avisa nada). `brushes/` se crea al
guardar el primero.

## 3. STRIDE
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| **T**ampering: `savebrush` con `../` escribe fuera de `brushes/` | Archivos del servidor | Nombre `[A-Za-z0-9_-]{1,32}`; la ruta se normaliza y tiene que quedar dentro |
| **E**levación: un pincel guardado o `secondary` arma algo que el jugador no puede | Saltarse permisos | El pincel se revisa al cargar como al escribirlo y en cada clic con los permisos de quien lo usa |
| **E**: `wea` deja editar en zonas protegidas | Saltarse protecciones | Permiso propio `worldcraft.bypass` (op), se mira en cada obra; el evento lo dice (`isBypassing`) y queda en la consola |
| **D**enegación: `gtransform #scale[4]`, `#spread` o un pincel grande | Obra enorme | Los límites se miden después de transformar (volumen × n³); `scale` 1–4, `spread` 0–16 |
| **D**: `fixlighting` de muchos chunks | Tick largo | Lo hace el motor de luz del juego en su hilo; tope `fixlighting_max_chunks` |
| **D**: `tool inspect` lee muchos diarios | E/S | Solo los de cajas que contienen el bloque, en el hilo de E/S, los 10 más nuevos; un clic a la vez |
| **I**nformación: `tool inspect` muestra quién editó | Privacidad entre jugadores | Permiso `worldcraft.moderate` (el mismo que mirar el historial de otro) |
| **I**: `debugpaste` vuelca logs | Datos del servidor | Solo administración, al disco del servidor, nunca a la red |
| **T**: el puente de luz | Cambios sin control | Solo pide recalcular luz; nunca bloques (lo comprueba la compuerta 2) |
| **D**: `/wc scroll` atrapa la barra | No poder cambiar de ítem | Solo la casilla de al lado; los números siguen; `/wc scroll none` |

## 4. Pruebas
- Unitarias primero (que fallen sin el código): `LineMathTest` (catenaria: pasa por los extremos, cuelga, factor 1 =
  recta; círculo perpendicular; línea plana y cáscara; reparto del barrido), `BrushMath041Test` (pull, shatter, stencil,
  fall), `TransformsTest` (cada transformación, errores, caja), `SourceMaskTest`, `ToolParser041Test` (los pinceles
  nuevos con alias y opciones, `target`/`scroll` en el ítem, nombres de `savebrush`), `SlashTableTest` (los nombres
  nuevos), `PresentacionTest` (las cifras de la tabla).
- Compuerta 2: `selftest` con la luz (un chunk recalculado sin cambiar bloques), `fixlighting`, `registry`, `debugpaste`.
- Suite 3a: un escenario por grupo y los más visibles (line, spline, catenary, circle, recursive, shatter, stencil,
  copypaste, savebrush/loadbrush, populateschematic, gsmask, gtransform, target/targetoffset, scroll, secondary, place,
  fall, nbtinfo, wea/wer con el plugin de ejemplo, registry, schem loadall/move, tool inspect, featureplacer, fixlighting).

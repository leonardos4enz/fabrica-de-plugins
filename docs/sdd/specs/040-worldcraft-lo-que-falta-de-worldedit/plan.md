# Plan 040 — Lo que le falta de WorldEdit 7.3.19 (Worldcraft 0.15.0)

Spec: [spec.md](spec.md) · Respuestas del dueño: los cuatro grupos; *features* con NMS aislado ([ADR 0009](../../../adr/0009-nms-aislado.md)).

## 1. Diseño

### 1.1 Comandos nuevos de `/wc` (`command/MoreCommands`)
17 subcomandos con su permiso, ayuda (`messages.yml`) y autocompletado, en una clase propia como las de la 028–036:
`curve`, `snowsmooth`, `fillr`, `feature`, `update` (`worldcraft.use`); `toggleplace`, `placement`, `toggleeditwand`,
`range`, `tracemask` (`worldcraft.use`); `chunkinfo`, `listchunks`, `searchitem`, `version`, `trace`, `tz`
(`worldcraft.use`) y `report` (`worldcraft.admin`). `schem formats` va dentro de `/wc schem`. Todos reciben `//x` solos
(la 039); `slash-commands.yml` suma los nombres de una barra de WorldEdit (`toggleplace`, `placement`, `toggleeditwand`,
`range`, `tracemask`, `chunkinfo`, `listchunks`, `searchitem`, `/curve`, `/fillr`, `/feature`, `/update`, `/snowsmooth`)
y las banderas (`//curve -h`, `//snowsmooth -m`, `/searchitem -b -i`).

### 1.2 Cálculo (Java puro, con pruebas)
- `region/Curve`: Catmull-Rom uniforme (el de WorldEdit: Kochanek-Bartels con tensión, sesgo y continuidad 0) entre los puntos,
  muestreado a medio bloque, con bolas de radio `grosor` como `Line`; `hollow` = los puntos del conjunto con un vecino
  de las 6 caras fuera.
- `edit/SnowMath`: columna → altura en octavos (cima del suelo × 8 + capas); alisado con el núcleo gaussiano 5×5 de
  WorldEdit (`HeightMapFilter` 1,4,6,4,1) por pasadas; capas nuevas = altura − suelo × 8 entre 0 y 8; solo cambia capas
  de nieve sobre un suelo que las aguanta; `snow` del pincel (con `stack`).
- `tool/BrushMath.morph`: erosión y dilatación por pasadas sobre la foto (6 caras), el bloque más común (empate: el
  menor id, determinista).
- `tool/BrushMath.heightmap`: imagen gris (0..1) en el círculo → cuánto sube o baja cada columna; copia la cima.
- `tool/ItemUses`: lee `item-uses.yml` (reglas `máscara -> patrón` y `above máscara -> patrón`, ítems con `*`) y
  valida con el parser de máscaras y patrones.
- `tool/Stacker`: los puntos a repetir desde un bloque en una dirección hasta el alcance o el primero que no cumple.
- `command/ChunkInfo`: chunk, región y lugar dentro; la lista de la selección por páginas.

### 1.3 Pinceles y herramientas (`ToolKind`, `ToolParser`, `ToolActions`)
`SNOW`, `SNOWSMOOTH`, `BIOME`, `MORPH`, `DILATE`, `HEIGHTMAP`, `FEATURE`, `APPLY` (brush `apply` y `paint … item`, con
`density`) y la herramienta `STACKER`. `/wc brush paint` con la palabra `item` es `APPLY`; sin ella, el terreno. Todo
termina en `Edits.request` (límites, máscara del pincel y global, deshacer). Lo que mira el mundo lee la foto por
tandas y calcula en el hilo de cálculo (como la 032).

`/wc range` y `/wc tracemask` guardan `reach=` y `trace=` (Base64) en el ítem; `ToolActions.target` los usa: con
`trace`, recorre con `BlockIterator` hasta el primer bloque que cumple la máscara (la máscara se compila al escribirla).

### 1.4 Dónde se pone (`Session.placement`)
`Session` guarda `placement` (`player`, `pos1`, `pos2`, `center`) y un desplazamiento. `BuildCommands.place` (formas y
utilidades) y `feature` lo consultan cuando no hay `--at`. `toggleplace` alterna `player` ↔ `pos1`.
`Session.wandOff` lo mira `Wand.onInteract`. `Session.zone` (para `RestoreCommands.zone`) y `Session.trace`.

### 1.5 El puente NMS (`paper/Nms`, ADR 0009)
Al arrancar, por reflexión: `CraftWorld.getHandle()`, `MinecraftServer.registryAccess().lookupOrThrow(Registries.
CONFIGURED_FEATURE)`, `ConfiguredFeature.place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)`,
`Block.updateFromNeighbourShapes(BlockState, LevelAccessor, BlockPos)`, `CraftBlockData.fromData`. Si algo falta, queda
apagado con el motivo (una línea en la consola).
- **Captura:** un `java.lang.reflect.Proxy` de `WorldGenLevel`: `setBlock`/`removeBlock`/`destroyBlock` guardan en un
  mapa; `getBlockState`/`getFluidState`/`isStateAtPosition`/`isFluidAtPosition` leen primero el mapa; `getBlockEntity`
  devuelve nada (no toca el del mundo); `addFreshEntity` no agrega; `getChunk` y cualquier `void` que cambie el mundo se
  cortan (un `getChunk` hace fallar el *feature*: escribe directo, como los de minerales); los métodos `default` corren
  sobre el proxy (para que lean el mapa); el resto se reenvía al `ServerLevel` (alturas, semilla, biomas).
- `feature` y `brush feature` usan el `Planter` de la 033 con la captura de un *feature* en vez de un árbol.
- `update`: para cada bloque de la selección (foto por tandas), el estado que da `updateFromNeighbourShapes` con el
  proxy; los distintos van por el motor. En el hilo principal, por tandas con el reloj (lección 025 F-01).

### 1.6 Información
- `searchitem`: `Material` no legado, por clave; 10 por página; `--blocks` / `--items`.
- `version`: `plugin.yml`, `Bukkit.getVersion()`, `Runtime.version()`, `Nms.status()`.
- `report`: escribe en un hilo aparte `reports/report-aaaaMMdd-HHmmss.txt` (versión, servidor, mundos, plugins con
  versión, `config.yml` tal cual, límites del que lo pide, obras en marcha). Nunca a la red.
- `trace`: `EditJob` ya cuenta lo cambiado; suma «ya era así» y «saltado por máscara»; al terminar una obra de alguien
  con `trace` prendido, una línea con los números, ms y ticks.
- `tz`: `ZoneId.of` (también `UTC-5`); `RestoreCommands.zone(sender)` usa la de la sesión.

## 2. Contrato de configuración
`item-uses.yml` en el jar (se lee de ahí siempre, lección 022) y uno opcional en `plugins/Worldcraft/` que agrega o
reemplaza ítems:
```yaml
# The item a brush "uses" on each block (/wc brush apply ... item <item>, /wc brush paint ... item <item>).
# Each rule is "<mask> -> <pattern>" (changes the block) or "above <mask> -> <pattern>" (puts something in the air on top).
# Item names can use * (any hoe: "*_hoe"). ^ keeps the block's states (a log keeps its axis).
uses:
  "*_hoe": ["grass_block, dirt, dirt_path -> farmland", "coarse_dirt, rooted_dirt -> dirt"]
  "*_shovel": ["grass_block, dirt, podzol, coarse_dirt, mycelium, rooted_dirt -> dirt_path"]
  "*_axe": ["oak_log -> ^stripped_oak_log", "..."]
  honeycomb: ["copper_block -> waxed_copper_block", "..."]
  bone_meal: ["above grass_block -> 70% air, 24% short_grass, 3% dandelion, 3% poppy"]
  flint_and_steel: ["above netherrack, soul_sand, soul_soil -> fire"]
```
Una regla mal escrita avisa con archivo, ítem y el error del parser, y se salta; `/wc validate` la revisa. Sin claves
nuevas en `config.yml`: `/wc range` usa `tools.range` como tope y el alcance del apilador `tools.max_range`.

## 3. STRIDE
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| **T**ampering: el *feature* escribe en el mundo sin pasar por el motor (sin límites ni deshacer) | Cambios sin control | El proxy no reenvía ninguna escritura; `getChunk` corta el *feature*; `getBlockEntity` no da el del mundo. La compuerta 2 pone *features* en una caja y mira que el mundo no cambió |
| **D**enegación: NMS de otra versión revienta | El plugin no arranca | Todo en `Nms` con `try`; falla cerrado (comando dice «not available»); el arranque sigue |
| **D**: un *feature* enorme o en bucle | Tick largo | Uno por clic/comando; los del pincel por tandas con el reloj (`Planter`); límites de bloques del grupo |
| **D**: `update` o `listchunks` sobre una selección enorme | Tick largo / chat inundado | `update` por tandas con el tope por tick y los límites del grupo; `listchunks` por páginas |
| **I**nformación: `report` vuelca la configuración | Rutas del servidor | Solo administración, al disco del servidor, nunca a la red (WorldEdit lo sube a un sitio: aquí no) |
| **E**levación: `report` | — | `worldcraft.admin` (`default: op`) |
| **T**: `item-uses.yml` de un dueño pone bloques prohibidos | Saltarse `blocked_blocks` | Lo que pone pasa por los límites del grupo de quien hace clic, como todo pincel |

## 4. Pruebas
- Unitarias primero (que fallen sin el código): `CurveTest`, `SnowMathTest`, `BrushMathTest` (morph, dilate,
  heightmap), `ItemUsesTest` (la tabla del jar se lee sin avisos; reglas, comodines, errores), `ToolParserTest` (los
  pinceles y la herramienta nuevos, `paint … item`), `StackerTest`, `ChunkInfoTest`, `SlashTableTest` (los nombres
  nuevos), `PresentacionTest` (las cifras 157/171 y 166/244 contadas de la tabla de `commands.md`).
- Compuerta 2: `selftest` con el puente NMS (un *feature* capturado sin tocar el mundo, `update` de una valla).
- Suite 3a: un escenario por grupo y los más visibles uno por uno (curve, fillr, feature, update, snowsmooth, brush
  snow/morph/biome/heightmap/apply/feature, stacker, range, tracemask, toggleplace/placement, toggleeditwand, chunkinfo,
  listchunks, searchitem, schem formats, version, report, trace, tz).

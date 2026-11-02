# Plan 033 — Biomas, árboles y estructuras del juego (Worldcraft 0.9.0)

Spec: [spec.md](spec.md) · Respuestas del dueño: todo menos los *features*; biomas solo con `--biomes`; estructuras al
portapapeles. Solo API de Paper 1.21.4, sin NMS.

## 0. Lo que da Paper 1.21.4 (revisado con `javap` sobre `paper-api-1.21.4-R0.1-SNAPSHOT.jar`)
| Necesidad | API | Nota |
|---|---|---|
| Leer y poner biomas | `RegionAccessor#getBiome(x,y,z)`, `setBiome(x,y,z,Biome)` | El juego guarda una celda de 4×4×4: `setBiome` cambia la celda entera |
| Que los jugadores lo vean | `World#refreshChunk(cx, cz)` | Reenvía el chunk; una vez por chunk tocado |
| Lista de biomas | `Registry.BIOME` (`Biome` es una interfaz desde 1.21.3) | Incluye los de paquetes de datos |
| Árboles | `generateTree(Location, Random, TreeType, Predicate<? super BlockState>)` | Paper genera en una lista de estados y pregunta uno por uno: devolviendo `false` no pone nada y queda la lista (la «captura») |
| Tipos de árbol | `TreeType` (26: `TREE` … `PALE_OAK_CREAKING`) | |
| Plantillas | `StructureManager#loadStructure(NamespacedKey)`, `Structure#getPalettes()`, `Palette#getBlocks()` | La lista completa no tiene API: se lee del jar del servidor (recursos `data/*/structure/**.nbt` del cargador de clases) más `getStructures()` |
| Botín de un cofre | `org.bukkit.loot.Lootable#getLootTable/setLootTable` | |
| *Features* (`//feature`) | **no hay** (solo el comando `/place feature` del juego) | Fuera de alcance (decisión del dueño) |

## 1. Diseño

### 1.1 Biomas en el motor (Java puro, paquete `biome`)
- `BiomeWorld`: `get(x,y,z)`/`set(x,y,z,nombre)` por celda, `ready(cx,cz)` (sin bloquear, como `EntityWorld`),
  `refresh(cx,cz)`, `close()`. En Paper `paper/PaperBiomes` (con un caché de nombre → `Biome`); en las unitarias, uno en
  memoria.
- `Cells.of(Region)`: las celdas (esquina mínima múltiplo de 4) que toca una región, en orden de chunks; para una caja,
  todas las de su caja; para otra forma, las que tienen algún bloque adentro.
- `BiomeStage implements EntityStage` (la interfaz de etapas de la 031 es genérica: `step(plazo, reloj)`, `done`,
  `skipped`): recorre las celdas mirando el reloj en cada una (lección 025 F-01), espera sin bloquear a que cargue el
  chunk, y por celda: `Apply` (pone el bioma que dice una función; anota antes y después en el `BiomeSink`), `Read`
  (lo lee para el portapapeles o `biomeinfo`) y `Undo` (desde el diario: solo si la celda sigue con el bioma que dejó la
  obra; si no, se salta y se cuenta, como los bloques). Al cambiar de chunk y al final, `refresh` del chunk tocado.
- `EntityStage.chain(a, b)`: dos etapas una tras otra (entidades y después biomas).
- Diario (`Journal`): registro nuevo `BIOME` (x, y, z de la celda, antes y después con el mismo diccionario).
  `JournalSink` implementa `BiomeSink`. `Journal.Data` con los biomas leídos. Un diario de la 0.8.0 no tiene ninguno.
- `Edits.Op` suma `BiomeWork(maker, cells, note)`: la etapa se arma en `run` con el mundo de Paper y el diario, y va
  después de los bloques y las entidades. `undo`/`redo` encadenan la etapa de deshacer los biomas. El mensaje de fin dice
  las celdas cambiadas (`edit-biomes`); una obra solo de biomas (`setbiome`) no habla de bloques.

### 1.2 Comandos de biomas (`command/NatureCommands`)
- `biomelist [página] [filtro]`: `Registry.BIOME` ordenado; 15 por página.
- `biomeinfo`: lectura de la selección por celdas (una obra de lectura con `EmptySource` + `BiomeStage.Read`) y el
  porcentaje por bioma contado en bloques de la selección; `--target`/`-t` el bloque que se mira; `--here`/`-p` los pies.
- `setbiome <bioma> [--here]`: `Op` con `EmptySource(caja)` y `BiomeWork` sobre `Cells.of(selección)`; el tamaño que
  cuenta para los límites y la confirmación es el de la selección. Con `gmask`, la celda cambia si su bloque central pasa.
- Máscara: `Mask.Near.Biome(Set<String>)`; `Mask.Compiled#biomes(BiomeLookup)` (en Paper, lee el mundo en el hilo
  principal: las máscaras se evalúan ahí). `Parser`: `$x` es un preset si existe; si no, un bioma de
  `BlockRegistry#biomes()`; si no, error con «did you mean» entre presets y biomas. Cuenta como máscara «de posición»
  (no se admite en el terreno ni en `mask=` de un pincel) pero no necesita foto (sí en `gmask`). `Edits.compile(mask,
  mundo)` pone el buscador en cada lugar que compila una máscara de posición (set/replace, count/trim, copy --mask,
  replacenear/removenear, gmask).

### 1.3 Biomas en el portapapeles y los `.schem`
- `Clipboard`: paleta de biomas + un índice por bloque (`-1` = sin bioma); `putBiome`, `biomeAt`, `hasBiomes`; `baked()`
  los gira con el resto; `biomeFor(x,y,z de destino, jugador)` con la transformación inversa.
- `copy|cut --biomes`: tras los bloques, `BiomeStage.Read` sobre las celdas de la región llena el bioma de cada bloque
  (de la región) de esa celda. `cut` no cambia los biomas.
- `paste --biomes`: `BiomeWork` sobre las celdas de la caja de destino; cada celda toma el bioma del bloque del
  portapapeles que cae en su centro (o el primero de la celda que tenga). Sin biomas en el portapapeles, lo dice.
- `Schematic` suma `biomePalette` y `biomes` (por bloque, en el orden de Sponge). `SchemFormat`: lee v2
  (`BiomePalette` + `BiomeData` por columna `x + z·ancho`, repetido en toda la altura) y v3 (`Biomes{Palette, Data}` por
  bloque); escribe v3 con `Biomes` si el portapapeles los tiene. `SchemConvert` los pasa al portapapeles y de vuelta.
  Al cargar, los nombres que el servidor no tiene se cuentan (`schem-biomes-unknown`) y quedan sin bioma.

### 1.4 Árboles y plantas (Java puro `nature`, Paper `paper/Trees`)
- `TreeKind`: los nombres de WorldEdit y sus alias → el `TreeType` de Paper (`oak/tree`, `big_oak/big/fancy`,
  `spruce/redwood`, `tall_spruce`, `mega_spruce`, `mega_pine/pine`, `birch`, `tall_birch`, `jungle`, `small_jungle`,
  `jungle_bush`, `cocoa`, `swamp`, `acacia`, `dark_oak`, `cherry`, `azalea`, `mangrove`, `tall_mangrove`, `pale_oak`,
  `pale_oak_creaking`, `red_mushroom`, `brown_mushroom`, `crimson_fungus`, `warped_fungus`, `chorus`, `random` (los de
  la superficie)); el suelo extra de los hongos del Nether (`#nylium`) y del coro (`end_stone`).
- `NatureMath` (sobre la foto `Snapshot`): `spots` (columnas de la región cuyo bloque más alto es suelo de
  `nature.soil` con aire encima, con probabilidad `densidad`% y la semilla), `flora` (sobre pasto o tierra los de
  `nature.flora_grass`; sobre arena los de `nature.flora_sand`; cactus solo con aire a los costados), `pumpkins` (matas:
  una calabaza mirando al azar y 2–4 hojas persistentes alrededor sobre el pasto) y `merge` (los árboles capturados se
  juntan: un tronco gana a una hoja; si no, el primero).
- `paper/Trees.Planter implements Scheduler.Job`: por cada lugar, pide sin bloquear los 3×3 chunks de alrededor (un
  árbol grande se sale del suyo) con tickets del plugin, y cuando están, captura con `generateTree(…, Predicate)`
  devolviendo `false`. Mira el reloj después de cada árbol, dentro de los 8 ms del motor. Al terminar, `merge` y una obra
  con `PointsSource` (vista previa, límites con los estados de verdad, historial, deshacer). `nature.max_trees` corta
  los lugares antes de capturar. `warmUp` captura un roble una vez (código frío, lecciones 025 F-04 y 027 F-01).
- `forest`, `forestgen`, `flora`, `pumpkins`: leen la foto (por tandas) y calculan en el hilo de cálculo; los árboles
  siguen con el `Planter`. `tool tree` y `brush forest` son un `ToolKind` nuevo cada uno (`TREE`, `FOREST`) en
  `ToolActions`.

### 1.5 Estructuras del juego (`paper/Structures`, `command/NatureCommands`)
- Lista: al habilitar, en el hilo de E/S, los nombres de `data/<ns>/structure/**.nbt` del jar del servidor (el recurso
  `data/minecraft/structure/igloo/top.nbt` dice dónde está el jar), más `getStructures()` del `StructureManager`. Filtro y
  páginas de 15.
- `structure load <nombre>`: `loadStructure(clave)` en el hilo principal (Paper lo lee de los paquetes de datos); la
  primera paleta (`--palette N` para otra); cada `BlockState` a su estado (`jigsaw` → aire; lo que la plantilla no
  guarda, `structure_void`, queda «sin leer» y no se pega); un cofre con tabla de botín guarda su clave (dato de bloque
  `L` en `PaperWorld.restoreTile`). Al portapapeles con el origen en la esquina mínima a los pies del jugador.

### 1.6 Lo que se reusa y lo que cambia
| Pieza | Cambio |
|---|---|
| `EditJob` | Nada (las etapas de la 031 llevan los biomas) |
| `Edits` | `Op.biomes`, la etapa en `run` y `undo`, la lectura con etapa extra (copy), `compile(mask, mundo)` |
| `Journal`, `JournalSink` | Registro `BIOME` |
| `Clipboard`, `Schematic`, `SchemFormat`, `SchemConvert` | Biomas |
| `Mask`, `Parser`, `BlockRegistry` | `$bioma`, `biomes()` |
| `ToolKind`, `ToolParser`, `ToolSpec`, `ToolActions` | `tool tree`, `brush forest` |
| `PaperWorld` | Dato de bloque `L` (tabla de botín) |

## 2. Contrato de configuración
```yaml
nature:
  max_trees: 300        # árboles por obra: forest, forestgen, brush forest (1..5000)
  max_size: 32          # forestgen y pumpkins: el tamaño más grande (1..64)
  soil: "grass_block, dirt, podzol, coarse_dirt, rooted_dirt, mycelium, moss_block, mud"   # dónde crece un árbol
  flora_grass: "80% short_grass, 5% dandelion, 5% poppy, 4% cornflower, 3% oxeye_daisy, 3% azure_bluet"
  flora_sand: "90% dead_bush, 10% cactus"
```
`/wc validate` avisa claves desconocidas, rangos, máscaras y patrones que no se entienden (con «did you mean») y un
preset de máscara que se llama como un bioma. Un `config.yml` de la 0.8.0 sin la sección usa los valores de fábrica.
Sin permisos nuevos: todo con `worldcraft.use` (los pinceles y herramientas, como en la 032).

## 3. STRIDE
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| **D**enegación: `setbiome` sobre una selección enorme | Muchos chunks reenviados en un tick | Límite de bloques del grupo y confirmación; por tandas con el reloj en cada celda; `refreshChunk` una vez por chunk dentro del presupuesto |
| **D**enegación: `forest` con densidad 100 en 500×500 | Miles de `generateTree` | `nature.max_trees` corta antes de capturar; captura por tandas mirando el reloj en cada árbol; los chunks se piden sin bloquear |
| **D**enegación: un árbol cerca de un chunk sin cargar | El generador lee el vecino y lo carga de golpe | Se esperan los 3×3 chunks de alrededor con tickets antes de capturar |
| **E**levación: un árbol o una estructura con bloques prohibidos | Un jugador de «default» pone lo que su grupo no puede | `blocked_blocks` con los estados **capturados** (lo que de verdad va a poner), en `Edits.request` |
| **T**ampering: un `.schem` con una paleta de biomas dañada | Índices fuera de rango, memoria | Se revisa como la de bloques (027): índices, tamaño contra el tope; un bioma desconocido se salta |
| **T**ampering: `structure load ../../x` | Leer fuera de los paquetes de datos | Solo `NamespacedKey` válida (minúsculas, `a-z0-9/._-`); la carga la hace Paper desde sus paquetes de datos |
| **R**epudio | ¿Quién cambió el bioma? | Cada obra va al historial con dueño, tipo y detalle |
| **I**nformación: `biomeinfo`, `structure list` | — | Lo que el jugador ya ve o lo que trae el servidor |
| **S**uplantación | — | Permisos de Bukkit en cada comando y cada clic |

## 4. Pruebas
- Unitarias: `BiomeTest` (celdas de una caja y de una esfera; `setbiome` por el motor en memoria con diario, deshacer
  seguro que salta la celda cambiada y rehacer; un diario viejo sin biomas), `BiomeClipboardTest` (copiar con biomas,
  girar 90° y el bioma va con su bloque; pegar elige la celda por su centro), `SchemFormatTest` (v3 con biomas ida y
  vuelta, v2 por columna, paleta dañada), `ParserTest`/`MaskNearTest` (`$desert`, el preset gana, «did you mean»,
  `!$plains and sand`), `NatureTest` (lugares solo en suelo con aire encima y con la densidad; flora por suelo; matas;
  `merge`; `TreeKind` con alias y sugerencia), `StructureTest` (plantilla → portapapeles: `jigsaw` → aire, sin
  `structure_void`, botín), `ToolSpecTest` (`tool tree`, `brush forest`), `ConfigTest` (sección `nature`).
- Compuerta 2: `selftest` con un árbol capturado de verdad (el mundo no cambia), el registro de biomas y una plantilla
  del juego (`igloo/top`); `wc validate`.
- 3a: escenarios con bots (en tasks).

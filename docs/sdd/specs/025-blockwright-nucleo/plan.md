# Plan 025 — Diseño técnico de Blockwright

## 1. Arquitectura
Paquete `dev.leo.blockwright`. El núcleo (región, patrones, motor, historial, configuración) es Java puro y se prueba
con un mundo en memoria; solo `paper/`, `command/` y la clase principal tocan la API de Paper. **Sin NMS** (constitución
VI.2): la investigación (§1.9) muestra que los fallos de luz, cofres y bloques fantasma de FAWE vienen de escribir el
mundo por dentro del servidor.

| Paquete | Clases | Qué hace |
|---|---|---|
| `region` | `Box`, `Direction`, `Shape`, `ChunkOrder` | La caja (expandir, achicar, mover con palabras), las formas (llena, paredes, caras) y el recorrido chunk por chunk |
| `block` | `StateTable`, `BlockRegistry`, `StateOps` | Estados de bloque como números; lo que el analizador necesita del servidor; giros y volteo de arriba abajo |
| `pattern` | `Parser`, `Pattern`, `Mask`, `ParseException` | Patrones y máscaras legibles, con errores y «did you mean» |
| `edit` | `EditWorld`, `EditSource` (`RegionSource`, `PasteSource`, `UndoSource`), `EditJob`, `Scheduler`, `Clipboard`, `Transform` | El motor: qué cambia en cada posición, el trabajo por tandas y el reparto del tiempo |
| `history` | `Journal`, `JournalSink`, `HistoryStore` | Diario por obra e índice por jugador en disco |
| `config` | `Settings`, `Limits`, `LoadedConfig`, `Issues`, `Messages` | Lectura y validación de `config.yml`, `presets.yml` y `messages.yml` |
| `paper` | `PaperBlocks`, `PaperWorld`, `PreviewSink`, `Edits`, `Session`, `Wand`, `SelfTest` | El registro real, el mundo con chunks asíncronos, la vista previa, la varita y el contorno, el servicio que une todo |
| `command` | `BlockwrightCommand`, `Args` | `/bwr`, ayuda con ejemplos y autocompletado |

### 1.1 Recorrido por chunks
`ChunkOrder` recorre la forma columna de chunks por columna (x, z) y dentro de cada una por (x, z, y). Así cada chunk
se carga, se usa y se suelta una sola vez. Para `walls` y `faces` las columnas interiores no se recorren (o solo en
piso y techo): una caja de 1000×256×1000 en `walls` visita 1 M de posiciones, no 256 M. `Box.count(shape)` da ese número
sin recorrer (límites y estimaciones).

### 1.2 Estados como números
`StateTable` da un número a cada estado canónico («minecraft:oak_stairs[facing=north,half=bottom,…]») la primera vez
que aparece. `PaperBlocks` guarda el `BlockData` de cada número y un `HashMap<BlockData, Integer>`: leer un bloque es
`world.getBlockData` + una búsqueda; ponerlo, `setBlockData(data, false)`. La máscara compilada decide una vez por
estado y lo recuerda; el patrón compilado es un arreglo de números y pesos acumulados.

### 1.3 Patrones y máscaras (gramática)
```
patrón  := $preset | entrada ("," entrada)*
entrada := [N "%"] bloque ["[" prop=valor ("," prop=valor)* "]"]       (todas con % o ninguna)
máscara := $preset | "*" | término ("," término)*
término := ["!" | "not "] ( "#"etiqueta | id-con-* | bloque ["[" props parciales "]"] | air )
```
Un bloque pasa la máscara si coincide con algún término positivo (o no hay) y con ningún negado. La mezcla elige con
SplitMix64 de (x, y, z, semilla): determinista (H2 CA5). Los errores comparan con la distancia de Damerau (F-02).

### 1.4 El motor
- `EditSource` da las posiciones en orden de chunks y, para cada una, el estado nuevo según el actual (`KEEP` = no
  tocar). La máscara se evalúa contra el mundo real **al poner** cada bloque (no contra una foto vieja).
- `EditJob` avanza dentro del plazo: si el chunk no está listo, devuelve y espera (no bloquea); por cada bloque que
  cambia, primero lo anota en el diario (`Sink`) y luego lo pone. Modos: `APPLY` (diario + mundo), `PREVIEW` (bloques
  falsos para un jugador, con tope) y `READ` (copiar). Mira el reloj en **cada** bloque (F-01).
- `Scheduler` (sin Bukkit): presupuesto de `max_ms_per_tick` y `max_blocks_per_tick` por tick, repartido por turnos entre
  los dueños con obras; cada dueño hace las suyas en orden; una obra que se cruza con otra más vieja de otro dueño en
  el mismo mundo espera (H5 CA6); una excepción cancela solo esa obra. La tarea de Paper existe solo mientras hay obras
  o confirmaciones pendientes (III.1).
- `UndoSource` lee el diario en el hilo de E/S y, al deshacer, pone el estado de antes solo si el actual es el que dejó
  la obra (al rehacer, al revés): lo demás se cuenta como saltado (H6 CA3).
- `PasteSource` recorre la caja de destino y busca cada posición en el portapapeles con la transformación inversa
  (`Transform`: matriz entera ortogonal; la inversa es la traspuesta). Los estados se giran con `BlockData.rotate`/
  `mirror` y el volteo de arriba abajo, sobre el texto (`half`, `type`, `face`, `facing`…), memorizados por estado.

### 1.5 Una obra de principio a fin (`Edits`)
1. El comando arma el `Op` (tipo, detalle, mundo, caja, volumen, fábrica de fuentes, estados que pondrá).
2. Límites (H7): mundo, `max_blocks`, bloques prohibidos, `max_jobs`. Rechazo antes de tocar nada.
3. `--preview` → trabajo `PREVIEW` con `PreviewSink` (`sendMultiBlockChange`) y espera; más de `confirm.above_blocks` →
   espera. `confirm` devuelve los bloques reales al jugador y corre la obra con una fuente nueva de la misma semilla.
4. `HistoryStore.begin` (descarta lo rehacible), `JournalSink` y `EditJob` al planificador; al terminar, `finish` con lo
   cambiado, mensaje y estimación aprendida (bloques por ms).

### 1.6 Datos de bloque
`PaperWorld.tile` guarda, solo para estados con `TileState` (memorizado por estado), el contenido de un `Container`
(`ItemStack.serializeItemsAsBytes`) y las 8 líneas de un cartel con brillo y cera (JSON de Adventure). `restoreTile` los
devuelve con `update(true, false)`. Tope de 2 MB por bloque en el diario.

## 2. Contrato de configuración
`plugins/Blockwright/config.yml` (comentado en inglés, ver `src/main/resources/config.yml`):
```yaml
wand: { item: minecraft:golden_axe }
selection: { show: true, show_every_ticks: 10, max_particles: 400 }
performance: { max_ms_per_tick: 8, max_blocks_per_tick: 200000 }
confirm: { above_blocks: 20000, expire_seconds: 120 }
preview: { max_blocks: 50000 }
history: { max_per_player: 30, keep_days: 7 }
limits:
  default: { max_blocks: 50000, max_jobs: 2, worlds: ["*"], blocked_blocks: [bedrock, barrier, command_block, …] }
  builder: { max_blocks: 1000000, max_jobs: 3 }
  admin:   { max_blocks: 10000000, max_jobs: 5, blocked_blocks: [] }
```
Rangos: `max_ms_per_tick` 1–40, `max_blocks_per_tick` 100–5 000 000, `show_every_ticks` 2–200, `max_particles` 12–5000,
`above_blocks` ≥ 0, `expire_seconds` 10–3600, `preview.max_blocks` 0–1 000 000, `max_per_player` 1–1000, `keep_days`
0–3650 (0 = sin vencer). Grupo: nombre `[a-z0-9_-]+`, `max_blocks` 1–2 000 000 000, `max_jobs` 1–100; un valor que falta
hereda de `default`.

`presets.yml`:
```yaml
patterns: { castle_wall: "60% stone_bricks, 25% cracked_stone_bricks, 15% mossy_stone_bricks" }
masks:    { natural: "grass_block, dirt, stone, gravel, sand" }
```
Un preset inválido o que nombra a otro preset se informa y se omite.

`messages.yml`: textos con MiniMessage; `help.<subcomando>.usage|text|example` para la ayuda. Los valores se insertan
escapados (II.2).

Comando `/blockwright` (alias `/bwr`):
| Subcomando | Permiso |
|---|---|
| `help`, `wand`, `pos1`, `pos2`, `sel`, `expand`, `contract`, `shift`, `set`, `replace`, `walls`, `faces`, `copy`, `cut`, `paste`, `rotate`, `flip`, `undo`, `redo`, `history`, `confirm`, `cancel`, `jobs`, `limits` | `blockwright.use` |
| `history <otro>`, `undo/redo --player <otro>`, `jobs` de todos y `jobs cancel <id>` | `blockwright.moderate` |
| `reload`, `validate`, `selftest` | `blockwright.admin` |
| Grupo de límites | `blockwright.limit.<grupo>` |

Datos: `history/<uuid>/index.yml` (nombre, siguiente número y por obra: tipo, detalle, mundo, caja, hora, bloques,
estado `RUNNING|DONE|CANCELLED|UNDONE`) y `history/<uuid>/<n>.bwj` (diario binario de solo agregar: cabecera `BWJ1`,
entradas de diccionario, registros `(x, y, z, antes, después)` y datos de bloque de antes y después).

## 3. Hilos y rendimiento
- **Hilo principal:** comandos (análisis del patrón, una vez), el tick del planificador, leer y poner bloques, la vista
  previa, el contorno de partículas (cada `show_every_ticks`, solo a quien tiene la varita en la mano, con tope).
- **Hilo de E/S** (uno, en orden): diarios, índices, lectura de diarios para deshacer y borrado de lo vencido. Un
  deshacer lee después de lo que se escribió antes (misma cola). `onDisable` espera a que termine (20 s).
- **Chunks:** `getChunkAtAsync` + ticket del plugin mientras la obra los usa; se piden por adelantado los 6 siguientes
  del recorrido.
- **Topes (III.4):** tiempo y bloques por tick, `max_blocks` por obra, `max_jobs`, tope de la vista previa y de
  partículas, 2 MB por bloque en el diario, `undo` de hasta 50 obras a la vez.
- **Memoria:** el portapapeles es un `int` por bloque (1 M de bloques = 4 MB); deshacer lee el diario en arreglos
  (~20 bytes por bloque). La obra no guarda la lista de bloques: la fuente los genera.

## 4. Persistencia
- El diario se escribe **antes** de poner cada tanda (la tanda se encola al hilo de E/S en el mismo tick). Una cola
  cortada se ignora al leer (H6 CA2).
- El índice se escribe de forma atómica (temporal + `ATOMIC_MOVE`, II.6) en el hilo de E/S.
- Al arrancar: lo que quedó `RUNNING` pasa a `CANCELLED` (H6 CA7) y se borra lo que pasa de `max_per_player` o de
  `keep_days`. Si un índice no se puede leer, el plugin no arranca (no pisa `history/`).
- `reload` no toca `history/` (I.4).

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación | Un jugador usa un hacha de oro común como varita, o deshace a otro diciendo su nombre | Solo el ítem con la marca del plugin (PDC) es varita; `--player` exige `blockwright.moderate` |
| Manipulación | Un jugador pone bloques prohibidos (bloques de comandos, roca madre, portales) | `blocked_blocks` por grupo, revisado en patrones y en lo que se pega |
| Manipulación | Deshacer borra lo que otro construyó después | Deshacer seguro: solo se devuelve lo que sigue como lo dejó la obra |
| Manipulación | Un nombre de jugador o un texto en un mensaje se lee como MiniMessage | Todo valor se escapa (II.2); las rutas del historial son UUID, nunca nombres |
| Repudio | ¿Quién cambió esto? | El historial guarda dueño, hora, mundo, caja y bloques de cada obra; `history <jugador>` para moderadores |
| Divulgación | La vista previa muestra algo a otros | Los bloques falsos se mandan solo a quien la pidió |
| Denegación de servicio | Una obra enorme congela o tumba el servidor; muchas a la vez; un contorno de millones de partículas | Presupuesto por tick, reloj en cada bloque, `max_blocks`, `max_jobs`, chunks asíncronos, tope de partículas y de vista previa; la obra no guarda la lista de bloques |
| Denegación de servicio | El disco se llena de historial | `max_per_player` y `keep_days` |
| Elevación de privilegios | Un jugador sin permiso edita o ve los subcomandos | Todos los permisos `default: op`; autocompletado y ayuda filtrados por permiso |

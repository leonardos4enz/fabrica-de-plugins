# Plan 029 — Selección, información y máscaras en Worldcraft (0.5.0)

Spec: [spec.md](spec.md) · Respuestas del dueño: 029 = selección, información y máscaras, con la spec chica de la 027 y la
deuda de la 028. Todo pasa por el motor de la 025 (`Edits.request`, `Edits.read`, `EditSource`), sin NMS.

## 1. Diseño

### 1.1 Máscaras con vecinos (H2)
Hoy una máscara es una lista de términos que solo miran el **estado** del bloque (`Mask.Compiled.test(id)`, memorizado
por número de estado). Lo nuevo:

- **Gramática** (en inglés, legible, sin símbolos de WorldEdit):
  ```
  máscara  := término (',' término)*                      ',' es «o»
  término  := ['!' | 'not '] parte (' and ' parte)*         'and' es «y»; '!' niega todo el término
  parte    := bloque | #tag | glob | '*' | '^[estado]'      solo el estado (como hoy; '^[…]' sin tipo, H2 CA3)
            | 'solid'                                        bloques sólidos (se resuelve al leer: una lista de bloques)
            | 'exposed' | 'surface'                          toca aire por alguna cara / no es aire y tiene aire encima
            | 'above' bloque… | 'below' bloque…               el de abajo (o de arriba) cumple esa parte de estado
            | N '% chance'                                   al azar, con la semilla de la obra (vista previa = obra)
  'stone above grass_block' = 'stone and above grass_block'
  ```
  `above`/`below` llevan dentro solo partes de estado (un bloque, tag, glob, `solid`, `^[…]`): sin anidar.
- **Dos niveles de máscara.** `Parser.mask()` sigue dando máscaras de estado (terreno, estilos, pincel, presets): si la
  máscara mira vecinos o la posición, el error dice dónde sí se puede usar. `Parser.maskNear()` acepta todo; la usan
  `replace`, `replacenear`, `removenear`, `count`, `trim`, `copy`/`cut --mask` y `gmask`.
- **Evaluación.** `Mask.Compiled.test(x, y, z, id, Neighbors n, seed)`: primero la parte de estado (memorizada); solo si
  pasa, lo que mira vecinos o la posición. `Neighbors` lee la **foto** de la obra (`Snapshot` de la caja + 1 bloque,
  leída antes con `Edits.read`): el resultado no depende del orden de escritura (H2 CA2). Si la máscara no mira vecinos,
  no se lee ninguna foto (cero costo extra).
- **solid**: `BlockRegistry.solid(id)` (en Paper, `Material.isSolid`; en las pruebas, una lista). Se resuelve al leer la
  máscara, así sigue siendo de estado.

### 1.2 Patrones que conservan (H3)
`Pattern` gana una lista paralela de **cambios** (`Change`): una entrada fija (como hoy) o `^tipo` / `^[estado]`.
- `^stone_stairs`: el tipo nuevo con las propiedades del bloque actual que el tipo acepta (las que no, de fábrica).
- `^[waterlogged=false]`: el mismo tipo con esa propiedad; si el bloque no la tiene, no se toca.
- `Pattern.Compiled.apply(x, y, z, current, seed)`: los cambios se memorizan por estado actual. Las fuentes que ya
  conocen el estado actual (`RegionSource`, `SolidSource`, los puntos de `BuildCommands`, `overlay`) lo pasan; las demás
  usan la forma vieja y un cambio deja el bloque como está.
- Bloques prohibidos: `placedStates()` da los estados fijos y el de fábrica de cada `^tipo` (el material es lo que se
  revisa); `^[estado]` no cambia el material.

### 1.3 Máscara global (H4)
`Session.gmask` (solo en memoria: se pierde al salir, como el portapapeles). `Edits.request` envuelve la fuente de la obra
y la de la vista previa en `MaskedSource` (deja `KEEP` donde no pasa). Solo máscaras sin vecinos (sí `% chance`): una
máscara con vecinos necesitaría una foto en cada obra. El mensaje de inicio o de confirmación lo menciona. `undo` no la
usa (no pasa por `request`).

### 1.4 Selección e información (H1)
Nueva clase `SelectionCommands` (como `BuildCommands` en la 028), con la geometría pura en `Box` y el conteo en
`edit.Census` (sin Bukkit, probado en unitarias):
- `hpos1/2`: `rayTraceBlocks` hasta `terrain.reach` (como `jumpto`).
- `chunk`: la columna entera del chunk (alto del mundo); `--chunk-coords x z`; `--expand` une con la selección.
- `outset`/`inset n` con `--horizontal`/`--vertical` → `Box.outset/inset`; achicar de más → `sel-empty`.
- `count`, `distr`, `trim`: una lectura por tandas (`Edits.read`/`readWith`) con un `Capture` que cuenta (sin guardar la
  foto); con una máscara que mira vecinos, primero la foto de la caja + 1. `distr` agrupa por material (o por estado con
  `--states`), los 10 primeros con %; `--clipboard` cuenta el portapapeles sin leer el mundo.
- `clearclipboard`; `clearhistory` con `/wc confirm` (un `Pending` con una acción en lugar de una obra) →
  `HistoryStore.clear(owner)` borra lo que no está en marcha.
- `paste --origin` (el portapapeles recuerda dónde se copió; uno cargado de un esquema no lo sabe y se dice),
  `--select`, `--only-select`; `copy --mask …`/`cut --mask …` (el resto de lo posicional es la máscara, que puede tener
  espacios): lo que no cumple queda como aire en el portapapeles y `cut` solo quita lo que cumple.

### 1.5 Esquemas (H5)
- `Schematics.importAll`: recorre las carpetas de WorldEdit y FAWE con el mismo `walk` de la 027 (sin enlaces, nombres
  seguros, tope de archivos) y copia cada `.schem` que falte en la propia con el mismo tope de tamaño que `load`; un
  nombre que ya existe se cuenta y no se pisa salvo `--overwrite` (que, como `save --overwrite`, pide
  `worldcraft.schematic.delete`). En el hilo de disco.
- `worldcraft.schematic.save` (op) para `save` e `import`; `worldcraft.schematic` queda para `list` y `load`.

### 1.6 Deuda de la 028 (H6)
- `Box.center()` (la caja de 1, 2, 4 u 8 bloques) y su prueba.
- `edit.Liquids`: la búsqueda y el cambio de `drain`/`fixwater`/`fixlava` fuera de `BuildCommands`, sobre una `Snapshot`
  y una `StateTable`; pruebas de `fixwater` y `fixlava` (el agua que corre queda quieta, no cruza a otro lago, no sube
  del nivel más alto encontrado).

## 2. Contrato de configuración
Sin claves nuevas en `config.yml`. Lo que se lee o cuenta usa `limits.<grupo>.max_blocks` (como `copy`); `hpos` usa
`terrain.reach`; `import` usa los topes de `schematics.*` de la 027. Mensajes nuevos en `messages.yml` (valores de fábrica
para quien actualiza, como en la 028). `plugin.yml`: `worldcraft.schematic.save` (op).

## 3. STRIDE
| Amenaza | Dónde | Mitigación |
|---|---|---|
| **T**ampering: borrar el historial de otro | `clearhistory` | Solo el propio, con `/wc confirm` y el plazo de `confirm.seconds`; lo que está en marcha no se borra |
| **T**ampering / **E**levación: escribir fuera de la carpeta | `schem import` | Nombres por `SchemFiles.clean`, sin enlaces simbólicos, escritura atómica en la propia; `--overwrite` pide `schematic.delete` |
| **D**enegación: contar o leer una selección enorme | `count`, `distr`, `trim`, `copy --mask` | Tope `max_blocks` del grupo antes de leer; lectura por tandas dentro de los 8 ms; la foto de vecinos solo si la máscara los mira |
| **D**enegación: copiar miles de esquemas | `schem import` | Tope de archivos de la 027 (`MAX_LIST`) y de tamaño por archivo; en el hilo de disco |
| **D**enegación: máscaras anidadas | `Parser` | `above`/`below` no se anidan |
| **R**epudio | `gmask` | La obra la menciona al empezar y en la confirmación |
| **E**levación: guardar sin permiso | `schem save` | Permiso aparte `worldcraft.schematic.save` (decisión del dueño) |

## 4. Pruebas
- Unitarias: `MaskNearTest` (gramática, errores con «did you mean», `above`/`below`/`exposed`/`surface`/`solid`/`chance`
  sobre una foto, que la foto y no el mundo a medio escribir decide), las `H3_*` del mismo `MaskNearTest` (`^tipo` conserva `facing`,
  `^[…]`), `SelectionTest` (`outset`, `inset`, `chunk`, `center`, `Census` de `count`/`distr`/`trim`), `LiquidsTest`
  (`fixwater`, `fixlava`), `HistoryStore.clear`, `SchemFiles` (importar sin pisar), mensajes y ayuda de cada comando.
- Compuerta 2: desde la consola en `plugins/worldcraft/run/`.
- Compuerta 3a: escenarios nuevos con el bot (`hpos1`, `count`/`distr`, `replace … above …`, `^tipo`, `gmask`, `schem import`
  con WorldEdit instalado y Beto sin `schematic.save`).

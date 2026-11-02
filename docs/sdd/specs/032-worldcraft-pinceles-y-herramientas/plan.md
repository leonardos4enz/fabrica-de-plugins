# Plan 032 — Pinceles de construcción y herramientas de un clic (Worldcraft 0.8.0)

Spec: [spec.md](spec.md) · Respuestas del dueño: todo menos `tool tree` (033); el ítem de la mano, solo ese;
`worldcraft.use` + `worldcraft.superpickaxe`; el superpico no suelta ítems. Solo API de Paper 1.21.4, sin NMS.

## 1. Diseño

### 1.1 Java puro (paquete `tool`)
- `ToolKind`: los pinceles (`sphere`, `cyl`/`cylinder`, `set`, `clipboard`, `splatter`, `scatter`, `rock`/`blob`,
  `gravity`, `extinguish`/`ex`, `butcher`) y las herramientas (`repl`, `floodfill`/`flood`, `info`, `cycler`, `deltree`,
  `farwand`, `lrbuild`, `navwand`), con su id, alias y si es pincel.
- `ToolSpec(kind, opts)`: lo que viaja en el ítem como texto (`kind=sphere;pattern=stone;radius=4;hollow=1`), como los
  ajustes del pincel de terreno (026): dos ítems de la barra pueden tener pinceles distintos y un ítem viejo sigue
  sirviendo (lo que no se entiende queda con el valor de fábrica). `describe()` para el nombre y los mensajes.
- `ToolParser.parse(tipo, palabras, opciones, clave=valor, topes)`: el orden de WorldEdit (`sphere stone 4`,
  `cyl stone 3 5`, `set cube 3 stone`, `lrbuild air stone`); los números del final son radio, alto, `decay`…; lo demás es
  el patrón. Errores con «did you mean». `ToolParser.update(spec, clave=valor)` cambia solo `radius=` o `mask=`.
- `BrushMath` (sobre la foto de la 028, `Snapshot`, o sin leer el mundo): `splatter` (superficie de la esfera con
  probabilidad `(1 − d/(r+½))^(decay/2)`), `scatter` (cima de cada columna del círculo, al azar con la semilla, separadas al
  menos `distancia`), `rock` (esfera deformada: ejes al azar entre `sphericity`% y 100 % del radio, borde con ruido de
  valor 3D de `frequency` y `amplitude`), `gravity` (cada columna se compacta hacia abajo dentro del tramo; lo fijo, los
  bloques con datos, hace de piso), `deltree` (inundación por 6 vecinos de troncos y hojas; si toca algo que no es árbol
  ni «cobertura» —aire, plantas, agua— no es flotante; si llega al borde de la foto, es muy grande), `area` y `recursive`
  del superpico (mismo material, sin `never_break`).
- `Cycler`: las propiedades del estado y el valor siguiente o anterior. Los valores posibles salen del valor actual
  (verdadero/falso, números 0..30, o un diccionario de los valores de Minecraft) filtrados por una prueba inyectada
  (`Bukkit.createBlockData` en Paper; lección 021: el registro no corre en unitarias).

### 1.2 Paper
- `paper/Tools` (oyente): marca del PDC `worldcraft:tool` (el `ToolSpec`) y `worldcraft:tool_added` (qué le agregó al
  ítem: nombre, descripción) para que `none` lo deje como era. `bind(jugador, spec)`: el ítem de la mano (H1 CA1; la
  varita no; uno de terreno deja de serlo), o uno nuevo si la mano está vacía. Clic derecho/izquierdo (`AIR` y `BLOCK`:
  la mira la calcula el plugin con `rayTraceBlocks` hasta `tools.range`, así un clic al aire de lejos sirve; lección
  026: mineflayer manda `RIGHT_CLICK_AIR`/`LEFT_CLICK_AIR`), cancela romper y poner. El círculo de partículas para los
  pinceles con radio. El superpico: estado por jugador (modo, radio, prendido), clic izquierdo en un bloque con cualquier
  pico (`*_PICKAXE`), se olvida al salir.
- `paper/ToolActions`: qué hace cada clic. Todo termina en `Edits.request` (sin vista previa): formas con `SolidSource`
  (la 028), `clipboard` con `PasteSource` (025), lo que mira el mundo con una lectura por tandas (`Edits.read` →
  `Snapshot`) y el cálculo en el hilo de cálculo (`PointsSource`, 028), `butcher` con la obra de entidades de la 031
  (`EmptySource` + `EntityStage.Scan` con el filtro de `Butcher` en la esfera). `mask=` envuelve la fuente en
  `MaskedSource` (029; sin máscaras que miran alrededor). Mientras el jugador lee o tiene una obra, los clics se ignoran.
- `command/ToolCommands`: `/wc brush <tipo de construcción> …`, `/wc tool …`, `/wc superpickaxe|sp …`, su autocompletado
  y ayuda. `/wc brush <modo de terreno>` sigue siendo el de la 026.
- `info` no es una obra: posición, estado, bioma y luz del bloque. `farwand` usa `setCorner` (la varita, 030). `navwand`
  usa `jumpto` y `thru` (028).

### 1.3 Lo que se reusa y lo que cambia
| Pieza | Cambio |
|---|---|
| `BrushTool` (026) | Un pincel de construcción en la mano no es de terreno; `/wc brush raise` sobre un ítem marcado lo pasa a terreno |
| `BuildCommands` | `butcherAt` público (la obra de `butcher` en un punto) |
| `WorldcraftCommand` | `navigate(jugador, sub)` público; `brush`, `tool`, `superpickaxe`/`sp` |
| `LoadedConfig` | `tools()` (sección `tools` de config.yml) |

## 2. Contrato de configuración
```yaml
tools:
  item: minecraft:blaze_rod      # lo que dan /wc brush y /wc tool con la mano vacía
  navwand_item: minecraft:compass
  range: 200                     # hasta dónde mira un pincel o herramienta (1..512)
  show: true                     # el círculo del pincel, solo para quien lo tiene
  brush_max_radius: 16           # 0..64
  max_range: 32                  # floodfill (1..64)
  deltree_range: 24              # cuánto se busca alrededor de un árbol flotante (4..64)
  superpickaxe_max_radius: 5     # area y recursive (1..16)
  tree_blocks: "#logs, #leaves, vine, cocoa, bee_nest, mangrove_roots"     # lo que es árbol para deltree
  never_break: "bedrock, barrier, end_portal_frame, end_portal, end_gateway, nether_portal, command_block, chain_command_block, repeating_command_block, structure_block, jigsaw"
```
`/wc validate` avisa claves desconocidas, rangos y máscaras que no se entienden (con «did you mean»). Permiso nuevo
`worldcraft.superpickaxe` (de fábrica OP). Un `config.yml` de la 0.7.0 sin la sección usa los valores de fábrica.

## 3. STRIDE
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| **E**levación: un ítem marcado pasa a otro jugador (tirado, cofre) | Usa un pincel con más de lo que su grupo permite | El ítem guarda solo el pincel; los límites, la máscara global y los permisos (`use`, `butcher`, `navigate`, `superpickaxe`) se revisan **en cada clic** con los del que lo usa |
| **E**levación: un patrón de bloques prohibidos guardado en un ítem (hecho por un admin) | Bloques de comandos puestos por cualquiera | `blocked_blocks` del grupo del que hace clic, en `Edits.request` |
| **T**ampering: un ítem con un texto de pincel armado a mano (otro plugin, NBT editado) | Radios enormes o patrones raros | `ToolSpec.decode` acota todo con los topes del momento (`brush_max_radius`, `max_range`) y el patrón se vuelve a leer con el parser al usarlo; si no se entiende, se dice y no se toca nada |
| **D**enegación: clic derecho rápido con un pincel grande | Cola de obras, tick trabado | Un clic mientras hay una lectura u obra en marcha se ignora; obras por tandas con 8 ms por tick; `max_jobs` |
| **D**enegación: `floodfill`/`deltree` sobre un bosque entero | Lectura enorme | La foto es de `max_range`/`deltree_range` (tope en config); si el árbol llega al borde, no se quita nada |
| **T**ampering: el superpico rompe la roca madre o un portal | Agujero al vacío, portales rotos | `never_break` (de fábrica, la roca madre, la barrera, los portales y los bloques de comandos); permiso propio de OP |
| **R**epudio | ¿Quién pintó esto? | Cada clic va al historial (`/wc history`) con dueño, tipo y detalle, como cualquier obra |
| **I**nformación: `info` | — | Solo el bloque que el jugador ya ve |
| **S**uplantación | — | Permisos de Bukkit en cada clic |

## 4. Pruebas
- Unitarias: `ToolSpecTest` (codificar y leer cada tipo, ítem viejo, acotar lo armado a mano, errores con sugerencia,
  `update`), `BrushMathTest` (superficie de `splatter` y que raleé hacia el borde, `scatter` separadas, `rock` redonda con
  sphericity 100 y dentro de su caja, `gravity` con lo fijo, `deltree` flotante/plantado/muy grande, superpico área y
  recursivo con `never_break`), `CyclerTest` (con valores inyectados; booleanos, números, propiedad siguiente), y en el
  mundo en memoria: `splatter` y `gravity` por el motor con deshacer. `ConfigTest` (sección `tools`), `CommandTest`
  (claves de mensajes).
- Compuerta 2: `selftest` con `Cycler` sobre el registro real (`oak_stairs`: 4 caras, 2 mitades, 5 formas) y la consola
  (`wc validate`).
- 3a: escenarios con bots (abajo, en tasks).

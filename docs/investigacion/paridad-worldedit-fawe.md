# Investigación: Worldcraft frente a WorldEdit y FAWE, comando por comando

**Fecha:** 2026-11-01 · **Para:** el dueño de la fábrica · **Spec:** [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md) ·
**Estado:** insumo de SPECIFY (nada implementado)

El dueño preguntó si Worldcraft ya tiene todos los comandos de WorldEdit y FAWE, y en qué sigue siendo mejor. Hasta
ahora nadie lo había comparado fila por fila: la [investigación de la 025](editores-de-mundo.md) miraba qué le duele al
usuario, no el inventario. Esta tabla sigue el modelo de la spec 014 de OmniEvents (paridad con ConditionalEvents).

## Fuente y método
- **Inventario sacado del código fuente**, con permiso del dueño (2026-11-01): **WorldEdit 7.3.19** (etiqueta `7.3.19`
  de `github.com/EngineHub/WorldEdit`, la misma versión que corre en la suite) y **FastAsyncWorldEdit 2.16.2** (etiqueta
  `2.16.2` de `github.com/IntellectualSites/FastAsyncWorldEdit`, la última, publicada en Modrinth el 2026-11-01 para 1.21.1–26.3).
  Un script propio leyó solo las anotaciones `@Command` (nombre, alias, descripción) y la lista de clases de máscaras,
  patrones, transformaciones y selectores. El resultado: **170 comandos en WorldEdit y 271 en FAWE**, contando
  subcomandos. Los clones quedaron en el scratchpad de la sesión, fuera del repositorio.
- **Licencia:** los dos son GPLv3. El inventario (qué comandos existen y qué hacen) es un dato de entrada. Worldcraft
  se sigue escribiendo desde cero, sin copiar código ni textos de ayuda, como en la 025 y la 027.
- **Comprobado contra la documentación pública** ([comandos de WorldEdit](https://worldedit.enginehub.org/en/latest/commands/)):
  coincide con el código, categoría por categoría.
- **No hizo falta bajar el jar de FAWE** a la suite: el código fuente alcanza para el inventario. Si una spec futura
  prueba la compatibilidad con FAWE en el juego, el jar se agrega a `tests-e2e/lib/plugins-extra.mjs` con su sha256
  (regla de la 012). El dueño ya dio su aprobación.
- **Worldcraft 0.3.0:** `WorldcraftCommand` (`/wc`, 33 subcomandos), su README y las specs 025–027.

**Leyenda.** «Worldcraft» dice qué tiene hoy. **=** equivalente · **mejor** (lo tiene y hace algo que WE/FAWE no) ·
**parcial** (tiene una parte o la forma es peor) · **falta** · **no** (no se hará, con el motivo). Costo: **B** bajo
(horas), **M** medio (una spec normal), **A** alto (varias specs o investigación). «Núcleo» marca lo que la spec 028
propone hacer (ver §4).

## 1. Resumen
| Área | WE | FAWE agrega | Worldcraft lo tiene | Lo que le falta a Worldcraft |
|---|---|---|---|---|
| Selección | 19 | — (+ selectores difuso y poliedro) | 7, mejor contorno y varita | `hpos`, `chunk`, `outset/inset`, `trim`, `count`, `distr`, selecciones no cuboides |
| Región | 19 | 12 | 4 (`set`, `replace`, `walls`, `faces`) con vista previa | `stack`, `move`, `overlay`, `naturalize`, `smooth`, `hollow`, `line`, `center`… |
| Generación | 13 | 5 | montaña e isla (WE no las tiene) | esferas, cilindros, pirámides, cono, `//generate`, árboles, estructuras |
| Portapapeles y esquemas | 14 | 8 | 10, mejor deshacer y seguridad | `clearclipboard`, pegar con opciones (`-o`, `-s`, `-e`, `-b`, máscara), multi-portapapeles |
| Historial | 3 | 8 (base de datos) | 3, mejor (en disco, seguro) | `clearhistory`, rollback por zona y tiempo, inspeccionar |
| Herramientas y pinceles | 49 | 37 | pincel de terreno (6 modos), mejor en terreno | pinceles de construcción (esfera, cilindro, portapapeles, dispersar…), herramientas (árbol, relleno, info) |
| Navegación | 7 | — | 0 | todas (`ascend`, `descend`, `thru`, `jumpto`, `up`, `ceil`, `unstuck`) |
| Utilidades | 17 | 5 | `cancel`, `confirm`, `help` | `drain`, `fixwater`, `fill`, `removenear`, `snow`, `butcher`… |
| Biomas, chunks, snapshots, scripts | 15 | — | 0 | biomas (medio); el resto, no |
| Máscaras y patrones | ~20 | ~60 | listas con %, estados, tags, `not`, presets: **mejor legibilidad** | `above/below`, bioma, ruido, expresiones, `#clipboard`, `^tipo`, ángulo, superficie |
| API para otros plugins | sí (WorldGuard, PlotSquared, CoreProtect dependen) | la misma | ninguna | API, eventos y puente de selección |

**Respuesta corta para el dueño:** no, Worldcraft no tiene todos los comandos. Hoy cubre cerca del **20 %** del
inventario de WorldEdit: las operaciones básicas de selección, relleno, copia, esquemas y deshacer. En esas lo hace
mejor: sin lag, con vista previa, deshacer en disco y seguro, límites, sin mod y con sintaxis legible. Además tiene
montañas, islas y pincel de terreno, que WorldEdit no tiene. Le faltan las formas, las operaciones de región (`stack`,
`move`, `smooth`…), las utilidades de agua y limpieza, la navegación, los pinceles de construcción, los biomas y la API
para otros plugins. Por la API, un servidor con WorldGuard o PlotSquared **tiene que seguir teniendo WorldEdit**,
aunque los constructores usen Worldcraft (§3).

## 2. Tabla fila por fila

### 2.1 Selección (WorldEdit `SelectionCommands`, `ExpandCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `//wand`, `/toggleeditwand` | Hacha de madera como varita | `/wc wand`: solo la varita marcada | **mejor** (un hacha cualquiera sigue siendo hacha) | — | |
| `//pos1`, `//pos2`, `//pos` | Esquina donde estás o en coordenadas | `/wc pos1/pos2 [x y z]` con `~` y `--world` | = | — | |
| `//hpos1`, `//hpos2` | Esquina en el bloque que miras (a distancia) | falta (la varita exige estar a alcance de clic) | **falta** | B | ✔ |
| `//chunk` | Selecciona el chunk (o varios) | — | **falta** | B | ✔ |
| `//expand`, `//expand vert`, `//contract`, `//shift` | Agrandar, achicar, mover la selección | `/wc expand/contract/shift` con dirección o `vert` | = | — | |
| `//outset`, `//inset` | Agrandar o achicar hacia todos los lados | — | **falta** | B | ✔ |
| `//trim` | Ajustar la selección a los bloques que cumplen una máscara | — | **falta** | B | |
| `//size` | Tamaño y esquinas | `/wc sel` | = | — | |
| `//count <máscara>` | Contar bloques | — | **falta** | B | ✔ |
| `//distr` | Distribución de bloques (%) | — | **falta** | B | ✔ |
| `//sel cuboid\|extend` | Selector de caja / caja que crece con cada clic | solo caja | parcial | B (`extend`) | |
| `//sel poly\|ellipsoid\|sphere\|cyl\|convex` | Polígono, elipsoide, esfera, cilindro, poliedro convexo | — | **falta** | M | |
| FAWE `//sel fuzzy`, `polyhedral` | Selección «mágica» por bloques conectados; poliedro | — | **falta** | M | |
| `//drawsel` y el mod WorldEdit CUI | Mostrar la selección (con un mod en el cliente) | contorno con partículas, sin mod, solo para quien selecciona | **mejor** | — | |
| FAWE `//wer` | Seleccionar la región donde te dejan construir | — | falta (depende de integrar regiones) | M | |

### 2.2 Región (`RegionCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `//set`, FAWE `//air` | Rellenar | `/wc set` con `--preview` y confirmación | **mejor** | — | |
| `//replace` | Reemplazar | `/wc replace <máscara> with <patrón>` | **mejor** (legible, vista previa) | — | |
| `//walls`, `//faces` (`//outline`) | Paredes; caja hueca | `/wc walls`, `/wc faces` | = (+ vista previa) | — | |
| `//stack <n> [dir]` (`-s -a -e -b -m`) | Repetir la selección n veces | — | **falta** | B | ✔ |
| `//move <n> [dir]` (`-s -a -e -b -m`, relleno) | Mover el contenido | — | **falta** | B | ✔ |
| `//overlay <patrón>` | Poner una capa sobre la superficie | — | **falta** | B | ✔ |
| FAWE `//lay` | Poner el bloque de arriba de cada columna | — | **falta** (≈ `overlay`) | B | |
| `//naturalize` | Pasto, 3 de tierra y roca debajo | parcial (estilos del terreno con capas, solo en montaña, isla y pincel) | **falta** como comando | B | ✔ |
| `//smooth [n] [máscara]` | Suavizar la altura de la selección | pincel `smooth` (no derrite árboles ni casas) | parcial (sin la versión por selección) | B | ✔ |
| `//snowsmooth` | Suavizar con capas de nieve | — | **falta** | B | |
| `//hollow [grosor] [patrón]` | Vaciar un objeto por dentro | — | **falta** | B | ✔ |
| `//center` | Bloque(s) del centro | — | **falta** | B | ✔ |
| `//line`, `//curve` | Línea o curva entre puntos | — | **falta** (necesita selector de puntos) | B / M | `line` ✔ |
| `//regen` | Volver a generar el terreno original | — | **falta**: la API de Paper no regenera de forma fiable (`regenerateChunk` no está soportado) | A | |
| `//deform <expresión>` | Deformar con una fórmula | — | **falta** (necesita expresiones; ver 2.10) | A | |
| `//forest`, `//flora` | Árboles o plantas en la selección | — | **falta** (`World#generateTree` de Paper existe) | B–M | |
| `//update` | Aplicar efectos secundarios (conexiones, física) | — | **falta** | M | |
| `//revolve` | Girar la selección alrededor del eje vertical (torno) | — | **falta** | M | |
| FAWE `//fall` | Hacer caer los bloques | — | **falta** | B | |
| FAWE `//fixblocks` | Arreglar conexiones (vallas, paneles) | — | **falta** | M | |
| FAWE `//fixlighting`, `//removelighting`, `//setblocklight`, `//setskylight` | Tocar la luz directamente | — | **no**: necesita código interno del servidor (NMS), el origen de los errores de luz de FAWE ([investigación §1.1](editores-de-mundo.md)); Paper recalcula la luz | — | |
| FAWE `//nbtinfo` | Ver el NBT de un bloque | — | **falta** | B | |
| FAWE `//wea`, `fawe.bypass` | Saltarse las regiones protegidas | — | falta (depende de integrar regiones) | — | |

### 2.3 Generación (`GenerationCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `//sphere`, `//hsphere` (radios x,y,z, `-r` elevada) | Esfera llena o hueca | — | **falta** | B | ✔ |
| `//cyl`, `//hcyl` (radio, alto, grosor) | Cilindro lleno o hueco | — | **falta** | B | ✔ |
| `//pyramid`, `//hpyramid` | Pirámide llena o hueca | — | **falta** | B | ✔ |
| `//cone` | Cono | — | **falta** | B | ✔ |
| `//generate` (`//gen`, `//g`) | Forma desde una fórmula | — | **falta** (expresiones) | A | |
| `//generatebiome` | Biomas desde una fórmula | — | **falta** | A | |
| `/forestgen`, `/pumpkins` | Bosque o calabazas alrededor | — | **falta** | B | |
| `//feature`, `//structure` | Generar un *feature* o estructura del juego | — | **falta** (Paper tiene `StructureManager` para estructuras) | M | |
| — | Montaña natural, isla flotante, con vista previa y estilos | `/wc mountain`, `/wc island`, `reroll` | **mejor** (WE no las tiene) | — | |
| FAWE `//caves` | Red de cuevas | — | **falta** | M–A | |
| FAWE `//ore`, `//ores` | Repartir minerales | — | **falta** | M | |
| FAWE `//img` | Construir una imagen desde un link | — | **falta** (link: misma red segura que SkinStatues 023) | M | |
| FAWE `//blob` (`//rock`) | Esfera deformada (roca) | — | **falta** | B | |

### 2.4 Portapapeles y esquemas (`ClipboardCommands`, `SchematicCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `//copy`, `//cut` (`-e` entidades, `-b` biomas, `-m` máscara) | Copiar, cortar | `/wc copy`, `/wc cut` (`--preview` en `cut`) | parcial: sin entidades, biomas ni máscara | M (entidades) / B (máscara) | máscara ✔ |
| `//paste` (`-a` sin aire, `-o` en el origen, `-s` seleccionar, `-e`, `-b`, `-m`, `-n`) | Pegar | `/wc paste [--air] [--preview]` (sin aire por defecto) | parcial: faltan `-o`, `-s`, máscara | B | ✔ |
| `//rotate <ángulo>` | Girar (cualquier ángulo en y; ejes x/z) | `/wc rotate 90/180/270` | parcial (WE también solo rota bien en múltiplos de 90) | — | |
| `//flip` (`//mirror`) | Espejar | `/wc flip [dir]` | = | — | |
| `/clearclipboard` | Vaciar el portapapeles | — | **falta** | B | ✔ |
| FAWE `//lazycopy`, `//lazycut` | Copia diferida: no lee nada hasta pegar | — | **falta**; en Worldcraft la copia ya va por tandas y no traba | — | |
| FAWE `//place` | Pegar sin aplicar el giro | — | **falta** | B | |
| `/schem save\|load\|list\|delete` | Esquemas | `/wc schem …` con papelera, nombres seguros y topes | **mejor** ([027](../sdd/specs/027-worldcraft-esquemas-schem/spec.md)) | — | |
| `/schem formats` | Formatos (`.schem` y `.schematic` de MCEdit) | solo `.schem` (Sponge v1–v3) | parcial | M | |
| `/schem share`, FAWE `//download` | Subir a un sitio externo | — | **no** sin permiso del dueño (servicio externo, se borra a los 30 días) | — | |
| FAWE `/schem loadall`, `unload`, `clear` | Varios portapapeles; `paste` elige uno al azar | — | **falta** (roadmap 027: «pegar al azar») | B | |
| FAWE `/schem move` | Mover un esquema de carpeta | — | **falta** | B | |

### 2.5 Historial (`HistoryCommands`, FAWE `HistorySubCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `//undo [n] [jugador]`, `//redo` | Deshacer y rehacer (memoria, 15 pasos) | En disco, sobrevive a reinicios; salta lo que otro cambió después | **mejor** | — | |
| `/clearhistory` | Borrar tu historial | — | **falta** | B | ✔ |
| FAWE `/history list` | Tus ediciones | `/wc history [jugador]` | = | — | |
| FAWE `/history rollback <jugador> <tiempo> [radio]`, `restore` | Revertir lo de un jugador en una zona y un tiempo (base de datos) | — | **falta** (roadmap 025: rollback por zona y tiempo, como CoreProtect, con vista previa) | M | |
| FAWE `/history find`, `//inspect`, `/tool inspect` | Quién cambió este bloque | — | **falta** | M | |
| FAWE `/history info`, `distr`, `import` | Resumen, bloques de una edición, importar a la base | — | **falta** | B–M | |

### 2.6 Herramientas, pinceles y superpico (`ToolCommands`, `BrushCommands`, `ToolUtilCommands`, `SuperPickaxeCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `/brush raise\|lower\|smooth\|erode\|dilate\|morph\|paint` | Pinceles de terreno | `/wc brush raise\|lower\|smooth\|flatten\|erode\|paint`: solo el suelo natural, círculo de partículas | **mejor** en terreno (no derrite árboles ni construcciones; estilos por pendiente y altura) | — | |
| FAWE `/brush flatten`, `blendball`, `height`, `cliff`, `stencil`, `layer`, `surface` | Más pinceles de terreno, mapas de alturas | `flatten`, `paint` con estilos | parcial | M | |
| `/brush heightmap`, FAWE `image` | Terreno o color desde una imagen | — | **falta** (roadmap 026: mapas de alturas PNG) | M | |
| `/brush sphere\|cylinder\|set` | Pincel que pone esferas o cilindros de un patrón | — | **falta** | B–M | |
| `/brush clipboard`, FAWE `copypaste`, `populateschematic` | Pegar el portapapeles (o esquemas al azar) con el pincel | — | **falta** | M | |
| `/brush splatter`, FAWE `scatter`, `rock`, `shatter` | Dispersar bloques, rocas, grietas | — | **falta** | M | |
| `/brush gravity`, `snow`, `snowsmooth`, `extinguish`, `butcher`, `biome`, `forest`, `feature`, `deform`, `apply` | Gravedad, nieve, apagar fuego, matar criaturas, biomas, árboles, fórmulas | — | **falta** | B–M c/u | |
| FAWE `/brush line`, `spline`, `surfacespline`, `catenary`, `sweep`, `circle`, `recursive` | Líneas, curvas, cuerdas colgantes, barrer el portapapeles por una curva | — | **falta** (roadmap 026: ríos y caminos) | M–A | |
| FAWE `/brush command`, `scattercommand` | Correr comandos donde toca el pincel | — | **no** (escalada de permisos, como `player_command_as_op` en la 014) | — | |
| `/mask`, `/material`, `/range`, `/size`, `/tracemask` | Ajustes del pincel | opciones `mask=`, `style=`, radio, fuerza; `terrain.brush.range` | parcial | — | |
| FAWE `primary`, `secondary`, `scroll`, `target`, `targetoffset`, `smask`, `transform` | Pincel en cada botón, cambiar con la rueda, modos de puntería | clic izquierdo cambia de modo | parcial | M (menú del pincel en el roadmap 026) | |
| FAWE `savebrush`, `loadbrush`, `/listbrush` | Guardar pinceles | — | **falta** | B | |
| `/tool tree`, `floodfill`, `repl`, `cycler`, `deltree`, `stacker`, `info`, `farwand`, `lrbuild`, `navwand`, `selwand` | Herramientas de un clic | — | **falta** | B c/u | |
| FAWE `/tool featureplacer`, `structureplacer` | Poner *features* o estructuras con un clic | — | **falta** | M | |
| `/superpickaxe single\|area\|recursive`, `//` | Romper al instante, en área o lo conectado | — | **falta** | B | |

### 2.7 Navegación (`NavigationCommands`)
| WE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `/unstuck` (`/!`) | Salir de dentro de un bloque | — | **falta** | B | ✔ |
| `/ascend [n]`, `/descend [n]` | Subir o bajar al piso siguiente | — | **falta** | B | ✔ |
| `/ceil [espacio]` | Subir al techo | — | **falta** | B | ✔ |
| `/thru` | Atravesar la pared | — | **falta** | B | ✔ |
| `/jumpto` (`/j`) | Ir al bloque que miras | — | **falta** | B | ✔ |
| `/up <n>` | Subir n bloques con un bloque de vidrio debajo | — | **falta** | B | ✔ |

### 2.8 Utilidades (`UtilityCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `//fill <patrón> <radio> [prof.]`, `//fillr` | Rellenar un pozo (o recursivo) | — | **falta** | B | ✔ |
| `//drain <radio> [-w]` | Quitar agua o lava conectada (y lo anegado con `-w`) | — | **falta** | B | ✔ |
| `/fixwater`, `/fixlava` | Dejar quieto el líquido que corre | — | **falta** | B | ✔ |
| `/removeabove`, `/removebelow` | Quitar lo de arriba o abajo | — | **falta** | B | ✔ |
| `/removenear <máscara> [radio]`, `/replacenear` | Quitar o reemplazar alrededor, sin seleccionar | — | **falta** | B | ✔ |
| `/snow`, `/thaw`, `/green` | Nevar, derretir, tierra a pasto | — | **falta** | B | ✔ |
| `/extinguish` | Apagar el fuego cerca | — | **falta** | B | ✔ |
| `/butcher`, `/remove <tipo>` | Matar criaturas o quitar entidades de un tipo | — | **falta** (toca entidades: con límites y deshacer, ver §4) | M | |
| `//calculate` | Calculadora | — | **falta**; sin uso sin expresiones | — | |
| `//help`, FAWE `masks`, `transforms` | Ayuda | `/wc help [comando]` con ejemplo | = | — | |
| FAWE `//cancel`, `//confirm` | Cancelar y confirmar | `/wc cancel`, `/wc confirm` con lo que se va a hacer | **mejor** (dice bloques y tiempo; vista previa) | — | |
| FAWE `//macro`, `/hmi` | Grabar macros; interfaz web de mapas de alturas | — | **no** (macros = comandos en nombre del jugador; la web es un servicio externo) | — | |

### 2.9 Biomas, chunks, snapshots y scripts
| WE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `/biomelist`, `/biomeinfo` | Listar biomas; ver el de un bloque | — | **falta** | B | |
| `//setbiome`, `//replacebiome` | Cambiar biomas en la selección | — | **falta** (Paper `setBiome` + reenviar el chunk) | M | |
| `/chunkinfo`, `/listchunks` | Datos del chunk | — | **falta** | B | |
| `/delchunks` | Borrar chunks del disco (se regeneran) | — | **no**: borra archivos de región con el servidor parado; fuera del modelo seguro de la 025 | — | |
| `/snapshot …`, `//restore` | Restaurar desde una copia de seguridad | — | **falta** | A | |
| `/cs`, `/.s` (CraftScripts en JavaScript) | Scripts | — | **no**: ejecutar JavaScript del servidor es un riesgo de seguridad; la automatización la da OmniEvents | — | |
| FAWE `AnvilCommands` (`/anvil …`, editar el mundo apagado) | Edición de archivos de región | — | **no**: en 2.16.2 la clase existe pero **no está registrada** (no son comandos activos) | — | |

### 2.10 Ajustes generales y máscara global (`GeneralCommands`, `WorldEditCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `/we reload\|version\|help\|report`, FAWE `debugpaste`, `threads` | Administración | `/wc reload`, `validate`, `selftest`, `jobs`, `limits` | = (`validate` y `jobs`: **mejor**) | — | |
| `//limit [n]` | Tu límite personal de bloques | límites por grupo de permiso (sin límite personal) | parcial | B | |
| `//timeout` | Tiempo máximo de las expresiones | — | sin expresiones, sin uso | — | |
| `//fast`, `//perf`, `//reorder`, `//watchdog` | Ajustes de rendimiento y efectos secundarios | el motor por tandas los decide | **mejor** (nada que ajustar; no se puede tumbar el servidor) | — | |
| `/gmask`, FAWE `/gsmask`, `/gtransform`, `/gtexture` | Máscara (o transformación) global que se aplica a toda edición | — | **falta** | B | ✔ (`gmask`) |
| `//world` | Fijar el mundo de las ediciones | `--world` en `pos1/pos2` | parcial | — | |
| `/toggleplace`, `/placement` | Pegar o generar en tus pies o en la esquina 1 | — | **falta** | B | |
| `/searchitem`, `//registry` | Buscar un ítem o bloque por nombre | autocompletado y «did you mean» | parcial | — | |
| FAWE `/tips` | Consejos en el chat | — | sin falta (mensajes con ejemplo y error claro) | — | |

### 2.11 Máscaras
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `stone,dirt` | Lista de bloques | `stone, dirt` | = | — | |
| `oak_log[axis=y]` | Estado de bloque | igual | = | — | |
| `##tag` | Tag de bloques del juego | `#logs` | = | — | |
| `!máscara` | Negación | `!` o `not` | **mejor** (legible) | — | |
| `#existing`, `#solid`, `#air` | Existe, sólido, aire | `!air`; aire incluye el de cuevas | parcial (falta `#solid`) | B | ✔ |
| `>máscara`, `<máscara` | Encima de / debajo de | — | **falta** | B | ✔ (`above`/`below`) |
| `#exposed` (7.3), FAWE `#surface`, `#wall`, `#beside`, `~adjacent` | Al aire, superficie, pared, al lado de | — | **falta** | B | ✔ (`exposed`, `surface`) |
| `#region`, `#dregion` | Dentro de la selección | — | **falta** | B | |
| `$bioma` | Bioma | — | **falta** | B | |
| `%50`, `#simplex`, `noise` | Probabilidad o ruido | — | **falta** | B | ✔ (`50% chance`) |
| `=expresión` | Fórmula | — | **falta** (expresiones) | A | |
| `#offset[x][y][z]`, FAWE `/[a][b]` (rango de altura), `#xaxis…` | Desplazamiento, alturas | — | **falta** | B | |
| FAWE `#angle`, `#roc`, `#extrema`, `#surfaceangle` | Pendiente de la superficie | estilos del terreno con `slope_above` | parcial (solo en terreno) | B–M | |
| FAWE `#liquid`, `#hotbar`, `#true/#false` | Líquido, lo de la barra rápida | — | **falta** | B | |
| — | Presets con nombre (`$natural`) en `presets.yml` | sí | **mejor** (WE no tiene) | — | |

### 2.12 Patrones y transformaciones
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `stone`, `oak_stairs[facing=north]` | Bloque, estado | igual | = | — | |
| `50%stone,10%andesite` (pesos relativos) | Mezcla | `70% stone, 30% andesite` (porcentajes reales o partes iguales) | **mejor** | — | |
| `##tag` como patrón, `*oak_log` (estado al azar) | Bloque al azar de un tag; estado al azar | — | **falta** | B | |
| `#clipboard`, `#copy` | Repetir el portapapeles como textura | — | **falta** | B | |
| `^tipo`, `^[estado]` | Cambiar solo el tipo o solo el estado y conservar lo demás | — | **falta** (útil: escaleras de roble a piedra sin girarlas) | B | ✔ |
| `{nbt}` | Bloque con NBT | — | **falta** | M | |
| FAWE `#linear`, `#linear3d`, `#buffer`, `#relative`, `#offset`, `#spread`, `#surfacespread` | Patrones por posición o desplazados | — | **falta** | B–M | |
| FAWE `#color`, `#anglecolor`, `#averagecolor`, `#saturate`, `#desaturate`, `#darken`, `#lighten` | Bloques por color (tabla de colores de bloques) | — | **falta** (SkinStatues ya mide el color de 145 bloques) | M | |
| FAWE `#simplex`, `#perlin`, `#voronoi`, `#ridgedmulti`, `#noise` | Patrones con ruido | ruido en los estilos y el generador | parcial | B | |
| FAWE `#biome`, `#existing`, `#typeswap`, `#fullcopy`, `=expresión` | Otros | — | **falta** | B–A | |
| FAWE transformaciones `#rotate`, `#scale`, `#spread`, `#offset`, `#linear`, `#pattern` (en pinceles y `//gtransform`) | Transformar al poner | — | **falta** | M | |

## 3. API para otros plugins
| Plugin | Qué necesita de WorldEdit | ¿Worldcraft lo puede reemplazar? |
|---|---|---|
| **WorldGuard** | **Dependencia obligatoria** (`depend: [WorldEdit]`): `/rg define` toma la selección de WorldEdit, usa sus clases de regiones y vectores | **No.** WorldGuard no arranca sin WorldEdit (o FAWE). Lo que sí se puede: **puente de selección**, que lo marcado con `/wc wand` llegue a la selección de WorldEdit para `/rg define` |
| **PlotSquared** | Obligatoria (WorldEdit o FAWE): limpiar y copiar parcelas, esquemas de parcelas | No |
| **CoreProtect** | Opcional: registra las ediciones de WorldEdit con su `EditSessionEvent` (por eso se pueden revertir con `/co rollback r:#worldedit`) | Hoy las ediciones de Worldcraft **no quedan en CoreProtect**. Se puede usar la API de CoreProtect (Artistic License 2.0, no GPL) para registrarlas |
| Plugins de protección (GriefPrevention, Towny, Residence, GriefDefender, PlotSquared) | FAWE los consulta para limitar las ediciones a donde el jugador puede construir | La puerta única `EditWorld.set` de la 025 está lista para eso; falta la integración (roadmap: «respetar regiones») |
| Otros (minijuegos, islas, generadores de estructuras) | Pegar esquemas y editar con `EditSession` | Solo si Worldcraft ofrece su propia API (Java) |

**Lo que le falta a Worldcraft:**
1. **API Java propia** (`Worldcraft.api()`): leer la selección de un jugador, encolar una obra (rellenar, pegar un
   esquema) por el motor de tandas, con límites y deshacer, y **eventos** (`WorldcraftEditEvent`, cancelable antes de
   empezar, con jugador, mundo, caja y cantidad de bloques) para que un plugin de protección pueda negar.
2. **Registro en CoreProtect** (opcional, si está instalado).
3. **Puente de selección con WorldEdit** (opcional, si está instalado): para no enlazar con la API GPL, Worldcraft puede
   mandar las esquinas como comandos de WorldEdit en nombre del jugador (`//pos1 x,y,z`), sin compilar contra su API. Hay
   que decidirlo en un ADR (riesgo de licencia frente a hacerlo con su API).

Mientras no haya API: **Worldcraft convive con WorldEdit**. Comandos y varitas distintos, ya probado en la suite de la
027. No reemplaza a WorldEdit en un servidor con WorldGuard o PlotSquared.

## 4. Lo que da más valor (propuesta para la spec 028)
Criterio (lección 004): lo que más se usa al construir y que el motor de la 025 ya resuelve casi solo, porque cada
comando es una forma nueva de `EditSource` con límites, vista previa, deshacer y tandas ya hechos. Lo que necesita
investigación (expresiones, entidades, biomas) va después.

| Grupo | Comandos | Por qué | Costo |
|---|---|---|---|
| A. Formas | `sphere`, `hsphere`, `cyl`, `hcyl`, `pyramid`, `hpyramid`, `cone` | Lo más pedido después de `set`; hoy no hay ninguna forma | Bajo–medio |
| B. Región | `stack`, `move`, `overlay`, `naturalize`, `smooth` (selección), `hollow`, `center`, `line` | Las operaciones de cada día de un constructor | Medio |
| C. Utilidades | `drain`, `fixwater`, `fixlava`, `fill`, `removenear`, `replacenear`, `removeabove`, `removebelow`, `extinguish`, `snow`, `thaw`, `green` | Arreglos rápidos alrededor sin seleccionar | Bajo |
| D. Selección e info | `hpos1/2`, `chunk`, `outset`, `inset`, `count`, `distr`, `clearclipboard`, `clearhistory`; `paste --origin/--select`, `copy --mask` | Detalles que faltan para trabajar sin WorldEdit | Bajo |
| E. Navegación | `unstuck`, `ascend`, `descend`, `ceil`, `thru`, `jumpto`, `up` | Se usan todo el tiempo al construir; muy baratos | Bajo |
| F. Máscaras y patrones | `above`/`below`, `exposed`, `surface`, `solid`, `50% chance` en máscaras; `^tipo` en patrones; máscara global `gmask` | Lo que más se escribe en `replace` y `overlay` | Bajo–medio |

Queda para el roadmap (con su costo): selecciones no cuboides (M), entidades en copiar, pegar y esquemas, `butcher` y
`remove` (M–A), biomas (M), pinceles de construcción y herramientas de un clic (M), API, eventos, CoreProtect y puente
con WorldGuard (M), rollback por zona y tiempo e `inspect` (M), árboles, *features* y estructuras (M), expresiones
`//generate`/`//deform`/`=máscara` (A), cuevas, minerales e imágenes de FAWE (M–A), `regen` y snapshots (A).
No se hará: luz directa (NMS), `delchunks`, CraftScripts, macros, pinceles de comandos, subir a sitios externos sin
permiso del dueño.

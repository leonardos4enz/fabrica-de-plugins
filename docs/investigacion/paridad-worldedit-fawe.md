# Investigación: Worldcraft frente a WorldEdit y FAWE, comando por comando

**Fecha:** 2026-11-01 · **Para:** el dueño de la fábrica · **Spec:** [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md),
corregida en la [029](../sdd/specs/029-worldcraft-paridad-worldedit-2/spec.md) contra los jars ·
**Estado:** la 028 implementó formas, región, utilidades y navegación; la 029–032, selección, máscaras, formas de selección, entidades, pinceles y herramientas (columna «Worldcraft» al día con la 0.8.0)

## 0. Verificación contra los jars (spec 029)
El dueño pidió confirmar que no faltara ningún comando, **no solo con la ayuda sino desde el código**. Se cruzaron tres
fuentes (2026-11-01):

| Fuente | Cómo | WorldEdit 7.3.19 | FAWE 2.16.2 |
|---|---|---|---|
| **Bytecode del jar** | `javap -v` sobre cada clase del jar: anotaciones `@Command` (nombre, alias), `@Switch`/`@ArgFlag` (flags con su descripción) y `@Arg`; las clases de máscaras, patrones y transformaciones con sus prefijos; el enum `SelectorChoice` | **171** comandos | 269 anotados: **244 comandos reales** (21 de `AnvilCommands` y 4 de `ListFilters`, que no son comandos) |
| **Código fuente** (etiquetas `7.3.19` y `2.16.2`) | Las mismas anotaciones en los `.java` | 172: los 171 + `selectworld` y `await`, que son de la versión de línea de comandos (no de Bukkit) | 4 que **no están en el jar**: `//lazycut`, `/brush loadbrush`, `masks`, `transforms` están comentados (`/* … */`). La 028 los contaba |
| **Servidor real** (Paper 1.21.4 build 232, uno a la vez: FAWE y WorldEdit no conviven) | `help WorldEdit` / `help FastAsyncWorldEdit` (mapa de comandos de Bukkit) y `worldedit help -p N <grupo>` (la línea `Usage:` de cada grupo) | 127 de primer nivel + los grupos `/brush` (con `apply` y `paint`), `/tool`, `/schematic`, `/snapshot`, `/superpickaxe`, `/worldedit`: **todo lo del bytecode está registrado** | 164 de primer nivel + `/brush`, `//history`, `/schematic`, `/snapshot`, `/superpickaxe`, `/worldedit`. `/anvil`: «could not be found» (confirma que no está activo). FAWE **no tiene** `//pos`, `//update`, `/placement` ni el grupo `/tool`: las herramientas y los ajustes del pincel van sueltos (`/tree`, `/mask`) y **dentro de `/brush`** (`/brush tree`, `/brush mask`) |

Correcciones a la tabla de la 028 (marcadas **029** abajo):
- **Sobraban** (no existen en el jar): FAWE `//lazycut`, `loadbrush`, `masks`, `transforms`.
- **Faltaban comandos:** `/brush none` y `/tool none` (desatar), `/brush apply|paint item`, FAWE `/brush pull`,
  `/brush structure` y el resto de las herramientas dentro de `/brush`, `/snapshot use|sel|before|after`,
  `/worldedit version|trace|cui|tz`, FAWE `//test` (depuración), FAWE `/schem list all|local|global` (filtros).
- **Faltaban selectores:** FAWE también tiene `hull`, `polyhedron`, `list` y `magic` (además de `fuzzy` y `polyhedral`).
- **Faltaban máscaras y patrones:** la máscara `^[estado]` de WorldEdit (y `^=[…]`, el estado exacto); `#sel`/`#dsel`;
  `#exposed` y `#surface` son el mismo en WorldEdit; FAWE `~2d`, `{mín,máx` (radio), `&` (y), `#true`/`#false`;
  patrones `#nox`/`#noy`/`#noz`, `#mask`, `#solidspread`, `#surfacespread`, `#buffer2d`, `#linear2d`, `#swaptype`,
  `#hotbar`, `#clipboard@[x,y,z]`, `*tag` (estado al azar) y `&`.
- **Faltaban ~40 flags** que cambian lo que hace un comando (se agregaron a su fila): `/butcher -p -n -g -a -b -t -f -r -w`,
  `/up` y `/ceil -f -g`, `/jumpto -f`, `/chunk -c`, `/outset`/`/inset -h -v`, `/size -c`, `/distr -c -d`, `//sel -d`,
  `/green -f`, `//drain -p`, `//paste -n -v -x`, `//copy -c`, `//stack -r`, `//regen -b -r`, `/schem load -r -d`,
  `/schem save -f`, `/schem list -d -n -f`, `/brush sphere -f`, `//ores -b -d`, `/biomeinfo -t -p`, `//setbiome -p`.
- Lo que solo es paginación (`-p <página>`) no se anota.

El dueño preguntó si Worldcraft ya tiene todos los comandos de WorldEdit y FAWE, y en qué sigue siendo mejor. Hasta
ahora nadie lo había comparado fila por fila: la [investigación de la 025](editores-de-mundo.md) miraba qué le duele al
usuario, no el inventario. Esta tabla sigue el modelo de la spec 014 de OmniEvents (paridad con ConditionalEvents).

## Fuente y método
- **Inventario sacado del código**, con permiso del dueño (2026-11-01): **WorldEdit 7.3.19** (etiqueta `7.3.19`
  de `github.com/EngineHub/WorldEdit`, la misma versión que corre en la suite) y **FastAsyncWorldEdit 2.16.2** (etiqueta
  `2.16.2` de `github.com/IntellectualSites/FastAsyncWorldEdit`, la última, publicada en Modrinth el 2026-11-01 para 1.21.1–26.3).
  En la 028, un script propio leyó las anotaciones `@Command` del código fuente; en la **029** se repitió sobre el
  **bytecode de los jars** y se comparó con lo que registra cada uno en un servidor real (§0). Resultado: **171 comandos
  en WorldEdit y 244 en FAWE**, contando subcomandos. Los clones, los jars descomprimidos y los servidores quedaron en el
  scratchpad de la sesión, fuera del repositorio.
- **Licencia:** los dos son GPLv3. El inventario (qué comandos existen y qué hacen) es un dato de entrada. Worldcraft
  se sigue escribiendo desde cero, sin copiar código ni textos de ayuda, como en la 025 y la 027.
- **Comprobado contra la documentación pública** ([comandos de WorldEdit](https://worldedit.enginehub.org/en/latest/commands/)):
  coincide con el código, categoría por categoría.
- **El jar de FAWE** (`FastAsyncWorldEdit-Paper-2.16.2.jar`, aprobado por el dueño) quedó fijado con su sha256 en
  `tests-e2e/lib/plugins-extra.mjs` (regla de la 012) como plugin **alternativo**: choca con WorldEdit, así que solo se
  instala si una suite lo pide con un filtro (`/fastasync/i`).
- **Worldcraft 0.4.0:** `WorldcraftCommand` y `BuildCommands` (`/wc`, 66 subcomandos), su README y las specs 025–028.

**Leyenda.** «Worldcraft» dice qué tiene hoy (0.4.0). **=** equivalente · **mejor** (lo tiene y hace algo que WE/FAWE no) ·
**parcial** (tiene una parte o la forma es peor) · **falta** · **no** (no se hará, con el motivo). Costo: **B** bajo
(horas), **M** medio (una spec normal), **A** alto (varias specs o investigación). «Núcleo» marca lo que la 028
propuso y quedó para después (selección e información, máscaras); la partición de lo que falta está en §4.

## 1. Resumen
Cantidades del bytecode (§0). «FAWE agrega» es la diferencia con WorldEdit en la misma área.
| Área | WE | FAWE agrega | Worldcraft lo tiene (0.4.0) | Lo que le falta a Worldcraft |
|---|---|---|---|---|
| Selección | 18 | — (FAWE quita `//pos`; + selectores `fuzzy`/`magic`, `polyhedral`/`hull`, `list`) | 7, mejor contorno y varita | `hpos`, `chunk`, `outset/inset`, `trim`, `count`, `distr`, selecciones no cuboides |
| Región | 19 | 11 (4 de luz, que no se harán) | 12 con vista previa: `set`, `replace`, `walls`, `faces`, `stack`, `move`, `overlay`, `naturalize`, `smooth`, `hollow`, `center`, `line` | `curve`, `snowsmooth`, máscara en `stack`/`move` (entidades: hecho en la 031; `forest` y `flora`: en la 033; `deform`: en la 034; `regen`: en la 036) |
| Generación | 13 | 5 | formas (7) y montaña e isla (WE no las tiene); desde la 033, árboles del juego (`forest`, `forestgen`, `flora`, `pumpkins`) y plantillas de estructuras | *features* y estructuras completas (sin API en Paper); FAWE: cuevas, minerales, imagen, roca (`//generate`: en la 034) |
| Portapapeles y esquemas | 12 | 7 | 10, mejor deshacer y seguridad | pegar con máscara, multi-portapapeles (entidades: hecho en la 031; biomas: en la 033) |
| Historial | 3 | 8 (`//history`, base de datos) | 3, mejor (en disco, seguro) | `clearhistory`, rollback por zona y tiempo, inspeccionar |
| Herramientas y pinceles | 49 | 34 | pincel de terreno (6 modos), mejor en terreno; desde la 032, 10 pinceles de construcción, 8 herramientas y el superpico | `snow`, los de líneas y curvas de FAWE, guardar pinceles (`tool tree` y `brush forest`: en la 033; `deform`: en la 034) |
| Navegación | 7 | — | 7 (`ascend`, `descend`, `ceil`, `thru`, `jumpto`, `up`, `unstuck`) | `-f`/`-g` en `up` y `ceil`; la varita de navegación |
| Utilidades | 17 | 4 | 12 (`drain`, `fixwater`, `fill`, `snow`…) + `cancel`, `confirm`, `help`; `butcher` y `remove` (031); `calc` (034) | — |
| Biomas, chunks, snapshots, scripts | 14 | — | 4 (033: `biomelist`, `biomeinfo`, `setbiome`; 034: `generatebiome`) + snapshots y `restore` (036) | las entidades de una copia (`-e`); `delchunks` y scripts, no |
| Ajustes generales y administración | 19 | 5 | `reload`, `validate`, `selftest`, `jobs`, `limits` | `gmask`, `//limit` personal, `toggleplace` |
| Máscaras y patrones | ~20 | ~60 | listas con %, estados, tags, `not`, presets: **mejor legibilidad** | ruido, `#clipboard`, ángulo (029: `above/below`, `exposed`, `^tipo`, superficie; 033: `$bioma`; 034: `=fórmula`) |
| API para otros plugins | sí (WorldGuard, PlotSquared, CoreProtect dependen) | la misma | ninguna | API, eventos y puente de selección |

**Respuesta corta para el dueño:** Worldcraft todavía no tiene todos los comandos. Con la 0.4.0 cubre cerca del
**40 %** del inventario de WorldEdit (unos 70 de 171): selección, relleno, copia, esquemas, deshacer, formas, las
operaciones de región de cada día, las utilidades alrededor y la navegación. En eso lo hace mejor: sin lag, con vista
previa, deshacer en disco y seguro, límites, sin mod y con sintaxis legible; y tiene montañas, islas y pincel de
terreno, que WorldEdit no tiene. Le faltan las selecciones que no son cajas, las entidades, los biomas, los pinceles de
construcción, los árboles y estructuras, las fórmulas, lo de FAWE (cuevas, minerales, imágenes), `regen` y snapshots,
y la API para otros plugins. Por la API, un servidor con WorldGuard o PlotSquared **tiene que seguir teniendo
WorldEdit**, aunque los constructores usen Worldcraft (§3).

## 2. Tabla fila por fila

### 2.1 Selección (WorldEdit `SelectionCommands`, `ExpandCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `//wand`, `/toggleeditwand` | Hacha de madera como varita | `/wc wand`: solo la varita marcada | **mejor** (un hacha cualquiera sigue siendo hacha) | — | |
| `//pos1`, `//pos2`, `//pos` | Esquina donde estás o en coordenadas | `/wc pos1/pos2 [x y z]` con `~` y `--world` | = | — | |
| `//hpos1`, `//hpos2` | Esquina en el bloque que miras (a distancia) | falta (la varita exige estar a alcance de clic) | **falta** | B | ✔ |
| `//chunk` (`-c` en coordenadas de chunk, `-s` agrandar) | Selecciona el chunk (o varios) | — | **falta** | B | ✔ |
| `//expand`, `//expand vert`, `//contract`, `//shift` | Agrandar, achicar, mover la selección | `/wc expand/contract/shift` con dirección o `vert` | = | — | |
| `//outset`, `//inset` (`-h` solo horizontal, `-v` solo vertical; **029**) | Agrandar o achicar hacia todos los lados | — | **falta** | B | ✔ |
| `//trim [máscara]` | Ajustar la selección a los bloques que cumplen una máscara (de fábrica `#existing`) | — | **falta** | B | |
| `//size` (`-c` el portapapeles; **029**) | Tamaño y esquinas | `/wc sel` | = (sin `-c`) | B | |
| `//count <máscara>` | Contar bloques | — | **falta** | B | ✔ |
| `//distr` (`-c` del portapapeles, `-d` separado por estado; **029**) | Distribución de bloques (%) | — | **falta** | B | ✔ |
| `//sel cuboid\|extend` (`-d` fija el selector de fábrica; **029**) | Selector de caja / caja que crece con cada clic | `/wc sel cuboid\|box\|extend` (**030**) | = (sin `-d`) | B (`-d`) | |
| `//sel poly\|ellipsoid\|sphere\|cyl\|convex` | Polígono, elipsoide, esfera, cilindro, poliedro convexo | `/wc sel poly\|ellipsoid\|sphere\|cyl\|convex` con los clics de WorldEdit, el contorno de cada forma con partículas sin mod y todos los comandos de región sobre la forma (**030**) | **mejor** (se ve sin CUI; `walls` de un cilindro es el tubo) | — | |
| FAWE `//sel fuzzy`/`magic`, `polyhedral`/`polyhedron`/`hull`, `list` (**029**: `hull`, `list` y `magic` faltaban) | Selección «mágica» por bloques conectados; poliedro (envolvente convexa de los puntos); lista de puntos sueltos | `magic`/`fuzzy` (mismo material, hasta `selection.magic_radius`, leído por tandas) y `hull`/`polyhedral` = `convex` (**030**); falta `list` | parcial (`list`) | B (`list`) | |
| WE `//pos` (`--s`; FAWE no lo tiene; **029**) | Las dos esquinas en un solo comando | `/wc pos1 x y z` y `pos2` | parcial | B | |
| `//wand -n` (**029**) | La varita de navegación (brújula) | `/wc tool navwand` (**032**) | igual | — | |
| `//drawsel` y el mod WorldEdit CUI | Mostrar la selección (con un mod en el cliente) | contorno con partículas, sin mod, solo para quien selecciona | **mejor** | — | |
| FAWE `//wer` | Seleccionar la región donde te dejan construir | — | falta (depende de integrar regiones) | M | |

### 2.2 Región (`RegionCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `//set`, FAWE `//air` | Rellenar | `/wc set` con `--preview` y confirmación | **mejor** | — | |
| `//replace` | Reemplazar | `/wc replace <máscara> with <patrón>` | **mejor** (legible, vista previa) | — | |
| `//walls`, `//faces` (`//outline`) | Paredes; caja hueca | `/wc walls`, `/wc faces` | = (+ vista previa) | — | |
| `//stack <n> [dir]` (`-s -a -e -b -m`, `-r` en bloques; **029**) | Repetir la selección n veces | `/wc stack [n] [dir] [--air] [--select] [--entities]` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md); entidades en la 0.7.0, [031](../sdd/specs/031-worldcraft-entidades/spec.md)) | parcial: sin biomas ni máscara | — | |
| `//move <n> [dir]` (`-s -a -e -b -m`, relleno) | Mover el contenido | `/wc move [n] [dir] [relleno] [--select] [--entities]` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md); entidades en la [031](../sdd/specs/031-worldcraft-entidades/spec.md)); solo visita las dos cajas | parcial: sin biomas ni máscara | — | |
| `//overlay <patrón>` | Poner una capa sobre la superficie | `/wc overlay` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)), no sobre plantas ni troncos | **mejor** | — | |
| FAWE `//lay` | Poner el bloque de arriba de cada columna | — | **falta** (≈ `overlay`) | B | |
| `//naturalize` | Pasto, 3 de tierra y roca debajo | `/wc naturalize` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `//smooth [n] [máscara]` | Suavizar la altura de la selección | `/wc smooth [pasadas]` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) solo sobre el suelo natural | **mejor** (no derrite árboles ni casas); sin máscara | — | |
| `//snowsmooth` | Suavizar con capas de nieve | — | **falta** | B | |
| `//hollow [grosor] [patrón]` (FAWE `-m` máscara) | Vaciar un objeto por dentro | `/wc hollow` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `//center` (`//middle`) | Bloque(s) del centro | `/wc center` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `//line`, `//curve` (`-h` solo la cáscara) | Línea o curva entre puntos | `/wc line [grosor]` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | parcial: falta `curve` (necesita selector de puntos) | M (`curve`) | |
| `//regen [semilla] [bioma]` (`-b` biomas, FAWE `-r` semilla al azar; **029**) | Volver a generar el terreno original | `/wc regen [semilla\|random] [bioma] [--biomes] [--preview]` (`-b`, `-r`; 0.12.0, [036](../sdd/specs/036-worldcraft-regen-snapshots/spec.md): un mundo temporal con la semilla y el generador del mundo, porque `regenerateChunk` no está soportado; los chunks se generan fuera del hilo y se pegan por el motor; cualquier forma de selección, vista previa y deshacer; los cofres de las estructuras con su botín) | **hecho** (crear el mundo temporal frena el servidor 40–140 ms una vez; se reutiliza 60 s) | — | |
| `//deform <expresión>` (`-r -o -c`) | Deformar con una fórmula | `/wc deform <fórmula> [--raw\|--offset\|--center] [--preview]` (0.10.0, [034](../sdd/specs/034-worldcraft-formulas/spec.md); lee todo antes, también fuera de la selección, y los cofres viajan con su contenido) | **hecho** | — | |
| `//forest`, `//flora` | Árboles o plantas en la selección | `/wc forest [árbol] [densidad] [--preview]`, `/wc flora [densidad]` (0.9.0, [033](../sdd/specs/033-worldcraft-biomas-arboles-estructuras/spec.md): los árboles del juego capturados con `generateTree(…, Predicate)` y puestos por el motor, con vista previa y deshacer) | **hecho** | — | |
| `//update` | Aplicar efectos secundarios (conexiones, física) | — | **falta** | M | |
| `//revolve` | Girar la selección alrededor del eje vertical (torno) | — | **falta** | M | |
| FAWE `//fall [relleno]` (`-m` solo dentro de la selección) | Hacer caer los bloques | — | **falta** | B | |
| FAWE `//fixblocks` | Arreglar conexiones (vallas, paneles) | — | **falta** | M | |
| FAWE `//fixlighting`, `//removelighting`, `//setblocklight`, `//setskylight` | Tocar la luz directamente | — | **no**: necesita código interno del servidor (NMS), el origen de los errores de luz de FAWE ([investigación §1.1](editores-de-mundo.md)); Paper recalcula la luz | — | |
| FAWE `//nbtinfo` (`//nbt`) | Ver el NBT de un bloque | — | **falta** | B | |
| FAWE `//test` (**029**) | Comando de depuración de los autores, sin uso para el constructor | — | **no** | — | |
| FAWE `//wea`, `fawe.bypass` | Saltarse las regiones protegidas | — | falta (depende de integrar regiones) | — | |

### 2.3 Generación (`GenerationCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `//sphere`, `//hsphere` (radios x,y,z, `-r` elevada, `-h` hueca) | Esfera llena o hueca | `/wc sphere`, `hsphere` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) con `--raised`, `--thickness` | **mejor** (grosor, vista previa) | — | |
| `//cyl`, `//hcyl` (radio, alto, grosor, `-h`) | Cilindro lleno o hueco | `/wc cyl`, `hcyl` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `//pyramid`, `//hpyramid` (`-h`) | Pirámide llena o hueca | `/wc pyramid`, `hpyramid` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `//cone` (`-h`) | Cono | `/wc cone` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `//generate` (`//gen`, `//g`; `-h` hueca, `-r` coordenadas crudas, `-o` desde la posición, `-c` desde el centro) | Forma desde una fórmula | `/wc generate <patrón> <fórmula>` (alias `gen`; `--hollow --raw --offset --center`, también `-h -r -o -c`; 0.10.0, [034](../sdd/specs/034-worldcraft-formulas/spec.md): la misma cuenta que WorldEdit 7.3.19 en la 3a; se calcula fuera del hilo del servidor) | **mejor** (no traba el servidor, vista previa, deshacer en disco) | — | |
| `//generatebiome` | Biomas desde una fórmula | `/wc generatebiome <bioma> <fórmula>` (alias `genbiome`; 0.10.0, [034](../sdd/specs/034-worldcraft-formulas/spec.md): por celdas de 4×4×4, en su centro) | **hecho** | — | |
| `/forestgen`, `/pumpkins` | Bosque o calabazas alrededor | `/wc forestgen [tamaño] [árbol] [densidad]`, `/wc pumpkins [tamaño] [densidad]` (0.9.0, [033](../sdd/specs/033-worldcraft-biomas-arboles-estructuras/spec.md)) | **hecho** | — | |
| `//feature` (FAWE `//placefeature`), `//structure` | Generar un *feature* o estructura del juego | `/wc structure list\|load` (0.9.0, [033](../sdd/specs/033-worldcraft-biomas-arboles-estructuras/spec.md)): las **plantillas** del juego al portapapeles (`StructureManager`) | parcial: *features* y estructuras completas con piezas unidas no tienen API en Paper (solo el `/place` del juego); decisión del dueño: fuera | M | |
| — | Montaña natural, isla flotante, con vista previa y estilos | `/wc mountain`, `/wc island`, `reroll` | **mejor** (WE no las tiene) | — | |
| FAWE `//caves` | Red de cuevas | `/wc caves [tamaño] [frecuencia] [--rooms]` (0.11.0, [035](../sdd/specs/035-worldcraft-creacion-fawe/spec.md)): túneles «espagueti» y salas «queso» con ruido 3D como el juego desde 1.18 (no los gusanos de FAWE); solo corta `caves.mask`; lava bajo `caves.lava_y` | **hecho** (otros parámetros que FAWE) | — | |
| FAWE `//ore <máscara> <patrón> <tamaño> [frecuencia] [rareza] [y mín] [y máx]`, `//ores` (`-b` y `-d` a deepslate) | Repartir minerales | `/wc ore` (los mismos argumentos), `/wc ores [máscara]` (0.11.0, [035](../sdd/specs/035-worldcraft-creacion-fawe/spec.md)): la veta del juego; la lista en `config.yml` | **mejor** (deepslate según la roca, sin `-d`; lista configurable) | — | |
| FAWE `//img <link>` (solo imgur) | Construir una imagen desde un link | `/wc image <archivo|link> [tamaño] [--floor|--wall]` (alias `img`; 0.11.0, [035](../sdd/specs/035-worldcraft-creacion-fawe/spec.md)): carpeta `images/` siempre; link apagado de fábrica con la red segura de SkinStatues ([ADR 0007](../adr/0007-red-segura-copiada.md)); de pie o acostada según la mirada | **mejor** (cualquier sitio público o la lista del dueño, carpeta local, de pie, vista previa y deshacer) | — | |
| FAWE `//blob` (`//rock`) | Esfera deformada (roca) | — | **falta** | B | |

### 2.4 Portapapeles y esquemas (`ClipboardCommands`, `SchematicCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `//copy`, `//cut` (`-e` entidades, `-b` biomas, `-m` máscara; FAWE `-c` origen en el centro, **029**) | Copiar, cortar | `/wc copy`, `/wc cut` (`--preview` en `cut`; `--mask` en la 029; `--entities` en la [031](../sdd/specs/031-worldcraft-entidades/spec.md); `--biomes` en la [033](../sdd/specs/033-worldcraft-biomas-arboles-estructuras/spec.md)) | **hecho** (salvo FAWE `-c`) | — | |
| `//paste` (`-a` sin aire, `-o` en el origen, `-s` seleccionar, `-n` solo seleccionar, `-e`, `-b`, `-m`; `-v` vacío de estructura; FAWE `-x` quita las entidades de ahí; **029**) | Pegar | `/wc paste [--air] [--preview]` (sin aire por defecto); `--origin`, `--select`, `--only-select` (029); entidades del portapapeles, `--no-entities` y `--remove-entities` (= `-x`, [031](../sdd/specs/031-worldcraft-entidades/spec.md)); `--biomes` ([033](../sdd/specs/033-worldcraft-biomas-arboles-estructuras/spec.md)) | parcial: falta la máscara | B | |
| `//rotate <ángulo>` | Girar (cualquier ángulo en y; ejes x/z) | `/wc rotate 90/180/270` | parcial (WE también solo rota bien en múltiplos de 90) | — | |
| `//flip` (`//mirror`) | Espejar | `/wc flip [dir]` | = | — | |
| `/clearclipboard` | Vaciar el portapapeles | — | **falta** | B | ✔ |
| FAWE `//lazycopy` (`//lazycut` está comentado en el código: **no existe en el jar**, 029) | Copia diferida: no lee nada hasta pegar | — | **falta**; en Worldcraft la copia ya va por tandas y no traba | — | |
| FAWE `//place` (`-a -o -s -n -e -b -x`) | Pegar sin aplicar el giro | — | **falta** | B | |
| `/schem save\|load\|list\|delete` (`save -f` sobrescribir; `list -d -n` orden; FAWE `load -r -d` giro al azar, `save -g`, `list -f` y `all\|local\|global`; **029**) | Esquemas | `/wc schem …` con papelera, nombres seguros y topes | **mejor** ([027](../sdd/specs/027-worldcraft-esquemas-schem/spec.md)); falta el giro al azar | B | |
| `/schem formats` | Formatos (`.schem` y `.schematic` de MCEdit) | solo `.schem` (Sponge v1–v3) | parcial | M | |
| `/schem share`, FAWE `//download` | Subir a un sitio externo | — | **no** sin permiso del dueño (servicio externo, se borra a los 30 días) | — | |
| FAWE `/schem loadall` (`-o -r -d`), `unload`, `clear` | Varios portapapeles; `paste` elige uno al azar | — | **falta** (roadmap 027: «pegar al azar») | B | |
| FAWE `/schem move` | Mover un esquema de carpeta | — | **falta** | B | |

### 2.5 Historial (`HistoryCommands`, FAWE `HistorySubCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `//undo [n] [jugador]`, `//redo` | Deshacer y rehacer (memoria, 15 pasos) | En disco, sobrevive a reinicios; salta lo que otro cambió después | **mejor** | — | |
| `/clearhistory` | Borrar tu historial | — | **falta** | B | ✔ |
| FAWE `/history list` | Tus ediciones | `/wc history [jugador]` | = | — | |
| FAWE `/history rollback <jugador> <tiempo> [radio]`, `restore` | Revertir lo de un jugador en una zona y un tiempo (base de datos) | — | **falta** (roadmap 025: rollback por zona y tiempo, como CoreProtect, con vista previa) | M | |
| FAWE `/history find`, `//inspect`, `/tool inspect` | Quién cambió este bloque | — | **falta** | M | |
| FAWE `//history info`, `distr`, `import`, `clear` | Resumen, bloques de una edición, importar a la base, borrar | — | **falta** | B–M | |

### 2.6 Herramientas, pinceles y superpico (`ToolCommands`, `BrushCommands`, `ToolUtilCommands`, `SuperPickaxeCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `/brush raise\|lower\|smooth\|erode\|dilate\|morph\|paint` | Pinceles de terreno | `/wc brush raise\|lower\|smooth\|flatten\|erode\|paint`: solo el suelo natural, círculo de partículas | **mejor** en terreno (no derrite árboles ni construcciones; estilos por pendiente y altura) | — | |
| FAWE `/brush flatten`, `blendball`, `height`, `cliff`, `stencil`, `layer`, `surface` | Más pinceles de terreno, mapas de alturas | `flatten`, `paint` con estilos | parcial | M | |
| `/brush heightmap`, FAWE `image` | Terreno o color desde una imagen | — | **falta** (roadmap 026: mapas de alturas PNG) | M | |
| `/brush sphere\|cylinder\|set` (`-h` hueca; FAWE `-f` esferas que caen; **029**) | Pincel que pone esferas o cilindros de un patrón | `/wc brush sphere\|cyl\|set` con `--hollow`/`-h` y `mask=` (**032**); solo el ítem de la mano | igual (sin `-f` de FAWE) | — | |
| `/brush none`, `/tool none` (**029**) | Desatar el pincel o la herramienta del ítem en la mano | `/wc brush none\|off`, `/wc tool none` (**032**): el ítem queda como era (nombre y descripción incluidos) | **mejor** | — | |
| `/brush apply\|paint <forma> <radio> forest\|item\|set` (**029**: `item` faltaba) | Aplicar o pintar con una forma: árboles, usar un ítem (huesos, semillas) o poner un bloque | `forest`: `/wc brush forest [árbol] [radio] [densidad]` (0.9.0, [033](../sdd/specs/033-worldcraft-biomas-arboles-estructuras/spec.md)) | parcial: falta `item`, `set` y las formas | M | |
| `/brush clipboard`, FAWE `copypaste`, `populateschematic` | Pegar el portapapeles (o esquemas al azar) con el pincel | `/wc brush clipboard [--air] [--origin]` (**032**: el portapapeles del momento del clic, sin entidades) | parcial (falta `-e`, `copypaste`, `populateschematic`) | M | |
| `/brush splatter`, FAWE `scatter`, `rock`, `shatter` | Dispersar bloques, rocas, grietas | `/wc brush splatter`, `scatter [--overlay]`, `rock` (`blob`) (**032**) | parcial (falta `shatter`) | B | |
| `/brush gravity` (`-h`), `snow` (`-s`), `snowsmooth` (`-l -m`), `extinguish`, `butcher` (flags de `/butcher`), `biome` (`-c`), `forest`, `feature`, FAWE `structure`, `deform` (`-r -o`), `apply` | Gravedad, nieve, apagar fuego, matar criaturas, biomas, árboles, fórmulas | `gravity [--height]`, `extinguish`, `butcher` con las opciones de `/wc butcher` (**032**, con deshacer) | parcial (falta `snow`, `snowsmooth`, `biome`, `feature`, `structure`, `apply`; `forest` hecho en la 033; `deform [radio] [fórmula] [--raw] [--offset]` en la 034) | B–M c/u | |
| FAWE `/brush pull` (**029**) | Tirar del terreno hacia el jugador | — | **falta** | B | |
| FAWE `/brush line`, `spline`, `surfacespline`, `catenary`, `sweep`, `circle`, `recursive` | Líneas, curvas, cuerdas colgantes, barrer el portapapeles por una curva | — | **falta** (roadmap 026: ríos y caminos) | M–A | |
| FAWE `/brush command`, `scattercommand` | Correr comandos donde toca el pincel | — | **no** (escalada de permisos, como `player_command_as_op` en la 014) | — | |
| `/mask`, `/material`, `/range`, `/size`, `/tracemask` | Ajustes del pincel | `mask=`, `radius=` sobre el pincel de la mano (**032**), `style=` y fuerza en el de terreno; `tools.range` y `terrain.brush.range` | parcial (falta `/tracemask`; el patrón se cambia escribiendo el pincel otra vez) | — | |
| FAWE `primary`, `secondary`, `scroll`, `target`, `targetoffset`, `smask`, `transform` | Pincel en cada botón, cambiar con la rueda, modos de puntería | clic izquierdo cambia de modo | parcial | M (menú del pincel en el roadmap 026) | |
| FAWE `savebrush` (`-g` global), `/listbrush` (`loadbrush` está comentado en el código: **no existe en el jar**, 029) | Guardar pinceles | — | **falta** (en FAWE tampoco se pueden volver a cargar) | B | |
| `/tool tree`, `floodfill`, `repl`, `cycler`, `deltree`, `stacker`, `info`, `farwand`, `lrbuild`, `navwand`, `selwand` (FAWE: sueltos, `/tree`, y dentro de `/brush`; sin el grupo `/tool`, **029**) | Herramientas de un clic | `/wc tool repl`, `floodfill`, `info`, `cycler`, `deltree`, `farwand`, `lrbuild`, `navwand` (**032**: cada clic es una obra con deshacer; solo el ítem de la mano); `selwand` es `/wc wand`; `tree` en la [033](../sdd/specs/033-worldcraft-biomas-arboles-estructuras/spec.md) | parcial (falta `stacker`) | B | |
| FAWE `/tool featureplacer`, `structureplacer` | Poner *features* o estructuras con un clic | — | **falta** (sin API en Paper; una plantilla se pega con `brush clipboard`) | M | |
| `/superpickaxe single\|area\|recursive`, `//` | Romper al instante, en área o lo conectado | `/wc superpickaxe single\|area\|recursive\|off`, `/wc sp` (**032**): no suelta ítems y se deshace; nunca la roca madre ni los portales (`tools.never_break`) | **mejor** (deshacer sin duplicar) | — | |

### 2.7 Navegación (`NavigationCommands`)
| WE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `/unstuck` (`/!`) | Salir de dentro de un bloque | `/wc unstuck` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `/ascend [n]`, `/descend [n]` | Subir o bajar al piso siguiente | `/wc ascend`, `descend` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `/ceil [espacio]` (`-f -g`; **029**) | Subir al techo | `/wc ceil` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = (sin `-f`/`-g`) | B | |
| `/thru` | Atravesar la pared | `/wc thru` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `/jumpto` (`/j`; FAWE `-f` forzar; **029**) | Ir al bloque que miras | `/wc jumpto` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `/up <n>` (`-f` volar, `-g` vidrio; **029**) | Subir n bloques con un bloque de vidrio debajo | `/wc up` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)): vuela si puede, si no un vidrio deshacible | **mejor** (el vidrio queda en el historial); sin `-f`/`-g` | B | |

### 2.8 Utilidades (`UtilityCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `//fill <patrón> <radio> [prof.]`, `//fillr` | Rellenar un pozo (o recursivo) | `/wc fill` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)), hondo como el radio (como `fillr`) | = | — | |
| `//drain <radio>` (`-w` lo anegado; FAWE `-p` también las plantas de agua, **029**) | Quitar agua o lava conectada | `/wc drain [--waterlogged]` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = (sin `-p`) | B | |
| `/fixwater`, `/fixlava` | Dejar quieto el líquido que corre | `/wc fixwater`, `fixlava` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `/removeabove`, `/removebelow` | Quitar lo de arriba o abajo | `/wc removeabove`, `removebelow` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `/removenear <máscara> [radio]`, `/replacenear` | Quitar o reemplazar alrededor, sin seleccionar | `/wc removenear`, `replacenear` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `/snow` (`-s` apilar), `/thaw`, `/green` (`-f` también la tierra gruesa; **029**) | Nevar, derretir, tierra a pasto | `/wc snow`, `thaw`, `green` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = (sin `-s`/`-f`) | B | |
| `/extinguish` | Apagar el fuego cerca | `/wc extinguish` (0.4.0, [028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md)) | = | — | |
| `/butcher [radio]` (`-p` mascotas, `-n` NPC, `-g` gólems, `-a` animales, `-b` ambiente, `-t` con nombre, `-f` todos los amistosos, `-r` soportes de armadura, `-w` acuáticos; **029**), `/remove <tipo> <radio>` (ítems, flechas, botes, carros, cuadros, marcos, soportes, xp…) | Matar criaturas o quitar entidades de un tipo | `/wc butcher [radio] --pets --npcs --golems --animals --ambient --water --named --armorstands --friendly`, `/wc remove <tipo> [radio]` ([031](../sdd/specs/031-worldcraft-entidades/spec.md)) | **mejor**: se deshacen, con tope por grupo y permisos propios; falta `-l` (rayos) | — | ✔ |
| `//calculate` (`//calc`, `//eval`) | Calculadora | `/wc calc` (alias `calculate`, `eval`; 0.10.0, [034](../sdd/specs/034-worldcraft-formulas/spec.md)) | **hecho** | — | |
| `//help` (FAWE `masks` y `transforms` están comentados en el código: **no existen en el jar**, 029) | Ayuda | `/wc help [comando]` con ejemplo | = | — | |
| FAWE `//cancel`, `//confirm` | Cancelar y confirmar | `/wc cancel`, `/wc confirm` con lo que se va a hacer | **mejor** (dice bloques y tiempo; vista previa) | — | |
| FAWE `//macro`, `/hmi` | Grabar macros; interfaz web de mapas de alturas | — | **no** (macros = comandos en nombre del jugador; la web es un servicio externo) | — | |

### 2.9 Biomas, chunks, snapshots y scripts
| WE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `/biomelist`, `/biomeinfo` (`-t` el que miras, `-p` donde estás; **029**) | Listar biomas; ver el de un bloque | `/wc biomelist [página] [filtro]`, `/wc biomeinfo [--target\|--here]` (`-t`, `-p`; 0.9.0, [033](../sdd/specs/033-worldcraft-biomas-arboles-estructuras/spec.md)) | **hecho** | — | |
| `//setbiome <bioma>` (`-p` solo tu columna; **029**). `//replacebiome` **no existe** en 7.3.19 ni en FAWE (029); se hace con `setbiome` y la máscara `$bioma` | Cambiar biomas en la selección | `/wc setbiome <bioma> [--here]` (0.9.0, [033](../sdd/specs/033-worldcraft-biomas-arboles-estructuras/spec.md): por celdas de 4×4×4, por el motor, deshacer seguro, `refreshChunk`) | **hecho** | — | |
| `/chunkinfo`, `/listchunks` | Datos del chunk | — | **falta** | B | |
| `/delchunks` | Borrar chunks del disco (se regeneran) | — | **no**: borra archivos de región con el servidor parado; fuera del modelo seguro de la 025 | — | |
| `/snapshot list\|use\|sel\|before\|after`, `//restore` (FAWE `-b -e` biomas y entidades), `/worldedit tz` (zona horaria de las fechas) | Restaurar desde una copia de seguridad | `/wc snapshot list\|use\|sel\|before\|after` (alias `snap`), `/wc restore [copia] [--biomes] [--preview]` (0.12.0, [036](../sdd/specs/036-worldcraft-regen-snapshots/spec.md): copias en carpetas y `.zip` de `snapshots.folder`, apagado de fábrica; lector de `.mca` propio con el NBT de la 027; bloques, cofres y carteles con su contenido, biomas con `--biomes`; copias desde 1.18) | **hecho** salvo las entidades de la copia (`-e`, decisión del dueño), LZ4, copias de antes de 1.18 y `/worldedit tz` (la hora del servidor) | B (`-e`) | |
| `/cs`, `/.s` (CraftScripts en JavaScript) | Scripts | — | **no**: ejecutar JavaScript del servidor es un riesgo de seguridad; la automatización la da OmniEvents | — | |
| FAWE `AnvilCommands` (`/anvil …`, editar el mundo apagado) | Edición de archivos de región | — | **no**: compilada en el jar pero **sin registrar** (029: `/anvil` → «could not be found») | — | |

### 2.10 Ajustes generales y máscara global (`GeneralCommands`, `WorldEditCommands`)
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `/we reload\|version\|help\|report` (`-p`), `trace`, `cui`; FAWE `debugpaste`, `threads` (**029**: `trace` y `cui` faltaban) | Administración; `trace` dice qué extensión cambió cada bloque; `cui` es el saludo del mod WorldEdit CUI | `/wc reload`, `validate`, `selftest`, `jobs`, `limits` | = (`validate` y `jobs`: **mejor**); `cui` no aplica (contorno sin mod) | — | |
| `//limit [n]` | Tu límite personal de bloques | límites por grupo de permiso (sin límite personal) | parcial | B | |
| `//timeout` | Tiempo máximo de las expresiones | `/wc timeout [ms\|default]` (0.10.0, [034](../sdd/specs/034-worldcraft-formulas/spec.md)): el de **todo el cálculo** de un comando, fuera del hilo del servidor, con tope `formulas.max_timeout_ms`; cada evaluación además se corta en `formulas.max_steps` vueltas | **mejor** (WorldEdit evalúa en el hilo del servidor) | — | |
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
| `#existing`, `#solid`, `#air` | Existe, sólido, aire | `!air`; aire incluye el de cuevas | parcial (falta `#solid`) | B | |
| WE `^[estado]`, `^=[estado]` (**029**: faltaba) | Bloques que tienen ese estado (cualquier tipo); `^=` exige el estado completo | `oak_log[axis=y]` exige el tipo | **falta** sin tipo | B | |
| `>máscara`, `<máscara` | Encima de / debajo de | — | **falta** | B | ✔ (`above`/`below`) |
| `#exposed` (7.3; `#surface` es el mismo en WE, **029**), FAWE `#surface`, `#wall`, `#beside`, `~adjacent`, `~2d` (**029**) | Al aire, superficie, pared, al lado de | — | **falta** | B–M (la máscara de hoy solo mira el estado del bloque, no los vecinos) | |
| `#region` (`#sel`, `#selection`), `#dregion` (`#dsel`) | Dentro de la selección (fija o la que tengas al usarla) | — | **falta** | B | |
| `$bioma` | Bioma | `$desert` (0.9.0, [033](../sdd/specs/033-worldcraft-biomas-arboles-estructuras/spec.md); el preset del mismo nombre gana y `validate` avisa) | **hecho** | — | |
| `%50`, `#simplex`, `noise` | Probabilidad o ruido | — | **falta** | B | ✔ (`50% chance`) |
| `=expresión` | Fórmula | `=y<64` (0.10.0, [034](../sdd/specs/034-worldcraft-formulas/spec.md); x, y, z del mundo; también en `gmask`) | **hecho** (sin `query()`: no lee bloques) | — | |
| `#offset[x][y][z]`, FAWE `/[a][b]` (rango de altura), `#xaxis…` | Desplazamiento, alturas | — | **falta** | B | |
| FAWE `#angle`, `#roc`, `#extrema`, `#surfaceangle` | Pendiente de la superficie | estilos del terreno con `slope_above` | parcial (solo en terreno) | B–M | |
| FAWE `#liquid`, `#hotbar`, `#true`/`#false`, `{mín,máx` (radio), `&` (todas a la vez) (**029**) | Líquido, lo de la barra rápida, distancia, conjunción | `,` es «o»; no hay «y» | **falta** | B | |
| — | Presets con nombre (`$natural`) en `presets.yml` | sí | **mejor** (WE no tiene) | — | |

### 2.12 Patrones y transformaciones
| WE / FAWE | Qué hace | Worldcraft | Brecha | Costo | Núcleo |
|---|---|---|---|---|---|
| `stone`, `oak_stairs[facing=north]` | Bloque, estado | igual | = | — | |
| `50%stone,10%andesite` (pesos relativos) | Mezcla | `70% stone, 30% andesite` (porcentajes reales o partes iguales) | **mejor** | — | |
| `##tag` y `*##tag` como patrón, `*oak_log` (estado al azar) | Bloque al azar de un tag; estado al azar | — | **falta** | B | |
| `#clipboard`, `#copy`, `#clipboard@[x,y,z]` (desplazado; **029**) | Repetir el portapapeles como textura | — | **falta** | B | |
| `^tipo`, `^[estado]` | Cambiar solo el tipo o solo el estado y conservar lo demás | — | **falta** (útil: escaleras de roble a piedra sin girarlas) | B | ✔ |
| `{nbt}` | Bloque con NBT | — | **falta** | M | |
| FAWE `#linear`, `#linear2d`, `#linear3d`, `#buffer`, `#buffer2d`, `#relative`, `#offset`, `#spread`, `#solidspread`, `#surfacespread`, `#nox`/`#noy`/`#noz`, `#mask`, `&` (**029**: varios faltaban) | Patrones por posición, desplazados, sin repetir, con máscara, encadenados | — | **falta** | B–M | |
| FAWE `#color`, `#anglecolor`, `#averagecolor`, `#saturate`, `#desaturate`, `#darken`, `#lighten` | Bloques por color (tabla de colores de bloques) | los mismos menos `#anglecolor` (0.11.0, [035](../sdd/specs/035-worldcraft-creacion-fawe/spec.md)): la tabla de 145 bloques de SkinStatues, editable en `config.yml` (`colors`) | parcial (falta `#anglecolor`) | B | |
| FAWE `#simplex`, `#perlin`, `#voronoi`, `#ridgedmulti`, `#noise` | Patrones con ruido | ruido en los estilos y el generador | parcial | B | |
| FAWE `#biome`, `#existing`, `#typeswap` (`#ts`), `#fullcopy`, `#hotbar`, `=expresión` | Otros | `=fórmula -> a, b, c` (0.10.0, [034](../sdd/specs/034-worldcraft-formulas/spec.md)): el número de la fórmula elige el bloque (FAWE da un número de bloque viejo) | parcial (falta `#biome`, `#existing`, `#typeswap`, `#fullcopy`, `#hotbar`) | B | |
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

## 4. Lo que da más valor
### 4.1 La 028 (hecha)
El dueño eligió formas (A), región (B), utilidades (C) y navegación (E): hechas en la
[028](../sdd/specs/028-worldcraft-paridad-worldedit/spec.md) (0.4.0). Selección e información (D) y máscaras (F) quedaron
para la siguiente.

### 4.2 Lo que falta, partido en specs (propuesta de la 029)
Alcance pedido por el dueño: **todo menos la API para otros plugins**, que va al final. Es demasiado para una sola spec
(lección 004). Criterio: primero lo que se usa todos los días y cambia poco el motor; después lo que cambia una pieza
central (la selección, el historial con entidades); al final lo que necesita un lenguaje nuevo o investigación.
Costo: **B** horas · **M** una spec normal · **A** más de una spec o con riesgo de que Paper no lo permita.

| Spec | Grupo | Qué entra | Costo | Por qué en este orden |
|---|---|---|---|---|
| **029** | Selección, información y máscaras (D + F) + la spec chica de la 027 + la deuda de la 028 | `hpos1/2`, `chunk`, `outset`, `inset`, `trim`, `count`, `distr`, `clearclipboard`, `clearhistory`, `paste --origin/--select/--only-select`, `copy --mask`; máscaras `above`/`below`, `exposed`, `surface`, `solid`, `50% chance`, `^[estado]`; patrones `^tipo` y `^[estado]`; `gmask`; `/wc schem import` y `worldcraft.schematic.save`; pruebas de `center` y `fixwater` | M | Lo que más se escribe al construir y en lo que el motor ya está; las máscaras con vecinos son el cambio más grande (hoy la máscara solo mira el estado) |
| ~~030~~ (hecha) | Selecciones que no son cajas | `sel poly`, `ellipsoid`, `sphere`, `cyl`, `convex`, `extend`; FAWE `fuzzy`/`magic` (bloques conectados); contorno con partículas de cada forma; `set`, `replace`, `copy`, `count`, `walls`… sobre cualquier forma | M | El dueño pidió no perderlas de vista; toca la selección, que usan casi todos los comandos |
| ~~031~~ (hecha) | Entidades | `copy`/`cut`/`paste`/`stack`/`move --entities`, entidades en los `.schem` (Sponge v2/v3), `paste --remove-entities`, `butcher` (con sus 9 tipos), `remove <tipo>`; deshacer que devuelve las entidades | M | Paper 1.21.4 copia y recrea entidades sin NMS (`Entity#createSnapshot`, `getAsString`, `EntityFactory`); hay que extender el historial |
| ~~032~~ (hecha) | Pinceles de construcción y herramientas | `brush sphere`, `cyl`, `set`, `clipboard`, `splatter`/`scatter`, `rock` (`blob`), `gravity`, `extinguish`, `butcher`, `none`; herramientas `floodfill`, `repl`, `cycler`, `info`, `deltree`, `farwand`, `lrbuild`, `navwand`, superpico (`tool tree` pasó a la 033, decisión del dueño) | M | Reusa el pincel de la 026 (mira, círculo, una obra por pasada) |
| ~~033~~ (hecha) | Biomas, árboles y estructuras | `biomelist`, `biomeinfo`, `setbiome`, máscara `$bioma`, biomas en `copy`/`paste`/`.schem`; `forest`, `flora`, `forestgen`, `pumpkins`, `brush forest`, `tool tree`; `structure` (plantillas del juego) | M | `setBiome` + `refreshChunk`; los árboles con `generateTree(…, Predicate)` pasan por el motor (vista previa y deshacer). Hecha en la 0.9.0: los *features* y las estructuras completas no tienen API en Paper (solo `/place`); el dueño los dejó fuera |
| ~~034~~ (hecha) | Fórmulas | `generate`, `generatebiome`, `deform`, máscara y patrón `=fórmula`, `calculate`, `brush deform`, `timeout` | A | Hecha en la 0.10.0 con un lenguaje propio como el de WorldEdit ([ADR 0006](../adr/0006-formulas-de-worldcraft.md)); queda `query()` (leer bloques desde la fórmula), `gmegabuf`, `closest()` y `switch` (B) |
| ~~035~~ (hecha) | Lo de FAWE para crear | `caves`, `ore`/`ores`, `img` desde la carpeta o un link (red segura de SkinStatues 023, [ADR 0007](../adr/0007-red-segura-copiada.md)), patrones por color (tabla de colores de SkinStatues) | M–A | Hecha en la 0.11.0 ([035](../sdd/specs/035-worldcraft-creacion-fawe/spec.md)); queda `#anglecolor`, tramado de la imagen, pinceles de imagen y `//blob` como comando (B c/u) |
| ~~036~~ (hecha) | `regen` y snapshots | Volver al terreno original (mundo temporal con la misma semilla, porque `regenerateChunk` no está soportado) y restaurar desde una copia de seguridad (lector de `.mca` con el NBT de la 027) | A | Hecha en la 0.12.0 ([036](../sdd/specs/036-worldcraft-regen-snapshots/spec.md)); queda: las entidades de una copia (`-e`, B), LZ4 y copias de antes de 1.18 |
| final | API para otros plugins | API Java, `WorldcraftEditEvent`, CoreProtect, puente con WorldGuard (§3) | M | Decisión del dueño (028): al final |

Fuera de esta partición (se anotan en el roadmap, el dueño no los pidió ahora): rollback por zona y tiempo e
`inspect` (M), multi-portapapeles y pegar al azar (B), `curve` (M), `snowsmooth`, `fall`, `fixblocks`, `update`,
`revolve` (B–M c/u), más patrones de FAWE (posición, desplazados, ruido) y transformaciones (M), `//limit` personal,
`toggleplace` (B).
No se hará: luz directa (NMS), `delchunks`, CraftScripts, macros, pinceles que corren comandos, `//test`, subir a sitios
externos sin permiso del dueño, `AnvilCommands` (FAWE no los registra).

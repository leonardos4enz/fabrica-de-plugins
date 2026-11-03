# Spec 041 — Worldcraft: lo que le falta de FAWE 2.16.2 (39 de los 44 «not yet» y `//fixlighting`)

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a) (ver [verify](verify.md)) · CLARIFY respondido por el dueño (2026-11-03, abajo) · **Plugin:** Worldcraft (0.15.0 → 0.16.0)
· **Depende de:** [032](../032-worldcraft-pinceles-y-herramientas/spec.md) (pinceles y herramientas),
[033](../033-worldcraft-biomas-arboles-estructuras/spec.md) (plantillas), [035](../035-worldcraft-creacion-fawe/spec.md)
(carpeta `images/` y tabla de colores), [037](../037-worldcraft-api/spec.md) (eventos), [040](../040-worldcraft-lo-que-falta-de-worldedit/spec.md)
(curva, puente NMS) · **Origen:** la tabla «What FAWE 2.16.2 adds to WorldEdit» de
[commands.md](../../../worldcraft/presentacion/commands.md): 44 comandos de FAWE están «not yet».

## Problema
Worldcraft tiene 166 de los 244 comandos de FAWE 2.16.2. Quien viene de FAWE escribe `/br spline`, `/br copypaste`,
`//gmask`… `//gtransform`, `/target`, `/scroll`, `//fall`, `/tool inspect` y recibe «Unknown command». Además
`//fixlighting` quedó «no, a propósito» porque Paper no da API para la luz.

## CLARIFY (respuestas del dueño, 2026-11-03)
| Pregunta | Respuesta |
|---|---|
| ¿Qué grupos? (a) pinceles de líneas, (b) pinceles avanzados, (c) historial, (d) global, (e) sueltos; se recomendó a+d+e | **a, b, d y e** |
| (c) `//history restore\|rollback\|import\|find\|distr` (rollback por zona y tiempo sobre el historial en disco), costo A | **Otra spec** (siguen «not yet») |
| `//fixlighting` y los otros tres de luz con el puente NMS (ADR 0009 dice que el puente nunca escribe) | **Solo `//fixlighting`**: pedirle al motor de luz del juego que recalcule los chunks de la selección; `removelighting`, `setblocklight`, `setskylight` siguen «no, a propósito» |

## Costo estimado (lección 004)
| Grupo | Comandos | Costo |
|---|---|---|
| (a) pinceles de líneas | `line`, `spline`, `sweep`, `catenary`, `surfacespline`, `circle`, `pull`, `recursive` | M |
| (b) pinceles avanzados | `shatter`, `stencil`, `image`, `height`, `populateschematic`, `copypaste`, `savebrush`, `listbrush` | M–A |
| (d) global | `//gtexture`, `//gsmask`, `//gtransform`, `/smask`, `/transform`, `/target`, `/targetoffset`, `/scroll`, `/primary`, `/secondary` | M |
| (e) sueltos | `//place`, `//fall`, `//fixblocks`, `//nbtinfo`, `//wea`, `//wer`, `//registry`, `/schem loadall\|move`, `/tool inspect\|featureplacer\|structureplacer`, `/worldedit debugpaste` | M |
| luz | `//fixlighting` | B (Paper tiene `relightChunks` en su motor de luz) |

## Historias

Todo lo que cambia bloques pasa por el motor (vista previa donde aplica, límites del grupo de quien lo hace, máscara
global, deshacer). Cada pincel es uno de la 032: ítem de la mano, `mask=`, círculo de partículas, permisos y límites de
quien hace clic en cada clic.

**H1 — Pinceles de líneas y curvas (grupo a).**
- CA1: `/wc brush line <patrón> [grosor] [--shell] [--select] [--flat]`: el primer clic marca un punto («Added point…»),
  el segundo traza la línea de uno al otro con ese grosor; `--shell` deja la cáscara; `--select` deja marcado el punto
  final (la siguiente sigue desde ahí); `--flat` la traza a la altura del primer punto.
- CA2: `/wc brush spline <patrón> [grosor]` y `/wc brush surfacespline <patrón> [grosor]`: cada clic agrega un punto;
  un clic en el mismo bloque que el último cierra y traza una curva suave por todos (la de `//curve`, 040). `surfacespline`
  la apoya en la superficie: cada punto de la curva baja o sube a la cima de su columna. Con menos de 2 puntos lo dice.
- CA3: `/wc brush sweep [copias]`: los puntos como `spline`; al cerrar pega el portapapeles a lo largo de la curva,
  girado (de a 90°) según hacia dónde va; con `copias` > 0, esa cantidad repartida pareja; sin número, una tras otra
  cada largo del portapapeles. Sin portapapeles lo dice.
- CA4: `/wc brush catenary <patrón> [largo] [grosor] [--shell] [--select] [--facing]`: dos clics, una cuerda colgante
  entre ellos con `largo` veces la distancia (de fábrica 1,2; 1 = recta); `--facing` la hace colgar hacia donde mira.
- CA5: `/wc brush circle <patrón> [radio] [--fill]`: un círculo de ese radio alrededor del bloque que mira, de frente a
  quien lo usa (perpendicular a la mirada); `--fill` lo rellena.
- CA6: `/wc brush pull [radio] [pasadas]`: tira del terreno hacia el jugador: el aire de la esfera que tiene detrás (del
  lado contrario al jugador) un bloque sólido se llena con ese bloque, por pasadas.
- CA7: `/wc brush recursive <patrón> [radio] [--depth-first]`: los bloques conectados al que mira dentro de la esfera
  (los del mismo material, o los que cumplen `mask=`) toman el patrón.

**H2 — Pinceles avanzados (grupo b).**
- CA1: `/wc brush shatter <patrón> [radio] [líneas]`: parte el terreno de la esfera en `líneas` pedazos al azar
  (Voronoi): los bloques sólidos en el borde entre dos pedazos toman el patrón (de fábrica, aire).
- CA2: `/wc brush stencil <patrón> [radio] [imagen] [--white] [--random-rotate]`: una imagen gris de `images/` sobre el
  círculo pinta la superficie: donde es clara (más que la mitad; con `--white`, solo blanca) la cima toma el patrón. Sin
  imagen, un cono (pinta más cerca del centro).
- CA3: `/wc brush image <imagen> [radio] [--alpha] [--fade]`: los colores de una imagen de `images/` sobre el círculo:
  cada cima toma el bloque de color más parecido (la tabla de la 035, o la textura de H3 CA1); `--alpha` salta lo
  transparente; `--fade` mezcla hacia el borde.
- CA4: `/wc brush height [radio] [imagen] [escala] [--random-rotate]`: sube (o baja, con escala negativa) el terreno con
  un mapa de alturas de `images/` (sin imagen, un cono), como `heightmap` de la 040; lo blanco sube `escala` × radio bloques (como FAWE, que estira la imagen al tamaño).
- CA5: `/wc brush populateschematic <máscara> <esquema|carpeta> [radio] [densidad] [--rotate]`: esquemas de la
  carpeta de esquemas (uno o uno al azar de una carpeta) en lugares al azar del círculo sobre lo que cumple la máscara;
  `densidad` (1–100) es cuánto del círculo se cubre; `--rotate` los gira al azar de a 90°.
- CA6: `/wc brush copypaste [radio] [--random-rotate] [--auto-rotate]`: clic izquierdo copia al portapapeles lo
  conectado al bloque que mira dentro del radio (lo que no es aire); clic derecho lo pega ahí; `--random-rotate` gira
  al azar, `--auto-rotate` según hacia dónde mira.
- CA7: `/wc brush savebrush <nombre> [--global]` guarda el pincel de la mano (`brushes/<jugador>/` o, con `--global` y
  permiso, `brushes/`); `/wc brush listbrush [página]` lista los guardados; `/wc brush loadbrush <nombre>` lo pone en la
  mano (FAWE no lo tiene: su `loadbrush` está comentado). Un nombre con `/`, `\` o `..` se rechaza.

**H3 — Lo global y el pincel de la mano (grupo d).**
- CA1: `/wc gtexture <máscara>|#clipboard|off`: los bloques entre los que eligen los patrones por color (`#color`,
  `#darken`…) y el pincel `image`: los de la tabla de colores que cumplen la máscara, o los del portapapeles. Solo en
  memoria, como `gmask`.
- CA2: `/wc gsmask <máscara>|off`: máscara global de **origen**: en toda obra propia, un bloque que la obra pondría y
  no cumple la máscara se deja (con `!air`, pegar no pone el aire). Se dice al pedir cada obra, como `gmask`.
- CA3: `/wc gtransform <transformación>|off`: toda obra propia pone sus bloques transformados: `#offset[x][y][z]`,
  `#rotate[grados]` (alrededor del centro de la obra, de a 90°), `#flip[x|y|z]`, `#spread[n]` (cada bloque corrido al
  azar hasta n), `#scale[n]` (cada bloque se vuelve un cubo de n, 1–4); varias juntas, separadas por espacios. Los límites
  se miden con la caja ya transformada.
- CA4: `/wc smask <máscara>|off` y `/wc transform <transformación>|off`: lo mismo, solo para el pincel de la mano.
- CA5: `/wc target <block|forward|height|face>` (sin nada, el siguiente): dónde apunta el pincel de la mano: el primer
  bloque (de fábrica), el punto a `range` bloques hacia donde mira aunque sea aire, el bloque a la altura de los pies
  bajo la mira, o el bloque delante de la cara tocada. `/wc targetoffset <n>`: corre el blanco n bloques hacia afuera
  de la cara tocada (negativo, hacia adentro).
- CA6: `/wc scroll <none|size|range|targetoffset|target|mask|pattern|clipboard> [valores…]`: girar la rueda (pasar a la
  casilla de al lado) con el pincel en la mano cambia eso en vez de cambiar de casilla: el radio (±1), el alcance, el
  desplazamiento, el modo de blanco, la máscara o el patrón (pasa por la lista dada), o el esquema cargado con
  `schem loadall`. Saltar casillas con los números sigue cambiando de ítem. Se dice en la barra de acción.
- CA7: `/wc primary <pincel…>` y `/wc secondary <pincel…>`: el pincel del clic derecho y el del clic izquierdo del mismo
  ítem (`/wc secondary sphere air 3`: el izquierdo borra). Los dos con sus permisos y límites.

**H4 — Sueltos (grupo e).**
- CA1: `/wc place [--air --origin --select --only-select --entities --biomes --remove-entities]` (`//place -a -o -s -n -e
  -b -x`): pega el portapapeles **sin** el giro ni el espejo que se le aplicó.
- CA2: `/wc fall [relleno] [--inside]` (`//fall -m`): los bloques de la selección caen hasta apoyarse; lo que dejan
  queda con el relleno (de fábrica aire); `--inside` no cae más abajo de la selección. Los que tienen datos (cofres) no se mueven.
- CA3: `/wc fixblocks` (`//fixblocks`, `//fixconnect`): lo mismo que `/wc update` (040).
- CA4: `/wc nbtinfo` (`//nbt`): el NBT del bloque que mira (cofre, cartel, generador), legible; sin datos, lo dice.
- CA5: `/wc wea [on|off]` (`//wea`, permiso `worldcraft.bypass`, de fábrica op): prende o apaga saltarse las protecciones: con
  él, una obra propia que otro plugin detiene (`WorldcraftEditEvent`, 037) sigue y se dice quién la habría detenido.
  El evento lo sabe (`isBypassing()`).
- CA6: `/wc wer` (`//wer`): selecciona la zona donde le dejan construir, preguntando a los plugins de protección con un
  evento nuevo de la API (`WorldcraftAllowedRegionEvent`); si ninguno contesta, lo dice. El plugin de ejemplo contesta.
- CA7: `/wc registry <registro> [búsqueda] [página]`: busca en los registros del servidor (`block`, `item`, `biome`,
  `entity`, `enchantment`, `effect`, `sound`, `structure`, `feature`…), 10 por página.
- CA8: `/wc schem loadall <archivo|carpeta…>` carga varios esquemas: `/wc paste` pega uno al azar cada vez (`/wc
  clearclipboard` los suelta); `/wc schem move <carpeta>` mueve el último esquema cargado a otra carpeta de esquemas.
- CA9: `/wc tool inspect [radio]`: clic en un bloque dice quién lo cambió con Worldcraft, de qué a qué y hace cuánto (el
  historial en disco de todos), los 10 más nuevos; permiso `worldcraft.moderate` (el de mirar el historial de otros).
- CA10: `/wc tool featureplacer <feature>` (puente NMS) y `/wc tool structureplacer <plantilla>` (las plantillas de la
  033): clic derecho lo pone sobre el bloque que mira.
- CA11: `/wc debugpaste` (`/worldedit debugpaste`, administración): escribe en `reports/` el informe de `/wc report` con
  el final de `logs/latest.log` y los `.yml` de Worldcraft; no lo sube a ningún lado.

**H5 — `//fixlighting`.**
- CA1: `/wc fixlighting` (`//fixlighting`): recalcula la luz de los chunks de la selección (o del chunk donde está) con
  el motor de luz del juego, por el puente NMS (ADR 0009, ampliado: pide recalcular, no escribe bloques); dice cuántos
  chunks. Si el puente no está, «not available on this server version». Tope: `tools.fixlighting_max_chunks`
  (de fábrica 64).

**H6 — Lo de siempre.**
- CA1: Cada comando nuevo tiene ayuda con ejemplo, autocompletado, su `//` (la 039, y los nombres de FAWE: `/target`,
  `/scroll`, `//gtexture`, `//fixconnect`…) y errores con «did you mean».
- CA2: `commands.md`, las descripciones y el póster dicen los números nuevos; `PresentacionTest` los ata a la tabla.
- CA3: README de Worldcraft y raíz, roadmap; capturas: ninguna nueva (lección 004).

## Fuera de alcance
El grupo (c) historial (rollback por zona y tiempo), `//removelighting`, `//setblocklight`, `//setskylight`, las
estructuras completas del juego (`structureplacer` usa plantillas: queda «en parte», como `//structure`) y lo «en parte»
anterior.

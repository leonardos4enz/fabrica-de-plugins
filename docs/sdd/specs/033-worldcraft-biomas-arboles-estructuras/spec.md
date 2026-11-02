# Spec 033 — Worldcraft: biomas, árboles y estructuras del juego

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a, 33/33 dos veces) (ver [verify](verify.md)) · CLARIFY respondido por el dueño (2026-11-02: todo menos los *features*; los biomas se copian y
pegan solo con `--biomes`; las estructuras del juego van al portapapeles) · **Plugin:** Worldcraft (`/wc`, hoy 0.8.0 →
0.9.0) · **Depende de:** [025](../025-worldcraft-nucleo/spec.md) (motor, deshacer, límites), [027](../027-worldcraft-esquemas-schem/spec.md)
(`.schem`), [029](../029-worldcraft-paridad-worldedit-2/spec.md) (máscaras), [030](../030-worldcraft-selecciones-no-cuboides/spec.md)
(selecciones), [031](../031-worldcraft-entidades/spec.md) (etapas de la obra), [032](../032-worldcraft-pinceles-y-herramientas/spec.md)
(pinceles y herramientas) · **Origen:** la partición aceptada por el dueño en la 029 (033 = biomas, árboles y
estructuras; en la 032 el dueño pasó `tool tree` y `brush forest` aquí).
**Investigación:** [paridad con WorldEdit y FAWE](../../../investigacion/paridad-worldedit-fawe.md) §2.3, §2.6, §2.9, §2.10
y §4.2 (fila 033).

## Problema
- Worldcraft no sabe nada de biomas: no los lista, no dice cuál hay, no los cambia, no se pueden usar en una máscara y
  se pierden al copiar, pegar o guardar un `.schem` (WorldEdit los lleva con `-b`).
- Para un bosque hay que poner cada árbol a mano; no hay plantas, calabazas, herramienta de árbol ni pincel de bosque.
- Las estructuras que trae el juego (iglú, casas de aldea, barcos hundidos, ruinas) no se pueden traer para pegarlas.

## Costo estimado (lección 004)
| Pieza | Costo |
|---|---|
| Biomas en el motor: leer y poner por celdas de 4×4×4, una etapa de la obra por tandas, deshacer, reenviar los chunks | ~20 % |
| `biomelist`, `biomeinfo`, `setbiome`, máscara `$bioma` | ~15 % |
| Biomas en `copy`/`cut`/`paste` (`--biomes`) y en los `.schem` (v2 y v3) | ~15 % |
| Árboles por el motor (`generateTree` capturado): `forest`, `forestgen`, `tool tree`, `brush forest` | ~20 % |
| `flora`, `pumpkins` | ~5 % |
| Estructuras del juego al portapapeles (`structure list|load`) | ~10 % |
| Pruebas en el juego y documentación | ~15 % |

Una spec larga (como la 031 o la 032). El dueño eligió todo menos los *features* (`//feature`: Paper no tiene API; solo
el `/place` del juego, sin vista previa ni deshacer del motor): van al roadmap.

## Historias

**H1 — Ver los biomas.** Como *constructor*, quiero saber qué biomas hay y cuál tiene cada lugar.
- CA1: `/wc biomelist [página] [filtro]` lista los biomas del servidor (los de los paquetes de datos también), 15 por
  página, con el nombre corto (`plains`) y el completo si no es de `minecraft:`.
- CA2: `/wc biomeinfo` dice los biomas de la selección con su porcentaje (los más primero, como `distr`);
  `--target` (alias `-t`) el del bloque que se mira; `--here` (alias `-p`) el de donde está el jugador.

**H2 — Cambiar biomas.** Como *constructor*, quiero pintar un bioma en la selección.
- CA1: `/wc setbiome <bioma>` cambia el bioma de la selección (cualquier forma, 030), con «did you mean» si el nombre
  está mal. El juego guarda el bioma por **celdas de 4×4×4**: cambia cada celda que toca la selección (como WorldEdit) y
  lo dice. `--here` (alias `-p`) solo la celda donde está el jugador.
- CA2: Es una obra del motor: límites del grupo (el tamaño cuenta como bloques), mundos permitidos, confirmación sobre
  `confirm.above`, por tandas dentro de los 8 ms por tick, en el historial, `/wc undo` y `/wc redo` (seguro: una celda
  que otro cambió después se deja y se cuenta). Los jugadores ven el bioma nuevo sin salir y entrar (se reenvían los
  chunks tocados).
- CA3: La máscara global (`gmask`) se respeta: solo cambian las celdas cuyo bloque central pasa la máscara.

**H3 — Máscara de bioma.** Como *constructor*, quiero cambiar bloques solo en un bioma.
- CA1: `$desert` (como WorldEdit) es una máscara que pasa donde el bioma es ese; se combina con lo demás
  (`$desert and sand`, `!$plains`, `$desert, $badlands`). Sirve en `replace`, `set`, `count`, `copy --mask`, `gmask`,
  `replacenear`… (donde se admiten máscaras que miran la posición, 029).
- CA2: Si un *preset* de máscara se llama igual, gana el preset (como hasta ahora) y `/wc validate` lo avisa. Un nombre
  que no es preset ni bioma da error con «did you mean».

**H4 — Biomas al copiar, pegar y guardar.** (decisión del dueño: solo si se pide, como WorldEdit)
- CA1: `/wc copy --biomes` y `/wc cut --biomes` guardan los biomas en el portapapeles (`cut` no cambia los biomas del
  lugar). Sin la opción, el portapapeles no lleva biomas.
- CA2: `/wc paste --biomes` pone los biomas del portapapeles (girados y volteados con él); sin la opción, solo bloques.
  Se deshace con lo demás. La vista previa no muestra biomas (lo dice).
- CA3: `/wc schem save` guarda los biomas si el portapapeles los tiene (Sponge v3, `Biomes`); `/wc schem load` lee los de
  v2 (`BiomePalette`/`BiomeData`, por columna) y v3, y dice cuántos traía. Un bioma que el servidor no tiene se salta y
  se dice. WorldEdit 7.3.19 lee los biomas de Worldcraft y al revés.

**H5 — Árboles.** Como *constructor*, quiero poner árboles del juego con un comando o un clic.
- CA1: Los árboles los hace el generador del juego (los mismos que crecen de un brote) y pasan por el motor: límites,
  máscara global, vista previa, historial y deshacer. Tipos con los nombres de WorldEdit (`oak`, `birch`, `spruce`,
  `big_oak`, `jungle`, `dark_oak`, `cherry`, `mangrove`, `pale_oak`, `crimson_fungus`, `red_mushroom`, `chorus`… y
  `random`), con «did you mean».
- CA2: `/wc forest [tipo=oak] [densidad=5]`: árboles en la selección, sobre el pasto o la tierra que tiene aire encima,
  con esa probabilidad en % por columna (0–100, como WorldEdit). `--preview` muestra el bosque antes.
- CA3: `/wc forestgen [tamaño=10] [tipo] [densidad=5]`: lo mismo alrededor del jugador (sin selección).
- CA4: `/wc tool tree [tipo]`: clic derecho pone un árbol sobre el bloque que se mira (una obra que se deshace).
- CA5: `/wc brush forest [tipo] [radio=5] [densidad=5]`: clic derecho, un bosque en el círculo donde se mira.
- CA6: Un árbol que no cabe (techo, otro árbol) no se pone y no rompe nada; si ninguno cupo, lo dice.

**H6 — Plantas y calabazas.**
- CA1: `/wc flora [densidad=10]`: en la selección, sobre el pasto pone pasto corto y flores; sobre la arena, arbustos
  secos y algún cactus (los de fábrica en `nature.flora`, editables).
- CA2: `/wc pumpkins [tamaño=10] [densidad=2]`: matas de calabazas (con sus hojas) sobre el pasto alrededor del jugador.
- CA3: Las dos con vista previa (`--preview`), límites y deshacer.

**H7 — Estructuras del juego.** (decisión del dueño: al portapapeles)
- CA1: `/wc structure list [filtro] [página]` lista las plantillas del juego y de los paquetes de datos
  (`igloo/top`, `village/plains/houses/plains_small_house_1`, `shipwreck/rightsideup_full`…).
- CA2: `/wc structure load <nombre>` deja la plantilla en el portapapeles, como un `.schem`: se pega con `/wc paste`
  (girar, voltear, vista previa, deshacer, límites). Sin `minecraft:` se entiende del juego; «did you mean» si no existe.
- CA3: Los bloques vacíos de la plantilla (`structure_void`) no pisan lo que hay al pegar; los bloques de unión
  (`jigsaw`) y los marcadores de datos (bloque de estructura en modo `data`) quedan como aire. Los cofres llegan con su tabla de botín si la tienen.

**H8 — Seguridad, límites y ayuda.**
- CA1: Topes en la configuración (`nature.*`: árboles por obra, tamaño de `forestgen`/`pumpkins`); los bloques prohibidos
  del grupo se revisan con lo que de verdad va a poner el árbol o la estructura.
- CA2: Errores con «did you mean», autocompletado de biomas, tipos de árbol y estructuras, `/wc help <subcomando>` con
  ejemplo para cada uno nuevo (`setbiome`, `forest`, `structure`…); `/wc validate` revisa la sección nueva.

**H9 — Documentación.** README de Worldcraft (biomas, árboles y plantas, estructuras, «Coming from WorldEdit»),
README raíz, tabla de paridad y roadmap.

## Fuera de alcance (al [roadmap](../../roadmap.md))
Los *features* del juego (`//feature`, FAWE `//placefeature`, `/tool featureplacer`) y las estructuras completas con
piezas unidas (`//structure` de WorldEdit 7.3: una aldea entera, `/tool structureplacer`): Paper no tiene API, solo el
`/place` del juego (sin vista previa ni deshacer del motor). `//generatebiome` (034, fórmulas). `/brush biome`.
Las entidades de las plantillas (el aldeano del iglú). Biomes en `stack`/`move`. Vista previa de biomas.

## Decisiones (CLARIFY)
| # | Pregunta | Propuesta de la IA | Respuesta del dueño (2026-11-02) |
|---|---|---|---|
| 1 | ¿Qué entra? | Todo menos los *features* | **Todo menos features** |
| 2 | ¿Los biomas se copian y pegan siempre? | Solo con `--biomes`, como WorldEdit y las entidades de la 031 | **Solo con --biomes** |
| 3 | ¿Cómo se ponen las estructuras del juego? | Al portapapeles y se pega con `/wc paste` | **Al portapapeles** |

Decisiones de la IA: la máscara de bioma usa `$nombre` (WorldEdit) y el preset gana si se llaman igual; el bioma se
cambia por celdas de 4×4×4 (lo que guarda el juego); `setbiome` no tiene vista previa (el cliente no la puede mostrar
sin reenviar el chunk); `density` de `pumpkins` en % (WorldEdit usa 0,02: un número menor que 1 se toma como fracción).

## Preguntas que se le hicieron al dueño
1. ¿Qué entra? (todo menos features · todo, features incluidos con `/place` · solo biomas y árboles)
2. ¿Los biomas se copian y pegan siempre, o solo si se pide? (solo con `--biomes` · siempre)
3. ¿Cómo se ponen las estructuras del juego? (al portapapeles · directo donde mira · las dos)

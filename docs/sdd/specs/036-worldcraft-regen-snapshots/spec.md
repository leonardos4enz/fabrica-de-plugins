# Spec 036 — Worldcraft: `regen` y copias de seguridad (snapshots)

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a) (ver [verify](verify.md)) · CLARIFY respondido por el dueño (2026-11-02: las dos piezas, sin las criaturas de la copia; copias
en carpetas y en `.zip`; solo restaurar, sin `snapshot create`) · **Plugin:** Worldcraft (`/wc`, hoy 0.11.0 → 0.12.0) ·
**Depende de:** [025](../025-worldcraft-nucleo/spec.md) (motor, deshacer, límites), [027](../027-worldcraft-esquemas-schem/spec.md)
(lector NBT, paleta de bloques de otras versiones, datos de cofres y carteles), [030](../030-worldcraft-selecciones-no-cuboides/spec.md)
(selecciones), [033](../033-worldcraft-biomas-arboles-estructuras/spec.md) (biomas en el portapapeles) · **Origen:** la
partición aceptada por el dueño en la 029 (036 = `regen` y snapshots, marcada «A»).
**Investigación:** [paridad con WorldEdit y FAWE](../../../investigacion/paridad-worldedit-fawe.md) §2.4 (`//regen`), §2.9
(`/snapshot list|use|sel|before|after`, `//restore`) y §4.2 (fila 036).

## Problema
- Un constructor que arruinó una zona, o un dueño que limpia el daño de un *griefer*, quiere volver al terreno como lo
  hizo el generador (`//regen` de WorldEdit) o como estaba en una copia de seguridad (`//restore` con `/snapshot`).
  Worldcraft no tiene ninguna de las dos.
- Paper no regenera chunks: `World.regenerateChunk` tira «not supported». Sin NMS, la única forma de obtener el terreno
  original es **generarlo en otro mundo** con la misma semilla y copiarlo.
- Una copia de seguridad son archivos de región (`.mca`) que escribió el juego (a veces en un `.zip`): leerlos es leer
  archivos que vienen de afuera del servidor (rotos, enormes, de otra versión), sin cargarlos como mundo.
- Todo tiene que pasar por el motor de la 025: 8 ms por tick, vista previa, deshacer en disco y límites por grupo.

## Costo estimado (lección 004)
| Pieza | Costo |
|---|---|
| `regen`: mundo temporal con la misma semilla (o otra, o un bioma), chunks generados fuera del hilo, copiados y pegados por el motor, biomas con `--biomes`, limpieza | ~35 % |
| Copias: lector de `.mca` (carpetas y `.zip`), `snapshot list|use|sel|before|after`, `restore` con bloques, cofres y carteles con su contenido, biomas | ~35 % |
| Pruebas en el juego y documentación | ~20 % |
| (fuera, al roadmap) Las criaturas de la copia (`-e` de FAWE) | ~10 % |

## Historias

**H1 — Volver al terreno original.** `/wc regen [semilla|random] [bioma] [--biomes] [--preview]`.
- CA1: La selección (cualquier forma, 030) vuelve a ser como la generó el mundo: los bloques, los cofres de las
  estructuras con su botín y, con `--biomes` (`-b` de WorldEdit), los biomas. Lo de afuera de la selección no cambia.
- CA2: De fábrica, con la semilla y el generador del mundo (también uno de otro plugin o el plano). `random` (`-r` de
  FAWE) usa una semilla al azar; un número o una palabra, esa semilla (una palabra como el juego). Un bioma
  (`regen 0 desert`) genera todo con ese bioma y escribe los biomas.
- CA3: Es una obra del motor: límites del grupo (mundos, tope de bloques, bloques prohibidos), máscara global,
  `--preview`, tandas de 8 ms, historial y `undo`. Las criaturas que hay no se tocan (como WorldEdit).
- CA4: Genera en un mundo temporal que el jugador no ve; los chunks se generan fuera del hilo del servidor. Hasta
  `regen.max_chunks` chunks por obra; una a la vez en el servidor («another regen is running»). El mundo temporal queda
  cargado `regen.keep_seconds` para la siguiente y después se borra del disco; uno que quedó de un apagón se borra al
  arrancar. Nunca se borra una carpeta que no creó Worldcraft.

**H2 — Elegir una copia de seguridad.** `/wc snapshot list|use|sel|before|after` (alias `snap`).
- CA1: Las copias están en `snapshots.folder` (apagado de fábrica): cada copia es una carpeta o un `.zip` con las
  carpetas de los mundos adentro (`2026-11-02-14-30/world/region/r.0.0.mca`, también una carpeta más dentro del `.zip`).
- CA2: `list [página]` muestra las copias que tienen el mundo del jugador, de la más nueva a la más vieja, con número y
  fecha (la del nombre si la tiene, si no la del archivo). `use <nombre|latest>`, `sel <número>`, `before <fecha>` (la
  más nueva antes de esa fecha) y `after <fecha>` (la más vieja después) eligen una; la fecha es `2026-11-02` o
  `2026-11-02 14:30`. Un nombre mal escrito sugiere con «did you mean».
- CA3: La copia elegida es de cada jugador hasta que salga; sin elegir, `restore` usa la más nueva y lo dice.

**H3 — Restaurar.** `/wc restore [copia] [--biomes] [--preview]`.
- CA1: La selección (cualquier forma) vuelve a como estaba en la copia: bloques, cofres, carteles y lo demás con datos
  (como un `.schem` de la 027) y, con `--biomes`, los biomas. Lo de afuera de la selección no cambia.
- CA2: Lee copias del juego desde 1.18 (las de otra versión: los bloques que cambiaron de nombre se traducen; los que no
  existen aquí se dejan como están y se cuentan). Un chunk que no está en la copia, sin terminar de generar, de antes de
  1.18 o con una compresión que no se soporta (LZ4) se deja como está y se dice cuántos.
- CA3: Es una obra del motor (H1 CA3), con `--preview`. Hasta `snapshots.max_chunks` chunks; la lectura va fuera del
  hilo del servidor.
- CA4: Un archivo roto, enorme o que no es una copia no tumba nada: se dice qué falló y no se toca el mundo.

**H4 — Seguridad, ayuda y documentación.**
- CA1: Permisos `worldcraft.regen` y `worldcraft.snapshot` (de fábrica solo OP).
- CA2: Secciones `regen` y `snapshots` en `config.yml`, revisadas por `/wc validate` (fuera de rango avisa y usa el de
  fábrica; claves desconocidas con «did you mean»). Un `config.yml` de la 0.11.0 sin ellas carga igual.
- CA3: `/wc help <subcomando>` con ejemplo; autocompletado de copias, números y biomas.
- CA4: README de Worldcraft («Coming from WorldEdit/FAWE», configuración, cómo actualizar), README raíz, tabla de
  paridad y roadmap.

## Fuera de alcance (al [roadmap](../../roadmap.md))
Las criaturas de la copia (`-e` de FAWE, decisión del dueño), `snapshot create` (decisión del dueño: las copias las hace
otra herramienta), copias de antes de 1.18, la compresión LZ4, la forma de WorldEdit con la carpeta del mundo primero
(`copias/world/2026-…/`), `/worldedit tz` (las fechas van en la hora del servidor), `regen` de un mundo con *datapack*
de generación propio que no sea el del mundo.

## Decisiones (CLARIFY)
| # | Pregunta | Propuesta de la IA | Respuesta del dueño (2026-11-02) |
|---|---|---|---|
| 1 | ¿Qué entra? (`regen` ~35 %, copias ~35 %, criaturas ~10 %) | Las dos, sin criaturas | **Las dos, sin criaturas** |
| 2 | ¿Cómo están guardadas las copias? | Carpetas y `.zip` | **Carpetas y .zip** |
| 3 | ¿`/wc snapshot create`? | No: las copias las hace otra herramienta (como WorldEdit) | **No, solo restaurar** |

Decisiones de la IA: `regen` genera en un mundo temporal creado con la API (`WorldCreator` con la semilla, el tipo, el
ambiente y el generador del mundo; sin cargar el *spawn*) porque `regenerateChunk` no está soportado; lo generado se lee
y se pega con el mismo camino que `copy`/`paste --biomes` (vista previa, límites y deshacer sin código nuevo en el
motor); una sola `regen` a la vez en el servidor (un mundo temporal cuesta memoria); el mundo temporal se marca con un
archivo antes de crearlo y solo se borra una carpeta con esa marca. `restore` arma un portapapeles desde los `.mca` con el
NBT de la 027 y la paleta de `SchemBlocks` (las mismas traducciones de nombres); los bloques desconocidos se **dejan**
(no se vuelven aire, como en un `.schem`: restaurar no debe borrar).

## Preguntas que se le hicieron al dueño
1. ¿Qué entra? (las dos sin criaturas · las dos con criaturas · solo regen · solo copias)
2. ¿Cómo guarda las copias un servidor? (carpetas y .zip · solo carpetas)
3. ¿Agregamos `/wc snapshot create`? (no, solo restaurar · sí, copia de la zona)

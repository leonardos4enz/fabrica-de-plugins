# Spec 031 — Worldcraft: entidades

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a, 25/25 dos veces) (ver [verify](verify.md)) · CLARIFY respondido por el dueño (2026-11-02: todo entra; `copy` sin entidades salvo `--entities`,
como WorldEdit; `butcher` como WorldEdit; `worldcraft.butcher` y `worldcraft.remove` aparte) · **Plugin:** Worldcraft
(`/wc`, hoy 0.6.0 → 0.7.0) · **Depende de:** [025](../025-worldcraft-nucleo/spec.md) (motor por tandas, deshacer en disco,
límites), [027](../027-worldcraft-esquemas-schem/spec.md) (`.schem`), [028](../028-worldcraft-paridad-worldedit/spec.md)
(`stack`, `move`), [030](../030-worldcraft-selecciones-no-cuboides/spec.md) (selecciones de cualquier forma) ·
**Origen:** la partición aceptada por el dueño en la 029 (031 = entidades).
**Investigación:** [paridad con WorldEdit y FAWE](../../../investigacion/paridad-worldedit-fawe.md) §4.2 (fila 031) y las
filas de `//copy`, `//paste`, `//stack`, `//move`, `/butcher` y `/remove`.

## Problema
- Worldcraft solo mueve **bloques**: al copiar una casa se pierden los cuadros, los marcos con sus ítems, los soportes de
  armadura y los animales del corral; `cut` y `move` los dejan flotando donde estaban; un `.schem` de WorldEdit con
  entidades avisa «N entities, not loaded» y las descarta; al guardar un esquema no se guardan.
- No hay cómo limpiar criaturas ni entidades sueltas (`/butcher`, `/remove` de WorldEdit): quien construye usa
  `/kill @e[...]`, que no se deshace y mata mascotas y aldeanos sin querer.
- El historial en disco (025) solo sabe de bloques: deshacer no devolvería una entidad.

## Costo estimado (lección 004)
| Pieza | Costo |
|---|---|
| Entidades en `copy`/`cut`/`paste`/`stack`/`move` (leer, quitar, crear por tandas; girar y voltear posición, mirada y cara de los colgantes) | M− (~35 %) |
| Diario con entidades y deshacer/rehacer que las devuelve (con el cambio de UUID al recrearlas) | ~25 % |
| `butcher` (9 opciones de WorldEdit) y `remove <tipo>` | ~15 % |
| Entidades en los `.schem` (Sponge v2/v3, leer y escribir) | ~25 % → menos: Paper 1.21.4 da el NBT binario de una entidad (`UnsafeValues#serializeEntity`/`deserializeEntity`) y el lector NBT de la 027 ya existe; no hace falta un traductor de SNBT |

Total: una spec larga. El dueño eligió hacer todo en esta.

## Historias

**H1 — Copiar, cortar y pegar con entidades.** Como *constructor*, quiero que una construcción copiada traiga sus
cuadros, marcos, soportes y criaturas cuando lo pido.
- CA1: `/wc copy --entities` y `/wc cut --entities` guardan en el portapapeles las entidades **cuyo bloque está dentro de
  la selección** (cualquier forma, 030), con todo lo suyo (ítems del marco, armadura del soporte, nombre, lo que lleva
  encima). Sin `--entities` no se copia ninguna (como `//copy -e` de WorldEdit; decisión del dueño). Nunca jugadores.
- CA2: `cut --entities` quita esas entidades del mundo (sin soltar nada) y se puede deshacer.
- CA3: `/wc paste` pone las entidades que tenga el portapapeles en su lugar relativo (`--no-entities` las deja). El
  mensaje de copiar y pegar dice cuántas entidades. `rotate` y `flip` giran su posición, hacia dónde miran y la pared de
  marcos y cuadros.
- CA4: `/wc paste --remove-entities` quita antes las entidades que haya en la caja donde se pega (FAWE `-x`); deshacer
  las devuelve.
- CA5: Una entidad que va montada en otra viaja con ella (no se duplica).
- CA6: La vista previa muestra solo bloques y dice cuántas entidades se van a crear o quitar.

**H2 — `stack` y `move` con entidades.**
- CA1: `/wc stack [n] [dir] --entities` repite también las entidades de la selección en cada copia.
- CA2: `/wc move [n] [dir] [relleno] --entities` lleva las entidades con los bloques (las quita de donde estaban y las
  pone en el lugar nuevo, las mismas, con su contenido).

**H3 — Deshacer devuelve las entidades.**
- CA1: `/wc undo` de una obra que quitó entidades las devuelve; de una que las creó, las quita. `/wc redo` lo inverso.
  Funciona después de reiniciar el servidor (el diario está en disco).
- CA2: Seguro como con los bloques: una entidad creada por la obra que ya no existe (la mataron, se fue a otro mundo) se
  salta y se cuenta.
- CA3: El mensaje dice cuántas entidades volvieron o se quitaron.

**H4 — `/wc butcher`.** Como *constructor o moderador*, quiero limpiar los monstruos de alrededor sin matar mascotas.
- CA1: `/wc butcher [radio]` quita (sin soltar nada) las criaturas hostiles en ese radio (de fábrica
  `entities.butcher_radius`, tope `entities.max_radius`). Nunca jugadores.
- CA2: Como WorldEdit (decisión del dueño), de fábrica se salvan y se agregan con opciones: `--pets` (domesticadas),
  `--npcs` (aldeanos, comerciantes y NPC de Citizens), `--golems`, `--animals`, `--ambient` (murciélagos), `--water`
  (peces, calamares, delfines), `--named` (los que tienen nombre), `--armorstands`, `--friendly` (todos los amistosos:
  pets, npcs, golems, animals, ambient, named y los demás que no son hostiles, como el allay). `--at x,y,z` y `--world`
  para la consola, como las utilidades de la 028.
- CA3: Dice cuántas quitó y de qué tipos; se deshace con `/wc undo`. Más de `max_entities` del grupo: quita hasta el
  tope y lo dice.
- CA4: Permiso `worldcraft.butcher` (de fábrica, OP).

**H5 — `/wc remove <tipo> [radio]`.**
- CA1: Tipos de WorldEdit: `items`, `arrows`, `projectiles`, `xp`, `boats`, `minecarts`, `tnt`, `falling`, `paintings`,
  `itemframes`, `armorstands`, o el id de una entidad (`cow`, `minecraft:cow`) con «did you mean». Nunca jugadores.
- CA2: Se deshace como `butcher`. Permiso `worldcraft.remove` (de fábrica, OP).

**H6 — Entidades en los `.schem`.**
- CA1: `/wc schem save` guarda las entidades del portapapeles (Sponge v3, `Entities` con `Pos` relativa a la esquina,
  `Id` y `Data`); WorldEdit y FAWE las cargan.
- CA2: `/wc schem load` carga las entidades de un esquema v2 o v3 (de WorldEdit, FAWE o Worldcraft); el juego las
  actualiza si el esquema es de una versión más vieja. Las que no se pueden leer se cuentan y se dicen.

**H7 — Seguridad y límites.**
- CA1: Cada grupo de límites tiene `max_entities` (entidades que una obra puede crear o quitar) y `blocked_entities`
  (tipos que no puede crear: de fábrica el carro con bloque de comandos, el carro generador, la TNT y su carro, el
  bloque que cae, el wither y el dragón). Se revisa también lo que va montado.
- CA2: Pegar (también lo cargado de un esquema) y `stack` con más entidades que el tope o con un tipo prohibido se
  rechaza antes de tocar nada, diciendo cuál.

**H8 — Documentación.** README de Worldcraft (entidades, `butcher`, `remove`, «Coming from WorldEdit»), README raíz,
tabla de paridad y roadmap.

## Fuera de alcance (al [roadmap](../../roadmap.md))
Entidades en la vista previa; `//paste -e` de WorldEdit como opción separada (aquí pegar pone lo que se copió);
`brush butcher` (va en la 032); `/butcher -l` (rayos); entidades en las obras de terreno y formas; biomas (033).

## Decisiones (CLARIFY)
| # | Pregunta | Propuesta de la IA | Respuesta del dueño (2026-11-02) |
|---|---|---|---|
| 1 | ¿Qué entra? | Todo (copiar/pegar, deshacer, `butcher`/`remove`, `.schem`) | **Todo** |
| 2 | ¿Qué entidades trae `copy`? | Decoración siempre, criaturas con `--entities` | **Como WorldEdit: ninguna sin `--entities`** |
| 3 | `butcher` sin opciones | Como WorldEdit (solo hostiles; lo demás con opciones) | Como WorldEdit |
| 4 | Permisos | `worldcraft.butcher` y `worldcraft.remove` aparte | Aparte |

Decisión de la IA (consecuencia de la #2): la elección se hace **una vez, al copiar**. `paste` pone lo que el portapapeles
tenga (WorldEdit pide `-e` otra vez al pegar); `--no-entities` lo evita. Un esquema con entidades las trae al cargar y lo
dice.

## Preguntas que se le hicieron al dueño
1. ¿Qué entra en la 031? (todo · todo menos los `.schem` · solo copiar/pegar y deshacer)
2. Al copiar, ¿qué entidades vienen? (decoración sí y criaturas con `--entities` · como WorldEdit · todas siempre)
3. `/wc butcher` sin opciones, ¿qué mata? (como WorldEdit · solo monstruos, sin opciones)
4. Permisos (`butcher` y `remove` aparte · todo con `worldcraft.use`)

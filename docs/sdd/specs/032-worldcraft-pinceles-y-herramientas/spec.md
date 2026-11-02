# Spec 032 — Worldcraft: pinceles de construcción y herramientas de un clic

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a, 29/29 dos veces) (ver [verify](verify.md)) · CLARIFY respondido por el dueño (2026-11-02: todo menos la herramienta de árboles, que pasa a la
033; el ítem de la mano se vuelve el pincel, solo ese; pinceles y herramientas con `worldcraft.use` y el superpico con
`worldcraft.superpickaxe`; el superpico no suelta ítems) · **Plugin:** Worldcraft (`/wc`, hoy 0.7.0 → 0.8.0) ·
**Depende de:** [025](../025-worldcraft-nucleo/spec.md) (motor, deshacer, límites), [026](../026-worldcraft-terreno-facil/spec.md)
(el pincel de terreno: mira, círculo, una obra por pasada), [028](../028-worldcraft-paridad-worldedit/spec.md) (formas,
`fill`, navegación), [031](../031-worldcraft-entidades/spec.md) (`butcher`) · **Origen:** la partición aceptada por el
dueño en la 029 (032 = pinceles y herramientas).
**Investigación:** [paridad con WorldEdit y FAWE](../../../investigacion/paridad-worldedit-fawe.md) §2.6 (filas de
`/brush`, `/tool` y `/superpickaxe`) y §4.2 (fila 032).

## Problema
- Quien construye con WorldEdit hace la mitad del trabajo con **pinceles** (una esfera de piedra donde mira, un
  portapapeles pegado con un clic, rocas, salpicar bloques) y **herramientas** (reemplazar el bloque que mira, la varita
  de lejos, la brújula para atravesar paredes, el superpico). En Worldcraft solo existe el pincel de **terreno** (026):
  para todo lo demás hay que escribir un comando por cada cosa.
- `/wc butcher` (031) pide pararse en el lugar; no hay pincel para limpiar criaturas donde se mira.

## Costo estimado (lección 004)
| Pieza | Costo |
|---|---|
| Base común: el ítem de la mano se vuelve pincel o herramienta (marca en el PDC, nombre y descripción), mira, círculo, una obra por clic con límites y deshacer, `none` | ~15 % |
| Pinceles: `sphere`, `cyl`, `set`, `clipboard`, `splatter`, `scatter`, `rock` (`blob`), `gravity`, `extinguish`, `butcher` | ~35 % |
| Herramientas: `repl`, `floodfill`, `info`, `cycler`, `deltree`, `farwand`, `lrbuild`, `navwand` | ~30 % |
| Superpico (`single`, `area`, `recursive`) | ~10 % |
| Pruebas en el juego y documentación | ~10 % |

Una spec larga (como la 031). El dueño eligió todo menos `tool tree` (va a la 033, con los árboles por el motor).

## Historias

**H1 — El ítem de la mano se vuelve el pincel (base común).** Como *constructor*, quiero ponerle un pincel o una
herramienta al ítem que tengo en la mano.
- CA1: `/wc brush <tipo> …` y `/wc tool <tipo> …` marcan **solo el ítem de la mano** (otro del mismo tipo sigue normal;
  decisión del dueño). Con la mano vacía dan uno (`tools.item`, de fábrica una vara de blaze; la brújula para `navwand`).
  Con un pincel o herramienta en la mano, lo vuelven a ajustar (también uno de terreno: deja de ser de terreno). La varita
  de selección no se puede volver pincel (lo dice).
- CA2: El ítem marcado dice qué hace (nombre si no tenía uno propio, y una descripción); `/wc brush none` y
  `/wc tool none` (también `/wc brush off`) le quitan la marca y lo que se le agregó; el ítem queda como era.
- CA3: Un pincel actúa con el **clic derecho** donde mira el jugador (hasta `tools.range`, de fábrica 200); cada clic es
  una obra del motor: límites del grupo, máscara global, deshacer con `/wc undo`, en el historial. Mientras el jugador
  tiene una obra en marcha, los clics se ignoran (como el pincel de terreno).
- CA4: Mientras se tiene un pincel con radio en la mano, un círculo de partículas donde caería, que ve solo ese jugador.
- CA5: Opciones comunes de los pinceles de bloques: `mask=<máscara>` (solo cambia los bloques que pasan) y `radius=`;
  `/wc brush mask=stone` o `/wc brush radius=5` cambian solo eso en el pincel de la mano. El radio va de 0 a
  `tools.brush_max_radius` (de fábrica 16).
- CA6: Con un pincel o herramienta nunca se rompe un bloque ni se pone el bloque de la mano.

**H2 — Pinceles de formas.**
- CA1: `/wc brush sphere <patrón> [radio=2] [--hollow]` (alias `-h`): una esfera centrada en el bloque que se mira.
- CA2: `/wc brush cyl <patrón> [radio=2] [alto=1] [--hollow]`: un cilindro que sube desde el bloque que se mira.
- CA3: `/wc brush set <cube|sphere|cyl> <radio> <patrón>`: la forma que se elija.
- CA4: `/wc brush clipboard [--air] [--origin]`: pega el portapapeles **actual** del jugador centrado en el bloque que se
  mira (con `--origin`, donde estaba el jugador al copiar queda en el bloque que se mira). Sin `--air` no pega el aire
  (como `/wc paste`). Si gira el portapapeles, el pincel pega girado.

**H3 — Pinceles de detalle.**
- CA1: `/wc brush splatter <patrón> [radio=2] [decay=1]`: cambia los bloques de la **superficie** (sólidos con aire
  encima) dentro de la esfera, cada vez menos hacia el borde (`decay` de 1, parejo, a 10, solo el centro).
- CA2: `/wc brush scatter <patrón> [radio=5] [puntos=5] [distancia=1] [--overlay]` (FAWE): `puntos` bloques al azar en la
  superficie del círculo, separados al menos `distancia`; con `--overlay`, encima de la superficie en vez de reemplazarla.
- CA3: `/wc brush rock <patrón> [radio=10] [sphericity=100] [frequency=30] [amplitude=50]` (alias `blob`, FAWE): una roca
  de borde irregular; `sphericity` 100 = redonda, menos = estirada al azar; `frequency` lo menudo del borde y
  `amplitude` cuánto se sale.

**H4 — Pinceles que arreglan.**
- CA1: `/wc brush gravity [radio=5] [--height]` (alias `-h`): en cada columna del círculo, de `y − radio` a `y + radio`
  (o hasta el techo del mundo con `--height`), los bloques caen hasta apoyarse (como WorldEdit). Los bloques con datos
  (cofres, carteles) no se mueven y los demás se apoyan sobre ellos.
- CA2: `/wc brush extinguish [radio=5]` (alias `ex`): apaga el fuego de la esfera.
- CA3: `/wc brush butcher [radio=5] [--pets …]`: las mismas opciones y reglas que `/wc butcher` (031) en la esfera donde
  se mira; se deshace. Necesita también `worldcraft.butcher` (al ponerlo y al usarlo).

**H5 — Herramientas.**
- CA1: `/wc tool repl <patrón>`: clic derecho pone el patrón en el bloque que se mira; clic izquierdo toma el bloque que
  se mira como patrón nuevo (con su estado: la escalera conserva su giro).
- CA2: `/wc tool floodfill <patrón> [alcance=5]`: clic derecho cambia por el patrón el bloque que se mira y todos los del
  mismo tipo **conectados** a él, hasta `alcance` bloques de distancia (tope `tools.max_range`, de fábrica 64).
- CA3: `/wc tool info`: cualquier clic dice del bloque que se mira la posición, el estado completo, el bioma y la luz
  (sin cambiar nada).
- CA4: `/wc tool cycler`: clic derecho pasa al siguiente valor de una propiedad del bloque que se mira (agachado, al
  anterior); clic izquierdo elige la propiedad siguiente (lo dice en la barra de acción). Cada cambio es una obra de un
  bloque que se deshace.
- CA5: `/wc tool deltree`: clic derecho en un árbol **flotante** (troncos y hojas que no tocan el suelo) lo quita entero;
  si toca el suelo, lo dice y no toca nada. Hasta `tools.deltree_range` bloques (de fábrica 50).
- CA6: `/wc tool farwand`: clic izquierdo y derecho marcan la esquina 1 y 2 (o los puntos de la forma, 030) en el bloque
  que se mira, de lejos.
- CA7: `/wc tool lrbuild <patrón izquierdo> <patrón derecho>`: cada clic pone su patrón pegado a la cara del bloque que se
  mira, de lejos; si el patrón es aire, quita el bloque que se mira.
- CA8: `/wc tool navwand`: clic izquierdo = `jumpto` (al bloque que se mira), clic derecho = `thru` (atraviesa la pared).
  Necesita también `worldcraft.navigate`.

**H6 — Superpico.** Como *constructor*, quiero romper bloques al instante con cualquier pico.
- CA1: `/wc superpickaxe single|area <radio>|recursive <radio>` (alias `/wc sp`; `/wc sp` solo lo prende o apaga con el
  último modo, como `//` de WorldEdit; `/wc sp off` lo apaga). Mientras está prendido, el clic izquierdo con un pico en un
  bloque lo rompe al instante: `single` ese bloque; `area` los del mismo tipo en el cubo de ese radio; `recursive` los
  del mismo tipo conectados, hasta ese radio. Radio hasta `tools.superpickaxe_max_radius` (de fábrica 5).
- CA2: No suelta ítems (decisión del dueño): cada clic es una obra que se deshace sin duplicar nada. Nunca rompe los
  bloques de `tools.never_break` (de fábrica la roca madre, la barrera, los portales y los bloques de comandos).
- CA3: Permiso `worldcraft.superpickaxe` (de fábrica OP). Se apaga al desconectarse.

**H7 — Seguridad, límites y ayuda.**
- CA1: Todo lo que pone un pincel o una herramienta pasa por los límites del grupo (`max_blocks`, `blocked_blocks`,
  mundos, obras a la vez) al **usarlo**, no solo al ponerlo: un ítem marcado que pasa a otro jugador no le da más de lo
  que su grupo permite, y sin `worldcraft.use` no hace nada.
- CA2: Errores con «did you mean» (tipo de pincel o herramienta, patrón, opción), autocompletado de tipos, opciones y
  bloques, y `/wc help brush` y `/wc help tool` con ejemplos.
- CA3: `/wc validate` revisa la sección `tools` (rangos, ítems, la máscara de `never_break`).

**H8 — Documentación.** README de Worldcraft (pinceles, herramientas, superpico, «Coming from WorldEdit»), README raíz,
tabla de paridad y roadmap.

## Fuera de alcance (al [roadmap](../../roadmap.md))
`/wc tool tree` y `brush forest` (033, con los árboles por el motor); entidades en `brush clipboard` (`-e` de WorldEdit);
`/tool stacker` y `selwand` (la varita ya existe: `/wc wand`); los pinceles de FAWE de líneas y curvas (`line`, `spline`,
`catenary`, `sweep`), `copypaste`, `populateschematic`, `shatter`, `pull`, `savebrush`/`listbrush`; `/brush apply|paint`
con `item`; `/brush command` (no, como en la 029: escalada de permisos); `/tracemask`; pinceles en la vista previa.

## Decisiones (CLARIFY)
| # | Pregunta | Propuesta de la IA | Respuesta del dueño (2026-11-02) |
|---|---|---|---|
| 1 | ¿Qué entra? | Todo menos `tool tree` (a la 033) | **Todo menos árbol** |
| 2 | ¿Qué ítem se vuelve pincel? | El de la mano, solo ese; con la mano vacía da uno | **El de la mano, solo ese** |
| 3 | Permisos | `worldcraft.use`; el superpico con `worldcraft.superpickaxe` | **use + superpico aparte** |
| 4 | ¿El superpico suelta ítems? | No (deshacer no duplica) | **No suelta** |

Decisiones de la IA: `brush clipboard` usa el portapapeles **del momento del clic** (WorldEdit guarda una copia al
ponerlo; así girar el portapapeles gira el pincel); `--air` como en `/wc paste` (WorldEdit usa `-a` al revés: «sin aire»).
`brush butcher` y `navwand` piden además el permiso de su comando (031 y 028).

## Preguntas que se le hicieron al dueño
1. ¿Qué entra? (todo menos árbol · todo, árbol incluido · solo lo más usado)
2. ¿Qué ítem se vuelve el pincel? (el de la mano, solo ese · siempre uno nuevo · como WorldEdit, todos los de ese tipo)
3. Permisos (use + superpico aparte · uno por cada cosa · todo con `worldcraft.use`)
4. ¿El superpico suelta los bloques? (no · sí, como WorldEdit)

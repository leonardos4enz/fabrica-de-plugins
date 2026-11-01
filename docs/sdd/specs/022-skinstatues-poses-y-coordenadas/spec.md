# Spec 022 — SkinStatues: más poses, poses propias fáciles y coordenadas en el comando

**Estado:** Implementado y verificado (compuertas 1, 2 y 3a en Paper 1.21.4 y 26.1.2) · aprobada por el dueño (2026-11-01) · **Plugin:** SkinStatues (0.1.0 → 0.2.0) · **Depende de:** [021](../021-skinstatues-nucleo/spec.md) ·
**Origen:** pedido del dueño (2026-11-01): «me gustarían más poses, y que el usuario pueda hacer sus propias poses
desde la config de una manera fácil, ¿se podría? También que en el comando pueda ponerlo por coordenadas; si no se
ponen, como está actualmente, que es 2 arriba. Agrega más poses también, y haz una lluvia de ideas de cosas que
podrían estar cool de agregar».

## Problema
La 0.1.0 trae 6 poses. Hacer una propia obliga a escribir todas las partes desde cero aunque solo se quiera cambiar un
brazo, y la misma pose con la otra mano es otra pose escrita a mano con los signos invertidos. Además, la estatua solo
se puede construir donde está el jugador (2 bloques arriba, mirando hacia donde mira): para ponerla en un lugar exacto
hay que pararse ahí y mirar con cuidado, y desde la consola (o un script) no se puede construir.

Una actualización de 0.1.0 a 0.2.0 tampoco recibiría poses nuevas: `poses.yml` solo se copia si no existe.

## Historias

**H1 — Más poses listas para usar.** Como *administrador*, quiero muchas poses que funcionen tal cual.
- CA1: El plugin trae unas **20 poses nuevas** además de las 6 de la 0.1.0 (p. ej. `running`, `saluting`, `thinking`,
  `arms_crossed`, `hands_on_hips`, `kneeling`, `flexing`, `dabbing`, `charging`, `bowing`, `t_pose`, `zombie`,
  `sitting_floor`, `lying`, `praying`, `crouching`, `fighting`, `archer`, `relaxing`, `facepalm`, `waving_left`,
  `pointing_left`), cada una revisada a ojo en la vista previa sin servidor (frente, costado, espalda).
- CA2: Las poses que trae el plugin (**incorporadas**) se leen del jar, no de `poses.yml`: un servidor que actualiza
  desde la 0.1.0 las recibe sin tocar sus archivos, y las que se agreguen en versiones futuras también.
- CA3: Una pose de `poses.yml` con el mismo nombre que una incorporada la **reemplaza** (el dueño puede ajustar
  cualquiera); el `poses.yml` de la 0.1.0 (con las 6 de entonces) sigue cargando sin errores (constitución IV.5).
- CA4: El plugin escribe `plugins/SkinStatues/builtin-poses.yml`, una copia de referencia de las incorporadas con
  sus números, que se reescribe al arrancar si cambió y dice en su cabecera que no se edita: para cambiar una, se
  copia a `poses.yml`.
- CA5: Toda pose incorporada pasa las pruebas de la 0.1.0: cáscara cerrada en cada segmento, nada por debajo de los
  pies y el codo o la rodilla sin grietas.
- CA6: El README muestra una imagen con todas las poses (de la vista previa, sin servidor) y la lista de nombres.

**H2 — Poses propias de manera fácil (con números).** Como *administrador*, quiero hacer mis poses en `poses.yml`
sin escribir todo desde cero ni pensar en signos al voltearlas.
- CA1: `extends: <pose>` parte de otra pose (incorporada o de `poses.yml`, declarada antes o después) y solo cambia
  lo que se escribe, **ángulo por ángulo**: `right_arm: { elbow: 90 }` deja el `pitch` y el `roll` heredados de ese
  brazo.
- CA2: `mirror: true` (con `extends`) voltea la pose heredada de izquierda a derecha: brazo y pierna derechos pasan
  a izquierdos y al revés, y los giros hacia un lado (`yaw`; `roll` de cabeza, cuerpo y capa) cambian de signo. Los
  ángulos que la pose escribe se aplican después del espejo, tal cual.
- CA3: Una herencia que no se puede resolver se informa al cargar con archivo, ruta y causa, y esa pose no carga
  (las demás sí): pose padre que no existe (con «did you mean»), padre con errores, ciclo (`a → b → a`), más de 8
  niveles, `mirror` sin `extends` o que no es `true`/`false`.
- CA4: `poses.yml` nuevo trae una **guía** en sus comentarios (qué hace cada ángulo con un ejemplo, de dónde salen
  las poses incorporadas, cómo usar `extends` y `mirror`) y dos poses propias de ejemplo que la usan; el README tiene la
  misma guía con una tabla de ángulos.
- CA5: `/statue poses` distingue las propias de las incorporadas (y marca las incorporadas reemplazadas); el
  autocompletado de `build` ofrece todas.

**H3 — Construir en unas coordenadas.** Como *administrador*, quiero decir dónde va la estatua y hacia dónde mira.
- CA1: `/statue build <name> <height> [pose] --at <x> <y> <z>` pone **los pies** de la estatua en ese bloque
  (centrada en x y z, la capa más baja de bloques en `y`), sin el `offset_up`; los números pueden ser absolutos o
  relativos a quien la pide con `~` (`~`, `~5`, `~-3`), como en Minecraft.
- CA2: `--facing north|south|east|west` dice hacia dónde mira la estatua; sin él, mira hacia donde mira el jugador (y
  al sur desde la consola).
- CA3: Sin `--at`, todo sigue como en la 0.1.0: 2 bloques arriba del jugador, centrada sobre él.
- CA4: Desde la **consola** se puede construir con `--at` (sin `~`, que no tiene posición) y `--world <name>` (sin
  él, el mundo principal); los mensajes van a la consola y `undo` de la consola quita la última estatua de la consola.
  Un jugador también puede usar `--world` con coordenadas.
- CA5: Se rechaza con un mensaje claro: coordenadas que no son números, faltan o son `~` desde la consola; un mundo
  que no existe («did you mean»); una dirección que no es una de las cuatro; un lugar fuera del borde del mundo; y,
  como antes, una estatua que no cabe entre el suelo y el techo del mundo ahí.
- CA6: Lejos de todo jugador la estatua se construye igual y sin trabar el servidor: los chunks que ocupa se cargan
  antes de empezar sin bloquear el hilo principal y se mantienen cargados hasta terminar de poner (o de quitar) los
  bloques.
- CA7: El autocompletado sugiere `--at`, `--facing` y `--world`; tras `--at`, `~` o la coordenada del bloque que mira
  el jugador; tras `--facing`, las cuatro direcciones; tras `--world`, los mundos cargados.

**H4 — Lluvia de ideas para lo que sigue.** Como *dueño*, quiero una lista de ideas para elegir las próximas specs.
- CA1: El roadmap tiene la lista de ideas de SkinStatues con una línea de qué es y su costo aproximado (bajo, medio,
  alto), sin implementar ninguna en esta spec.

## Fuera de alcance (y por qué)
- Copiar la pose de un soporte de armadura, palabras en vez de grados (`up`, `forward`…) y editar poses por comando:
  el dueño prefiere los números (CLARIFY 1); quedan en la lluvia de ideas.
- Todo lo de la lluvia de ideas (vista previa, pedestal, nombre flotante, filas de estatuas…): al roadmap (CLARIFY 4).
- Giros en diagonal: `--facing` acepta solo las cuatro direcciones, como la 0.1.0.

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Cómo se hacen poses propias «de manera fácil»? | Con números («está bien tocar números»): guía y plantilla en `poses.yml` y el README, más `extends` y `mirror`. Sin copiar de un soporte de armadura ni palabras |
| 2 | ¿Cuántas poses nuevas? | Unas 20: quedaron 22 nuevas (28 en total) |
| 3 | ¿Cómo van las coordenadas? | `--at x y z` (con `~`) y `--facing`; sin ellas, como hoy. Desde la consola con `--at` y `--world` |
| 4 | ¿Algo de la lluvia de ideas en esta spec? | No: todo al roadmap |
| 5 | ¿Cómo reciben las poses nuevas los servidores que ya tienen `poses.yml`? | Incorporadas desde el jar + `builtin-poses.yml` de referencia; `poses.yml` agrega o reemplaza (H1 CA2–CA4) |

## Preguntas abiertas
- (ninguna: aprobada el 2026-11-01)

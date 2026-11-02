# Spec 030 — Worldcraft: selecciones que no son cajas

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a, 22/22 dos veces) (ver [verify](verify.md)) · CLARIFY respondido por el dueño (2026-11-02: las 6 de WorldEdit **y** la mágica de
FAWE; nombres de WorldEdit; clics como WorldEdit; `walls`/`faces` = la cáscara) · **Plugin:** Worldcraft (`/wc`, hoy 0.5.0 → 0.6.0) ·
**Depende de:** [025](../025-worldcraft-nucleo/spec.md) (motor por tandas, vista previa, deshacer, límites, varita y
contorno), [028](../028-worldcraft-paridad-worldedit/spec.md) (`stack`, `move`, `hollow`, `overlay`, `smooth`…),
[029](../029-worldcraft-paridad-worldedit-2/spec.md) (selección e información, máscaras con vecinos, partición 030–036) ·
**Origen:** la partición aceptada por el dueño en la 029 (030 = selecciones que no son cajas).
**Investigación:** [paridad con WorldEdit y FAWE](../../../investigacion/paridad-worldedit-fawe.md) §2.1 y §4.2.

## Problema
- En Worldcraft la selección es siempre una **caja** (`Session.box()`): no se puede llenar un cilindro, copiar una
  cúpula, contar los bloques de una isla redonda ni hacer las paredes de una torre de base irregular. WorldEdit tiene
  7 selectores (`cuboid`, `extend`, `poly`, `ellipsoid`, `sphere`, `cyl`, `convex`) y FAWE suma la «mágica» (`fuzzy`/`magic`:
  los bloques conectados del mismo tipo), `polyhedral`/`hull` (= `convex`) y `list`.
- Casi todos los comandos de región (`set`, `replace`, `walls`, `faces`, `copy`, `cut`, `count`, `distr`, `trim`, `stack`,
  `move`, `hollow`, `overlay`, `naturalize`, `smooth`…) reciben un `Box`; el recorrido por chunks (`ChunkOrder`) y el
  contorno de la varita (`Wand`) también asumen caja.

## Costo estimado (lección 004)
| Pieza | Costo |
|---|---|
| Abstracción de región (caja, columnas, puntos) + recorrido por chunks de cualquier forma | M− |
| `poly`, `ellipsoid`, `sphere`, `cyl`, `extend` (columnas con un intervalo; reusan las fórmulas de la 028) | B |
| `convex` (envolvente convexa 3D propia, sin degenerar con puntos en un plano) | B+ |
| `magic` (leer alrededor del clic e inundar en el hilo de cálculo; unión con otro clic) | M− (~30 %) |
| Contorno con partículas de cada forma | B+ |
| Los comandos de región sobre cualquier forma | M− |

Total: una spec normal larga. Se le propuso al dueño dejar la mágica para después; eligió hacerla en esta.

## Historias

**H1 — Elegir la forma.** Como *constructor*, quiero elegir con qué forma selecciono.
- CA1: `/wc sel cuboid|extend|poly|ellipsoid|sphere|cyl|convex|magic` cambia la forma (los nombres de WorldEdit; `box` es
  otro nombre de `cuboid`, `fuzzy` de `magic` y `hull`/`polyhedral` de `convex`, como en FAWE) y borra los puntos que había;
  dice cómo se marca esa forma con la varita.
- CA2: `/wc sel` sin nada dice la forma, sus puntos (centro y radios, vértices…), la caja que la abarca y cuántos
  bloques tiene **de verdad** (no los de la caja). `/wc sel clear|show|hide` como hoy.
- CA3: La forma se recuerda mientras el jugador está conectado (como la selección de hoy). `/wc sel` de fábrica = `cuboid`.

**H2 — Marcar con la varita (como WorldEdit).** Clic izquierdo = el primer punto o el centro (empieza de nuevo); clic
derecho = agrega un punto o agranda hasta donde se hizo clic. `/wc pos1`, `pos2`, `hpos1` y `hpos2` hacen lo mismo que
los clics. Cada clic dice qué quedó.
- CA1 `cuboid`: como hoy (esquina 1 y 2).
- CA2 `extend`: izquierdo = una caja de un bloque; derecho = la caja crece hasta abarcar ese bloque.
- CA3 `poly`: izquierdo = primer vértice; derecho = otro vértice. El alto va del bloque más bajo al más alto marcado.
- CA4 `ellipsoid`: izquierdo = centro; derecho = cada radio crece hasta llegar al bloque (por eje).
- CA5 `sphere`: izquierdo = centro; derecho = el radio es la distancia al bloque (redondeada hacia arriba).
- CA6 `cyl`: izquierdo = centro (alto de un bloque); derecho = los radios en x y z crecen hasta el bloque y el alto
  crece hasta incluirlo.
- CA7 `convex`: izquierdo = primer vértice; derecho = otro vértice; la forma es la envolvente convexa de los vértices
  (un punto = un bloque; puntos en una recta o en un plano = una línea o una lámina de un bloque de grueso).
- CA8 `magic`: izquierdo = los bloques conectados (por las caras) del mismo tipo que el clicado, hasta
  `selection.magic_radius` bloques de distancia; derecho = suma los conectados a otro bloque. Lee el mundo por tandas
  (sin trabar) y respeta el tope de bloques del grupo.
- CA9: Más de `selection.max_points` vértices (`poly`, `convex`) se rechaza con un mensaje claro.

**H3 — Ver la forma.** El contorno con partículas (solo para quien selecciona, con la varita en la mano, con el tope de
`selection.max_particles`) dibuja cada forma: `poly` el polígono abajo y arriba y las aristas verticales; `ellipsoid` y
`sphere` los tres círculos por el centro; `cyl` los círculos de abajo y arriba y cuatro líneas; `convex` las aristas de la
envolvente; `magic` el borde de los bloques elegidos.

**H4 — Los comandos sobre cualquier forma.**
- CA1: `set`, `replace`, `copy`, `cut`, `count`, `distr`, `trim`, `hollow`, `overlay`, `naturalize`, `smooth` y `gmask`
  tocan **solo** los bloques de la forma. El tope de bloques y la confirmación usan los bloques de la forma.
- CA2: `walls` = la cáscara de los costados (los bloques de la forma que tocan el afuera por un costado); `faces` = toda
  la cáscara (también arriba y abajo). En una caja es lo mismo que hoy (decisión del dueño).
- CA3: `copy` de una forma guarda solo sus bloques: al pegar, lo de afuera no se pega (ni como aire con `--air`).
- CA4: `stack` repite la forma (separada por el tamaño de la caja que la abarca, como WorldEdit) y `move` la mueve: lo
  que queda atrás es solo lo de la forma que no cubre la forma movida.
- CA5: `shift` mueve cualquier forma. `expand`/`contract` hacia arriba o abajo y `expand vert` cambian el alto de `poly` y
  `cyl`; `outset`/`inset` cambian los radios de `sphere`, `ellipsoid` y `cyl`. Lo demás (y `trim`, `chunk`) deja una caja
  (`cuboid`), o dice con claridad que esa forma no se puede agrandar así.
- CA6: `line` necesita dos esquinas (`cuboid` o `extend`); `center` usa el centro de la caja que abarca la forma.

**H5 — Documentación.** README de Worldcraft (tabla de formas y clics, «Coming from WorldEdit»), README raíz, tabla de
paridad (§2.1) y roadmap.

## Fuera de alcance (al [roadmap](../../roadmap.md))
FAWE `//sel list` (puntos sueltos) y `//sel -d` (forma de fábrica guardada); `//curve` sobre `convex`; `//wer`;
`expand`/`contract` de lado en formas que no son caja; seleccionar la forma de una edición ya hecha. La API para otros
plugins sigue al final de la partición.

## Decisiones (CLARIFY)
| # | Pregunta | Propuesta de la IA | Respuesta del dueño (2026-11-02) |
|---|---|---|---|
| 1 | ¿Qué entra? | Las 6 de WorldEdit; la mágica y la lista de FAWE después | **Las 6 + la mágica** |
| 2 | Nombres | Como WorldEdit (`/wc sel poly…`, `box` = `cuboid`) | Como WorldEdit |
| 3 | Clics de la varita | Como WorldEdit (izquierdo empieza, derecho agrega o agranda) | Como WorldEdit |
| 4 | `walls`/`faces` en otras formas | La cáscara (de costado / toda) | La cáscara |

## Preguntas que se le hicieron al dueño
1. ¿Qué entra en la 030? (las 6 de WorldEdit · las 6 + la mágica · solo las 4 fáciles)
2. ¿Cómo se llaman los comandos? (como WorldEdit · nombres más claros con alias)
3. ¿Cómo marca los puntos la varita? (como WorldEdit · siempre agregar)
4. ¿Qué hacen `walls` y `faces` en un cilindro? (la cáscara · solo en cajas)

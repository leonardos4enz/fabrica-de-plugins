# Plan 030 — Selecciones que no son cajas en Worldcraft (0.6.0)

Spec: [spec.md](spec.md) · Respuestas del dueño: las 6 de WorldEdit y la mágica de FAWE, nombres y clics de WorldEdit,
`walls`/`faces` = la cáscara. Todo sigue pasando por el motor de la 025 (`Edits.request`, `Edits.read`, `EditSource`), sin NMS.

## 1. Diseño

### 1.1 La región (Java puro, paquete `region`)
Hoy todo recibe un `Box`. Se agrega una interfaz `Region` y la caja pasa a ser una de sus formas:

```
interface Region
  Box bounds()                        la caja que la abarca
  boolean contains(x, y, z)
  long volume()                       los bloques de verdad (no los de la caja)
  long count(Shape)                   cuántos visita FILL / WALLS / FACES
  int[] ys(x, z, Shape)               las y de esa columna que visita la forma (para el recorrido por chunks)
  int[] span(x, z)                    [y mín, y máx] de la columna o null (naturalize, overlay, smooth)
  Region clampY(min, max)             dentro del alto del mundo, o null
```

Tres familias:
| Familia | Formas | Cómo |
|---|---|---|
| `Box` | `cuboid`, `extend` | Como hoy (el `record` implementa `Region`; el recorrido rápido de la 025 queda igual) |
| `Columns` (abstracta) | `poly`, `ellipsoid`, `sphere`, `cyl`, `convex` | Cada columna (x, z) es **un solo intervalo** de y. Las subclases calculan el intervalo; `Columns` lo guarda en una tabla (una vez, perezosa) y de ahí salen `contains`, `volume`, el recorrido y la cáscara. `contains` se define por la columna: el recorrido, el conteo y el contorno nunca discrepan |
| `PointSet` | `magic` | Posiciones empaquetadas ordenadas (`long[]`), `contains` por búsqueda binaria |

- **Cáscara (decisión del dueño).** `walls` = los bloques de la forma con algún vecino **de costado** (4) fuera; `faces` =
  con algún vecino de los 6 fuera. En una caja da exactamente lo de hoy (paredes de los bordes; más piso y techo). En
  `Columns` es aritmética de intervalos: interior(c) = intervalo(c) ∩ intervalos de los 4 vecinos (y además sin `lo` ni
  `hi` para `faces`); la cáscara es el intervalo menos el interior (a lo más dos tramos). En `PointSet`, mirando los vecinos.
- **Fórmulas** (las de la 028, para que una esfera seleccionada sea igual a la de `/wc sphere`):
  - `ellipsoid`/`sphere` de radios (a, b, c): la columna (dx, dz) ocupa `|dy| ≤ ⌊(b+½)·√(1 − (dx/(a+½))² − (dz/(c+½))²)⌋`.
  - `cyl` de radios (a, c) entre y₁ e y₂: las columnas con `(dx/(a+½))² + (dz/(c+½))² ≤ 1`.
  - `poly`: un punto (x, z) está dentro si el rayo lo deja dentro (regla par-impar, con enteros exactos) **o** está a medio
    bloque de una arista (`2·|cruz| ≤ max(|dx|, |dz|)` dentro de la caja de la arista: las aristas quedan como líneas
    continuas, también con 2 vértices). Alto: del vértice más bajo al más alto.
  - `convex` (§1.2).
- **Recorrido por chunks.** `ChunkOrder(Region, Shape)`: para la caja, el camino de hoy; para las demás, por columna
  `region.ys(x, z, shape)`, y las columnas vacías se saltan. `RegionSource`, `Edits.read`, `Census` y `copy` reciben una
  `Region` (una `Box` sigue sirviendo donde se pasaba).

### 1.2 La envolvente convexa (`region.Hull`)
- Cada vértice marcado se infla al **cubo de su bloque** (sus 8 esquinas a ±½): la envolvente de esos cubos nunca es
  plana ni una recta, así que el algoritmo no tiene casos degenerados, y un vértice siempre queda dentro. Un bloque está
  dentro si su **centro** está en la envolvente (con el borde): un punto = un bloque; dos = una línea; puntos en un plano =
  una lámina de un bloque.
- Coordenadas al doble y relativas al primer vértice: todo es **entero** (`long`), sin errores de redondeo. Ancho máximo
  60 000 bloques (los productos caben en un `long`).
- Algoritmo incremental: un tetraedro inicial; cada punto que ve caras las quita y une el horizonte con él. Los planos
  se normalizan (MCD) y se quitan los repetidos. La columna (x, z) es la intersección de los semiespacios con la recta
  vertical: `n_y > 0` da un techo, `n_y < 0` un piso, `n_y = 0` dentro o fuera.
- Tope de vértices: `selection.max_points` (también para `poly`).

### 1.3 El selector (`region.Selector`, Java puro)
La sesión guarda un `Selector` en lugar de dos esquinas. `primary(x, y, z)` (clic izquierdo, `pos1`, `hpos1`) y
`secondary(x, y, z)` (clic derecho, `pos2`, `hpos2`) según la forma (spec H2); `region()` da la `Region` o null si falta
un punto; `describe()` el texto de `/wc sel`; `shift`, `expandY`, `outset` según la forma (H4 CA5); `select(Box)` vuelve
a `cuboid` (lo usan `chunk`, `trim`, `paste --select`). `corner1()`/`corner2()` siguen para `line` y el origen de la
consola. `magic`: el selector guarda el clic y el `PointSet` que le da el comando.

### 1.4 La mágica (`magic`)
`primary`/`secondary` en `magic` → `SelectionCommands.magic`: lee la caja de `selection.magic_radius` alrededor del bloque
(dentro del alto del mundo, con el tope de lectura del grupo), inunda en el **hilo de cálculo** por las 6 caras los bloques
del mismo material (`region.Magic.flood`, sobre la foto) y vuelve al hilo principal; el clic derecho une con lo que ya
había (tope: `max_blocks` del grupo).

### 1.5 Los comandos
| Comando | Cambio |
|---|---|
| `set`, `replace`, `walls`, `faces` | `RegionSource(region, shape…)`; volumen = `region.count(shape)` |
| `count`, `distr`, `trim` | Lectura de solo la forma; con vecinos, la foto de la caja + 1 y `Census.over(snap, region)`. `trim` deja una caja |
| `copy`, `cut` | Se leen solo los bloques de la forma; el resto queda «sin leer» (−1) en el portapapeles, que `paste` ya salta (también con `--air`) |
| `stack`, `move` | La foto de la forma (−1 fuera). `stack` ya salta el −1; `move` deja atrás solo lo de la forma que la forma movida no cubre. `--select` mueve la forma (no la vuelve caja) |
| `hollow` | Lo calculado se filtra por la forma |
| `overlay`, `naturalize`, `smooth` | Solo las columnas de la forma (`span`) |
| `center` | El centro de la caja que abarca la forma |
| `line` | Necesita `cuboid` o `extend` |
| `shift` / `expand` `contract` arriba-abajo y `vert` / `outset` `inset` | Por forma (spec H4 CA5); lo que no aplica lo dice |
| `sel` | `sel <forma>` y la información de la forma |
| Varita y contorno | Los clics van al selector; `Wand.draw` dibuja `region.Outline` (segmentos y puntos, Java puro) con el tope de partículas |

## 2. Contrato de configuración
Claves nuevas en `selection` (con valor de fábrica para quien actualiza; `/wc validate` las revisa):
```yaml
selection:
  max_points: 64          # vértices de poly y convex (1..256)
  magic_radius: 16        # cuánto busca la selección mágica alrededor del clic (1..64); se lee (2r+1)³ como mucho
```
Mensajes nuevos en `messages.yml` (de fábrica para quien actualiza). Sin permisos nuevos: elegir la forma es parte de
`worldcraft.use`.

## 3. STRIDE
| Amenaza | Dónde | Mitigación |
|---|---|---|
| **D**enegación: una forma enorme o con miles de vértices | `poly`, `convex` | `selection.max_points`; ancho de la envolvente ≤ 60 000; la tabla de columnas solo se arma con ≤ 16 millones de columnas; las obras siguen con el tope de bloques del grupo, contado con los bloques de la forma |
| **D**enegación: la mágica lee e inunda mucho | `magic` | `magic_radius` ≤ 64; tope de lectura del grupo antes de leer; lectura por tandas; la inundación en el hilo de cálculo; la unión con el tope de bloques del grupo |
| **D**enegación: contar la cáscara en el hilo principal | `walls`, `faces` | En `Columns`, O(columnas) con la tabla; nunca O(bloques) salvo en `magic` (acotada) |
| **T**ampering: pisar fuera de la forma | `paste`, `stack`, `move` | Lo de afuera queda −1 en la foto y en el portapapeles: nunca se escribe |
| **E**levación | — | Nada nuevo: las obras pasan por `Edits.request` (límites, mundos y bloques prohibidos) |
| **R**epudio | — | El historial anota cada obra como hoy (el tipo y el detalle) |

## 4. Pruebas
- Unitarias: `ShapesTest` (cada forma: contiene, volumen, cáscara de costado y completa contra la cuenta a mano y contra
  la fuerza bruta; caja = lo de hoy; `ChunkOrder` sobre cada forma visita exactamente lo de `contains`), `HullTest`
  (cubo, tetraedro, un punto, una recta, puntos en un plano, coordenadas lejanas), `SelectorTest` (clics de cada forma,
  `shift`, `outset`, el alto, mensajes, tope de vértices), la mágica (inundación del mismo material, unión),
  `OutlineTest` (segmentos de cada forma), y en `ShapeOpsTest` las fuentes: `copy` de una esfera deja −1 afuera, `move` y `stack` de un
  cilindro, `Census` sobre una forma.
- Compuerta 2: desde la consola en `plugins/worldcraft/run/` (`pos1`/`pos2` con coordenadas en cada forma).
- Compuerta 3a: escenarios nuevos con el bot (varita en `cyl`, `poly`, `sphere`, `convex` y `magic`; `set`, `walls`, `copy`
  y `paste` sin las esquinas, `count`, `stack`/`move`, `undo`).

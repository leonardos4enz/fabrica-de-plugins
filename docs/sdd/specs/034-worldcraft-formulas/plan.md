# Plan 034 — Fórmulas (Worldcraft 0.10.0)

Spec: [spec.md](spec.md) · Respuestas del dueño: todo menos `query`; lenguaje propio como WorldEdit
([ADR 0006](../../../adr/0006-formulas-de-worldcraft.md)); patrón `=fórmula -> a, b, c`. Solo API de Paper 1.21.4, sin NMS.

## 1. Diseño

### 1.1 El lenguaje (Java puro, paquete `formula`)
- `Lexer`: números (`1`, `.5`, `1e-3`), nombres, operadores por el más largo (`**=`, `**`, `++`, `+=`, `==`, `!=`, `~=`,
  `<=`, `&&`, `<<`…), palabras `if else while do for break continue return`. Cada ficha guarda su posición (1 = primer
  carácter) para los errores.
- `FormulaParser`: descenso recursivo. Sentencias: `{…}`, `if`/`else` (acepta `;` antes de `else`), `while`,
  `do … while`, `for (a; b; c)`, `for (i = a, b)`, `break`, `continue`, `return [expr]` y expresiones; `;` entre
  sentencias (opcional tras un bloque). Precedencia de menor a mayor (la de WorldEdit): asignación (derecha) · `?:` ·
  `||` · `&&` · `== != ~=` · `< <= > >=` · `<< >>` · `+ -` · `* / %` · `^ **` (derecha) · prefijos `- + ! ~ ++ --` ·
  sufijos `++ -- !` · primarios (número, nombre, llamada, paréntesis). Así `-2^2 = 4` como en WorldEdit. Tope de
  anidamiento 128 (una fórmula no revienta la pila).
- Resolución en dos pasadas: se juntan los nombres asignados en todo el texto (`=`, `++`, `for`, `rotate`, `swap`); cada
  nombre que se lee es una entrada (`x`, `y`, `z`…), una constante (`pi`, `e`, `true`, `false`; no se pueden asignar) o
  una variable asignada en algún lugar; si no, error con «did you mean». Las funciones se revisan con su número de
  argumentos. `query*`, `gmegabuf`, `closest` y `switch` dan un error que dice que no están.
- `Node`: árbol inmutable de `double eval(Run)`; las variables son posiciones de `Run.slots`. `break`/`continue`/`return`
  con una bandera en `Run` (sin excepciones). Verdadero = `> 0`; comparaciones y lógicos dan 1 o 0.
- `Run` (una evaluación por hilo, no compartida): `slots` (entradas + variables, siguen entre bloques como en WorldEdit),
  `megabuf` (crece hasta `formulas.megabuf_size`; fuera de rango lee 0 y no escribe), `random` con semilla, contador de
  vueltas por evaluación y plazo (`deadline`). Pasar `max_steps` lanza `Formula.TooManySteps`; pasar el plazo,
  `Formula.TimedOut` (las dos sin traza, `Formula.Stop`). El plazo se mira al empezar cada evaluación y cada 1024
  vueltas.
- `Noise3`: Perlin 3D mejorado con permutación por semilla; `perlin` (fbm con persistencia), `ridgedmulti`,
  `voronoi` (valor de la celda más cercana).
- `Formula`: `parse(texto, entradas, límites)`, `text()`, `run(semilla)`, `local()` (un `Run` por hilo, para máscaras y
  patrones), `Limits(maxSteps, maxLength, megabuf)`.
- `Space` (cómo se ven las coordenadas): `NORMAL` (cero = centro de la caja, unidad = (máx − mín) / 2, 1 si es 0, como
  WorldEdit), `RAW`, `OFFSET` (desde un punto), `CENTER` (centro sin escalar). `toWorld` redondea (`floor(v + 0.5)`).
- `Shapes` (cálculo, en el hilo de fórmulas): `generate(región, fórmula, espacio, hueca, plazo)` → `long[]` de puntos
  (columna por columna con `span`; hueca: un `BitSet` sobre la caja y la cáscara con 6 vecinos, fuera de la región =
  fuera); `deform(…)` → pares destino/origen (solo los que cambian) y la caja de los orígenes; `cells(celdas, …)` → las
  celdas verdaderas en su centro (hueca: un vecino a ±4 fuera). Cada uno cuenta lo evaluado para el mensaje del tope.

### 1.2 Máscara y patrón
- `Mask.Near.Formula(Formula)`: cuenta como «de posición» pero no mira vecinos (sirve en `gmask`). `Mask.Compiled` la
  evalúa con `local()` y `x, y, z` del mundo; un `Formula.Stop` cuenta como falso.
- `Parser`: un término que empieza con `=` es una fórmula (`x`, `y`, `z`); `split` respeta los paréntesis
  (`=max(x,z)<5, stone`). En un patrón, `=fórmula -> a, b, c`: la lista (estados, sin pesos ni `^`) y el índice
  `floor(valor)` acotado. `Pattern` suma `pick` (la fórmula o null); los *presets* lo conservan.
- `Parser#formulaLimits(Formula.Limits)`: `LoadedConfig` le pasa los de `config.yml`.

### 1.3 Comandos (`command/FormulaCommands`) y el motor (`paper/Formulas`)
- `generate|gen [--hollow] [--raw|--offset|--center] [--preview] <patrón> <fórmula>`: el patrón es el primer argumento
  (como WorldEdit; con espacios, un *preset*); el resto es la fórmula. `-h -r -o -c` como alias. Se revisa el tope del
  grupo con el volumen de la selección (lo que se evalúa), se calcula en `Formulas` y se pide un `Op` con
  `PointsSource(puntos, patrón)`.
- `deform [--raw|--offset|--center] [--preview] <fórmula>`: cálculo → caja de los orígenes (∩ alto del mundo; su
  volumen y el de la selección contra el tope) → `Edits.read` de esa caja a una `Snapshot` → `Op` con `MapSource`
  (nuevo, en `edit`): recorre los destinos en orden de chunks y pone el estado y los datos de bloque (`tile`) del origen
  en la foto; lo que no se leyó (fuera del alto) es aire.
- `generatebiome|genbiome [--hollow] [--raw|--offset|--center] <bioma> <fórmula>`: `Cells.of(selección)`, cálculo de las
  celdas y la misma obra que `setbiome` (factorizada: `NatureCommands#biomeOp`), con `gmask`.
- `calc|calculate|eval <fórmula>`: sin entradas, en el hilo de fórmulas con el plazo; `= valor` (entero sin decimales,
  si no 10 cifras).
- `timeout [ms|default]`: `Session.formulaTimeout` (−1 = el de fábrica), de 1 a `max_timeout_ms`.
- `paper/Formulas`: el hilo `Worldcraft-Formula` (propio: una fórmula lenta no frena el terreno), `compute(quien,
  trabajo, luego)` que traduce `TimedOut`/`TooManySteps` a mensajes y cualquier otro error a `compute-error`; y
  `generate`, `deform`, `biomes` compartidos por los comandos y el pincel.
- `brush deform [radio] [fórmula] [--raw] [--offset]`: `ToolKind.DEFORM`; la fórmula va en el ítem en Base64 (el `;`
  separa las opciones); se revisa al crear el pincel. El clic: `Solid.sphere` en el bloque que se mira → `Formulas.deform`
  con `NORMAL` sobre la caja de la esfera (`OFFSET` desde el bloque mirado) → la máscara del pincel envuelve la fuente.

## 2. Contrato de configuración (`config.yml`)
```yaml
# Formulas (/wc generate, deform, generatebiome, calc, =formula masks and patterns, /wc brush deform).
formulas:
  timeout_ms: 2000        # how long one command's formula may calculate (each player: /wc timeout) (1..max_timeout_ms)
  max_timeout_ms: 10000   # the most /wc timeout can set (100..60000)
  max_steps: 100000       # loop rounds (while, for, do) in one evaluation (1000..10000000)
  max_length: 1000        # characters in one formula (32..10000)
  megabuf_size: 65536     # cells of megabuf() (0..10000000)
```
Un valor fuera de rango avisa y usa el de fábrica (constitución I.3); una clave desconocida avisa con «did you mean».
`timeout_ms` mayor que `max_timeout_ms` se baja al tope y avisa.

## 3. STRIDE
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| **D**enegación: una fórmula cara (`while(1){}`, `for(i=0,1e9)`) o una selección enorme | Trabar el servidor | El cálculo de `generate`/`deform`/`generatebiome`/`calc` corre en un hilo propio con plazo (`/wc timeout`, como mucho `max_timeout_ms`); cada evaluación se corta en `max_steps` vueltas; lo evaluado y lo leído cuentan para el tope de bloques del grupo; lo que se pone va por el motor (8 ms por tick) |
| **D**: máscara o patrón con fórmula en una obra grande | Ticks largos | Se evalúa dentro del presupuesto del tick (el reloj se mira en cada bloque, 025 F-01); cada evaluación acotada por `max_steps`; la obra se cancela con `/wc cancel` |
| **D**: `megabuf` enorme | Memoria | Crece hasta `megabuf_size` celdas (512 KB de fábrica); fuera de rango lee 0 |
| **D**: texto con miles de paréntesis | Desbordar la pila al leer | `max_length` y tope de anidamiento 128 |
| **E**levación: ejecutar código desde la fórmula | Acceso al servidor | El lenguaje solo tiene números y funciones matemáticas fijas: no hay reflexión, nombres de clases, archivos ni mundo (ADR 0006) |
| **T**ampering: deformar o generar fuera de lo permitido | Romper construcciones ajenas | Las mismas puertas que toda obra: mundos permitidos, tope de bloques, bloques prohibidos (con los estados que se van a poner, también los leídos por `deform`), `gmask`, historial y `undo` |
| **R**epudio | — | Cada obra queda en el historial con la fórmula en el detalle |
| **I**nformación | — | `calc` no lee nada del mundo |

## 4. Pruebas
- Unitarias: `FormulaTest` (precedencia, asignaciones, bucles, funciones, errores con posición y «did you mean», topes
  de vueltas, tiempo, anidamiento y `megabuf`), `ShapesTest` (esfera normalizada que llena la
  selección, `--raw/--offset/--center`, hueca, `deform` con orígenes fuera, celdas), `FormulaMaskTest` (máscara y
  patrón, `split` con paréntesis, presets), `MapSourceTest`, `ToolSpecTest` (deform con `;` en la fórmula),
  `ConfigTest` (sección `formulas`), `CommandTest` (ayuda y mensajes).
- Compuerta 2: `selftest` evalúa una fórmula, corta un bucle por vueltas y un cálculo por tiempo.
- Suite 3a: `generate` (la misma cuenta que `//g` de WorldEdit 7.3.19), hueca, `calc`, errores, `timeout`;
  máscara `=…` en `replace` y `gmask`, patrón de tablero; `deform` que sube un piso, `generatebiome` por mitades,
  `brush deform` y `undo`.

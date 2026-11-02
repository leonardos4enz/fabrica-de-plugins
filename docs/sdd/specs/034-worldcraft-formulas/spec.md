# Spec 034 — Worldcraft: fórmulas

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a, 36/36 dos veces) (ver [verify](verify.md)) · CLARIFY respondido por el dueño (2026-11-02: todo menos `query`; lenguaje propio como el de
WorldEdit; patrón `=fórmula -> a, b, c`) · **Plugin:** Worldcraft (`/wc`, hoy 0.9.0 → 0.10.0) · **Depende de:**
[025](../025-worldcraft-nucleo/spec.md) (motor, deshacer, límites), [029](../029-worldcraft-paridad-worldedit-2/spec.md)
(máscaras de posición, `gmask`), [030](../030-worldcraft-selecciones-no-cuboides/spec.md) (selecciones),
[032](../032-worldcraft-pinceles-y-herramientas/spec.md) (pinceles), [033](../033-worldcraft-biomas-arboles-estructuras/spec.md)
(biomas por celdas) · **Origen:** la partición aceptada por el dueño en la 029 (034 = fórmulas, marcada «A»).
**Investigación:** [paridad con WorldEdit y FAWE](../../../investigacion/paridad-worldedit-fawe.md) §2.2 (`//deform`),
§2.3 (`//generate`, `//generatebiome`), §2.6 (`/brush deform`), §2.8 (`//calculate`), §2.10 (`//timeout`), §2.11 y §2.12
(`=expresión`) y §4.2 (fila 034).

## Problema
- Los constructores de WorldEdit hacen formas que ningún comando trae (toros, olas, espirales, superficies matemáticas)
  con `//generate` y una fórmula, deforman lo construido con `//deform` y eligen bloques con `=y<64`. Worldcraft no
  tiene nada de eso: una fórmula de un tutorial de WorldEdit no se puede usar.
- En WorldEdit una fórmula cara (un bucle sin fin) se corta con `//timeout`, pero se evalúa en el hilo del servidor por
  cada bloque: un `//g` grande traba el servidor. En Worldcraft todo tiene que pasar por el motor de la 025 (8 ms por
  tick, vista previa, deshacer en disco, límites por grupo) y una fórmula no puede tumbar el servidor.

## Costo estimado (lección 004)
| Pieza | Costo |
|---|---|
| Lenguaje de fórmulas propio (números, operadores, funciones, bucles, ruido) con tope de pasos y de tiempo | ~25 % |
| `generate` (y su forma hueca, `-r -o -c`) | ~15 % |
| `deform` (lee de dónde vienen los bloques, también fuera de la selección) | ~15 % |
| `generatebiome` (por celdas de 4×4×4, con la etapa de biomas de la 033) | ~7 % |
| Máscara `=fórmula` y patrón `=fórmula -> a, b, c` | ~10 % |
| `calc` y `timeout` | ~3 % |
| `brush deform` | ~8 % |
| Pruebas en el juego y documentación | ~17 % |

Una spec larga (como la 032 o la 033). El dueño eligió todo menos `query()` (leer un bloque del mundo desde la fórmula:
en WorldEdit usa los números de bloque viejos, de antes de 1.13): va al roadmap.

## Historias

**H1 — El lenguaje.** Como *constructor*, quiero escribir la misma fórmula que en WorldEdit.
- CA1: Números (`3`, `0.5`, `.5`, `1e3`), variables, `+ - * / %`, potencia `^` (y `**`), comparaciones
  (`< <= > >= == !=`, `~=` casi igual), lógicos `&& || !`, `? :`, desplazamientos `<< >>` y `~`, factorial `n!`,
  asignaciones (`= += -= *= /= %= ^=`, `++`, `--`), varias sentencias con `;`, bloques `{ }`, `if`/`else`, `while`,
  `do … while`, `for (i = 0; i < 5; i++)`, `for (i = 1, 10)` (de 1 a 10), `break`, `continue`, `return`. Verdadero es
  mayor que 0 (como WorldEdit).
- CA2: Constantes `pi`, `e`, `true`, `false`. Funciones con los nombres de WorldEdit: `abs acos asin atan atan2 cbrt
  ceil cos cosh exp floor ln log log10 max min rint round sin sinh sqrt tan tanh`, `random()`, `randint(n)`,
  `perlin(semilla, x, y, z, frecuencia, octavas, persistencia)`, `ridgedmulti(semilla, x, y, z, frecuencia, octavas)`,
  `voronoi(semilla, x, y, z, frecuencia)`, `megabuf(i)` y `megabuf(i, valor)`, `rotate(x, y, ángulo)` y `swap(x, y)`; y
  `mod(a, b)` (el resto siempre positivo, cómodo con coordenadas negativas). El ruido no da los mismos números que
  WorldEdit (otro generador), pero se usa igual.
- CA3: Todo error se dice antes de tocar el mundo, con dónde está y «did you mean»: `Unknown function 'sinn' at 1 (did you
  mean 'sin'?)`, una variable que no existe, un paréntesis que falta, `query` no soportado.
- CA4: Una fórmula nunca traba el servidor: un bucle de una evaluación se corta en `formulas.max_steps` vueltas y el
  cálculo de un comando en el tiempo de `/wc timeout` (fuera del hilo del servidor). Lo que se corta lo dice.

**H2 — Forma desde una fórmula.** `/wc generate <patrón> <fórmula>` (alias `gen`).
- CA1: Pone el patrón en cada bloque de la selección (cualquier forma, 030) donde la fórmula es verdadera. Como
  WorldEdit, `x`, `y`, `z` van de −1 a 1 en la selección (0 en el centro): `/wc generate stone x^2+y^2+z^2<1` es la
  esfera que llena la selección. `--raw` (`-r`): las coordenadas del mundo; `--offset` (`-o`): desde los pies del
  jugador; `--center` (`-c`): desde el centro de la selección, sin escalar.
- CA2: `--hollow` (`-h`): solo la cáscara (los bloques de la forma con un vecino fuera de ella o fuera de la selección).
- CA3: La fórmula se calcula fuera del hilo del servidor (con el tope de H1 CA4) y después es una obra del motor:
  límites del grupo, máscara global, `--preview`, confirmación, tandas de 8 ms, historial y `undo`. Si no es verdadera
  en ningún lugar, lo dice y no hace nada.

**H3 — Deformar.** `/wc deform <fórmula>`.
- CA1: Como WorldEdit: para cada bloque de la selección la fórmula cambia `x`, `y`, `z` y el bloque toma lo que había en
  ese otro lugar (`y-=0.2` sube lo de abajo; `x*=2` lo estira). Las mismas coordenadas que `generate` (`--raw`,
  `--offset`, `--center`). Lo de fuera de la selección también se lee (como WorldEdit); fuera del alto del mundo es aire.
- CA2: Se lee todo antes de cambiar nada (una foto), así lo ya movido no se vuelve a mover. Los cofres y carteles viajan
  con su contenido. Lo que hay que leer cuenta para el tope de bloques del grupo.
- CA3: Es una obra del motor como H2 CA3; si ningún bloque cambia, lo dice.

**H4 — Biomas desde una fórmula.** `/wc generatebiome <bioma> <fórmula>` (alias `genbiome`).
- CA1: Cambia el bioma de cada celda de 4×4×4 de la selección donde la fórmula es verdadera en el centro de la celda;
  `--hollow`, `--raw`, `--offset` y `--center` como en H2. «did you mean» si el bioma no existe.
- CA2: Es la obra de biomas de la 033: límites, `gmask`, historial, `undo` seguro y los jugadores lo ven sin salir.

**H5 — Fórmulas en máscaras y patrones.**
- CA1: La máscara `=fórmula` pasa donde la fórmula es verdadera, con `x`, `y`, `z` del mundo (como WorldEdit):
  `/wc replace =y<64 with stone`, `/wc gmask =x<100`, `!=y<64`, `=y<64 and stone`. Sirve donde sirven las máscaras de
  posición (029) y en `gmask` (no mira vecinos).
- CA2: El patrón `=fórmula -> a, b, c` elige el bloque por el número de la fórmula (0 el primero, 1 el segundo; menos de
  0 el primero y más del último el último): `/wc set =(x+z)%2 -> white_wool, black_wool` hace un tablero. Sirve en
  todos los comandos con patrón y en los *presets*.

**H6 — Calculadora y tope de tiempo.**
- CA1: `/wc calc <fórmula>` (alias `calculate`, `eval`) dice el resultado (`2^10 + sqrt(16) = 1028`).
- CA2: `/wc timeout [ms|default]` dice o cambia, para quien lo pide, cuánto puede calcular una fórmula en un comando
  (de fábrica `formulas.timeout_ms`, como mucho `formulas.max_timeout_ms`).

**H7 — Pincel de deformar.** `/wc brush deform [radio] [fórmula]` (de fábrica 5 y `y-=0.2`, como WorldEdit).
- CA1: Clic derecho: la deformación de H3 en la esfera del radio donde se mira, con las coordenadas de −1 a 1 en la
  esfera (`--raw` y `--offset` como en H3; `--offset` desde el bloque que se mira). Con límites, máscara del pincel
  (`mask=`) y global, y `undo`.

**H8 — Seguridad, ayuda y documentación.**
- CA1: Sección `formulas` en `config.yml` (tiempo, tope de tiempo, vueltas, largo, tamaño de `megabuf`) revisada por
  `/wc validate`; `/wc help <subcomando>` con ejemplo para cada uno nuevo; autocompletado de opciones y biomas.
- CA2: README de Worldcraft (fórmulas, «Coming from WorldEdit»), README raíz, tabla de paridad y roadmap.

## Fuera de alcance (al [roadmap](../../roadmap.md))
`query()`, `queryRel()`, `queryAbs()` (leer bloques desde la fórmula; en WorldEdit con números de bloque viejos),
`gmegabuf` (compartido entre fórmulas), `closest()`, `switch`, las variables `type` y `data` de `//generate` (números de
bloque viejos: el patrón `=fórmula -> …` las reemplaza), los mismos números de ruido que WorldEdit, la forma `/brush
deform` con otra forma que no sea esfera.

## Decisiones (CLARIFY)
| # | Pregunta | Propuesta de la IA | Respuesta del dueño (2026-11-02) |
|---|---|---|---|
| 1 | ¿Qué entra? | Todo menos `query` | **Todo menos query** |
| 2 | ¿Lenguaje propio o el de OmniEvents como biblioteca común? | Propio, como WorldEdit ([ADR 0006](../../../adr/0006-formulas-de-worldcraft.md)) | **Propio, como WorldEdit** |
| 3 | ¿Cómo se escribe una fórmula como patrón? | `=fórmula -> a, b, c` | **`=fórmula -> a, b, c`** |

Decisiones de la IA: `/wc timeout` es el tiempo de **todo el cálculo** de un comando (WorldEdit corta cada evaluación;
aquí cada evaluación se corta por vueltas y el cálculo entero por tiempo, fuera del hilo del servidor). En las máscaras
y los patrones la fórmula se evalúa en el hilo del servidor bloque por bloque, dentro de los 8 ms por tick; una
evaluación que pasa del tope de vueltas cuenta como 0 (falso). Las variables que asigna una fórmula siguen de un bloque
al siguiente (como WorldEdit). `generatebiome` evalúa el centro de cada celda.

## Preguntas que se le hicieron al dueño
1. ¿Qué entra? (todo menos `query` · solo el núcleo: lenguaje, `generate`, máscara, `calc`, `timeout` · todo, `query`
   incluido)
2. ¿El lenguaje es propio o se saca el de OmniEvents a una biblioteca común? (propio como WorldEdit · biblioteca común)
3. ¿Cómo se escribe una fórmula como patrón? (`=fórmula -> a, b, c` · sin patrón, solo máscara)

# Verify 034 — Fórmulas (Worldcraft 0.10.0)

**Fecha:** 2026-11-02 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar (como la
025–033). Alcance elegido por el dueño en el CLARIFY: todo menos `query`; lenguaje propio como el de WorldEdit
([ADR 0006](../../../adr/0006-formulas-de-worldcraft.md)); patrón `=fórmula -> a, b, c`.

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew build`: en verde para los tres plugins (OmniEvents 209, SkinStatues 69, Worldcraft **252** = 222 de la 033 + 30 nuevas: `FormulaTest` 11, `ShapesTest` 7, `FormulaMaskTest` 6, `MapSourceTest` 2, `ToolSpecTest` 2, `ConfigTest` 1, `CommandTest` 1). Precedencia de WorldEdit (`-2^2 = 4`, `2^3^2 = 512`), asignaciones, `++`/`--`, los dos `for`, `while`, `do`, `break`, `continue`, `return`, las funciones y el ruido, `rotate`/`swap`; errores con posición y «did you mean» (`sinn`, una variable, `X` en mayúscula, `query`, `switch`, constantes); el tope de vueltas, el plazo, el largo, el anidamiento y `megabuf`; un `Run` por hilo; coordenadas de −1 a 1, `--raw`, `--offset`, `--center`; la esfera contada a mano, la cáscara, una caja hueca, un cilindro con `--raw`; `deform` con orígenes fuera de la selección, NaN y sin cambios; celdas en su centro y huecas; `MapSource` lee la foto (lo movido no se vuelve a mover) y lleva el cofre; la máscara y el patrón, combinados, negados, en *presets*, la coma dentro de una función; `brush deform` con `;` y `=` en la fórmula por el ítem; la sección `formulas`; la ayuda de los 5 subcomandos y los ejemplos del README |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24, con WorldEdit 7.3.19), con la sección `formulas` de fábrica: «Configuration OK» (un `config.yml` de la 0.9.0, sin ella, lo cubre `ConfigTest`). `selftest`: «Selftest formulas passed: a sphere of 485 blocks from x^2+y^2+z^2<1, the mask =y<0, a loop cut at 100000 rounds and a calculation cut at its time». Desde la consola: `generate stone x^2+y^2+z^2<1` = 485 bloques (`count stone` = 485), `--hollow glass` = 210, `calc 2^10 + sqrt(16)` = 1028, «Unknown function 'sinn' at 1 (did you mean 'sin'?)», `timeout 30` corta un cálculo largo («134 of 1,331 worked out»), `while(1){}` se corta por las vueltas, el tablero `=(x+z)%2->white_wool,black_wool` (671 de lana blanca), `replace =y<-45 with dirt` = 605, `deform --raw y-=1` = 242, `generatebiome desert x<0` = 12 de 36 celdas, `undo 6` deja 0 bloques. Logs sin errores en los dos arranques |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **36/36 dos veces** (33 de antes + 3 nuevos), tick más largo de la obra de 36 000 bloques 8 ms |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1–CA3 | `FormulaTest`; 3a («did you mean 'sin'», `calc`); compuerta 2 |
| H1 CA4 | `FormulaTest` y `ShapesTest` (vueltas, plazo, hasta dónde llegó); 3a (`timeout 30` corta `for(i=0;i<5000;i++){…}`; `while(1){}` «ran more than 100,000 times»); compuerta 2 (`selftest`) |
| H2 CA1 | 3a: **la misma cantidad de bloques que `//g stone x^2+y^2+z^2<1` de WorldEdit 7.3.19 en la misma caja de 11×11×11** y la contada a mano; `ShapesTest` (`--raw`, `--offset`, `--center`, un cilindro) |
| H2 CA2 | 3a (`--hollow`: el centro queda en aire, el borde con vidrio); `ShapesTest` |
| H2 CA3 | 3a («Working out the formula on 1,331 blocks», obra, `undo`); el motor de siempre (`Edits.request`); `formula-nothing` en el código |
| H3 CA1–CA3 | 3a (`deform --raw y-=1` sube la piedra leída **debajo** de la selección, `undo`; `deform x = x` «moves no block»); `MapSourceTest`; compuerta 2 (242 bloques) |
| H4 CA1–CA2 | 3a (`generatebiome desert x<0`: 72 de 144 celdas, desierto en el centro de su mitad y llano en la otra, `undo`); `ShapesTest`; compuerta 2 |
| H5 CA1 | 3a (`replace =y<-59 with stone` = 36, `gmask =x<43` con `set glass` = 54, «ends too soon» para `=y<<`); `FormulaMaskTest` |
| H5 CA2 | 3a (el tablero de 108 bloques, lana blanca y negra alternadas); `FormulaMaskTest` (acotado a la lista, *preset*) |
| H6 CA1–CA2 | 3a (`calc`, `timeout 30`, `timeout default`); compuerta 2 |
| H7 CA1 | 3a (`brush deform 3 y-=0.5` con la mano vacía: el suelo sube uno donde mira, `undo`); `ToolSpecTest` |
| H8 CA1 | `ConfigTest.spec034…` (tope bajado, fuera de rango, «did you mean 'megabuf_size'», el analizador con los topes); `CommandTest.spec034…` |
| H8 CA2 / README | README de Worldcraft (sección *Formulas* con ejemplos probados en `FormulaTest`, arriba dos líneas, «Coming from WorldEdit», configuración `formulas`, cómo actualizar desde la 0.9.0, compatibilidad 0.10.0, «Not yet» sin las fórmulas), README raíz (fila 0.10.0), tabla de paridad (12 filas), roadmap. Sin capturas nuevas |

## Hallazgos
| # | Qué pasó | Arreglo | Prueba |
|---|---|---|---|
| F-01 | Armado de la suite (no del plugin): el escenario de `deform` ponía piedra sobre el pasto del mundo plano; tapado, el pasto se volvió tierra y la **corrida siguiente** falló en escenarios viejos (H1 espera pasto en 40 −61 40; la 033 cuenta pasto en las celdas de desierto: 47 y 20 en vez de 48). Se repitió la lección de la 029 | La piedra va en el aire, fuera de la selección (y sigue probando que se lee lo de afuera); el `finally` del escenario deja bedrock, tierra y pasto como eran donde trabajó el pincel; el suelo del servidor de prueba se reparó con `forceload add` + `fill` (72 bloques) | 3a 36/36 dos veces seguidas (falló dos veces seguidas con el escenario anterior) |
| — | Dos expectativas de la suite mal escritas por la IA: `<<` es un operador (`=y<<` da «ends too soon», no «Unexpected '<'») y con la mano vacía el pincel dice «Here is your brush (…)» | Corregidas | 3a |

## Riesgos
- Las máscaras y los patrones con fórmula se evalúan en el hilo del servidor (dentro de los 8 ms por tick): una fórmula
  con bucles largos hace lenta la obra (no traba el servidor; `/wc cancel` la corta) y una evaluación cortada por las
  vueltas cuenta como falsa sin avisar.
- El ruido (`perlin`, `ridgedmulti`, `voronoi`) es propio: una fórmula de WorldEdit con ruido da otra forma parecida.
- `deform` lee la caja de todos los orígenes: una fórmula que manda lejos pide leer mucho (lo frena el tope de bloques).
- Paper 26.1.2 sin verificar.

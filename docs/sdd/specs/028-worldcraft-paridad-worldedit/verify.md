# Verify 028 — Formas, región, utilidades y navegación en Worldcraft

**Fecha:** 2026-11-01 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar (como la
025–027). Alcance elegido por el dueño en el CLARIFY: A + B + C + E.

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew :worldcraft:test`: **121** en verde (94 de la 027 + 27 nuevas: `SolidTest` 7, `OpsTest` 13, `SpotsTest` 4, `ConfigTest` 1, `CommandTest` 2) |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24), actualizando desde la 0.3.0 (`config.yml` y `messages.yml` viejos: `shapes`, `utilities` y los mensajes nuevos salen de los valores de fábrica). Desde la consola con `--at` en x = 6000: `validate` OK; `sphere`, `hsphere --thickness 2`, `cyl`, `hcyl` (sin piso), `pyramid`, `cone`; radio 50000 rechazado por `shapes.max_radius`; `hollow` y `undo`; `center`; `stack 3 east` con un cofre (los 5 diamantes en la copia, por `data get`); `move 10 up --select`; `line` con grosor; `drain --waterlogged` (la escalera queda `waterlogged=false`) y `undo`; `fixwater` (`level=0`); `fill` que no sube de los pies; `removenear`; `snow`, `thaw`, `green`; `naturalize` (pasto, tierra, piedra); `overlay`; `extinguish`; `removebelow`; navegación desde la consola → «Only a player»; `drain 500` → tope de `utilities.max_radius`. **31 comprobaciones `execute if block`, todas «Test passed»**; log sin errores en los dos arranques. F-01 y F-02 salieron de aquí |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **18/18 dos veces** (15 de antes + 3 nuevos): esfera con `--preview` que solo ve Ana, `confirm` con la cantidad exacta de la fórmula y `undo`; pirámide sobre Ana y `unstuck`; `stack` con un cofre y `move --select`, `undo 2`; `drain` y `undo`; `ascend`, `descend`, `thru`; `up` en supervivencia pone un vidrio (obra de 1 bloque) y `undo` lo quita; Beto sin `worldcraft.navigate`. El tick más largo de la obra de 36 000 bloques sigue en 8 ms |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1–CA2 | `SolidTest` (esfera punto por punto, elipsoide, cilindro, pirámide, cono, alturas negativas); compuerta 2 (las 7 formas); 3a (esfera) |
| H1 CA3 | `SolidTest.H1_CA3_hueca_sin_agujeros…` (inundación desde fuera, grosor 1 y 2) y simetría |
| H1 CA4 | Compuerta 2 (radio rechazado antes de calcular); 3a (`--preview`, `confirm`, `undo`) |
| H2 CA1–CA2 | `OpsTest` (stack sin y con aire, cofre; move lejano: solo 2 chunks; move que se pisa con relleno); compuerta 2; 3a |
| H2 CA3–CA4 | `OpsTest` (overlay no reemplaza un tronco; naturalize por capas, la profundidad sigue bajo un mineral); compuerta 2 |
| H2 CA5 | `smooth` usa el suavizado del pincel de la 026 sobre el suelo natural (`Brush.smoothArea`); compuerta 2: sin suelo natural lo dice y no toca nada. **Sin prueba en el juego sobre terreno natural**: queda como riesgo |
| H2 CA6 | `OpsTest` (hollow cerrado y abierto, grosor 2; line diagonal); `SolidTest` (line con grosor); compuerta 2 (center, line) |
| H2 CA7 | Compuerta 2 y 3a (undo de hollow, stack, move) |
| H3 CA1–CA5 | `OpsTest` (drain solo lo conectado y con lo anegado; fill no sube de los pies; snow, thaw, green); compuerta 2 (las 12 utilidades); 3a (drain) |
| H4 CA1–CA3 | `SpotsTest`; 3a (unstuck, ascend, descend, thru, up con vidrio, Beto sin permiso) |
| H7 CA1–CA2 | README «Coming from WorldEdit»; roadmap con lo que queda fuera |

## Hallazgos
| # | Qué pasó | Arreglo | Prueba |
|---|---|---|---|
| F-01 | `green 1` no cambió nada con la tierra 2 bloques bajo el punto: `snow`, `thaw` y `green` buscaban la cima solo entre ±radio, así que volando o en una ladera quedaba fuera | La cima se busca desde el radio por encima hasta el fondo del mundo (`surfaceBox`), leyendo primero (`TopScan`) para no recorrer la columna entera al poner | `CommandTest.spec028_F01_…`, **falló con el código anterior** (`Box[... minY=171 ...]` no contenía y = 170); compuerta 2 repetida: «green changed 9» |
| F-02 | Tras leer la foto, `stack`, `move` y `snow` decían «is waiting for your previous edit to finish»: la lectura ya terminada seguía en el planificador al pedir la obra | Lo que sigue a una lectura corre en el tick siguiente | Compuerta 2 repetida: ningún «waiting» (mensaje, sin prueba unitaria: depende del planificador de Bukkit) |
| — | El escenario de `drain 8` esperaba 9 bloques y fueron 8: la esquina del estanque está a √73 > 8,5. Error del escenario, no del plugin | Radio 10 en el escenario | — |

## Riesgos
- `smooth` sobre terreno natural real solo se probó en unitarias (el algoritmo) y en la compuerta 2 sin suelo natural.
- Navegación: `thru` y `jumpto` probados en un caso cada uno; escaleras, losas y bloques con caja rara (`isPassable` de Paper) sin probar.
- Paper 26.1.2 sin verificar.

## README
`plugins/worldcraft/README.md`: sección de formas, región, utilidades y navegación; «Coming from WorldEdit» con la
equivalencia de comandos y lo que falta (WorldGuard y PlotSquared necesitan WorldEdit); configuración `shapes` y
`utilities`; permiso `worldcraft.navigate`; compatibilidad 0.4.0. README raíz: fila de Worldcraft (0.4.0) y la
investigación nueva. Sin capturas nuevas (lección 004).

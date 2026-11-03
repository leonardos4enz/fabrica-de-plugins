# Verify 040 — Lo que le falta de WorldEdit 7.3.19 (Worldcraft 0.15.0)

**Fecha:** 2026-11-03 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar (como la
025–039). CLARIFY del dueño: los cuatro grupos; los *features* con NMS aislado ([ADR 0009](../../../adr/0009-nms-aislado.md)).

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew build`: en verde. Worldcraft **342** = 312 de antes + 30 nuevas: `CurveTest` 4, `SnowMathTest` 4, `MorphHeightmapTest` 4, `ItemUsesTest` 4, `ToolParser040Test` 8, `StackerTest` 1, `InfoTest` 3, `SlashTableTest` 1, `PresentacionTest` 1. Con el código anterior: las nuevas no compilan (no existen `Curve`, `SnowMath`, `ItemUses`, `Stacker`, `Info`, `BrushMath.morph/heightmap`, los `ToolKind` nuevos) y `SlashTableTest` y `PresentacionTest` fallan (faltaban `/chunkinfo`, `//curve`, `//fillr`… y las cifras 157/166/119) |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24, con WorldEdit 7.3.19 y el plugin de ejemplo): «Game features available (//feature, //update): 204 features»; `selftest` completo con el paso nuevo «Selftest game features passed: 204 features; captured without touching the world: forest_rock, huge_red_mushroom, patch_grass…» (la zona de 25 × 37 × 25 queda igual bloque por bloque) y la forma de una valla rodeada de suelo que se une; con el jar final, «ore_diamond_small stopped (getChunk)» y «a fence next to bedrock connects»; `version`, `validate` («Configuration OK»), `schem formats`, `searchitem`, `report` (archivo escrito), `tz`, `trace`; sin errores en los dos arranques |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **51/51 dos veces seguidas** (46 de antes + 5 nuevos; la 028 de `up` sin poder volar sigue igual) |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1 | `CurveTest` (pasa por cada punto, sin huecos, se abre afuera del vértice, grosor y hueca); 3a (`/wc curve glass` por los 3 puntos de la convexa, con una caja dice qué falta; `//curve -h stone 1` deja el centro vacío; undo) |
| H1 CA2, H2 CA2 | `SnowMathTest` (alisa sin tocar el suelo, no sobre vidrio que no aguanta ni fuera de la forma, pareja no cambia); 3a (8 capas → 1 y el vecino 1; undo) |
| H1 CA3 | 3a (`//fillr white_wool 2 3`: la esquina de abajo queda; la de arriba no) |
| H1 CA4, CA6, H2 CA7 | Compuerta 2 (captura sin tocar el mundo); 3a (`/feature huge_red_mushroom` sobre tierra con vista previa, cancel y undo; sin tierra «doesn't fit»; nombre mal escrito sugiere; `ore_diamond_small` se rechaza; `brush feature patch_grass`; `wc version` dice «available») |
| H1 CA5 | Compuerta 2 (una valla rodeada de suelo se une); 3a (tres vallas puestas sin física: `update` cambia 3 y las une; undo las separa) |
| H2 CA1, CA3–CA6 | `ToolParser040Test`, `MorphHeightmapTest`, `ItemUsesTest`; 3a (sobre un piso de vidrio: nieve y `--stack`, bioma y un nombre mal escrito, morph quita una punta, dilate llena un pozo, heightmap con una imagen blanca y una que no existe, apply hoe → cultivo, paint item honeycomb → cobre encerado, un ítem que no está) |
| H3 CA1 | 3a (`/placement pos1 0,2,0` pone la esfera ahí; `toggleplace`; `center`; `player`) |
| H3 CA6 | 3a (en creativo `wc up 4` cambia 1 bloque: vidrio bajo los pies, Ana queda encima; undo lo quita; el escenario de la 028 sigue igual sin poder volar) |
| H3 CA2 | 3a (con la varita apagada el clic no marca; prendida, sí) |
| H3 CA3, CA4 | `ToolParser040Test` (`reach` y `trace` en el ítem, acotados); 3a (con `range 3` «Nothing in reach»; `tracemask white_wool` apunta a la lana detrás del vidrio; `off` vuelve al vidrio) |
| H3 CA5 | `StackerTest`; 3a (`tool stacker 4` repite el vidrio 4 hacia arriba; undo) |
| H4 CA1–CA5 | `InfoTest`; 3a (`//chunkinfo` con `r.0.0.mca`, `listchunks`, `searchitem` y `//searchitem -b`, `schem formats`, `report` desde la consola escribe el archivo con versiones, plugins y `config.yml`, `trace` con los números de un `set`, `tz` con sugerencia) |
| H5 CA1 | `CommandTest` (cada `msg().get` en `messages.yml`), `SlashTableTest` (los nombres nuevos y sus banderas) |
| H5 CA2 | `PresentacionTest.H5_CA2…` cuenta las filas de `commands.md` (157 de 171, 166 de 244, cada estado) y exige lo mismo en las descripciones, el póster y el README; `H1_CA5` exige 119 |
| H5 CA3 / README | README de Worldcraft (sección «Everything else from WorldEdit (0.15.0)» con `item-uses.yml`, la tabla «Coming from WorldEdit», permisos, compatibilidad, lo que usa del servidor), README raíz (fila 0.15.0), `commands.md`, `modrinth-hangar.md`, `spigotmc.bbcode`, `worldcraft.html` y las franjas `1-header.png` y `5-commands.png` regeneradas (`npm --prefix tests-e2e run presentacion -- --plugin worldcraft`); `/wc up` en la tabla de navegación; roadmap; lección en `CLAUDE.md`. Sin capturas nuevas del juego (lección 004) |

## Hallazgos
| # | Qué pasó | Arreglo | Prueba |
|---|---|---|---|
| F-01 | `//searchitem diamond -b` buscaba «diamond -b»: el `//x` que se arma solo para cada comando no tenía las banderas que la tabla da a `/x` (en WorldEdit son el mismo comando) | `SlashTable.load`: el `//x` generado toma `to`, banderas y valores de fábrica de `/x` si la tabla no dice nada de `//x` | `SlashTableTest.spec040…` falla sin el arreglo (`searchitem diamond -b`), pasa con él; 3a `040 H4` |
| F-02 | Armado de pruebas (no del plugin): `ore_diamond` no existe en 1.21.4 (son `ore_diamond_small`…), un geodo en el aire no cabe, la cáscara de una curva de grosor 1 no está encima del punto y `'//fillr'` en la suite llegaba como `///fillr` | Escenarios y selftest corregidos | Lección en `CLAUDE.md` |

## Riesgos
- El puente NMS depende de la versión: en un Minecraft nuevo `feature`, `brush feature` y `update` pueden apagarse hasta
  actualizar `paper/Nms` (lo dicen `/wc version`, el arranque y el selftest). 26.1.2 sin verificar.
- Un *feature* que escriba por `getLevel()` (el `ServerLevel` de verdad) saltaría la captura: la compuerta 2 comprueba
  que los probados no tocan el mundo; los de minerales se cortan.
- `apply`/`paint … item` usa una tabla (`item-uses.yml`), no lo que hace el juego al usar cualquier ítem.

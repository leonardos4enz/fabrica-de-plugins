# Verify 029 — Tabla verificada contra los jars; selección, información y máscaras en Worldcraft (0.5.0)

**Fecha:** 2026-11-01 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar (como la
025–028). Alcance elegido por el dueño en el CLARIFY: selección, información y máscaras, con la spec chica de la 027 y la
deuda de la 028; orden 030–036 aceptado.

## Verificación de la tabla (pedido del dueño)
| Fuente | Resultado |
|---|---|
| Bytecode de los jars (`javap -v`, anotaciones `@Command`, `@Switch`, `@ArgFlag`, `@Arg`; parsers de máscaras, patrones y transformaciones; `SelectorChoice`) | WorldEdit 7.3.19: 171 comandos. FAWE 2.16.2: 269 anotados, 244 reales (`AnvilCommands` y `ListFilters` no son comandos) |
| Código fuente (etiquetas `7.3.19` y `2.16.2`) | WorldEdit: +2 de la versión de línea de comandos. FAWE: 4 comentados (`//lazycut`, `loadbrush`, `masks`, `transforms`) que la tabla de la 028 contaba |
| Servidor real (Paper 1.21.4, uno a la vez, puerto 25601 en el scratchpad) | `help WorldEdit`/`help FastAsyncWorldEdit` y `worldedit help -p N <grupo>`: todo lo del bytecode registrado; `/anvil` de FAWE «could not be found»; FAWE sin `//pos`, `//update`, `/placement` ni grupo `/tool` |

La [tabla](../../../investigacion/paridad-worldedit-fawe.md) (§0 y filas marcadas **029**) agrega una docena de comandos,
3 selectores de FAWE, la máscara `^[estado]`, máscaras y patrones de FAWE y ~40 flags; quita los 4 comentados; la columna
«Worldcraft» quedó al día con la 0.4.0. FAWE 2.16.2 fijado en `tests-e2e/lib/plugins-extra.mjs` (sha256
`a74219fa…c3f87d`, sha1 igual al de Modrinth) como plugin alternativo; la suite de Worldcraft filtra `/^worldedit/i`.

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew :worldcraft:test`: **145** en verde (121 de la 028 + 24 nuevas: `MaskNearTest` 12, `SelectionTest` 7, `LiquidsTest` 3, más `CommandTest` y `SchemFilesTest` 1 cada una; `H2_CA4` ajustada al mensaje nuevo de F-02) |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24, con WorldEdit 7.3.19), actualizando desde la 0.4.0 (`messages.yml` viejo: los mensajes nuevos salen de los de fábrica). Desde la consola en x = 7000: `count` con `above`, `surface`, `50% chance` y «did you mean 'exposed'»; `distr` por material y `--states`; `^stone_stairs` conserva `facing=west,half=top`; `replace … above … with andesite` (25); `gmask andesite` + `set` solo cambia la andesita y `gmask exposed` se rechaza; `outset`, `inset`, `trim`, `chunk --chunk-coords`; `copy --mask`, `paste --only-select`, `--select`, `--origin`; `distr --clipboard`; `clearclipboard`; `clearhistory` + `confirm` (6 borradas, después «nothing to undo»); `schem import` (2 copiadas; la segunda vez «2 already here»). Segunda vuelta tras F-01/F-02: `stone and !surface` = 51, `stone and !exposed` = 19 (lo calculado a mano), `^[half=top]` → `^[half=bottom]`, `gmask 50% chance` puso 69 de 125 y `undo` los devolvió. Log sin errores en los dos arranques |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **20/20 dos veces** (18 de antes + 2 nuevos), con WorldEdit 7.3.19 en el servidor: `hpos2`/`hpos1` en bloques a la vista; `count stone above dirt` (25 de 50); `distr`; `^stone_stairs` que el bot ve con `facing=west,half=top`; `replace stone above dirt and !surface` (1, la de debajo de la escalera); `gmask stone` + `set` (24) y `gmask exposed` rechazado; `undo 3`; `outset`, `inset` de más, `trim`, `chunk`; `copy --mask gold_block`, `distr --clipboard`, `paste --select` en otro lugar (solo el oro), `paste --origin` donde se copió; `clearclipboard`; `clearhistory` + `confirm`; en el escenario de esquemas de la 027, Ana importa los de WorldEdit (dos veces: la segunda no pisa) y Beto, sin `schematic.save`, no guarda, no importa ni los ve en el autocompletado. El tick más largo de la obra de 36 000 bloques sigue en 8 ms |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1 | 3a (`hpos1`/`hpos2` mirando bloques en el aire) |
| H1 CA2–CA4 | `SelectionTest` (`outset`, `inset`, `chunk`, `union`, `trim` con `Census`); compuerta 2; 3a |
| H1 CA5 | `SelectionTest` (`count` y `distr` por material y estado, con vecinos sobre la foto con borde); compuerta 2; 3a |
| H1 CA6 | `SelectionTest.H1_CA6_clearhistory…` (lo que está en marcha queda, el diario se borra, lo de otro no se toca); compuerta 2; 3a |
| H1 CA7 | Compuerta 2 y 3a (`copy --mask`, `paste --select`, `--only-select`, `--origin`) |
| H2 CA1–CA4 | `MaskNearTest` (cada palabra, «and», negación, errores, la foto decide); compuerta 2; 3a |
| H3 CA1–CA3 | `MaskNearTest.H3_*`; compuerta 2 (`^stone_stairs`, `^[half=…]`); 3a |
| H4 | Compuerta 2 (`gmask andesite`, `50% chance` y `undo`, `exposed` rechazado); 3a (`gmask stone`: 24 de 50) |
| H5 CA1–CA3 | `SchemFilesTest.spec029_H5_CA1…`; `CommandTest.spec029_…` (ayuda y permiso); compuerta 2; 3a (Ana importa; Beto sin `schematic.save` no guarda ni importa ni los ve en el autocompletado) |
| H6 | `SelectionTest.H6_center…` y `LiquidsTest` (antes sin prueba); `tasks.md` de la 028 corregido |
| H7 | Tabla, README, roadmap |

## Hallazgos
| # | Qué pasó | Arreglo | Prueba |
|---|---|---|---|
| F-01 | `stone above grass_block and !surface` decía «'!surface' is not a block name»: una parte de un «and» no se podía negar (y `!above grass_block` se partía en «!» y «above …») | Cada parte se puede negar con `!` o `not`: las de estado quedan como exclusiones del término (siguen siendo de estado, sin leer vecinos); las de posición, como `Near.Not` | `MaskNearTest.spec029_F01_negar_una_parte_del_and`, **falló con el código anterior** («'!surface' is not a block name»); compuerta 2 repetida: 51 y 19, lo calculado |
| F-02 | `stone above` sin bloque decía «'stone above' is not a block name» | El error dice qué falta: «after 'above' write blocks, like 'above grass_block'» | `MaskNearTest.spec029_F02_…`, **falló con el código anterior** |
| — | 3a: `count stone above grass_block` dio 17 de 25: el pasto tapado se vuelve tierra con los ticks al azar. Comportamiento de Minecraft, no del plugin | El escenario pone tierra bajo la piedra | — |
| — | 3a: `paste --origin` cambió 0: faltaba `srv.sincronizar()` después del `setblock` de la consola (lección 002 F-03) | Escenario | — |
| — | 3a: en una corrida completa `hpos1` decía «Look at a block»: tras el escenario de navegación de la 028, mineflayer 4.39 dejó de mandar el giro del bot (`data get entity Ana_e2e Rotation` seguía con el ángulo de `hpos2`, aunque el bot calculaba bien el nuevo). Cinco reintentos con `lookAt` no cambiaron nada: no era una carrera ni el plugin | El escenario pone el giro desde la consola (`tp … yaw pitch`); `hpos` usa lo que sabe el servidor | — |
| — | 3a: el escenario H2 CA3 de la 025 esperaba el mensaje viejo de «%» en una máscara | El mensaje ahora sugiere `30% chance`; regex actualizada | — |

## Riesgos
- `solid` usa `Material.isSolid` de Paper; en unitarias, una lista (como la 021). Sin prueba en el juego de bloques raros.
- Una máscara con vecinos lee la caja + 1 bloque antes de la obra: el doble de lectura (por tandas, con el tope de bloques).
- `gmask` vive en la sesión: se pierde al salir del servidor (como el portapapeles); el README lo dice.
- `hpos` desde la consola no aplica (solo jugadores); `replacenear`/`removenear` con vecinos solo se probaron en unitarias.
- En una corrida parcial (`--solo "028 H3|029"`), el escenario de navegación de la 028 falló dos veces en «estar del otro lado
  de la pared» (`thru`) cuando era el primero tras arrancar; en las 6 corridas completas pasó. No toca código de la 029 y
  parece el mismo problema de mineflayer con el movimiento del bot: queda anotado.
- Paper 26.1.2 sin verificar.

## README
`plugins/worldcraft/README.md`: selección e información, `copy`/`cut --mask`, `paste --origin/--select/--only-select`,
`clearclipboard`, `clearhistory`, `gmask`; máscaras que miran alrededor con «and» y patrones `^`; `schem import`;
permiso `worldcraft.schematic.save` y aviso en «Updating from 0.4.0»; «Coming from WorldEdit» con lo nuevo y el orden de
lo que falta; compatibilidad 0.5.0. README raíz: fila de Worldcraft (0.5.0). Sin capturas nuevas (lección 004).

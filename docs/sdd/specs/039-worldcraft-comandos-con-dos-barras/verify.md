# Verify 039 — Los comandos de WorldEdit con dos barras (Worldcraft 0.14.0)

**Fecha:** 2026-11-03 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar (como la
025–038). CLARIFY del dueño: prendidos de fábrica, Worldcraft se queda con los `//` aunque esté WorldEdit, banderas
cortas como en WorldEdit.

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew build`: en verde (OmniEvents 209, SkinStatues 69, Worldcraft **312** = 303 de antes + 9 nuevas: `SlashTableTest` 8, `ConfigTest` 1). Con el código anterior no compilan (no existen `SlashTable`, `SlashSettings` ni `LoadedConfig.slash()`). `SlashTableTest` revisa que cada destino de la tabla sea un subcomando de `/wc`, que cada opción la conozca ese subcomando y que estén **todos** los comandos de WorldEdit 7.3.19 que la tabla de la 038 marca «yes» |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24, con WorldEdit 7.3.19 y el plugin de ejemplo): «188 WorldEdit-style commands (//set, //copy, /brush...) run Worldcraft; 145 taken from WorldEdit (its own stay as /worldedit:/set...)»; ningún nombre de otro plugin tomado; `selftest` completo con el paso nuevo «Selftest commands passed: //set and 187 more WorldEdit-style commands run Worldcraft»; «Configuration OK»; sin errores en los dos arranques |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **46/46 dos veces seguidas** (43 de antes + 3 nuevos). Los escenarios de la 027, 031, 033 y 034 que comparan con WorldEdit ahora lo llaman por su espacio de nombres (`worldedit:/pos1`, `worldedit:/copy`, `worldedit:/schem save`, `worldedit:/g`, `worldedit:/paste -e`…) y pasan: WorldEdit sigue funcionando entero |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1 | `SlashTableTest` (`//x` para cada subcomando salvo `reload`, `validate`, `selftest`; los de WorldEdit de la 038; `//`, `//desel`, `/br`, `//g`); 3a (`//pos1`, `//pos2`, `//set`, `//re`, `//undo`, `//copy -e`, `//paste`, `//limit`) |
| H1 CA2 | 3a (los mismos mensajes «Done»/«Undone» y el historial de `/wc`; `//sel ` completa `magic` y `cuboid`, que es el autocompletado de Worldcraft; Beto ve y usa solo lo de `worldcraft.use`: `/regen` no está en su árbol) |
| H1 CA3 | El código (`SlashCommands.apply`: un nombre de otro plugin, del juego o de `commands.yml` se deja y se anota «Kept …»); en la compuerta 2 no hubo ninguno |
| H2 CA1 | Compuerta 2 y 3a (la línea «145 taken from WorldEdit» al arrancar; se toman en el primer tick, cuando WorldEdit ya registró los suyos; `worldedit:/pos1` dice «First position set») |
| H2 CA2, CA4 | 3a (`worldedit: leave` + `/wc reload`: «left to WorldEdit», `//pos1` vuelve a ser de WorldEdit y `//limits` sigue en Worldcraft; con `take` y `/wc reload`, `//pos1` otra vez de Worldcraft); al apagar, `unregisterAll` devuelve los de WorldEdit (mismo camino que `leave`) |
| H2 CA3 | 3a (Beto, solo con `worldcraft.use`: `/set` y `/pos1` en el árbol que recibe y `//set` hecho y deshecho). **Con el oyente de `PlayerCommandSendEvent` apagado el escenario falla:** el árbol de Beto trae solo `worldcraft:/set` (WorldEdit borra `/set`, `/pos1`… porque Beto no tiene sus permisos) |
| H3 CA1 | `SlashTableTest` (`-eb`, `-m <máscara>`, `-s -e`, `-h` → `hsphere`, `-hr`, `-pa`, `-n`); 3a (`//copy -e`) |
| H3 CA2 | `SlashTableTest` (`//paste` → `--air --no-entities`, `-ae`, `-a -os`, `//stack 3 -a`); 3a (`//paste` cambia 9 bloques con el aire, `//paste -a` solo 1) |
| H3 CA3 | `SlashTableTest` (`-5`, `-y`, `-x>0`, `-x` en una fórmula, `-v` y `-av` pasan igual) |
| H4 CA1 | `ConfigTest.spec039…` (de fábrica, valores malos con «did you mean», `skip` sin barra o desconocido); 3a (`/wc reload` aplica `worldedit: leave`); compuerta 2 («Configuration OK») |
| H4 CA2 | Compuerta 2 (paso del `selftest`) |
| H4 CA3 / README | README de Worldcraft (sección «Type it like in WorldEdit», la tabla «Coming from WorldEdit» dice que la columna izquierda funciona tal cual, `slash_commands` en la configuración, cómo actualizar desde la 0.13.0 y qué hacer con alias propios de `commands.yml`, permisos, compatibilidad 0.14.0, convivencia con WorldEdit/WorldGuard), README raíz (fila 0.14.0), `commands.md`, `modrinth-hangar.md` y `spigotmc.bbcode` (los `//`, 0.14.0; el 102 no cambia: `PresentacionTest` en verde), roadmap, lección en `CLAUDE.md`. Sin capturas nuevas |

## Hallazgos
| # | Qué pasó | Arreglo | Prueba |
|---|---|---|---|
| F-01 | WorldEdit escucha `PlayerCommandSendEvent` y quita del árbol del cliente todo nombre que su propio gestor conoce (`/set`, `/pos1`…) si el jugador no tiene el permiso de WorldEdit, aunque el comando ya sea de otro plugin | Oyente de Worldcraft en `HIGHEST` que vuelve a poner los suyos que el jugador puede usar (Paper solo borra del árbol lo que falta en la colección) | 3a `039 H2 CA3`: falla sin el oyente (visto en esta spec), pasa con él |
| F-02 | Armado (no del plugin): un heredoc de Bash con los escenarios de la suite cortó el script («unexpected EOF») | Escenarios y scripts con la herramienta de escritura (se repitió la 026/036) | — |

## Riesgos
- Un dueño con `//set` en su propio `commands.yml` (como `run-juego/`): ese alias se queda con el nombre y Worldcraft lo
  respeta; el README dice que lo quite. `run-juego/commands.yml` (arreglo temporal de otra sesión) no se tocó:
  `commands.yml.orig` es el original.
- `//paste` y `//stack` siguen a WorldEdit (pegan el aire) y `/wc paste` a Worldcraft (no): está escrito en el README.
- WorldGuard y PlotSquared siguen necesitando WorldEdit; con los dos instalados, sus `//` son de Worldcraft (WorldEdit en
  `/worldedit:/set`, o `worldedit: leave`).
- Paper 26.1.2 sin verificar.

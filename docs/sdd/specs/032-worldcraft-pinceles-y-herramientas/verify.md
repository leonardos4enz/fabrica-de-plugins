# Verify 032 — Pinceles de construcción y herramientas de un clic (Worldcraft 0.8.0)

**Fecha:** 2026-11-02 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar (como la
025–031). Alcance elegido por el dueño en el CLARIFY: todo menos `tool tree` (pasa a la 033); el ítem de la mano, solo
ese; `worldcraft.use` + `worldcraft.superpickaxe`; el superpico no suelta ítems.

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew :worldcraft:test`: **201** en verde (188 de la 031 + 13 nuevas: `ToolSpecTest` 4, `BrushMathTest` 6, `CyclerTest` 2, `ConfigTest` 1). El orden de WorldEdit y la ida y vuelta por el ítem (un ítem armado a mano se acota a los topes), errores con «did you mean», `radius=`/`mask=` sobre el pincel de la mano; `splatter` solo en la superficie y más ralo con `decay` alto, `scatter` separadas y encima con `--overlay`, `rock` con sphericity 100 y sin amplitud igual a la esfera de `/wc sphere`, `gravity` por el motor con un cofre que no se mueve (y una segunda pasada no cambia nada), `deltree` flotante, plantado y muy grande, superpico de área sin la roca madre; el cycler con valores inyectados |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24, con WorldEdit 7.3.19), actualizando desde la 0.7.0 (`config.yml` sin la sección `tools`: «Configuration OK»). `selftest` con el **cycler sobre el registro real**: 4 caras, 2 mitades y 5 formas de una escalera y las 8 edades del trigo con `Bukkit.createBlockData`. `help tool` y `help sp` con su ejemplo; `sp` y `tool info` desde la consola: «Only a player can do that». Logs sin errores en los dos arranques |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **29/29 dos veces** (25 de antes + 4 nuevos). Una primera corrida dio 28/29: el escenario de la 025 «una obra grande espera confirmación» midió 12,1 ms en un tick (tope de la prueba 12): el motor no cambió en esta spec y las dos corridas siguientes midieron 8 y 8,1 ms; se toma como ruido en frío (recolector), como la 025 F-04 |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1–CA2 | 3a: con la mano vacía da la vara; otra vara igual no es pincel; `none` deja la vara sin `custom_data`, nombre ni descripción (`data get entity … SelectedItem`); la vara normal de la mano se vuelve el cilindro; `ToolSpecTest.H1_CA2…` |
| H1 CA3–CA4 | 3a: clic derecho al aire de lejos (mineflayer manda `RIGHT_CLICK_AIR`), el círculo lo ve Ana y no Beto, `undo` |
| H1 CA5 | `ToolSpecTest.H1_CA5…`; 3a (`mask=air` deja el pasto) |
| H1 CA6 | `Tools.onBreak`/`onPlace`/interacción cancelada; 3a (el superpico en creativo no rompe por su cuenta la roca madre) |
| H2 CA1–CA3 | `ToolSpecTest.H2…`; 3a (esfera, cilindro de alto 3) |
| H2 CA4 | 3a (`brush clipboard` pega los dos de oro centrados donde mira) |
| H3 CA1–CA3 | `BrushMathTest.H3_CA1…`, `H3_CA2…`, `H3_CA3…` (son al azar: en el juego no se comprueban bloque a bloque) |
| H4 CA1–CA3 | `BrushMathTest.H4_CA1…`; 3a (gravity con el cofre, extinguish, butcher con y sin `--animals` y `undo`) |
| H5 CA1–CA8 | 3a (repl toma `oak_log[axis=x]` con el clic izquierdo y lo pone; cycler `axis=y` y la propiedad en la barra; info; farwand; floodfill 3 de 4; lrbuild pone y quita; deltree 3 bloques y «touches the ground»; navwand `thru` y `jumpto`); `CyclerTest`; `BrushMathTest.H5_CA5…`; compuerta 2 (cycler real) |
| H6 CA1–CA3 | 3a (`sp` single sin ítems en el suelo y `undo`, `sp area 1` solo la piedra, la roca madre «never breaks», `sp off`, Beto sin permiso); `BrushMathTest.H6_CA1…` |
| H7 CA1 | Permisos y límites en cada clic (`Tools.onInteract`, `Edits.request`); 3a (Beto sin `worldcraft.use` ni `worldcraft.superpickaxe`) |
| H7 CA2 | `ToolSpecTest.H7_CA2…`; 3a (`brush spere` sugiere `sphere`); ayuda en la compuerta 2 |
| H7 CA3 | `ConfigTest.spec032_H7_CA3…` |
| H8 | README de Worldcraft (sección *Building brushes and one-click tools*, «Coming from WorldEdit», configuración, permisos, compatibilidad), README raíz, tabla de paridad, roadmap |

## Hallazgos
| # | Qué pasó | Arreglo | Prueba |
|---|---|---|---|
| — | En la compuerta 2, `/wc help brush` mostraba la ayuda de la 0.7.0: el `messages.yml` del servidor es del dueño y conserva los textos que ya tenía (una línea nueva sí llega, como `help.tool`) | No es un error del plugin (los textos son personalizables); el README explica que se borre la línea `brush:` de `help:` para ver la nueva. Al roadmap: avisar al actualizar qué textos de la ayuda cambiaron | — |
| — | Armado de la suite: un `setblock` de la consola llegaba después del clic del bot (la consola no espera; se repitió la 002 F-03) | `srv.sincronizar()` después de cada preparación | — (lección en `CLAUDE.md`) |

No se encontraron errores del plugin.

## Riesgos
- `brush clipboard` usa el portapapeles del momento del clic, sin entidades (WorldEdit guarda una copia al ponerlo y pega
  entidades con `-e`): documentado, al roadmap.
- El cycler arma los valores posibles con un diccionario de los valores de Minecraft 1.21.4; una propiedad nueva de otra
  versión con valores que no estén ahí solo ofrecería los números y verdadero/falso. El `selftest` lo comprueba con una
  escalera y el trigo.
- `deltree` mira 6 vecinos (como WorldEdit): hojas que solo se tocan en diagonal quedan.

## README
README de Worldcraft (sección *Building brushes and one-click tools*, ejemplos de arriba, «Coming from WorldEdit»,
configuración `tools`, permisos, cómo actualizar desde la 0.7.0, compatibilidad 0.8.0) y README raíz (fila de
Worldcraft, 0.8.0). Sin capturas nuevas.

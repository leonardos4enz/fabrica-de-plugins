# Verify 041 — Lo que le falta de FAWE 2.16.2 (Worldcraft 0.16.0)

**Fecha:** 2026-11-03 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar (como la
025–040). CLARIFY del dueño: grupos a, b, d y e; el historial (c) a otra spec; solo `//fixlighting` por el puente NMS
([ADR 0009](../../../adr/0009-nms-aislado.md), ampliado).

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew build`: en verde. Worldcraft **362** = 342 de antes + 20 nuevas: `LineMathTest` 4, `BrushMath041Test` 4, `TransformsTest` 4, `ToolParser041Test` 6, `SlashTableTest` 1, `ColorPatternTest` 1. Con el código anterior: las nuevas no compilan (176 errores: no existen `LineMath`, `Transforms`, `TransformSource`, `SourceMaskedSource`, `Scroll`, `Brushes`, `BrushMath.pull/shatter/stencil/fall`, los `ToolKind` nuevos, `Caps` de 4) y `PresentacionTest` falla (119 → 138 comandos, la sección de la tabla) |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, con WorldEdit 7.3.19 y el plugin de ejemplo): `version`, `validate` («Configuration OK»), `selftest` completo con el paso nuevo «Selftest light passed: 1 chunk(s) relit, blocks unchanged; //nbtinfo no data at the bottom» (la zona de 25 × 37 × 25 queda igual bloque por bloque), `fixlighting` de una selección de 9 chunks («Light worked out again in 9 chunks»), `registry biome forest` (8), `debugpaste` (archivo con el final de `latest.log` y los `.yml`); sin errores en los dos arranques (solo el «No key layers» de Paper, ruido conocido) |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **55/55 dos veces seguidas** (51 de antes + 4 nuevos) |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1, CA4 | `LineMathTest` (línea plana y cáscara; catenaria por los dos extremos, cuelga en el medio, factor 1 recta, hacia donde mira, con desnivel); 3a (`line` con dos clics pone la piedra en los dos puntos; `catenary 1.5` pone más bloques que la recta; `0.5` se rechaza; undo) |
| H1 CA2, CA3 | `LineMathTest` (el barrido reparte las copias y las gira según la curva); 3a (`spline` cierra con el mismo bloque y pasa por el segundo punto) |
| H1 CA5 | `LineMathTest` (el círculo es perpendicular a la mirada; `--fill` el disco); 3a (`circle 2` pone el anillo) |
| H1 CA6, CA7, H2 CA1 | `BrushMath041Test` (pull por pasadas; shatter con bordes, determinista, un pedazo sin bordes); 3a (pull estira la piedra hacia Ana; recursive pinta el vidrio conectado y no el de fuera; `partition` parte el vidrio) |
| H2 CA2–CA4 | `BrushMath041Test` (stencil pinta lo claro en la cima; el cono; girar la imagen); 3a (stencil sin imagen pinta solo el centro; image pone la lana roja en el círculo 3 × 3 y avisa de una imagen que no está; height sube el cono 2) |
| H2 CA5 | 3a (un esquema guardado se esparce sobre el vidrio del círculo; undo) |
| H2 CA6, CA7 | `ToolParser041Test` (nombres de pinceles guardados: `../x`, `a/b`, vacíos y largos se rechazan); 3a (copypaste copia 9 con el clic izquierdo y pega 9 con el derecho; savebrush, listbrush, loadbrush y un nombre mal escrito sugiere) |
| H3 CA1 | `ColorPatternTest` (con la textura, `#color[red]` elige entre esos; sin ella, toda la tabla); 3a (`//gtexture white_wool, red_wool`: el negro cae en la roja; off: la negra) |
| H3 CA2, CA3, CA4 | `TransformsTest` (offset, rotate y flip alrededor del centro, scale y spread con la caja y el volumen, errores con «did you mean»; la máscara de origen deja lo que no cumple); 3a (`//gtransform #offset[0][3][0]` corre el set; `//globalsourcemask stone` no pone el vidrio; `/smask !glass` y `/transform` en el pincel) |
| H3 CA5–CA7 | `ToolParser041Test` (target, toffset acotado, smask, transform y secondary viajan en el ítem; la rueda: size, target, pattern con la vuelta; casilla de al lado ±1, saltar 0); 3a (`target face`, `/tar block`, `targetoffset 2`, un modo mal escrito; `primary` y `secondary` con el clic izquierdo; `scroll size` sube el radio con la rueda) |
| H4 CA1–CA4 | 3a (`place --origin` deja la fila sin girar; `//fall -m` baja la piedra hasta el fondo de la selección; `//fixconnect` une 3 vallas; `//nbt` muestra los diamantes del cofre) |
| H4 CA5, CA6 | 3a (la zona protegida del ejemplo detiene el set; con `//wea` sigue y dice quién la habría detenido; su undo también (F-02); `//wer` en la parcela selecciona 9×25×9; fuera, que nadie contesta) |
| H4 CA7, CA8 | Compuerta 2 y 3a (`registry biome cherry`, un registro mal escrito; `schem loadall` de dos, `schem move` mueve el archivo) |
| H4 CA9, CA10 | 3a (`tool inspect` dice «Ana_e2e changed glass to stone … (brush sphere)»; `featuretool huge_red_mushroom` sobre tierra) |
| H4 CA11, H5 CA1 | Compuerta 2 (`debugpaste`, selftest de la luz); 3a (`//fixlighting` de 2 chunks) |
| H6 CA1 | `CommandTest` (cada `msg().get` en `messages.yml`), `SlashTableTest.spec041…` (`//fall -m`, `//place -o`, `/globalsourcemask`, `//fixconnect`, `//nbt`, `//worldeditanywhere`, `/tar`…) |
| H6 CA2 | `PresentacionTest` cuenta las filas de `commands.md`: 157 de 171 de WorldEdit, **205 de 244** de FAWE (13 en parte, 5 «not yet»: el historial), 138 comandos; las descripciones, el póster y el README dicen lo mismo |
| H6 CA3 / README | README de Worldcraft (sección «What FAWE adds (0.16.0)», `config.yml`, permisos, API, compatibilidad), README del plugin de ejemplo (`build_areas`), README raíz (fila 0.16.0), `commands.md`, `modrinth-hangar.md`, `spigotmc.bbcode`, `worldcraft.html` y las franjas `1-header.png` y `5-commands.png` regeneradas (`npm --prefix tests-e2e run presentacion -- --plugin worldcraft`); roadmap; ADR 0009 ampliado; lección en `CLAUDE.md`. Sin capturas nuevas del juego (lección 004) |

## Hallazgos
| # | Qué pasó | Arreglo | Prueba |
|---|---|---|---|
| F-01 | `//fixlighting` y `//nbtinfo` quedaban apagados en Paper 1.21.4: `getLightEngine()` se declara con el tipo de arriba y en el servidor el método es `starlight$serverRelightChunks` (el `javap` del jar extraído mostraba `relightChunks`) | `Nms.find` busca por nombre en la clase cargada y sus padres; primero el de Starlight, si no `relightChunks`; el error lista los nombres parecidos | Compuerta 2: el selftest decía «not available (NoSuchMethodException…)» y ahora «Selftest light passed» |
| F-02 | Con `/wc wea`, el `undo` de una obra en la zona protegida seguía detenido (el deshacer dispara su propio evento) | El camino del deshacer también mira `bypass` y lo dice | 3a `041 H4`: fallaba en «Undone», pasa |
| F-03 | Armado de pruebas (no del plugin): un círculo de radio 1 son 3 × 3 (9, no 5), `tp … facing` no gira al bot de mineflayer, y una corrida cortada dejó la piedra en la zona protegida | Escenarios corregidos (9 bloques, `tp x y z yaw pitch`, la consola limpia el bloque con `forceload`) | Lección en `CLAUDE.md` |

## Riesgos
- `structureplacer` pone plantillas, no estructuras completas (queda «en parte», como `//structure`).
- `#rotate` y `#flip` mueven los bloques pero no giran sus estados (escaleras, troncos); está en el README.
- La luz depende del puente NMS: en un Minecraft nuevo `fixlighting` y `nbtinfo` pueden apagarse hasta actualizar `Nms`.
- `/wc scroll` toma la casilla de al lado como la rueda: con una acción puesta, la tecla del número vecino también cambia el valor.

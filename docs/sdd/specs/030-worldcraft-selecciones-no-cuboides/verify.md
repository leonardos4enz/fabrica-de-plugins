# Verify 030 — Selecciones que no son cajas en Worldcraft (0.6.0)

**Fecha:** 2026-11-02 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar (como la
025–029). Alcance elegido por el dueño en el CLARIFY: las 6 formas de WorldEdit **y** la mágica de FAWE, con los nombres y
los clics de WorldEdit; `walls`/`faces` = la cáscara.

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew :worldcraft:test`: **175** en verde (145 de la 029 + 30 nuevas: `ShapesTest` 7, `HullTest` 5, `SelectorTest` 8, `OutlineTest` 3, `ShapeOpsTest` 7). `ShapesTest` compara cada forma (caja, polígono cóncavo, polígono de 2 vértices, elipsoide, esfera, cilindro, convexa, mágica, recortada en y) contra la fuerza bruta en los tres recorridos (`FILL`, `WALLS`, `FACES`): cuenta, recorrido por chunks (cada posición una vez) y volumen |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24, con WorldEdit 7.3.19), actualizando desde la 0.5.0 (`config.yml` sin las claves nuevas: «Configuration OK»). Desde la consola en x = 7100: `sel cyll` → «did you mean 'cyl'»; esfera de radio 4 (389 bloques, no los 729 de su caja), `set` 389, `count` 389, `walls glass` 160, `expand 2 north` y `line` rechazados con el mensaje de la forma, `outset 1` → radio 5 (739); cilindro (185), `faces` 122, `expand 2 up` → 259; polígono de 3 vértices (264); convexa de 4 (117); `extend` (18); `magic` sobre los tablones → 18 conectados, `set` 18; esfera de radio 3 copiada, `shift 20 east`, `paste --air` cambió **179** de una caja de 343 y `count` en la caja da 179 (las esquinas quedaron); `stack 1 up` 179, `move 3 south` 222, `undo 2`. Logs sin errores en los dos arranques; el tick más largo de una obra, 1,3 ms |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **22/22 dos veces** (20 de antes + 2 nuevos), con WorldEdit 7.3.19 en el servidor: con la varita, `sel cyl`, clic izquierdo = centro, dos clics derechos = radios 2, 0 y 2, 2 (21 bloques), sin romper el pasto; el contorno llega a Ana y no a Beto; `expand 3 up` (84) y `expand 2 north` rechazado; `walls glass` = el tubo (48: 12 columnas × 4, sin el centro ni las esquinas de la caja); `undo`. Polígono de 3 vértices (84 = 28 columnas × 3) y `set`; `sel magic` toma los 84 de piedra conectados y `pos2` suma otro grupo de 2 (86), `set` 86; esfera de radio 2 copiada y pegada con `--air` en otro lugar: cambia lo de la esfera y el oro en la esquina de su caja queda; `stack 1 up` repite la esfera; `move 2 east --select` la mueve con la selección (`sel` dice el centro nuevo); convexa de 4 vértices (tetraedro) con `set`; `undo 6`. Los escenarios anteriores (que usan cajas) siguen en verde |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1–CA3 | `SelectorTest.H1_CA1…` (nombres de WorldEdit y FAWE, cambiar borra); compuerta 2 (`sel cyll`, cada `sel <forma>`); 3a |
| H2 CA1–CA2 | `SelectorTest.H2_CA1…`, `H2_CA2…`; compuerta 2 (`extend`) |
| H2 CA3, CA9 | `SelectorTest.H2_CA3_y_CA9…` (alto de los vértices, tope); `ShapesTest.H2_CA3…` (L cóncava, línea sin huecos); 3a |
| H2 CA4–CA6 | `SelectorTest.H2_CA4_a_CA6…`; `ShapesTest.H2_CA5…` (la esfera seleccionada es la de `/wc sphere`), `H2_CA6…`; 3a (cilindro con la varita, esfera) |
| H2 CA7 | `HullTest` (un punto, una recta, una lámina, un cubo, un tetraedro, coordenadas lejanas, demasiado ancha); compuerta 2; 3a |
| H2 CA8 | `ShapeOpsTest.H2_CA8…` (mismo material conectado, unión, aire); `SelectorTest.H2_CA8…`; compuerta 2; 3a (clic derecho suma) |
| H3 | `OutlineTest` (cada forma; la convexa sin las diagonales de sus caras; la mágica solo el borde); 3a (partículas solo para Ana) |
| H4 CA1 | `ShapeOpsTest` (`set` en un cilindro, `Census` sobre la forma y con la foto); compuerta 2 (`count`); 3a |
| H4 CA2 | `ShapesTest.H4_CA2…` (en una caja, lo de siempre); `ShapeOpsTest.H4_CA2…` (tubo y tapas); compuerta 2; 3a (48) |
| H4 CA3 | `ShapeOpsTest.H4_CA3…`; compuerta 2 (179 de 343); 3a |
| H4 CA4 | `ShapeOpsTest.H4_CA4…` (`move` y `stack`); compuerta 2; 3a |
| H4 CA5–CA6 | `SelectorTest.H4_CA5…`; compuerta 2 (`outset`, `expand up`, `expand north` y `line` rechazados); 3a |
| H5 | README de Worldcraft y raíz, tabla de paridad §2.1 y §4.2, roadmap |

## Hallazgos
| # | Qué pasó | Arreglo | Prueba |
|---|---|---|---|
| F-01 | En `poly`, volver a marcar un vértice que ya estaba (para subir el alto) con el tope de vértices lleno decía «at most N points» | El punto repetido se busca antes del tope: solo cambia el alto | `SelectorTest.H2_CA3_y_CA9…`, **falló con el código anterior** (IllegalArgumentException) |
| F-02 | Una clave de mensaje armada con `"sel-kind-" + forma` no la puede revisar `CommandTest` (que busca cada `msg().get("…")` en `messages.yml`) | Un `switch` con las 8 claves escritas | `CommandTest`, **falló con el código anterior** |
| F-03 | `messages.yml` quedó con `schem-import-none:"…` (sin espacio) al insertar los mensajes nuevos: YAML inválido | Corregido | `CommandTest` (carga `messages.yml`), **falló con el código anterior** |
| — | Primera idea para el contorno de la convexa: contar 12 aristas en un cubo. Los vértices de los cubos inflados que caen sobre una arista la parten en tramos (26 segmentos) | La prueba mide el largo total (12 × 5) y que no haya diagonales; el dibujo es el mismo | `OutlineTest` |

## Riesgos
- `Columns` guarda una tabla de columnas (8 bytes por columna) al primer uso; una forma de más de 16 millones de columnas
  se rechaza. Una convexa con muchos vértices tiene cientos de planos: la tabla se arma una vez por forma (el selector la
  guarda hasta que cambia).
- La mágica compara el **material** (no el estado): una escalera girada distinta cuenta como la misma. Como FAWE.
- `move`/`stack` de una forma reservan la foto de la caja que la abarca (como la 028 con una caja).
- `hollow` de una forma calcula el interior con la caja que la abarca y se queda lo que está en la forma.
- `naturalize` de una mágica usa, por columna, de su bloque más bajo al más alto (rellena huecos de la columna).
- Paper 26.1.2 sin verificar.

## README
`plugins/worldcraft/README.md`: «Selections that are not boxes» (tabla de formas y clics, qué hace cada comando sobre una
forma), la varita y el contorno, «Coming from WorldEdit» (`//sel …`), «Not yet» (`//sel list`), `config.yml`
(`max_points`, `magic_radius`), «Updating from 0.5.0», compatibilidad 0.6.0. README raíz: fila de Worldcraft (0.6.0).
Sin capturas nuevas (lección 004).

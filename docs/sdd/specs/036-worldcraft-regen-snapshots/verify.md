# Verify 036 — `regen` y copias de seguridad (Worldcraft 0.12.0)

**Fecha:** 2026-11-02 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar (como la
025–035). Alcance elegido por el dueño en el CLARIFY: `regen` y restaurar desde copias, sin las criaturas de la copia;
copias en carpetas y `.zip`; sin `snapshot create`.

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew build`: en verde para los tres plugins (OmniEvents 209, SkinStatues 69, Worldcraft **290** = 277 de la 035 + 13 nuevas: `SnapshotTest` 11, `ConfigTest` 1, `CommandTest` 1). La paleta empaquetada como la guarda el juego (17 entradas = 5 bits, 12 por `long`, sin partir; una sección de un solo estado sin `data`; los biomas por celdas de 4×4×4), los datos de un cofre sin la posición y con su `DataVersion`; gzip, zlib y sin comprimir; el chunk grande en el `.mcc` de al lado (y roto sin él); LZ4 dicho como no soportado; sin terminar (`Status`), de antes de 1.18 (`DataVersion` y `Level`), un largo empaquetado equivocado, un índice fuera de la paleta, un NBT cortado, una cabecera cortada y una posición fuera del archivo: rotos, sin reventar; una bomba de compresión (32 MiB de ceros en pocos KB) se corta; `Restore` solo dentro de un cilindro, la paleta global y sus cuentas, lo que falta o está sin terminar queda sin leer, el del `.mcc` se restaura; al portapapeles lo desconocido se deja (−1), los biomas que el servidor no tiene quedan sin bioma, los datos de un bloque que quedó aire se sueltan; copias en carpeta y en `.zip` (con una carpeta de más; muy hondo, roto o sin el mundo no se listan), la fecha del nombre o del archivo, la más nueva primero; una región de más de 64 MiB dentro de un `.zip` se corta; fechas, `before` y `after`; nombres con `..`, `/` o `\` no valen; los chunks de la caja región por región; la semilla (`random`, número, palabra como el juego) y las carpetas temporales (solo con la marca y el prefijo; la del dueño y una cargada no se tocan); las dos secciones de `config.yml` (un `config.yml` de la 0.11.0 sin avisos, fuera de rango, claves con «did you mean»); la ayuda, las claves armadas con `msg().text` y los permisos en `plugin.yml` |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24, con WorldEdit 7.3.19), `config.yml` de fábrica con `snapshots.folder: snapshots` y dos copias hechas a mano (una carpeta `2026-11-02-12-00/world/region/` y un `2026-11-01_08-00.zip` hecho con `jar`): «Configuration OK». Antes, la capa de pasto reparada (`forceload add` + `fill … grass_block replace dirt|sand`, nota de la 035). `save-all flush` y `selftest`: «Selftest backups passed: the spawn chunk read from the region file is the world, block by block (98,304 blocks, 0 with data, 1 biome(s)); a temporary world for regen was made in 138 ms with the same bottom layer, and deleted». Desde la consola en x = 200: `set stone` y `regen` = 216 bloques de vuelta a aire (`count stone` = 0); el suelo con oro y `regen 0` = pasto y roca madre (36 y 36); `snapshot list` (las dos, la más nueva primero, con su fecha), `snapshot use 2026-11-02-12-0` («did you mean '2026-11-02-12-00'»), `sel 2`, `before 2026-11-02` (el `.zip`), `after 2026-11-01 09:00` (la carpeta), `restore` desde la carpeta y `restore 2026-11-01_08-00.zip --biomes` desde el `.zip` (pasto otra vez, 0 de oro), `regen desert` (100 % desierto en `biomeinfo`, el suelo igual), `undo 7` y 0 de oro, `restore nada`, `snapshot before 2000-01-01`. Mundos temporales hechos en 37–138 ms; las 5 carpetas `worldcraft_regen_*` borradas (la última al apagar). Logs sin errores del plugin en los dos arranques (el «No key layers» del mundo plano es del juego, ya filtrado) |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **41/41 dos veces seguidas** (39 de antes + 2 nuevos), después de arreglar F-01 y F-02; tick más largo de la obra de 36 000 bloques 8,5 ms. Una primera corrida dio 40/41: la 033 falló por el desgaste conocido del pasto (F-02) |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1 | 3a (la piedra en el aire vuelve a ser aire; con el suelo, pasto, tierra y roca madre; `--biomes` devuelve la llanura); compuerta 2 |
| H1 CA2 | 3a (`regen 42 desert`: la semilla y el bioma en el mensaje y el desierto escrito; «Unknown biome 'desrt' … did you mean 'desert'»); compuerta 2 (`regen 0`, `regen desert`, `regen 30000`); `SnapshotTest` (semilla) |
| H1 CA3 | 3a (`undo` de cada `regen` trae la piedra y el oro); el motor de siempre (`Edits.request`: límites, `gmask`, vista previa, historial) |
| H1 CA4 | Compuerta 2 (un mundo temporal por semilla, reutilizado, descargado y borrado; al apagar también); `SnapshotTest` (solo carpetas con la marca) |
| H2 CA1 | 3a (una carpeta con la fecha en el nombre y un `.zip` con una carpeta de más); `SnapshotTest`; compuerta 2 |
| H2 CA2 | 3a (`list` con las dos y sus fechas, `use` con «did you mean», `before`, `after`, `after` sin copia, `sel 1`); compuerta 2 |
| H2 CA3 | 3a (`restore` sin nombre usa la elegida con `sel`; con un nombre, esa) ; compuerta 2 (sin elegir, la más nueva) |
| H3 CA1 | 3a (99 de piedra y el cofre con sus 5 diamantes vuelven desde la carpeta y desde el `.zip`); compuerta 2 (`--biomes`); `SnapshotTest` (cilindro, datos de bloque) |
| H3 CA2 | `SnapshotTest` (sin terminar, viejo, LZ4, desconocido se deja); compuerta 2 (el `.mca` que escribió Paper igual al mundo) |
| H3 CA3 | 3a (`restore … --preview`: el cofre sigue siendo aire hasta `confirm`; `undo`); el motor de siempre |
| H3 CA4 | `SnapshotTest` (bombas, regiones enormes, archivos rotos: un mensaje, nada cambia) |
| H4 CA1 | 3a (Beto: «You don't have permission» en `regen` y en `snapshot list`); `plugin.yml` (`default: op`) |
| H4 CA2 | `ConfigTest.spec036…`; 3a (apagado de fábrica: «Backups are off on this server»); compuerta 2 («Configuration OK») |
| H4 CA3 | `CommandTest.spec036…` (ayuda con ejemplo de los 3); autocompletado en el código (`RestoreCommands.complete`: copias de la última lista, `latest`, `random`, biomas, opciones) |
| H4 CA4 / README | README de Worldcraft (sección *Back to the original terrain, or to a backup*, dos líneas arriba, «Coming from WorldEdit» con dos filas, configuración, cómo actualizar desde la 0.11.0, permisos `worldcraft.regen` y `worldcraft.snapshot`, compatibilidad 0.12.0, «Not yet» sin `//regen` ni snapshots), README raíz (fila 0.12.0), tabla de paridad (5 filas), roadmap, lección en `CLAUDE.md`. Sin capturas nuevas |

## Hallazgos
| # | Qué pasó | Arreglo | Prueba |
|---|---|---|---|
| F-01 | Armado de la suite (no del plugin): el suelo de prueba de `regen` (x = 104) quedaba lejos de los bots y `fill`, `fillbiome` y `execute if biome` decían «That position is not loaded» (se repitió la 022) | `forceload add` del suelo al empezar y `forceload remove` en el `finally` | Sin `forceload`, el escenario falla en `execute if biome … desert` («Test failed»); con él, en verde |
| F-02 | Armado de la suite (el riesgo anotado en la 035): escenarios anteriores a la 035 gastan despacio el pasto del mundo plano; en la primera corrida completa un pasto de x 40–51 ya era tierra y la 033 contó 47 en vez de 48 (y, al fallar a la mitad, dejó arena en el suelo) | `antes` de la suite repara la capa y = -61 de x, z 10–110 (`forceload add` + `fill … grass_block replace dirt` y `replace sand`) | Sin la reparación, 40/41 (la 033 falla); con ella, 41/41 dos veces seguidas |

## Riesgos
- Crear el mundo temporal **frena el servidor una vez** 40–140 ms (medido): no hay forma de crear un mundo por partes con
  la API. Se reutiliza `regen.keep_seconds` para las siguientes con la misma semilla. FAWE hace lo mismo.
- El mundo temporal dispara los eventos de mundo nuevo: un plugin que administra mundos (Multiverse) o dibuja mapas lo
  puede ver; el README pide ignorar `worldcraft_regen_*`. `WorldCreator.copy` no copia los ajustes de un mundo plano
  propio (`generator-settings`) ni los presets `amplified`/`large_biomes` (Paper solo dice `NORMAL` o `FLAT`): ahí `regen`
  da el terreno por defecto de ese tipo.
- Un mundo con un *datapack* de generación (Terralith…) regenera con lo que el *datapack* define para todo mundo nuevo.
- `restore` solo lee copias desde 1.18 sin LZ4 y no trae las criaturas de la copia (al roadmap).
- Paper 26.1.2 sin verificar.

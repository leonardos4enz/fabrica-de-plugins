# Verify 033 — Biomas, árboles y estructuras del juego (Worldcraft 0.9.0)

**Fecha:** 2026-11-02 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar (como la
025–032). Alcance elegido por el dueño en el CLARIFY: todo menos los *features*; biomas solo con `--biomes`; estructuras
del juego al portapapeles.

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew :worldcraft:test`: **222** en verde (201 de la 032 + 21 nuevas: `BiomeTest` 5, `BiomeMaskTest` 3, `SchemBiomesTest` 4, `NatureTest` 6, `ToolSpecTest` 1, `ConfigTest` 1, `CommandTest` 1). Celdas de una caja y de una esfera (contadas a mano), `setbiome` por el motor en memoria con diario, deshacer seguro que salta la celda que otro cambió, rehacer, un diario de la 0.8.0 sin biomas; el portapapeles con biomas girado y horneado, pegar eligiendo cada celda por su centro; `$desert` solo, combinado, negado, sin buscador, el preset que gana y «did you mean»; `.schem` v3 ida y vuelta, v2 por columna, paleta dañada, sin biomas no escribe `Biomes`; lugares de árboles solo en el suelo con la densidad y `max_trees`, flora por suelo y el cactus con los costados libres, matas de calabazas, árboles juntos (el tronco gana), nombres de WorldEdit, plantillas (`jigsaw` → aire, `structure_void` sin pegar, nombres seguros) |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24, con WorldEdit 7.3.19), con un `config.yml` sin la sección `nature` (como uno de la 0.8.0): «Configuration OK». `selftest` con el **registro real**: 65 biomas, un roble de 63 bloques **capturado sin tocar el mundo**, la plantilla `igloo/top` (7×5×8) y 1201 plantillas listadas del jar. Desde la consola: `setbiome cherry_grove` en 12×12×12 = 36 celdas, `execute if biome` lo confirma en el centro, `biomeinfo` 100 %, `undo` lo devuelve; `structure load` de casas de aldea, torre de saqueadores, barco hundido (8 paletas), portal en ruinas (cofres con botín). Logs sin errores en los dos arranques |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **33/33 dos veces** (29 de antes + 4 nuevos), tick más largo de la obra de 36 000 bloques 8 ms |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1–CA2 | 3a (`biomelist 1 cherry`, `biomeinfo` de la selección con 1 728 bloques, `--here`); compuerta 2 |
| H2 CA1 | 3a (27 celdas de 4×4×4, «did you mean 'desert'»); `BiomeTest.H2_CA1…` |
| H2 CA2 | 3a (`undo`, `redo`; el bot recibe el bioma nuevo sin salir: el id pasa de 40 a 14); `BiomeTest.H2_CA2…` (deshacer seguro) |
| H2 CA3 | `NatureCommands.setBiome` (la celda por su bloque central con la máscara global); sin prueba en el juego |
| H3 CA1–CA2 | 3a (`replace $desert and grass_block with sand` cambia 48 bloques, solo en las celdas de desierto); `BiomeMaskTest`; `ConfigTest.spec033…` (aviso del preset) |
| H4 CA1–CA2 | 3a (sin `--biomes` el oro llega sin el desierto; con `--biomes`, con él; `undo`); `BiomeTest.H4…` (girado) |
| H4 CA3 | 3a (`schem save` con biomas, `schem load` lo dice, `paste --biomes`; **WorldEdit 7.3.19 pega el esquema de Worldcraft con `//paste -b`** y el desierto llega); `SchemBiomesTest` (v2 y v3) |
| H5 CA1–CA6 | 3a (`forest birch 15 --preview`: la vista previa no pone troncos, `confirm` los pone con hojas, `undo` los quita; `forestgen 6 spruce 20`; `tool tree cherry` pone un cerezo sobre el bloque que mira; `brush forest oak 4 60`; «did you mean 'birch'»); `NatureTest`; compuerta 2 (captura real) |
| H6 CA1–CA3 | 3a (`flora 60`, `pumpkins 8 10` con hojas, `undo`); `NatureTest.H6…` |
| H7 CA1–CA3 | 3a (`structure list igloo` = 3, `load igloo/top` 7×5×8 pegado con su nieve y `undo`, «did you mean 'igloo/top'», `ruined_portal/portal_1` con el cofre y su `LootTable` `chests/ruined_portal`); `NatureTest.H7…` |
| H8 CA1–CA2 | `ConfigTest.spec033…`; `CommandTest.spec033…` (ayuda de los 8 subcomandos); errores con sugerencia en la 3a |
| H9 | README de Worldcraft (sección *Biomes, trees and the game's structures*, ejemplos de arriba, «Coming from WorldEdit», configuración `nature`, cómo actualizar desde la 0.8.0, compatibilidad 0.9.0), README raíz, tabla de paridad, roadmap |

## Hallazgos
| # | Qué pasó | Arreglo | Prueba |
|---|---|---|---|
| F-01 | `/wc schem save` no guardaba los biomas: arma un portapapeles nuevo (con los datos de bloque en la forma estándar) y no le pasaba los biomas; `schem load` no decía «It has biomes» | `Schematics.save` pasa los biomas a esa copia | 3a `033 H4` (falló con el código anterior: «no recibió /It has biomes/») |
| — | Armado de la suite: `execute if biome` en el borde de una celda daba el llano con el desierto puesto. `getBiome` de Bukkit lee la celda exacta; el comando y el cliente usan el «zoom difuso» del juego, que cerca del borde toma la vecina (WorldEdit igual) | Las pruebas miran el centro de una zona de 3×3×3 celdas | — (lección en `CLAUDE.md`) |
| — | mineflayer 4.39 no nombra los biomas de 1.21.4 (`biome.name` vacío) | La 3a compara el id que recibe el bot | — (lección en `CLAUDE.md`) |
| — | El cofre de la plantilla del iglú no trae botín: el juego se lo pone desde un marcador de datos al generar la estructura | Los marcadores (`structure_block[mode=data]`) quedan como aire; la prueba usa `ruined_portal/portal_1` | 3a `033 H7` |

## Riesgos
- Las plantillas no tienen API de lista: se leen del jar del servidor y de los paquetes de datos de los mundos. Un
  servidor que cargue el juego de otra forma muestra solo las que conoce el `StructureManager` (cargar por nombre sigue
  funcionando).
- Los árboles capturados ven el mundo de antes: dos que se tocan pueden quedar mezclados (el tronco gana a la hoja).
  WorldEdit los pone de a uno.
- `setbiome` sin vista previa (el cliente no la puede mostrar sin reenviar el chunk); la vista previa de `paste --biomes`
  muestra solo bloques y lo dice.
- Los *features*, las estructuras completas y las entidades de las plantillas: al roadmap.

## README
README de Worldcraft (sección *Biomes, trees and the game's structures*, ejemplos, «Coming from WorldEdit»,
configuración `nature`, actualizar desde la 0.8.0, compatibilidad 0.9.0) y README raíz (fila de Worldcraft, 0.9.0).
Sin capturas nuevas.

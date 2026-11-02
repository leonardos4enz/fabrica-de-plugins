# Verify 031 — Entidades en Worldcraft (0.7.0)

**Fecha:** 2026-11-02 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar (como la
025–030). Alcance elegido por el dueño en el CLARIFY: todo (copiar/pegar/`stack`/`move`, deshacer, `butcher`/`remove`,
`.schem`); `copy` sin entidades salvo `--entities`; `butcher` como WorldEdit; `worldcraft.butcher` y `worldcraft.remove`.

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew :worldcraft:test`: **188** en verde (175 de la 030 + 13 nuevas: `EntityTest` 5, `EntityEditTest` 5, `SchemEntitiesTest` 2, `ConfigTest` 1). Mundo de entidades en memoria (`MemoryEntities`): orden quitar → bloques → crear, diario con `ENTITY_OUT`/`ENTITY_IN`/`REMAP`, deshacer → rehacer → deshacer siguiendo el uuid, la creada que ya no está se salta y se cuenta, tope y chunks que tardan, un diario de la 030 que se sigue leyendo |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24, con WorldEdit 7.3.19), actualizando desde la 0.6.0 (`config.yml` sin las claves nuevas: «Configuration OK»). `selftest` con entidades de verdad: un soporte con casco y nombre pasa por un esquema (NBT sin uuid y con `DataVersion`) y vuelve entero; un marco girado 90° queda en la pared este con su manzana; un cerdo con una gallina montada se copia y se crea como una sola entidad, y quitarlo quita a la gallina. Desde la consola (con `spawn-monsters` activado un momento): `butcher` quita un husk y una araña y salva al husk «Bob» y a la vaca; `undo` los devuelve; `--named` se lleva a Bob; `--friendly` a la vaca, el aldeano y el lobo de alguien; `remove cow` y «did you mean 'cow'»; `copy --entities` (4), `paste --remove-entities` (4 quitadas y 4 creadas), `paste`, `undo 2`; un portapapeles girado con entidades guardado, cargado («4 entities come with it») y pegado, y `undo`. Logs sin errores en los arranques |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **25/25 dos veces** (22 de antes + 3 nuevos), con WorldEdit 7.3.19: `copy` sin `--entities` no copia ninguna; con `--entities`, girado y pegado, el marco cuelga de la pared girada (`Facing` 5b) con su manzana, el soporte conserva el casco, la vaca en su lugar; `undo` quita las tres; `cut --entities` sin que el marco caiga como ítem y `undo`; `--no-entities`; `move --entities` (el mismo soporte, con su nombre) y `stack --entities`; `paste --remove-entities`; `butcher` no toca al lobo de alguien, al aldeano ni a la vaca, `--animals` se lleva la vaca y `undo` la devuelve; `remove itms` sugiere `items`; Beto sin `worldcraft.butcher`; autocompletado de `--friendly`; un esquema con entidades guardado por Worldcraft lo pega WorldEdit con `//paste -e` y uno de `//copy -e` lo pega Worldcraft |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1–CA3 | `EntityTest.H1_CA3…` (girar, cuatro giros, voltear, la pared); `EntityEditTest.H1_CA3…` (portapapeles); compuerta 2; 3a |
| H1 CA4 | compuerta 2 y 3a (`paste --remove-entities`) |
| H1 CA5 | compuerta 2 (`selftest`: el cerdo y su gallina) |
| H1 CA6 | `preview-entities` (la vista previa dice cuántas; no las muestra) |
| H2 CA1–CA2 | `EntityEditTest.H2_CA2…` (orden y misma entidad); 3a |
| H3 CA1–CA3 | `EntityEditTest.H2_CA2…`, `H3_CA2…`, `H3_CA1…` (diario viejo, `reopen`); compuerta 2; 3a |
| H4 | `EntityTest.H4_CA2…` (cada opción, nunca jugadores); `EntityEditTest.H4_CA1…` (región, tope, chunk lento); compuerta 2; 3a |
| H5 | `EntityTest.H5_CA1…`; compuerta 2; 3a |
| H6 | `SchemEntitiesTest` (v3 ida y vuelta girado, v2 de WorldEdit con lo montado); compuerta 2; 3a con WorldEdit en los dos sentidos |
| H7 | `ConfigTest.spec031…` (tope, prohibidos, la lista del grupo reemplaza la de `default`); `SchemEntitiesTest` (`contains` con lo montado); `/wc limits` en la compuerta 2 |
| H8 | README de Worldcraft (sección *Entities*, «Coming from WorldEdit», permisos, configuración, compatibilidad), README raíz, tabla de paridad, roadmap |

## Hallazgos
| # | Qué pasó | Arreglo | Prueba |
|---|---|---|---|
| F-01 | WorldEdit pegaba el marco de un esquema de Worldcraft en y = -120: pega un colgante con la diferencia entre `Data.Pos` y `TileX/Y/Z`, y Worldcraft quitaba `Pos` (WorldEdit leía 0 y sumaba el origen dos veces) | `Data.Pos` y `TileX/Y/Z` (o `block_pos`) relativos a quien copió y coherentes | `SchemEntitiesTest.H6_CA1…`, **falló con el código anterior** |
| F-02 | Al cargar un esquema, `Pos` iba relativa y `TileX/Y/Z` quedaba del mundo de quien lo guardó: Paper avisaba «Block-attached entity at invalid position» | El bloque del colgante se pone junto a la `Pos` nueva | `SchemEntitiesTest.H6_CA2…`, **falló con el código anterior** |
| — | El mismo aviso aparece al pegar con WorldEdit 7.3.19 **su propio** esquema con un marco a más de 16 bloques (comprobado con un escenario a 30 bloques): WorldEdit carga el NBT antes de moverlo. El marco queda bien | Ruido de terceros en `ruidoDelServidor` de la suite, con su explicación | 3a |
| — | Armado de las pruebas: los zombis se queman al sol, el soporte cae, `spawn-monsters=false` borra los monstruos invocados, `difficulty` desde la consola queda en el mundo, la vaca muerta suelta ítems, `data get entity` pide `limit=1` | Husk y araña con `spawn-monsters` un momento en la compuerta 2 (se devolvió `false` y `easy`); `NoGravity`; criaturas no hostiles en la suite; matar dos veces; `limit=1` | — (lección en `CLAUDE.md`) |

## Riesgos
- `serializeEntity`/`deserializeEntity` son de `UnsafeValues` (marcados obsoletos en Paper): si Paper los cambia, `selftest`
  lo detecta.
- Un cuadro de lado par girado puede quedar un bloque corrido (su bloque se calcula del centro); probado solo con marcos.
- Las etapas esperan hasta 200 ticks un chunk cuyas entidades no cargan; después lo saltan y lo cuentan.

## README
README de Worldcraft (sección *Entities*, filas de `copy`/`cut`/`paste`/`stack`/`move`, «Coming from WorldEdit», `.schem`,
configuración, permisos, compatibilidad 0.7.0) y README raíz (fila de Worldcraft, 0.7.0). Sin capturas nuevas.

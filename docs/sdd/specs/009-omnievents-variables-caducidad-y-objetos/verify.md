# Verify 009 — OmniEvents: variables con caducidad y en bloques, entidades e ítems

**Fecha:** 2026-10-25 · **Versión:** OmniEvents 1.4.0

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ 113/113 (19 nuevas) | `./gradlew build`: `DataStoreTest` (caducidad al leer, `set` sin `expire`, persistencia y descarte al cargar, barrido con entradas viejas y jugador descargado, formato 1 → respaldo único, instalación nueva sin respaldo, formato 3 en solo lectura, caducidad no numérica), `ObjectDataTest` (codificación de nombres en claves de Bukkit con `ñ` y mayúsculas, tipos, largo máximo), `DatosObjetoExprTest` (`X.data.y`, respaldo a la propiedad `data`, `ttl` y sus errores), `VariablesCargaTest` (`expire` en `var` y duraciones inválidas, `set`/`unset` de `.data`, `var_expire` con `variables`, `.data` y `ttl` no deducibles en un `state`) y `SchemaTest` con lo nuevo |
| 2. Servidor real (1.21.4) | ✔ | `compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni reload" "omni stats" "omni reload" "omni stats"`: el primer arranque con los datos de la 1.3.0 del servidor de prueba los respaldó en `data/respaldo-formato1-20261025-013955/`; 27 reglas, 0 errores, 0 avisos; autoprueba 14/14 (dos casos nuevos sobre el PDC real: variables de un ítem con caducidad y de un bloque en el chunk del spawn, que se deja como estaba); dos `reload` con 8 oyentes, 3 temporizadores y 8 oyentes de estado (iguales); el arranque 2 lee los datos y no repite el respaldo; log sin errores |
| 3a. En el juego, Paper 1.21.4 | ✔ 37/37 dos veces | 3 escenarios nuevos (`009 …`) + los 34 anteriores |
| 3a. En el juego, Paper 26.1.2 (Java 25) | ✔ 37/37 dos veces | mismo jar; `--paper 26.1.2 --version-bot 26.1`; también migró los datos de formato 1 de `run-26.1.2/` |

### Escenarios nuevos
| ID | Qué prueba |
|---|---|
| 009 H1 CA1 · H1 CA4 · H1 CA5 · H2 CA1 · H2 CA2 · H2 CA3 | `set … expire: 2s` dos veces: `ttl()` entre 1 y 2 s, el estado que lee la `pvar` entra; `/omni var get` muestra «(caduca en …)»; a los 2 s `var_expire` avisa con `value`, `scope` y `variable`, el estado sale y la variable es nula; `/omni var set … --expire 1s` caduca sin aviso (fuera de `variables`) |
| 009 H3 CA1 · H3 CA2 · H3 CA3 · H3 CA6 | Clic en un bloque de esmeralda guarda `block.data.e2e_dueño`; el segundo clic lo lee; `/omni data bloque` lo muestra; al romperlo la regla de `block_break` aún lo lee y después se borra (un bloque nuevo en el mismo lugar no lo hereda) |
| 009 H3 CA1 · H3 CA2 · H3 CA6 | `/ligar` (ejemplo) guarda el dueño en el ítem de la mano; `/omni data mano` lo muestra; la espada copiada a otro jugador con `item replace` lo lleva |

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (`expire`, persistencia, `ttl`, `/omni var`) | `DataStoreTest`, `VariablesCargaTest`, `DatosObjetoExprTest`, escenario de caducidad |
| H2 (`var_expire`, aviso a `state`, cola sin sondeo, desconectados) | `DataStoreTest` (barrido), escenario de caducidad; compuerta 2 (`/omni stats`: caducidades en cola) |
| H3 (`.data` en bloques, entidades e ítems; limpieza de bloques; límites; `/omni data`) | `ObjectDataTest`, `DatosObjetoExprTest`, `VariablesCargaTest`, autoprueba sobre el PDC real, escenarios de bloque e ítem |
| H4 (migración con respaldo, formato más nuevo) | `DataStoreTest`; compuerta 2 y suite en 26.1.2 con datos reales de la 1.3.0 |
| H5 (documentación generada) | `SchemaTest` (esquema y referencia al día; `variables.yml` válido contra el esquema y sin avisos) |

No se probaron en el juego (cubiertos por unitarias o sin bot que los ejerza): variables en entidades que no son
jugadores (mismo camino que los jugadores: `getPersistentDataContainer()`), `var_expire` de una `gvar`, la limpieza
por explosiones, fuego, pistones y criaturas (mismo `clearBlock` que romper) y los límites `max-vars`/`max-text-length`
en un objeto real (unitaria del texto). Riesgo bajo.

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| — | — | Ninguno en las compuertas. | — |

## Revisión exploratoria (3b, agente)
- Compatibilidad hacia atrás: `X.data.y` sobre algo que no es bloque, entidad ni ítem sigue leyendo la propiedad
  `data` (unitaria con un mapa); los 34 escenarios anteriores pasan sin cambios.
- Rendimiento: sin reglas que escriban `.data` la limpieza de bloques no se registra (`/omni stats`: «apagada» en la
  compuerta 2, con los ejemplos viejos del servidor de prueba); el barrido de caducidad mira la cola una vez por
  segundo y sin nada vencido cuesta un `peek`.
- Seguridad: `/omni data` con `omnievents.command.data` (`op`); los textos guardados en un objeto tienen tope;
  README advierte que un jugador en creativo puede fabricar ítems con cualquier dato.
- Ítems: escribir en el `item` de un evento que es copia del inventario se pierde sin aviso; documentado en el
  README (usar `player.item`). Queda como mejora posible avisar cuando el ítem no es el del inventario.

## README
- `plugins/omnievents/README.md`: sección «Variables que caducan y variables de objetos» (ejemplo, `expire`, `ttl`,
  `var_expire`, `.data`, limpieza de bloques, creativo, `/omni data`, `config.yml`, actualizar desde 1.3.0),
  variables y funciones en «Expresiones», comandos y Compatibilidad (1.4.0 verificada en 1.21.4 y 26.1.2).
- `README.md` raíz: fila de OmniEvents en 1.4.0. Roadmap e índice (`docs/sdd/README.md`) con la 009.
- Capturas: ninguna nueva (CLARIFY 7).

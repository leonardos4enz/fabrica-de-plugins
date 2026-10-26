# Verify 015 — OmniEvents: paridad con ConditionalEvents, segunda parte

**Fecha:** 2026-10-25 · **Versión:** OmniEvents 1.10.0

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ 191/191 (9 nuevas) | `./gradlew build`: `ParidadCe2Test` (6: casillas por nombre y número con «¿quisiste decir?»; claves nuevas de `give_item`, `remove_item` con filtros y `remove_item_slot` en `tests:`, y sus errores de carga con ruta; `summon` en mapa, `set_block` con datos, `particle`/`sound` con `location`, `stop_sound` con claves, `set_caught_item`/`set_item` y `keep_inventory` con modo; `json_message` propia con el texto del jugador escapado y JSON inválido como error de carga; los cuatro disparadores nuevos en `tests:` con sus raíces y `tab_complete` mal escrito; `tab_complete` como CE; registro), `ExpressionTest.S015_*` (`pick`, `is_number`) e `ImporterTest.S015_*` (una configuración de CE con cada fila nueva: carga sin errores y solo `datavalue:` queda REVISAR). El ejemplo `estilo_ce.yml` suma 3 reglas con `tests:` (`EjemplosPruebasTest`) y valida contra el esquema regenerado |
| 2. Servidor real (1.21.4) | ✔ | `compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni test" "omni pack list" "omni menu list" "omni reload" "omni stats" "omni reload" "omni stats"`: 57 reglas (las 3 del ejemplo nuevo), 0 errores, 0 avisos; autoprueba 14/14; «32 pruebas: todas pasan»; `menu list`: «Menús (2, 0 abiertos)»; dos `reload` con 14 oyentes (dos nuevos: `player_statistic` y `player_tab_complete`), 3 temporizadores, 8 oyentes y 1 tarea de estado, 12 comandos (`/reglas`), 5 oyentes de regiones y 5 de menús (iguales); segundo arranque igual; log sin errores |
| 3a. En el juego, Paper 1.21.4 (build 232, Java 24) | ✔ 55/55 dos veces | 6 escenarios nuevos (`015 …`) + los 49 anteriores |
| 3a. En el juego, Paper 26.1.2 (build 74, Java 25) | ✔ 55/55 dos veces | Mismo jar |

### Escenarios nuevos
| ID | Qué prueba |
|---|---|
| 015 H1 CA1 · H1 CA2 · H1 CA3 | Ana recibe «e2e libro» en la casilla 3 de la barra y un casco de hierro puesto con 50 de daño; de 5 papeles (2 «e2e ticket»), `remove_item … name:e2e ticket` quita solo los 2 con nombre y no toca las esmeraldas; `remove_item_slot: "3;1"` vacía la casilla del libro |
| 015 H2 CA1 | `summon` en mapa: hay 2 soportes «e2e_guardian» (`execute if entity`), con `Health` 40 (`data get`), y el bot ve el casco de diamante y la espada |
| 015 H3 CA1 | `json_message` llega como «e2e json de Ana_e2e» (la expresión dentro del JSON resuelta) |
| 015 H4 CA1 · H4 CA2 | Tras «e2e espera 1», `omni interrupt e2e_espera015 Ana_e2e` responde «Cortadas 1…» y «e2e espera 2» no llega en 5 s; sin cortar sí llega; `omni call … -s` corre la regla sin responder y `omni interrupt * -s` tampoco responde |
| 015 H5 CA1 | Con `pvar.e2e_saltos`, el bot salta y la regla `player_statistic` dice «e2e salto 1» |
| 015 H5 CA3 | El bot pide autocompletar `/e2e e2e_tab` y recibe solo `e2e_tab_alfa` y `e2e_tab_beta` (la lista de `tab_complete: set;…` filtrada por lo escrito) |

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (ítems) | Escenario 015 H1, `ParidadCe2Test.H1_*`; `set_caught_item` (CA4) solo con unitaria (carga y `set_item` como alias) |
| H2 (mundo) | Escenario 015 H2 (CA1), `ParidadCe2Test.H1_CA4_H2_*` (CA2–CA5: carga y errores) |
| H3 (mensajes, funciones, placeholder) | Escenario 015 H3, `ParidadCe2Test.H3_CA1_*`, `ExpressionTest.S015_*`; `%omni_enabled_r%` (CA5) y `block_at` (CA3) por revisión de código |
| H4 (`interrupt`, `-s`) | Escenario 015 H4 |
| H5 (disparadores) | Escenarios 015 H5 CA1 y CA3, `ParidadCe2Test.H5_*` (los cuatro en `tests:`) |
| H6 (importador, registro) | `ImporterTest.S015_*`, `SchemaTest`, `EjemplosPruebasTest`, README |

No se probó en el juego: `set_caught_item` (pescar con el bot), `player_air_change` e `item_mend`, `block_at`,
`skull_owner`/`skull_texture`, `particle`/`sound` con `location` y `%omni_enabled_r%` (PlaceholderAPI no está en la
suite). Riesgo bajo: cargan y corren en `tests:`, y usan la API de Bukkit directamente.

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Baja (arnés) | `./gradlew build` terminaba con `OutOfMemoryError` en el JVM de pruebas. Dos causas: el ejemplo de `particle` (`force: false`) no validaba porque el esquema solo admitía texto o número en las claves hermanas, y `SchemaTest` armaba el mensaje con los miles de errores del `anyOf` de acciones; además, con las acciones nuevas el validador ya no entra en 512 MB | El esquema admite booleanos en las claves hermanas; `dump()` muestra 30 mensajes y «… y N más» (comprobado: con el generador anterior la prueba falla legible, sin OOM); `maxHeapSize = "1g"` para las pruebas en `build.gradle.kts` |
| F-02 | Media | `summon` con `equipment` sobre una entidad que no es `Mob` (un soporte de armadura) fallaba entero: «Cannot set drop chance for non-Mob entity» | Solo se fija la probabilidad de soltar en un `Mob`. El escenario 015 H2 falló con el código anterior (corrida 1 de 1.21.4) y pasa con la corrección |
| F-03 | Baja (arnés) | En 26.x `execute if entity` responde «Test passed. Count: 2» (en 1.21.4, «Test passed, count: 2») | El escenario acepta las dos formas |

## Revisión exploratoria (3b, agente)
- **Compatibilidad (IV.5):** una configuración de la 1.9.0 carga igual (los 49 escenarios anteriores pasan). Cambio de
  comportamiento: `json_message` ya no es un alias de `message`; un `json_message` con texto que no es JSON ahora es
  error al cargar con la ruta. `keep_inventory: items|xp|all` es nuevo; antes esos textos eran una expresión inválida.
- **Seguridad:** los `{…}` de `json_message` se escapan como en `message` (unitaria con `<red>` del jugador);
  `skull_owner` solo acepta un nombre de jugador válido y OmniEvents no hace red (el perfil lo resuelve el servidor);
  `/omni interrupt` pide `omnievents.command.interrupt` (`op`); `-s` solo quita la respuesta (la consola registra el comando).
- **Rendimiento:** `summon` tiene tope de 50 por acción; `block_at` no carga chunks y solo corre en el hilo principal;
  `player_statistic` y `player_air_change` solo escuchan si una regla los usa (la referencia avisa que el aire cambia
  cada tick bajo el agua). El registro de ejecuciones en espera es concurrente (un `delay` puede programarse desde `player_chat`).
- **Jar de CE:** el mismo de la 014; de él salen las decisiones de la spec (CE no repara por Mending; `player_change_air`
  declarado y nunca disparado; claves exactas de `particle`, `summon`, `tab_complete`, `remove_item_slot`).

## README
- `plugins/omnievents/README.md`: specs, funciones nuevas, acciones y disparadores nuevos, `give_item` con casilla y
  `summon` en mapa con ejemplos, tabla «Desde ConditionalEvents» al día, `/omni interrupt` y `-s`,
  `%omni_enabled_<regla>%`, Compatibilidad (1.10.0).
- `README.md` raíz: fila de OmniEvents en 1.10.0. Roadmap (fila 015, «segunda parte» hecha) e índice. Esquemas y
  referencia regenerados con `./gradlew :omnievents:schema`.
- Capturas: ninguna nueva.

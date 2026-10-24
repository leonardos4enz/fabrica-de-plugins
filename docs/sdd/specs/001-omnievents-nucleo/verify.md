# Verificación 001 — OmniEvents: núcleo

**Fecha:** 2026-10-24 · **Resultado:** Compuertas 1 y 2 aprobadas · compuerta 3 (revisión en el juego) pendiente

| Compuerta | Resultado |
|---|---|
| Unitarias | **38/38** (`ExpressionTest` 13, `RuleLoaderTest` 14, `TemplateTest` 8, `ImporterTest` 2, `RuleCommandsTest` 1). `gradlew build` sin errores |
| Servidor real | **Aprobada** en Paper 1.21.4 build 232 (sha256 verificado), Java 24, EULA aceptado por el dueño (2026-10-24). Ver detalle abajo |
| Revisión exploratoria | **Pendiente:** necesita un jugador conectado (acciones sobre el mundo, chat, combate) |

## Compuerta 2 — servidor real
Tres corridas con `tools/compuerta-servidor.sh` (logs en `plugins/omnievents/run/run*.log`, fuera del repositorio).

| Comprobación | Resultado |
|---|---|
| Carga | `20 reglas cargadas, 8 oyentes, 3 temporizadores, 2 comandos (0 errores, 0 avisos)`; `server_start` corrió (`Arranque número 1`) |
| `/omni validate` | 0 errores, 0 avisos |
| `/omni selftest` | 12/12 y validate 0/0 |
| `reload` ×2 | 8 oyentes, 3 temporizadores y 2 comandos antes y después de cada uno (sin fugas); 9.7–15.3 ms |
| Persistencia | `gvar.arranques` pasó de 1 a 2 al reiniciar; `global.prueba = 10` sobrevivió al reinicio |
| Comandos de reglas | `monedas` y `spawnvip` desde la consola responden «Solo los jugadores…» (rama `if player == null`) |
| `call`, `eval`, `var`, `info`, `list`, `triggers`, `stats` | Responden correctamente; `stats` muestra tiempos por regla (0.4–5 ms) |
| `debug all` | Traza con la condición explicada: `player (nulo) == nulo → true` y cada acción |
| Importador | El ejemplo del wiki de ConditionalEvents se importó, validó 0/0 y cargó (21 reglas, 9 oyentes) |
| Errores del servidor | Ninguno de OmniEvents en la última corrida (el `No key layers` es de Paper, no del plugin) |

## Cobertura
| Criterio | Prueba |
|---|---|
| H1 CA1 nombres únicos | `RuleLoaderTest.H1_CA1_nombre_repetido_nombra_los_dos_archivos` |
| H1 CA3 recarga | `H1_CA3_un_archivo_roto_no_tumba_a_los_demas`; servidor: `reload` ×2 sin fugas y sin perder datos |
| H1 CA4 ejemplos | `H1_CA4_los_ejemplos_cargan_sin_errores_ni_avisos`; servidor: carga 0/0 |
| H2 CA1–CA4, CA7 lenguaje | `ExpressionTest.H2_*`; `selftest` en el servidor |
| H2 CA5 errores con columna | `ExpressionTest.H2_CA5…`, `RuleLoaderTest.H2_CA5…` |
| H3 CA2–CA4 | `ExpressionTest.H3_*`, `RuleLoaderTest.H3_CA4…`; servidor: `eval server.name` por reflexión |
| H4 CA1 control de flujo | `RuleLoaderTest.H4_CA1…`; servidor: `if` + `stop` en `monedas` y `spawnvip` |
| H4 CA3–CA6 | `RuleLoaderTest.H4_CA4…`, `TemplateTest.H4_*`; `selftest` |
| H5 CA6 | `RuleLoaderTest.H5_CA6…` |
| H6 CA3 comandos | servidor: `monedas`, `spawnvip` registrados y ejecutados |
| H6 CA5 oyentes bajo demanda | servidor: `stats` lista solo los 8 eventos que usan las reglas; 9 al importar una regla de `player_command` |
| H7 CA1–CA2, CA4 variables | servidor: `var set/get global`, persistencia tras reinicio |
| H8 CA1–CA3 | `RuleLoaderTest.H8…`; servidor: F-05 |
| H9 CA1–CA4 | servidor: `debug`, `eval`, `stats`, `validate` |
| H10 importador | `ImporterTest.*`; servidor: `/omni import conditionalevents` |
| H11 comandos | servidor: todos los subcomandos de consola |
| H5 CA1–CA5, H6 CA1 (eventos de jugador), H7 CA3 (PlaceholderAPI), H12 | Pendientes de la revisión en el juego |

## Hallazgos
| ID | Severidad | Descripción | Evidencia | Prueba de regresión | Estado |
|---|---|---|---|---|---|
| F-01 | Media | `World` es `Keyed` en Paper: `player.world.name` devolvía `OVERWORLD` | Revisión de código | `ExpressionTest.un_mundo_no_se_convierte_en_valor_de_registro` (falla sin el arreglo) | Resuelto |
| F-02 | Baja | El lexer quitaba la barra de escapes desconocidos (`\b` → `b`) y rompía las regex | Ejemplo `chat.yml` | `ExpressionTest.H2_CA1…` (`matches`) | Resuelto |
| F-03 | Alta | `/omni reload` fallaba con `UnsupportedOperationException` al quitar los comandos de reglas (el mapa de Paper no permite borrar desde el iterador) y dejaba el plugin **sin oyentes ni temporizadores** | Servidor, corrida 1: `stats` con 0 oyentes después del reload | `RuleCommandsTest.quitar_comandos_de_un_mapa_que_no_permite_borrar_desde_el_iterador` (reproduce el fallo del código anterior); además, `reload` ahora registra cada servicio por separado | Resuelto |
| F-04 | Alta | Al apagar, la misma excepción impedía guardar: **se perdían variables, enfriamientos y usos** | Servidor, corrida 1: no existía `data/global.yml` | Servidor, corrida 2: `global.yml` guardado y `arranques` = 2 al reiniciar. `onDisable` guarda siempre, en un `finally` | Resuelto |
| F-05 | Media | Un enfriamiento por jugador usado desde la consola se guardaba como **global** y bloqueaba a todos los jugadores | Servidor, corrida 2: `cooldowns: spawn_vip` en `global.yml` | Servidor, corrida 3: dos `spawnvip` seguidos desde la consola corren y `global.yml` no tiene enfriamientos | Resuelto |

## Riesgos residuales
- Sin jugador conectado no se ejercitaron las acciones sobre el mundo ni los eventos de jugador (compuerta 3).
- `Sound.valueOf` está obsoleto en 1.21.4; con claves (`entity.player.levelup`) funciona aunque se elimine.
- PlaceholderAPI no estaba instalado en el servidor de prueba.
- F-04 y F-05 se verificaron en el servidor y no con una unitaria: necesitarían un servidor simulado (MockBukkit), que se
  propone para una spec de la fábrica.

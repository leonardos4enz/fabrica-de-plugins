# Verify 008 — OmniEvents: disparador de estado

**Fecha:** 2026-10-25 · **Versión:** OmniEvents 1.3.0

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ 94/94 (21 nuevas) | `./gradlew build`: `StateDepsTest` (tabla de dependencias de plan §1.2, azar, no deducibles, uso del jugador; transiciones de `StateTable`), `EstadoCargaTest` (forma, errores, `check_every`, estado del servidor, huella, `in_state` entre reglas, compatibilidad) y `SchemaTest` con las opciones nuevas |
| 2. Servidor real (1.21.4) | ✔ | `compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni reload" "omni stats" "omni reload" "omni stats" "omni info vida_baja"`: 27 reglas, 0 errores, 0 avisos; autoprueba 12/12; dos `reload` con 8 oyentes, 3 temporizadores y 8 oyentes de estado (iguales); el estado de `evento_activo` se conserva (1 revisión antes y después de recargar); `info` muestra «Depende de: vida · Se revisa con: EntityDamageEvent, EntityRegainHealthEvent, PlayerRespawnEvent»; el arranque 2 lee los datos guardados; log sin errores |
| 3a. En el juego, Paper 1.21.4 | ✔ 34/34 dos veces | 7 escenarios nuevos (`008 …`) + los 27 anteriores |
| 3a. En el juego, Paper 26.1.2 (Java 25) | ✔ 34/34 dos veces | mismo jar; `--paper 26.1.2 --version-bot 26.1` |

### Escenarios nuevos
| ID | Qué prueba |
|---|---|
| 008 H1 CA1 · H1 CA2 · H1 CA4 · H2 CA1 · H2 CA2 | Vida: `damage 12` → `on_enter` una vez con la vida ya restada (8); otro golpe no lo repite; curar → `on_exit` con `var.state_seconds` |
| 008 H2 CA1 · H3 CA1 | Zona con `in_area`: teletransporte dentro y fuera |
| 008 H2 CA1 · H4 CA1 · H4 CA2 · H5 CA1 · H5 CA4 | `pvar` cambiada por una acción `set` y por `/omni var`; `/omni info` (dependencias), `/omni check` (sin cambio / dentro), `/omni debug` (`✔ Ana_e2e entra en el estado → on_enter`), `/omni state` e `in_state` |
| 008 H2 CA3 | `check_every: 1s` con `!world.day`: `time set midnight` / `noon` |
| 008 H6 CA1 · H6 CA2 · H5 CA4 | Estado del servidor con `gvar`: un solo `on_enter`/`on_exit` para los dos bots; el mismo valor no repite; `/omni state` |
| 008 H1 CA3 | Pareja: en enfriamiento `on_enter` no corre y su `on_exit` tampoco |
| 008 H3 CA1 | Al entrar al servidor con el estado ya verdadero corre `on_enter`; `initial: silent` no avisa |

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (flanco, pareja, `state_seconds`, errores de forma) | `EstadoCargaTest`, `StateDepsTest` (tabla), escenarios vida y pareja |
| H2 (deducción sin sondeo, una revisión por tick, `check_every`, `watch`, azar) | `StateDepsTest`, `EstadoCargaTest`, escenarios vida, zona, `pvar` y noche; compuerta 2 (`/omni info`) |
| H3 (entrar, salir, recargar, memoria) | `StateTable` en `StateDepsTest`, `EstadoCargaTest` (huella), escenario al entrar, compuerta 2 (estado conservado al recargar) |
| H4 (debug, check, run, stats, cpu) | Escenario `pvar`; compuerta 2 (`stats`) |
| H5 (`in_state`, placeholder, dependencia entre estados, `/omni state`) | `EstadoCargaTest` (`in_state` entre reglas), escenarios `pvar` y servidor |
| H6 (estado del servidor) | `EstadoCargaTest`, escenario servidor |
| H7 (documentación generada) | `SchemaTest` (esquema y referencia al día, ejemplo `estados.yml` válido contra el esquema) |

No se probaron en el juego (cubiertos por unitarias o sin bot que los ejerza): `watch`, el placeholder
`%omni_state_<regla>%` (sin PlaceholderAPI en el servidor de prueba), la dependencia entre estados con `in_state` al
cambiar el otro, el inventario (`has_item`, ítem en mano) y el clima. Quedan como riesgo bajo: usan el mismo camino
de marcar y revisar que los escenarios.

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Media | `when` también quedaba como `conditions` de la regla: al entrar, `RuleManager.run` volvía a evaluarlo (doble costo) y `/omni check` lo listaba dos veces | `RuleLoader` deja `conditions` vacía en una regla `state`. Regresión: `EstadoCargaTest.H1_CA1_regla_state_completa` (falló con el código anterior) |
| F-02 | Media (fábrica) | En Paper 26.1.2 las reglas de juego de la suite (`naturalRegeneration`, `doDaylightCycle`, `doMobSpawning`…) fallan con «Incorrect argument»: el jugador se regeneraba solo y el escenario de vida fallaba («perder otro medio corazón»). Probablemente explica el fallo suelto de `player_attack + set_damage` de la 007 | La suite manda también los nombres de 26.x (`natural_health_regeneration`, `advance_time`, `advance_weather`, `spawn_mobs`, `immediate_respawn`, `send_command_feedback`, `respawn_radius`); cada versión rechaza los de la otra sin efecto. Regresión: el escenario de vida, que falló en 26.1.2 antes del cambio y pasa después. Lección en `CLAUDE.md` |

## Revisión exploratoria (3b, agente)
- `/omni validate` con `when: "perm('vip')"` sin `check_every`: error en `vip.when` que nombra «la función perm()» y
  sugiere `check_every: 5s` (unitaria).
- Rendimiento: sin reglas `state` no hay oyente ni tarea de estado (`register` sale antes de registrar). Con los
  ejemplos, 8 oyentes de estado; el movimiento solo cuenta al cambiar de bloque y varios eventos de un tick se juntan.
- Memoria: la tabla de cada regla se vacía al salir el jugador; no se escribe nada en disco.

## README
- `plugins/omnievents/README.md`: sección «Disparador de estado», opciones, `in_state`, `state` en disparadores y
  comandos, `/omni stats`, placeholder `%omni_state_<regla>%` y Compatibilidad (1.3.0 verificada en 1.21.4 y 26.1.2).
- `README.md` raíz: fila de OmniEvents en 1.3.0. Roadmap e índice (`docs/sdd/README.md`) con la 008.
- Capturas: ninguna nueva (CLARIFY 7).

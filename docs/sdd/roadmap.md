# Roadmap de la fábrica

## Estado
| Spec | Estado |
|---|---|
| 001 OmniEvents: núcleo | Verificado: unitarias 38/38, servidor real y en el juego (3a) aprobados ([verify](specs/001-omnievents-nucleo/verify.md)) |
| 002 Fábrica: pruebas en el juego | Verificado: `npm --prefix tests-e2e test` 21/21 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/002-fabrica-pruebas-en-juego/verify.md)) |
| 004 Fábrica: capturas en el juego | Verificado: `npm --prefix tests-e2e run capturas` genera la [galería de OmniEvents](../omnievents/galeria.md) ([verify](specs/004-fabrica-capturas-en-juego/verify.md)). Desde ahora, capturas solo puntuales (lección del dueño) |
| 005 OmniEvents: enable/disable sin re-sincronizar comandos | Verificado: 1.0.1, F-01 de la 004 resuelto; `npm --prefix tests-e2e test` 22/22 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/005-omnievents-comandos-sin-resync/verify.md)) |
| 006 OmniEvents: herramientas de autor (P1, P2, P4) | Verificado: 1.1.0; 62 unitarias; 27/27 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/006-omnievents-herramientas-de-autor/verify.md)) |

## Por qué empezamos con OmniEvents
OmniEvents es la versión propia de *ConditionalEvents* (Ajneb97): «cuando pase X, si se cumple Y, haz Z». Es el
plugin más general posible y su motor (expresiones, plantillas, acciones, persistencia) es la base que reutilizarán
los siguientes plugins de la fábrica.

## Diagnóstico de ConditionalEvents (lo que hacemos mejor)
| Limitación de ConditionalEvents | Respuesta en OmniEvents | Spec |
|---|---|---|
| Una línea de condición no mezcla `and` y `or`; no hay paréntesis ni `not` | Lenguaje de expresiones completo: `&&`, `\|\|`, `!`, paréntesis, ternario, aritmética, listas, funciones | 001 |
| Las matemáticas en condiciones requieren una opción por evento | La aritmética es nativa del lenguaje | 001 |
| Las acciones son una lista plana; los grupos `execute` emulan un `if` | `if`/`else`, `repeat`, `while` acotado, `for_each`, `random` ponderado, `delay`, `stop`, `call` dentro de las acciones | 001 |
| Sin variables propias (dependen de otros plugins) | Variables temporales, por jugador y globales, persistentes, expuestas a PlaceholderAPI | 001 |
| `one_time` y `cooldown` son fijos (OP los salta, sin ventana de reinicio) | Enfriamientos por jugador o globales y límites de usos con reinicio diario, semanal o por duración; persistentes | 001 |
| Variables de evento fijas por tipo; eventos personalizados con cadenas de métodos | Acceso por propiedades a cualquier objeto (`event.damage`, `player.location.block.type`) con reflexión cacheada y solo de lectura | 001 |
| Comandos propios = interceptar `player_command` | Disparador `command` que registra comandos reales con alias, permiso y autocompletado | 001 |
| Diagnóstico limitado | `/omni debug` muestra en vivo por qué cada condición pasó o falló; `/omni eval`; `/omni stats` con tiempos por regla | 001 |
| Un solo `deny` por evento; para decir qué falta hay que duplicar eventos | `deny` por condición y grupos «al menos N de M» | 006 |
| No se puede probar un evento sin dispararlo | `/omni check` (condiciones con valores, sin efectos), `/omni run --simular` y `/omni debug export` | 006 |
| Sin medición de costo | `/omni cpu` por regla, condición y acción (muestreo) y `budget` con aviso | 006 |
| Errores de configuración poco precisos | Validación al cargar con archivo, ruta y «¿quisiste decir?»; `/omni validate` | 001 |
| Oyentes siempre registrados | Solo se escucha un evento si alguna regla lo usa | 001 |
| — | Importador de configuraciones de ConditionalEvents | 001 |

## Siguientes specs (propuestas, en orden)
Elegidas por el dueño tras la [investigación de plugins exitosos](../investigacion/plugins-exitosos.md) (§6; todas
menos el editor visual), una por sesión. El número de cada una se asigna al abrirla:
1. ~~**OmniEvents: herramientas de autor** (P1, P2, P4)~~: hecha en la [006](specs/006-omnievents-herramientas-de-autor/spec.md).
2. **OmniEvents: esquema JSON y referencia generada** (P3).
3. **OmniEvents: disparador de estado** `trigger: state` con `on_enter` y `on_exit`, sin sondeo (P6).
4. **OmniEvents: variables con caducidad y en bloques, entidades e ítems** (P5).
5. **OmniEvents: pruebas de reglas en el YAML** `tests:` y `/omni test` (P7).
6. **OmniEvents: paquetes, plantillas y versión** (P8).
7. **OmniEvents: integraciones con costos**: Vault, `cost:` todo o nada (P9), WorldGuard y Citizens (abajo).
8. **OmniEvents: menús de inventario y formularios de Bedrock** (P10).

Otras pendientes:
- **Fábrica: servidor simulado** — MockBukkit para probar en unitarias lo que hoy solo se ve en el servidor real (F-04, F-05 de la 001).
  Con la suite en el juego (002) es menos urgente: queda para la persistencia y los casos de bordes.
- **Fábrica: más escenarios en el juego** — las acciones y disparadores sin escenario (riesgos de la 002) y
  PlaceholderAPI con su jar en el servidor de prueba.
- **OmniEvents: integraciones** — WorldGuard (disparadores de región sin plugins extra) y Citizens (NPC); Vault va en la n.º 7.
- **OmniEvents: editor en el juego** — inventario para ver, activar y probar reglas sin editar YAML.
- **OmniEvents: almacenamiento SQL** — MySQL/MariaDB para redes con varios servidores (variables compartidas).
- Después: el siguiente plugin de la fábrica, reutilizando el motor de expresiones de OmniEvents como biblioteca
  común (`libs/`), con su propio ADR.

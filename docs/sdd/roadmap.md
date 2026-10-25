# Roadmap de la fábrica

## Estado
| Spec | Estado |
|---|---|
| 001 OmniEvents: núcleo | Verificado: unitarias 38/38, servidor real y en el juego (3a) aprobados ([verify](specs/001-omnievents-nucleo/verify.md)) |
| 002 Fábrica: pruebas en el juego | Verificado: `npm --prefix tests-e2e test` 21/21 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/002-fabrica-pruebas-en-juego/verify.md)) |
| 004 Fábrica: capturas en el juego | Verificado: `npm --prefix tests-e2e run capturas` genera la [galería de OmniEvents](../omnievents/galeria.md) ([verify](specs/004-fabrica-capturas-en-juego/verify.md)) |

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
| Errores de configuración poco precisos | Validación al cargar con archivo, ruta y «¿quisiste decir?»; `/omni validate` | 001 |
| Oyentes siempre registrados | Solo se escucha un evento si alguna regla lo usa | 001 |
| — | Importador de configuraciones de ConditionalEvents | 001 |

## Siguientes specs (propuestas, en orden)
- **Fábrica: servidor simulado** — MockBukkit para probar en unitarias lo que hoy solo se ve en el servidor real (F-04, F-05 de la 001).
  Con la suite en el juego (002) es menos urgente: queda para la persistencia y los casos de bordes.
- **Fábrica: más escenarios en el juego** — las acciones y disparadores sin escenario (riesgos de la 002) y
  PlaceholderAPI con su jar en el servidor de prueba.
- **OmniEvents: `/omni enable|disable` sin re-sincronizar comandos** — hallazgo F-01 de la 004: cada cambio vuelve a
  registrar los comandos y llama a `syncCommands()`; dos seguidos chocan con el envío asíncrono de Paper.
- **OmniEvents: integraciones** — Vault (economía como funciones `money()` y acciones `give_money`), WorldGuard
  (disparadores de región sin plugins extra), Citizens (NPC).
- **OmniEvents: editor en el juego** — inventario para ver, activar y probar reglas sin editar YAML.
- **OmniEvents: almacenamiento SQL** — MySQL/MariaDB para redes con varios servidores (variables compartidas).
- Después: el siguiente plugin de la fábrica, reutilizando el motor de expresiones de OmniEvents como biblioteca
  común (`libs/`), con su propio ADR.

# Roadmap de la fábrica

## Estado
| Spec | Estado |
|---|---|
| 001 OmniEvents: núcleo | Implementado: unitarias 38/38 y servidor real aprobados; falta la revisión en el juego ([verify](specs/001-omnievents-nucleo/verify.md)) |

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
- **002 OmniEvents: integraciones** — Vault (economía como funciones `money()` y acciones `give_money`), WorldGuard
  (disparadores de región sin plugins extra), Citizens (NPC).
- **003 OmniEvents: editor en el juego** — inventario para ver, activar y probar reglas sin editar YAML.
- **004 OmniEvents: almacenamiento SQL** — MySQL/MariaDB para redes con varios servidores (variables compartidas).
- Después: el siguiente plugin de la fábrica, reutilizando el motor de expresiones de OmniEvents como biblioteca
  común (`libs/`), con su propio ADR.

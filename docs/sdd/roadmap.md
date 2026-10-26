# Roadmap de la fábrica

## Estado
| Spec | Estado |
|---|---|
| 001 OmniEvents: núcleo | Verificado: unitarias 38/38, servidor real y en el juego (3a) aprobados ([verify](specs/001-omnievents-nucleo/verify.md)) |
| 002 Fábrica: pruebas en el juego | Verificado: `npm --prefix tests-e2e test` 21/21 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/002-fabrica-pruebas-en-juego/verify.md)) |
| 004 Fábrica: capturas en el juego | Verificado: `npm --prefix tests-e2e run capturas` genera la [galería de OmniEvents](../omnievents/galeria.md) ([verify](specs/004-fabrica-capturas-en-juego/verify.md)). Desde ahora, capturas solo puntuales (lección del dueño) |
| 005 OmniEvents: enable/disable sin re-sincronizar comandos | Verificado: 1.0.1, F-01 de la 004 resuelto; `npm --prefix tests-e2e test` 22/22 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/005-omnievents-comandos-sin-resync/verify.md)) |
| 006 OmniEvents: herramientas de autor (P1, P2, P4) | Verificado: 1.1.0; 62 unitarias; 27/27 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/006-omnievents-herramientas-de-autor/verify.md)) |
| 007 OmniEvents: esquema JSON y referencia generada (P3) | Verificado: 1.2.0; esquema y [referencia](../omnievents/referencia.md) generados del registro; 73 unitarias; 27/27 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/007-omnievents-esquema-y-referencia/verify.md)) |
| 008 OmniEvents: disparador de estado (P6) | Verificado: 1.3.0; `trigger: state` con `on_enter`/`on_exit` sin sondeo, `in_state`, estados del servidor y `/omni state`; 94 unitarias; 34/34 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/008-omnievents-disparador-de-estado/verify.md)) |
| 009 OmniEvents: variables con caducidad y en objetos (P5) | Verificado: 1.4.0; `expire` y `ttl()`, `trigger: var_expire`, `objeto.data.nombre` en bloques, entidades e ítems (PDC), `/omni data` y migración de `data/` con respaldo; 113 unitarias; 37/37 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/009-omnievents-variables-caducidad-y-objetos/verify.md)) |
| 010 OmniEvents: pruebas de reglas en el YAML (P7) | Verificado: 1.5.0; `tests:` con `given` y `expect` sin servidor ni efectos, `/omni test`, pruebas al cargar y de los ejemplos en gradle; 123 unitarias; 38/38 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/010-omnievents-pruebas-de-reglas/verify.md)) |
| 011 OmniEvents: paquetes, plantillas y versión (P8) | Verificado: 1.6.0; carpetas con `pack.yml` y nombres `paquete:regla` con búsqueda relativa, `template`/`extends` con parámetros `${nombre}`, `requires` y `migrations` al subir de versión (`data/packs.yml`), `/omni pack`; 143 unitarias; 39/39 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/011-omnievents-paquetes-plantillas-version/verify.md)) |
| 012 OmniEvents: integraciones con costos (P9, Vault, WorldGuard, Citizens) | Verificado: 1.7.0; `cost` todo o nada con `on_cost_fail` y devolución, dinero de Vault, `region_enter`/`region_leave` e `in_region()`, `npc_click`/`npc_left_click` e `is_npc()`; 155 unitarias; 42/42 dos veces en Paper 1.21.4 y en 26.1.2 con EssentialsX, WorldGuard y Citizens ([verify](specs/012-omnievents-integraciones-con-costos/verify.md)) |
| 013 OmniEvents: menús de inventario y formularios de Bedrock (P10) | Verificado: 1.8.1 (F-03 y F-04 corregidos tras el cierre); entradas `menu:` con `open_menu`/`close_menu`, `view`, clics como reglas (`conditions`, `cost`, `cooldown`, `tests`), ítems que no se pueden sacar, formulario de botones para Bedrock con Floodgate e `is_bedrock()`, `/omni menu`; 170 unitarias; 45/45 dos veces en Paper 1.21.4 y en 26.1.2 con Geyser, Floodgate y un jugador de Bedrock ([verify](specs/013-omnievents-menus-y-formularios-bedrock/verify.md)) |
| 014 OmniEvents: paridad con ConditionalEvents | Verificado: 1.9.0; tabla completa contra el jar de CE 4.65.1, `centered_message`, `firework`, `item_repair`, permiso `omnievents.bypasscooldown.<regla>` y `cooldown_bypass`, `/omni cooldown|limit reset` con `*`, importador que traduce todo lo que tiene equivalente; 182 unitarias; 49/49 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/014-omnievents-paridad-conditionalevents/verify.md)) |
| 015 OmniEvents: paridad con ConditionalEvents, segunda parte | Verificado: 1.10.0; `give_item` con `slot`, `flags`, `durability`, `item_model`, `skull_owner`; `remove_item` con `name:`/`lorecontains:` y `remove_item_slot`; `summon` en mapa; `json_message` propia; `tab_complete`; disparadores `player_statistic`, `player_air_change`, `player_tab_complete` e `item_mend`; `pick`, `block_at`, `is_number`; `/omni interrupt` y `-s`; `%omni_enabled_r%`; importador sin «REVISAR» salvo lo que no tiene equivalente; 191 unitarias; 55/55 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/015-omnievents-paridad-conditionalevents-2/verify.md)) |
| 016 OmniEvents: paridad con ConditionalEvents, tercera parte (detalles y ProtocolLib) | Verificado: 1.11.0; `actionbar` con duración, `effect` con ícono, `firework` con `shot_direction`; funciones `world`, `location`, `near`, `slice`, `relative`, `looking_block`/`looking_entity`, `is_outside`, `random_last` y `block.states`; el importador traduce todas las variables pedidas (F-01 `%damage%` y F-02 `%block_data%` corregidos); `OmniRuleFireEvent` para otros plugins; `player_receive_chat` con ProtocolLib opcional ([ADR 0004](../adr/0004-protocollib-opcional.md)); 200 unitarias; 57/57 dos veces en Paper 1.21.4 y en 26.1.2 con ProtocolLib ([verify](specs/016-omnievents-paridad-conditionalevents-3/verify.md)) |

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
| Sin ayuda al escribir el YAML; la documentación se queda atrás | Esquema JSON para VS Code (autocompletado y errores) y referencia generados del código, con prueba que falla si se desactualizan | 007 |
| Lo que recibe el jugador en el chat solo se lee con ProtocolLib y una regla por evento | `player_receive_chat` con ProtocolLib opcional: solo escucha si una regla lo usa, texto no confiable y tope contra bucles | 016 |
| Lo que depende de un estado («cuando la vida baja de 3») se sondea con `repetitive` y una variable de «ya avisé» | `trigger: state` con `on_enter`/`on_exit`: el motor deduce de qué eventos depende y solo revisa ahí; `check_every` declarado para lo demás | 008 |
| Variables sin caducidad y solo en el jugador o el servidor; los datos de un ítem o un bloque necesitan otro plugin | `expire:` con aviso `var_expire` sin sondeo, `ttl()`, y `objeto.data.nombre` en el PDC de bloques, entidades e ítems | 009 |
| Una regla se prueba jugando; al actualizar nadie vuelve a probar lo que ya andaba | `tests:` en la regla (`given` y `expect`) que corren sin servidor ni efectos con `/omni test`, al cargar y en gradle | 010 |
| Sin espacios de nombres: dos reglas `diaria` chocan; lo que se repite se copia y pega; una configuración compartida no dice su versión ni qué necesita | Paquetes (`pack.yml`, `paquete:regla`), plantillas con `extends` y `${parámetros}`, `requires` y migraciones al subir de versión | 011 |
| Cobrar algo es encadenar condiciones y comandos de consola: si uno falla a mitad, ya se cobró una parte; las regiones de WorldGuard necesitan otro plugin | `cost:` todo o nada con `on_cost_fail` y devolución (Vault, niveles, ítems, variables), `region_enter`/`region_leave`, `npc_click` | 012 |
| Nadie sabía qué le faltaba a OmniEvents frente a CE; el mensaje centrado, los fuegos artificiales, reparar ítems y el permiso por evento para el enfriamiento no existían | Comparación fila por fila con el jar (51 eventos, 53 acciones), lo que faltaba y un importador que traduce todo lo que tiene equivalente | 014 |
| Sin menús: hace falta DeluxeMenus u otro plugin con su propio lenguaje; los jugadores de Bedrock ven el inventario traducido | `menu:` con clics que son reglas (`cost`, `cooldown`, `tests`) y el mismo menú como formulario nativo de Bedrock con Floodgate | 013 |
| Errores de configuración poco precisos | Validación al cargar con archivo, ruta y «¿quisiste decir?»; `/omni validate` | 001 |
| Oyentes siempre registrados | Solo se escucha un evento si alguna regla lo usa | 001 |
| — | Importador de configuraciones de ConditionalEvents | 001 |

## Siguientes specs (propuestas, en orden)
Elegidas por el dueño tras la [investigación de plugins exitosos](../investigacion/plugins-exitosos.md) (§6; todas
menos el editor visual), una por sesión. El número de cada una se asigna al abrirla:
1. ~~**OmniEvents: herramientas de autor** (P1, P2, P4)~~: hecha en la [006](specs/006-omnievents-herramientas-de-autor/spec.md).
2. ~~**OmniEvents: esquema JSON y referencia generada** (P3)~~: hecha en la [007](specs/007-omnievents-esquema-y-referencia/spec.md). Las specs siguientes documentan su sintaxis nueva al registrarla (descripción en `Triggers`, `Actions`, `Functions` o `RuleOptions`) y regeneran con `./gradlew :omnievents:schema`.
3. ~~**OmniEvents: disparador de estado** (P6)~~: hecha en la [008](specs/008-omnievents-disparador-de-estado/spec.md).
4. ~~**OmniEvents: variables con caducidad y en bloques, entidades e ítems** (P5)~~: hecha en la [009](specs/009-omnievents-variables-caducidad-y-objetos/spec.md).
5. ~~**OmniEvents: pruebas de reglas en el YAML** `tests:` y `/omni test` (P7)~~: hecha en la [010](specs/010-omnievents-pruebas-de-reglas/spec.md).
6. ~~**OmniEvents: paquetes, plantillas y versión** (P8)~~: hecha en la [011](specs/011-omnievents-paquetes-plantillas-version/spec.md).
7. ~~**OmniEvents: integraciones con costos**: Vault, `cost:` todo o nada (P9), WorldGuard y Citizens~~: hecha en la [012](specs/012-omnievents-integraciones-con-costos/spec.md).
8. ~~**OmniEvents: menús de inventario y formularios de Bedrock** (P10)~~: hecha en la [013](specs/013-omnievents-menus-y-formularios-bedrock/spec.md).

Otras pendientes:
- **Fábrica: servidor simulado** — MockBukkit para probar en unitarias lo que hoy solo se ve en el servidor real (F-04, F-05 de la 001).
  Con la suite en el juego (002) es menos urgente: queda para la persistencia y los casos de bordes.
- **Fábrica: más escenarios en el juego** — las acciones y disparadores sin escenario (riesgos de la 002) y
  PlaceholderAPI con su jar en el servidor de prueba.
- **OmniEvents: más integraciones** — lo que la 012 dejó fuera: acciones sobre NPC (`npc_say`, `npc_teleport`) y regiones (banderas), bancos de Vault.
- ~~**OmniEvents: paridad con ConditionalEvents, segunda parte**~~: hecha en la [015](specs/015-omnievents-paridad-conditionalevents-2/spec.md) (Mending como `item_mend`: CE no lo tiene).
- ~~**OmniEvents: paridad con ConditionalEvents, tercera parte** (detalles y ProtocolLib)~~: hecha en la [016](specs/016-omnievents-paridad-conditionalevents-3/spec.md). Queda fuera de CE: `player_command_as_op`, DiscordSRV, `datavalue:`, `custom_model_component_data_*`, `vector` y unas pocas variables (`%player_attack_cooldown%`, `%format_remove_…%`, `%item_meta%`…).
- **OmniEvents: menús dinámicos** — lo que la 013 dejó fuera: paginación, ítems generados de una lista, refresco
  periódico y formularios de Bedrock con campos o de sí/no.
- **OmniEvents: editor en el juego** — inventario para ver, activar y probar reglas sin editar YAML.
- **OmniEvents: almacenamiento SQL** — MySQL/MariaDB para redes con varios servidores (variables compartidas).
- Después: el siguiente plugin de la fábrica, reutilizando el motor de expresiones de OmniEvents como biblioteca
  común (`libs/`), con su propio ADR.

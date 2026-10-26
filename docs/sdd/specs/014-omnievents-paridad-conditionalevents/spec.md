# Spec 014 — OmniEvents: paridad con ConditionalEvents

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 001, 006, 012, 013 ·
**Origen:** pedido del dueño (2026-10-25) tras comparar con la página de ConditionalEvents: «revisar a fondo, con el
jar, qué más falta». La 003 sigue reservada; esta va como 014 (decisión del dueño).

## Problema
OmniEvents nació como la versión propia de ConditionalEvents (CE, de Ajneb97) y hoy hace casi todo lo que CE hace y
mucho más, pero nadie lo comparó capacidad por capacidad. El dueño vio cuatro faltas a simple vista (mensaje centrado,
fuegos artificiales, evento de reparar ítems, permiso por evento para saltar el enfriamiento). Un servidor que migra
de CE con `/omni import` tampoco debería encontrarse con «REVISAR» en lo que sí se puede traducir.

## Fuente de la comparación
- **Jar:** `ConditionalEvents.jar` 4.65.1, del release oficial
  (`github.com/Ajneb97/ConditionalEvents/releases/download/4.65.1/ConditionalEvents.jar`, 305 370 bytes,
  sha256 `15847ebb0e17dc3ed95409b1b16b1e69948ce3ac62837f68c551961eec307d92`), guardado en `tests-e2e/.cache/ce/`
  (fuera de git). Analizado sin ejecutarlo: `plugin.yml`, `config.yml`, `events/more_events.yml` y las constantes de
  las clases con `javap` (enumeraciones `EventType`, `ActionType`, `ActionTargeterType`, `ConditionalType`; textos
  de `MainCommand`, `ConfigsManager`, `ActionUtils`, `VariablesUtils`, oyentes y `ExpansionCE`).
- **Wiki oficial** (`ajneb97.gitbook.io/conditionalevents`, 21 páginas): sintaxis de cada acción y variables de cada
  evento. Donde la wiki y el jar no coinciden manda el jar (la wiki documenta `player_receive_chat`, `player_jump`,
  `throw_directional`/`throw_coordinate`, que el 4.65.1 no trae; el jar trae `PLAYER_CHANGE_AIR` y `VECTOR` sin
  documentar).
- **OmniEvents 1.8.1:** [referencia generada](../../../omnievents/referencia.md), `Triggers`, `Actions`, `Functions`,
  `OmniCommand` y el importador `ConditionalEventsImporter`.

Leyenda de la columna «Brecha»: **—** cubierto · **parcial** · **falta** · **no** (no se hará, con el motivo).
«Imp.» dice si el importador lo traduce hoy (✔), mal o a medias (~) o lo deja como «REVISAR» (✘).

### Eventos (51 en el jar)
| CE | OmniEvents | Brecha | Imp. | Propuesta |
|---|---|---|---|---|
| player_join, player_leave, player_pre_join, player_respawn, player_death, player_command, player_chat, player_levelup, player_world_change, player_teleport, player_bed_enter, player_fish, player_sneak, player_run, player_regain_health, player_change_food | `player_join`, `player_quit`, `player_pre_login`, `player_respawn`, `player_death`, `player_command`, `player_chat`, `player_level_change`, `player_world_change`, `player_teleport`, `player_bed_enter`, `player_fish`, `player_sneak`, `player_sprint`, `player_regain_health`, `player_food_change` | — | ✔ | Mapear las variables que faltan en el importador (`%from_x%`, `%caught_type%`, `%result%`, `%online_players_from%`…) |
| player_attack, player_kill, player_damage | `player_attack`, `player_kill`, `player_damage` | — | ✔ | Importador: `%attack_type%`, `%original_damage%`, `%damager_type/name%`, `%victim_health%` |
| player_armor | `player_armor_change` (`item`, `old_item`, `slot`) | — | ~ | Importador: `%armor_type%`, `%equip_type%` |
| player_swap_hand (jar) / player_offhand (wiki) | `player_swap_hands` | — | ✔ | — |
| player_open/close/click_inventory | `inventory_open/close/click` | — | ✔ | Importador: `%action_type%`, `%slot_type%`, `%cursor:…%` |
| item_interact, item_consume, item_pickup, item_craft, item_drop, item_select, item_enchant | `item_interact`, `item_consume`, `item_pickup`, `item_craft`, `item_drop`, `item_held`, `item_enchant` | — | ✔ | Importador: `%select_type%` (SELECT/DESELECT ↔ `item`/`previous_item`), `%enchantment_list%` |
| block_interact, block_break, block_place | `block_interact`, `block_break`, `block_place` | — | ✔ | — |
| entity_spawn, entity_interact | `entity_spawn`, `entity_interact` | — | ✔ | — |
| console_command, server_start, server_stop, repetitive, repetitive_server, call, custom | `console_command`, `server_start`, `server_stop`, `timer`, `timer` + `per_player: false`, `call`, `custom` | — | ✔ | — |
| citizens_right_click_npc | `npc_click` (spec 012) | — | ✘ | Importador: tipo y `%npc_id%`, `%npc_name%` |
| wgevents_region_enter/leave (plugin WorldGuard Events) | `region_enter`/`region_leave` con WorldGuard solo (spec 012) | — | ✘ | Importador: tipo y `%region%` |
| **item_repair** (yunque, afiladora, reparación, `%rename_text%`, `%repair_type%`) | — | **falta** | ✘ | Disparador `item_repair` (ver H3) |
| **player_statistic** (`%statistic_name%`, `%previous_value%`, `%new_value%`) | — | **falta** | ✘ | Disparador `player_statistic` |
| **player_change_air** (sin documentar) | — | **falta** | ✘ | Disparador `player_air_change` |
| **player_tab_complete** (+ acción `tab_complete`) | Solo el `tab` de los comandos propios (`trigger: command`) | **falta** | ✘ | Disparador `player_tab_complete` y acción `tab_complete` |
| item_move (clic que mueve un ítem del inventario) | `inventory_click` (con `slot`, `click`, `item`) | parcial | ✘ | Alias `item_move` → `inventory_click` en el importador |
| protocollib_receive_message (discontinuado), player_receive_chat (wiki) | — | **no** | ✘ | Lee paquetes (ProtocolLib/PacketEvents): fuera de la constitución VI.2 sin un ADR; se queda como REVISAR con nota |

OmniEvents además trae `player_move`, `player_jump`, `player_gamemode_change`, `player_toggle_flight`,
`player_advancement`, `player_portal`, `player_kick`, `player_resource_pack`, `player_shoot`, `projectile_hit`,
`sign_change`, `bucket_fill/empty`, `item_break`, `entity_death`, `clock`, `command`, `state`, `var_expire`, `menu_*`.

### Acciones (53 en el jar)
| CE | OmniEvents | Brecha | Imp. | Propuesta |
|---|---|---|---|---|
| message, console_message, mini_message, console_command, player_command, player_send_chat, send_to_server, kick, gamemode, damage, heal, freeze, set_on_fire, set_food_level, close_inventory, clear_inventory, cancel_event, stopsound, give_potion_effect, remove_potion_effect, wait, wait_ticks, call_event, lightning_strike | `message`, `console_message`, `console`, `command`, `chat`, `send_to_server`, `kick`, `gamemode`, `damage`, `heal`, `freeze`, `fire`, `set_food`, `close_inventory`, `clear_inventory`, `cancel`, `stop_sound`, `effect`, `remove_effect`, `delay`, `call`, `lightning` | — | ✔ | — |
| **centered_message** | `message` (sin centrar) | **falta** | ~ (pierde el centrado) | Acción `centered_message` (H1) |
| **firework** (`colors`, `type`, `power`, `fade`, `flicker`, `trail`, `location`, `shot_direction`) | — | **falta** | ✘ | Acción `firework` (H2) |
| json_message (JSON con clic y hover) | `message` con MiniMessage (`<click>`, `<hover>`) | parcial | ~ (mostraría el JSON crudo) | Acción `json_message` que convierte el JSON con Adventure; el importador la usa |
| actionbar `texto;duración` | `actionbar` (un envío) | parcial | ~ | Segundo parámetro opcional: duración en ticks (se repite cada 2 s hasta cubrirla) |
| title `entrada;estancia;salida;título;subtítulo` | `title` (`título;subtítulo;…`) | — | ✔ | — |
| playsound `SONIDO;vol;tono[;x,y,z,mundo]`, playsound_resource_pack | `sound` (también claves de paquete de recursos) | parcial | ~ | 4.º parámetro opcional: ubicación `mundo;x;y;z`… (H5) |
| stopsound_resource_pack (wiki) | `stop_sound` con nombre de enum | parcial | ✘ | `stop_sound` acepta claves con `.`/`:` |
| teleport | `teleport` | — | ✔ | — |
| give_potion_effect `…;partículas;icono` | `effect` `EFECTO;seg;nivel;partículas` | parcial | ✔ | 5.º parámetro `icono` |
| remove_item `MAT;n;name:…;lorecontains:…;datavalue:…` | `remove_item` `MAT;n` (por material) | parcial | ~ (pierde los filtros) | Filtros `name:` y `lorecontains:` (H5) |
| **remove_item_slot** `HAND/OFF_HAND/HELMET…/n;cantidad` | — | **falta** | ✘ | Acción `remove_item_slot` (H5) |
| give_item `id:…;amount;name;lore;enchants;flags;durability;custom_model_data;item_model;skull_owner;skull_texture;slot;slot_replace;saved_item` | `give_item` (texto o mapa: `material`, `amount`, `name`, `lore`, `model`, `enchants`, `glow`, `unbreakable`, `hide_flags`, `saved`) | parcial | ~ (solo id, amount, name) | Claves `slot`, `slot_replace`, `flags`, `durability`, `item_model`, `skull_owner` (H5); el importador traduce todas |
| drop_item `location:x,y,z,mundo;id:…` | `drop_item` con `location` | — | ✘ | Importador |
| set_item (cambia el pez de `player_fish`) | — | **falta** | ✘ | `set_event: "caught.item_stack = …"` no sirve (no hay expresión de ítem): acción `set_caught_item` con el formato de `give_item` |
| set_block `location:…;id:…;block_data:…` | `set_block` `[mundo;]x;y;z;MATERIAL` | parcial | ✘ | `block_data` opcional (`MATERIAL[facing=north]`, lo admite `createBlockData`) |
| summon `location;entity;custom_name;health;amount;scale;equipment;hand_equipment` | `summon` `TIPO[;mundo;x;y;z]` | parcial | ~ (solo tipo y lugar) | Forma de mapa con `name`, `health`, `amount`, `scale`, `equipment` (H5) |
| particle `effect;amount;offset;speed;location;force;for_player` | `particle` `PARTÍCULA;n;dx;dy;dz;vel[;r;g;b]` | parcial | ~ (solo efecto y cantidad) | Claves `location`, `force`, `for_player` en forma de mapa |
| vector (jar) / throw_directional, throw_coordinate (wiki) | `launch`, `velocity` | — | ~ | Importador: `throw_coordinate` → `velocity` |
| keep_items `items/xp/all` | `keep_inventory` (las dos cosas) | parcial | ~ | `keep_inventory: items|xp|all` |
| cancel_drop, set_damage, set_death_message, hide_join_message, hide_leave_message, prevent_join | `cancel_drops`, `set_damage`, `set_death_message`, `set_join_message: none`, `set_quit_message: none`, `deny_join` | — | ✔ | — |
| set_event_xp | `set_event: "exp_to_drop = 0"` | — | ✘ | Importador |
| execute_action_group `g1:70;g2:30` | `random` ponderado | — | ✘ | Importador |
| tab_complete | — | **falta** | ✘ | Con el disparador `player_tab_complete` |
| player_command_as_op | — | **no** | ✘ | Escalada de permisos (plan 001, STRIDE); se queda como REVISAR |
| discordsrv_embed | — | **no** (esta spec) | ✘ | Integración con DiscordSRV: candidata a «más integraciones» del roadmap |
| API (acciones de otros plugins) | `Actions.register` (API Java) | — | — | — |

### Destinos, condiciones y variables
| CE | OmniEvents | Brecha | Imp. | Propuesta |
|---|---|---|---|---|
| to_all, to_target, to_world, to_player | `to: all/target/world:…/player:…` | — | ✔ | — |
| to_range `radio;incluir_jugador[;entidades]` | `to: radius:n` / `nearby:n` | parcial | ~ (ignora el booleano) | Importador: `false` → `nearby` (sin el jugador); entidades no jugadoras → REVISAR |
| to_condition + `Config.to_condition_groups` | `to` + `where` | — | ✘ | Importador: traduce el grupo a `where` |
| Operadores (`equals`, `equalsIgnoreCase`, `startsWith`, `contains`, `matches` y sus `!`) | Lenguaje de expresiones | — | ✔ | — |
| `execute grupo` y `execute grupo{%v%=x}` | `if`/`else` | — | ~ (sin los parámetros) | Importador: los parámetros como `set: var.v = …` al inicio del grupo |
| allow_math_formulas_in_conditions | aritmética nativa | — | ✔ | — |
| `%random_a_b%`, `%player%`, `%player_x%`… | `random(a,b)`, `player.name`… (y los mismos `%…%` incorporados) | — | ✔ | — |
| `%random_player%`, `%randomword_a-b%` | — | **falta** | ✘ | Función `pick(lista)` (`pick(players()).name`, `pick(['a','b'])`) |
| `%block_at_x_y_z_mundo%`, `%block_data_at_…%`, `%is_nearby_x_y_z_mundo_r%` | `in_area`, `distance` | parcial | ✘ | Función `block_at(mundo, x, y, z)` (devuelve el bloque: `.type`, `.data`); el importador arma `distance(...) <= r` |
| `%playerblock_below_d%`, `%playerblock_above_d%`, `%playerblock_inside%`, `%player_is_outside%` | `player.location.block` y reflexión | parcial | ✘ | Importador con `block_at(…)`; `is_outside` = luz del cielo 15 |
| `%playerarmor_x%`, `%item_lore_line_n%`, `%victim_health%`, `%world_time_m%`, `%world_is_raining%` | `player.helmet.type`, `item.lore[n-1]`, `target.health`, `world.time`, `world.storm` | — | ✘ | Importador |
| `%is_number_x%`, `%empty%` | `num()`; `''` | parcial | ~ | Función `is_number(x)` |
| `%target:…%`, `%to:…%` | `papi('%…%', target)`; `player` en `where` | — | ~ | Importador para `%to:…%` |
| `%time%` (grupo cooldown) | `var.cooldown_left` | — | ✘ | Importador |

### Opciones de un evento
| CE | OmniEvents | Brecha | Imp. | Propuesta |
|---|---|---|---|---|
| type, enabled, conditions, actions (grupos), one_time, cooldown, ignore_with_permission, ignore_if_cancelled, repetitive_time, custom_event_data | mismas o equivalentes (`limit: 1`, `bypass_permission`, `ignore_cancelled`, `every`, `event`/`player_from`) | — | ✔ | — |
| **OP salta el cooldown; `conditionalevents.bypasscooldown.<evento>`** | `cooldown-bypass-op` global en `config.yml` | **falta** | ✘ | Permiso por regla (H4) |
| prevent_cooldown_activation / prevent_one_time_activation (grupos que no activan) | acción `no_cooldown` | — | ✘ | Importador: agrega `no_cooldown` a esos grupos |
| variables_to_capture | `event.<propiedad>` | — | ~ (nota) | — |

### Comandos, placeholders, configuración y API
| CE | OmniEvents | Brecha | Imp. | Propuesta |
|---|---|---|---|---|
| /ce reload, verify, help, enable/disable, debug `<evento> [jugador]`, call `… player: …`, item save/remove | `/omni reload`, `validate`, `help`, `enable/disable`, `debug`, `call <regla> [jugador] k=v`, `item save/delete` | — | — | — |
| **/ce reset `<jugador>`/`*` `<evento>`/`all`** | `/omni cooldown|limit reset <regla> [jugador|global]` (una regla, un jugador) | **falta** | — | `*` para todas las reglas o todos los jugadores, también desconectados (H4) |
| **/ce interrupt `<evento>` [jugador]** (corta los `wait`) | — | **falta** | — | `/omni interrupt <regla> [jugador]` (H5) |
| `silent:true` en call/enable/interrupt | — | parcial | — | `-s` en esos comandos (sin mensaje; para comandos de otras reglas) |
| %conditionalevents_cooldown_e%, _onetime_ready_e, _is_enabled_e | `%omni_cooldown_r%`, `%omni_uses_r%` | parcial | — | `%omni_enabled_r%` |
| Config: data_save_time, debug_actions, update_notification, register_commands, use_minimessage, experimental.variable_replacement, item_meta_variable_enabled | `storage.autosave-minutes`; comandos reales; MiniMessage y `&` siempre; expresiones anidables | — | — | Sin avisos de versión (constitución II.4) |
| API: `ConditionalEventsEvent` (se dispara al correr un grupo) | `Actions.register`, `Functions.register`, `Triggers.register` | parcial | — | Evento `OmniRuleFireEvent` para otros plugins (opcional) |

## Historias
**H1 — Mensaje centrado.** Como *administrador*, quiero centrar un anuncio en el chat como en ConditionalEvents.
- CA1: `centered_message: <texto>` manda al jugador (o a los destinatarios de `to`) cada línea del texto centrada en
  el chat por defecto (320 px): se calcula el ancho con la fuente de Minecraft (letras angostas como `i`, `l`, `.`,
  y un píxel más por letra en **negrita**) y se agregan espacios delante. Acepta MiniMessage, `&` y `{expresiones}`;
  `\n` o `<newline>` separan líneas y cada una se centra por su cuenta.
- CA2: Una línea más ancha que el chat se manda sin espacios (no se corta). Sin jugador, va a la consola sin centrar.

**H2 — Fuegos artificiales.** Como *administrador*, quiero lanzar fuegos artificiales con colores y forma.
- CA1: `firework: "colors:RED,#FFAA00 type:STAR power:1 fade:AQUA flicker:true trail:true"` (la forma de CE) lanza un
  cohete desde el jugador, o desde cada destinatario de `to` (también entidades con `to: target`). `colors` (uno o
  más: nombres de `org.bukkit.Color` o `#RRGGBB`) es obligatorio; `type` (BALL por defecto, BALL_LARGE, STAR, BURST,
  CREEPER), `power` (0–3, 1 por defecto), `fade`, `flicker`, `trail` y `location` (`mundo;x;y;z`, `~` relativo) son
  opcionales. Los valores aceptan `{expresiones}`.
- CA2: La explosión no hace daño (a diferencia de un cohete de vanilla con estrellas), salvo `damage:true`.
- CA3: Un color, tipo o clave desconocidos dan error al cargar con la ruta y «¿quisiste decir?» cuando el texto es
  constante; si sale de una expresión, el error es de la ejecución (como las demás acciones).

**H3 — Reparar ítems.** Como *administrador*, quiero reaccionar cuando alguien repara o renombra un ítem.
- CA1: `trigger: item_repair` corre cuando un jugador saca el resultado de un yunque o de una afiladora (clic o
  shift-clic en la casilla del resultado), con `item` (el resultado), `repair_type` (`ANVIL` o `GRINDSTONE`),
  `base_item` (lo que estaba en la primera casilla), `addition` (la segunda), `rename_text` (el nombre escrito en el
  yunque, o vacío) y `level_cost` (niveles que cuesta en el yunque; 0 en la afiladora).
- CA2: No corre si el clic no se lleva nada (resultado vacío, o sin niveles suficientes en el yunque fuera de
  creativo). `cancel` impide la reparación (el jugador conserva todo y no paga niveles).
- CA3: Está en el registro (esquema y referencia) y en los ejemplos, con `tests:`.

**H4 — Enfriamientos al estilo CE.** Como *administrador* que viene de ConditionalEvents, quiero saltar el
enfriamiento de una regla con un permiso y reiniciar datos de todos.
- CA1: El permiso `omnievents.bypasscooldown.<regla>` salta el enfriamiento de esa regla (no lo activa ni lo mira);
  `cooldown_bypass: <permiso>` le pone otro nombre. Solo cuenta si el permiso está **dado** al jugador
  (`isPermissionSet`): un OP sin él no lo salta (eso sigue siendo `cooldown-bypass-op` en `config.yml`).
- CA2: `/omni check` y `/omni debug` dicen «libre (permiso …)» cuando lo salta.
- CA3: `/omni cooldown reset` y `/omni limit reset` aceptan `*` como regla (todas las que tienen enfriamiento o
  límite) y `*` como jugador (todos: conectados y desconectados, y el global en las reglas con `scope: global`). Los
  archivos de los desconectados se tocan fuera del hilo principal y el mensaje dice cuántos jugadores cambió.

**H5 — Importador completo.** Como *administrador* que migra, quiero que `/omni import` traduzca todo lo que
OmniEvents puede hacer.
- CA1: Cada fila de las tablas con equivalente en OmniEvents y marca ✘ o ~ se traduce: los tipos de evento
  (`citizens_right_click_npc`, `wgevents_*`, `item_repair`, `item_move`), las variables de cada evento y las globales
  con equivalente, las acciones (`centered_message`, `firework`, `title`, `actionbar`, `playsound` con ubicación,
  `give_item`/`drop_item`/`summon`/`particle`/`set_block` con sus claves conocidas, `keep_items`, `set_event_xp`,
  `execute_action_group`, `throw_*`/`vector`), los destinos (`to_range` con su booleano, `to_condition` con
  `Config.to_condition_groups`), `execute grupo{%v%=x}`, `prevent_cooldown_activation`/`prevent_one_time_activation`
  y el permiso `conditionalevents.bypasscooldown.<evento>` (como `cooldown_bypass`).
- CA2: Lo que no tiene equivalente (lo marcado **no**, y las claves de CE que OmniEvents todavía no tiene, p. ej.
  `summon` con `equipment`) queda como «REVISAR» con una nota que dice qué falta, sin perder el resto de la línea.
- CA3: Una prueba unitaria importa una configuración de CE que usa cada fila y comprueba que el resultado carga sin
  errores en OmniEvents.

**H6 — Documentado y compatible.** `centered_message`, `firework`, `item_repair` y `cooldown_bypass` están en su
registro (esquema JSON y referencia regenerados) y en los ejemplos; el README del plugin trae la tabla resumida «Desde
ConditionalEvents» (qué se traduce, qué no y por qué).

## Fuera de alcance
- Lectura de paquetes (ProtocolLib, PacketEvents): `player_receive_chat`, `protocollib_receive_message`.
- `player_command_as_op` (escalada de permisos).
- DiscordSRV (va con «más integraciones» del roadmap).
- Lo parcial que el dueño dejó para otra spec («paridad con CE, segunda parte» en el roadmap): disparadores
  `player_statistic`, `player_air_change`, `player_tab_complete` y la acción `tab_complete`; `json_message`,
  `remove_item_slot` y los filtros de `remove_item`; claves nuevas de `give_item`, `summon`, `particle` y `sound`;
  `set_caught_item`; `keep_inventory` con modo; funciones `pick`, `block_at` e `is_number`; `/omni interrupt`;
  `%omni_enabled_r%`; `-s` (silencioso) en los comandos; reparación por Mending.

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Número de spec? | 014 (la 003 queda reservada) |
| 2 | ¿Alcance? (estimado: núcleo + importador ≈ 1 sesión larga; + parciales ≈ 1.6; todo ≈ 2+) | Núcleo + importador: las 4 brechas vistas, `reset` con `*` y el importador completo para lo que ya tiene equivalente |
| 3 | ¿Permiso para saltar el enfriamiento? | Automático `omnievents.bypasscooldown.<regla>` (solo si está dado) + opción `cooldown_bypass` |
| 4 | ¿Qué es reparar en `item_repair`? | Yunque y afiladora (sin Mending) |

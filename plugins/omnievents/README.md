# OmniEvents

Eventos condicionales para **Paper 1.21.4 y 26.1** (ver [Compatibilidad](#compatibilidad)): «cuando pase X, si se cumple Y, haz Z», declarado en YAML.
Es la versión propia de la fábrica de *ConditionalEvents*, con un lenguaje de expresiones real, control de flujo,
variables persistentes y herramientas de diagnóstico. Spec: [001](../../docs/sdd/specs/001-omnievents-nucleo/spec.md).

## Compatibilidad
| Servidor | Estado | Evidencia |
|---|---|---|
| **Paper 1.21.4** (build 232, Java 24) | Verificada | Compuertas 1, 2 y 3a: unitarias, servidor real y 21/21 escenarios en el juego (2026-10-24, specs [001](../../docs/sdd/specs/001-omnievents-nucleo/verify.md) y [002](../../docs/sdd/specs/002-fabrica-pruebas-en-juego/verify.md)) |
| **Paper 26.1.2** (build 74, Java 25) | Verificada | 21/21 escenarios en el juego con el mismo jar (2026-10-24, spec [002](../../docs/sdd/specs/002-fabrica-pruebas-en-juego/verify.md)) |
| Paper 1.21.5 – 1.21.11 | Sin verificar | Debería funcionar (misma API, sin NMS), pero no se probó |
| Paper 26.2 y 26.3 | Sin verificar | El bot de pruebas (mineflayer 4.39) llega hasta 26.1 |
| Paper 1.21.3 o anterior | No soportada | Se compila contra la API de 1.21.4 |
| Spigot, CraftBukkit | No soportada | Usa la API Adventure de Paper |
| Folia | No soportada | Planificada para una spec futura |

- **Java:** el jar necesita Java 21 o más; usa el Java que pida tu versión de Paper (las pruebas en 26.x usaron Java 25).
- **Opcional:** PlaceholderAPI 2.11+ (sin verificar en el juego todavía).
- Para verificar otra versión: `npm --prefix tests-e2e test -- --paper <versión> --version-bot <versión> --java-servidor <JDK>`
  (ver [verificacion.md](../../docs/sdd/verificacion.md#3a-en-el-juego-automática)).

## Instalación
1. Copia `OmniEvents-x.y.z.jar` a `plugins/` (opcional: PlaceholderAPI).
2. Arranca el servidor: se crean `config.yml`, `messages.yml` y ejemplos en `plugins/OmniEvents/rules/ejemplos/`.
3. Edita o agrega archivos en `rules/` y usa `/omni reload`.

¿Vienes de ConditionalEvents? `/omni import conditionalevents` convierte tus eventos a `rules/importado/`.

## Una regla
```yaml
diamantes:
  trigger: block_break                 # cuándo
  worlds: [world]
  conditions:                          # si (todas deben cumplirse)
    - "block.type in ['DIAMOND_ORE', 'DEEPSLATE_DIAMOND_ORE']"
    - "player.gamemode == 'survival' && !player.hasPermission('staff')"
  cooldown: 10s
  actions:                             # haz
    - set: "pvar.diamantes += 1"
    - if: "pvar.diamantes % 10 == 0"
      then:
        - broadcast: "<aqua>{player.name}</aqua> ya minó {pvar.diamantes} diamantes"
  else:
    - actionbar: "<gray>Solo cuentan los diamantes"
  on_cooldown:
    - message: "<red>Espera {var.cooldown_left}s"
```

## Opciones de una regla
| Opción | Qué hace |
|---|---|
| `trigger` | Disparador (`/omni triggers` los lista) |
| `enabled` | `false` la apaga sin borrarla |
| `priority`, `ignore_cancelled` | Prioridad del oyente; no correr si otro plugin canceló el evento |
| `worlds`, `permission`, `bypass_permission` | Filtros rápidos |
| `conditions` | Una expresión o una lista (todas) |
| `actions`, `else` | Qué hacer si se cumplen / si no |
| `cooldown`, `cooldown_scope` | `30s`, `5m`, `1h30m`, `2d`, `20t`; `player` o `global` |
| `limit`, `limit_scope`, `limit_reset` | Usos máximos; `never`, `daily`, `weekly`, `monthly` o una duración |
| `on_cooldown`, `on_limit` | Acciones cuando está en enfriamiento o sin usos |
| `every`, `per_player` | Para `timer` |
| `at`, `timezone` | Para `clock` (`["08:00", "20:30"]`) |
| `command` | Para `command`: `name`, `aliases`, `permission`, `description`, `usage`, `tab` |
| `event`, `player_from` | Para `custom`: clase del evento y ruta al jugador |
| `move_mode` | Para `player_move`: `block` (defecto), `chunk` o `any` |
| `args` | Para `call`: parámetros (llegan como `var.<nombre>`) |

## Expresiones
- Lógica: `&&` `and`, `||` `or`, `!` `not`, paréntesis, `c ? a : b`.
- Comparación: `==` `!=` `<` `<=` `>` `>=`, `~=` (igual sin mayúsculas), `contains`, `startsWith`, `endsWith`,
  `matches` (regex), `in`; negaciones `!contains`, `not in`…
- Aritmética: `+ - * / %` (con texto, `+` concatena). Listas `[1, 2]`, índices `args[0]`.
- Datos: `player.health`, `player.location.block.type`, `item.name`, `target.type`, `event.damage`… Cualquier
  getter de la API sirve en estilo `snake_case` (`event.final_damage` → `getFinalDamage()`); los métodos que
  modifican están bloqueados.
- Variables: `var.x` (de la ejecución), `pvar.x` (del jugador, persistente), `gvar.x` (del servidor, persistente).
- Placeholders: `%vault_eco_balance% > 100` (con PlaceholderAPI; si el resultado es un número, se compara como número).
- Funciones: `random(a,b)` `chance(p)` `min` `max` `abs` `round(x,d)` `floor` `ceil` `sqrt` `pow` `clamp` `len`
  `lower` `upper` `trim` `replace` `substr` `split` `join` `str` `num` `int` `bool` `strip` `format` `default(a,b)`
  `if(c,a,b)` `papi(t[,jugador])` `online(n)` `player(n)` `players()` `perm(p)` `now()` `hour()` `minute()`
  `weekday()` `date(patrón)` `cooldown(regla)` `uses(regla)` `distance(a,b)` `in_area(mundo,x1,y1,z1,x2,y2,z2)`
  `has_item(material[,n])` `pvar_of(jugador,nombre)` `size(x)`.

Prueba cualquier expresión en el juego: `/omni eval player.location.biome`.

## Textos
`{expresión}` interpola, `%placeholder%` pasa por PlaceholderAPI, y se aceptan MiniMessage, `&a` y `&#RRGGBB`.
Lo que escribe el jugador e interpolas con `{}` nunca se interpreta como MiniMessage (no puede inyectar `<click>`),
y en comandos pierde los saltos de línea.

## Acciones
Dos formas equivalentes: `- message: "hola"` o `- "message: hola"`. Modificadores en cualquier acción:
```yaml
- message: "<gold>¡Evento!"
  to: all                  # self | target | all | world:<w> | radius:<n> | nearby:<n> | player:<nombre> | expresión
  where: "player.level >= 10"   # filtro por destinatario
  chance: 25               # porcentaje
  when: "hour() >= 20"     # guarda
```
Control de flujo: `if/then/else`, `repeat/do` (`var.i`), `while/do`, `for_each/as/do`, `random` (ramas con `weight`),
`delay`, `stop`, `call`, `set`, `unset`, `no_cooldown`, `reset_cooldown`.

| Grupo | Acciones |
|---|---|
| Mensajes | `message` `broadcast` `actionbar` `title` (`título;subtítulo;in;stay;out`) `bossbar` (`texto;segundos;color;progreso`) `sound` (`SONIDO;vol;tono`) `stop_sound` `console_message` `log` |
| Comandos | `console` `command` `chat` `send_to_server` |
| Jugador | `teleport` (`mundo;x;y;z;yaw;pitch`, `~` relativo) `heal` `damage` `feed` `set_food` `gamemode` `fly` `effect` (`EFECTO;segundos;nivel;partículas`) `remove_effect` `fire` `freeze` `velocity` `launch` `give_item` `drop_item` `remove_item` (`MATERIAL;cantidad`: por material, aunque el ítem tenga nombre) `clear_inventory` `close_inventory` `give_exp` `give_level` `kick` |
| Mundo | `set_block` `lightning` `explosion` (`potencia;fuego;romper`) `particle` (`PARTÍCULA;n;dx;dy;dz;vel[;r;g;b]`) `summon` |
| Evento | `cancel` `set_event` (`propiedad = expresión`) `set_damage` (`10` o `150%`) `set_message` `set_join_message` `set_quit_message` `set_death_message` (`none` la oculta) `keep_inventory` `cancel_drops` `deny_join` |

Ítems: `give_item: "DIAMOND;2;<aqua>Nombre;<gray>Lore 1|<gray>Lore 2"`, en mapa (`material`, `amount`, `name`,
`lore`, `model`, `enchants`, `glow`, `unbreakable`, `hide_flags`) o `saved:<nombre>` (guardado con `/omni item save`).

## Disparadores
| Grupo | Disparadores |
|---|---|
| Jugador | `player_join` (`first_join`) `player_quit` `player_pre_login` (`name`, `uuid`, `ip`) `player_respawn` `player_death` (`killer`, `cause`) `player_chat` (`message`) `player_command` (`cmd`, `args`) `player_move` `player_teleport` `player_world_change` `player_sneak` `player_sprint` `player_jump` `player_level_change` `player_gamemode_change` `player_toggle_flight` `player_bed_enter` `player_fish` `player_regain_health` `player_food_change` `player_advancement` `player_swap_hands` `player_armor_change` `player_portal` `player_kick` `player_resource_pack` |
| Combate | `player_damage` (`cause`, `damager`) `player_attack` (`target`) `player_kill` (`target`) `player_shoot` `projectile_hit` |
| Bloques | `block_break` `block_place` `block_interact` (`action`: `RIGHT_CLICK`, `SHIFT_LEFT_CLICK`…) `sign_change` (`lines`) `bucket_fill` `bucket_empty` |
| Ítems | `player_interact` `item_interact` `item_consume` `item_craft` `item_drop` `item_pickup` `item_held` `item_enchant` `item_break` |
| Inventario | `inventory_open` `inventory_close` `inventory_click` (`slot`, `click`, `title`, `cursor`) |
| Entidades | `entity_interact` `entity_spawn` `entity_death` |
| Servidor | `console_command` `server_start` `server_stop` `timer` `clock` `call` `command` `custom` |

Siempre disponibles: `player`, `event`, `world`, `location`, `item` (mano principal), `server`, `online`, `rule`.

## Comandos (`/omni`, alias `/oe`)
`reload` · `validate` · `list [filtro]` · `info <regla>` · `enable|disable <regla>` · `debug <regla|all|off>` ·
`eval <expr>` · `call <regla> [jugador] [k=v…]` · `var get|set|del|list <jugador|global> [nombre] [valor]` ·
`cooldown reset <regla> [jugador|global]` · `limit reset <regla> [jugador|global]` · `stats` · `triggers` · `actions` ·
`item save|delete|list` · `import conditionalevents` · `selftest`.
Permisos: `omnievents.command.<sub>` (por defecto solo OP) o `omnievents.command.*`.

## Diagnóstico
- `/omni debug mi_regla`: en cada disparo ves cada condición con sus valores (`player.level (3) >= 10 → false`),
  la rama que se tomó y cada acción.
- `/omni stats`: disparos, aciertos, errores y tiempo promedio y máximo por regla; oyentes y tareas activos.
- Los errores de configuración dicen archivo, ruta y causa, con «¿quisiste decir…?».

## PlaceholderAPI
`%omni_var_<n>%` · `%omni_gvar_<n>%` · `%omni_cooldown_<regla>%` · `%omni_uses_<regla>%`.

## API
```java
OmniEventsAPI.registerFunction("doble", 1, 1, (ctx, a) -> Values.num(a[0]) * 2);
OmniEventsAPI.registerAction("mi_accion", args -> Action.of(ctx -> ctx.requirePlayer().sendMessage("hola")));
OmniEventsAPI.registerProperty(Player.class, "clan", p -> MiPlugin.clanDe(p));
OmniEventsAPI.callRule("recompensa", jugador, Map.of("cantidad", 5));
```

# Plan 015 — Diseño técnico

## 1. Arquitectura

### 1.1 Ítems (`action/ItemSpec.java`, `action/Actions.java`, `action/Slots.java`)
- `ItemSpec` lee del mapa `flags`, `durability`, `item_model`, `skull_owner` y `skull_texture`. Las banderas
  constantes se validan al cargar contra `ItemFlag` con `Suggest`. Al construir: `Damageable.setDamage`,
  `ItemMeta.setItemModel(NamespacedKey)`, y en una `SkullMeta` un `PlayerProfile` (`Bukkit.createProfile(nombre)` o
  `createProfile(UUID, null)` con la propiedad `textures`); el perfil por nombre lo resuelve el servidor por su cuenta
  (no se bloquea el hilo principal ni se hace red desde OmniEvents).
- `slot` y `slot_replace` los lee la acción `give_item` del mapa (no `ItemSpec`: en un menú `slot` es la casilla del
  menú). `Slots.parse(texto)` convierte `HAND`, `OFF_HAND`, `HELMET`… o un número 0–40 a un índice del inventario del
  jugador (`HAND` = la casilla de la mano en el momento). Pura: unitaria.
- `remove_item`: partes 3+ con `name:` y `lorecontains:`; `removeByMaterial` recibe un filtro (`Predicate<ItemStack>`)
  que compara el texto plano del nombre y del lore con el filtro sin colores (`&`, `§` y etiquetas MiniMessage fuera).
- `remove_item_slot` (`CASILLA;cantidad`) con `Slots`; `set_caught_item` (ítem de `give_item`) sobre
  `PlayerFishEvent.getCaught()` si es un `Item`.

### 1.2 Mundo (`action/Actions.java`)
- `summon` con mapa: `entity`/`type`, `location`, `name` (`customName` + visible), `health` (atributo `MAX_HEALTH` y
  vida), `amount` (tope 50), `scale` (`Attribute.SCALE`), `equipment`/`hand_equipment` (texto con comas o lista;
  material o `none`; probabilidad de soltar 0).
- `set_block`: si el último valor tiene `[`, `Bukkit.createBlockData(texto)` y `setBlockData`; si no, `setType`.
- `particle`: claves hermanas `location`, `force`, `for_player` (`Player.spawnParticle` o `World.spawnParticle(…, force)`).
- `sound`: clave `location` (`World.playSound`); `stop_sound` con `.` o `:` usa `stopSound(String)`.
- `keep_inventory`: si el texto es `items`, `xp` o `all` es un modo fijo; si no, una expresión (verdadero = `all`).

### 1.3 Mensajes y funciones (`action/Actions.java`, `expr/Functions.java`, `hook/PapiHook.java`)
- `json_message`: se quita el alias a `message`. Al cargar, `GsonComponentSerializer.gson().deserialize(json)` →
  `MiniMessage.serialize` → `Template.compile`; el resto como `message` (destinatarios, consola).
- `pick(…)`: con un argumento lista, de la lista; con varios, de los argumentos. `block_at`: `Bukkit.isPrimaryThread()`
  o `ExprException`; `World.isChunkLoaded(x >> 4, z >> 4)` o nulo. `is_number`: `Values.toNumberOrNull != null`.
- `%omni_enabled_<regla>%`: `plugin.rules().get(regla)?.enabled()`.

### 1.4 Interrumpir (`action/Execution.java`, `rule/RuleManager.java`, `command/OmniCommand.java`)
- `Execution` guarda el jugador de la ejecución (el sujeto al empezar) y, al esperar, su `BukkitTask`. `RuleManager`
  lleva un `Set<Execution>` de las que esperan (se agrega al programar la espera y se quita al reanudar o terminar).
- `interrupt(regla|*, uuid|null)`: por cada una que coincide, cancela la tarea, `stop()` y `finish()` (corre su
  `onFinish`, como al terminar). Devuelve cuántas cortó. Solo en el hilo principal (el comando y el planificador ya lo son).
- `-s`: `OmniCommand` quita `-s` de los argumentos de `call`, `enable`, `disable` e `interrupt` y no manda el mensaje.

### 1.5 Disparadores (`trigger/Triggers.java`, `action/Actions.java`)
- `player_statistic` (`PlayerStatisticIncrementEvent`): `statistic` (nombre), `previous_value`, `new_value`,
  `material`, `entity_type`.
- `player_air_change` (`EntityAirChangeEvent`, solo jugadores): `old_air` (`getRemainingAir` del jugador),
  `new_air` (`getAmount`), `max_air`.
- `player_tab_complete` (`TabCompleteEvent`, solo jugadores, buffer que empieza por `/`): `buffer`, `cmd`
  (`Triggers.command`), `args`, `last`, `completions` (la lista del evento). Acción `tab_complete`: `modo;a,b,c` con
  plantillas; sobre `TabCompleteEvent.getCompletions()` (copia modificable y `setCompletions`).
- `item_mend` (`PlayerItemMendEvent`): `item`, `amount` (`getRepairAmount`), `orb`.
- Todos con descripción (esquema y referencia) y grupo; solo se escuchan si una regla los usa (III.1).

### 1.6 Importador (`importer/ConditionalEventsImporter.java`)
- Tipos: `player_statistic`, `player_change_air` → `player_air_change`, `player_tab_complete` salen de `NO_TYPE`.
- Variables: `statistic_name`, `previous_value`, `new_value` (por evento), `main_command` en `player_tab_complete` →
  `('/' + cmd.name)`; `%random_player%` → `pick(players()).name`; `%randomword_a-b-c%` → `pick(['a','b','c'])`;
  `%is_number_x%` → `is_number(x)`; `%block_at_x_y_z_mundo%` → `block_at('mundo', x, y, z).type`.
- Acciones: `json_message` → `json_message:` con las `%variables%` como `{expresiones}` (sin escapar las llaves del
  JSON); `remove_item` con filtros (`datavalue:` → REVISAR); `remove_item_slot`; `give_item`/`drop_item` con las claves
  nuevas; `summon` → mapa; `particle` con `location`/`force` → claves hermanas; `playsound` con ubicación →
  `location`; `stopsound_resource_pack` → `stop_sound`; `set_block` con `block_data` → `MATERIAL[datos]`; `set_item`
  → `set_caught_item`; `keep_items` → `keep_inventory: modo`; `tab_complete` → `tab_complete`.

## 2. Contrato de configuración
```yaml
kit_vip:
  trigger: command
  command: kitvip
  actions:
    - give_item:
        material: DIAMOND_SWORD
        name: "<aqua>Espada VIP"
        flags: HIDE_ENCHANTS, HIDE_ATTRIBUTES
        durability: 100
        enchants: ["SHARPNESS:3"]
        slot: 0
        slot_replace: true
    - give_item: {material: PLAYER_HEAD, skull_owner: "{player.name}", slot: HELMET}
    - remove_item: "PAPER;1;name:Ticket VIP"
    - remove_item_slot: "OFF_HAND;1"

guardian:
  trigger: command
  command: guardian
  actions:
    - summon:
        entity: ZOMBIE
        name: "<red>Guardián"
        health: 40
        scale: 1.5
        equipment: "DIAMOND_HELMET,none,none,IRON_BOOTS"
        hand_equipment: "IRON_SWORD,SHIELD"
    - particle: "FLAME;30"
      location: "~;~2;~"
      force: true
    - sound: "ENTITY_WITHER_SPAWN;1;1"
      location: "~;~;~"

autocompletar_warp:
  trigger: player_tab_complete
  conditions: ["cmd.name == 'warp' && args.size <= 1"]
  actions:
    - tab_complete: "set;spawn,tienda,arena"

al_pescar:
  trigger: player_fish
  conditions: ["state == 'CAUGHT_FISH' && chance(5)"]
  actions:
    - set_caught_item: "DIAMOND;1;<aqua>Diamante del lago"
```
Funciones: `pick(lista)`, `block_at(mundo, x, y, z)`, `is_number(x)`. Placeholder `%omni_enabled_<regla>%`.
Comandos: `/omni interrupt <regla|*> [jugador] [-s]`; `-s` en `call`, `enable`, `disable`.

Compatibilidad: todo es nuevo y opcional; una configuración de la 1.9.0 carga igual. `json_message` deja de ser un
alias de `message`: un `json_message` con texto que no es JSON (antes se mandaba tal cual) ahora es error al cargar con
la ruta —se ve en `/omni validate`—.

## 3. Hilos y rendimiento
- `player_statistic` y `player_air_change` pueden dispararse muy seguido (aire: cada tick bajo el agua): solo existen
  sus oyentes si alguna regla los usa (III.1); la referencia lo avisa.
- `block_at` no carga chunks y solo corre en el hilo principal. `json_message` se convierte una vez al cargar (III.2).
- `interrupt` corre en el hilo principal (comando).

## 4. Persistencia
Sin cambios.

## 5. Amenazas (STRIDE)
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| Manipulación: `{expresiones}` con texto del jugador dentro de `json_message` | Etiquetas o clics inyectados | El JSON se pasa a MiniMessage al cargar y los valores se escapan al interpolar como en `message` (II.2) |
| Manipulación: `skull_owner: "{player.name}"` | Un nombre raro | Solo arma un perfil por nombre; el servidor lo resuelve (no OmniEvents) y un nombre inválido se queda sin piel |
| Denegación: `summon` con `amount` enorme o en un bucle | Lag por entidades | Tope de 50 por acción y los bucles con su tope (III.4) |
| Denegación: `block_at` en un temporizador sobre zonas lejanas | Carga de chunks | No carga chunks: nulo |
| Denegación: `player_air_change` en un servidor con agua | Un evento por tick | Solo con reglas que lo usen; documentado |
| Elevación: `/omni interrupt * ` corta las esperas de todos | Abuso de un comando administrativo | `omnievents.command.interrupt` con `default: op` |
| Repudio: `-s` oculta lo que hizo un comando | Acciones sin rastro en el chat | Solo quita la respuesta al que lo manda; la consola sigue registrando el comando |
| Divulgación | — | Nada nuevo sale del servidor |

## 6. Pruebas
- Unitarias: `Slots`, `ItemSpec` (flags con sugerencia, claves nuevas leídas), `json_message` (convierte, JSON inválido
  es error de carga, escape de texto del jugador en `tests:`), `pick`/`is_number`, `keep_inventory` con modo, los cuatro
  disparadores en `tests:` con `given`, `tab_complete` y `set_caught_item` registrados en una prueba, e importador (una
  configuración de CE con cada fila nueva → carga sin errores y sin REVISAR salvo `datavalue`).
- En el juego (3a): `give_item` con `slot`/`flags`/`durability` y `remove_item` con `name:` y `remove_item_slot` (el
  inventario del bot); `summon` con nombre, equipo y vida (la entidad cerca del bot); `/omni interrupt` (el segundo
  mensaje tras el `delay` no llega) y `-s`; `tab_complete` (el bot pide autocompletar y recibe la lista); `json_message`
  (el bot recibe el texto).

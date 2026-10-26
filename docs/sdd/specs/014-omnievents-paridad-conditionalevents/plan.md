# Plan 014 — Diseño técnico

## 1. Arquitectura

### 1.1 `centered_message` (`action/Actions.java`, `text/Centered.java`)
- Se compila como `message` (una `Template`). Al correr, el componente renderizado se recorre con su estilo
  (`Component.iterable` no da el estilo heredado: se recorre el árbol a mano pasando la negrita del padre) y se parte
  en líneas por `\n`. Cada línea mide `Σ (ancho(c) + 1)`, más 1 por letra en negrita; los espacios delante salen de
  `(154 - ancho/2) / 4` (espacio de 3 px + 1). 154 = la mitad de los 320 px del chat por defecto menos el margen.
- `text/Centered.java`: la tabla de anchos de la fuente por defecto de Minecraft (5 px salvo `i l ! . , : ; ' |` = 1,
  `` ` `` = 2, `I [ ] " t` = 3/4…, `@` = 6) y `pad(Component)` que devuelve el componente con los espacios delante.
  Pura (sin Bukkit): unitarias.
- Destinatarios como `message` (sujeto de `to`); sin jugador, a la consola sin centrar.

### 1.2 `firework` (`action/Firework.java`)
- `Firework.parse(String)` lee la forma de CE: pares `clave:valor` separados por espacios; claves `colors`, `type`,
  `power`, `fade`, `flicker`, `trail`, `location`, `damage` (y `shot_direction` se ignora con aviso: no se soporta).
  Si el texto no tiene `{`, se valida al cargar (clave, color, tipo, `power` 0–3) con «¿quisiste decir?»; si no, cada
  valor es una `Template` y se valida al correr (`ActionException`).
- Al correr: ubicación = `location` o la del sujeto (jugador o entidad de `to`) + 1 bloque arriba; se crea un
  `Firework` con `FireworkMeta` (`FireworkEffect.builder()…`, `setPower`) y, salvo `damage:true`, una marca en su PDC
  (`omnievents:no_damage`).
- `FireworkGuard` (oyente de `EntityDamageByEntityEvent` que cancela el daño de un cohete con la marca) se registra
  **la primera vez** que se lanza un cohete sin daño (III.1: sin cohetes, sin oyente) y se queda hasta el `onDisable`.

### 1.3 `item_repair` (`trigger/Triggers.java`)
- Un `TriggerType.event` más sobre `InventoryClickEvent` (como `inventory_click`, oyente propio por regla), con filtro:
  la vista es de yunque (`AnvilInventory`) o de afiladora (`GrindstoneInventory`), `rawSlot == 2`, el resultado no es
  aire y, en el yunque fuera de creativo, `player.level >= AnvilView.getRepairCost()`.
- Raíces: `item` (resultado), `repair_type` (`ANVIL`/`GRINDSTONE`), `base_item` (casilla 0), `addition` (casilla 1),
  `rename_text` (`AnvilView.getRenameText()`, vacío si es nulo) y `level_cost` (`getRepairCost()`, 0 en la afiladora).
  Cancelable: cancelar el clic deja todo como estaba (Bukkit no saca el resultado ni cobra).
- Grupo «ítems» en la referencia; en `tests:` se usa con `given` como cualquier otro.

### 1.4 Permiso para saltar el enfriamiento (`rule/Rule.java`, `RuleLoader`, `RuleManager`, `RuleOptions`)
- `Rule.cooldownBypass` (texto): `cooldown_bypass` o, si falta, `omnievents.bypasscooldown.<nombre de la regla>`.
- En `RuleManager` (disparo, `check` y simulación) `bypassCd = op && cooldown-bypass-op || p.isPermissionSet(perm) &&
  p.hasPermission(perm)`. La traza dice por qué está libre: «(OP)» o «(permiso …)». Igual que el OP: no se mira ni se
  activa el enfriamiento; el límite y el costo siguen.
- `cooldown_bypass` es una opción de regla registrada (`Kind.TEXT`), también en los clics de menú.

### 1.5 Reinicio con `*` (`command/OmniCommand.java`, `RuleManager`, `data/DataStore.java`)
- `/omni cooldown|limit reset <regla|*> [jugador|*|global]`. Reglas: una o, con `*`, todas las que tienen
  enfriamiento (o límite). Jugador: el que se nombra (como hoy), `global`, o `*`.
- Con `*`: en el hilo principal se limpian los `Holder` cargados (y el global en las reglas con alcance global);
  luego, en una tarea asíncrona, `DataStore.storedPlayers()` (los UUID de `data/players/*.yml`) y, por cada uno que
  **no** esté cargado, `peekOffline` → quitar las claves → `writeOffline` (escritura atómica). Al terminar se avisa al
  que mandó el comando, en el hilo principal, con cuántos jugadores cambiaron.
- Carrera conocida: si un jugador entra mientras se recorre su archivo, su reinicio puede perderse (lo cargó antes de
  escribirlo); se documenta. No hay pérdida de otros datos: solo se quitan claves de `cooldowns`/`uses`.

### 1.6 Importador (`importer/ConditionalEventsImporter.java`)
- Tipos: `citizens_right_click_npc` → `npc_click`, `wgevents_region_enter/leave` → `region_enter/leave`,
  `item_repair` → `item_repair`, `item_move` → `inventory_click`, `player_swap_hand`/`player_offhand` →
  `player_swap_hands`, `player_kill_entity`. Los que no tienen equivalente (`player_statistic`, `player_change_air`,
  `player_tab_complete`, `protocollib_receive_message`, `player_receive_chat`) quedan con `trigger` original y una
  nota (el cargador dirá «disparador desconocido» con la ruta: se ve).
- Variables: se suman las de cada evento de la wiki con equivalente (§ tablas de la spec) y las globales:
  `%item_lore_line_N%` → `item.lore[N-1]`, `%playerarmor_x%` → `player.x.type`, `%victim_health%` → `target.health`,
  `%world_time_<mundo>%` → `world.time` cuando el mundo es `{player_world}` (con otro mundo, REVISAR),
  `%world_is_raining%` → `world.storm`, `%time%` → `var.cooldown_left`, `%to:x%` → la expresión de `x`
  sobre el destinatario (solo `player` y `player_name`; lo demás PAPI con `papi('%x%', player)` dentro de `where`).
- Acciones: `centered_message`, `firework` (tal cual), `title` (CE `entrada;estancia;salida;título;subtítulo`),
  `actionbar` (sin duración), `playsound` con ubicación `x,y,z,mundo` → `sound` + `location`? (`sound` no tiene
  ubicación: REVISAR con nota), `give_item`/`drop_item` → forma de mapa con `material`, `amount`, `name`, `lore`,
  `enchants`, `model` (de `custom_model_data`), `saved` (de `saved_item`) y `location` en `drop_item`; las claves sin
  equivalente (`flags`, `skull_*`, `slot`…) van a la nota «REVISAR» sin perder el ítem. `summon` y `particle` con
  sus claves conocidas; `set_block` `location:…;id:…;block_data:…` → `set_block: mundo;x;y;z;MATERIAL[datos]`;
  `keep_items` → `keep_inventory`; `set_event_xp` → `set_event: "exp_to_drop = N"`; `execute_action_group` →
  `random` con `weight`; `throw_coordinate`/`vector` → `velocity`, `throw_directional` → `launch`;
  `stopsound_resource_pack` → `stop_sound`.
- Destinos: `to_range: r;false` → `to: nearby:r`, `r;true` → `radius:r`; con el tercer valor (entidades) → nota.
  `to_condition: g` → `to: all` + `where` con las condiciones del grupo (`Config.to_condition_groups`) traducidas.
- `execute grupo{%v%=x;…}` → el grupo con `set: var.v = 'x'` delante y `%v%` → `var.v` dentro.
- `prevent_cooldown_activation` / `prevent_one_time_activation`: `no_cooldown` al inicio de esos grupos (con nota si
  el efecto difiere: `no_cooldown` libera los dos).
- Permiso: si existe un `cooldown`, `cooldown_bypass: conditionalevents.bypasscooldown.<evento>` (el que ya usan los
  rangos del servidor que migra).

### 1.7 Registro, esquema y referencia
`centered_message` y `firework` (`Actions.Doc`, grupo mensajes y mundo), `item_repair` (`Triggers`, grupo ítems),
`cooldown_bypass` (`RuleOptions`); `./gradlew :omnievents:schema` regenera esquema y referencia.

## 2. Contrato de configuración
```yaml
anuncio:
  trigger: command
  command: anuncio
  actions:
    - centered_message: "<gold><b>EVENTO</b></gold>\n<gray>Empieza en {var.min} minutos"
    - to: all
      firework: "colors:RED,#FFAA00 type:STAR power:1 fade:WHITE trail:true"

recompensa_reparar:
  trigger: item_repair
  conditions:
    - "repair_type == 'ANVIL' && rename_text contains 'Excalibur'"
  actions:
    - cancel
    - message: "<red>Ese nombre está reservado."

kit_diario:
  trigger: command
  command: kit
  cooldown: 1d
  cooldown_bypass: rangos.vip.kit   # sin la opción: omnievents.bypasscooldown.kit_diario
  actions:
    - give_item: "BREAD;16"
```
Claves de `firework`: `colors` (obligatoria, lista con comas), `type`, `power`, `fade`, `flicker`, `trail`,
`location`, `damage`. Raíces de `item_repair`: `item`, `repair_type`, `base_item`, `addition`, `rename_text`,
`level_cost`.

Comandos: `/omni cooldown reset <regla|*> [jugador|*|global]`, `/omni limit reset <regla|*> [jugador|*|global]`.

Compatibilidad: todo es nuevo y opcional; una configuración de la 1.8.1 carga igual. Única diferencia de
comportamiento: un jugador con `omnievents.bypasscooldown.<regla>` **dado** salta ese enfriamiento (antes no
existía el permiso; nadie lo tiene por defecto, ni los OP).

## 3. Hilos y rendimiento
- `centered_message` mide en el hilo de la acción (puro, sin E/S). `firework` y `item_repair` solo en el principal.
- El oyente de `item_repair` existe solo si alguna regla lo usa (como todo disparador); el de los cohetes, solo
  después del primer cohete sin daño.
- El reinicio con `*` lee y escribe archivos fuera del hilo principal (III.2).

## 4. Persistencia
Sin formato nuevo. `reset *` quita claves de `cooldowns`/`uses` de los archivos de jugadores con la escritura atómica
de siempre (`DataStore.write`).

## 5. Amenazas (STRIDE)
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| Elevación: un jugador se da el permiso de bypass | El permiso lo da el gestor de permisos; sin él, nada cambia. Que no se otorgue por defecto a OP | `isPermissionSet` (no basta el `default: op` implícito de un permiso no declarado) |
| Manipulación: `rename_text` con `<red>`, `§` o `; op` | El nombre lo escribe el jugador | Es texto de jugador: al interpolarlo se escapa como `message` (constitución II.2); prueba unitaria |
| Denegación: `firework` en un bucle o `to: all` | Muchas entidades | Los bucles ya tienen tope (`max-loop-iterations`); un cohete por destinatario |
| Daño no deseado: un cohete con estrellas lastima a los cercanos | Jugadores heridos por un anuncio | Sin daño por defecto (marca en el PDC + `FireworkGuard`) |
| Repudio / pérdida: `reset * *` borra los enfriamientos de todos | Acción masiva por error | Solo con `omnievents.command.cooldown`/`limit` (`default: op`); el mensaje dice cuántos cambió; solo toca esas claves |
| Divulgación | — | Nada nuevo sale del servidor |
| Importador: JSON o comandos de CE con texto del jugador | Ya lo trata la 001 (escape al interpolar) | Sin `player_command_as_op` (queda REVISAR) |

## 6. Pruebas
- Unitarias: `Centered` (anchos, negrita, varias líneas, línea demasiado larga), `Firework.parse` (forma de CE,
  errores con sugerencia, `{expresiones}`), `cooldown_bypass` en `RuleLoader`/`check`, `item_repair` en `tests:`,
  `reset *` sobre un `DataStore` en una carpeta temporal (cargados y desconectados), e importador (una configuración
  de CE con cada fila → carga sin errores; REVISAR solo donde corresponde).
- En el juego (3a): `centered_message` (el bot recibe la línea con los espacios esperados), `firework` (aparece un
  cohete cerca del bot y el bot no pierde vida), `item_repair` en la afiladora y en el yunque con nombre (el bot pone
  el ítem y toma el resultado; la regla corre y una cancela), bypass (sin permiso → `on_cooldown`; con
  `lp`/`omni`… dado por consola → pasa dos veces) y `reset * *`.

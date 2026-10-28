---
description: "Every event a rule can react to, and the values each one gives you."
---

# Triggers

The `trigger` of a rule is the event that runs it. Each trigger gives the rule some **values** (also called roots)
that you can read in conditions and texts: `block.type`, `{target.name}`, `cause == 'FALL'`.

These values exist in every rule (some can be empty, for example `player` in `server_start`):
`player`, `event`, `world`, `location`, `item` (the item in the main hand), `server`, `online` (number of players
online), `rule` (this rule's name), `source`, `target`, `block` and `entity`.

"Cancellable" means the `cancel` action stops the event (the block is not broken, the message is not sent…).
`/omni triggers` lists the triggers in game.

Older ConditionalEvents names also work: `player_kill_entity` is `player_kill`, `player_leave` is `player_quit`, and
`repetitive` and `repetitive_server` are `timer`.

## Player
| Trigger | Extra values | When it runs |
|---|---|---|
| `player_join` | `first_join` | A player joins (`first_join` is `true` the first time). |
| `player_quit` | `reason` | A player leaves. |
| `player_pre_login` | `name`, `uuid`, `ip` | Before a player joins, off the main thread: there is no player yet. `deny_join` rejects them. |
| `player_respawn` | `reason` | A player respawns. |
| `player_death` | `killer`, `cause` | A player dies (`killer` if another player killed them). Cancellable. |
| `player_chat` | `message` | A player chats (off the main thread; `message` is the plain text). Cancellable. |
| `player_command` | `cmd`, `args` | A player types any command, from any plugin. Cancellable. |
| `player_move` | `from`, `to` | A player moves (see the `move_mode` option). `player.location` is still where they came from: use `to.block` and `to.y` for where they arrive. Cancellable. |
| `player_teleport` | `from`, `to`, `cause` | A player is teleported. Cancellable. |
| `player_world_change` | `from_world` | A player changes world. |
| `player_sneak` | `sneaking` | A player starts or stops sneaking. Cancellable. |
| `player_sprint` | `sprinting` | A player starts or stops sprinting. Cancellable. |
| `player_jump` | `from`, `to` | A player jumps. Cancellable. |
| `player_level_change` | `old_level`, `new_level` | A player's experience level changes. |
| `player_gamemode_change` | `new_mode` | A player's game mode changes. Cancellable. |
| `player_toggle_flight` | `flying` | A player starts or stops flying. Cancellable. |
| `player_bed_enter` | `block`, `result` | A player tries to sleep. Cancellable. |
| `player_fish` | `target`, `state` | A player fishes (`state` of the rod, `target` what was caught). Cancellable. |
| `player_regain_health` | `amount`, `reason` | A player heals. Cancellable. |
| `player_food_change` | `old_food`, `new_food` | A player's hunger changes. Cancellable. |
| `player_advancement` | `advancement` | A player gets an advancement (recipes are skipped). |
| `player_swap_hands` | `item`, `offhand` | A player swaps hands (F key). Cancellable. |
| `player_armor_change` | `item`, `old_item`, `slot` | A player puts on or takes off armor. |
| `player_portal` | `from`, `to`, `cause` | A player uses a portal. Cancellable. |
| `player_kick` | `reason` | A player is kicked. Cancellable. |
| `player_resource_pack` | `status` | A player answers the resource pack prompt. |
| `player_statistic` | `statistic`, `previous_value`, `new_value`, `material`, `entity_type` | A statistic goes up (`JUMP`, `MOB_KILLS`…). Some, like walking, change very often. Cancellable. |
| `player_air_change` | `old_air`, `new_air`, `max_air` | A player's air changes (in ticks; 300 is full). Runs every tick under water. Cancellable. |
| `player_tab_complete` | `buffer`, `cmd`, `args`, `last`, `completions` | A player asks to tab-complete a plugin command. The `tab_complete` action changes the suggestions. Cancellable. |

## Combat
| Trigger | Extra values | When it runs |
|---|---|---|
| `player_damage` | `cause`, `damage`, `damager` | A player takes damage (`damager` if an entity or a projectile did it). Cancellable. |
| `player_attack` | `target`, `damage`, `projectile` | A player hits an entity, directly or with a projectile. Cancellable. |
| `player_kill` | `target` | A player kills an entity. Cancellable. |
| `player_shoot` | `projectile`, `force` | A player shoots a bow or a crossbow. Cancellable. |
| `projectile_hit` | `target`, `block`, `projectile` | A player's projectile hits an entity or a block. Cancellable. |

## Blocks
| Trigger | Extra values | When it runs |
|---|---|---|
| `block_break` | `block` | A player breaks a block. Cancellable. |
| `block_place` | `block`, `against` | A player places a block (`against`: the block it was placed on). Cancellable. |
| `block_interact` | `block`, `action`, `face` | A player clicks a block with the main hand (`action`: `RIGHT_CLICK`, `SHIFT_LEFT_CLICK`…). Cancellable. |
| `sign_change` | `lines`, `block` | A player writes a sign. Cancellable. |
| `bucket_fill` | `block` | A player fills a bucket. Cancellable. |
| `bucket_empty` | `block`, `bucket` | A player empties a bucket. Cancellable. |

## Items
| Trigger | Extra values | When it runs |
|---|---|---|
| `player_interact` | `action`, `block` | A player clicks, with or without a block, with the main hand. Cancellable. |
| `item_interact` | `action`, `block` | A player clicks while holding an item in the main hand. Cancellable. |
| `item_consume` | — | A player eats or drinks. Cancellable. |
| `item_craft` | `click` | A player crafts an item (`item` is the result). Cancellable. |
| `item_drop` | `entity` | A player drops an item. Cancellable. |
| `item_pickup` | `entity` | A player picks up an item. Cancellable. |
| `item_held` | `previous_item`, `slot` | A player changes the selected hotbar slot. Cancellable. |
| `item_enchant` | `enchants`, `cost` | A player enchants an item on the table. Cancellable. |
| `item_repair` | `repair_type`, `base_item`, `addition`, `rename_text`, `level_cost` | A player takes the result of an anvil or a grindstone (`repair_type`: `ANVIL` or `GRINDSTONE`). Cancellable. |
| `item_mend` | `amount`, `orb` | Mending repairs a player's item with an experience orb. Cancelling gives the experience instead. Cancellable. |
| `item_break` | — | A player's tool or armor breaks. |

## Inventory
| Trigger | Extra values | When it runs |
|---|---|---|
| `inventory_open` | `title`, `inventory_type` | A player opens an inventory. Cancellable. |
| `inventory_close` | `title`, `inventory_type` | A player closes an inventory. |
| `inventory_click` | `cursor`, `slot`, `raw_slot`, `click`, `title`, `inventory_type` | A player clicks in an inventory. Cancellable. |

## Entities
| Trigger | Extra values | When it runs |
|---|---|---|
| `entity_interact` | `target` | A player right-clicks an entity. Cancellable. |
| `entity_spawn` | `entity`, `reason` | A creature spawns (no player). Cancellable. |
| `entity_death` | `entity`, `killer` | An entity dies (`player` and `killer` if a player killed it). Cancellable. |

## Server
| Trigger | Extra values | When it runs |
|---|---|---|
| `console_command` | `cmd`, `args`, `sender` | The console runs a command. Cancellable. |
| `server_start` | — | When the server starts and OmniEvents has loaded. |
| `server_stop` | — | When the server stops. |
| `timer` | — | Every so often (`every: 5m`); by default once for each online player (`per_player`). |
| `clock` | `time` | At a time of day (`at: "20:00"`, `timezone`). |
| `call` | `sender`, `caller` | Only when another rule calls it (`call` action) or with `/omni call`. Its `args` arrive as `var.<name>`. |
| `command` | `sender`, `cmd`, `args` | Registers a real command (`command:` option) and runs when it is used. |
| `state` | — | When the `when` expression becomes true (`on_enter`) or false again (`on_exit`). See [state rules](features/state-rules.md). |
| `var_expire` | `variable`, `value`, `scope` | A variable with `expire` runs out. See [variables](variables.md#expiring-variables). |
| `custom` | — | Any Bukkit or plugin event by its class name (`event:` option). |

## Integrations
These need the optional plugin; without it, the rule loads with a warning and does not run.

| Trigger | Extra values | When it runs |
|---|---|---|
| `region_enter` | `region` | A player enters a WorldGuard region (`region.id`). Cancelling keeps them out. |
| `region_leave` | `region` | A player leaves a WorldGuard region. Cancelling keeps them inside. |
| `npc_click` | `npc`, `target` | A player right-clicks a Citizens NPC (`npc.id`, `npc.name`). Cancellable. |
| `npc_left_click` | `npc`, `target` | A player hits a Citizens NPC. Cancellable. |
| `player_receive_chat` | `message`, `message_legacy`, `json`, `type` | A player is about to receive a chat or system message (needs ProtocolLib; off the main thread). Cancelling hides it. |

## Menus
You never write these by hand: a [menu](features/menus.md) creates them for its open, click and close actions.
They show up in `/omni list` and `/omni debug`.

| Trigger | Extra values | When it runs |
|---|---|---|
| `menu_open` | `menu` | A menu is opened with `open_menu`. |
| `menu_click` | `menu`, `slot`, `click` | A player clicks an item of a menu. |
| `menu_close` | `menu` | A menu is closed. |

## Example: one trigger, several rules
```yaml
no_fall_damage_in_spawn:
  trigger: player_damage
  conditions:
    - "cause == 'FALL'"
    - "player.world.name == 'spawn'"
  actions:
    - cancel

zombie_bounty:
  trigger: player_kill
  conditions: "target.type == 'ZOMBIE'"
  actions:
    - set: "pvar.zombies = default(pvar.zombies, 0) + 1"
    - actionbar: "<green>Zombies killed: {pvar.zombies}"
```

---
description: "Every action a rule can run, from chat messages to explosions."
---

# Actions

`actions` is a list. Each action can be written in two equivalent ways:

```yaml
greet:
  trigger: player_join
  actions:
    - message: "Hello"          # as a map
    - "message: Hello again"    # as a text (the ConditionalEvents style)
```

Values with several parts are separated by `;`: `title: "Title;Subtitle;10;70;20"`. Texts accept MiniMessage,
`&` codes and `{expressions}`.

## Modifiers
Any action can take these extra keys:

| Modifier | What it does |
|---|---|
| `to` | Who receives it: `self` (default), `target`, `all`, `world:<world>`, `radius:<n>` (players), `nearby:<n>` (living entities), `player:<name>` or an expression. |
| `where` | Filter for each receiver of `to` (inside it, `player` is the receiver and `source` the original player). |
| `chance` | Probability, in percent, that the action runs. |
| `when` | The action only runs if this expression is true. |

```yaml
lucky_break:
  trigger: block_break
  conditions: "block.type == 'STONE'"
  actions:
    - give_item: "EMERALD"
      chance: 2
    - message: "<gold>{player.name} is mining nearby"
      to: "radius:30"
      where: "player.name != source.name"
```

## Flow control
| Action | What it does |
|---|---|
| `if` | `if: "expression"` with `then:` and `else:` lists. |
| `repeat` | `repeat: 3` with `do:`; the round number (from 0) is `var.i`, or the name you give in `as:`. |
| `while` | `while: "expression"` with `do:`, with a safety limit of rounds (`config.yml`). |
| `for_each` | `for_each: all` (or `world:<w>`, `radius:<n>`…) with `do:`; inside, `player` is each receiver. |
| `random` | Picks one branch at random by its `weight` and runs its `do`. |
| `delay` | Waits before the next actions: `delay: 2s`. |
| `wait_ticks` | Waits a number of ticks (20 ticks = 1 second). |
| `stop` | Stops the rest of this run. |
| `call` | Runs another rule with `trigger: call`, with arguments: `call: "reward; amount=10"`. |
| `set` | Sets a [variable](variables.md): `set: "pvar.coins += 5"` (also `-=`, `*=`, `/=`), optionally with `expire:`. |
| `unset` | Deletes a variable. |
| `no_cooldown` | This run does not count for the cooldown or the limit. |
| `reset_cooldown` | Resets a rule's cooldown for the player. |

```yaml
mystery_box:
  trigger: command
  command: box
  cooldown: 1h
  actions:
    - random:
        - weight: 8
          do:
            - give_item: "IRON_INGOT;5"
        - weight: 2
          do:
            - give_item: "DIAMOND"
            - broadcast: "<aqua>{player.name} found a diamond in the box!"
    - repeat: 3
      do:
        - sound: "BLOCK_NOTE_BLOCK_PLING;1;1.5"
        - delay: 5t
```

## Messages
| Action | Value |
|---|---|
| `message` | Text sent to the player. |
| `centered_message` | Like `message`, with each line centered in the chat. |
| `json_message` | A Minecraft JSON text (click and hover events). |
| `broadcast` | Text sent to the whole server. |
| `actionbar` | Text above the hotbar; `text;ticks` or the `duration:` key keeps it visible longer. |
| `title` | `title;subtitle;fade_in;stay;fade_out` (times in ticks; `none` leaves one empty). |
| `bossbar` | `text;seconds;color;progress` (progress from 0 to 1). |
| `sound` | `SOUND;volume;pitch`; `location:` plays it at a place so nearby players hear it. |
| `stop_sound` | Stops a sound, or `all`. |
| `console_message` | Writes a message in the console. |
| `log` | Writes in the server log, with the rule name. |

## Commands
| Action | Value |
|---|---|
| `console` | Runs a command as the console: `console: "give {player.name} diamond 1"`. |
| `command` | Runs a command as the player. |
| `chat` | Makes the player say something in chat. |
| `send_to_server` | Sends the player to another server of a BungeeCord or Velocity network. |

## Player
| Action | Value |
|---|---|
| `teleport` | `world;x;y;z;yaw;pitch` (`~` is relative: `~;~10;~`). |
| `heal`, `feed` | Restore health or hunger (an amount, or full without one). |
| `set_food` | Set the hunger level. |
| `damage` | Damage the player (an amount of health). |
| `gamemode` | `SURVIVAL`, `CREATIVE`, `ADVENTURE` or `SPECTATOR`. |
| `fly` | `true` or `false`. |
| `effect` | `EFFECT;seconds;level;particles;icon`. |
| `remove_effect` | An effect, or `all`. |
| `fire`, `freeze` | Set on fire, or freeze like powder snow, for a number of ticks. |
| `velocity`, `launch` | Set the velocity (`x;y;z`), or push the player where they look (`launch: 2`). |
| `give_item` | `MATERIAL;amount;name;lore 1\|lore 2`, or a map (below). |
| `drop_item` | Like `give_item`, dropped on the ground. |
| `remove_item` | `MATERIAL;amount`, optionally `;name:…` or `;lorecontains:…`. |
| `remove_item_slot` | `SLOT;amount` (`HAND`, `OFF_HAND`, `HELMET`… or 0 to 40). |
| `set_caught_item` | Changes what a `player_fish` rule catches. |
| `clear_inventory`, `close_inventory` | Empty the inventory, or close the open one. |
| `give_exp`, `give_level` | Give experience points or levels. |
| `give_money`, `take_money` | Vault money. `take_money` takes nothing if the balance is not enough. |
| `open_menu`, `close_menu` | Open or close a [menu](features/menus.md). |
| `kick` | Kick the player with a reason. |

`give_item` as a map accepts `material`, `amount`, `name`, `lore`, `enchants`, `glow`, `unbreakable`, `flags`,
`durability`, `model`, `item_model`, `skull_owner`, `skull_texture`, `slot` and `slot_replace`:

```yaml
starter_sword:
  trigger: player_join
  conditions: "first_join"
  actions:
    - give_item:
        material: IRON_SWORD
        name: "<aqua>Starter sword"
        lore: ["<gray>Welcome to the server"]
        enchants: ["SHARPNESS:1"]
        unbreakable: true
        slot: 0
```

## World
| Action | Value |
|---|---|
| `set_block` | `MATERIAL[data]` changes the event's block; `[world;]x;y;z;MATERIAL[data]` changes that one (`~;~-1;~;GOLD_BLOCK`). |
| `lightning` | A harmless lightning strike at the player, or at `world;x;y;z`. |
| `explosion` | `power;fire;break_blocks`. |
| `particle` | `PARTICLE;count;dx;dy;dz;speed`, with optional `location`, `force` and `for_player`. |
| `summon` | An entity type, or a map with `entity`, `location`, `name`, `health`, `amount`, `scale`, `equipment`… |
| `firework` | `colors:RED,#FFAA00 type:STAR power:1 fade:WHITE flicker:true trail:true`. |

## Event
These change the event that ran the rule.

| Action | Value |
|---|---|
| `cancel` | Cancels the event (only in cancellable [triggers](triggers.md)). |
| `set_event` | `property = expression`, for any property of the event. |
| `set_damage` | `10` or `150%`. |
| `set_message` | Changes the chat message in `player_chat`. |
| `set_join_message`, `set_quit_message`, `set_death_message` | Change those messages (`none` hides them). |
| `keep_inventory` | `items`, `xp` or `all`, in `player_death`. |
| `cancel_drops` | Nothing drops (in `block_break`, `entity_death`, `player_kill`). |
| `deny_join` | Rejects the player in `player_pre_login`, with a reason. |
| `tab_complete` | `set\|add\|remove\|clear;a,b,c`, in `player_tab_complete`. |

The exact value, keys and an example of every action are in the
[generated reference](https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/docs/omnievents/referencia.md#acciones)
(in Spanish). `/omni actions` lists them in game.

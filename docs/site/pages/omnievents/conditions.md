---
description: "Write conditions with a real expression language, and every built-in function."
---

# Conditions and expressions

## Conditions
`conditions` is one expression or a list of expressions. **All** of them must be true for the actions to run. If one is
false, the rule's `else` runs.

```yaml
night_miner:
  trigger: block_break
  conditions:
    - "block.type == 'DIAMOND_ORE' || block.type == 'DEEPSLATE_DIAMOND_ORE'"
    - "player.gamemode == 'survival' && !player.hasPermission('staff')"
    - "player.world.time > 13000"
  actions:
    - message: "<aqua>A diamond at night!"
  else:
    - actionbar: "<gray>Nothing special."
```

Quote each expression (`"…"`): YAML treats some characters (`:`, `#`, `!`, `[`) specially.

### A message for each failed condition
Write a condition as a map with `expr` and its own `deny` actions. When it fails, only its `deny` runs (not the
`else`), so the player knows exactly what is missing.

```yaml
sword_shop:
  trigger: command
  command: sword
  conditions:
    - expr: "player.level >= 5"
      deny:
        - message: "<red>You need level 5 (you have {player.level})."
    - expr: "has_item('EMERALD', 3)"
      deny:
        - message: "<red>You need 3 emeralds."
  actions:
    - remove_item: "EMERALD;3"
    - give_item: "DIAMOND_SWORD"
```

### At least N of M
`any: N` with `of: [...]` passes when at least N of its conditions are true. Groups can be nested.

```yaml
night_vip_door:
  trigger: block_interact
  conditions:
    - "block.type == 'IRON_DOOR'"
    - any: 2
      of:
        - "perm('rank.vip')"
        - "has_item('EMERALD', 1)"
        - "player.world.time > 12000"
      deny:
        - message: "<red>You need 2 of 3: be VIP, carry an emerald, or come at night."
  actions:
    - message: "<green>Welcome in."
```

## Expressions
Expressions appear in `conditions`, between `{ }` in texts, and in many action values.

| Kind | Syntax |
|---|---|
| Logic | `&&` or `and`, `\|\|` or `or`, `!` or `not`, parentheses, `condition ? a : b` |
| Comparison | `==`, `!=`, `<`, `<=`, `>`, `>=`, `~=` (equal ignoring case) |
| Text and lists | `contains`, `startsWith`, `endsWith`, `matches` (regular expression), `in`; negate with `!contains`, `not in`… |
| Math | `+ - * / %` (`+` joins texts) |
| Lists | `[1, 2, 3]`, `['A', 'B']`, `args[0]` |
| Text | `'single'` or `"double"` quotes |

Examples:

```text
player.health < 6 && !player.flying
lower(message) contains 'discord'
block.type in ['OAK_LOG', 'BIRCH_LOG'] && random(1, 100) <= 25
player.level >= 30 ? 'veteran' : 'newbie'
```

### Reading game values
Start from any value the trigger gives you (see [triggers](triggers.md)) and follow properties with dots:

- `player.name`, `player.health`, `player.level`, `player.gamemode`, `player.world.name`
- `player.location.y`, `player.location.block.type`, `player.location.biome`
- `item.type`, `item.amount`, `item.name`, `block.type`, `target.type`, `event.damage`

Any getter of the Paper API works in `snake_case`: `event.final_damage` calls `getFinalDamage()`. Methods that change
things are blocked, so an expression can never modify the world. Try any expression in game with
`/omni eval player.location.biome`.

### Variables and placeholders
- `var.x`, `pvar.x`, `gvar.x` and `block.data.x` read [variables](variables.md).
- With PlaceholderAPI, `%vault_eco_balance% > 100` works directly (a numeric result is compared as a number), and
  `papi('%player_ping%')` resolves a placeholder inside an expression.

### Texts
In any text, `{expression}` is replaced by its value and `%placeholder%` goes through PlaceholderAPI. What a player
typed (a chat message, a sign, command arguments) is **never** read as MiniMessage when you insert it with `{ }`, so
players cannot inject clickable or colored text.

## Functions
| Function | What it returns |
|---|---|
| `abs(x)` | The absolute value. |
| `block_at(world, x, y, z)` | The block at those coordinates, or null if the chunk is not loaded (it never loads chunks). |
| `bool(x)` | `x` as true or false. |
| `ceil(x)` | `x` rounded up. |
| `chance(p)` | `true` with a probability of `p` percent. |
| `clamp(x, min, max)` | `x` kept between `min` and `max`. |
| `cooldown(rule)` | Seconds left of the player's cooldown in that rule. |
| `date([pattern])` | Today's date with a Java pattern (`yyyy-MM-dd` by default). |
| `default(x, other)` | `x`, or `other` if `x` is null or empty. |
| `distance(a, b)` | Distance between two locations, entities or blocks (-1 in different worlds). |
| `floor(x)` | `x` rounded down. |
| `format(x[, decimals])` | A number with thousands separators (1,234.5). |
| `has_item(material[, n])` | `true` if the player carries at least `n` (default 1) of that material, armor not counted. |
| `hour()` | The server's hour of the day (0 to 23). |
| `if(condition, a, b)` | `a` if the condition is true, otherwise `b`. |
| `in_area(world, x1, y1, z1, x2, y2, z2)` | `true` if the player is inside that box. |
| `in_region(id)` | `true` if the player is in that WorldGuard region (false without WorldGuard). |
| `in_state(rule)` | `true` if the player is in that [state rule](features/state-rules.md). |
| `int(x)` | `x` as a whole number (0 if it is not a number). |
| `is_bedrock()` | `true` if the player joined from Bedrock (needs Floodgate). |
| `is_npc(entity)` | `true` if the entity is a Citizens NPC. |
| `is_number(x)` | `true` if `x` is a number or a text with a number. |
| `is_outside([entity])` | `true` if there is nothing but air above the player, up to the sky. |
| `join(list[, separator])` | The list as one text (`", "` by default). |
| `len(x)` | The length of a text, or the size of a list or a map. |
| `location(world, x, y, z)` | A location (null if the world does not exist). |
| `looking_block(distance[, entity])` | The block the player is looking at, up to that distance, or null. |
| `looking_entity(distance[, entity])` | The entity the player is looking at, up to that distance, or null. |
| `lower(t)` | The text in lowercase. |
| `max(a, b, …)` | The largest number (also of a list). |
| `min(a, b, …)` | The smallest number (also of a list). |
| `minute()` | The minute of the hour (0 to 59). |
| `money()` | The player's balance in the Vault economy. |
| `money_format(n)` | The amount with the economy's currency (`$1,500.00`). |
| `near(a, b, radius)` | `true` if `a` and `b` are in the same world, within `radius` blocks. |
| `now()` | Milliseconds since 1970 (server time). |
| `num(x)` | `x` as a number (0 if it is not one). |
| `online(name)` | `true` if that player is online. |
| `papi(text[, player])` | The text with its PlaceholderAPI placeholders resolved (a number if the result is a number). |
| `perm(permission[, player])` | `true` if the player has the permission. |
| `pick(list)` | A random element of the list. `pick(players()).name` is a random online player. |
| `player(name)` | The online player with that name, or null. |
| `players()` | The list of online players. |
| `pow(x, y)` | `x` to the power of `y`. |
| `pvar_of(player, name)` | Another player's `pvar` (by name). |
| `random(a, b)` | A random whole number from `a` to `b`, both included. |
| `random_last()` | The last number `random(a, b)` returned in this run. |
| `randomf(a, b)` | A random decimal number between `a` and `b`. |
| `regions()` | The WorldGuard regions the player is in, highest priority first. |
| `relative(entity, forward, side, up)` | A location moved from the entity according to where it looks (right and up are positive). |
| `replace(t, find, put)` | The text with every `find` replaced by `put`. |
| `round(x[, decimals])` | `x` rounded (to 0 decimals by default). |
| `size(x)` | Same as `len`. |
| `slice(x, from[, to])` | Part of a list or a text (positions from 0; `to` not included). `join(slice(args, 1), ' ')` joins every argument after the first. |
| `split(t, separator)` | The text cut into a list. |
| `sqrt(x)` | The square root. |
| `str(x)` | `x` as text. |
| `strip(t)` | The text without colors or MiniMessage tags. |
| `substr(t, from[, to])` | Part of a text (positions from 0). |
| `trim(t)` | The text without spaces at the ends. |
| `ttl(variable)` | Seconds left before a variable with `expire` runs out; -1 if it never expires. |
| `upper(t)` | The text in uppercase. |
| `uses(rule)` | How many times the player has used that rule. |
| `weekday()` | The day of the week: 1 is Monday, 7 is Sunday. |
| `world(name)` | The world with that name, or null (`world('world_nether').time`). |

`block_at`, `is_outside`, `looking_block` and `looking_entity` only work on the main thread: you cannot use them in
`player_chat`, `player_pre_login` or `player_receive_chat`. A block also has `.states` (`facing=north,half=bottom`).
Every function with its exact signature is in the
[generated reference](https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/docs/omnievents/referencia.md#funciones)
(in Spanish).

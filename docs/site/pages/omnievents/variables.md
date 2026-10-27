---
description: "Remember things per run, per player, server-wide, or on blocks, entities and items."
---

# Variables

OmniEvents has its own variables, so you do not need another plugin to remember things.

| Variable | Lives | Saved to disk | Example |
|---|---|---|---|
| `var.<name>` | During one run of the rule | No | `var.price` |
| `pvar.<name>` | Per player | Yes | `pvar.coins` |
| `gvar.<name>` | Server-wide | Yes | `gvar.event_running` |
| `<object>.data.<name>` | On a block, an entity or an item | Yes, inside the object | `block.data.owner` |

Read them in any expression (`pvar.coins >= 100`) or text (`{pvar.coins}`). A variable that was never set is null:
use `default(pvar.coins, 0)` when you need a number.

## Setting and deleting
```yaml
coin_shop:
  trigger: command
  command: buyfood
  conditions:
    - expr: "default(pvar.coins, 0) >= 10"
      deny:
        - message: "<red>You need 10 coins. You have {default(pvar.coins, 0)}."
  actions:
    - set: "pvar.coins -= 10"
    - give_item: "COOKED_BEEF;8"
    - message: "<green>Bought! Coins left: {pvar.coins}"
    - set: "gvar.food_sold = default(gvar.food_sold, 0) + 1"
```

- `set: "scope.name = expression"`, and also `+=`, `-=`, `*=`, `/=`.
- `unset: "pvar.coins"` deletes a variable.
- Another player's variable: `pvar_of('Alex', 'coins')`.

From the console or in game:

```text
/omni var get Alex coins
/omni var set Alex coins 50
/omni var set global event_running true
/omni var list Alex
/omni var del Alex coins
```

## Expiring variables
Add `expire:` to a `set` and the variable deletes itself after that time. Each new `set` with `expire` starts the
countdown again. It survives restarts.

```yaml
kill_streak:
  trigger: player_kill
  conditions: "target.type == 'ZOMBIE'"
  actions:
    - set: "pvar.streak = default(pvar.streak, 0) + 1"
      expire: 2m
    - actionbar: "Streak x{pvar.streak} · {round(ttl(pvar.streak))}s left"

streak_lost:
  trigger: var_expire
  variables: [streak]
  actions:
    - message: "<red>You lost your streak of {value}."
```

`ttl(pvar.streak)` gives the seconds left (-1 if it never expires). The `var_expire` trigger runs when a `pvar` of an
online player or a `gvar` expires, with `variable`, `value` (its last value) and `scope`.

## Data on blocks, entities and items
`.data.<name>` stores a variable inside the object itself (Paper's persistent data container):

```yaml
chest_owner:
  trigger: block_place
  conditions: "block.type == 'CHEST'"
  actions:
    - set: "block.data.owner = player.name"

chest_guard:
  trigger: block_interact
  conditions:
    - "block.type == 'CHEST'"
    - "block.data.owner != null && block.data.owner != player.name"
  actions:
    - cancel
    - message: "<red>This chest belongs to {block.data.owner}."
```

- An item's data **travels with the item**. Write to `player.item` (the main hand), not to the `item` of events like
  crafting or dropping, which can be a copy.
- A block's data is deleted when the block is gone (broken, exploded, burned, moved by a piston).
- Players in creative mode can create items with any data: do not trust `item.data` for them.
- `/omni data block`, `/omni data entity` and `/omni data hand` show the data of what you are looking at or holding.

## PlaceholderAPI
With PlaceholderAPI, other plugins (scoreboards, tab lists, holograms) can show OmniEvents values:

| Placeholder | Value |
|---|---|
| `%omni_var_<name>%` | The player's `pvar` |
| `%omni_gvar_<name>%` | A `gvar` |
| `%omni_cooldown_<rule>%` | Seconds of cooldown left in a rule |
| `%omni_uses_<rule>%` | Uses of a rule |
| `%omni_state_<rule>%` | `true` or `false`: is the player in that state rule |
| `%omni_enabled_<rule>%` | `true` or `false`: is the rule enabled |

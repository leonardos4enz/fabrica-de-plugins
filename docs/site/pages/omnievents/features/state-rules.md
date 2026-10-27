---
description: "Run actions once when something becomes true, and once when it stops being true."
---

# State rules

"When X **becomes** true" instead of "every X seconds, check". With `trigger: state`, `on_enter` runs once when `when`
becomes true for a player, and `on_exit` once when it stops being true. While nothing changes, nothing runs, and you
do not need an "already warned" variable.

```yaml
low_health:
  trigger: state
  when: "player.health <= 6"
  cooldown: 20s
  on_enter:
    - title: "<red>Danger!;<gray>Find shelter"
  on_exit:
    - actionbar: "<green>Safe after {var.state_seconds}s"
```

## How it knows when to check
When the rule loads, OmniEvents works out which events can change the expression and only checks then. Health is
checked on damage, healing and respawn. Location, block, biome, world and `in_area()` are checked when the player moves
to another block, teleports or changes world. Level, game mode, sneaking, flying, armor, the item in hand, `online`,
the weather, `pvar` and `gvar` work the same way. Several events of the same player in one tick are merged into **one**
check at the end of the tick. `/omni info low_health` shows what was detected.

## When it cannot know
Placeholders, `perm()`, the time of day and functions from other plugins send no event. A rule that uses them does not
load until you add `check_every: 5s`, so the cost of polling is always visible. `watch: [player_interact]` adds
triggers to the detected ones. Random functions (`chance`, `random`) are not allowed in `when`.

## Options
| Option | What it does |
|---|---|
| `when` | The expression, or a list of conditions that must all be true. |
| `on_enter`, `on_exit` | Actions when the state starts and ends. `{var.state_seconds}` is how long it lasted. |
| `check_every` | Fallback interval for what cannot be detected (`5s`, `1m`). |
| `watch` | Extra triggers that re-check the state. |
| `initial` | `enter` (default) runs `on_enter` when a player joins with the state already true; `silent` does not. |
| `per_player` | `false` keeps one server-wide state (for example `"gvar.event_on == true && online >= 2"`). |

`permission`, `cooldown` and `limit` hold back `on_enter` as in any rule, and `on_exit` only runs after its `on_enter`.

## From other rules
`in_state('low_health')` (and the placeholder `%omni_state_low_health%`) tells whether the player is in the state.
`/omni state low_health` lists who is inside and since when, and `/omni debug low_health` shows each enter and exit
with the values of `when`.

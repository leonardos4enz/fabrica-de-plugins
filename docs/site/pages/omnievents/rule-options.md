# Rule options

Every key directly under a rule name is an option. Only `trigger` is required; everything else is optional.

```yaml
vip_kit:
  trigger: command
  command: {name: vipkit, aliases: [vk], permission: rank.vip}
  description: "Free kit for VIPs, once a week"
  worlds: [world, world_nether]
  conditions:
    - "player.gamemode == 'survival'"
  limit: 1
  limit_reset: weekly
  actions:
    - give_item: "GOLDEN_APPLE;3"
    - message: "<gold>Enjoy your kit!"
  else:
    - message: "<red>Switch to survival first."
  on_limit:
    - message: "<red>You already claimed it this week."
```

## Basics
| Option | Value | What it does |
|---|---|---|
| `trigger` (or `type`) | trigger name | The event that runs the rule. See [triggers](triggers.md); `/omni triggers` lists them in game. |
| `description` | text | A free note for whoever reads the rule. The plugin ignores it. |
| `enabled` | `true` / `false` | `false` turns the rule off without deleting it. |
| `conditions` (or `if`) | expression or list | Everything that must be true. See [conditions](conditions.md). |
| `actions` (or `then`) | list of actions | What to do when the conditions pass. See [actions](actions.md). |
| `else` | list of actions | What to do when a condition fails (and it has no `deny` of its own). |

## Quick filters
| Option | Value | What it does |
|---|---|---|
| `worlds` | world or list | Only run in these worlds. |
| `permission` | permission | The player needs it for the rule to run. |
| `bypass_permission` | permission | Players with it are ignored by the rule. |
| `priority` | `lowest` … `highest`, `monitor` | The Bukkit listener priority (`normal` by default). |
| `ignore_cancelled` | `true` / `false` | `true`: do not run if another plugin already cancelled the event. |

## Cooldowns
| Option | Value | What it does |
|---|---|---|
| `cooldown` | duration | Minimum time between two runs: `30s`, `5m`, `1h30m`, `2d`, `20t` (ticks) or a number of seconds. |
| `cooldown_scope` | `player` / `global` | One cooldown per player (default) or one for the whole server. |
| `on_cooldown` | list of actions | Runs while the rule is cooling down. `{var.cooldown_left}` has the seconds left. |
| `cooldown_bypass` | permission | Players with this permission skip the cooldown. Default: `omnievents.bypasscooldown.<rule>`. It only counts if it is **given** to the player; operators do not skip cooldowns unless you set `cooldown-bypass-op: true` in `config.yml`. |

## Limits
| Option | Value | What it does |
|---|---|---|
| `limit` | number | Maximum uses (0 = no limit). |
| `one_time` | `true` / `false` | Same as `limit: 1`. |
| `limit_scope` | `player` / `global` | Count uses per player (default) or for the whole server. |
| `limit_reset` | `never`, `daily`, `weekly`, `monthly` or a duration | When the uses go back to zero (`12h` works too). |
| `on_limit` | list of actions | Runs when no uses are left. |

Cooldowns and uses are saved to disk. Reset them with `/omni cooldown reset` and `/omni limit reset`
([commands](commands.md)).

## Costs
`cost` charges the player before the actions run, **all or nothing**: if anything is missing, nothing is taken, the
use does not count and `on_cost_fail` runs instead.

```yaml
buy_bow:
  trigger: command
  command: buybow
  cost:
    items: "DIAMOND;3"
    levels: 5
  actions:
    - give_item: "BOW;1;<aqua>Shop bow"
  on_cost_fail:
    - message: "<red>You are missing: {var.cost_missing}"
```

| `cost` key | Charges |
|---|---|
| `money` | Money from the Vault economy (needs Vault and an economy plugin). |
| `levels`, `exp` | Experience levels or experience points. |
| `items` | `"MATERIAL;amount"` or a list of them, by material (armor slots are not counted). |
| `pvar.<name>`, `gvar.<name>` | A numeric [variable](variables.md) (an empty variable counts as 0). |

Each amount can be an expression (`money: "var.price * 2"`). If a charge fails halfway, what was already taken is
given back.

## Options for specific triggers
| Option | Trigger | What it does |
|---|---|---|
| `every` | `timer` | How often it runs: `10s`, `5m` (minimum one tick). |
| `per_player` | `timer`, `state` | `true` (default): once for each online player. `false`: once, with no player. |
| `at`, `timezone` | `clock` | Time of day, `"08:00"` or `["08:00", "20:30"]`, and an optional time zone such as `Europe/London`. |
| `command` | `command` | A name, or a map with `name`, `aliases`, `permission`, `description`, `usage` and `tab`. |
| `args` | `call` | Parameter names; they arrive as `var.<name>`. |
| `move_mode` | `player_move` | `block` (default: only when the player moves to another block), `chunk` or `any`. |
| `event`, `player_from` | `custom` | The full class name of any Bukkit event, and where to find the player in it. |
| `variables` | `var_expire` | Only these variable names run the rule. |
| `when`, `on_enter`, `on_exit`, `check_every`, `watch`, `initial` | `state` | See [state rules](more-features.md#state-rules). |

## Advanced
| Option | What it does |
|---|---|
| `tests` | Test cases for the rule (`given` and `expect`). See [rule tests](more-features.md#rule-tests). |
| `template`, `extends`, `with`, `params` | Reuse a rule as a template with `${parameters}`. See [packs and templates](more-features.md#packs-and-templates). |
| `menu` | This entry is an inventory menu instead of a rule. See [menus](more-features.md#menus). |
| `budget` | Time budget per run (`0.5ms`, `200us`). The console warns you if the rule gets slower than that. |

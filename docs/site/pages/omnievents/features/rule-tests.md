---
description: "Test cases inside your YAML that run on every reload, without touching the server."
---

# Rule tests

Each rule can carry its own test cases: the context that goes in (`given`) and what you expect (`expect`). They run
with no server, no players and no real effects, so you find out when a change, a plugin update or a Paper update
breaks a rule that used to work.

```yaml
diamond_reward:
  trigger: block_break
  conditions: ["block.type == 'DIAMOND_ORE'", "player.gamemode == 'survival'"]
  actions:
    - set: "pvar.diamonds = default(pvar.diamonds, 0) + 1"
    - actionbar: "<aqua>Diamonds: {pvar.diamonds}"
    - console: "give {player.name} emerald 1"
  tests:
    - name: counts one
      given:
        player.name: Alex
        player.gamemode: SURVIVAL
        block.type: DIAMOND_ORE
        pvar.diamonds: 9
      expect:
        fired: true
        pvar.diamonds: 10
        messages: ["Diamonds: 10"]
        commands: ["give Alex emerald"]
    - name: stone does not count
      given: {block.type: STONE}
      expect: {fired: false, branch: none}
```

## `given`
Each key is a path with dots (`block.type`, `player.level`, `event.damage`, `player.location.y`) and its value. You
can also set variables (`pvar.x`, `gvar.x`, `var.x`, `block.data.x`), placeholders (`"%vault_eco_balance%": 100`),
`money`, `inventory: {DIAMOND: 5}`, `regions` and `bedrock`. Whatever you do not give is null.

## `expect`
| Key | Checks |
|---|---|
| `fired` | `true` if the main actions ran. |
| `branch` | Which branch ran: `actions`, `else`, `deny`, `deny conditions[1]`, `on_cost_fail`, `none`, or `on_enter` in a state rule. |
| `pvar.x`, `gvar.x`, `var.x`… | The final value of a variable (`null` means it must not exist). |
| `messages` | Texts that must appear, in order, without formatting, in what would be sent. |
| `commands` | Texts that must appear, in order, in the commands that would run. |
| `actions` | Action types that would run (`give_item`, or `"sound: LEVELUP"`). |
| `cancelled` | `true` if the event would be cancelled. |
| `assert` | Expressions that must be true at the end. |

## What runs and what does not
Conditions, `deny`, `else` and flow actions (`if`, loops, `random` with the case's `seed`, `stop`, `call`) really run;
`set` and `unset` only change the test case. Messages, commands, items and world changes are **recorded**, never
applied. `worlds`, `permission`, `cooldown` and `limit` are not tested.

## Running them
- On every start and `/omni reload`: the console says `Pruebas de reglas: 12/12 pasan` ("rule tests: 12/12 pass") or
  shows each failure. Turn this off with `tests.on-load: false` in `config.yml`.
- `/omni test [rule]` runs them on demand and shows, for each failing case, what was expected, what came out and the
  trace.

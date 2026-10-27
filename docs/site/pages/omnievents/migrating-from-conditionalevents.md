---
description: "Import your ConditionalEvents configuration and learn the equivalents."
---

# Migrating from ConditionalEvents

OmniEvents can do everything ConditionalEvents (CE) 4.65 does, and it ships an importer that converts your
configuration automatically.

## Import your events
1. Keep the `plugins/ConditionalEvents/` folder on the server (you can remove the CE jar).
2. Install OmniEvents and start the server.
3. Run `/omni import conditionalevents`.
4. Your events are written to `plugins/OmniEvents/rules/importado/`, one file for each CE file, and the command lists
   what it could not translate.
5. Search the new files for `REVISAR` ("review"): each one is a `log` action with the original line and a note.
6. Run `/omni reload` and `/omni validate`.

The importer reads `config.yml` (including `to_condition_groups`) and every file in `events/`. It translates the
events, conditions, actions, `to_*` targets, cooldowns and `one_time`, action groups, and the variables of each event.
What has no equivalent stays as `REVISAR`: `player_command_as_op` (it gives a player operator rights for a moment), the
DiscordSRV actions, `datavalue:` for 1.12 items, `custom_model_component_data_*` and `vector`.

## The same ideas, side by side
| In ConditionalEvents | In OmniEvents |
|---|---|
| An event under `Events:` with `type:` | A rule (top-level key) with `trigger:` (`type:` also works) |
| `conditions:` with one comparison per line (`%player_level% >= 10`) | `conditions:` with full expressions (`player.level >= 10 && !player.flying`) |
| `or` / `and` inside one line, no parentheses | `&&`, `\|\|`, `!` and parentheses, mixed freely |
| `execute <group>` in a condition, and action groups | `deny:` per condition, `else:`, and `if`/`then`/`else` inside actions |
| `cooldown:` and `one_time: true` | `cooldown:`, `limit:` with `limit_reset:` (`one_time: true` still works) |
| `conditionalevents.bypasscooldown.<event>` | `omnievents.bypasscooldown.<rule>` or `cooldown_bypass:` (the CE permission still works for imported rules) |
| `prevent_cooldown_activation` | `no_cooldown` |
| `to_all:`, `to_target:`, `to_world:`, `to_player:`, `to_range:`, `to_condition:` | `to:` and `where:` on any action |
| Random action groups | `random:` with a `weight:` for each branch |
| `wait: 5` | `delay: 5s` |
| `repetitive` with `repetitive_time:` (ticks) | `timer` with `every: 5m` |
| `player_leave`, `player_kill_entity` | `player_quit`, `player_kill` (the old names also work) |
| `%player%`, `%block%`, `%main_command%` | `{player.name}`, `{block.type}`, `{cmd.name}` (without the slash) |
| `%random_1_10%` | `{random(1, 10)}` |
| `%args_length%`, `%arg_1%` | `{args.size}`, `{args[0]}` (lists start at 0) |
| `%random_player%` | `{pick(players()).name}` |
| Variables from other plugins | Your own `var`, `pvar` and `gvar` (see [variables](variables.md)) |
| `/ce reload`, `/ce debug <event>` | `/omni reload`, `/omni debug <rule>` |
| `/ce reset <player\|*> <event\|all>` | `/omni cooldown reset <rule\|*> [player\|*\|global]` and `/omni limit reset …` |
| `/ce interrupt` | `/omni interrupt <rule\|*> [player]` |
| `ConditionalEventsEvent` (Java API) | `OmniRuleFireEvent` |

## An example
A typical CE event (a welcome title on join):

```text
Events:
  welcome:
    type: player_join
    actions:
      default:
        - "title: 20;40;20;&6Welcome;&7%player%"
        - "playsound: ENTITY_PLAYER_LEVELUP;1;1"
```

The same rule written for OmniEvents:

```yaml
welcome:
  trigger: player_join
  actions:
    - title: "<gold>Welcome;<gray>{player.name};20;40;20"
    - sound: "ENTITY_PLAYER_LEVELUP;1;1"
```

Note the differences: the rule is not inside `Events:` and has no `default:` group, the title puts its times at the
end, and the player's name is the expression `{player.name}`.

## After importing
- `/omni validate` lists anything the importer left in a shape the plugin does not accept.
- `/omni debug <rule>` shows why an imported rule does or does not run.
- Write a few [tests](features/rule-tests.md) for your most important rules, so a future update cannot break them
  without you knowing.

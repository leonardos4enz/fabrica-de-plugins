---
description: "Write, reload, test and debug your first rule."
---

# Your first rule

A rule has three parts: a **trigger** (when), **conditions** (if) and **actions** (do). Create
`plugins/OmniEvents/rules/welcome.yml` with this content:

```yaml
welcome:
  trigger: player_join
  actions:
    - if: "first_join"
      then:
        - broadcast: "<gold>Please welcome <yellow>{player.name}</yellow>, it's their first time here!"
        - give_item: "BREAD;8;<yellow>Welcome bread"
      else:
        - message: "<green>Welcome back, {player.name}!"
    - delay: 2s
    - title: "<gold>Welcome;<gray>{online} players online"
    - sound: "ENTITY_PLAYER_LEVELUP;1;1.2"
```

Run `/omni reload` and join the server. Here is what each line does:

| Line | Meaning |
|---|---|
| `welcome:` | The rule name. It must be unique; commands like `/omni debug welcome` use it. |
| `trigger: player_join` | Run when a player joins. See all [triggers](triggers.md). |
| `if: "first_join"` | `first_join` is a value the `player_join` trigger gives you: `true` the first time. |
| `broadcast`, `message` | Send a text to everyone, or to the player. |
| `{player.name}` | Anything between `{ }` is an [expression](conditions.md#expressions), replaced by its value. |
| `give_item: "BREAD;8;<name>"` | Give 8 bread with a custom name. |
| `delay: 2s` | Wait two seconds before the next actions. |
| `title: "title;subtitle"` | Show a title on screen. |

Texts accept [MiniMessage](https://docs.advntr.dev/minimessage/format.html) tags (`<gold>`, `<b>`, `<gradient:…>`),
legacy codes (`&a`) and hex colors (`&#FFAA00`).

## Add a condition
Conditions are expressions that must all be true. If one is false, the `else` actions run instead.

```yaml
night_bonus:
  trigger: block_break
  conditions:
    - "block.type == 'COAL_ORE'"
    - "player.world.time > 13000"
  actions:
    - give_exp: 5
    - actionbar: "<gray>Night bonus: +5 XP"
```

## Add a cooldown and a limit
```yaml
daily_reward:
  trigger: player_join
  limit: 1
  limit_reset: daily
  actions:
    - delay: 5s
    - set: "pvar.coins = default(pvar.coins, 0) + 25"
    - message: "<green>Daily reward: <yellow>+25 coins</yellow> (you have {pvar.coins})."
```

`limit: 1` with `limit_reset: daily` means once per player per day. `cooldown: 10m` would mean "at most every ten
minutes". Both are saved, so they survive restarts. See [rule options](rule-options.md).

## Test it without playing
A rule can carry test cases. They run on every `/omni reload` (and with `/omni test`) with a fake context, and they
never touch the real server:

```yaml
greet_command:
  trigger: command
  command: greet
  actions:
    - message: "<green>Hello, {player.name}!"
  tests:
    - name: says hello
      given:
        player.name: Alex
      expect:
        fired: true
        messages: ["Hello, Alex!"]
```

## When something does not work
1. `/omni validate` lists every error with its file and key, for example a misspelled trigger with a
   "did you mean player_join?" hint.
2. `/omni debug welcome` shows, on every run, each condition with its values and the actions that ran.
   `/omni debug off` stops it.
3. `/omni check welcome <player>` evaluates the rule right now for that player without running any action.

Next: learn the [rule options](rule-options.md).

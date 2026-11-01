---
description: "Ready-to-copy rules for common server needs. Every example here is loaded by an automated test."
---

# Examples

Copy any of these into a file in `plugins/OmniEvents/rules/` and run `/omni reload`. Every example on this page is
loaded by the plugin's automated tests, so they are valid for the current version.

## The English examples pack
Since 1.12.0 the plugin ships a whole pack of beginner examples in English, ready to switch on. It is created in
`plugins/OmniEvents/rules/examples-en/` the first time the plugin starts, and it comes **turned off**, so it changes
nothing on your server until you enable it:

- set `enabled: true` in `rules/examples-en/pack.yml` and run `/omni reload`, or
- run `/omni pack enable examples` to try it until the next reload.

| File | What it shows |
|---|---|
| `welcome.yml` | Join and quit messages; on the first join, an announcement, a starter kit and 100 coins; `/coins [player]` |
| `daily.yml` | A daily login bonus (once a day), `/daily` with a 24-hour cooldown and a streak that resets after 48 hours, a reward per hour played |
| `zones.yml` | A safe spawn area by coordinates (enter and leave messages, no fall damage, no PvP) and launch pads, without any other plugin |
| `common.yml` | Announcements, a chat word filter, hiding `/plugins` from non-staff, death coordinates, a low health warning, the first diamond announced |
| `regions.yml` | WorldGuard: enter and leave messages, a VIP-only region, Haste inside a mine region, a reward for standing in an AFK region, `/where` |
| `integrations.yml` | The other optional plugins: Vault (`/sellwheat` and a `/shop` menu that charges money), Citizens (a guide NPC and a small quest NPC), PlaceholderAPI (`/stats` with `%player_ping%`), Floodgate (a hint for Bedrock players, who get `/shop` as a form) and ProtocolLib (hide messages from other plugins) |

The values you would want to change (coins per day, where your spawn is and how big, region ids, permissions) are
parameters at the top of `pack.yml`. Its rules are called `examples:<name>` (`/omni test examples:daily` runs the tests
of one of them) and every rule has `tests:`, so you can change a rule and check it still does what it should.
**Without WorldGuard** (or any of the other optional plugins) the examples still load: the ones that need it just never
run.

Already had OmniEvents before 1.12.0? The plugin only copies its examples when `rules/` does not exist yet, so the pack
is not added to an existing server: download the
[`examples-en` folder](https://github.com/leonardos4enz/fabrica-de-plugins/tree/main/plugins/omnievents/src/main/resources/rules/examples-en)
into your `rules/` folder and run `/omni reload`.

## Welcome and rewards

### Welcome message with a first-join kit
```yaml
welcome:
  trigger: player_join
  actions:
    - set_join_message: "<gray>[<green>+</green>] {player.name}"
    - if: "first_join"
      then:
        - broadcast: "<gold>Please welcome <yellow>{player.name}</yellow>!"
        - give_item: "BREAD;8;<yellow>Welcome bread"
      else:
        - message: "<green>Welcome back, {player.name}!"
```

### Daily login reward
```yaml
daily_login:
  trigger: player_join
  limit: 1
  limit_reset: daily
  actions:
    - delay: 3s
    - set: "pvar.coins = default(pvar.coins, 0) + 25"
    - message: "<green>Daily reward: <yellow>+25 coins</yellow> (you have {pvar.coins})."
    - sound: "ENTITY_PLAYER_LEVELUP;1;1.5"
```

### Playtime milestones
```yaml
playtime_tick:
  trigger: timer
  every: 1m
  actions:
    - set: "pvar.minutes = default(pvar.minutes, 0) + 1"
    - if: "pvar.minutes % 60 == 0"
      then:
        - broadcast: "<aqua>{player.name}</aqua> has played for {pvar.minutes / 60} hours!"
        - give_item: "DIAMOND"
```

## Custom commands

### A /spawn command with a warm-up
```yaml
spawn_command:
  trigger: command
  command: {name: spawn, aliases: [s]}
  cooldown: 30s
  actions:
    - message: "<gray>Teleporting in 3 seconds..."
    - delay: 3s
    - teleport: "world;0;80;0"
    - sound: "ENTITY_ENDERMAN_TELEPORT;1;1"
  on_cooldown:
    - message: "<red>Wait {var.cooldown_left}s before using /spawn again."
```

### A /coins command that reads arguments
```yaml
coins_command:
  trigger: command
  command: {name: coins, tab: [["{players}"]]}
  actions:
    - if: "len(args) == 0"
      then:
        - message: "<gold>You have {default(pvar.coins, 0)} coins."
      else:
        - message: "<gold>{args[0]} has {default(pvar_of(args[0], 'coins'), 0)} coins."
  tests:
    - name: own balance
      given: {pvar.coins: 40}
      expect: {messages: ["You have 40 coins"]}
```

### A shop with an all-or-nothing cost
```yaml
buy_golden_apple:
  trigger: command
  command: buyapple
  cost:
    pvar.coins: 50
  actions:
    - give_item: "GOLDEN_APPLE"
    - message: "<green>Bought a golden apple. Coins left: {pvar.coins}"
  on_cost_fail:
    - message: "<red>You need 50 coins."
  tests:
    - name: enough coins
      given: {pvar.coins: 60}
      expect: {branch: actions, pvar.coins: 10}
    - name: not enough
      given: {pvar.coins: 20}
      expect: {branch: on_cost_fail, pvar.coins: 20}
```

## Chat and moderation

### Block a word in chat
```yaml
chat_filter:
  trigger: player_chat
  conditions: "lower(message) contains 'badword'"
  actions:
    - cancel
    - message: "<red>Please keep the chat friendly."
  tests:
    - given: {message: "this has a BadWord in it"}
      expect: {cancelled: true}
    - given: {message: "hello everyone"}
      expect: {fired: false}
```

### Block a command for non-staff
```yaml
no_plugins_command:
  trigger: player_command
  conditions:
    - "cmd.name in ['pl', 'plugins', 'bukkit:plugins']"
    - "!perm('staff')"
  actions:
    - cancel
    - message: "<red>Unknown command."
```

## World and combat

### No fall damage in the lobby
```yaml
lobby_no_fall:
  trigger: player_damage
  worlds: [lobby]
  conditions: "cause == 'FALL'"
  actions:
    - cancel
```

### Double damage with a golden sword at night
```yaml
night_blade:
  trigger: player_attack
  conditions:
    - "player.item.type == 'GOLDEN_SWORD'"
    - "player.world.time > 13000"
  actions:
    - set_damage: "200%"
    - particle: "CRIT;10"
```

### A lucky block
```yaml
lucky_block:
  trigger: block_break
  conditions: "block.type == 'SPONGE'"
  actions:
    - cancel_drops
    - random:
        - weight: 5
          do:
            - give_item: "DIAMOND;2"
            - message: "<aqua>Lucky! Two diamonds."
        - weight: 3
          do:
            - give_exp: 100
            - message: "<green>Some experience for you."
        - weight: 2
          do:
            - summon: "ZOMBIE"
            - message: "<red>Unlucky... a zombie!"
```

### Launch pads
```yaml
launch_pad:
  trigger: player_move
  conditions: "to.block.type == 'LIGHT_WEIGHTED_PRESSURE_PLATE'"   # to: the block the player steps into
  cooldown: 2s
  actions:
    - launch: 2.5
    - sound: "ENTITY_BAT_TAKEOFF;1;1"
```

## WorldGuard regions
These need WorldGuard (create the regions as usual with `/rg define <id>`). `region_enter` and `region_leave` run for
each region the player enters or leaves; `cancel` keeps them where they were. `in_region(id)` and `regions()` work in
any rule. More in the pack's `regions.yml` and in [integrations](features/integrations.md#worldguard-regions).

### Enter and leave messages, and a VIP-only region
```yaml
spawn_welcome:
  trigger: region_enter
  conditions: "region.id == 'spawn'"
  actions:
    - actionbar: "<gold>Welcome to the spawn, {player.name}"
  tests:
    - given: {player.name: Ana, region.id: spawn}
      expect: {messages: ["Welcome to the spawn, Ana"]}

spawn_goodbye:
  trigger: region_leave
  conditions: "region.id == 'spawn'"
  actions:
    - actionbar: "<gray>You left the spawn"

vip_area:
  trigger: region_enter
  conditions:
    - "region.id == 'vip'"
    - "!perm('rank.vip')"
  actions:
    - cancel
    - message: "<red>Only VIP players can enter this area."
  tests:
    - given: {region.id: vip}
      expect: {cancelled: true}
```

### An effect while inside a region
```yaml
mine_boost_on:
  trigger: region_enter
  conditions: "region.id == 'mine'"
  actions:
    - effect: "HASTE;-1;2;false"     # -1 seconds: until it is removed
    - actionbar: "<aqua>Mining boost on"

mine_boost_off:
  trigger: region_leave
  conditions: "region.id == 'mine'"
  actions:
    - remove_effect: HASTE
```

### A reward for standing in a region, and /where
```yaml
afk_coins:
  trigger: timer
  every: 5m
  conditions: "in_region('afk')"
  actions:
    - set: "pvar.coins = default(pvar.coins, 0) + 1"
  tests:
    - given: {regions: [afk], pvar.coins: 4}
      expect: {pvar.coins: 5}

where_command:
  trigger: command
  command: where
  actions:
    - if: "len(regions()) == 0"
      then:
        - message: "<gray>You are not in any region."
      else:
        - message: "<gray>You are in: <yellow>{join(regions(), ', ')}"
  tests:
    - given: {regions: [mine, spawn]}
      expect: {messages: ["You are in: mine, spawn"]}
```

## Server events

### Scheduled announcements
```yaml
announcer:
  trigger: timer
  every: 10m
  per_player: false
  actions:
    - random:
        - do:
            - broadcast: "<gold>[Tip]</gold> Vote every day with /vote!"
        - do:
            - broadcast: "<gold>[Tip]</gold> Join our Discord for events."
        - do:
            - broadcast: "<gold>[Tip]</gold> Type /spawn to go back to spawn."
```

### A nightly event at 20:00
```yaml
nightly_event:
  trigger: clock
  at: "20:00"
  per_player: false
  actions:
    - set: "gvar.event_on = true"
    - broadcast: "<gold><b>EVENT</b></gold> Double rewards for one hour!"
    - delay: 1h
    - set: "gvar.event_on = false"
    - broadcast: "<gray>The event is over. Thanks for playing!"
```

### Warn a player once when their health is low
```yaml
low_health_warning:
  trigger: state
  when: "player.health <= 6"
  on_enter:
    - title: "<red>Low health!;<gray>Eat something"
  on_exit:
    - actionbar: "<green>You recovered"
```

Want more? Turn on [the English examples pack](#the-english-examples-pack) (`rules/examples-en/`); the plugin also
ships Spanish-language examples in `plugins/OmniEvents/rules/ejemplos/`. Every building block is explained in [triggers](triggers.md), [conditions](conditions.md) and [actions](actions.md).
For a whole game (start, win, lose, records), see [Build a minigame](minigames.md).

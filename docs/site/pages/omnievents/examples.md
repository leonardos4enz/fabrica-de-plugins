---
description: "Ready-to-copy rules for common server needs. Every example here is loaded by an automated test."
---

# Examples

Copy any of these into a file in `plugins/OmniEvents/rules/` and run `/omni reload`. Every example on this page is
loaded by the plugin's automated tests, so they are valid for the current version.

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
  conditions: "player.location.block.type == 'LIGHT_WEIGHTED_PRESSURE_PLATE'"
  cooldown: 2s
  actions:
    - launch: 2.5
    - sound: "ENTITY_BAT_TAKEOFF;1;1"
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

Want more? The plugin ships with Spanish-language examples in `plugins/OmniEvents/rules/ejemplos/`, and every
building block is explained in [triggers](triggers.md), [conditions](conditions.md) and [actions](actions.md).

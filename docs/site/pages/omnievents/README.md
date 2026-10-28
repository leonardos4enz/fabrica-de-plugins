---
description: "What OmniEvents does better, and the Paper versions it runs on."
---

# Why OmniEvents

OmniEvents lets you script your Paper server in YAML: **when** something happens (a player joins, breaks a block,
types a command…), **if** some conditions are true, **do** a list of actions. No Java, no restarts: edit a file and run
`/omni reload`.

```yaml
diamond_counter:
  trigger: block_break
  conditions:
    - "block.type in ['DIAMOND_ORE', 'DEEPSLATE_DIAMOND_ORE']"
    - "player.gamemode == 'survival'"
  actions:
    - set: "pvar.diamonds = default(pvar.diamonds, 0) + 1"
    - actionbar: "<aqua>Diamonds mined: {pvar.diamonds}"
    - if: "pvar.diamonds % 10 == 0"
      then:
        - broadcast: "<aqua>{player.name}</aqua> has mined {pvar.diamonds} diamonds!"
```

## Why OmniEvents
- **A real expression language.** Mix `&&`, `||`, `!` and parentheses in one condition, do math, compare text and
  read any value of the game: `player.location.block.type`, `event.damage`, `item.name`.
- **Control flow inside actions.** `if`/`else`, `repeat`, `for_each`, weighted `random`, `delay`, `stop` and `call`.
- **Your own variables.** Per run (`var`), per player (`pvar`) and server-wide (`gvar`), saved to disk, with optional
  expiry. You can also store data on a block, an entity or an item.
- **Cooldowns and limits that make sense.** Per player or global, with daily, weekly or custom resets.
- **Real commands.** A `command` rule registers a real command with aliases, permission and tab completion.
- **See why a rule did or did not run.** `/omni debug` shows every condition with its values, `/omni check` tests a
  rule without running it, and errors name the file and the exact key, with a "did you mean…?".
- **Tests in your YAML.** A rule can carry its own test cases; they run on every reload without touching the world.
- **Menus**, also as native forms for Bedrock players (with Floodgate), **costs** paid all at once or not at all, and
  optional integrations with PlaceholderAPI, Vault, WorldGuard, Citizens and ProtocolLib.
- **Light on the server.** A Bukkit event is only listened to if some rule uses it.
- **An importer for ConditionalEvents** configurations: `/omni import conditionalevents`.

## Compatibility
| Server | Status |
|---|---|
| **Paper 1.21.4** (Java 21+) | Verified with OmniEvents 1.11.1 (unit tests, a real server and 58 in-game scenarios run by bots) |
| **Paper 26.1.2** (Java 25) | Verified with OmniEvents 1.11.1 (58 in-game scenarios) |
| Paper 1.21.5 – 1.21.11, 26.2, 26.3 | Not verified (same API, should work) |
| Paper 1.21.3 or older | Not supported |
| Spigot, CraftBukkit, Folia | Not supported |

The full and always up-to-date compatibility table, with the tested versions of each optional plugin, is in the
[plugin README](https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/plugins/omnievents/README.md#compatibilidad)
(in Spanish).

## Where to go next
1. [Installation](installation.md)
2. [Your first rule](first-rule.md)
3. Learn the building blocks: [triggers](triggers.md), [conditions](conditions.md), [actions](actions.md) and
   [variables](variables.md).
4. Check the [commands](commands.md) you will use every day: `reload`, `validate`, `debug`, `check`.

> **Language note.** OmniEvents is developed in Spanish. Its in-game messages (`messages.yml`), the bundled example
> rules and the [generated reference](https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/docs/omnievents/referencia.md)
> are in Spanish. Option, trigger, action and function names are English words, and you can translate every message
> in `messages.yml`.

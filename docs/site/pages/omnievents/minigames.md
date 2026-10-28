---
description: "Build a complete single-player minigame with plain OmniEvents rules: start, play, win, lose, leave and records."
---

# Build a minigame

You don't need a minigame plugin for a single-player game: the triggers, variables and actions you already have are
enough. OmniEvents ships a complete **parkour** as an example pack, and this page explains the pattern behind it so
you can build your own (a race, a maze, a target range, a treasure hunt…).

## Try the parkour that ships with the plugin

The pack lives in `plugins/OmniEvents/rules/ejemplos/parkour/` (its messages are in Spanish; translate them freely).
It comes **disabled**, so it changes nothing until you turn it on:

1. Set `enabled: true` in `parkour/pack.yml` and run `/omni reload` (or `/omni pack enable parkour` until the next
   reload).
2. Build your course anywhere and mark it with pressure plates. There are no coordinates to configure:
   - a **light weighted (gold) plate** starts or restarts the run;
   - **heavy weighted (iron) plates** are checkpoints;
   - a **polished blackstone plate** is the finish line.
3. Or stand somewhere flat and run `/parkour construir` (OP) to get a small test course built to the east.

What the player gets: a title and the rules when the run starts, a timer in the action bar, a return to the last
checkpoint after falling, a time limit, `/parkour salir` (leave), `/parkour reiniciar` (restart), `/parkour checkpoint`,
`/parkour record` (personal best and server record, saved across restarts), and a firework and an announcement on a new
server record. Flying, changing game mode, dying or teleporting away (a command, an ender pearl) ends the run. If the
player disconnects, the run is cancelled, and a run left half-way by a crash is cleaned up when the player joins again.

Everything is a parameter in `pack.yml`:

```yaml
params:
  inicio: LIGHT_WEIGHTED_PRESSURE_PLATE        # start plate
  checkpoint: HEAVY_WEIGHTED_PRESSURE_PLATE    # checkpoint plates
  meta: POLISHED_BLACKSTONE_PRESSURE_PLATE     # finish plate
  limite: 300                                  # seconds to reach the finish
  caida: 3                                     # blocks below the last checkpoint that count as a fall
  admin: omnievents.parkour.admin              # permission for /parkour construir
```

Every rule of the pack has `tests:`, and the plugin's test suite plays the whole game with a bot on a real server.

## The pattern

1. **Keep the game state in player variables.** One variable says whether the player is playing (`pvar.pk_activo`),
   others hold the start time, the checkpoint and the counters. Records are player and global variables that are never
   cleared.
2. **One rule per thing that can happen** (stepping on a plate, falling, dying, quitting), and each one first checks
   that the player is playing. That check comes first, so the rules cost almost nothing for everyone else.
3. **Put what is shared in `trigger: call` rules**: start, lose (with a reason), finish. Every way out of the game ends
   in the same cleanup, so no variable is left behind.
4. **A per-player `timer`** shows the clock and enforces the time limit.
5. **Clean up on `player_quit`, and again on `player_join`** in case the server stopped mid-game.
6. **Ship it as a pack** with `params`, so another server changes the materials or the limits without touching the rules.

A minimal version of the same game, in English, that you can paste into `rules/` and grow:

```yaml
race_start:
  trigger: player_move
  conditions: "to.block.type == 'LIGHT_WEIGHTED_PRESSURE_PLATE'"
  actions:
    - set: "pvar.race_on = true"
    - set: "pvar.race_start = now()"
    - title: "<gold>Go!;<gray>Reach the finish;5;30;10"
  tests:
    - given: {to.block.type: LIGHT_WEIGHTED_PRESSURE_PLATE}
      expect: {pvar.race_on: true, messages: ["Go!"]}

race_finish:
  trigger: player_move
  conditions:
    - "to.block.type == 'POLISHED_BLACKSTONE_PRESSURE_PLATE'"
    - expr: "pvar.race_on == true"
      deny:
        - actionbar: "<gray>Step on the start plate first."
  actions:
    - set: "var.t = default(var.now, now()) - pvar.race_start"   # var.now lets tests fix the clock
    - message: "<green>Finished in {round(var.t / 1000, 2)} s"
    - if: "pvar.race_best == null || var.t < pvar.race_best"
      then:
        - set: "pvar.race_best = var.t"
        - message: "<gold>New personal best!"
    - call: race_end
  tests:
    - given: {to.block.type: POLISHED_BLACKSTONE_PRESSURE_PLATE, pvar.race_on: true,
              pvar.race_start: 1000, var.now: 43500}
      expect: {messages: ["Finished in 42.5 s", "New personal best!"], pvar.race_best: 42500, pvar.race_on: null}
    - given: {to.block.type: POLISHED_BLACKSTONE_PRESSURE_PLATE}
      expect: {branch: deny, messages: ["Step on the start plate first."]}

race_clock:
  trigger: timer
  every: 1s
  conditions: "pvar.race_on == true"
  actions:
    - set: "var.t = default(var.now, now()) - pvar.race_start"
    - if: "var.t > 120000"
      then:
        - message: "<red>Time is up."
        - call: race_end
        - stop
    - actionbar: "<gold>{floor(var.t / 1000)} s"
  tests:
    - given: {pvar.race_on: true, pvar.race_start: 0, var.now: 130000}
      expect: {messages: ["Time is up."], pvar.race_on: null}

race_quit:
  trigger: player_quit
  conditions: "pvar.race_on == true"
  actions:
    - call: race_end

race_end:
  trigger: call
  actions:
    - unset: pvar.race_on
    - unset: pvar.race_start
```

## Tips

- **Where the player arrives.** In `player_move`, `player.location` is still the block the player comes from (the
  event fires before the move). Check `to.block.type` for the plate they step on and `to.y` for a fall.
- **Testing with the clock.** `given` values are plain values, not expressions, so a rule that uses `now()` can't be
  tested directly. Write `default(var.now, now())` and set `var.now` in the test.
- **Saving a location.** `teleport` splits its value on `;` when the rule loads, so a whole location saved in one text
  variable can't be passed to it. Save the world, x, y, z and yaw in separate variables and write
  `teleport: "{pvar.w};{pvar.x};{pvar.y};{pvar.z};{pvar.yaw};0"`.
- **Blocking shortcuts.** `player_teleport` has a `cause`: `COMMAND`, `ENDER_PEARL` and the portals are the player's
  doing, and OmniEvents' own `teleport` is `PLUGIN`. `player_toggle_flight` and `player_gamemode_change` catch flying
  and creative mode.
- **Scoreboards.** Records are ordinary variables, so a scoreboard or hologram plugin can show them with PlaceholderAPI
  (`%omni_var_pk_record%`, `%omni_gvar_pk_record_nombre%`; see [Variables](variables.md)).

## What needs another plugin today

These are not possible with rules alone. Here is what to use instead:

| You want | Today |
|---|---|
| Save the inventory when the game starts and give it back at the end | Clear and give a kit with `clear_inventory` and `give_item`, or run the game in its own world with a per-world inventory plugin |
| A sidebar scoreboard | A scoreboard plugin reading the variables through PlaceholderAPI |
| A top 10 of all players | Only the single best (a global variable updated on each new record) is possible; a ranked list needs a leaderboard plugin |
| Reset the map after a game | `set_block` for a few blocks, or `console` with `fill`, or a WorldEdit schematic pasted with `console` |
| Teams, lobbies and arenas for many players | Possible with global variables, but hard to keep correct; a dedicated minigame plugin is the better tool |

# Blockwright

World editing for Paper that never lags the server: select with a wand and see the box, preview an edit before it
happens, undo even after a restart (without erasing what others built since), and write patterns and masks the way
you would say them. Spec: [025](../../docs/sdd/specs/025-blockwright-nucleo/spec.md) (core).
*Blockwright is a working name: the owner has not chosen the final one yet.*

```
/bwr wand                                        left click = corner 1, right click = corner 2
/bwr set 70% stone, 30% andesite --preview      see it first (only you), then /bwr confirm
/bwr replace dirt, grass_block with $castle_wall
/bwr undo                                        works after a restart; leaves alone what others changed since
```

## What it does (and what it does better)
- **No lag, ever.** Every edit runs in small batches inside a time budget shared by the whole plugin
  (`performance.max_ms_per_tick`, 8 ms of the 50 ms of a tick). The clock is checked on every block, so a tick never
  goes over by more than one block. Unloaded chunks load in the background. A huge edit and a small one from two
  players share the time in turns, so the small one is not stuck waiting. Progress shows in your action bar.
- **See before you change.** `--preview` shows the result with fake blocks that **only you see**; nothing changes
  until `/bwr confirm` (and the edit then does exactly what you saw). Edits bigger than `confirm.above_blocks` (20 000)
  always ask first, saying how many blocks and how long.
- **See your selection without a mod.** While you hold the wand, the selection box is drawn with particles only you
  see. Only the wand that `/bwr wand` gives selects: an ordinary golden axe stays an axe.
- **Undo that is not lost.** The history of every player is saved to disk: `undo` and `redo` work after a restart or a
  crash (a half-finished edit can be undone too). **Safe undo:** a block somebody changed after your edit is left
  alone, and you are told how many. Chest contents and sign text come back too. Moderators can see and undo other
  players' edits (`/bwr history <player>`, `/bwr undo --player <name>`).
- **Patterns and masks in plain words**, with errors that say what to fix (`Unknown block 'stnoe' (did you mean
  'stone'?)`), checked before anything changes. Tab completes blocks, tags and presets, even in the middle of a list.
- **Safe limits out of the box.** Per permission group: biggest edit, edits at once, worlds and blocked blocks
  (nobody places bedrock, barriers, command blocks or portals by default except `admin`). Over the limit, the edit is
  refused before it starts.
- **Copy, cut, paste, rotate and flip**, relative to where you stand. Stairs, logs and doors turn with the copy;
  copied air is not pasted (it does not erase what is around) unless `--air`.

## Selecting
| Command | What it does |
|---|---|
| `/bwr wand` | The wand: left click a block = corner 1, right click = corner 2. Nothing breaks |
| `/bwr pos1 [x y z]`, `/bwr pos2 [x y z]` | A corner where you stand, or at coordinates (`~` works; `--world <w>` from the console) |
| `/bwr sel` | Size, blocks, world and corners. `sel clear`, `sel hide`, `sel show` |
| `/bwr expand <n> [direction]` | Grow towards where you look, or `up`, `down`, `north`, `south`, `east`, `west` (or `u`, `d`, `n`…). `expand vert` = whole world height |
| `/bwr contract <n> [direction]`, `/bwr shift <n> [direction]` | Shrink from that side; move without changing the size |

## Editing
| Command | What it does |
|---|---|
| `/bwr set <pattern>` | Fill the selection |
| `/bwr replace <mask> with <pattern>` | Change only what matches (without `with`, the first word is the mask) |
| `/bwr walls <pattern>` | The four side walls |
| `/bwr faces <pattern>` | Walls, floor and roof: a hollow box |
| `/bwr copy`, `/bwr cut` | Copy (or copy and leave air), relative to where you stand |
| `/bwr paste [--air]` | Paste relative to where you stand; copied air only with `--air` |
| `/bwr rotate <90\|180\|270\|-90>`, `/bwr flip [direction]` | Turn or mirror the clipboard (block states too) |
| `--preview` | On `set`, `replace`, `walls`, `faces`, `cut` and `paste`: show it to you first |
| `/bwr confirm`, `/bwr cancel` | Run the waiting edit; drop it (or stop your running edits: what was done can be undone) |
| `/bwr undo [n]`, `/bwr redo [n]`, `/bwr history` | Undo and redo, also after a restart; your last edits |
| `/bwr jobs` | Running edits, plugin time per tick and the longest tick of the last edit |
| `/bwr limits` | Your limits |
| `/bwr help [command]` | Every command you can use; with a command, an example |

### Patterns (what to place)
```
stone                                  one block (minecraft: is optional)
oak_stairs[facing=north,half=top]      a block state; the rest of its properties keep their defaults
70% stone, 30% andesite                a mix with weights (or no weights: equal parts)
$castle_wall                           a preset from presets.yml
```
### Masks (what to change)
```
dirt, grass_block                      any of these
*_wool, !red_wool                      all wool except red ("not red_wool" works too)
#logs                                  a block tag of the game
oak_log[axis=y]                        only upright oak logs
!air                                   everything except air (air includes cave and void air)
$natural                               a preset from presets.yml
```

## Configuration
`config.yml` (every value is checked on load; `/bwr validate` lists problems, `/bwr reload` applies changes):
```yaml
wand: { item: minecraft:golden_axe }
selection: { show: true, show_every_ticks: 10, max_particles: 400 }
performance: { max_ms_per_tick: 8, max_blocks_per_tick: 200000 }
confirm: { above_blocks: 20000, expire_seconds: 120 }
preview: { max_blocks: 50000 }
history: { max_per_player: 30, keep_days: 7 }
limits:
  default: { max_blocks: 50000, max_jobs: 2, worlds: ["*"], blocked_blocks: [bedrock, barrier, command_block, …] }
  builder: { max_blocks: 1000000, max_jobs: 3 }                 # permission blockwright.limit.builder
  admin:   { max_blocks: 10000000, max_jobs: 5, blocked_blocks: [] }
```
With several groups the most generous value wins: the biggest number, every world of every group, and a block is
blocked only if every group blocks it. `presets.yml` names patterns and masks; `messages.yml` has every text
(MiniMessage, translatable).

## Permissions
| Permission | Default | What it allows |
|---|---|---|
| `blockwright.use` | op | The wand and every edit on your own selection and history |
| `blockwright.moderate` | op | See and undo other players' edits; see and cancel everybody's running edits |
| `blockwright.admin` | op | `reload`, `validate`, `selftest` |
| `blockwright.limit.<group>` | — | The limits of that group in `config.yml` |

## Compatibility
| Server | Status | Evidence |
|---|---|---|
| Paper 1.21.4 (build 232, Java 24) | **Verified** (0.1.0) | Gate 2 (2026-11-01): load, `validate`, `selftest`, 100 000-block edit, `reload` twice, undo after a restart. In-game suite 8/8 twice (2026-11-01) |
| Paper 26.1.2 and other 1.21.x / 26.x | Not verified | |
| Spigot, Folia | Not supported | |

- **Java:** 21 or newer.
- **Optional:** nothing. No network access, no server internals (only the Paper API).
- **Not yet:** `.schem` schematics, brushes and shapes, WorldGuard regions, rollback by area and time, entities
  (ideas in the [roadmap](../../docs/sdd/roadmap.md)). Running next to WorldEdit works: different command and wand.

## Installation
1. Copy `Blockwright-x.y.z.jar` to `plugins/` and start the server.
2. As an operator: `/bwr wand`, click two corners, `/bwr set stone --preview`, `/bwr confirm`, `/bwr undo`.
3. Give your builders `blockwright.use` (and `blockwright.limit.builder` for bigger edits).

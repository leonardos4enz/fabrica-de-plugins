# Worldcraft

World editing for Paper that never lags the server: select with a wand and see the box, preview an edit before it
happens, undo even after a restart (without erasing what others built since), and write patterns and masks the way
you would say them. And terrain that is easy to make: a mountain or a floating island with one word, a terrain brush,
and full control for experienced builders. Specs: [025](../../docs/sdd/specs/025-worldcraft-nucleo/spec.md) (core),
[026](../../docs/sdd/specs/026-worldcraft-terreno-facil/spec.md) (terrain).

```
/wc wand                                        left click = corner 1, right click = corner 2
/wc set 70% stone, 30% andesite --preview      see it first (only you), then /wc confirm
/wc replace dirt, grass_block with $castle_wall
/wc undo                                        works after a restart; leaves alone what others changed since
/wc mountain                                    a mountain where you look: [Build] [Another] [Cancel]
/wc island                                      a floating island in front of you
/wc brush smooth                                a terrain brush: right click to use it, left click for the next mode
```

## Terrain: easy for beginners, precise for experts
With WorldEdit the hard part is not the command but *how to make it look good*: mountains need math in `//generate`,
`//smooth` melts trees and houses, and nothing makes a floating island. Worldcraft does the shaping for you.

**One word, a good result.** Every terrain edit is shown first, only to you, with buttons in the chat:
`[Build]` (`/wc confirm`), `[Another]` (`/wc reroll`, a new seed without aiming again) and `[Cancel]`.
- `/wc mountain [small|medium|large|huge|<radius>] [style]` puts a mountain **where you look** (or under your feet):
  ridged peaks, an irregular outline, a foot that blends into the ground already there. The default style `alpine` puts
  snow on top, rock on steep slopes and grass on the flat parts. It **only adds**: it fills air, plants and water,
  can bury trees, and leaves any build alone (its column is not touched).
- `/wc island [size] [style]` (or `/wc floating_island`) puts a floating island **in front of you**, above your head
  (at your feet if you are flying): an almost flat top, an irregular edge, a rough cone underneath with stalactites,
  hanging roots at the thin edge and small trees with leaves that do not decay. It only goes into the air.
- `/wc brush [raise|lower|smooth|flatten|erode|paint] [radius] [strength]` gives you the **terrain brush** (a golden
  shovel; only that brush works). Right click = one stroke where you look (up to 200 blocks away); left click = the next
  mode; while you hold it, a ring of particles shows where and how big it will be (only for you). Every mode works on the
  **natural ground** (`terrain.ground`): trees, plants and builds are left alone, so smoothing does not melt anything.
  Raising and lowering keep the grass on top and the dirt below; plants on lowered ground are cleared. `erode` collapses
  steps of more than one block downhill; `paint` repaints the surface by slope and height (`style=…`) or with a pattern
  (`/wc brush paint 70% grass_block, 30% podzol`). Each stroke is an edit: limits, history, `undo`.
- Sizes: `medium` (the default) fits the default group's limit of 50 000 blocks; `huge` needs a bigger group.

**Every knob, all optional (experts).** Options go as `key=value` in any order, with tab completion and "did you mean":
```
/wc mountain radius=70 height=90 peaks=3 roughness=0.7 ridges=0.8 sharpness=1.4 crater=0 seed=42 style=alpine
/wc island radius=24 depth=1.2 relief=0.1 spikes=1 roots=0.5 trees=6 seed=7 style=snowy
/wc mountain at=100,64,-20 world=world_nether style=volcano --now     (from the console too; --now skips the preview)
/wc brush paint style=desert mask=grass_block,dirt                    only where the surface is grass or dirt
```
Every message says the seed: the same seed and options give exactly the same result.

**Styles** live in `plugins/Worldcraft/terrain.yml`: layers of material from the surface inwards, each with rules by
relative height (0 = foot, 1 = top), slope in degrees and world height; every block is a Worldcraft pattern. Seven come
with the plugin (`alpine`, `hills`, `desert`, `volcano`, `snowy`, `meadow`, `end`; `/wc styles` lists them). Make your own:
```yaml
styles:
  my_peak:
    description: "Grey peaks with a snow cap"
    blend: 0.06                       # a ragged snow line instead of a straight one
    options: { peaks: 2, ridges: 0.8 } # generator options this style brings
    layers:
      - thickness: 1                  # the surface
        rules:
          - { block: snow_block, height_above: 0.8 }
          - { block: "70% stone, 30% andesite", slope_above: 50 }
          - { block: grass_block }
      - thickness: 3
        rules: [ { block: dirt } ]
      - rules: [ { block: stone } ]   # the rest
    hanging: hanging_roots            # islands: roots under the edge
    tree: { log: spruce_log, leaves: spruce_leaves }   # islands: small trees
```
`terrain.yml` also sets the sizes and the default value of every option. Mistakes are reported with the file, the path
and a suggestion (`styles.my_peak.layers[0].rules[1].slope_abve: unknown condition (did you mean 'slope_above'?)`); a
broken style is skipped and the rest keeps working.

**Safe like every other edit.** Mountains, islands and strokes run in batches inside the plugin's time budget, are
undoable after a restart, and follow the group limits and blocked blocks. Reading the ground also runs in batches,
and the noise is computed off the main thread. A request clearly too big for your group is refused before reading
anything.

## What it does (and what it does better)
- **No lag, ever.** Every edit runs in small batches inside a time budget shared by the whole plugin
  (`performance.max_ms_per_tick`, 8 ms of the 50 ms of a tick). The clock is checked on every block, so a tick never
  goes over by more than one block. Unloaded chunks load in the background. A huge edit and a small one from two
  players share the time in turns, so the small one is not stuck waiting. Progress shows in your action bar.
- **See before you change.** `--preview` shows the result with fake blocks that **only you see**; nothing changes
  until `/wc confirm` (and the edit then does exactly what you saw). Edits bigger than `confirm.above_blocks` (50 000)
  ask first, saying how many blocks and how long (`confirm.enabled: false` turns that off).
- **See your selection without a mod.** While you hold the wand, the selection box is drawn with particles only you
  see. Only the wand that `/wc wand` gives selects: an ordinary golden axe stays an axe.
- **Undo that is not lost.** The history of every player is saved to disk: `undo` and `redo` work after a restart or a
  crash (a half-finished edit can be undone too). **Safe undo:** a block somebody changed after your edit is left
  alone, and you are told how many. Chest contents and sign text come back too. Moderators can see and undo other
  players' edits (`/wc history <player>`, `/wc undo --player <name>`).
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
| `/wc wand` | The wand: left click a block = corner 1, right click = corner 2. Nothing breaks |
| `/wc pos1 [x y z]`, `/wc pos2 [x y z]` | A corner where you stand, or at coordinates (`~` works; `--world <w>` from the console) |
| `/wc sel` | Size, blocks, world and corners. `sel clear`, `sel hide`, `sel show` |
| `/wc expand <n> [direction]` | Grow towards where you look, or `up`, `down`, `north`, `south`, `east`, `west` (or `u`, `d`, `n`…). `expand vert` = whole world height |
| `/wc contract <n> [direction]`, `/wc shift <n> [direction]` | Shrink from that side; move without changing the size |

## Editing
| Command | What it does |
|---|---|
| `/wc set <pattern>` | Fill the selection |
| `/wc replace <mask> with <pattern>` | Change only what matches (without `with`, the first word is the mask) |
| `/wc walls <pattern>` | The four side walls |
| `/wc faces <pattern>` | Walls, floor and roof: a hollow box |
| `/wc copy`, `/wc cut` | Copy (or copy and leave air), relative to where you stand |
| `/wc paste [--air]` | Paste relative to where you stand; copied air only with `--air` |
| `/wc rotate <90\|180\|270\|-90>`, `/wc flip [direction]` | Turn or mirror the clipboard (block states too) |
| `--preview` | On `set`, `replace`, `walls`, `faces`, `cut` and `paste`: show it to you first |
| `/wc confirm`, `/wc cancel` | Run the waiting edit; drop it (or stop your running edits: what was done can be undone) |
| `/wc undo [n]`, `/wc redo [n]`, `/wc history` | Undo and redo, also after a restart; your last edits |
| `/wc jobs` | Running edits, plugin time per tick and the longest tick of the last edit |
| `/wc limits` | Your limits |
| `/wc help [command]` | Every command you can use; with a command, an example |

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
`config.yml` (every value is checked on load; `/wc validate` lists problems, `/wc reload` applies changes):
```yaml
wand: { item: minecraft:golden_axe }
selection: { show: true, show_every_ticks: 10, max_particles: 400 }
performance: { max_ms_per_tick: 8, max_blocks_per_tick: 200000 }
confirm: { enabled: true, above_blocks: 50000, expire_seconds: 120 }
preview: { max_blocks: 50000 }
history: { max_per_player: 30, keep_days: 7 }
limits:
  default: { max_blocks: 50000, max_jobs: 2, worlds: ["*"], blocked_blocks: [bedrock, barrier, command_block, …] }
  builder: { max_blocks: 1000000, max_jobs: 3 }                 # permission worldcraft.limit.builder
  admin:   { max_blocks: 10000000, max_jobs: 5, blocked_blocks: [] }
terrain:
  preview_first: true    # mountains and islands show a preview first (--now skips it)
  reach: 200             # how far the crosshair looks for the ground
  max_radius: 160        # the biggest mountain or island
  ground: "#dirt, #sand, #base_stone_overworld, …"   # natural ground (the only thing brushes move)
  cover: "air, #replaceable, #flowers, #saplings"    # what may be covered or cleared
  buryable: "#logs, #leaves"                         # what a mountain may bury
  brush: { item: minecraft:golden_shovel, max_radius: 16, range: 200, show: true }
```
With several groups the most generous value wins: the biggest number, every world of every group, and a block is
blocked only if every group blocks it. `presets.yml` names patterns and masks; `terrain.yml` has the terrain sizes,
defaults and styles; `messages.yml` has every text (MiniMessage, translatable). Updating from 0.1.0: `terrain.yml` is
created on the next start and a `config.yml` without the `terrain` section uses the values above.

## Permissions
| Permission | Default | What it allows |
|---|---|---|
| `worldcraft.use` | op | The wand and every edit on your own selection and history |
| `worldcraft.moderate` | op | See and undo other players' edits; see and cancel everybody's running edits |
| `worldcraft.admin` | op | `reload`, `validate`, `selftest` |
| `worldcraft.limit.<group>` | — | The limits of that group in `config.yml` |

## Compatibility
| Server | Status | Evidence |
|---|---|---|
| Paper 1.21.4 (build 232, Java 24) | **Verified** (0.2.0) | Gate 2 (2026-11-01): updating from 0.1.0, mountains (up to 811 675 blocks), an island and undo from the console. In-game suite 12/12 twice (2026-11-01), terrain included. 0.1.0: gate 2 with a 100 000-block edit and undo after a restart, suite 8/8 twice |
| Paper 26.1.2 and other 1.21.x / 26.x | Not verified | |
| Spigot, Folia | Not supported | |

- **Java:** 21 or newer.
- **Optional:** nothing. No network access, no server internals (only the Paper API).
- **Not yet:** `.schem` schematics, shapes, rivers, height maps from images, a brush menu, WorldGuard regions,
  rollback by area and time, entities
  (ideas in the [roadmap](../../docs/sdd/roadmap.md)). Running next to WorldEdit works: different command and wand.

## Installation
1. Copy `Worldcraft-x.y.z.jar` to `plugins/` and start the server.
2. As an operator: `/wc wand`, click two corners, `/wc set stone --preview`, `/wc confirm`, `/wc undo`.
3. Give your builders `worldcraft.use` (and `worldcraft.limit.builder` for bigger edits).

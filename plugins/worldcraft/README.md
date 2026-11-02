# Worldcraft

World editing for Paper that never lags the server: select with a wand and see the box, preview an edit before it
happens, undo even after a restart (without erasing what others built since), and write patterns and masks the way
you would say them. And terrain that is easy to make: a mountain or a floating island with one word, a terrain brush,
and full control for experienced builders. Specs: [025](../../docs/sdd/specs/025-worldcraft-nucleo/spec.md) (core),
[026](../../docs/sdd/specs/026-worldcraft-terreno-facil/spec.md) (terrain), [027](../../docs/sdd/specs/027-worldcraft-esquemas-schem/spec.md) (`.schem` schematics),
[028](../../docs/sdd/specs/028-worldcraft-paridad-worldedit/spec.md) (the WorldEdit commands builders use most).

```
/wc wand                                        left click = corner 1, right click = corner 2
/wc set 70% stone, 30% andesite --preview      see it first (only you), then /wc confirm
/wc replace dirt, grass_block with $castle_wall
/wc undo                                        works after a restart; leaves alone what others changed since
/wc mountain                                    a mountain where you look: [Build] [Another] [Cancel]
/wc island                                      a floating island in front of you
/wc brush smooth                                a terrain brush: right click to use it, left click for the next mode
/wc schem load castles/tower                     a WorldEdit .schem into your clipboard: then /wc paste --preview
/wc sphere glass 8 --preview                    shapes, stack, move, smooth, drain, ascend... with WorldEdit's names
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

## Shapes, region tools, fixes around you and moving around
The commands builders use every day in WorldEdit, with the same names (without the slashes) and everything Worldcraft
adds: `--preview`, the group limits, undo after a restart, no lag.

| Command | What it does |
|---|---|
| `/wc sphere <pattern> <radius>[,y,z] [--raised]`, `/wc hsphere …` | A sphere or ellipsoid centered on your feet (`--raised`: sitting on the ground); `h…` = hollow |
| `/wc cyl <pattern> <radius>[,z] [height]`, `/wc hcyl …` | A cylinder from your feet up (negative height: down); the hollow one is only the round wall, like WorldEdit's |
| `/wc pyramid <pattern> <size>`, `/wc hpyramid …`, `/wc cone <pattern> <radius> <height>` | A pyramid or a cone on your feet |
| `--thickness <n>` | How thick a hollow shape is (1 by default) |
| `/wc stack [copies] [direction] [--air] [--select]` | Repeat the selection where you look; copied air is skipped unless `--air`; chests keep their items |
| `/wc move [blocks] [direction] [pattern] [--select]` | Move the selection's contents (far moves only touch the two places); what is left behind becomes air or the pattern |
| `/wc overlay <pattern>` | A layer on top of the ground (never on a tree trunk; flowers are covered) |
| `/wc naturalize` | Grass, 3 dirt, then stone, only on grass, dirt and stone |
| `/wc smooth [passes]` | Smooth the natural ground of the selection; trees and builds are left alone (WorldEdit's melts them) |
| `/wc hollow [thickness] [pattern]`, `/wc center <pattern>`, `/wc line <pattern> [thickness]` | Empty closed things keeping a shell; the middle block(s); a line from corner 1 to corner 2 |
| `/wc drain <radius> [--waterlogged]` | Remove the water or lava nearest to you and what is connected (`--waterlogged` dries stairs and slabs too) |
| `/wc fixwater <radius>`, `/wc fixlava <radius>` | Make the connected flowing water or lava still |
| `/wc fill <pattern> <radius> [depth]` | Fill the hole you stand in, never above your feet |
| `/wc removenear <mask> [radius]`, `/wc replacenear <radius> <mask> with <pattern>` | Remove or replace around you, without selecting |
| `/wc removeabove [size] [height]`, `/wc removebelow …`, `/wc extinguish [radius]` | Clear above or below you; put out fire |
| `/wc snow [radius]`, `/wc thaw [radius]`, `/wc green [radius]` | Snow on the ground (water freezes), melt it, dirt to grass; the top of every column is found even if you fly |
| `/wc ascend`, `/wc descend [floors]`, `/wc ceil`, `/wc thru`, `/wc jumpto`, `/wc unstuck`, `/wc up <blocks>` | Move around while building (permission `worldcraft.navigate`). `up` and `ceil`: if you can fly you fly; if not, a glass block goes under your feet as a normal edit, so `/wc undo` removes it |

From the console, shapes and the commands around you take `--at x,y,z [--world <w>]`. The biggest radius is
`shapes.max_radius` (200) and `utilities.max_radius` (64); your group's block limit always applies too.

## Coming from WorldEdit
| WorldEdit | Worldcraft |
|---|---|
| `//wand`, `//pos1`, `//pos2`, `//expand`, `//contract`, `//shift`, `//size` | `/wc wand`, `/wc pos1`, `/wc pos2`, `/wc expand`, `/wc contract`, `/wc shift`, `/wc sel` |
| `//set`, `//replace`, `//walls`, `//faces` | `/wc set`, `/wc replace <mask> with <pattern>`, `/wc walls`, `/wc faces` (all with `--preview`) |
| `//copy`, `//cut`, `//paste`, `//rotate`, `//flip`, `//schem` | `/wc copy`, `/wc cut`, `/wc paste`, `/wc rotate`, `/wc flip`, `/wc schem` |
| `//undo`, `//redo` | `/wc undo`, `/wc redo` (on disk: they survive a restart) |
| `//sphere`, `//hsphere`, `//cyl`, `//hcyl`, `//pyramid`, `//hpyramid`, `//cone` | `/wc sphere`, `/wc hsphere`, … (same arguments) |
| `//stack`, `//move`, `//overlay`, `//naturalize`, `//smooth`, `//hollow`, `//center`, `//line` | `/wc stack`, `/wc move`, … (`-s` is `--select`, `-a` is `--air`) |
| `//drain`, `/fixwater`, `/fixlava`, `//fill`, `/removenear`, `/replacenear`, `/removeabove`, `/removebelow`, `/ex`, `/snow`, `/thaw`, `/green` | `/wc drain`, `/wc fixwater`, … |
| `/ascend`, `/descend`, `/ceil`, `/thru`, `/jumpto`, `/unstuck`, `/up` | `/wc ascend`, `/wc descend`, … |
| `//brush smooth`, `//brush raise` | `/wc brush smooth`, `/wc brush raise` (only natural ground) |
| — | `/wc mountain`, `/wc island`, styles, `/wc confirm`, `/wc history` |

Not yet (see the [command-by-command comparison](../../docs/investigacion/paridad-worldedit-fawe.md)): selections
that are not boxes, entities, biomes, building brushes (sphere, clipboard, scatter), `//generate` and other formulas,
`//regen`, snapshots, and an API for other plugins. **WorldGuard and PlotSquared need WorldEdit itself**: keep it
installed next to Worldcraft (different command and wand, they work side by side).

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

## Schematics (.schem): coming from WorldEdit
Worldcraft reads and writes the `.schem` files of WorldEdit and FAWE (the open Sponge format), so a server can switch
without losing its schematics, and a schematic made here works on a server that uses WorldEdit.
```
/wc schem list [page] [filter]          yours and WorldEdit's (marked [WorldEdit]); click a name to load it
/wc schem load castles/tower            into your clipboard; then /wc paste --preview, /wc rotate, /wc flip, /wc undo
/wc schem save gate                     your clipboard (already turned if you rotated it) as schematics/gate.schem
/wc schem save gate --overwrite         replace it (needs the permission to delete)
/wc schem delete gate                   moved to schematics/.trash/ for 7 days, in case you want it back
```
- **WorldEdit's folder is read where it is:** `plugins/WorldEdit/schematics` and `plugins/FastAsyncWorldEdit/schematics`
  (`schematics.extra_folders`), subfolders included. Worldcraft never writes or deletes there, and it keeps working if
  you remove WorldEdit and leave its folder. Your own folder is searched first.
- **Reads Sponge v1, v2 and v3** (WorldEdit 6 and 7, FAWE); **writes v3**. It pastes in the same place relative to you as
  WorldEdit does.
- **Chests, barrels, furnaces, hoppers, shulker boxes and signs keep their contents** (items from older game versions are
  upgraded by the game). Other block data (heads, banners, spawners…) is not pasted yet, but it is kept if you save the
  schematic again; the message says how many. Entities and biomes are not loaded (the message counts the entities).
- **Old or unknown blocks:** renamed blocks are translated (`grass` → `short_grass`, `grass_path` → `dirt_path`,
  `chain` ↔ `iron_chain`), a property this server does not have is dropped, and a block it does not know (from a mod)
  becomes air, with the names in the message.
- **Safe with anybody's files:** names are letters, numbers, `_`, `-` and `.` with up to 3 folders (never `..`, and a
  link that points outside the folder is refused); a file bigger than `max_file_kb`, a schematic bigger than
  `max_blocks` (read from the header, before using any memory), a zip bomb or a damaged file is refused with the
  reason and your clipboard does not change. Loading never skips your limits: pasting still checks your biggest edit,
  your worlds and the blocked blocks (the `default` group cannot paste the command blocks of a schematic).
- **No lag:** reading, writing and compressing run on their own thread; the block data that needs the game is converted
  in small steps inside the same 8 ms per tick as every edit.

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
schematics:
  max_file_kb: 10240     # the biggest .schem that is loaded
  max_blocks: 5000000    # the biggest schematic that is loaded (pasting still uses your limits)
  trash_days: 7          # deleted schematics wait this long in schematics/.trash/
  extra_folders: [plugins/WorldEdit/schematics, plugins/FastAsyncWorldEdit/schematics]   # read-only
shapes: { max_radius: 200 }       # the biggest sphere, cylinder, pyramid or cone (your group limit still applies)
utilities: { max_radius: 64 }     # drain, fixwater, fill, removenear, snow, thaw, green...
```
With several groups the most generous value wins: the biggest number, every world of every group, and a block is
blocked only if every group blocks it. `presets.yml` names patterns and masks; `terrain.yml` has the terrain sizes,
defaults and styles; `messages.yml` has every text (MiniMessage, translatable). Updating from 0.1.0: `terrain.yml` is
created on the next start and a `config.yml` without the `terrain` section uses the values above. Updating from 0.2.0: a
`config.yml` without `schematics` uses the values above and `schematics/` is created on the next start. Updating from
0.3.0: a `config.yml` without `shapes` and `utilities` uses the values above.

## Permissions
| Permission | Default | What it allows |
|---|---|---|
| `worldcraft.use` | op | The wand and every edit on your own selection and history |
| `worldcraft.moderate` | op | See and undo other players' edits; see and cancel everybody's running edits |
| `worldcraft.admin` | op | `reload`, `validate`, `selftest` |
| `worldcraft.schematic` | op | `/wc schem list`, `load` and `save` (pasting also needs `worldcraft.use`) |
| `worldcraft.schematic.delete` | op | `/wc schem delete` and `save --overwrite` |
| `worldcraft.navigate` | op | `/wc ascend`, `descend`, `ceil`, `thru`, `jumpto`, `unstuck`, `up` |
| `worldcraft.limit.<group>` | — | The limits of that group in `config.yml` |

## Compatibility
| Server | Status | Evidence |
|---|---|---|
| Paper 1.21.4 (build 232, Java 24) | **Verified** (0.4.0) | 0.4.0 (2026-11-01): gate 2 updating from 0.3.0 (every shape, hollow, center, stack with a chest, move, line, drain and undo, fixwater, fill, removenear, snow, thaw, green, naturalize, overlay, extinguish, removebelow from the console: 31 checks); in-game suite 18/18 twice (a sphere previewed only for the editor, a pyramid and unstuck, stack and move with undo, drain, ascend, descend, thru, up with a glass block for a player who cannot fly). 0.3.0 (2026-11-01): 0.3.0 (2026-11-01): gate 2 updating from 0.2.0 (save, load and paste a rotated chest and sign from the console, damaged files and unsafe names refused); in-game suite 15/15 twice with WorldEdit 7.3.19 on the server (WorldEdit's v3 and v2 files loaded and pasted by Worldcraft, Worldcraft's loaded and pasted by WorldEdit). 0.2.0: gate 2 (2026-11-01): updating from 0.1.0, mountains (up to 811 675 blocks), an island and undo from the console. In-game suite 12/12 twice (2026-11-01), terrain included. 0.1.0: gate 2 with a 100 000-block edit and undo after a restart, suite 8/8 twice |
| Paper 26.1.2 and other 1.21.x / 26.x | Not verified | |
| Spigot, Folia | Not supported | |

- **Java:** 21 or newer.
- **Optional:** nothing. No network access, no server internals (only the Paper API).
- **Not yet:** entities and biomes in schematics, `.schematic` (MCEdit) and `.litematic` files, non-box selections, biomes, building brushes, an API for other plugins, rivers, height maps from images, a brush menu, WorldGuard regions,
  rollback by area and time, entities
  (ideas in the [roadmap](../../docs/sdd/roadmap.md)). Running next to WorldEdit works: different command and wand.

## Installation
1. Copy `Worldcraft-x.y.z.jar` to `plugins/` and start the server.
2. As an operator: `/wc wand`, click two corners, `/wc set stone --preview`, `/wc confirm`, `/wc undo`.
3. Give your builders `worldcraft.use` (and `worldcraft.limit.builder` for bigger edits).

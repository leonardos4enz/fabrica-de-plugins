# SkinStatues

Block statues of any player's skin: type a name and a height, and the player's figure is built in the world out of
blocks, in the pose you choose, with the second skin layer in relief and the cape on its back.
Specs: [021](../../docs/sdd/specs/021-skinstatues-nucleo/spec.md) (core),
[022](../../docs/sdd/specs/022-skinstatues-poses-y-coordenadas/spec.md) (more poses, your own poses, coordinates).

```
/statue build Example 32 waving
/statue build Example 48 t_pose --at 100 64 -20 --facing north
```

![The 28 built-in poses](../../docs/img/skinstatues/poses.png)

## What it does
- **Any height.** A player is 32 skin pixels tall: height 32 is one block per pixel, 64 is two, 16 averages four
  pixels into each block. From `min_height` to `max_height` (8 to 128 by default). The height is the figure standing,
  hat included; raised arms can reach a few blocks higher and a sitting statue is lower.
- **Where you stand, or where you say.** By default the feet go 2 blocks above you (`offset_up`), so you are never
  trapped inside, and the statue faces the direction you are looking (rounded to north, south, east or west). With
  `--at x y z` (`~` works) and `--facing` it goes exactly where you want, even from the console.
- **Hollow.** Only the skin surface is built, so a big statue costs a fraction of the blocks and looks the same.
- **Both skin layers, joined.** The hat, jacket, sleeves and pants stick out in relief right on top of the first
  layer, like the *3D Skin Layers* mod. On small statues, where there is no room for relief, the second layer paints
  over the first. Transparent pixels add nothing.
- **28 poses with elbows and knees.** Arms and legs bend in the middle without gaps; head, body and cape turn too.
  Built in: `standing`, `waving`, `walking`, `victory`, `pointing`, `sitting`, `running`, `saluting`, `thinking`,
  `arms_crossed`, `hands_on_hips`, `kneeling`, `flexing`, `dabbing`, `charging`, `bowing`, `t_pose`, `zombie`,
  `sitting_floor`, `lying`, `praying`, `crouching`, `fighting`, `archer`, `relaxing`, `facepalm`, `waving_left`,
  `pointing_left`.
- **Your own poses, the easy way.** Write them in `poses.yml` with angles in degrees; start from another pose with
  `extends` and change only what is different, or flip one left to right with `mirror`.
- **The cape** of the account (or your own PNG), with its outside facing out.
- **You choose the blocks.** Palettes are whitelists of block ids with wildcards and exclusions: only wool, only
  terracotta, everything… Colors are matched the way the eye sees them.
- **No lag.** The figure is computed off the main thread and blocks are placed in batches per tick, with a progress
  bar. Statues queue up one after another.
- **Undo without losing terrain.** Every block that was there (grass, water, stairs with their facing…) is saved
  before it is replaced; `undo` and `remove` put it back. Blocks someone changed after the statue was built are left
  alone, and blocks with contents (chests, signs, furnaces…) are never replaced.

## Commands and permissions
| Command | What it does | Permission (all `op` by default) |
|---|---|---|
| `/statue build <name> <height> [pose] [--at <x> <y> <z>] [--facing <dir>] [--world <w>] [--palette <p>] [--no-overlay] [--no-cape]` | Build a statue | `skinstatues.build` |
| `/statue undo` | Remove your last statue (from the console, the console's last one) | `skinstatues.build` |
| `/statue cancel [id]` | Stop a statue being built (what was placed can be removed) | `skinstatues.build` |
| `/statue remove <id>` | Remove a statue and restore what was there | `skinstatues.remove` (`skinstatues.remove.others` for other players' statues) |
| `/statue list [page]`, `/statue info <id>` | Statues, where they are and who built them | `skinstatues.build` |
| `/statue poses`, `/statue palettes` | What is loaded (poses: built in and yours) | `skinstatues.build` |
| `/statue reload`, `/statue validate`, `/statue selftest` | Reload the files, list config problems, build and remove a test statue | `skinstatues.admin` |

Players without a permission do not see those subcommands when they press Tab. `/statues` is an alias.

## Where the statue goes
| You write | The statue |
|---|---|
| nothing | Centered on you, feet 2 blocks above you (`offset_up`), facing where you look |
| `--at 100 64 -20` | Feet **in** block 100 64 -20 (the lowest layer of blocks is at y = 64, so use the y just above the ground), centered on x and z, without `offset_up` |
| `--at ~ ~ ~8` | Relative to you, like Minecraft commands: here, 8 blocks south of you, at your feet |
| `--facing north` | Faces north (`north`, `south`, `east`, `west`); without it, where you look |
| `--world world_nether` | In that world (with `--at`) |

From the **console** (or a script), `--at` with plain numbers is required and `--world` defaults to the main world:
`statue build Example 32 victory --at 0 70 0 --world world --facing south`. Building far from every player is fine:
the chunks the statue needs are loaded (or generated) in the background first and kept loaded until the last block is
placed, so the server does not freeze. Coordinates outside the world border are refused.

## Where skins come from
1. **A file:** `plugins/SkinStatues/skins/<name>.png` (64×64, or the old 64×32). Optionally `skins/<name>.yml`:
   ```yaml
   model: slim            # 3-pixel arms (default: classic)
   cape: my-cape.png      # a 64x32 cape in the same folder
   ```
   The plugin comes with `Example.png`, an original skin (a builder with a hard hat and a safety vest, and a green
   cape), so `/statue build Example 32` works right away.
2. **Mojang, by account name**, only if you turn it on in `config.yml` (`network.mojang: true`). The plugin then asks
   Mojang's servers (and nothing else) for the skin, the arm model and the official cape, and keeps them in `cache/`
   for `network.cache_hours`. If Mojang does not answer, the last downloaded copy is used.

## Configuration
Five files in `plugins/SkinStatues/`, all checked when they load: a mistake names the file, the path inside it and
what is wrong, with a "did you mean…?"; the rest keeps working. `/statue reload` applies changes.

### config.yml
```yaml
min_height: 8
max_height: 128
max_blocks: 400000         # largest statue allowed
offset_up: 2               # feet this many blocks above the player
default_pose: standing
default_palette: all
overlay:
  enabled: true
  depth: 0.5               # skin pixels the second layer sticks out (at least one block)
  alpha_threshold: 128
cape: true
build:
  blocks_per_tick: 4000
  max_ms_per_tick: 10
network:
  mojang: false
  cache_hours: 24
```

### poses.yml — your own poses
The 28 built-in poses live inside the plugin, so they update with it. `builtin-poses.yml` (next to `poses.yml`) is a
copy for reading their numbers: it is rewritten on every start, so copy a pose from it into `poses.yml` to change it.
A pose in `poses.yml` with a new name is added; one with the name of a built-in pose replaces it.

```yaml
waving:                     # how the built-in "waving" is written
  head: { pitch: -5, yaw: 10 }
  right_arm: { roll: 135, elbow: 40 }
  cape: { pitch: 8 }

my_wave:                    # yours: "waving" with the elbow bent more
  extends: waving
  right_arm: { elbow: 90 }  # only what changes, angle by angle: roll 135 is kept

my_point_left:              # "pointing" with the left hand
  extends: pointing
  mirror: true              # flip left <-> right first...
  head: { pitch: 10 }       # ...then apply what is written here
```
Parts: `head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`, `cape` ("right" is the statue's own right).
Angles in degrees, from -180 to 180; leave out what does not move.

| Angle | Arms and legs | Head | Body | Cape |
|---|---|---|---|---|
| `pitch` | + forward / up, − backward | + looks down | + leans forward | + lifts it back |
| `yaw` | + turns toward the statue's left | + looks to its left | + turns to its left | + turns to its left |
| `roll` | + opens outward (to the side) | + tilts to its left | + tilts to its left | + tilts to its left |
| `elbow` | arms only, 0 to 150: bends the forearm forward | | | |
| `knee` | legs only, 0 to 150: bends the lower leg backward | | | |

Handy values: an arm straight forward is `{ pitch: 90 }`, straight up `{ roll: 180 }`, out to the side `{ roll: 90 }`;
a hand on the shoulder is `{ elbow: 150 }`; a sitting leg is `{ pitch: 90, knee: 90 }`. The body carries the head, the
arms and the cape with it; the legs stay where they are. Angles are applied pitch first, then yaw, then roll, so a
`yaw` on an arm hanging down only turns the direction its elbow bends: `right_arm: { yaw: 60, elbow: 100 }` brings the
hand across the belly (that is how `arms_crossed` and `hands_on_hips` are made).

`extends` can name a built-in pose or one of yours, written before or after it, up to 8 levels deep. Mistakes (a pose
that does not exist, a circle of `extends`, `mirror` without `extends`, a wrong angle…) are reported with the file, the
path and a "did you mean…?", and only that pose is skipped. `/statue poses` lists the built-in ones and yours (a
built-in one you replaced shows a `*`). To try a pose quickly: edit, `/statue reload`,
`/statue build Example 16 <pose> --at ~ ~ ~6`, look, `/statue undo`.

### palettes.yml
```yaml
wool: ["*_wool"]
wool_and_terracotta: ["*_wool", "*_terracotta", "-pink_wool"]
```
Each palette is a whitelist of block ids (`white_wool` or `minecraft:white_wool`), wildcards (`*_wool`) and
exclusions (`-pink_wool`), applied in order. Built in: `all` (the default), `wool`, `concrete`, `terracotta`.

### block-colors.yml
The color of each of the 145 blocks the palettes can use (wool, concrete, terracotta, planks, stones, ores, waxed
copper…), measured as the average of each block's side texture. Edit a color to change when a block is picked, or
remove a line to never use that block. Only solid, opaque blocks that do not fall are accepted.

### messages.yml
Every message, in English, with MiniMessage. Translate freely.

## Compatibility
| Server | Status | Evidence |
|---|---|---|
| Paper 1.21.4 (build 232, Java 24) | **Verified** (0.2.0) | Gate 2 and in-game suite 14/14 twice (2026-11-01) |
| Paper 26.1.2 (build 74, Java 25) | **Verified** (0.2.0) | In-game suite 14/14 twice (2026-11-01) |
| Other Paper 1.21.x and 26.x | Not verified | |
| Spigot, Folia | Not supported | |

- **Java:** 21 or newer (26.x needs Java 25).
- **Optional:** nothing. Network access only if you turn on `network.mojang`.
- **Not yet:** armor and items in the hands, filled statues, diagonal facing (ideas in the [roadmap](../../docs/sdd/roadmap.md)).

## Installation
1. Copy `SkinStatues-x.y.z.jar` to `plugins/` and start the server.
2. Try `/statue build Example 32` (as an operator), and `/statue poses` to see every pose.
3. For your players' skins, put their PNGs in `plugins/SkinStatues/skins/`, or set `network.mojang: true`.

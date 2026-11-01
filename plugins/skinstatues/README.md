# SkinStatues

Block statues of any player's skin: type a name and a height, and the player's figure is built in the world out of
blocks, in the pose you choose, with the second skin layer in relief and the cape on its back.
Spec: [021](../../docs/sdd/specs/021-skinstatues-nucleo/spec.md).

```
/statue build Example 32 waving
```

## What it does
- **Any height.** A player is 32 skin pixels tall: height 32 is one block per pixel, 64 is two, 16 averages four
  pixels into each block. From `min_height` to `max_height` (8 to 128 by default). The height is the figure standing,
  hat included; raised arms can reach a few blocks higher and a sitting statue is lower.
- **Where you stand, facing where you look.** The feet go 2 blocks above you (`offset_up`), so you are never trapped
  inside, and the statue faces the direction you are looking (rounded to north, south, east or west).
- **Hollow.** Only the skin surface is built, so a big statue costs a fraction of the blocks and looks the same.
- **Both skin layers, joined.** The hat, jacket, sleeves and pants stick out in relief right on top of the first
  layer, like the *3D Skin Layers* mod. On small statues, where there is no room for relief, the second layer paints
  over the first. Transparent pixels add nothing.
- **Poses with elbows and knees.** Arms and legs bend in the middle without gaps; head, body and cape turn too.
  Six poses come ready: `standing`, `waving`, `walking`, `victory`, `pointing`, `sitting`.
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
| `/statue build <name> <height> [pose] [--palette <p>] [--no-overlay] [--no-cape]` | Build a statue | `skinstatues.build` |
| `/statue undo` | Remove your last statue | `skinstatues.build` |
| `/statue cancel [id]` | Stop a statue being built (what was placed can be removed) | `skinstatues.build` |
| `/statue remove <id>` | Remove a statue and restore what was there | `skinstatues.remove` (`skinstatues.remove.others` for other players' statues) |
| `/statue list [page]`, `/statue info <id>` | Statues, where they are and who built them | `skinstatues.build` |
| `/statue poses`, `/statue palettes` | What is loaded | `skinstatues.build` |
| `/statue reload`, `/statue validate`, `/statue selftest` | Reload the files, list config problems, build and remove a test statue | `skinstatues.admin` |

Players without a permission do not see those subcommands when they press Tab. `/statues` is an alias.

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
Four files in `plugins/SkinStatues/`, all checked when they load: a mistake names the file, the path inside it and
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

### poses.yml
```yaml
waving:
  head: { pitch: -5, yaw: 10 }
  right_arm: { roll: 135, elbow: 40 }
  cape: { pitch: 8 }
```
Parts: `head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`, `cape`. Angles in degrees (-180 to 180):
`pitch` raises an arm or leg forward (head: looks down; body: leans forward; cape: lifts it back), `yaw` turns to the
statue's left, `roll` opens an arm or leg outward (head and body: tilt to its left). `elbow` (arms) and `knee` (legs)
go from 0 to 150. The body carries the head, the arms and the cape with it.

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
| Paper 1.21.4 (build 232, Java 24) | **Verified** | Gate 2 and in-game suite 10/10 twice (2026-11-01) |
| Paper 26.1.2 (build 74, Java 25) | **Verified** | In-game suite 10/10 twice (2026-11-01) |
| Other Paper 1.21.x and 26.x | Not verified | |
| Spigot, Folia | Not supported | |

- **Java:** 21 or newer (26.x needs Java 25).
- **Optional:** nothing. Network access only if you turn on `network.mojang`.
- **Not yet:** armor and items in the hands (next spec), filled statues, diagonal facing.

## Installation
1. Copy `SkinStatues-x.y.z.jar` to `plugins/` and start the server.
2. Try `/statue build Example 32` (as an operator).
3. For your players' skins, put their PNGs in `plugins/SkinStatues/skins/`, or set `network.mojang: true`.

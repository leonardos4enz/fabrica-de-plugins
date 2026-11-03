# Worldcraft commands, and how they compare with WorldEdit and FAWE

Worldcraft 0.14.0 has **102 commands** under `/wc` (alias of `/worldcraft`), and every one also works the WorldEdit
way: `//set`, `//copy -e`, `//paste -a`, `/brush`, `//undo` (WorldEdit's names, aliases and short flags; spec 039). Counted the way WorldEdit counts its own
(every brush, tool and sub-command on its own line), that is **140**.

It covers **129 of WorldEdit's 171 commands** (75 %) and **142 of FastAsyncWorldEdit's 244** (58 %), counting only the
ones Worldcraft does the same or better; a few more are there in part (below). The 171 and 244 come from the
`@Command` annotations in the bytecode of the WorldEdit 7.3.19 and FAWE 2.16.2 jars, sub-commands included; FAWE's
21 `/anvil` commands are compiled into its jar but never registered, so they are not counted.

| | Commands | Worldcraft has it | In part | Not yet | No, on purpose | Not needed |
|---|---|---|---|---|---|---|
| WorldEdit 7.3.19 | 171 | **129** | 5 | 28 | 4 | 5 |
| FAWE 2.16.2 | 244 | **142** | 12 | 68 | 14 | 8 |
| FAWE, only what it adds to WorldEdit | 79 | 14 | 8 | 44 | 10 | 3 |

*Not needed* means Worldcraft has nothing to tune there (`//fast`, `//perf`, `//watchdog`: every edit already runs in
batches inside a time budget). *No, on purpose* means it will not be added (scripts, commands run for the player,
uploads to outside services, server internals).

Worldcraft is not affiliated with or endorsed by EngineHub (WorldEdit), IntellectualSites (FastAsyncWorldEdit) or Mojang.

## Only in Worldcraft
`/wc mountain` and `/wc island` (terrain in one word, with `[Build] [Another] [Cancel]`), `/wc reroll`, `/wc styles`,
`--preview` on every edit, `/wc jobs`, `/wc limits`, `/wc validate`, `/wc selftest`, `/wc schem import`, the terrain
brush (`raise`, `lower`, `smooth`, `flatten`, `erode`, `paint`) on natural ground only, and undo that survives a restart.

## The 102 commands of Worldcraft
| Group | Commands |
|---|---|
| Select (15) | `/wc wand`, `/wc pos1`, `/wc pos2`, `/wc hpos1`, `/wc hpos2`, `/wc sel`, `/wc expand`, `/wc contract`, `/wc shift`, `/wc outset`, `/wc inset`, `/wc trim`, `/wc chunk`, `/wc count`, `/wc distr` |
| Edit a selection (13) | `/wc set`, `/wc replace`, `/wc walls`, `/wc faces`, `/wc stack`, `/wc move`, `/wc overlay`, `/wc naturalize`, `/wc smooth`, `/wc hollow`, `/wc center`, `/wc line`, `/wc gmask` |
| Preview, undo and jobs (8) | `/wc confirm`, `/wc cancel`, `/wc undo`, `/wc redo`, `/wc history`, `/wc clearhistory`, `/wc jobs`, `/wc limits` |
| Copy and schematics (8) | `/wc copy`, `/wc cut`, `/wc paste`, `/wc rotate`, `/wc flip`, `/wc clearclipboard`, `/wc schem`, `/wc structure` |
| Shapes (7) | `/wc sphere`, `/wc hsphere`, `/wc cyl`, `/wc hcyl`, `/wc pyramid`, `/wc hpyramid`, `/wc cone` |
| Terrain (4) | `/wc mountain`, `/wc island`, `/wc reroll`, `/wc styles` |
| Biomes, trees and plants (7) | `/wc biomelist`, `/wc biomeinfo`, `/wc setbiome`, `/wc forest`, `/wc forestgen`, `/wc flora`, `/wc pumpkins` |
| Formulas (5) | `/wc generate`, `/wc generatebiome`, `/wc deform`, `/wc calc`, `/wc timeout` |
| Caves, ores and pictures (4) | `/wc caves`, `/wc ore`, `/wc ores`, `/wc image` |
| Fixes around you (12) | `/wc drain`, `/wc fixwater`, `/wc fixlava`, `/wc fill`, `/wc removenear`, `/wc replacenear`, `/wc removeabove`, `/wc removebelow`, `/wc extinguish`, `/wc snow`, `/wc thaw`, `/wc green` |
| Entities (2) | `/wc butcher`, `/wc remove` |
| Brushes and tools (3) | `/wc brush`, `/wc tool`, `/wc superpickaxe` |
| Moving around (7) | `/wc unstuck`, `/wc ascend`, `/wc descend`, `/wc ceil`, `/wc thru`, `/wc jumpto`, `/wc up` |
| Back to the original (3) | `/wc regen`, `/wc snapshot`, `/wc restore` |
| Admin (4) | `/wc help`, `/wc reload`, `/wc validate`, `/wc selftest` |

Inside the groups:
- **19 brushes** (`/wc brush …`): 12 for building (`sphere`, `cyl`, `set`, `clipboard`, `splatter`, `scatter`, `rock`,
  `gravity`, `extinguish`, `butcher`, `forest`, `deform`), 6 terrain modes (`raise`, `lower`, `smooth`, `flatten`,
  `erode`, `paint`) and `none`.
- **10 tools** (`/wc tool …`): `repl`, `floodfill`, `info`, `cycler`, `deltree`, `farwand`, `lrbuild`, `navwand`, `tree`
  and `none`.
- **5 schematic commands** (`/wc schem save|load|list|delete|import`), **5 backup commands** (`/wc snapshot
  list|use|sel|before|after`) and **3 super pickaxe modes** (`single`, `area`, `recursive`).
- **11 selection words** (`/wc sel cuboid|extend|poly|ellipsoid|sphere|cyl|convex|magic|clear|show|hide`; WorldEdit and
  FAWE's names `box`, `fuzzy`, `hull` and `polyhedral` work too).

Every command: `/wc help <command>` in the game. Full guide: [the README](../../../plugins/worldcraft/README.md).

## Every WorldEdit 7.3.19 command
| WorldEdit | In Worldcraft | How |
|---|---|---|
| `/brush apply … forest` | yes | `/wc brush forest` |
| `/brush apply … set` | yes | `/wc brush set` |
| `/biomelist` | yes | `/wc biomelist` |
| `/biomeinfo` | yes | `/wc biomeinfo` |
| `//setbiome` | yes | `/wc setbiome` |
| `/brush none` | yes | `/wc brush none` |
| `/brush sphere` | yes | `/wc brush sphere` |
| `/brush cylinder` | yes | `/wc brush cyl` |
| `/brush splatter` | yes | `/wc brush splatter` |
| `/brush clipboard` | yes | `/wc brush clipboard` |
| `/brush smooth` | yes | `/wc brush smooth` |
| `/brush extinguish` | yes | `/wc brush extinguish` |
| `/brush gravity` | yes | `/wc brush gravity` |
| `/brush butcher` | yes | `/wc brush butcher` |
| `/brush deform` | yes | `/wc brush deform` |
| `/brush set` | yes | `/wc brush set` |
| `/brush forest` | yes | `/wc brush forest` |
| `/brush raise` | yes | `/wc brush raise` |
| `/brush lower` | yes | `/wc brush lower` |
| `/brush erode` | yes | `/wc brush erode` |
| `//copy` | yes | `/wc copy` |
| `//cut` | yes | `/wc cut` |
| `//paste` | yes | `/wc paste` |
| `//rotate` | yes | `/wc rotate` |
| `//flip` | yes | `/wc flip` |
| `/clearclipboard` | yes | `/wc clearclipboard` |
| `//expand` | yes | `/wc expand` |
| `//timeout` | yes | `/wc timeout` |
| `//drawsel` | yes | the wand draws the selection with particles; `/wc sel show|hide` |
| `/gmask` | yes | `/wc gmask` |
| `//hcyl` | yes | `/wc hcyl` |
| `//cyl` | yes | `/wc cyl` |
| `//cone` | yes | `/wc cone` |
| `//hsphere` | yes | `/wc hsphere` |
| `//sphere` | yes | `/wc sphere` |
| `/forestgen` | yes | `/wc forestgen` |
| `/pumpkins` | yes | `/wc pumpkins` |
| `//hpyramid` | yes | `/wc hpyramid` |
| `//pyramid` | yes | `/wc pyramid` |
| `//generate` | yes | `/wc generate` |
| `//generatebiome` | yes | `/wc generatebiome` |
| `/undo` | yes | `/wc undo` |
| `/redo` | yes | `/wc redo` |
| `/clearhistory` | yes | `/wc clearhistory` |
| `/unstuck` | yes | `/wc unstuck` |
| `/ascend` | yes | `/wc ascend` |
| `/descend` | yes | `/wc descend` |
| `/ceil` | yes | `/wc ceil` |
| `/thru` | yes | `/wc thru` |
| `/jumpto` | yes | `/wc jumpto` |
| `/up` | yes | `/wc up` |
| `/brush paint … forest` | yes | `/wc brush forest` |
| `/brush paint … set` | yes | `/wc brush paint <pattern>` |
| `//set` | yes | `/wc set` |
| `//line` | yes | `/wc line` |
| `//replace` | yes | `/wc replace` |
| `//overlay` | yes | `/wc overlay` |
| `//center` | yes | `/wc center` |
| `//naturalize` | yes | `/wc naturalize` |
| `//walls` | yes | `/wc walls` |
| `//faces` | yes | `/wc faces` |
| `//smooth` | yes | `/wc smooth` |
| `//move` | yes | `/wc move` |
| `//stack` | yes | `/wc stack` |
| `//regen` | yes | `/wc regen` |
| `//deform` | yes | `/wc deform` |
| `//hollow` | yes | `/wc hollow` |
| `//forest` | yes | `/wc forest` |
| `//flora` | yes | `/wc flora` |
| `/schem load` | yes | `/wc schem load` |
| `/schem save` | yes | `/wc schem save` |
| `/schem delete` | yes | `/wc schem delete` |
| `/schem list` | yes | `/wc schem list` |
| `//pos1` | yes | `/wc pos1` |
| `//pos2` | yes | `/wc pos2` |
| `//hpos1` | yes | `/wc hpos1` |
| `//hpos2` | yes | `/wc hpos2` |
| `//chunk` | yes | `/wc chunk` |
| `//wand` | yes | `/wc wand` |
| `//contract` | yes | `/wc contract` |
| `//shift` | yes | `/wc shift` |
| `//outset` | yes | `/wc outset` |
| `//inset` | yes | `/wc inset` |
| `//trim` | yes | `/wc trim` |
| `//size` | yes | `/wc sel` |
| `//count` | yes | `/wc count` |
| `//distr` | yes | `/wc distr` |
| `//sel` | yes | `/wc sel` |
| `/snapshot list` | yes | `/wc snapshot list` |
| `/snapshot use` | yes | `/wc snapshot use` |
| `/snapshot sel` | yes | `/wc snapshot sel` |
| `/snapshot before` | yes | `/wc snapshot before` |
| `/snapshot after` | yes | `/wc snapshot after` |
| `/restore` | yes | `/wc restore` |
| `/superpickaxe single` | yes | `/wc sp single` |
| `/superpickaxe area` | yes | `/wc sp area` |
| `/superpickaxe recursive` | yes | `/wc sp recursive` |
| `/tool none` | yes | `/wc tool none` |
| `/tool selwand` | yes | `/wc wand` |
| `/tool navwand` | yes | `/wc tool navwand` |
| `/tool info` | yes | `/wc tool info` |
| `/tool tree` | yes | `/wc tool tree` |
| `/tool repl` | yes | `/wc tool repl` |
| `/tool cycler` | yes | `/wc tool cycler` |
| `/tool floodfill` | yes | `/wc tool floodfill` |
| `/tool deltree` | yes | `/wc tool deltree` |
| `/tool farwand` | yes | `/wc tool farwand` |
| `/tool lrbuild` | yes | `/wc tool lrbuild` |
| `//` | yes | `/wc sp` |
| `/mask` | yes | `/wc brush … mask=…` |
| `/size` | yes | `/wc brush … radius=…` |
| `//fill` | yes | `/wc fill` |
| `//drain` | yes | `/wc drain` |
| `/fixlava` | yes | `/wc fixlava` |
| `/fixwater` | yes | `/wc fixwater` |
| `/removeabove` | yes | `/wc removeabove` |
| `/removebelow` | yes | `/wc removebelow` |
| `/removenear` | yes | `/wc removenear` |
| `/replacenear` | yes | `/wc replacenear` |
| `/snow` | yes | `/wc snow` |
| `/thaw` | yes | `/wc thaw` |
| `/green` | yes | `/wc green` |
| `/extinguish` | yes | `/wc extinguish` |
| `/butcher` | yes | `/wc butcher` |
| `/remove` | yes | `/wc remove` |
| `//calculate` | yes | `/wc calc` |
| `//help` | yes | `/wc help` |
| `/worldedit reload` | yes | `/wc reload` |
| `/worldedit help` | yes | `/wc help` |
| `//limit` | partly | `/wc limits` (shows your group's limits; set by permission, not per player) |
| `//world` | partly | `/wc pos1 … --world` |
| `//structure` | partly | `/wc structure load` + `/wc paste` (templates, not whole jigsaw structures) |
| `//pos` | partly | `/wc pos1` and `/wc pos2` |
| `/material` | partly | write the brush again with the new pattern |
| `/brush apply … item` | not yet |  |
| `/brush snowsmooth` | not yet |  |
| `/brush heightmap` | not yet |  |
| `/brush feature` | not yet |  |
| `/brush snow` | not yet |  |
| `/brush biome` | not yet |  |
| `/brush morph` | not yet |  |
| `/brush dilate` | not yet |  |
| `/chunkinfo` | not yet |  |
| `/listchunks` | not yet |  |
| `/toggleplace` | not yet |  |
| `/placement` | not yet |  |
| `/searchitem` | not yet |  |
| `//feature` | not yet |  |
| `/brush paint … item` | not yet |  |
| `//curve` | not yet |  |
| `//snowsmooth` | not yet |  |
| `//update` | not yet |  |
| `/schem formats` | not yet |  |
| `/toggleeditwand` | not yet |  |
| `/tool stacker` | not yet |  |
| `/range` | not yet |  |
| `/tracemask` | not yet |  |
| `//fillr` | not yet |  |
| `/worldedit version` | not yet |  |
| `/worldedit report` | not yet |  |
| `/worldedit trace` | not yet |  |
| `/worldedit tz` | not yet |  |
| `/delchunks` | no (on purpose) | deletes region files with the server stopped; outside the safe, undoable model |
| `/schem share` | no (on purpose) | uploads to an outside service |
| `/cs` | no (on purpose) | runs JavaScript on the server |
| `/.s` | no (on purpose) | runs JavaScript on the server |
| `//fast` | not needed | nothing to tune: every edit is already batched |
| `//perf` | not needed | nothing to tune: every edit is already batched |
| `//reorder` | not needed | nothing to tune: every edit is already batched |
| `//watchdog` | not needed | nothing to tune: an edit never holds a tick |
| `/worldedit cui` | not needed | the selection is drawn with particles, no client mod |

## What FAWE 2.16.2 adds to WorldEdit
FAWE has the WorldEdit commands above (except `//pos`, `//update`, `/placement`, `/brush none`, `/brush heightmap` and
`/worldedit report`) and these 79 more.

| FAWE | In Worldcraft | How |
|---|---|---|
| `/brush rock` | yes | `/wc brush rock` |
| `/brush scatter` | yes | `/wc brush scatter` |
| `/brush flatten` | yes | `/wc brush flatten` |
| `//caves` | yes | `/wc caves` |
| `//ores` | yes | `/wc ores` |
| `//img` | yes | `/wc image` |
| `//ore` | yes | `/wc ore` |
| `//history list` | yes | `/wc history` |
| `//history clear` | yes | `/wc clearhistory` |
| `//air` | yes | `/wc set air` |
| `/schem clear` | yes | `/wc clearclipboard` |
| `/schem unload` | yes | `/wc clearclipboard` |
| `//cancel` | yes | `/wc cancel` |
| `//confirm` | yes | `/wc confirm` |
| `/brush blendball` | partly | `/wc brush smooth` (terrain) |
| `/brush surface` | partly | `/wc brush paint` |
| `/brush layer` | partly | `/wc brush paint style=…` |
| `/brush cliff` | partly | `/wc brush flatten` |
| `/brush structure` | partly | `/wc brush clipboard` after `/wc structure load` |
| `//blob` | partly | `/wc brush rock` |
| `//history info` | partly | `/wc history` |
| `//lay` | partly | `/wc overlay` |
| `/brush pull` | not yet |  |
| `/brush circle` | not yet |  |
| `/brush recursive` | not yet |  |
| `/brush line` | not yet |  |
| `/brush spline` | not yet |  |
| `/brush sweep` | not yet |  |
| `/brush catenary` | not yet |  |
| `/brush surfacespline` | not yet |  |
| `/brush shatter` | not yet |  |
| `/brush stencil` | not yet |  |
| `/brush image` | not yet |  |
| `/brush populateschematic` | not yet |  |
| `/brush height` | not yet |  |
| `/brush copypaste` | not yet |  |
| `/brush savebrush` | not yet |  |
| `/brush /listbrush` | not yet |  |
| `//place` | not yet |  |
| `//gtexture` | not yet |  |
| `//gsmask` | not yet |  |
| `//gtransform` | not yet |  |
| `//registry` | not yet |  |
| `//history restore` | not yet |  |
| `//history rollback` | not yet |  |
| `//history import` | not yet |  |
| `//history find` | not yet |  |
| `//history distr` | not yet |  |
| `//nbtinfo` | not yet |  |
| `//wea` | not yet |  |
| `//wer` | not yet |  |
| `//fall` | not yet |  |
| `//fixblocks` | not yet |  |
| `/schem loadall` | not yet |  |
| `/schem move` | not yet |  |
| `/tool inspect` | not yet |  |
| `/tool featureplacer` | not yet |  |
| `/tool structureplacer` | not yet |  |
| `/primary` | not yet |  |
| `/secondary` | not yet |  |
| `/target` | not yet |  |
| `/targetoffset` | not yet |  |
| `/scroll` | not yet |  |
| `/smask` | not yet |  |
| `/transform` | not yet |  |
| `/worldedit debugpaste` | not yet |  |
| `/brush scattercommand` | no (on purpose) | runs commands for the player (a permission escalation) |
| `/brush command` | no (on purpose) | runs commands for the player (a permission escalation) |
| `/download` | no (on purpose) | uploads to an outside service |
| `//test` | no (on purpose) | FAWE's own debugging |
| `//fixlighting` | no (on purpose) | needs server internals (NMS); Paper fixes the light itself |
| `//removelighting` | no (on purpose) | needs server internals (NMS); Paper fixes the light itself |
| `//setblocklight` | no (on purpose) | needs server internals (NMS); Paper fixes the light itself |
| `//setskylight` | no (on purpose) | needs server internals (NMS); Paper fixes the light itself |
| `//macro` | no (on purpose) | runs commands for the player |
| `//heightmapinterface` | no (on purpose) | a web page served by the plugin |
| `//lazycopy` | not needed | every copy is already read in batches |
| `//tips` | not needed | every error says how to fix it |
| `/worldedit threads` | not needed | nothing to tune |

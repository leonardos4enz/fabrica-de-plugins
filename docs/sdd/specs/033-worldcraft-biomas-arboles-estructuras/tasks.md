# Tasks 033 — Biomas, árboles y estructuras del juego

- [x] T1 Pruebas unitarias: `BiomeTest` (celdas, motor, diario, portapapeles), `BiomeMaskTest`, `SchemBiomesTest`,
      `NatureTest` (lugares, flora, calabazas, árboles juntos, nombres, plantillas), `ToolSpecTest` (tree, forest),
      `ConfigTest` (sección `nature`), `CommandTest` (ayuda y densidad).
- [x] T2 Paquete `biome`: `BiomeWorld`, `BiomeSink`, `Cells`, `BiomeStage` (Apply, Read, Undo); `EntityStage.chain`;
      registro `BIOME` en `Journal`/`JournalSink`.
- [x] T3 `Edits`: `Op.biomes`, etapa en `run`, `undo`/`redo`, lectura con etapa extra; `compile(mask, mundo)`;
      `PaperBiomes`; mensajes.
- [x] T4 Máscara `$bioma` (`Mask.Near.Biome`, `Parser`, `BlockRegistry#biomes`, aviso de preset en `validate`).
- [x] T5 Biomas en `Clipboard` (`copy|cut|paste --biomes`) y en los `.schem` (`Schematic`, `SchemFormat` v2/v3,
      `SchemConvert`, `Schematics`).
- [x] T6 `command/NatureCommands`: `biomelist`, `biomeinfo`, `setbiome`; `forest`, `forestgen`, `flora`, `pumpkins`;
      `structure list|load`; autocompletado, ayuda de cada subcomando y mensajes.
- [x] T7 Paquete `nature` (`TreeKind`, `NatureMath`) y `paper/Trees` (`Planter`); `warmUp` con un árbol.
- [x] T8 `tool tree` y `brush forest` (`ToolKind`, `ToolParser`, `ToolSpec`, `ToolActions`).
- [x] T9 `paper/Structures` (lista del jar y del `StructureManager`, carga al portapapeles, botín `L` en `PaperWorld`).
- [x] T10 Configuración `nature` (`NatureSettings`, `LoadedConfig`, `validate`, `config.yml` comentado), versión 0.9.0.
- [x] T11 `selftest` (compuerta 2): un árbol capturado sin cambiar el mundo, biomas del registro, `igloo/top`.
- [x] T12 Suite 3a (`tests-e2e/worldcraft/`): `setbiome` + `biomeinfo` + `undo`/`redo` + `$desert` en `replace`;
      `copy|paste --biomes` y `schem save|load` con biomas (WorldEdit 7.3.19 los lee con `//paste -b`); `forest` con
      vista previa y `undo`, `flora`, `pumpkins`, `forestgen`; `tool tree` y `brush forest`; `structure list|load` +
      `paste` + `undo`; errores con sugerencia.
- [x] T13 Compuertas 1, 2 y 3a (dos veces), README del plugin y raíz, tabla de paridad, roadmap, `verify.md`, commit y push.

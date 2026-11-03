# Tasks 041 — Lo que le falta de FAWE 2.16.2

- [x] T1 Pruebas unitarias primero (LineMath, BrushMath041, Transforms, SourceMask, ToolParser041, SlashTable,
      Presentacion); ver que fallan sin el código nuevo.
- [x] T2 Cálculo puro: `LineMath`, `BrushMath` (pull, shatter, stencil, fall, cone, surface), `Transforms`,
      `TransformSource`, `SourceMaskedSource`.
- [x] T3 Pinceles y herramientas nuevos en `ToolKind`/`ToolParser`/`ToolActions`; puntos por clic, clic izquierdo,
      `target`/`toffset`/`smask`/`transform`/`secondary`/`scroll` en el ítem; `savebrush`/`listbrush`/`loadbrush`.
- [x] T4 `FaweCommands` (gtexture, gsmask, gtransform, smask, transform, target, targetoffset, scroll, primary, secondary,
      fall, fixblocks, nbtinfo, wea, wer, registry, fixlighting, debugpaste), `place`, `schem loadall|move`; `Session`,
      `Edits.request`, `Parser.texture`; evento `WorldcraftAllowedRegionEvent`; ayuda y `slash-commands.yml`.
- [x] T5 `Nms.relight` y ADR 0009 ampliado; `selftest`; 0.16.0; suite 3a con escenarios nuevos.
- [x] T6 Compuertas 1, 2 y 3a (dos veces); README del plugin y raíz, `commands.md`, descripciones y póster, roadmap,
      `verify.md`; commit y push.

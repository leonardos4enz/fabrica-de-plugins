# Tareas 029

## Especificación
- [x] T0 Tabla verificada contra los jars (bytecode, código fuente y servidor real) en `docs/investigacion/`; FAWE fijado
      con sha256; `spec.md` con la partición; CLARIFY respondido por el dueño; `plan.md`; roadmap.

## Pruebas primero (sin Bukkit)
- [x] T1 `MaskNearTest`: gramática nueva y errores; `above`, `below`, `exposed`, `surface`, `solid`, `% chance`, `^[estado]`
      sobre una foto; `Parser.mask` rechaza lo que mira vecinos (H2).
- [x] T2 En `MaskNearTest` (las `H3_*`): `^tipo` conserva lo compatible, `^[estado]`, mezcla con porcentajes, errores (H3).
- [x] T3 `SelectionTest`: `Box.outset/inset/center`, chunk; `Census` (count, distr por material y por estado, trim) (H1, H6).
- [x] T4 `LiquidsTest`: `fixwater`, `fixlava`, `drain` sobre la foto (H6).
- [x] T5 `HistoryStore.clear`; importar esquemas sin pisar (H1 CA6, H5).

## Implementación
- [x] T6 `Mask` y `Parser` (máscaras con vecinos), `BlockRegistry.solid`, `RegionSource` con vecinos.
- [x] T7 `Pattern` con cambios; las fuentes pasan el estado actual.
- [x] T8 `SelectionCommands` (hpos, chunk, outset, inset, trim, count, distr, clearclipboard, clearhistory), `paste`
      y `copy`/`cut` con sus opciones, `gmask` en `Edits.request`; ayuda, autocompletado y mensajes.
- [x] T9 `schem import` y `worldcraft.schematic.save`; `plugin.yml`; 0.5.0.
- [x] T10 `Box.center` y `Liquids` fuera de `BuildCommands`.

## Cierre
- [x] T11 Compuerta 1 (`./gradlew :worldcraft:test`) y compuerta 2 en `plugins/worldcraft/run/`.
- [x] T12 Compuerta 3a: escenarios nuevos en `tests-e2e/worldcraft/suite.mjs`, dos veces.
- [x] T13 README (Worldcraft y raíz), roadmap, `tasks.md` de la 028 corregido, `verify.md`, commit y push.

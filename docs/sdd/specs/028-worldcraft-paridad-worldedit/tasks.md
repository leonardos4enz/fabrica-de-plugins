# Tareas 028

## Especificación
- [x] T0 Investigación fila por fila (`docs/investigacion/paridad-worldedit-fawe.md`) con el código de WorldEdit 7.3.19 y
      FAWE 2.16.2; `spec.md` con el núcleo; CLARIFY respondido por el dueño (A + B + C + E, solo bajo `/wc`, permiso de
      navegación aparte); `plan.md`; roadmap.

## Pruebas primero (sin Bukkit)
- [ ] T1 `SolidTest`: esfera, elipsoide, cilindro, pirámide y cono llenos (cantidad por capa contra la fórmula, simetría,
      radio 0 y 1); huecas sin agujeros vistas desde fuera (inundación desde afuera no entra), grosor 1 y 2, cilindro hueco
      sin tapas, altura negativa; volumen estimado (H1 CA1–CA4).
- [ ] T2 `OpsTest` en el mundo en memoria: `stack` (n copias, sin aire, `--air`), `move` (dos cajas lejanas, sin visitar lo
      de en medio, relleno), `hollow` (cáscara de 1 y de 2, lo abierto no se vacía), `line` (diagonal sin huecos), `center`
      (1, 2 u 8 bloques), `naturalize` (capas), `overlay` (sobre el suelo, no en un tronco) (H2).
- [ ] T3 `UtilityTest` en el mundo en memoria: `drain` (un lago, solo lo conectado, `--waterlogged`), `fixwater`,
      `fill` (pozo, no sube de los pies), `thaw`, `green`, `snow` (H3).
- [ ] T4 `NavigationTest` (lógica pura): buscar lugar libre arriba/abajo, atravesar, salir de dentro (H4).
- [ ] T5 `ConfigTest`/`CommandTest`: secciones `shapes` y `utilities` con rangos; ayuda y mensajes de cada comando.

## Implementación
- [ ] T6 `region.Solid`, `edit.SolidSource`, `edit.PointsSource`, `edit.StackSource`, `edit.MoveSource`,
      `EditSource.chunks()`, `Edits.read`.
- [ ] T7 Formas (`ShapeCommands`), región (`RegionCommands`), utilidades (`UtilityCommands`), navegación
      (`paper.Navigation`, `NavigationCommands`); despacho, ayuda y autocompletado en `WorldcraftCommand`.
- [ ] T8 Recursos: `shapes`, `utilities` en `config.yml`, mensajes, `plugin.yml` (`worldcraft.navigate`); 0.4.0.

## Cierre
- [ ] T9 Compuerta 2 en `plugins/worldcraft/run/` (Paper 1.21.4) desde la consola.
- [ ] T10 Suite en el juego (`tests-e2e/worldcraft/suite.mjs`): escenarios nuevos con el bot (forma con vista previa,
      `stack` y `undo`, `drain`, navegación); dos veces.
- [ ] T11 README (sección «Coming from WorldEdit» con la equivalencia), README raíz, roadmap, `verify.md`, commit y push.

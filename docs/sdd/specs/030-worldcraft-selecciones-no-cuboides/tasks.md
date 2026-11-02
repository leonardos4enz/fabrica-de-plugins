# Tareas 030

## Especificación
- [x] T0 `spec.md` con el costo por pieza; CLARIFY respondido por el dueño; `plan.md`.

## Pruebas primero (sin Bukkit)
- [x] T1 `ShapesTest` (`RegionTest` ya era de la 025): cada forma (contiene, volumen, cáscara), caja igual que antes, `ChunkOrder` sobre cada forma.
- [x] T2 `HullTest`: envolvente convexa sin casos degenerados.
- [x] T3 `SelectorTest`: clics de cada forma, `shift`, `outset`, el alto, tope de vértices.
- [x] T4 La mágica (en `ShapeOpsTest`) y `OutlineTest`.
- [x] T5 `ShapeOpsTest`, fuentes sobre una forma: `copy` (−1 afuera), `stack`, `move`, `Census`.

## Implementación
- [x] T6 `Region`, `Columns`, `Polygon`, `Ellipsoid`, `Cylinder`, `Convex`/`Hull`, `PointSet`, `Magic`; `ChunkOrder` sobre una región.
- [x] T7 `Selector` en la sesión; `pos`/`hpos`/varita; `sel <forma>` e información.
- [x] T8 Comandos sobre cualquier forma (`set`…`smooth`); `shift`/`expand`/`outset` por forma.
- [x] T9 La mágica (lectura e inundación en el hilo de cálculo); contorno de cada forma.
- [x] T10 `config.yml` (`max_points`, `magic_radius`), mensajes, ayuda, autocompletado; 0.6.0.

## Cierre
- [x] T11 Compuerta 1 (`./gradlew :worldcraft:test`) y compuerta 2 en `plugins/worldcraft/run/`.
- [x] T12 Compuerta 3a: escenarios nuevos en `tests-e2e/worldcraft/suite.mjs`, dos veces.
- [x] T13 README (Worldcraft y raíz), tabla de paridad, roadmap, `verify.md`, commit y push.

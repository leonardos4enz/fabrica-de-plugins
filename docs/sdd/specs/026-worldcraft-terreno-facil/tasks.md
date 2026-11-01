# Tareas 026

## Investigación y especificación
- [x] T0 Informe [terreno para constructores](../../../investigacion/terreno-para-constructores.md) con fuentes; `spec.md` con
      dos niveles, el núcleo y el resto al roadmap; CLARIFY como propuesta pendiente del dueño; `plan.md`.

## Pruebas primero (sin Bukkit)
- [x] T1 `ShapeTest` (ruido): determinista por semilla, rango, distintas semillas dan distinto, continuidad.
- [x] T2 `ShapeTest` (montaña): cima = `height` en el centro, cae a 0 en el borde, picos, cráter, misma semilla = mismo plan,
      solo agrega, respeta obstáculos (H1 CA2, CA3, H4 CA1, CA2).
- [x] T3 `ShapeTest` (isla): cima casi plana, base más honda en el centro, nada fuera del radio, estalactitas, raíces, árboles con
      hojas persistentes, solo en aire (H2 CA2, CA3).
- [x] T4 `TerrainEditTest` con el mundo en memoria: leer el suelo por tandas, montaña aplicada y deshecha exacta, piel de la
      vista previa ⊂ obra y suficiente, pincel subir/bajar conserva materiales, suavizar no toca árboles ni casas, aplanar,
      erosión, pintar por pendiente (H1 CA4–CA5, H3 CA3–CA4, H5 CA1).
- [x] T5 `TerrainConfigTest` y `ConfigTest`: `terrain.yml` de fábrica sin avisos; reglas, rangos y claves inválidas con sugerencia; un
      estilo malo se omite; ajustes del pincel ida y vuelta (H4 CA3, CA5, H6 CA1, CA2).
- [x] T6 `TerrainConfigTest` y `CommandTest`: opciones `clave=valor` con «did you mean», `at=~,~5,~`; ayuda y mensajes de los subcomandos nuevos.

## Implementación
- [x] T7 `terrain` (núcleo): `Noise`, `HeightField`, `ColumnScan`, `Style`/`Painter`, `MountainShape`, `IslandShape`,
      `Brush`/`BrushSettings`, `ColumnSource`, `TerrainConfig`.
- [x] T8 Motor: `Edits.Op` con `preview`, `note`, `rerollable`; `Edits.scan`; receta en `Session`.
- [x] T9 `paper.Terrain` y `paper.BrushTool`; subcomandos `mountain`, `island`, `reroll`, `brush`, `styles`, ayuda y
      autocompletado.
- [x] T10 Recursos: `terrain.yml`, sección `terrain` de `config.yml`, mensajes con botones; versión 0.2.0.
- [x] T11 Vista previa sin servidor (`./gradlew :worldcraft:terrainPreview`): PNG de montañas e islas para ajustar la forma.

## Cierre
- [x] T12 Compuerta 2 en `plugins/worldcraft/run/` (Paper 1.21.4): `validate`, montaña e isla desde la consola, `undo`,
      tiempos por tick.
- [x] T13 Suite en el juego: escenarios nuevos en `tests-e2e/worldcraft/suite.mjs`, dos veces seguidas en 1.21.4.
- [x] T14 `verify.md`, README del plugin y raíz, roadmap e índice, CLAUDE.md, commit y push (sin publicar).

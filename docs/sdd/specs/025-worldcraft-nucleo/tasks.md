# Tareas 025

## Investigación y especificación
- [x] T0 Informe [editores de mundo](../../../investigacion/editores-de-mundo.md) con fuentes; `spec.md` con el núcleo
      y el resto al roadmap (lección 004); decisiones como propuestas pendientes del dueño.

## Pruebas primero (núcleo sin Bukkit)
- [x] T1 `RegionTest`: expandir, achicar y mover con palabras, hacia dónde mira, recorrido por chunks sin repetir ni
      volver a un chunk, cuentas de formas (H1 CA4, H5 CA3).
- [x] T2 `ParserTest`: patrones (estado, pesos, proporción, semilla), máscaras (comodín, etiqueta, `!`/`not`, estado
      parcial, aire), errores con sugerencia (F-02), presets (H2 CA1–CA5).
- [x] T3 `EditTest` con un mundo en memoria: set, replace, walls, faces, chunks lentos, vista previa = obra, tope de la
      vista previa, deshacer/rehacer exacto, deshacer seguro, cofres, cancelar, copiar/cortar/pegar sin aire, girar y
      voltear con estado, plazo por bloque (F-01) (H3, H4 CA2–CA3, H5 CA1, CA3, CA4, H6 CA1, CA3, CA4).
- [x] T4 `SchedulerTest`: tope de tiempo, turnos entre jugadores, obras que se cruzan, orden por jugador, error,
      cancelar y apagar (H5 CA1, CA2, CA6).
- [x] T5 `HistoryTest`: diario ida y vuelta con datos de bloque, cola cortada, deshacer/rehacer/descartar, reinicio con
      obra a medias, limpieza por cantidad y días (H6 CA1, CA2, CA5, CA7).
- [x] T6 `ConfigTest`: archivos de fábrica sin avisos, valores y claves inválidas con sugerencia, archivo roto conserva
      lo anterior, grupos de límites (H7 CA1, CA3, H8 CA2, CA3).
- [x] T7 `CommandTest`: opciones en cualquier lugar, `replace … with …`, coordenadas `~`, ayuda y mensajes completos
      (H2 CA1, H3 CA1, H1 CA5, H8 CA5).

## Implementación
- [x] T8 `region`, `block`, `pattern`, `edit`, `history`, `config` (núcleo).
- [x] T9 `paper`: `PaperBlocks`, `PaperWorld` (chunks asíncronos con ticket, contenedores y carteles), `PreviewSink`,
      `Edits` (límites, confirmación, vista previa, historial, barra de acción, vencimiento), `Wand` (varita marcada y
      contorno), `SelfTest`.
- [x] T10 `command`: `/bwr` con 27 subcomandos, ayuda con ejemplos, autocompletado de bloques, etiquetas y presets.
- [x] T11 Recursos en inglés: `config.yml`, `presets.yml`, `messages.yml`, `plugin.yml` (permisos `default: op`).

## Cierre
- [x] T12 Compuerta 2 en Paper 1.21.4 (servidor propio `plugins/blockwright/run/`): `validate`, `selftest`, obra de
      100 000 bloques, `jobs`, `history`, `limits`, `reload` ×2, reinicio con historial y `undo` tras el reinicio.
- [x] T13 Suite en el juego `tests-e2e/blockwright/suite.mjs` (3a): 8 escenarios, 8/8 dos veces en 1.21.4 (F-03).
- [x] T14 `verify.md`, README del plugin y raíz, roadmap e índice, CLAUDE.md (lecciones), commit y push.

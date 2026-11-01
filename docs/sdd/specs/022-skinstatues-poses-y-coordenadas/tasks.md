# Tareas 022

## Pruebas primero (núcleo sin Bukkit)
- [x] T1 `PoseLoaderTest` (en `ConfigTest`): `extends` ángulo por ángulo, padre declarado después, padre incorporado,
      ciclo, profundidad, padre con errores, padre inexistente con «did you mean», `mirror` sin `extends` (H2 CA1–CA3);
      incorporadas + propias, reemplazo y el `poses.yml` de la 0.1.0 sin reemplazos (H1 CA2, CA3).
- [x] T2 `VoxelizerTest`: espejo bloque por bloque con skin de un color (H2 CA2); cáscara cerrada, nada bajo los pies y
      codo sin grietas en las 28 poses de `builtin-poses.yml` (H1 CA5).
- [x] T3 `BuildArgsTest`: `--at` absoluto, relativo y con decimales, faltantes y texto, `--facing`, `--world`, flags
      repetidos o desconocidos con sugerencia, pose en cualquier lugar (H3 CA1, CA2, CA5).

## Implementación
- [x] T4 `PoseLoader` en dos etapas (`RawPose`, resolver con memo, ciclos y profundidad), `Pose.mirrored()`,
      `LoadedConfig` con incorporadas y `poseOrigins`.
- [x] T5 `builtin-poses.yml` con las 28 poses (22 nuevas), ajustadas con la vista previa (`-Ponly`, `-Pgrid`); `poses.yml` nuevo
      con la guía y dos ejemplos; `SkinStatues` escribe la copia de referencia.
- [x] T6 `BuildArgs` y `StatueCommand.build` con jugador o consola; `Statues.build(sender, Target, Request)`;
      `undo` desde la consola; `/statue poses` con orígenes; autocompletado (H3 CA7); mensajes nuevos.
- [x] T7 `ChunkHold` en construir y quitar (H3 CA6).
- [x] T8 Versión 0.2.0.

## Cierre
- [x] T9 `gradlew build` verde; compuerta 2 (`statue validate`, `selftest`, `statue build … --at … --world world`
      desde la consola, `reload` ×2); suite 3a con escenarios nuevos (coordenadas, consola lejos de todo, `extends`/
      `mirror`, poses nuevas) en 1.21.4 y 26.1.2; 3b con la vista previa; `verify.md`.
- [x] T10 README del plugin (poses con imagen, guía, coordenadas, Compatibilidad) y raíz; lluvia de ideas en el
      roadmap; índice; CLAUDE.md si hay lecciones; commit y push.

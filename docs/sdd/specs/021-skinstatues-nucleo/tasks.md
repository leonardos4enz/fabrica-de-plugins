# Tareas 021

## Pruebas primero (núcleo sin Bukkit)
Las pruebas de T3 y T4 quedaron en `ConfigTest`, las de T5 en `StoreTest`, y `ResourcesTest` comprueba que la skin de
ejemplo y `block-colors.yml` están al día con sus generadores.
- [x] T1 `SkinTest`: 64×64 y 64×32 (conversión con espejo y truco de Notch), tamaños inválidos, capa (H2 CA1, CA4).
- [x] T2 `VoxelizerTest`: escala y alto (H1 CA1, CA3), cáscara hueca y cerrada en todas las poses de ejemplo (H1 CA4,
      H4 CA2), segunda capa pegada y sin flotar, transparente, umbral y pintada a escala chica (H3 CA1–CA3), prioridad
      sobre el brazo, capa en la espalda e inclinada (H5), nada bajo los pies (H4 CA6), orientación (H1 CA2).
- [x] T3 `PaletteTest`: Lab y el más parecido, comodines, exclusiones, ids desconocidos con sugerencia, paleta vacía (H6).
- [x] T4 `PoseLoaderTest` y `SettingsTest`: claves desconocidas con «did you mean», rangos, `standing` siempre (H4 CA1,
      CA4; H9 CA2).
- [x] T5 `JournalTest` y `StatueStoreTest`: ida y vuelta, cola truncada, índice atómico (H8 CA1, CA4).
- [x] T6 `MojangClientTest`: JSON de perfil y de texturas, host no permitido, nombre inválido, sin red (H2 CA2–CA5;
      con respuestas falsas, sin red real).

## Implementación
- [x] T7 `skin`: `Skin`, `SkinPng`, `SkinRepository`, `MojangClient`.
- [x] T8 `model`: `PlayerModel`, `Pose`, `Voxelizer` (inglete, cáscara, capas, capa, prioridades).
- [x] T9 `color` y `config`: `Lab`, `BlockPalette`, `BlockCatalog`, cargadores con `Issues` («did you mean»), `Messages`.
- [x] T10 Generador de `block-colors.yml` (`./gradlew :skinstatues:blockColors -Pmc=…`) y skin de ejemplo original
      (`ExampleSkinMain`); los dos resultados en `src/main/resources/`.
- [x] T11 `build`: `StatueStore`, `Journal`, `TickWorker`, trabajos de construir y quitar, cancelar.
- [x] T12 `command` + `plugin.yml` (permisos `default: op`), `reload`, `validate`, `selftest`; `config.yml`,
      `poses.yml` (6 poses), `palettes.yml`, `messages.yml` en inglés.

## Cierre
- [x] T13 `gradlew build` en verde; compuerta 2 (`statue validate`, `selftest`, `reload` ×2, reinicio con datos);
      suite en el juego `tests-e2e/skinstatues/suite.mjs` (3a) en 1.21.4 y 26.1.2; exploratoria (3b); `verify.md`.
- [x] T14 README del plugin (en inglés, capacidades y Compatibilidad) y raíz; roadmap e índice de specs; CLAUDE.md
      (lecciones); commit y push.

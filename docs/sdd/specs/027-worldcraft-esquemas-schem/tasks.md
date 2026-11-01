# Tareas 027

## Especificación
- [x] T0 `spec.md` con el núcleo y el resto al roadmap (lección 004); CLARIFY como propuesta pendiente del dueño; `plan.md`
      desde la especificación pública de Sponge (v2, v3), sin código de WorldEdit.

## Pruebas primero (sin Bukkit)
- [x] T1 `NbtTest`: ida y vuelta de todos los tipos; archivo cortado; largo mentiroso; profundidad; bomba de GZip; NBT sin
      comprimir (H5 CA2, CA3).
- [x] T2 `SchemFormatTest`: v3 escrito y leído igual; v2 con `WEOffset` y v1 con `TileEntities` armados según la
      especificación; paleta de más de 127 (`varint` de 2 bytes); cabecera enorme rechazada antes de reservar; datos que
      faltan; versión desconocida (H2 CA2, H5 CA2, CA3).
- [x] T3 `SchemConvertTest` con el registro de prueba: portapapeles → esquema → portapapeles igual (también girado);
      bloques desconocidos a aire y contados; nombres cambiados; propiedad que no existe; datos de bloque por tipo;
      pegar un esquema cargado en el mundo en memoria pone cada bloque en su lugar (H1 CA1–CA3, H2 CA1, CA3–CA5).
- [x] T4 `SchemFilesTest`: nombres seguros y peligrosos, enlace que sale, carpeta propia antes que la de WorldEdit,
      listar con subcarpetas, no pisar sin `--overwrite`, papelera y su limpieza, carpetas de solo lectura (H3, H4, H5 CA1).
- [x] T5 `TaskJobTest` y `TextNbtTest`: pasos dentro del plazo; JSON ↔ NBT de un texto con colores y estilos.
- [x] T6 `ConfigTest`/`CommandTest`: sección `schematics` con rangos; ayuda y mensajes de `schem`.

## Implementación
- [x] T7 `schem`: `Nbt`, `VarInts`, `Schematic`, `SchemFormat`, `TileData`, `TextNbt`, `SchemBlocks`, `SchemConvert`,
      `SchemFiles`; `Clipboard.of`/`baked`; `edit.TaskJob`.
- [x] T8 `paper`: `TileNbt`, `'N'` en `PaperWorld.restoreTile`, `Schematics` (hilo propio), `Edits.submitTask`.
- [x] T9 `/wc schem list|load|save|delete` (alias `schematic`), permisos, ayuda, autocompletado de nombres.
- [x] T10 Recursos: sección `schematics` en `config.yml`, mensajes, `plugin.yml`; versión 0.3.0.

## Cierre
- [x] T11 Compuerta 2 en `plugins/worldcraft/run/` (Paper 1.21.4): `validate`, guardar y cargar desde la consola, un archivo
      roto, `list`, `delete`.
- [x] T12 Suite en el juego (3a) con WorldEdit 7.3.19 en el servidor de prueba: un esquema de WorldEdit (v3 y v2) se
      carga y se pega en Worldcraft con su cofre y su cartel; uno de Worldcraft lo carga y pega WorldEdit; límites y
      nombres peligrosos. Dos veces seguidas.
- [x] T13 `verify.md`, README del plugin y raíz, roadmap e índice, lecciones en `CLAUDE.md`, commit y push.

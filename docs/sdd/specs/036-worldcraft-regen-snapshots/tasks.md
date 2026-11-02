# Tasks 036 — `regen` y copias de seguridad

- [x] T1 Pruebas unitarias primero: `SnapshotTest` (con un escritor de `.mca` de prueba; también la semilla y las carpetas temporales), `ConfigTest`
      (dos secciones), `CommandTest`.
- [x] T2 Paquete `snapshot`: `RegionFile` (carpeta y `.zip`, compresiones, `.mcc`, topes), `ChunkNbt` (paleta empaquetada,
      biomas, datos de bloque, `Status`, `DataVersion`), `SnapshotRepo` (listar, fechas, `before`/`after`), `Restore`
      (portapapeles crudo con paleta global).
- [x] T3 `paper/Regen`: mundo temporal con marca, reutilizado `keep_seconds`, uno a la vez, limpieza al arrancar y al
      apagar; `Edits.readClip`; `PaperWorld.tile` guarda la tabla de botín de un cofre sin abrir.
- [x] T4 `command/RestoreCommands`: `regen`, `snapshot|snap list|use|sel|before|after`, `restore`; ayuda,
      autocompletado, mensajes, permisos `worldcraft.regen` y `worldcraft.snapshot`.
- [x] T5 Configuración (`RestoreSettings`: `regen`, `snapshots`), `validate`, `config.yml`, versión 0.12.0.
- [x] T6 `selftest` (compuerta 2): el `.mca` del *spawn* contra el mundo; un mundo temporal plano.
- [x] T7 Suite 3a: `regen` (aire, biomas, suelo), `restore` desde carpeta y `.zip`, `snapshot …`, sin permiso.
- [x] T8 Compuertas 1, 2 y 3a (dos veces), README del plugin y raíz, tabla de paridad, roadmap, `verify.md`, commit y push.

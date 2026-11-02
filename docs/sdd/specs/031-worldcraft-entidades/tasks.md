# Tasks 031 — Entidades en Worldcraft

- [x] T1 Pruebas unitarias: `EntityTest` (girar y voltear, `butcher`, `remove`), `EntityEditTest` (motor con etapas,
      diario, deshacer/rehacer, tope, chunks lentos, portapapeles), `SchemEntitiesTest`, `ConfigTest` (entidades).
- [x] T2 Paquete `entity`: `EntityCopy`, `Kind`, `EntityInfo`, `Butcher`, `RemoveType`, `EntityWorld`, `EntityStage`
      (`Scan`, `Spawn`, `Kill`), `EntitySink`.
- [x] T3 `Journal` (ENTITY_OUT, ENTITY_IN, REMAP, `reopen`) y `JournalSink` como `EntitySink`.
- [x] T4 `EditJob` con `before`/`after`; `Edits.Op` con `EntityWork`; revisión de `max_entities` y `blocked_entities`.
- [x] T5 `Clipboard` con entidades (girar, voltear, `baked`); `copy`/`cut --entities`, `paste --no-entities
      --remove-entities`, `stack`/`move --entities`.
- [x] T6 Deshacer/rehacer con entidades (`UndoSource` + etapas, `REMAP`).
- [x] T7 `/wc butcher` y `/wc remove`, permisos, autocompletado, ayuda, mensajes.
- [x] T8 `.schem`: leer y escribir `Entities` (v2/v3); notas al cargar.
- [x] T9 `PaperEntities` (Paper) y configuración (`entities.*`, `max_entities`, `blocked_entities`, `validate`).
- [x] T10 `selftest` (compuerta 2) con soporte y marco.
- [x] T11 Suite 3a (`tests-e2e/worldcraft/`): copiar y pegar un soporte con armadura y un marco con ítem (girado),
      `cut` + `undo`, `move --entities`, `stack --entities`, `butcher` (salva al lobo domesticado y al aldeano; `--animals`)
      + `undo`, `remove items`, `paste --remove-entities`, `.schem` con entidades ida y vuelta con WorldEdit 7.3.19.
- [x] T12 Compuertas 1, 2 y 3a (dos veces), README del plugin y raíz, tabla de paridad, roadmap, `verify.md`, commit y push.

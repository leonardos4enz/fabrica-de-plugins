# Tasks 037 — API para otros plugins

- [x] T1 Pruebas unitarias primero: `ApiTest` (resultado, dueño de un plugin, solapamiento, grupos, el paquete `api` sin
      tipos internos), `ConfigTest` (sección `api`), `CommandTest` (mensajes).
- [x] T2 Paquete `api` y `api.event`: `WorldcraftApi`, `Selection`, `EditBuilder`, `EditResult`, `WorldcraftEditEvent`,
      `WorldcraftEditDoneEvent`.
- [x] T3 `Edits`: revisiones separadas (`refusal`), evento antes de la vista previa y de `undo`, `submit` para la API,
      fin con `Consumer` y `WorldcraftEditDoneEvent`.
- [x] T4 `paper/Api` (implementación, registro en el `ServicesManager`), `Schematics.loadClipboard`, `PasteOp` extraído de
      `/wc paste`; configuración (`ApiSettings`: `api.enabled`, `api.plugin_groups`), `validate`, mensajes, versión 0.13.0.
- [x] T5 `selftest` (compuerta 2): servicio, obra hecha y deshecha, obra cancelada por un oyente.
- [x] T6 Plugin de ejemplo `plugins/worldcraft-api-example` (comandos, zonas protegidas, registro), su README; `dist`.
- [x] T7 Suite 3a: `jarsExtra` en `correr.mjs`, escenarios de la API y del evento.
- [x] T8 Compuertas 1, 2 y 3a (dos veces), README del plugin y raíz, tabla de paridad, roadmap, `verify.md`, commit y push.

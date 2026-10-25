# Tareas 009

- [x] T1 `DataStore`: caducidad en `Holder` (API `get`/`put`/`remove`/`expiresAt`/`live`), reloj inyectable,
      sección `expire`, `format: 2`, cola y `sweep`, respaldo único y formato más nuevo en solo lectura;
      `DataStoreTest`.
- [x] T2 Pasar los lectores de `vars()` a la API (`ExecContext`, `pvar_of`, `/omni var`, PlaceholderAPI).
- [x] T3 `ObjectData` (codificación, tipos, límites, caducidad al leer, bloque/entidad/ítem) y `ObjectDataTest`;
      opciones `object-data.*` en `Settings` y `config.yml`.
- [x] T4 Lenguaje: `Node.DataRef`, `ttl` perezosa con su validación, `EvalContext.objectVar`, `StateDeps` y
      `Expression.collect`; `DatosObjetoExprTest`.
- [x] T5 Acciones: `set` con `expire` y la forma `.data`, `unset` de `.data`, `Rule.writesObjectData`;
      `VariablesCargaTest` (también `StateDeps` con `.data` y `ttl`).
- [x] T6 `var_expire` (`Kind.VAR_EXPIRE`, opción `variables`), barrido cada segundo, aviso a `StateService`; casos
      de `/omni selftest` sobre el PDC real (ítem y bloque).
- [x] T7 `BlockDataCleaner` registrado solo si alguna regla escribe `.data`.
- [x] T8 Comandos: `/omni var` con caducidad y `--expire`; `/omni data` (permiso, mensajes, autocompletado).
- [x] T9 Ejemplo `rules/ejemplos/variables.yml`; esquema y referencia regenerados; versión 1.4.0.
- [x] T10 Escenarios e2e y reglas de la suite.
- [x] T11 Compuertas 1, 2 y 3a (dos veces, en 1.21.4 y 26.1.2).
- [x] T12 `verify.md`, README del plugin y raíz, roadmap, índice, commit y push.

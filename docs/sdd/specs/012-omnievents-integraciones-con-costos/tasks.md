# Tareas 012

- [x] T1 Dependencias `compileOnly` (VaultAPI, WorldGuard), `softdepend`, `hook/Economy`, `hook/Regions`, `hook/Npcs`
      con sus implementaciones y la detección al habilitar; línea de integraciones en `/omni stats`.
- [x] T2 `give_money`, `take_money`, `money()`, `money_format()`; saldo en el `Sandbox`.
- [x] T3 `rule/Cost.java` (cotizar, cobrar, devolver), `cost`/`on_cost_fail` en `RuleLoader` (validación con ruta) y en
      `RuleManager` (disparo, `check`, simulación) y `RuleTester` (`given.money`, `inventory`, rama `on_cost_fail`).
- [x] T4 `region_enter`/`region_leave` con `RegionService`, `in_region()`, `regions()`; `npc_click`,
      `npc_left_click`, `is_npc()`; avisos sin la integración.
- [x] T5 Registro (`RuleOptions` con `Kind.COST`, `Triggers`, `Actions.Doc`, `Functions`), esquema y referencia
      regenerados; ejemplos con `tests:`; versión 1.7.0.
- [x] T6 Unitarias H1–H5.
- [x] T7 Jars de prueba en la suite (sha256), escenarios e2e; compuertas 1, 2 y 3a en 1.21.4 y 26.1.2.
- [x] T8 `verify.md`, README del plugin y raíz, roadmap, índice, commit y push.

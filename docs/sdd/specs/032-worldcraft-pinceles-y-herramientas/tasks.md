# Tasks 032 — Pinceles de construcción y herramientas de un clic

- [x] T1 Pruebas unitarias: `ToolSpecTest`, `BrushMathTest`, `CyclerTest`, `ConfigTest` (sección `tools`).
- [x] T2 Paquete `tool`: `ToolKind`, `ToolSpec`, `ToolParser`, `BrushMath`, `Cycler`.
- [x] T3 Configuración: `ToolSettings` (`tools.*`), `LoadedConfig.tools()`, `validate`, `config.yml` comentado.
- [x] T4 `paper/Tools`: marcar el ítem de la mano (y dejarlo como era con `none`), clics con la mira propia, círculo,
      superpico (estado por jugador, pico, `never_break`).
- [x] T5 `paper/ToolActions`: los 10 pinceles y las 8 herramientas por el motor (límites, máscara global, deshacer).
- [x] T6 `command/ToolCommands`: `/wc brush <construcción>`, `/wc tool`, `/wc superpickaxe|sp`, permisos
      (`worldcraft.superpickaxe`), autocompletado, ayuda y mensajes; convivencia con el pincel de terreno.
- [x] T7 `selftest` (compuerta 2) con `Cycler` sobre el registro real.
- [x] T8 Suite 3a (`tests-e2e/worldcraft/`): pincel de esfera en la mano (otro igual no es pincel, `mask=`, `undo`,
      `none` lo deja como era, círculo solo de Ana, cilindro en una vara normal); `gravity`/`clipboard`/`extinguish`/`butcher`
      (`splatter`, `scatter` y `rock` en las unitarias: son al azar); herramientas `repl`,
      `cycler`, `info`, `farwand`, `floodfill`, `deltree`, `lrbuild`, `navwand`; superpico (`single` sin soltar y `undo`,
      `area`, roca madre salvada, Beto sin permiso).
- [x] T9 Compuertas 1, 2 y 3a (dos veces), README del plugin y raíz, tabla de paridad, roadmap, `verify.md`, commit y push.

# Tasks 039 — Los comandos de WorldEdit con dos barras

- [x] T1 Inventario del bytecode de WorldEdit 7.3.19 y FAWE 2.16.2 (nombres, alias, banderas) → `slash-commands.yml`.
- [x] T2 Pruebas unitarias primero: `SlashTableTest` (tabla, destinos, opciones, traducción, la lista de la 038) y
      `ConfigTest` (sección `slash_commands`); ver que fallan sin el código nuevo.
- [x] T3 `command/SlashTable` (leer la tabla, generar `//x`, traducir); `SlashSettings` en `config.yml`, `validate`.
- [x] T4 `paper/SlashCommands` (registrar, tomar y devolver los de WorldEdit, un `syncCommands`, el oyente de
      `PlayerCommandSendEvent`); `onEnable` (primer tick), `/wc reload`, `onDisable`; `selftest`; ayuda; 0.14.0.
- [x] T5 Suite 3a: escenarios que usaban WorldEdit con `worldedit:`; escenarios nuevos de `//`.
- [x] T6 Compuertas 1, 2 y 3a (dos veces), README del plugin y raíz, presentación, roadmap, `verify.md`, commit y push.

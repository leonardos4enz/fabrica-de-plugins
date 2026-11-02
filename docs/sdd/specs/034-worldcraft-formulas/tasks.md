# Tasks 034 — Fórmulas

- [x] T1 Pruebas unitarias primero: `FormulaTest`, `ShapesTest`, `FormulaMaskTest`, `MapSourceTest`, `ToolSpecTest`
      (deform), `ConfigTest` (`formulas`), `CommandTest`.
- [x] T2 Paquete `formula`: `Lexer`, `FormulaParser`, `Node`, `Functions`, `Noise3`, `Formula` (con `Run`),
      `FormulaException`, `Space`, `Shapes`.
- [x] T3 Máscara `=fórmula` (`Mask.Near.Formula`, `Parser`, `split` con paréntesis) y patrón `=fórmula -> …`
      (`Pattern.pick`).
- [x] T4 `edit/MapSource`; `paper/Formulas` (hilo propio, mensajes de los topes, `deform` compartido con el pincel).
- [x] T5 `command/FormulaCommands`: `generate`, `deform`, `generatebiome`, `calc`, `timeout`; `NatureCommands#biomeOp`;
      `Session.formulaTimeout`; ayuda, autocompletado, mensajes.
- [x] T6 `brush deform` (`ToolKind`, `ToolParser`, `ToolSpec` con la fórmula en Base64, `ToolCommands.Tokens`, `ToolActions`).
- [x] T7 Configuración `formulas` (`FormulaSettings`, `LoadedConfig`, `Settings`, `validate`, `config.yml`), versión 0.10.0.
- [x] T8 `selftest` (compuerta 2).
- [x] T9 Suite 3a (`tests-e2e/worldcraft/suite.mjs`): tres escenarios (generate + calc + timeout, con la cuenta de
      `//generate` de WorldEdit; máscara y patrón; deform + generatebiome + brush deform).
- [x] T10 Compuertas 1, 2 y 3a (dos veces), README del plugin y raíz, tabla de paridad, roadmap, `verify.md`, commit y push.

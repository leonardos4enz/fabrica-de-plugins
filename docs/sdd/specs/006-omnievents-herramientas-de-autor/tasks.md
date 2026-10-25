# Tasks 006

## P1 · Condiciones
- [x] T1 `Condition` (`Single`, `AnyOf`) con `test` (con `all` para evaluar todo sin cortar) y `source`; `Rule.conditions` como `List<Condition>`.
- [x] T2 `RuleLoader`: textos y mapas (`expr`, `any`, `of`, `deny`), validación y avisos (H1 CA4, H2 CA3).
- [x] T3 `RuleManager.run`: `deny` de la condición que falla, si no `else` (H1 CA1-2); `/omni info` y `actionCount`.
- [x] T4 Unitarias `H1_*`, `H2_*` (carga, errores, compatibilidad de la forma de texto, semántica de `any`).

## P2 · Revisión y simulación
- [x] T5 `RuleManager.check` (todo sin cortar, rama, sin efectos) y `/omni check` (H3).
- [x] T6 Simulación: `Trace.simulation`, `ActionNode.control/source`, `Execution.simulating`, `run` sin efectos (H4 CA1-3).
- [x] T7 `/omni run <regla> <jugador> [--simular]` (H4 CA4), registro en consola.
- [x] T8 Búfer de trazas y `/omni debug export` asíncrono y atómico (H5).
- [x] T9 Permisos, mensajes y autocompletado de `check`, `run`, `cpu`, `debug export`.

## P4 · Rendimiento
- [x] T10 `Profiler` (muestreo) integrado en `RuleManager.run` y `Execution` (H6 CA1-2).
- [x] T11 `budget` (carga y validación), `Budget` con calentamiento, promedio móvil y aviso limitado (H7).
- [x] T12 `/omni cpu [on|off|reset]` con reglas, partes, disparadores y presupuestos (H6 CA2-3, H7 CA3).
- [x] T13 `config.yml`: `profiler.*`, `debug.export-buffer` con valores por defecto.

## Ejemplos, pruebas y cierre
- [x] T14 Ejemplo `rules/ejemplos/herramientas.yml` (tienda con `deny` y `any`, regla con `budget`).
- [x] T15 Unitarias de `budget`, `Budget`, `Profiler`, simulación sin efectos.
- [x] T16 Escenarios e2e en `tests-e2e/omnievents/suite.mjs` con reglas en `reglas/e2e.yml`.
- [x] T17 Compuerta 1 y 2; e2e dos veces; README del plugin y raíz, roadmap, índice; `verify.md`; versión 1.1.0; commit y push.

# Tareas 001

## Pruebas primero
- [x] T1 Unitarias del lenguaje: lexer (números, textos, operadores de palabra, placeholders), precedencia (H2 CA3),
  operadores de texto y `in` (H2 CA1), aritmética y ternario (H2 CA2), nulos (H2 CA7), errores con columna (H2 CA5).
- [x] T2 Unitarias de propiedades y valores: mapas, listas, enums como texto (H3 CA3), reflexión solo lectura (H3 CA2).
- [x] T3 Unitarias de duraciones, plantillas (interpolación, escape, saneo de comandos: H4 CA5–CA6) y del importador.

## Motor
- [x] T4 `expr/`: Lexer, Parser, Node, Values, Named, Properties, Functions.
- [x] T5 `text/`: Template y TextFormat.
- [x] T6 `action/`: Action, ActionNode (modificadores), Execution (pila, delay, topes), control de flujo.
- [x] T7 Catálogo de acciones (H5).
- [x] T8 `trigger/`: catálogo de disparadores (H6 CA1), ListenerManager bajo demanda (H6 CA5), TimerService
  (`timer`, `clock`), `command` (CommandMap), `custom`.
- [x] T9 `rule/`: RuleLoader con validación y sugerencias (H1, H2 CA5, H3 CA4, H5 CA6), RuleManager.
- [x] T10 `data/`: DataStore, enfriamientos y límites (H7, H8).
- [x] T11 `debug/`: trazas y estadísticas (H9).
- [x] T12 `command/`: `/omni` con permisos y autocompletado (H11).
- [x] T13 `hook/`: PlaceholderAPI (H7 CA3).
- [x] T14 `importer/`: ConditionalEvents (H10).
- [x] T15 `api/` (H12), `plugin.yml`, `config.yml`, `messages.yml`, ejemplos (H1 CA4), README del plugin.

## Cierre
- [x] T16 `gradlew build` en verde (38/38) y servidor real aprobado (carga, validate, selftest, reload ×2, persistencia); revisión en el juego automatizada por la spec 002 (compuerta 3a, 21/21); `verify.md`.
- [x] T17 Roadmap, índice de specs, commit.

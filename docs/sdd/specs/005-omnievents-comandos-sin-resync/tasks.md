# Tareas 005

## Pruebas primero
- [x] T1 `tests-e2e/lib/bot.mjs` registra cada árbol de comandos que recibe el bot (`declare_commands` → tipo `comandos`).
- [x] T2 Escenario `005 H1 CA1 · H1 CA2 · H1 CA3 · H2 CA1 · H2 CA2` en `tests-e2e/omnievents/suite.mjs`: cuatro
      `enable`/`disable` seguidos de una regla común (0 árboles), tres de la regla comando `e2e` (un árbol por cambio,
      sin `e2e` mientras está desactivada, y `/e2e` funciona al final), sin `ConcurrentModificationException`.
- [x] T3 Correr el escenario con el código anterior y anotar que falla (y por qué) en `verify.md`.
- [x] T4 Unitarias de `RuleCommands.Spec.of` (misma sección → igual; alias, permiso o tab distintos → distinto).

## Implementación
- [x] T5 `RuleCommands`: `Spec`, `testPermissionSilent` oculto, `register()`/`refresh()`/`reconcile`, un solo envío.
- [x] T6 `OmniCommand.toggle` llama a `refresh()`; `OmniEvents.reload()` sin `unregister()` previo.
- [x] T7 Versión 1.0.1.

## Cierre
- [x] T8 `gradlew build` en verde; servidor real (`compuerta-servidor.sh`); `npm --prefix tests-e2e test` dos veces; `verify.md`.
- [x] T9 README del plugin (Compatibilidad, nota de `/omni disable`) y raíz; roadmap, índice, F-01 resuelto en la 004 y `CLAUDE.md`; commit y push.

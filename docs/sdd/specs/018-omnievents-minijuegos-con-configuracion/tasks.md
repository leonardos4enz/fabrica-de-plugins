# Tareas 018

## Pruebas primero
- [x] T1 `tests:` de cada regla del paquete (empezar, creativo, checkpoint una vez, meta con récords, límite, caída,
      reloj, muerte, modo, vuelo, atajo, desconexión, partida vieja, cada subcomando de `/parkour`).
- [x] T2 Escenario de la suite «H1–H4 · parkour de punta a punta» en `tests-e2e/omnievents/suite.mjs`.

## Implementación (solo configuración)
- [x] T3 `rules/ejemplos/parkour/pack.yml`: `enabled: false`, `requires`, parámetros.
- [x] T4 `rules/ejemplos/parkour/reglas.yml`: placas, caída, reloj, perder, salir del servidor, comando y lo común.
- [x] T5 `PaquetesTest` carga los ejemplos con la versión mínima que piden (1.9.0).

## Cierre
- [x] T6 `gradlew build` en verde; `:omnievents:dist`; compuerta 2; `npm --prefix tests-e2e test` dos veces en 1.21.4 y
      dos en 26.1.2; exploratoria (3b); `verify.md`.
- [x] T7 Guía «Cómo hacer un minijuego» en el README del plugin y `docs/site/minigames.md` (con `SUMMARY.md` y
      `mkdocs.yml`); README raíz, roadmap, índice de specs, lecciones en `CLAUDE.md`; commit y push.

# Tareas 019

## Pruebas primero
- [x] T1 `EjemplosEnInglesTest`: el paquete viene apagado, carga sin avisos sin integraciones, cada regla trae
      `tests:` y pasan; F-02 (falla con el código anterior: 3 avisos en vez de 1).
- [x] T2 `EjemplosDelJarTest` (F-01; falla con el código anterior: faltaban `estilo_ce.yml`, `parkour/` y `examples-en/`).
- [x] T3 Escenario de la suite «019» en `tests-e2e/omnievents/suite.mjs`.

## Implementación
- [x] T4 `rules/examples-en/`: `pack.yml` (apagado, parámetros) y `welcome.yml`, `daily.yml`, `zones.yml`,
      `common.yml`, `regions.yml`, cada regla con comentario y `tests:`.
- [x] T5 F-01: lista de ejemplos del jar completa. F-02: `RuleLoader` no avisa de integraciones en reglas apagadas.
- [x] T9 Pedido del dueño: `integrations.yml` (Vault, Citizens, PlaceholderAPI, Floodgate, ProtocolLib) con `tests:`, el
      escenario ampliado y `requires: >=1.11.0`.
- [x] T6 La compuerta 2 y la suite copian también `examples-en/`. Versión 1.12.0.

## Cierre
- [x] T7 `gradlew build`; `:omnievents:dist`; compuerta 2 (instalación nueva sin WorldGuard y la de siempre);
      `npm --prefix tests-e2e test` dos veces en 1.21.4 y dos en 26.1.2; `verify.md`.
- [x] T8 `docs/site/pages/omnievents/examples.md` (el paquete y un ejemplo de regiones), README del plugin
      (ejemplos en inglés, F-02, Compatibilidad), README raíz, roadmap, índice de specs, lecciones en `CLAUDE.md`;
      commit y push.

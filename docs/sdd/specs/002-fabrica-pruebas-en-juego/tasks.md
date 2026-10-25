# Tareas 002

## Pruebas primero
- [x] T1 Unitarias del arnés (`lib/servidor.test.mjs`): UUID sin cuenta igual al de Java, negativa sin EULA (H2 CA1),
  propiedades forzadas sin pisar las demás (H2 CA2), borrado solo de los datos de los bots (H1 CA3).

## Arnés
- [x] T2 `tests-e2e/package.json` con `mineflayer` ~4.39.0; `npm test` corre las unitarias y la suite.
- [x] T3 `lib/servidor.mjs`: propiedades seguras, olvidar jugadores (mundo de 1.21 y de 26.1), arrancar, consola con
  espera, `sincronizar`, errores del log, apagar.
- [x] T4 `lib/bot.mjs`: registro de chat, barra de acción (paquete propio de Paper), títulos, bossbar, sonidos por
  tono, partículas, explosiones, habilidades, entidades y expulsión; esperas con tiempo límite y `nunca`.
- [x] T5 `correr.mjs`: compilar, instalar jar y configuración, arrancar, escenarios en serie con revisión del log,
  limpieza en `finally`, reporte y código de salida; `--solo`, `--sin-build`.
- [x] T6 `lib/paper.mjs` y `--paper`/`--version-bot`/`--java-servidor` (H5).

## Suite de OmniEvents
- [x] T7 `omnievents/reglas/e2e.yml`: `/e2e <grupo>` → `call` a una regla por grupo de acciones.
- [x] T8 `omnievents/suite.mjs`: un escenario por criterio pendiente de la 001 (H4 CA1–CA11 de esta spec).
- [x] T9 Hallazgos convertidos en regresión: F-01 (`set pvar` tras un kick), F-02 (`remove_item` con nombre).

## Cierre
- [x] T10 Suite en verde dos veces seguidas en Paper 1.21.4 (y dos en 26.1.2); `gradlew build` y compuerta 2 en verde;
  `verify.md`.
- [x] T11 `verificacion.md` (compuerta 3a/3b), verify de la 001, roadmap, índice, `CLAUDE.md`, commit y push.

# Metodología: Spec-Driven Development (SDD) asistido por IA

La fábrica usa **SDD** (al estilo de GitHub Spec Kit) combinado con **pruebas primero en código crítico** y **ADRs**,
igual que el proyecto ERP AI. La IA trabaja contra un documento verificable y no contra una conversación que se
olvida. Cada plugin de la fábrica nace y crece por specs.

## Flujo por cada funcionalidad

```
constitution.md  (principios fijos de la fábrica, se leen siempre)
      │
 1. SPECIFY   specs/NNN-plugin-nombre/spec.md    QUÉ y POR QUÉ. Historias, criterios de aceptación.
      │                                           Sin tecnología.
 2. CLARIFY   sección "Decisiones (CLARIFY)" del spec, resueltas por el dueño.
      │
 3. PLAN      specs/NNN-plugin-nombre/plan.md    CÓMO. Arquitectura, formato de configuración (el contrato),
      │                                           modelo de datos, hilos, análisis de amenazas (STRIDE).
 4. TASKS     specs/NNN-plugin-nombre/tasks.md   Tareas pequeñas, ordenadas, cada una verificable.
      │
 5. IMPLEMENT la IA ejecuta tarea por tarea, marcando [x].
      │
 6. VERIFY    specs/NNN-plugin-nombre/verify.md  Ver verificacion.md:
                 a) unitarias  b) servidor Paper real (carga, ejemplos, comandos de consola)
                 c) en el juego: suite automática con bots (3a) y revisión exploratoria (3b)
                    → cada hallazgo se vuelve prueba
```

Las plantillas de cada documento están en [plantillas/](plantillas/). Un plugin nuevo se crea con
`tools/nuevo-plugin.ps1` (proyecto Gradle + carpeta de spec con las plantillas).

## Numeración
Las specs se numeran en una sola secuencia para toda la fábrica (`001`, `002`, …) y su nombre empieza por el plugin:
`001-omnievents-nucleo`, `002-omnievents-…`, `003-otroplugin-…`. Así el índice cuenta la historia completa.

## Una spec por sesión
Cada spec se trabaja en **su propia sesión de IA, nueva**, de principio a fin (SPECIFY → VERIFY → commit).
Al cerrar la spec:
1. `verify.md` completo y compuertas en verde.
2. Actualizar los README: el del plugin (capacidades y sección **Compatibilidad** con las versiones de Paper
   verificadas y sin verificar) y el raíz (tabla de plugins). Ver [CLAUDE.md](../../CLAUDE.md).
3. Actualizar el estado en el índice de abajo y en [roadmap.md](roadmap.md).
4. Revisión de secretos y artefactos, commit y push.
5. La siguiente spec se abre en una sesión nueva, que arranca leyendo [CLAUDE.md](../../CLAUDE.md).

## Reglas para agentes de IA
- Leer `constitution.md` y el `spec.md` vigente antes de escribir código.
- No implementar nada que no esté en `tasks.md`. Si falta algo, proponer cambio al spec.
- Si el spec es ambiguo, preguntar (fase CLARIFY) en vez de suponer.
- Cada tarea termina con `gradlew build` verde y pruebas pasando.
- Cada criterio de aceptación del spec tiene al menos una prueba (unitaria o de servidor) con su ID en el nombre
  (`H1_CA3_…`) o en la tabla de cobertura de `verify.md`.
- La revisión exploratoria se hace **usando el plugin en un servidor real**, no leyendo código.

## Índice de specs
| # | Spec | Plugin | Estado |
|---|------|--------|--------|
| 001 | [OmniEvents: núcleo (eventos condicionales)](specs/001-omnievents-nucleo/spec.md) | OmniEvents | Verificado · compuertas 1, 2 y 3a aprobadas |
| 002 | [Fábrica: pruebas automáticas en el juego](specs/002-fabrica-pruebas-en-juego/spec.md) | fábrica (OmniEvents) | Verificado · 21/21 en Paper 1.21.4 y 26.1.2 |
| 004 | [Fábrica: documentación con imágenes generadas en el juego](specs/004-fabrica-capturas-en-juego/spec.md) | fábrica (OmniEvents) | Verificado · galería de 147 escenas, dos regeneraciones sin diferencias inesperadas |
| 005 | [OmniEvents: activar y desactivar reglas sin re-sincronizar comandos](specs/005-omnievents-comandos-sin-resync/spec.md) | OmniEvents | Verificado · 1.0.1, F-01 de la 004 resuelto · 22/22 dos veces |
| 006 | [OmniEvents: herramientas de autor](specs/006-omnievents-herramientas-de-autor/spec.md) | OmniEvents | Verificado · 1.1.0: `deny` por condición, «N de M», `check`, `run --simular`, `debug export`, `cpu` y `budget` · 27/27 dos veces en 1.21.4 y 26.1.2 |
| 007 | [OmniEvents: esquema JSON y referencia generada](specs/007-omnievents-esquema-y-referencia/spec.md) | OmniEvents | Verificado · 1.2.0: `/omni schema`, `gradlew :omnievents:schema`, [referencia](../omnievents/referencia.md) · 27/27 dos veces en 1.21.4 y 26.1.2 |
| 008 | [OmniEvents: disparador de estado](specs/008-omnievents-disparador-de-estado/spec.md) | OmniEvents | Verificado · 1.3.0: `trigger: state` (`when`, `on_enter`, `on_exit`, `check_every`, `watch`, `initial`, `per_player: false`), `in_state`, `/omni state` · 34/34 dos veces en 1.21.4 y 26.1.2 |
| 009 | [OmniEvents: variables con caducidad y en bloques, entidades e ítems](specs/009-omnievents-variables-caducidad-y-objetos/spec.md) | OmniEvents | Verificado · 1.4.0: `expire`, `ttl()`, `var_expire`, `objeto.data.nombre` (PDC), `/omni data`, `data/` en formato 2 con respaldo · 37/37 dos veces en 1.21.4 y 26.1.2 |
| 010 | [OmniEvents: pruebas de reglas en el YAML](specs/010-omnievents-pruebas-de-reglas/spec.md) | OmniEvents | Verificado · 1.5.0: `tests:` (`given`, `expect`), `/omni test`, pruebas al cargar y en gradle · 38/38 dos veces en 1.21.4 y 26.1.2 |
| 011 | [OmniEvents: paquetes, plantillas y versión](specs/011-omnievents-paquetes-plantillas-version/spec.md) | OmniEvents | Verificado · 1.6.0: `pack.yml`, `paquete:regla`, `template`/`extends`/`with`/`params`, `requires`, `migrations`, `/omni pack` · 39/39 dos veces en 1.21.4 y 26.1.2 |
| 012 | [OmniEvents: integraciones con costos](specs/012-omnievents-integraciones-con-costos/spec.md) | OmniEvents | Verificado · 1.7.0: `cost` todo o nada con `on_cost_fail` y devolución, `give_money`/`take_money`/`money()` (Vault), `region_enter`/`region_leave`/`in_region()` (WorldGuard), `npc_click`/`npc_left_click`/`is_npc()` (Citizens) · 42/42 dos veces en 1.21.4 y 26.1.2 |
| 013 | [OmniEvents: menús de inventario y formularios de Bedrock](specs/013-omnievents-menus-y-formularios-bedrock/spec.md) | OmniEvents | Verificado · 1.8.1: `menu:` con `open_menu`/`close_menu`, `view`, clics como reglas (`cost`, `cooldown`, `tests`), formulario de botones para Bedrock (Floodgate), `is_bedrock()`, `/omni menu`; F-03 (enfriamientos de menús) y F-04 (`has_item`) corregidos · 45/45 dos veces en 1.21.4 y 26.1.2 |
| 014 | [OmniEvents: paridad con ConditionalEvents](specs/014-omnievents-paridad-conditionalevents/spec.md) | OmniEvents | Verificado · 1.9.0: comparación fila por fila con el jar de CE 4.65.1, `centered_message`, `firework`, `item_repair`, `cooldown_bypass` (`omnievents.bypasscooldown.<regla>`), `reset` con `*` e importador completo; F-01 (`%player%` dentro de `to_…`) corregido · 49/49 dos veces en 1.21.4 y 26.1.2 |
| 015 | [OmniEvents: paridad con ConditionalEvents, segunda parte](specs/015-omnievents-paridad-conditionalevents-2/spec.md) | OmniEvents | Verificado · 1.10.0: claves de ítems de CE (`slot`, `flags`, `skull_owner`…), `remove_item` con filtros, `remove_item_slot`, `summon` en mapa, `json_message`, `tab_complete`, `player_statistic`, `player_air_change`, `player_tab_complete`, `item_mend`, `pick`/`block_at`/`is_number`, `/omni interrupt` y `-s`, importador al día; F-02 (`summon` sobre lo que no es `Mob`) corregido · 55/55 dos veces en 1.21.4 y 26.1.2 |

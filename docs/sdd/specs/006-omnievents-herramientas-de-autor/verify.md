# Verificación 006 — OmniEvents: herramientas de autor

**Fecha:** 2026-10-24 · **Resultado:** Aprobada · **Versión:** OmniEvents 1.1.0

| Compuerta | Resultado |
|---|---|
| Unitarias | `gradlew build`: **62/62** (21 nuevas: 14 en `CondicionesTest` y 7 en `RendimientoYTrazasTest`). Los ejemplos, con el nuevo `herramientas.yml`, cargan con 0 errores y 0 avisos |
| Servidor real (carga, validate, selftest, reload) | `compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni reload" "omni reload" "omni stats" "omni check tienda_espada" "omni cpu on" "omni cpu" "omni cpu off" "omni debug export"`: carga 1.1.0 con 21 reglas, 0 errores de `validate`, `selftest` 12/12, dos `reload` con 8 oyentes · 3 temporizadores · 3 comandos, `check` sin jugador, `cpu` y `debug export` sin trazas responden bien; segundo arranque sin errores |
| En el juego (3a) | `npm --prefix tests-e2e test`: **27/27 dos veces** en Paper 1.21.4 build 232 (58 s y 57 s). Paper 26.1.2 build 74 (Java 25, bot 26.1): **27/27 dos veces** (53 s y 52 s). 5 escenarios nuevos (`006 …`) |
| Revisión exploratoria (3b) | Salida de `check`, `run --simular`, `cpu` y `debug export` leída en la consola del servidor real y en el archivo exportado: dio F-01 y F-02 |
| README (capacidades, Compatibilidad, tabla raíz) | OmniEvents: secciones «Condiciones con deny y N de M» y «Rendimiento: /omni cpu y budget», `check`/`run`/`debug export` en Diagnóstico, `budget` en Opciones, comandos nuevos y Compatibilidad 1.1.0 (27/27 en 1.21.4 y 26.1.2). Raíz: versión 1.1.0. Sin capturas nuevas (decisión 5) |

## Cobertura
| Criterio | Prueba |
|---|---|
| H1 CA1 | Escenario `006 H1…` (nivel 0 → `e2e deny nivel`, nunca `e2e else`); unitaria `H1_CA1_H2_CA1_mapas_con_expr_any_y_deny_cargan` |
| H1 CA2 | Escenario `006 H1…` (condición de texto que falla → `e2e else`) |
| H1 CA3 | Unitarias `H1_CA3_la_forma_de_texto_sigue_igual`, `H1_CA3_un_mapa_suelto_…`; todos los escenarios y ejemplos anteriores siguen en verde |
| H1 CA4 | Unitarias `H1_CA4_errores_de_forma_con_ruta`, `H1_CA4_clave_desconocida_sugiere`, `H1_CA4_las_raices_del_deny_se_validan` |
| H2 CA1 | Escenario `006 H1…` (1 de 3 → deny del grupo; 2 de 3 → actions); unitarias `H2_CA1_any_cuenta_y_corta…`, `H2_CA1_any_se_puede_anidar` |
| H2 CA2 | Escenario `006 H1…` (deny del grupo); unitaria `H2_CA2_deny_dentro_de_of_se_ignora_con_aviso` |
| H2 CA3 | Unitaria `H2_CA3_any_fuera_de_rango_es_error` (0, 4, 1.5, texto) |
| H2 CA4 | Escenario `006 H3…` (`al menos 2 de 3 → se cumplen N` con cada valor) |
| H3 CA1 | Escenario `006 H3…`: con nivel 0 falla la primera con deny y aun así se listan las de después |
| H3 CA2 | Escenario `006 H3…` (`→ correría: else…` / `deny de conditions[1]…`; ningún chat al jugador; `e2e_compras` sin cambio) |
| H3 CA3 | Unitaria `H3_CA3_avisa_las_raices_sin_valor_separando_al_jugador`; compuerta 2 (`check` desde la consola sin jugador) |
| H3 CA4 | Compuerta 2 (`el jugador 'x' no está conectado`); permiso `omnievents.command.check` en `plugin.yml` |
| H4 CA1 | Escenario `006 H4…` (`set`, `message`, `console`, `delay` sin esperar, `call` simulado por dentro); unitaria `H4_CA1_solo_las_acciones_de_control…` |
| H4 CA2 | Escenario `006 H4…` (`[Server] e2e no debe salir` nunca aparece, el jugador no recibe nada, `e2e_compras` sigue en 1) |
| H4 CA3 | Escenario `006 H4…` (el `set` se lista y la variable no cambia); documentado en el README |
| H4 CA4 | Escenario `006 H1…` (`/omni run` real corre actions y deja «CONSOLE ejecutó la regla…» en la consola) |
| H4 CA5 | Permiso `omnievents.command.run` en `plugin.yml` |
| H5 CA1 | Escenario `006 H5…` (archivo con encabezado, problemas, traza de `debug`, simulación y YAML de la regla); unitaria `H5_CA1_…` |
| H5 CA2 | Unitaria `H5_CA2_escribe_atomico_y_no_pisa…`; escritura en `runTaskAsynchronously` |
| H5 CA3 | Compuerta 2 (`No hay trazas guardadas…`) |
| H6 CA1 | Unitaria `H6_CA1_apagado_no_mide_nada`; escenario `006 H6…` (`on`, `off`, `reset`) |
| H6 CA2 | Escenario `006 H6…` (25 disparos → exactamente 2 medidos; partes `actions[0]…`); unitaria `H6_CA2_mide_uno_de_cada_n…` |
| H6 CA3 | Escenario `006 H6…` (cabecera con tiempo midiendo y muestreo) |
| H7 CA1 | Unitarias `H7_CA1_budget_acepta_ms_us_y_µs`, `H7_CA1_budget_valido_e_invalido` |
| H7 CA2 | Escenario `006 H6…` (un solo aviso en 25 disparos); unitarias `H7_CA2_*` (calentamiento, mínimo de disparos, intervalo) |
| H7 CA3 | Escenario `006 H6…` (`e2e_presupuesto · … ms de 0.001 ms · ✘ lo pasa`); compuerta 2 (`calentando` con el perfilador apagado) |

## Hallazgos
| ID | Severidad | Descripción | Evidencia | Prueba de regresión | Estado |
|---|---|---|---|---|---|
| F-01 | Baja | Una condición en forma de mapa quedaba con la ruta `conditions[1].expr`: `check`, `debug` y `cpu` la nombraban distinto que a una de texto (`→ correría: deny de conditions[1].expr`) | Escenario `006 H3…` en la segunda corrida | `CondicionesTest.H1_CA1_H2_CA1_…` afirma la ruta `tienda.conditions[1]`; falló con el código anterior (`but was: <tienda.conditions[1].expr>`) | Resuelto |
| F-02 | Baja | `/omni check` desde la consola decía «sin evento: player valen nulo», como si el jugador fuera un dato del evento, y con la concordancia mal | `run1.log` de la compuerta 2 | `CondicionesTest.H3_CA3_avisa_las_raices_sin_valor_separando_al_jugador`; falló con el código anterior (`but was: <[• sin evento: player valen nulo en esta revisión]>`) | Resuelto |
| F-03 | Baja | **Prueba**: la primera versión del escenario `006 H3…` buscaba `✔ player.level >= 3`, pero `explain` intercala los valores (`player.level (10) >= 3 → true`). No es un error del plugin | Primera corrida | El escenario busca el texto real | Resuelto (la prueba) |

## Riesgos residuales
- `check` y `--simular` no tienen un evento real: `block`, `message`… valen nulo (se avisa). Una regla de evento se
  simula con los datos del jugador, no con los del evento.
- En una simulación, las asignaciones no se aplican: una condición posterior que dependa de un `set` ve el valor de
  antes (documentado).
- El % del hilo principal es una estimación por muestreo; las reglas de chat asíncrono no cuentan (se marcan
  «asíncrona»).
- Durante la verificación otro servidor de la máquina (otro proyecto) ocupó el puerto 25599; la compuerta 2 corrió en
  `run/` con el puerto 25601 (la suite reescribe el puerto en cada corrida).

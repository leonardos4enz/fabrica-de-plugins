# Verificación 005 — OmniEvents: activar y desactivar reglas sin re-sincronizar comandos

**Fecha:** 2026-10-24 · **Resultado:** Aprobada · **Versión:** OmniEvents 1.0.1

| Compuerta | Resultado |
|---|---|
| Unitarias | `gradlew build`: **41/41** (3 nuevas de `RuleCommands.Spec` en `RuleCommandsTest`). Arnés: `node --test lib/*.test.mjs`, 5/5 en `servidor.test.mjs` (1 nueva, 005 F-01) |
| Servidor real (carga, validate, selftest, reload) | `compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni reload" "omni reload" "omni stats" "omni disable bienvenida" "omni enable bienvenida" "omni disable monedas" "omni enable monedas"`: carga 1.0.1, 0 errores de `validate`, `selftest` en verde, dos `reload` con 8 oyentes · 3 temporizadores · 2 comandos, los comandos siguen en 2 tras los cambios, segundo arranque sin errores |
| En el juego (3a) | `npm --prefix tests-e2e test`: **22/22 dos veces** en Paper 1.21.4 build 232 (49 s y 47 s), 0 `ConcurrentModificationException` en `run/e2e.log`. Paper 26.1.2 build 74 (Java 25, bot 26.1): **22/22 dos veces** (46 s y 47 s), 0 excepciones |
| Revisión exploratoria (3b) | El escenario nuevo es la revisión: el bot lee cada árbol de comandos que recibe (paquete `declare_commands`) y prueba el comando oculto y reactivado |
| README (capacidades, Compatibilidad, tabla raíz) | OmniEvents: nota de `enable|disable` en Comandos y fila de 1.21.4 (1.0.1, 22/22). Raíz: versión 1.0.1. `CLAUDE.md`: lección de F-01 resuelta y la de los jars. Sin capturas nuevas |

## La prueba falla con el código anterior
`npm --prefix tests-e2e test -- --solo "005"` con el jar 1.0.0 (antes del cambio):
`una regla que no es comando mandó 16 árboles de comandos`: cuatro `enable`/`disable` de `e2e_mensajes`, cuatro árboles
por cambio (`unregister` y `register`, cada uno con `syncCommands()` + `updateCommands()`). La excepción en sí es una
carrera y no salió en esa corrida (sí en las de la 004); el conteo de árboles falla siempre. Con el arreglo: 0 árboles
para la regla común y 3 para tres cambios de la regla comando `e2e`.

## Cobertura
| Criterio | Prueba |
|---|---|
| H1 CA1 | Escenario `005 …` (0 árboles tras 4 cambios de una regla común); unitarias `H1_CA1_*` de `Spec.of` |
| H1 CA2 | Escenario `005 …`: exactamente 3 árboles para 3 cambios de `/e2e`, y el 4.º al reactivarla |
| H1 CA3 | Escenario `005 …` (sin `ConcurrentModificationException` desde su marca) y la comprobación global del log en cada escenario |
| H2 CA1 | Escenario `005 …`: el último árbol no tiene `e2e` y `/e2e mensajes` responde «Unknown or incomplete command» |
| H2 CA2 | Escenario `005 …`: tras `omni enable e2e` el árbol vuelve a tener `e2e` y `/e2e mensajes` funciona |

## Hallazgos
| ID | Severidad | Descripción | Evidencia | Prueba de regresión | Estado |
|---|---|---|---|---|---|
| F-01 | Media | **Arnés**: con dos versiones en `dist/` (1.0.0 y 1.0.1), la suite instalaba la primera por orden alfabético (probaba el jar viejo) y `compuerta-servidor.sh` copiaba las dos: Paper no cargaba ninguna («Ambiguous plugin name») | La corrida con el arreglo seguía en 16 árboles; el log decía `OmniEvents v1.0.0` | `servidor.test.mjs` «005 F-01» (`jarMasNuevo`; afirma que el `find` anterior elegía 1.0.0); la compuerta 2 corrió limpia con 1.0.1 | Resuelto |
| F-02 | Baja | Paper arma cada árbol en otro hilo: tres cambios en el mismo tick mandan tres árboles con el estado **final**. No es un error, pero la primera versión del escenario esperaba ver `e2e` en el segundo | Tres árboles idénticos (2516 caracteres) | El escenario revisa el último árbol de cada tanda | Resuelto (la prueba) |

## Riesgos residuales
- Activar una regla comando que el archivo traía con `enabled: false`, o un `reload` que cambia comandos, sí registra
  (una vez) y puede chocar con un envío anterior todavía en curso: es la carrera de Paper, ahora solo en cambios reales.
- No se comprobó `/help` del servidor: el comando oculto podría seguir listado ahí hasta el próximo `reload` (no se puede usar).

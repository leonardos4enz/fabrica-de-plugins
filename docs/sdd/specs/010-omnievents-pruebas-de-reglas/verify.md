# Verify 010 — OmniEvents: pruebas de reglas en el YAML

**Fecha:** 2026-10-25 · **Versión:** OmniEvents 1.5.0

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ 123/123 (10 nuevas) | `./gradlew build`: `PruebasReglasTest` (casos que pasan con `fired`, `branch`, variables, `messages`, `commands`, `actions`, `cancelled` y `assert`; un caso que falla dice qué esperaba y qué salió, con la traza; errores de forma con ruta y «¿quisiste decir?» sin tumbar la regla; flujo con `repeat`, `if`, `call` a otra regla, `random` con semilla, `delay`, `chance` y `block.data`; bucle sin fin y división entre cero como fallas; regla `state` con su `when`; placeholders, `to:`, `for_each` y `permission`/`cooldown` que no tocan el servidor; resumen con reglas sin pruebas; F-01), `EjemplosPruebasTest` (las 11 pruebas de los ejemplos pasan, en 4 archivos) y `SchemaTest` con lo nuevo. Todo el recorrido corre con `plugin = null`: cualquier acceso real al servidor reventaría la prueba |
| 2. Servidor real (1.21.4) | ✔ | `compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni test" "omni reload" "omni stats" "omni reload" "omni stats"`: al arrancar «Pruebas de reglas: 11/11 pasan»; 32 reglas, 0 errores, 0 avisos; autoprueba 14/14; `omni test` desde la consola, sin jugadores: 6 reglas ✔ y «11 pruebas: todas pasan · 26 reglas sin pruebas»; cada `reload` vuelve a correrlas; dos `reload` con 10 oyentes, 3 temporizadores, 8 oyentes y 1 tarea de estado (iguales); arranque 2 igual; log sin errores (repetida con el jar final, tras F-03: igual) |
| 3a. En el juego, Paper 1.21.4 | ✔ 38/38 dos veces | 1 escenario nuevo (`010 …`) + los 37 anteriores; antes de corregir F-03, 37/38 en las 4 corridas |
| 3a. En el juego, Paper 26.1.2 (Java 25) | ✔ 38/38 dos veces | mismo jar; `--paper 26.1.2 --version-bot 26.1` |

### Escenario nuevo
| ID | Qué prueba |
|---|---|
| 010 H3 CA1 · H3 CA2 · H3 CA3 · H4 CA1 | Al arrancar, la consola avisó de la prueba que falla a propósito (`e2e_probada_falla`, con lo esperado y lo que salió); con la `pvar` real en 5, el bot (OP) corre `/omni test e2e_probada` y ve «2/2» y «todas pasan»; `/omni test e2e_probada_falla` muestra el caso, la diferencia y el resumen; `/omni test` desde la consola cuenta 1 que falla; el mensaje de la regla no le llega al bot, el `say` de su `console` no aparece en el log y la `pvar` real sigue en 5 |

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (`tests:` con `given` y `expect`; errores de forma) | `PruebasReglasTest` (H1_CA1, H1_CA3), `SchemaTest` (esquema con `test`) |
| H2 (qué se espera) | `PruebasReglasTest` (casos con cada clave de `expect`), ejemplos |
| H3 (`/omni test` sin efectos, desde la consola y sin jugadores) | `PruebasReglasTest` (H3), compuerta 2 (`omni test` por consola), escenario 010 |
| H4 (al cargar; gradle; `state`) | Compuerta 2 (línea al arrancar y en cada `reload`), escenario 010 (aviso de la que falla), `EjemplosPruebasTest`, `PruebasReglasTest` (H4_CA3) |

No se probó en el juego `tests.on-load: false` (una lectura de `config.yml` en `Settings`); riesgo bajo.

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Media | Una constante de `given` (`player.gamemode: SURVIVAL`) no se comparaba como en el servidor: allí `GameMode` es un enum y `== 'survival'` se cumple sin mayúsculas; en la prueba, el texto no, y la prueba del ejemplo `diamantes_contados` fallaba aunque la regla estaba bien | `RuleTester.value` convierte un texto en mayúsculas (`^[A-Z][A-Z0-9_]*$`) en un `Named`, como el motor hace con los enums. `PruebasReglasTest.F01_…` falla sin la corrección (comprobado) |
| F-02 | Media | La compuerta 2 probaba los ejemplos viejos del servidor de prueba (el plugin solo los copia si la carpeta no existe): `omni test` respondió «No hay pruebas en las reglas» | `tools/compuerta-servidor.sh` copia `src/main/resources/rules/ejemplos/` antes de arrancar, como ya hacía la suite (002 F-05). La corrida siguiente mostró «11/11 pasan»; lección en `CLAUDE.md` y `verificacion.md` |
| F-03 | Baja | El aviso de consola de una prueba que falla mostraba el código en lugar del nombre de la regla («de la regla  + r.rule().name() +  falla»): faltaban comillas en el texto de `OmniEvents.logTests` | Corregido. El escenario 010 busca ese aviso exacto y falló en las 4 corridas con el código anterior (37/38); pasa con la corrección |

## Revisión exploratoria (3b, agente)
- **Sin efectos:** las pruebas corren el motor de verdad (`Condition.test`, `Execution`, `ActionNode`) con un
  `Sandbox` colgado del contexto; solo `set`, `unset` y `cancel` corren, sobre el `Sandbox`. Los disparos reales no
  cambian: con `sandbox == null` cada rama nueva se salta (los 37 escenarios anteriores pasan igual).
- **Rendimiento:** sin oyentes ni tareas nuevas; las 11 pruebas de los ejemplos tardan pocos ms en cada `reload`
  (el `reload` de la compuerta 2 siguió en 11–14 ms con ellas). `ActionNode.preview` se compila al cargar.
- **Seguridad:** `/omni test` con `omnievents.command.test` (`op`); `console` y `command` solo se registran con su
  texto (la unitaria corre sin servidor y el escenario comprueba que el `say` no sale).
- **Límites conocidos (documentados):** `worlds`, `permission`, `cooldown` y `limit` no se prueban; `online()`,
  `player()` y `perm()` leen el servidor real (solo leen); un método de un objeto falso (`player.hasPermission(…)`)
  es un error del caso. Queda como mejora posible dar permisos y mundo falsos (CLARIFY 2: fuera del núcleo).

## README
- `plugins/omnievents/README.md`: sección «Pruebas de reglas» (ejemplo, `given`, `expect`, qué corre y qué no,
  `/omni test`, al cargar, `config.yml`), comando en la lista y Compatibilidad.
- `README.md` raíz: fila de OmniEvents en 1.5.0. Roadmap e índice (`docs/sdd/README.md`) con la 010.
- Capturas: ninguna nueva (CLARIFY 5).

# Verify 016 — OmniEvents: paridad con ConditionalEvents, tercera parte (detalles y ProtocolLib)

**Fecha:** 2026-10-25 · **Versión:** OmniEvents 1.11.0

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ 200/200 (9 nuevas) | `./gradlew build`: `ParidadCe3Test` (6: `actionbar` con duración en las tres formas, `effect` con ícono y `shot_direction` cargan, y sus errores de carga con ruta; `ShotDirection` con rangos, un número por eje y negativos; `slice`, `random_last`, `relative` con dos giros y el registro de las nueve funciones; `player_receive_chat` en `tests:` con `cancel` y su aviso sin ProtocolLib; `OmniRuleFireEvent`; registro), `ImporterTest.S016_*` (una configuración de CE con cada variable, acción y evento nuevo: carga sin errores, sin REVISAR y sin notas) e `ImporterTest.F01_016`/`F02_016`. El ejemplo `estilo_ce.yml` suma `anuncio_barra` con 2 pruebas y valida contra el esquema regenerado |
| 2. Servidor real (1.21.4) | ✔ | `compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni test" "omni reload" "omni stats" "omni reload" "omni stats"`: 58 reglas (la del ejemplo nuevo), 0 errores, 0 avisos; autoprueba 14/14; «34 pruebas: todas pasan»; dos `reload` con 14 oyentes, 3 temporizadores y 13 comandos (`/anunciar`); integraciones con ProtocolLib 5.4.0; segundo arranque igual; log sin errores. Antes, sin ProtocolLib en el servidor, el mismo resultado sin esa integración |
| 3a. En el juego, Paper 1.21.4 (build 232, Java 24) | ✔ 57/57 dos veces | 2 escenarios nuevos (`016 …`) + los 55 anteriores; ProtocolLib 5.4.0 (release de GitHub) |
| 3a. En el juego, Paper 26.1.2 (build 74, Java 25) | ✔ 57/57 dos veces | Mismo jar; ProtocolLib 5.5.0-SNAPSHOT, build #765 del Jenkins oficial (`ProtocolLib-Spigot.jar`) |

### Escenarios nuevos
| ID | Qué prueba |
|---|---|
| 016 H1 CA1 | `actionbar: "…;100"`: Ana ve «e2e barra larga de Ana_e2e» y la vuelve a ver dentro de los 4 s siguientes (reenvío cada 40 ticks; sin duración llegaría una vez) |
| 016 H4 CA1 | Con ProtocolLib (`/omni stats` lo lista): un `/tellraw` a Ana dispara `player_receive_chat` y la respuesta llega por la barra de acción con `type` `SYSTEM`; un `/tellraw @a` con «e2e oculto 016» no lo ven ni Ana ni Beto (`cancel`) y el mensaje siguiente sí llega; el chat de Beto llega a Ana como `PLAYER` (paquete firmado; regresión F-03) |

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (acciones) | Escenario 016 H1 (CA1), `ParidadCe3Test.H1_*` (CA1–CA3: carga, errores y `ShotDirection`) |
| H2 (variables) | `ImporterTest.S016_*`, `F01_016`, `F02_016`, `ParidadCe3Test.H2_*`; `world`, `location`, `near`, `looking_*` e `is_outside` por revisión de código (usan la API de Bukkit directamente) |
| H3 (`OmniRuleFireEvent`) | `ParidadCe3Test.H3_*` (campos, asíncrono, sin oyentes no se crea); el disparo por revisión de código |
| H4 (ProtocolLib) | Escenario 016 H4, `ParidadCe3Test.H4_*` (raíces, `cancel`, asíncrono, aviso sin ProtocolLib), `ImporterTest.S016_*` (CA4), [ADR 0004](../../../adr/0004-protocollib-opcional.md) (CA5) |
| H5 (documentación) | `SchemaTest`, `EjemplosPruebasTest`, README |

No se probó en el juego: `effect` con ícono, `firework` con `shot_direction`, las funciones nuevas en un servidor,
`OmniRuleFireEvent` (no hay un plugin oyente en la suite), el tope `max-messages-per-second` y `DISGUISED_CHAT`.
Riesgo bajo: cargan y corren en `tests:` o en unitarias y usan la API de Bukkit o de ProtocolLib directamente.

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Media | El importador traducía `%damage%` como `event.damage` (`getDamage()`); en CE es `getFinalDamage()` (el jar lo dice) | `%damage%` → `event.final_damage` y `%original_damage%` → `event.damage`. `ImporterTest.F01_016` falló con el importador de la 1.10.0 (comprobado) y pasa con la corrección |
| F-02 | Media | `%block_data%` se traducía como `block.data` (`minecraft:oak_stairs[facing=north,…]`); en CE es solo lo de dentro de los corchetes | Propiedad `block.states` y `%block_data%` → `block.states`. `ImporterTest.F02_016` falló con el importador de la 1.10.0 (comprobado) y pasa |
| F-03 | Alta | Con una regla `player_receive_chat`, cada mensaje de chat de un jugador (paquete firmado) daba en el hilo de red «no se pudo leer un paquete de chat (NullPointerException … "generic" is null)»: `getStructures().getValues()` de ProtocolLib revienta con el campo de firma nulo, y el chat de otros no disparaba la regla | Cada campo se lee por separado con `readSafely(i)` dentro de un `try`. El escenario H4 CA10 (que hace hablar a Ana) falló con el código anterior en la corrida 1 de 1.21.4 (el aviso cuenta como error del plugin) y pasa con la corrección; el escenario 016 H4 suma el chat de Beto (`PLAYER`) |
| F-04 | Baja (arnés) | Al parar una tanda de la suite en segundo plano siguió corriendo su `node` (y su Paper); una corrida encima dejó `gvar.e2e_evento = 1` guardado y el escenario 008 H6 falló en las dos corridas siguientes de 1.21.4 (ponerla en 1 no era un cambio) | El escenario borra la gvar antes de empezar. Con la gvar todavía en 1 en el servidor de prueba, las dos corridas de 1.21.4 pasaron. Lección en `CLAUDE.md` |

## Revisión exploratoria (3b, agente)
- **Compatibilidad (IV.5):** una configuración de la 1.10.0 carga igual (los 55 escenarios anteriores pasan). Cambios de
  comportamiento: un `actionbar` que termina en `;<número>` ahora es una duración; `firework` con `shot_direction` (antes
  error de carga) carga; el importador da otra traducción para `%damage%` y `%block_data%` (lo ya importado no cambia).
- **Seguridad:** ProtocolLib es opcional (`softdepend`, `compileOnly`) y la clase que lo usa solo se carga si está; lo
  recibido se trata como texto del jugador (se escapa al interpolar); el servidor de prueba baja ProtocolLib solo de
  GitHub y del Jenkins oficial con sha256 fijado (unitaria del arnés con las fuentes permitidas).
- **Rendimiento:** el adaptador de paquetes solo existe con reglas `player_receive_chat` habilitadas (se registra o
  quita en cada `reload`); tope por jugador y segundo contra bucles; `OmniRuleFireEvent` no se crea sin oyentes;
  `looking_*`, `is_outside` y `block_at` solo en el hilo principal (`is_outside` recorre como mucho la altura del mundo).
- **Jar de CE:** el mismo de la 014; de él salen los paquetes que lee CE, sus variables, `ConditionalEventsEvent`, que
  `to_range` lee dos valores (el tercero es de la wiki), y las formas exactas de `%block_data%`, `%damage%`,
  `%args_substring_…%` y `shot_direction`. Lo que solo está en la wiki (decisión 2) quedó marcado así en la spec.
- **ProtocolLib en 26.1.2:** no hay release; la build de desarrollo #765 pasó las dos corridas. Queda como versión
  verificada para la suite, no como recomendación de producción (lo dice el README).

## README
- `plugins/omnievents/README.md`: specs (016), funciones nuevas y `block.states`, acciones (`actionbar` con duración,
  `effect` con ícono, `shot_direction`) con ejemplos, `player_receive_chat` y la sección «Lo que recibe el jugador
  (ProtocolLib)», ProtocolLib en las dependencias opcionales, tabla «Desde ConditionalEvents» al día,
  `OmniRuleFireEvent` en la API, Compatibilidad (1.11.0). Se arreglaron dos trozos de texto corrupto que dejó la 015
  (antes del título y tras el ejemplo de `summon`) y una fila rota de la tabla de CE; las tablas de acciones y
  disparadores suman lo de la 015 que solo estaba en ese texto corrupto.
- `README.md` raíz: fila de OmniEvents (1.11.0) y ProtocolLib en la suite. Sin capturas nuevas (la estrella de la spec es
  texto que recibe un bot; no hace falta imagen).

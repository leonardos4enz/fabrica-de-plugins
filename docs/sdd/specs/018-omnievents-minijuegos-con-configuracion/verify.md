# Verificación 018

Fecha: 2026-10-27 · OmniEvents 1.11.1 (sin cambios de comportamiento: el ejemplo nuevo y la descripción de
`player_move`).

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ 203/203 | `./gradlew build`: `EjemplosPruebasTest` corre los 31 casos nuevos de `tests:` del paquete `parkour`; `RuleLoaderTest` (los ejemplos cargan sin avisos), `SchemaTest` (validan con el esquema regenerado), `PaquetesTest` (carga los ejemplos como 1.9.0, la versión que pide el paquete) y `DocsSiteTest` (el bloque de reglas de `minigames.md` carga y sus 4 pruebas pasan; índices y enlaces) |
| 2. Servidor real (1.21.4) | ✔ | `compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni test" "omni pack list" "omni reload" "omni stats"`: 76 reglas, 0 errores, 0 avisos; «65 pruebas: todas pasan» (las 12 reglas del parkour con pruebas, en verde); `○ parkour 1.0.0 · 18 reglas (0 activas)`: viene apagado; segundo arranque igual; log sin errores |
| 3a. En el juego, Paper 1.21.4 (build 232, Java 24) | ✔ 58/58 dos veces (con EssentialsX, WorldGuard, Citizens, ProtocolLib, Geyser y Floodgate, como siempre) | 1 escenario nuevo (`018 …`) + los 57 anteriores |
| 3a. En el juego, Paper 26.1.2 (build 74, Java 25) | ✔ 58/58 dos veces | Mismo jar |
| Sitio estático | Sin correr | Zensical no está instalado en esta máquina; `DocsSiteTest` comprueba que `SUMMARY.md` y el `nav:` de `mkdocs.yml` coinciden y que los enlaces existen |

## Escenario nuevo
| ID | Qué prueba |
|---|---|
| 018 H1 CA3 · H2 CA1–CA7 · H3 CA1–CA2 · H4 CA1 | `omni pack enable parkour`; Ana (OP) usa `/parkour construir` y ve las tres placas donde deben; en un recorrido elevado de la zona de trabajo camina sobre la placa de inicio («¡Ya!» y las reglas), pasa el checkpoint («Checkpoint 1 · <tiempo> s») y ve el reloj en la barra de acción; se tira por un costado y vuelve al checkpoint («¡Caída!… (1 caídas)»); llega a la meta: tiempo, récord personal y el anuncio del récord del servidor que ve Beto; `pvar.pk_record` y `gvar.pk_record_nombre` guardados y `/parkour record`; `/parkour reiniciar` la lleva a la placa de inicio; con `pk_inicio` adelantado por consola pierde por tiempo; un `/tp` de consola la hace perder; `/parkour salir`; al desconectarse en plena partida el registro dice que se cancela, `pk_activo` queda nulo y al volver no hay aviso de partida a medias; `pk_partidas` = 5 |

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (el parkour del dueño) | CA1: compuerta 2 (`0 activas`) y el escenario (`pack enable`); CA2: `tests:` de `inicio`, `checkpoint` y `meta` con los materiales de los parámetros; CA3: escenario; CA4: `pack.yml` (revisión: ninguna regla tiene números del juego escritos a mano) |
| H2 (una partida completa) | Escenario (CA1–CA7) y `tests:` (creativo, volando, checkpoint repetido, límite al llegar, muerte, modo, vuelo, perla y comando frente a `PLUGIN`, cada subcomando) |
| H3 (récords) | Escenario (CA1–CA2), `tests:` de `meta` (mejor y peor que el récord) y `comando` (`record` con y sin tiempos); CA3: guía (placeholders de la expansión existente, sin probar en la suite como el resto de PlaceholderAPI) |
| H4 (no se rompe) | CA1: escenario y `tests:` de `desconexion`; CA2: `tests:` de `partida_vieja`; CA3: `tests:` de `checkpoint` y `meta` (rama `deny`) |
| H5 (probado y documentado) | `EjemplosPruebasTest`, escenario, README del plugin y `docs/site/pages/omnievents/minigames.md` (`DocsSiteTest`) |

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Media | Las reglas del ejemplo miraban `player.location` en `player_move`, pero Bukkit dispara el evento **antes** de mover al jugador: ese es el bloque de origen. La caída no se detectaba (un paquete de -58.5 a -60 nunca tuvo el origen por debajo del margen) y la meta solo se habría disparado al bajarse de la placa | Las reglas usan la raíz `to` (`to.block.type`, `to.y`) y `guardar_punto` recibe la placa por argumentos. El escenario 018 falló con las reglas anteriores (comprobado, corrida 2: «no recibió actionbar ¡Caída!») y pasa con las nuevas. La descripción de `player_move` (referencia, esquema y página en inglés de disparadores) y la guía lo explican; lección en `CLAUDE.md` |
| F-02 | Baja (prueba) | `PaquetesTest.H5_CA2` cargaba todos los ejemplos como OmniEvents 1.6.0 y el paquete nuevo pide `>=1.9.0` | La prueba carga como 1.9.0, la versión más baja que piden los ejemplos |
| F-04 | Baja (prueba) | En la primera tanda completa (cuatro corridas) el escenario siguiente, `H5 CA3` (kick), falló siempre con «Error en regla 'bienvenida' (title): el jugador Ana_e2e ya no está conectado»: el escenario 018 la hacía volver a entrar y el título de `bienvenida` (2 s después) llegaba cuando el kick ya la había sacado | El escenario 018 espera el título «Bienvenido» antes de terminar. No es un error del plugin: la acción con el jugador desconectado se informa como aviso, como siempre |
| F-03 | Baja (arnés) | Al insertar el escenario en `suite.mjs` con `String.replace`, el `` $` `` de una regex del texto se interpretó como patrón y corrompió el archivo | Restaurado desde git e insertado con una función de reemplazo; lección en `CLAUDE.md` |

## Revisión exploratoria (3b)
- Recorrido de `/parkour construir` revisado contra las distancias del juego: saltos de un bloque de hueco con +1 de altura
  y uno en diagonal, todos posibles en supervivencia; la caída solo cuenta después del checkpoint (antes, el suelo está
  a la altura del inicio y el jugador vuelve caminando).
- Un jugador que no es OP recibe «Necesitas el permiso omnievents.parkour.admin» (`tests:`) y la ayuda no le muestra
  `construir` (la ayuda lo agrega solo con `perm(...)`; revisado en el YAML).
- En el juego, el mensaje de inicio llega en minúsculas («llega a la meta…»): la primera corrida del escenario lo
  buscaba con mayúscula (error de la prueba, no del juego).

## README
- README del plugin: sección «Cómo hacer un minijuego» (el paquete, parámetros, el patrón, trucos y lo que no se hace
  solo con reglas), enlace en la cabecera y Compatibilidad al día (1.11.1).
- README raíz: la fila de OmniEvents (1.11.1 y el minijuego de ejemplo).
- `docs/site/`: página nueva `minigames.md` en los dos índices, enlaces desde la portada y los ejemplos, y la nota de
  `player_move` en `triggers.md`.
- Sin capturas nuevas (el dueño no las pidió).

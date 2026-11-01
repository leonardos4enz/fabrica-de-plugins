# Verificación 019

Fecha: 2026-11-01 · OmniEvents 1.12.0 (paquete de ejemplos nuevo y dos correcciones: F-01 y F-02).

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ 207/207 | `./gradlew build`: `EjemplosEnInglesTest` (el paquete viene apagado; sin integraciones carga sin avisos; cada una de sus 22 reglas trae `tests:` y los 38 casos pasan; F-02), `EjemplosDelJarTest` (F-01); `EjemplosPruebasTest`, `RuleLoaderTest`, `SchemaTest` (los 6 archivos nuevos validan con el esquema) y `PaquetesTest` recorren todo `rules/`; `DocsSiteTest` carga los bloques nuevos de `examples.md` y corre sus pruebas |
| 2. Servidor real (1.21.4), instalación nueva **sin WorldGuard** | ✔ | Sin `run/plugins/OmniEvents/rules/` y sin el jar de WorldGuard: el jar crea `ejemplos/` (con `estilo_ce.yml` y `parkour/`, F-01) y `examples-en/`; 98 reglas, 0 errores y solo los 3 avisos de siempre de `ejemplos/integraciones.yml` (activo), ninguno del paquete apagado (F-02); «103 pruebas: todas pasan»; `○ examples 1.0.0 · 22 reglas (0 activas)`; `omni pack enable examples` → `where` desde la consola registra «/where is for players»; `omni test examples:where` 2/2; segundo arranque igual; log sin errores |
| 2. Servidor real (1.21.4), con las integraciones | ✔ | `compuerta-servidor.sh omnievents "omni validate" "omni test" "omni pack list" "omni reload" "omni stats"` (copia `ejemplos/` y `examples-en/` del código): Vault, WorldGuard, Citizens, ProtocolLib y Floodgate detectados; 98 reglas, 0 errores, 0 avisos; 103/103 pruebas; segundo arranque igual |
| 3a. En el juego, Paper 1.21.4 (build 232, Java 24) | ✔ 59/59 dos veces (133 s y 132 s) | 1 escenario nuevo (`019 …`) + los 58 anteriores, con EssentialsX, WorldGuard, Citizens, ProtocolLib, Geyser y Floodgate |
| 3a. En el juego, Paper 26.1.2 (build 74, Java 25) | ✔ 59/59 dos veces (129 s cada una) | Mismo jar |

## Escenario nuevo
| ID | Qué prueba |
|---|---|
| 019 H1 CA1 · H2 CA1–CA3 · H3 CA1–CA2 | `omni pack enable examples`; Ana usa `/daily` («Daily reward: +25 coins (streak: 1 days, you have 25).») y otra vez («You already claimed today. Come back in 2xh …m.»); `pvar.daily_streak` = 1 con «caduca en 47h 59m» (`expire: 48h`); sale y vuelve: «Welcome back, Ana_e2e! You have 25 coins.», el título «Welcome!» y el bono diario («+25 coins (you have 50)»); fuera del spawn y teletransportada 10 bloques arriba dentro: «You entered the spawn: a safe zone.» y aterriza con la vida completa; con regiones `mine` y `vip` marcadas con WorldEdit: `/where` fuera («You are not in any region.»), al entrar a la mina «Mining boost on» y el efecto, `/where` → «You are in: mine», al salir «Mining boost off» y sin efecto; como OP entra a `vip` (permiso no declarado), sin OP la región la frena («Only VIP players can enter this area.») y queda afuera |

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (paquete apagado en el jar) | CA1: compuerta 2 sin `rules/` (el jar lo crea, `0 activas`) y `EjemplosEnInglesTest`; CA2: `pack.yml` con 13 parámetros, y las pruebas los usan (revisión: ninguna regla tiene a mano un valor que esté en `params`); CA3: revisión de los comentarios; CA4: `EjemplosEnInglesTest` (cada regla con `tests:`, 38 casos) y `/omni test` en la compuerta 2 |
| H2 (lo común) | `tests:` de cada regla; escenario (bienvenida al volver, `/daily` y su enfriamiento, bono diario, zona del spawn y sin daño de caída) |
| H3 (WorldGuard) | CA1–CA2: escenario y `tests:`; CA3: compuerta 2 sin WorldGuard (carga, sin avisos del paquete apagado, `/where` responde) y `EjemplosEnInglesTest` con `integrations(Set.of())` |
| H4 (probado y documentado) | Escenario; `examples.md` (sección del paquete y de regiones, cargada por `DocsSiteTest`), `installation.md` y README del plugin |

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Alta | El jar copia a `rules/` una **lista fija** de ejemplos (`OmniEvents.EXAMPLES`) y le faltaban el paquete `parkour` (018) y `estilo_ce.yml` (014): en un servidor nuevo no aparecían, aunque el README y la documentación los prometen. La suite y la compuerta 2 copian los ejemplos del código fuente, así que nunca se vio | Lista completa (con `examples-en/`) y `EjemplosDelJarTest`, que exige que coincida con `src/main/resources/rules/`: con el código anterior falla (faltaban 9 archivos). La compuerta 2 sin `rules/` lo comprobó en un servidor real. Lección en `CLAUDE.md` |
| F-02 | Media | Una regla apagada, o de un paquete apagado, avisaba en cada arranque de la integración que le falta («las regiones necesita WorldGuard»): con el paquete de ejemplos apagado, cada servidor sin WorldGuard vería avisos de algo que no activó | `RuleLoader` solo revisa las integraciones de reglas activas. `EjemplosEnInglesTest.F02` (un paquete apagado, uno prendido y una regla con `enabled: false`) da 3 avisos con el código anterior y 1 con el nuevo. Límite: `/omni pack enable` no vuelve a revisar, así que activado así no avisa hasta el próximo reload |
| F-03 | Baja (prueba) | La primera corrida del escenario esperaba `daily_streak = 1` al final de la línea y `/omni var get` agrega «(caduca en 47h 59m)» | El escenario lo espera con la caducidad (comprueba de paso el `expire`) |
| F-04 | Baja (documentación) | El ejemplo «Launch pads» de `examples.md` miraba `player.location.block.type` en `player_move`, el bloque de origen (lección de la 018): lanzaba al bajarse de la placa | Usa `to.block.type`, como el paquete |

## Revisión exploratoria (3b)
- Nombres de comando revisados contra EssentialsX del servidor de prueba: `/coins`, `/daily` y `/where` no chocan.
- `chat_filter` es asíncrono: solo cancela y manda un mensaje (seguro fuera del hilo principal, plan 001 §3).
- `hide_plugins` usa un permiso no declarado: los OP lo tienen y ven `/plugins` (lo dice el comentario de `pack.yml`).
- Un servidor que ya tenía `rules/` no recibe el paquete: el README del plugin y `examples.md` dicen cómo copiarlo; un
  comando para instalarlo queda en el roadmap.

## README
- README del plugin: sección «Ejemplos en inglés (`examples-en`)», enlace en la cabecera, paso 2 de Instalación, nota de
  F-02 en Integraciones y Compatibilidad al día (1.12.0).
- README raíz: la fila de OmniEvents (1.12.0 y el paquete de ejemplos).
- `docs/site/`: `examples.md` (el paquete, cómo traerlo a un servidor existente, una sección de regiones de WorldGuard y
  F-04), `installation.md` (estructura de `rules/` y avisos de reglas apagadas) y la portada.
- Sin capturas nuevas (no se pidieron).

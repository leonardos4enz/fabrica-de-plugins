# Verificación 019

Fecha: 2026-11-01 · OmniEvents 1.12.0 (paquete de ejemplos nuevo y dos correcciones: F-01 y F-02). Al revisar el cierre, el dueño pidió
ejemplos de los demás plugins soportados (H5): se sumó `integrations.yml` y se repitieron las compuertas con el jar final.

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ 207/207 | `./gradlew build`: `EjemplosEnInglesTest` (el paquete viene apagado; sin integraciones carga sin avisos; cada regla trae `tests:` salvo la apertura, el cierre y el botón «Close» del menú, y los 53 casos pasan; F-02), `EjemplosDelJarTest` (F-01); `EjemplosPruebasTest`, `RuleLoaderTest`, `SchemaTest` (los 7 archivos nuevos validan con el esquema) y `PaquetesTest` recorren todo `rules/`; `DocsSiteTest` carga los bloques nuevos de `examples.md` y corre sus pruebas |
| 2. Servidor real (1.21.4), instalación nueva **sin WorldGuard** | ✔ | Sin `run/plugins/OmniEvents/rules/` y sin el jar de WorldGuard: el jar crea `ejemplos/` (con `estilo_ce.yml` y `parkour/`, F-01) y `examples-en/`; 98 reglas, 0 errores y solo los 3 avisos de siempre de `ejemplos/integraciones.yml` (activo), ninguno del paquete apagado (F-02); «103 pruebas: todas pasan»; `○ examples 1.0.0 · 22 reglas (0 activas)`; `omni pack enable examples` → `where` desde la consola registra «/where is for players»; `omni test examples:where` 2/2; segundo arranque igual; log sin errores. Corrida con el paquete sin `integrations.yml`; el arranque nuevo con el jar final está en la fila siguiente |
| 2. Servidor real (1.21.4), con las integraciones | ✔ | `compuerta-servidor.sh omnievents "omni validate" "omni test" "omni pack list" "omni reload" "omni stats"` (copia `ejemplos/` y `examples-en/` del código): Vault, WorldGuard, Citizens, ProtocolLib y Floodgate detectados; con el jar final, 110 reglas, 0 errores, 0 avisos; 118/118 pruebas; `○ examples 1.0.0 · 34 reglas (0 activas)`; segundo arranque igual |
| 3a. En el juego, Paper 1.21.4 (build 232, Java 24) | ✔ 59/59 dos veces con el jar final (138 s cada una) | 1 escenario nuevo (`019 …`) + los 58 anteriores, con EssentialsX, WorldGuard, Citizens, ProtocolLib, Geyser y Floodgate |
| 3a. En el juego, Paper 26.1.2 (build 74, Java 25) | ✔ 59/59 dos veces con el jar final y el filtro de F-05 (133 s cada una) | Mismo jar |

## Escenario nuevo
| ID | Qué prueba |
|---|---|
| 019 H1 CA1 · H2 CA1–CA3 · H3 CA1–CA2 · H5 CA1–CA2 | `omni pack enable examples`; Ana usa `/daily` («Daily reward: +25 coins (streak: 1 days, you have 25).») y otra vez («You already claimed today. Come back in 2xh …m.»); `pvar.daily_streak` = 1 con «caduca en 47h 59m» (`expire: 48h`); sale y vuelve: «Welcome back, Ana_e2e! You have 25 coins.», el título «Welcome!» y el bono diario («+25 coins (you have 50)»); fuera del spawn y teletransportada 10 bloques arriba dentro: «You entered the spawn: a safe zone.» y aterriza con la vida completa; con regiones `mine` y `vip` marcadas con WorldEdit: `/where` fuera («You are not in any region.»), al entrar a la mina «Mining boost on» y el efecto, `/where` → «You are in: mine», al salir «Mining boost off» y sin efecto; como OP entra a `vip` (permiso no declarado), sin OP la región la frena («Only VIP players can enter this area.») y queda afuera; con 150 de dinero (EssentialsX por Vault) y 20 de trigo, `/sellwheat` («Sold 16 wheat for $20.00. Balance: $170.00.») y otra vez («You need 12 × WHEAT.»); `/shop`: compra la manzana dorada, no le alcanza para otra y «Close» cierra; clic derecho al NPC «Guide» de Citizens (consejos y 4 de pan) y un golpe («Guide says: hey, that hurts!»); un `tellraw` con «VOTE FOR US» no le llega y el siguiente sí (ProtocolLib) |

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (paquete apagado en el jar) | CA1: compuerta 2 sin `rules/` (el jar lo crea, `0 activas`) y `EjemplosEnInglesTest`; CA2: `pack.yml` con 18 parámetros, y las pruebas los usan (revisión: ninguna regla tiene a mano un valor que esté en `params`); CA3: revisión de los comentarios; CA4: `EjemplosEnInglesTest` (cada regla con `tests:`, 53 casos) y `/omni test` en la compuerta 2 |
| H2 (lo común) | `tests:` de cada regla; escenario (bienvenida al volver, `/daily` y su enfriamiento, bono diario, zona del spawn y sin daño de caída) |
| H3 (WorldGuard) | CA1–CA2: escenario y `tests:`; CA3: compuerta 2 sin WorldGuard (carga, sin avisos del paquete apagado, `/where` responde) y `EjemplosEnInglesTest` con `integrations(Set.of())` |
| H5 (los demás plugins) | CA1: `integrations.yml` y sus `tests:` (Vault con `money` y `inventory`, menú, Citizens con `npc.name`, `%player_ping%`, `bedrock`, `player_receive_chat`); CA2: compuerta 2 (apagado, 0 avisos) y escenario (Vault, menú, Citizens, ProtocolLib). PlaceholderAPI y Floodgate solo en `tests:`: la suite no tiene PlaceholderAPI y el jugador de Bedrock entra en otro escenario |
| H4 (probado y documentado) | Escenario; `examples.md` (sección del paquete y de regiones, cargada por `DocsSiteTest`), `installation.md` y README del plugin |

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Alta | El jar copia a `rules/` una **lista fija** de ejemplos (`OmniEvents.EXAMPLES`) y le faltaban el paquete `parkour` (018) y `estilo_ce.yml` (014): en un servidor nuevo no aparecían, aunque el README y la documentación los prometen. La suite y la compuerta 2 copian los ejemplos del código fuente, así que nunca se vio | Lista completa (con `examples-en/`) y `EjemplosDelJarTest`, que exige que coincida con `src/main/resources/rules/`: con el código anterior falla (faltaban 9 archivos). La compuerta 2 sin `rules/` lo comprobó en un servidor real. Lección en `CLAUDE.md` |
| F-02 | Media | Una regla apagada, o de un paquete apagado, avisaba en cada arranque de la integración que le falta («las regiones necesita WorldGuard»): con el paquete de ejemplos apagado, cada servidor sin WorldGuard vería avisos de algo que no activó | `RuleLoader` solo revisa las integraciones de reglas activas. `EjemplosEnInglesTest.F02` (un paquete apagado, uno prendido y una regla con `enabled: false`) da 3 avisos con el código anterior y 1 con el nuevo. Límite: `/omni pack enable` no vuelve a revisar, así que activado así no avisa hasta el próximo reload |
| F-03 | Baja (prueba) | La primera corrida del escenario esperaba `daily_streak = 1` al final de la línea y `/omni var get` agrega «(caduca en 47h 59m)» | El escenario lo espera con la caducidad (comprueba de paso el `expire`) |
| F-05 | Baja (arnés) | Con el NPC «Guide» del escenario, en una corrida de 26.1.2 Paper registró al apagar «Nag author(s): [fullwall] of Citizens … not properly shutting down its async tasks» (Citizens seguía bajando la skin del NPC) y el arnés lo contó como error del servidor, con los 59 escenarios en verde | Es ruido de Citizens, no del plugin: va en `ruidoDelServidor` de la suite con su comentario, y se repitieron las dos corridas de 26.1.2 |
| F-04 | Baja (documentación) | El ejemplo «Launch pads» de `examples.md` miraba `player.location.block.type` en `player_move`, el bloque de origen (lección de la 018): lanzaba al bajarse de la placa | Usa `to.block.type`, como el paquete |

## Revisión exploratoria (3b)
- Nombres de comando revisados contra EssentialsX del servidor de prueba: `/coins`, `/daily`, `/where`, `/sellwheat`, `/shop` y `/stats` no
  chocan (EssentialsX tiene `/ping`: por eso `/stats`).
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

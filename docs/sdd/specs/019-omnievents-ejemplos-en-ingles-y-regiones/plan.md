# Plan 019 — Diseño técnico

## 1. Arquitectura
Casi todo es configuración que ya existe: un paquete (011) con `params`, apagado con `enabled: false`; disparadores
`player_join`, `player_quit`, `command`, `timer`, `state`, `player_damage`, `player_move`, `player_chat`,
`player_command`, `player_death`, `block_break`, `region_enter` y `region_leave`; `expire` (009); `tests:` (010);
`in_region()` y `regions()` (012). En el código solo van las dos correcciones de VERIFY (§6).

| Archivo | Qué es |
|---|---|
| `plugins/omnievents/src/main/resources/rules/examples-en/pack.yml` | Paquete `examples`, `enabled: false`, parámetros |
| `…/examples-en/welcome.yml` | `welcome`, `goodbye`, `coins` (`/coins [jugador]`) |
| `…/examples-en/daily.yml` | `daily_login` (limit 1 diario), `daily` (`/daily`, enfriamiento 24 h, racha con `expire: 48h`), `playtime` |
| `…/examples-en/zones.yml` | `spawn_zone` (`state`), `safe_spawn` (sin caída ni PvP), `launch_pad` |
| `…/examples-en/common.yml` | `announcer`, `chat_filter`, `hide_plugins`, `death_coordinates`, `low_health`, `first_diamond` |
| `…/examples-en/regions.yml` | `region_welcome`, `region_goodbye`, `vip_only`, `mine_haste`, `mine_haste_off`, `afk_reward` (`in_region`), `where` (`regions()`) |
| `…/examples-en/integrations.yml` | Vault (`sell_wheat`, menú `shop` con `open_shop`), Citizens (`npc_guide`, `npc_quest`, `npc_punch`), PlaceholderAPI (`stats`), Floodgate (`bedrock_hint`), ProtocolLib (`hide_spam`) |
| `OmniEvents.java` | La lista de ejemplos que se copian del jar (F-01) |
| `RuleLoader.java` | Sin avisos de integración en una regla apagada (F-02) |
| `tools/compuerta-servidor.sh`, `tests-e2e/omnievents/suite.mjs` | Copian también `examples-en/` del código fuente |

Decisiones de diseño:
- **Las pruebas no dependen de los parámetros.** Las claves de `given`/`expect` no se sustituyen, los valores sí: los
  casos dan `region.id: "${vip_region}"` y comprueban cantidades con `assert: "pvar.coins == 10 + ${daily_coins}"`. Si
  el dueño cambia `pack.yml`, las pruebas siguen pasando. Las monedas son `pvar.coins` literal por la misma razón.
- **Zona del spawn por coordenadas, no `in_area()`:** `in_area()` mira al jugador real y en `tests:` da falso; con
  `abs(player.location.x - ${spawn_x}) <= ${spawn_radius}` se prueba.
- **`launch_pad` con `to.block.type`** (lección de la 018: en `player_move`, `player.location` es el origen) y una placa
  poco usada (`CRIMSON_PRESSURE_PLATE`), para que no lance a quien pisa la placa de una puerta.
- **Nombres de comando** sin chocar con EssentialsX en el servidor de la suite: `/coins`, `/daily`, `/where`, `/sellwheat`,
  `/shop`, `/stats` (EssentialsX ya tiene `/ping`).
- **El paquete pide `>=1.11.0`** (`player_receive_chat`): `PaquetesTest` carga los ejemplos como 1.11.0 (lección 018).

## 2. Contrato de configuración
```yaml
name: examples
enabled: false
requires: { omnievents: ">=1.11.0" }   # player_receive_chat
params:
  daily_coins: 25            # bono diario y por día de racha (/daily, hasta 7 días)
  hourly_coins: 50           # por cada hora jugada
  spawn_world: world         # zona del spawn: cuadrado de spawn_radius alrededor de (spawn_x, spawn_z)
  spawn_x: 0
  spawn_z: 0
  spawn_radius: 50
  launch_pad: CRIMSON_PRESSURE_PLATE
  staff_permission: examples.staff
  spawn_region: spawn        # ids de regiones de WorldGuard
  vip_region: vip
  vip_permission: examples.vip
  mine_region: mine
  afk_region: afk
  wheat_price: 20            # Vault
  apple_price: 100
  guide_npc: Guide           # Citizens
  quest_npc: Miner
  hidden_text: vote for us   # ProtocolLib
```
Variables que deja: `pvar.coins`, `pvar.daily_streak` (caduca a las 48 h), `pvar.minutes_played`,
`pvar.found_diamond`. Sin claves nuevas de `config.yml`.

## 3. Hilos y rendimiento
Nada nuevo: viene apagado. Activado, `launch_pad` corre en cada cambio de bloque con una comparación barata
(`to.block.type == '…'`); `spawn_zone` es un `state` que solo se revisa al moverse (008); los temporizadores son de
1, 5 y 10 minutos; `chat_filter` es asíncrono y solo cancela y manda un mensaje (seguro fuera del hilo principal).

## 4. Persistencia
Las `pvar` de siempre en `data/players/<uuid>.yml`; la caducidad de la racha con el mecanismo de la 009.

## 5. Amenazas (STRIDE)
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| Suplantación: alguien sin permiso entra a la zona VIP o ve `/plugins` | Acceso indebido | `perm('${vip_permission}')` y `perm('${staff_permission}')`; la guía avisa que un permiso no declarado vale para los OP (lección 014) |
| Manipulación: `/daily` repetido o la racha falsa | Monedas de más | `cooldown: 24h` por jugador y persistente; la racha la escribe solo la regla |
| Repudio | — | Sin acciones de consola |
| Divulgación: `/coins <jugador>` muestra las monedas de otro | Bajo (como `/balance`) | Comentado en la regla; el dueño la cambia si no quiere |
| Denegación: `player_move` y `state` en un servidor lleno | TPS | Condiciones baratas; viene apagado; `/omni cpu` las mide |
| Elevación | — | Sin `console`, `command` ni `op` |

## 6. Hallazgos de código (correcciones)
- **F-01:** `OmniEvents.EXAMPLES` es la lista fija de lo que se copia del jar a `rules/` la primera vez. No tenía el
  paquete `parkour` (018) ni `estilo_ce.yml` (014): en un servidor nuevo no aparecían, aunque los README lo prometían.
  La suite y la compuerta 2 copian los ejemplos del código fuente, así que nunca se vio. Se completa la lista y
  `EjemplosDelJarTest` exige que coincida con los archivos de `src/main/resources/rules/`.
- **F-02:** un paquete apagado (o una regla con `enabled: false`) avisaba en cada arranque de la integración que le
  falta («necesita WorldGuard»), aunque no va a correr. Con el paquete de regiones apagado, cada servidor sin WorldGuard
  vería cinco avisos de algo que no activó. `RuleLoader` solo avisa de integraciones de reglas activas.

## 7. Pruebas
- Unitarias: `EjemplosEnInglesTest` (viene apagado, carga sin avisos sin integraciones, cada regla tiene `tests:` y los
  53 casos pasan; F-02 con un paquete apagado, uno prendido y una regla apagada), `EjemplosDelJarTest` (F-01);
  `EjemplosPruebasTest`, `RuleLoaderTest`, `SchemaTest` y `PaquetesTest` ya recorren todo `rules/`.
- Compuerta 2: una instalación nueva **sin WorldGuard** (sin `rules/`: el jar crea los ejemplos, sin avisos del paquete
  apagado, `/where` desde la consola) y la de siempre con las integraciones.
- Suite (3a), escenario «019»: `/daily` y su enfriamiento, saludo y bono diario al volver, la zona del spawn sin daño de
  caída, regiones de WorldGuard (mina con Haste al entrar y sin él al salir, `/where`, VIP para un OP y bloqueada sin OP),
  `/sellwheat` con la economía de EssentialsX, el menú `/shop`, el NPC «Guide» de Citizens y un mensaje oculto con ProtocolLib.

# Spec 016 — OmniEvents: paridad con ConditionalEvents, tercera parte (detalles y ProtocolLib)

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 001, 014, 015 ·
**Origen:** las specs 014 y 015 dejaron sin traducir detalles chicos de ConditionalEvents (CE) y marcaron como **no** la
lectura de paquetes. El dueño fijó el alcance de esta: todos esos detalles y, además, la lectura del chat con ProtocolLib.
La 003 sigue reservada; esta va como 016.

## Problema
Un servidor que migra de CE con `/omni import` todavía ve notas «sin equivalente» o pierde datos sin aviso: el
`actionbar` con duración se queda en un envío, el ícono de `give_potion_effect` y el `shot_direction` de `firework` se
descartan, y una docena de variables (`%playerblock_below_1%`, `%is_nearby_…%`, `%args_substring_2-5%`…) no tienen
traducción. Las reglas que reaccionan a lo que el jugador **recibe** en el chat (`protocollib_receive_message`,
`player_receive_chat`) no existen en OmniEvents. Y otros plugins no tienen cómo enterarse de que una regla corrió.

## Fuente
El jar de la 014 (`tests-e2e/.cache/ce/ConditionalEvents-4.65.1.jar`, sha256
`15847ebb0e17dc3ed95409b1b16b1e69948ce3ac62837f68c551961eec307d92`), leído con `javap`, y la wiki actual de CE para lo
que el jar no tiene (decisión 2). Lo que dice el jar:
- **`ProtocolLibManager`** escucha los paquetes `Play.Server.CHAT`, `SYSTEM_CHAT` y `DISGUISED_CHAT` con prioridad
  `HIGHEST`; descarta los de tipo `GAME_INFO` y los `SYSTEM_CHAT` con `overlay = true` (la barra de acción); toma el texto
  de un campo `String` (JSON), de `BaseComponent[]`, de un `Component` de Adventure o de `getChatComponents()`, y dispara
  `PROTOCOLLIB_RECEIVE_MESSAGE` con `%json_message%`, `%normal_message%` (con `&`) y
  `%normal_message_without_color_codes%`. Si la regla cancela, se cancela el paquete (el jugador no lo ve). Sin reglas de
  ese tipo, el oyente vuelve enseguida. `player_receive_chat` solo está en la wiki (lee paquetes con PacketEvents).
- **`ConditionalEventsEvent`** (`api`): jugador (nulo si no hay), nombre del evento y grupo de acciones; se lanza en el
  hilo principal cuando las condiciones se cumplen y corre un grupo. No se puede cancelar.
- **`to_range`** lee solo `radio;incluir_jugador` (y solo jugadores): el tercer valor de la wiki no está en el jar. El
  importador ya lo traduce a `nearby:` (seres vivos); esta spec lo deja probado.
- **`actionbar`** es `texto;ticks` y se reenvía hasta cubrir la duración; **`give_potion_effect`** es
  `efecto;ticks;nivel;partículas;ícono`; **`firework`** con `shot_direction:xmin,xmax;ymin,ymax;zmin,zmax;vmin-vmax`
  dispara en ángulo con una velocidad al azar en esos rangos.
- **`%block_data%`** y **`%block_data_at_…%`** son lo de dentro de los corchetes de `BlockData.getAsString()`
  (`facing=north,half=bottom`), no el texto entero; **`%damage%`** es `getFinalDamage()` y **`%original_damage%`**
  `getDamage()`; **`%player_is_outside%`** es «no hay ningún bloque que no sea aire arriba hasta el techo del mundo»;
  **`%args_substring_a-b%`** une los argumentos `a` a `b` (desde 1) con espacios; **`%block_above_n%`/`%block_below_n%`**
  son relativos al bloque del evento; **`%select_type%`** es `SELECT` o `DESELECT` (CE dispara `item_select` dos veces
  por cambio de casilla).
- Solo en la wiki: `%random_last%` (el último `%random_a_b%`), `%player_location_<x|y|z>_<adelante>_<lado>_<arriba>%`,
  `%player_looking_<block|entity>_<distancia>%` y `%movement_type%` de `player_offhand` (`SWAP`, `PLACE`, `RECOVER`, `NOTHING`).

## Historias
**H1 — Acciones con todos sus parámetros.** Como *administrador*, quiero que las acciones acepten lo mismo que en CE.
- CA1: `actionbar: "texto;ticks"` (o la clave hermana `duration`, en ticks o con unidad: `6s`) mantiene el texto en la
  barra de acción ese tiempo (se reenvía cada 2 s); sin duración, un envío como hoy. Si el jugador se desconecta, se deja
  de enviar.
- CA2: `effect: "EFECTO;segundos;nivel;partículas;ícono"`: con `ícono` falso el efecto no se ve en la pantalla del jugador.
- CA3: `firework` con `shot_direction:xmin,xmax;ymin,ymax;zmin,zmax[;vmin-vmax]` (o un solo número por eje) dispara el
  cohete en ángulo con esa dirección y velocidad al azar. Un rango mal escrito constante es error al cargar.

**H2 — Variables de CE que faltaban.** Como *administrador* que migra, quiero que `/omni import` traduzca todas las variables.
- CA1: Funciones nuevas: `world(nombre)`, `location(mundo, x, y, z)`, `near(a, b, radio)`, `slice(lista o texto, desde[, hasta])`,
  `relative(entidad, adelante, lado, arriba)`, `looking_block(distancia[, entidad])`, `looking_entity(distancia[, entidad])`,
  `is_outside([entidad])` y `random_last()`; propiedad `states` de un bloque (lo de dentro de los corchetes).
- CA2: El importador traduce `%block_data_at_x_y_z_m%`, `%is_nearby_x_y_z_m_r%`, `%playerblock_below_n%`,
  `%playerblock_above_n%`, `%playerblock_inside%`, `%player_is_outside%`, `%block_above_n%`, `%block_below_n%`,
  `%world_time_<mundo>%`, `%original_damage%`, `%args_substring_a-b%`, `%player_location_…%`, `%player_looking_…%`,
  `%random_last%`, `%select_type%` y `%movement_type%`, sin nota «sin equivalente».
- CA3: Corrige dos traducciones que no eran las de CE: `%damage%` → `event.final_damage` y `%block_data%` → `block.states`.
- CA4: `actionbar` con duración, el ícono de `give_potion_effect` y `shot_direction` se traducen (sin nota ni pérdida);
  `to_range: r;false;true` sigue siendo `to: nearby:r` sin el jugador del evento.

**H3 — Evento para otros plugins.** Como *desarrollador de otro plugin*, quiero enterarme cuando una regla corre.
- CA1: `dev.leo.omnievents.api.OmniRuleFireEvent` se lanza cuando una regla pasa sus condiciones, su límite, su enfriamiento
  y su costo, justo antes de sus acciones: nombre de la regla, disparador, jugador (nulo si no hay) y grupo (`actions`).
  No se puede cancelar. Es asíncrono si la regla corre fuera del hilo principal (`isAsynchronous()`).
- CA2: Sin oyentes no se crea (III.1); `/omni run --simular` y `/omni test` no lo lanzan.

**H4 — Lo que recibe el jugador (ProtocolLib).** Como *administrador*, quiero reaccionar a los mensajes del chat que recibe un jugador.
- CA1: `trigger: player_receive_chat`: el jugador va a recibir un mensaje del sistema o del chat (`message` sin formato,
  `message_legacy` con códigos `&`, `json` en JSON de Minecraft, `type`: `SYSTEM`, `DISGUISED` o `PLAYER`). `cancel` hace
  que no lo vea. La barra de acción no cuenta (como CE).
- CA2: Necesita ProtocolLib (opcional, `softdepend`): sin él, una regla con ese disparador carga con aviso y no corre. Los
  paquetes se escuchan solo si una regla habilitada usa el disparador; al recargar sin ninguna, se deja de escuchar.
- CA3: Es asíncrono (el hilo de red): corre como `player_chat` (lo que toca el mundo pasa al hilo principal). El texto
  recibido es entrada no confiable (II.2). Un tope de mensajes por jugador y segundo
  (`protocollib.max-messages-per-second`, 20) corta una regla que se responde a sí misma.
- CA4: El importador traduce `protocollib_receive_message` y `player_receive_chat` a `player_receive_chat`, con
  `%json_message%` → `json`, `%normal_message%` → `message_legacy` y `%normal_message_without_color_codes%` → `message`.
- CA5: Un ADR registra la dependencia de ProtocolLib (constitución VI.2).

**H5 — Documentación.** Todo lo nuevo en su registro (esquema y referencia regenerados), en un ejemplo con `tests:` y en el README.

## Fuera de alcance
- `player_command_as_op`, DiscordSRV, `remove_item` con `datavalue:`, `custom_model_component_data_*` y `vector` (como en la 015).
- Cambiar el texto de un mensaje recibido (CE tampoco) y leer la barra de acción o el chat firmado de otros jugadores
  completo: un paquete `PLAYER_CHAT` firmado solo trae su texto sin firmar si el servidor lo puso (CE con ProtocolLib
  tampoco lo lee; la wiki pide PacketEvents). El chat de otros se lee con `player_chat`.
- PacketEvents (la otra biblioteca de paquetes): solo ProtocolLib.
- Variables de CE que no pidió el dueño: `%player_attack_cooldown%`, `%format_remove_…%`, `%item_meta%`,
  `%item_itemsadder_id%`, `%block_head_texture%`, `%item_model%` (siguen con nota).

## Decisiones (CLARIFY)
| # | Pregunta | Decisión |
|---|---|---|
| 1 | ¿Alcance? (estimado ≈ 1.2 sesiones: A ≈ 0.6 —variables 0.3, acciones 0.2, evento 0.1—; B ≈ 0.6 —ADR, hook, disparador, suite con ProtocolLib en dos versiones—) | Fijado por el dueño: todo, con escenarios en el juego para lo más visible |
| 2 | `%random_last%`, `%player_location_…%`, `%player_looking_…%`, `%movement_type%` no están en el jar 4.65.1, solo en la wiki | Traducir según la wiki (dueño) |
| 3 | Para Paper 26.1.2 no hay release de ProtocolLib, solo la build de desarrollo 5.5.0-SNAPSHOT del Jenkins oficial (#765, `ProtocolLib-Spigot.jar`); para 1.21.4, el release 5.4.0 de GitHub | Usar la #765 con su sha256 fijado (dueño) |
| 4 | ¿Leer también la barra de acción? | No, solo el chat, como CE (dueño): así una regla no reacciona al `actionbar` que se reenvía |

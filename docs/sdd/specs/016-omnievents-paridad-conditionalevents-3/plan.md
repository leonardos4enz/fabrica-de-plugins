# Plan 016 — Diseño técnico

## 1. Arquitectura

### 1.1 Acciones (`action/Actions.java`, `action/Firework.java`)
- `actionbar`: si el texto termina en `;<entero>` (o `;{expresión}`), ese último trozo es la duración en ticks; la
  clave hermana `duration` (ticks o `Durations`: `6s`) también vale. Con duración: se manda ya y una tarea
  (`runTaskTimer`, cada 40 ticks) lo reenvía mientras no se cubra la duración y el jugador siga conectado. El texto se
  calcula una vez (como CE). Sin duración, como hoy.
- `effect`: 5.º trozo `ícono` → `new PotionEffect(tipo, duración, nivel, false, partículas, ícono)`.
- `firework`: clave `shot_direction` (deja de ser error). `Firework.ShotDirection.parse("a,b;c,d;e,f[;v1-v2]")`: cada
  eje es `n` o `min,max`; la velocidad, `n` o `min-max` (1 por defecto). Pura y con unitaria; lo constante se valida al
  cargar. Al lanzar: `setShotAtAngle(true)` y `setVelocity(vector al azar × velocidad)`.

### 1.2 Funciones y propiedades (`expr/Functions.java`, `expr/BukkitProperties.java`)
| Función | Qué hace | Hilo |
|---|---|---|
| `world(nombre)` | El mundo o nulo | cualquiera |
| `location(mundo, x, y, z)` | Una ubicación (nulo si el mundo no existe); `mundo` es un nombre o un mundo | cualquiera |
| `near(a, b, r)` | true si `a` y `b` (ubicaciones, entidades o bloques) están en el mismo mundo a `r` o menos | cualquiera |
| `slice(x, desde[, hasta])` | Sublista (o subtexto), posiciones desde 0, `hasta` excluido y recortado al largo | cualquiera |
| `relative(e, adelante, lado, arriba)` | La ubicación de `e` corrida según hacia dónde mira (solo el giro horizontal): adelante +, derecha +, arriba + | cualquiera |
| `looking_block(d[, e])` | El bloque sólido que mira el jugador (o `e`) a `d` bloques o menos (`getTargetBlockExact`), o nulo | principal |
| `looking_entity(d[, e])` | La entidad que mira (`getTargetEntity`), o nulo | principal |
| `is_outside([e])` | Ningún bloque que no sea aire encima de `e` (el jugador) hasta el techo del mundo (como CE; bucle acotado por la altura) | principal |
| `random_last()` | El último resultado de `random(a, b)` o `%random_a_b%` en este hilo (nulo si no hubo) | cualquiera |
- Las de «principal» lanzan `ExprException` fuera del hilo principal, como `block_at` (015).
- `random()` y `Placeholders` guardan su resultado en un `ThreadLocal` (`Functions.lastRandom`).
- Propiedad `Block.states`: lo de dentro de los corchetes de `getBlockData().getAsString()` (vacío si no hay).

### 1.3 Importador (`importer/ConditionalEventsImporter.java`)
| CE | OmniEvents |
|---|---|
| `%block_data_at_x_y_z_m%` | `block_at('m', x, y, z).states` |
| `%is_nearby_x_y_z_m_r%` | `near(player, location('m', x, y, z), r)` |
| `%playerblock_below_n%` / `_above_n%` | `block_at(player.world, player.x, player.y - n, player.z).type` (`+ n`) |
| `%playerblock_inside%` | `player.location.block.type` |
| `%player_is_outside%` | `is_outside()` |
| `%block_below_n%` / `%block_above_n%` | `block_at(block.world, block.x, block.y - n, block.z).type` (`+ n`) |
| `%world_time_<m>%` | `world('m').time` (`%world_time_{player_world}%` como hoy) |
| `%damage%` · `%original_damage%` | `event.final_damage` · `event.damage` |
| `%block_data%` | `block.states` |
| `%args_substring_a-b%` | `join(slice(args, a - 1, b), ' ')` |
| `%player_location_<c>_<f>_<s>_<u>%` | `relative(player, f, s, u).<c>` |
| `%player_looking_block_d%` · `_entity_d%` | `looking_block(d).type` · `looking_entity(d).type` |
| `%random_last%` | `random_last()` |
| `%select_type%` (item_select) | `'SELECT'`; si una condición del evento compara `%select_type%` con `DESELECT`, `'DESELECT'` y las variables de ítem del evento usan `previous_item` |
| `%movement_type%` (player_offhand) | `(item.is_air && offhand.is_air ? 'NOTHING' : item.is_air ? 'PLACE' : offhand.is_air ? 'RECOVER' : 'SWAP')` (en `player_swap_hands`, `item` va a la mano principal y `offhand` a la secundaria) |
| `actionbar: t;n` | `actionbar: t;n` |
| `give_potion_effect: e;t;n;p;i` | `effect: e;segundos;n;p;i` |
| `firework … shot_direction:…` | igual (sin nota) |
| `protocollib_receive_message`, `player_receive_chat` | `player_receive_chat`; `%json_message%` → `json`, `%normal_message%` → `message_legacy`, `%normal_message_without_color_codes%` → `message` |
- Las variables con números (`n`, `x`…) aceptan `{variable}` anidada como `%block_at_…%` (015): `piece()`.

### 1.4 `OmniRuleFireEvent` (`api/OmniRuleFireEvent.java`, `rule/RuleManager.java`)
- `Event(async)` con `rule()`, `trigger()`, `player()` (nulo) y `group()` (`"actions"`). `RuleManager.run`, tras
  `registerUsage` y antes de `execute(rule.actions…)`, si `!sim` y `getHandlerList().getRegisteredListeners().length > 0`:
  `Bukkit.getPluginManager().callEvent(new OmniRuleFireEvent(!Bukkit.isPrimaryThread(), …))`. Un error de un oyente
  ajeno lo atrapa Bukkit (no corta la regla).

### 1.5 ProtocolLib (`hook/ChatPackets.java`, `trigger/ReceivedChatEvent.java`, `trigger/Triggers.java`, `OmniEvents.java`)
- `ReceivedChatEvent` es un evento propio (`Event(true)`, `Cancellable`) con `player`, `message`, `legacy`, `json`,
  `type`. El disparador `player_receive_chat` es un `TriggerType.event` normal sobre esa clase (asíncrono): el
  `ListenerManager`, el `cancel`, el paso al hilo principal y `tests:` funcionan sin código nuevo. La clase no toca
  ProtocolLib: el disparador existe aunque no esté.
- `ChatPackets` (solo se carga si ProtocolLib está: `detectIntegrations`, con `LinkageError` atrapado) registra un
  `PacketAdapter` (prioridad `HIGHEST`) para `SYSTEM_CHAT`, `DISGUISED_CHAT` y `CHAT`. Tras cada
  `listeners.register(...)`, `OmniEvents` llama `chatPackets.listen(listeners.listens(ReceivedChatEvent.class))`:
  agrega o quita el adaptador (III.1).
- En `onPacketSending` (hilo de red): `SYSTEM_CHAT` con `getBooleans().readSafely(0) == true` (overlay) se ignora; el
  texto sale de `getChatComponents()` (el primero no nulo) y, si no hay, de un `Component` de Adventure en
  `getModifier()` (Paper); de `PLAYER_CHAT`, el contenido sin firmar si existe. JSON → `GsonComponentSerializer` →
  texto plano y `LegacyComponentSerializer.legacyAmpersand()`. Tope por jugador: contador por segundo
  (`ConcurrentHashMap<UUID, long[]>`); al pasar `protocollib.max-messages-per-second` no se dispara y se avisa una vez
  por minuto. Luego `callEvent(new ReceivedChatEvent(...))` y, si quedó cancelado, `event.setCancelled(true)`.
- Errores al leer un paquete se atrapan y se avisan una vez (no deben romper la conexión del jugador).
- `plugin.yml`: `softdepend` suma `ProtocolLib`. `RuleLoader.needs("ProtocolLib", …)` avisa si falta.
- Gradle: `compileOnly("net.dmulloy2:ProtocolLib:5.4.0") { isTransitive = false }` (Maven Central).

## 2. Contrato de configuración
```yaml
aviso_largo:
  trigger: command
  command: aviso
  actions:
    - actionbar: "<gold>Evento en 1 minuto;120"      # 6 s en la barra de acción
    - effect: "NIGHT_VISION;60;1;false;false"       # sin partículas ni ícono
    - firework: "colors:YELLOW type:BALL power:0 shot_direction:-1,1;1,1;-1,1;0.5-1"

ocultar_publicidad:
  trigger: player_receive_chat                      # necesita ProtocolLib
  conditions: ["lower(message) contains 'compra en'"]
  actions:
    - cancel
    - log: "oculto a {player.name}: {message}"

cueva:
  trigger: state
  check_every: 2s
  when: "!is_outside() && block_at(player.world, player.x, player.y - 1, player.z).type == 'STONE'"
  on_enter: [ {actionbar: "Estás en una cueva"} ]
```
Funciones: `world`, `location`, `near`, `slice`, `relative`, `looking_block`, `looking_entity`, `is_outside`,
`random_last`. Propiedad `block.states`. Disparador `player_receive_chat` (`message`, `message_legacy`, `json`, `type`).
`config.yml`: `protocollib.max-messages-per-second: 20`. API: `OmniRuleFireEvent`.

Compatibilidad (IV.5): todo es nuevo u opcional. Un `actionbar` que hoy termina en `;<número>` pasa a ser una duración
(antes se mostraba con el `;123`). `firework` con `shot_direction`, antes error de carga, ahora carga. El importador da
otra traducción para `%damage%` y `%block_data%` (lo que ya se importó no cambia).

## 3. Hilos y rendimiento
- El adaptador de ProtocolLib solo existe con reglas `player_receive_chat` habilitadas; sin ProtocolLib nada se carga.
- `player_receive_chat` corre en el hilo de red: condiciones y acciones seguras ahí, el resto al hilo principal (III.3);
  `block_at`, `looking_*` e `is_outside` avisan con error de expresión si se usan ahí.
- El tope por jugador y segundo acota una regla que se responde a sí misma (III.4).
- `actionbar` con duración: una tarea por envío, que termina sola (tope: la duración).
- `OmniRuleFireEvent` no se crea sin oyentes.

## 4. Persistencia
Sin cambios.

## 5. Amenazas (STRIDE)
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| Manipulación: el texto recibido (chat de otros, nombres) en `message`/`json` interpolado en comandos o mensajes | Inyección de etiquetas o de comandos | Valores no confiables: se escapan al interpolar como el chat (II.2); `json` es solo texto |
| Denegación: regla de `player_receive_chat` que manda un mensaje que la vuelve a disparar | Bucle de paquetes, servidor saturado | Tope por jugador y segundo (`max-messages-per-second`) con aviso; documentado |
| Denegación: un paquete con forma inesperada (otra versión de ProtocolLib o de Minecraft) | Excepción en el hilo de red, desconexión | Todo atrapado; aviso una vez; el paquete sigue sin cambios |
| Elevación / ejecución de código: ProtocolLib como dependencia | Un jar de terceros con acceso a la red | Opcional (`softdepend`); el servidor de prueba lo baja solo de la fuente oficial con sha256 fijado; ADR 0004 |
| Divulgación: `OmniRuleFireEvent` muestra a otros plugins qué reglas corren | Información interna | Solo nombres de reglas y el jugador, que cualquier plugin del servidor ya ve |
| Repudio: `cancel` oculta mensajes a un jugador | Mensajes del sistema perdidos sin rastro | La regla puede registrar con `log`; documentado en el README |

## 6. Pruebas
- Unitarias: `ShotDirection.parse` (rangos y errores); `actionbar` con duración y `effect` con ícono cargan; funciones
  nuevas en el evaluador (`slice`, `near`, `location`, `world`, `random_last`, `relative` con giros); `block.states`;
  `player_receive_chat` en `tests:` con `given` y `cancel`; `OmniRuleFireEvent` (campos); importador con cada variable,
  `to_range` de tres valores, `actionbar`, ícono, `shot_direction`, `select_type`, `movement_type` y el evento de
  ProtocolLib, sin notas «sin equivalente»; F-xx: `%damage%` y `%block_data%` (fallan con el importador de la 1.10.0).
- En el juego (3a), con ProtocolLib en el servidor de prueba: una regla `player_receive_chat` que reacciona a un
  mensaje del sistema (el bot lo recibe y llega la respuesta) y otra que cancela uno (el bot no lo ve); `actionbar` con
  duración (el bot ve la barra de nuevo pasados 2 s).

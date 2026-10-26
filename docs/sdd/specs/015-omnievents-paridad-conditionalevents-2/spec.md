# Spec 015 — OmniEvents: paridad con ConditionalEvents, segunda parte

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 001, 010, 014 ·
**Origen:** la spec 014 comparó OmniEvents fila por fila con el jar de ConditionalEvents (CE) 4.65.1 y cerró el
núcleo; el dueño pidió que lo marcado «parcial» o «falta» que quedó fuera vaya en una spec propia (roadmap, «paridad con
ConditionalEvents, segunda parte»). La 003 sigue reservada; esta va como 015.

## Problema
Tras la 014, un servidor que migra de CE con `/omni import` todavía encuentra «REVISAR» en cosas de uso diario: dar un
ítem en una casilla concreta o con banderas, quitar solo los ítems con cierto nombre, invocar un mob con equipo,
partículas y sonidos en una ubicación, cortar las esperas de una regla, autocompletado propio, `%random_player%`,
`%block_at_…%`. Cada una es poca cosa; juntas son la diferencia entre «importa y funciona» e «importa y hay que
reescribir».

## Fuente
El mismo jar de la 014 (`tests-e2e/.cache/ce/ConditionalEvents-4.65.1.jar`, sha256
`15847ebb0e17dc3ed95409b1b16b1e69948ce3ac62837f68c551961eec307d92`), leído con `javap` (oyentes, `ActionUtils`,
`ItemUtils`, `PlayerUtils`, `MainCommand`). Lo que el jar dice y la tabla de la 014 no sabía:
- **Mending no existe en CE:** `onItemRepair` solo mira el yunque (ni siquiera la afiladora, que OmniEvents ya suma).
- **`player_change_air` está declarado pero nunca se dispara:** `EventType.PLAYER_CHANGE_AIR` existe y ningún oyente lo usa.
- **`particle`** lee `effect`, `amount`, `offset`, `speed`, `location` y `force` (sin `for_player`); **`summon`** lee
  `location`, `entity`, `custom_name`, `health`, `amount`, `equipment` y `hand_equipment` (sin `scale`).
- **`tab_complete`** es `modo;a,b,c` con `set` (deja solo los de la lista que empiezan por la palabra a medio escribir,
  ordenados), `remove` y `clear`; el evento da `%main_command%`, `%args_length%` y `%arg_N%`.
- **`remove_item_slot`** es `CASILLA;cantidad` con `HAND`, `OFF_HAND`, `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS` o un número.
- **`set_item`** cambia el ítem pescado (`PlayerFishEvent.getCaught()`); **`keep_items`** es `items`, `xp` o `all`.

## Historias
**H1 — Ítems al estilo CE.** Como *administrador*, quiero dar y quitar ítems con el detalle que permite CE.
- CA1: `give_item` en forma de mapa acepta `slot` (número de casilla 0–40 o `HAND`, `OFF_HAND`, `HELMET`, `CHESTPLATE`,
  `LEGGINGS`, `BOOTS`), `slot_replace` (sin él, si la casilla está ocupada el ítem va al inventario como siempre; con
  `true`, reemplaza), `flags` (lista o texto con comas de `ItemFlag`: `HIDE_ENCHANTS`…), `durability` (daño, como el
  `durability` de CE), `item_model` (clave `espacio:modelo`, 1.21.4+), `skull_owner` (nombre de jugador para una
  cabeza) y `skull_texture` (textura en base64). Un nombre de bandera desconocido es error al cargar con «¿quisiste
  decir?».
- CA2: `remove_item: "MATERIAL;cantidad;name:<nombre>;lorecontains:<texto>"` quita solo los ítems de ese material cuyo
  nombre (sin colores) es igual y/o con alguna línea de lore que contiene el texto (sin colores). Sin filtros, como hoy.
- CA3: `remove_item_slot: "CASILLA;cantidad"` quita esa cantidad (sin cantidad, toda la pila) de la casilla, con los
  nombres de CA1. Una casilla desconocida es error al cargar si es constante.
- CA4: `set_caught_item` (en `player_fish`) cambia el ítem pescado por uno con el formato de `give_item`; fuera de la
  pesca, o si lo pescado no es un ítem, es error de la ejecución.

**H2 — Mundo al estilo CE.** Como *administrador*, quiero invocar, poner bloques, partículas y sonidos con las claves de CE.
- CA1: `summon` en forma de mapa: `entity` (o `type`), `location`, `name` (MiniMessage, visible), `health` (vida máxima
  y actual), `amount` (1 por defecto, tope 50), `scale` (atributo de escala) y `equipment` (`casco,peto,pantalones,botas`
  con materiales o `none`) y `hand_equipment` (`principal,secundaria`). El equipo no se suelta al morir. La forma de
  texto `TIPO[;mundo;x;y;z]` sigue igual.
- CA2: `set_block` acepta datos de bloque: `set_block: "~;~;~;OAK_STAIRS[facing=north]"`. Datos inválidos son error
  de la ejecución (como un material desconocido).
- CA3: `particle` acepta las claves `location` (`mundo;x;y;z`, `~` relativo), `force: true` (se ve de lejos) y
  `for_player: true` (solo la ve el jugador).
- CA4: `sound` acepta `location` (el sonido sale de ahí y lo oyen los cercanos) y `stop_sound` acepta claves de
  paquete de recursos (`minecraft:block.note_block.pling`, `mi_pack:musica`).
- CA5: `keep_inventory: items|xp|all` (sin valor o con una expresión verdadera, `all` como hoy).

**H3 — Mensajes, funciones y placeholders.** Como *administrador*, quiero lo que CE da en variables, en expresiones.
- CA1: `json_message` es una acción propia: el valor es JSON de texto de Minecraft (clic, hover…); se convierte al
  cargar (JSON inválido es error al cargar con la ruta) y los `{expresiones}` dentro de los textos se resuelven y se
  escapan como en `message`.
- CA2: `pick(lista)` o `pick(a, b, …)`: un elemento al azar (nulo si la lista está vacía). `pick(players()).name`
  equivale a `%random_player%`; `pick(['a','b'])` a `%randomword_…%`.
- CA3: `block_at(mundo, x, y, z)`: el bloque (`.type`, `.data`…) o nulo si el mundo no existe o el chunk no está
  cargado (no se carga un chunk por una condición). `mundo` es un nombre o un mundo. Fuera del hilo principal
  (`player_chat`) es error de la expresión.
- CA4: `is_number(x)`: verdadero si `x` es un número o un texto numérico.
- CA5: `%omni_enabled_<regla>%` (PlaceholderAPI) vale `true` o `false`.

**H4 — Comandos al estilo CE.** Como *administrador*, quiero cortar las esperas de una regla y comandos silenciosos.
- CA1: `/omni interrupt <regla|*> [jugador]` corta las ejecuciones de esa regla (o de todas) que están esperando un
  `delay` o `wait_ticks`, del jugador o de todos; las acciones que faltaban no corren y el mensaje dice cuántas cortó.
  Permiso `omnievents.command.interrupt` (`op`).
- CA2: `-s` en cualquier lugar de `/omni call`, `enable`, `disable` e `interrupt` no manda mensaje (para usarlos desde
  `console:` en otras reglas).

**H5 — Disparadores que faltaban.** Como *administrador*, quiero reaccionar a estadísticas, aire, autocompletado y Mending.
- CA1: `trigger: player_statistic`: sube una estadística del jugador (`statistic`, `previous_value`, `new_value`,
  `material` y `entity_type` cuando la estadística los tiene, si no nulos). Cancelable.
- CA2: `trigger: player_air_change`: cambia el aire de un jugador (`old_air`, `new_air`, `max_air`, en ticks).
  Cancelable. (CE lo declara sin dispararlo; aquí sí funciona.)
- CA3: `trigger: player_tab_complete`: un jugador pide autocompletar un comando de un plugin (`buffer`, `cmd`, `args`,
  `last` —la palabra a medio escribir—, `completions`). La acción `tab_complete: "modo;a,b,c"` con `set`, `add`,
  `remove` y `clear` cambia las sugerencias (`set` y `add` solo agregan las que empiezan por `last`, y `set` ordena,
  como CE); fuera de ese disparador es error de la ejecución.
- CA4: `trigger: item_mend`: Mending repara un ítem del jugador con un orbe (`item`, `amount` —durabilidad que
  repara—, `orb`). Cancelable (el orbe da experiencia en vez de reparar).

**H6 — Importador y documentación.** Como *administrador* que migra, quiero que `/omni import` traduzca todo lo nuevo.
- CA1: El importador traduce `player_statistic`, `player_change_air`, `player_tab_complete` (y `%main_command%`,
  `%args_length%`, `%arg_N%`, `%statistic_name%`, `%previous_value%`, `%new_value%`), `json_message` (como acción
  propia), `remove_item` con `name:`/`lorecontains:`, `remove_item_slot`, `give_item`/`drop_item` con `slot`,
  `slot_replace`, `flags`, `durability`, `item_model`, `skull_owner`, `skull_texture`, `summon` con todas sus claves,
  `particle` con `location` y `force`, `playsound` con ubicación, `stopsound_resource_pack`, `set_block` con
  `block_data`, `set_item`, `keep_items`, `tab_complete`, y `%random_player%`, `%randomword_a-b%`, `%is_number_x%`,
  `%block_at_x_y_z_mundo%`.
- CA2: Siguen como «REVISAR» con nota solo lo marcado **no** en la 014, `remove_item` con `datavalue:` (legado de 1.12), `vector` y `custom_model_component_data_*`.
- CA3: Todo lo nuevo está en su registro (esquema y referencia regenerados), en los ejemplos con `tests:` y en el README.

## Fuera de alcance
- Lo marcado **no** en la 014: lectura de paquetes, `player_command_as_op`, DiscordSRV.
- `custom_model_component_data_*` de CE (componentes de 1.21.4 sin API estable en Paper 1.21.4): queda `model`.
- `vector` de CE (está en el jar pero sin documentar: no se sabe su forma): el importador lo deja como «REVISAR» con una nota (`velocity` o `launch`).

## Decisiones (CLARIFY)
| # | Pregunta | Decisión |
|---|---|---|
| 1 | ¿Alcance? (estimado frente a la 014 = 1 sesión: funciones y comandos 0.3, claves de acciones 0.6, disparadores 0.4, importador 0.2; todo ≈ 1.5) | Todo (dueño), con escenarios en el juego solo para lo más visible |
| 2 | ¿Mending dentro de `item_repair` o aparte? | El dueño pidió mirar el jar: CE no repara por Mending. Va como disparador aparte `item_mend`: es otro evento (cada orbe) y en `item_repair` haría correr las reglas del yunque con cada orbe |
| 3 | ¿`for_player` y `scale`, que el jar no tiene? | Se agregan (la wiki los documenta y cuestan poco) |
| 4 | ¿`block_at` carga chunks? | No: nulo si el chunk no está cargado (constitución III) |

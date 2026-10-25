# Plan 008 — Diseño técnico

## 1. Arquitectura

### 1.1 Carga (`RuleLoader`)
- Disparador nuevo `state` (`TriggerType.Kind.STATE`, grupo «servidor», raíces `player`, `world`, `location`).
- Para una regla `state` el cargador lee `when` (mismo analizador de condiciones de la 006: textos o mapas con
  `expr`/`any`; un `deny` dentro de `when` avisa y se ignora), `on_enter` (va a `Rule.actions`, para que el flujo de
  siempre aplique permiso, límite y enfriamiento) y `on_exit`. `conditions`, `actions` y `else` son error con la
  opción que va. Falta `when`, o faltan `on_enter` y `on_exit` a la vez → error.
- `check_every` (duración ≥ 1 tick), `watch` (nombres de disparadores con evento: `player_damage`…; uno desconocido
  → error con «¿quisiste decir?»), `per_player` (por defecto `true`) e `initial` (`enter` por defecto, o `silent`).
- Con las condiciones compiladas, `StateDeps.analyze` deduce las dependencias (§1.2). Errores de carga: azar,
  `in_state` de sí mismo, partes no deducibles sin `check_every`, y en un estado del servidor cualquier cosa del
  jugador. `worlds` agrega la dependencia de movimiento (cambio de mundo).
- El resultado va en `Rule.state` (`StateSpec`, `null` en las demás reglas): condiciones, `onExit`, dependencias,
  `checkEveryTicks`, `perPlayer`, `initialSilent`, `watch` y una **huella** (`when` + `worlds` + `per_player`) para
  conservar el estado al recargar.

### 1.2 Deducción de dependencias (`trigger/StateDeps`, puro y probado sin servidor)
Recorre el árbol de cada expresión. Para una cadena de propiedades lo que decide es **la primera propiedad** tras
la raíz (`player.location.block.type` → movimiento). Las claves se normalizan como en `Properties` (sin `_`, en
minúsculas).

| Se usa | Fuente | Eventos de Bukkit (en `StateService`) |
|---|---|---|
| `player.health`, `max_health`, `absorption_amount`, `alive`, `dead` | `HEALTH` | `EntityDamageEvent`, `EntityRegainHealthEvent`, `PlayerRespawnEvent` |
| `player.food`, `food_level`, `saturation`, `exhaustion` | `FOOD` | `FoodLevelChangeEvent`, `PlayerRespawnEvent` |
| `location`, `world` (y `world.name`), `player.location`, `x`/`y`/`z`, `block_*`, `world`, `in_water`, `swimming`, `on_ground`, `gliding`, `eye_location`; `in_area()`, `distance()` | `MOVE` | `PlayerMoveEvent` (solo si cambia de bloque), `PlayerTeleportEvent`, `PlayerChangedWorldEvent`, `PlayerRespawnEvent` |
| `player.level`, `exp`, `total_experience` | `XP` | `PlayerExpChangeEvent`, `PlayerLevelChangeEvent` |
| `player.game_mode` | `GAMEMODE` | `PlayerGameModeChangeEvent` |
| `player.sneaking` / `sprinting` / `flying`, `allow_flight` | `SNEAK` / `SPRINT` / `FLIGHT` | los `PlayerToggle…Event` |
| `player.helmet`, `chestplate`, `leggings`, `boots` | `ARMOR` | `PlayerArmorChangeEvent` |
| `player.item`, `offhand`, `inventory`, `empty_slots`, `item_in_hand`; `has_item()` | `INVENTORY` | ítem en mano, cambio de manos, clic/arrastre/cierre de inventario, recoger, tirar, consumir, romper, colocar bloque, reaparecer |
| `online`, `players()`, `online(nombre)` | `ONLINE` | `PlayerJoinEvent`, `PlayerQuitEvent` |
| `world.storm`, `world.thunder` | `WEATHER` | `WeatherChangeEvent`, `ThunderChangeEvent` |
| `pvar.x` / `gvar.x` | variable `x` | aviso interno al asignar (acciones `set`/`del`, `/omni var set|del`) |
| `in_state('otra')` | estado `otra` | cuando `otra` cambia |
| `player.name`, `uuid`, `first_join`, `locale`, literales, `server.name`… | — (constante) | — |
| placeholders, `perm()`, `papi()`, `now()`/`hour()`/…, `cooldown()`, `uses()`, `pvar_of()`, `player('x')`, `world.time`/`day`, `player.op`/`ping`, métodos, funciones de otros plugins, cualquier otra propiedad | **no deducible** | solo con `check_every` |
| `random()`, `randomf()`, `chance()` | **error** | — |

Las funciones puras del lenguaje (`min`, `abs`, `lower`, `len`, `if`…) no agregan nada: se recorren sus argumentos.
Una función desconocida para la tabla (registrada por otro plugin) es no deducible.

### 1.3 Ejecución (`trigger/StateService`)
- **Oyentes:** un `Listener` propio registra con `registerEvent` (prioridad `MONITOR`, `ignoreCancelled = true`)
  solo las clases que piden las fuentes de las reglas `state` habilitadas, más `watch` (la clase del disparador
  nombrado y su `bind()` para sacar el jugador), y `PlayerJoinEvent`/`PlayerQuitEvent` si hay algún estado por
  jugador. Sin reglas `state`, no hay oyente ni tarea (constitución III.1).
- **Marcar y revisar al final del tick:** un evento solo **marca** (regla, jugador) en un conjunto; una tarea de
  un tick (`runTask`, programada cuando el conjunto pasa de vacío a no vacío) revisa cada par **una vez**. Así el
  cambio ya se aplicó (el daño restó vida) y cien movimientos en un tick cuestan una evaluación. Los eventos del
  mundo (`ONLINE`, `WEATHER`) y las `gvar` marcan a todos los conectados. `check_every` es una tarea por regla que
  marca a todos.
- **Revisión:** contexto `{player, world, location}` (como un `timer`); valor = en `worlds` (si tiene) y todas las
  condiciones de `when`. Un error de evaluación cuenta como falso, suma un error a la regla y avisa en la consola
  como mucho una vez por minuto por regla.
- **Tabla por regla:** `Map<UUID, Entrada(desde, pareja)>` (presente = verdadero) y el conjunto de jugadores ya
  evaluados. Entrar: guarda la entrada y corre `RuleManager.run` con `on_enter` (permiso, bypass, límite,
  enfriamiento, estadísticas, presupuesto y traza de siempre); `pareja` = si corrió. Salir: si `pareja`, corre
  `on_exit` con `var.state_seconds`. Un estado del servidor usa la clave `UUID(0, 0)` y un contexto sin jugador.
- **Ciclo de vida:** entrar al servidor → revisión inicial (con `initial: silent`, sin acciones y sin pareja);
  salir → se borra al jugador de todas las tablas. Al recargar o con `/omni enable|disable`, `register(reglas)`
  conserva la tabla de cada regla con la misma huella; una regla nueva o cambiada se evalúa en el acto para los
  conectados, sin acciones. Una regla deshabilitada pierde su tabla. Al apagar: se desregistra todo.
- **Variables:** `ExecContext.setVar` y `/omni var set|del` llaman a `StateService.varChanged(ámbito, jugador,
  nombre)`, que busca en un índice `nombre → reglas` (sin costo si ninguna regla `state` usa esa variable).
- **`in_state`:** función registrada en `Functions` con `c.service("in_state", regla)`; `ExecContext` la resuelve
  con `StateService.isIn(regla, jugador)`. Al cambiar un estado, se marcan las reglas que dependen de él.
  Placeholder `%omni_state_<regla>%` en `PapiHook`.

### 1.4 Herramientas
- `/omni debug <regla>`: solo las revisiones que **cambian** el estado se envían (`✔ entra en el estado` / `✘ sale
  del estado` con los valores de `when`), seguidas de la rama (`→ actions` de `on_enter`, `→ on_exit`).
- `/omni check <regla> [jugador]`: valor actual de `when` con valores, estado guardado (`dentro desde 12s`/`fuera`),
  y «correría: on_enter / on_exit / nada (sin cambio)». Si entraría, sigue con permiso, límite y enfriamiento de
  siempre. `/omni run` y `--simular` corren o simulan `on_enter` (ya son las `actions` de la regla).
- `/omni info <regla>`: dependencias (`depende de: vida (player_damage, player_regain_health…), pvar.racha`,
  `check_every`) y, si hay, «no deducible: …».
- `/omni state <regla>` (nuevo, `omnievents.command.state`): jugadores dentro y desde cuándo, o el estado del
  servidor.
- `/omni stats`: por regla `state`, revisiones y cambios; total de oyentes de estado (cuentan en la compuerta de
  recarga). `/omni cpu`: la revisión se mide como parte `when` de la regla cuando toca muestreo.

### 1.5 Documentación (spec 007)
`Triggers`: `.doc("…")` de `state`. `RuleOptions`: `when` (CONDITIONS), `on_enter`, `on_exit` (ACTIONS),
`check_every` (DURATION), `watch` (TEXTS), `initial` (ENUM) para `state`, y `per_player` pasa a «timer, state».
`SchemaGenerator`: `trigger: state` requiere `when`. `Functions`: firma y descripción de `in_state(regla)`.
Se regenera con `./gradlew :omnievents:schema`.

## 2. Contrato de configuración
```yaml
vida_baja:
  trigger: state
  when: "player.health <= 6"            # o una lista de condiciones (todas)
  on_enter:
    - title: "<red>¡Peligro!;Busca refugio"
  on_exit:
    - actionbar: "<green>A salvo tras {var.state_seconds}s"
  cooldown: 30s                         # opcional: frena on_enter (y su on_exit)
  initial: enter                        # enter (por defecto) | silent

en_la_plaza:
  trigger: state
  when: "in_area('world', 0, 60, 0, 20, 80, 20)"
  on_enter: [message: "Entraste a la plaza"]
  on_exit:  [message: "Saliste de la plaza"]

vip_por_permiso:
  trigger: state
  when: "perm('servidor.vip')"          # no deducible: necesita check_every
  check_every: 5s
  on_enter: [message: "<gold>Ya eres VIP"]

evento_activo:
  trigger: state
  per_player: false
  when: "gvar.evento == true && online >= 2"
  on_enter: [message: "<gold>¡Empieza el evento!", to: all]
  on_exit:  [message: "<gray>Terminó el evento", to: all]
```
- `watch: [player_interact]` agrega eventos a los deducidos. `in_state('vida_baja')` se usa en cualquier regla.
- Compatibilidad: ninguna opción existente cambia de significado; las reglas de las versiones anteriores cargan
  igual (constitución IV.5). Nada se guarda en disco.

## 3. STRIDE
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| **S**uplantación | Un jugador provoca `on_enter` de otro | El estado es por UUID; las acciones corren con el jugador cuyo estado cambió |
| **T**ampering | Texto del jugador en `when` (`player.name`, mensajes) | Se evalúa con el lenguaje cerrado (II.1); interpolado en acciones, se escapa como siempre (II.2) |
| **R**epudio | No se sabe por qué corrió un `on_enter` | `/omni debug` con los valores, `/omni check` y `/omni state` |
| **I**nformación | `/omni state` lista jugadores | Permiso propio `op` por defecto (II.3) |
| **D**enegación de servicio | Un estado sobre `player.location` con cientos de jugadores; un estado que oscila cada tick; `check_every` muy corto | Una revisión por (regla, jugador) por tick; movimiento solo al cambiar de bloque; `check_every` ≥ 1 tick y declarado a la vista; `/omni cpu`, `budget` y revisiones en `/omni stats`; un ciclo entre estados con `in_state` se reevalúa como mucho una vez por tick; azar prohibido |
| **E**levación de privilegio | `/omni state` o `watch` usados por un jugador | Subcomando con permiso `op`; `watch` solo se declara en el YAML del dueño |

Memoria: una entrada por (regla, jugador dentro del estado) y un conjunto de evaluados por regla, que se vacían al
salir el jugador. Sin E/S ni red (III.2, II.4).

## 4. Pruebas
- **Unitarias** (`trigger/StateDepsTest`, `rule/EstadoCargaTest`): la tabla de §1.2 caso por caso; `in_state` de sí
  mismo; azar; no deducible con y sin `check_every`; estado del servidor con `player`; errores de forma
  (`actions`/`conditions`/`else`, sin `when`, sin `on_enter` ni `on_exit`, `watch` desconocido, `initial`
  inválido); la lógica de transición pura (`StateTable`: entra, sale con y sin pareja, silencio, olvidar, conservar
  por huella); `SchemaTest` cubre las opciones nuevas.
- **En el juego** (`tests-e2e/omnievents/suite.mjs`, reglas en `reglas/e2e.yml`): vida (daño → `on_enter` una sola
  vez aunque siga el daño; curación → `on_exit` con segundos), zona (teletransporte dentro y fuera), `pvar`
  (`/omni var set`), `check_every` (`world.day` con `time set`), estado del servidor (`gvar`) con `/omni state` e
  `in_state`, pareja con enfriamiento.
- **Compuerta 2:** el ejemplo `estados.yml` valida sin errores y dos `reload` dejan los mismos oyentes y tareas.

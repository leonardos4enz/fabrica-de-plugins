`item_repair` (yunque o afiladora: `repair_type`, `rename_text`, `level_cost`, `base_item`, `addition`) `item_mend` (Mending: `amount`, `orb`) |`player_portal` `player_kick` `player_resource_pack` `player_statistic` (`statistic`, `previous_value`, `new_value`) `player_air_change` (`old_air`, `new_air`, `max_air`) `player_tab_complete` (`buffer`, `cmd`, `args`, `last`, `completions`; la acción `tab_complete` las cambia) |`keep_inventory` (`items`, `xp` o `all`) `cancel_drops` `deny_join` `tab_complete` (`set|add|remove|clear;a,b,c`, en `player_tab_complete`) |`sound` (`SONIDO;vol;tono`, `location:` opcional) `stop_sound` (también claves de paquete de recursos) `console_message` `log` || [Mensajes](../../docs/omnievents/galeria.md#acciones--mensajes) | `message` `centered_message` (cada línea centrada en el chat) `json_message` (JSON de texto con clic y hover)# OmniEvents

Eventos condicionales para **Paper 1.21.4 y 26.1** (ver [Compatibilidad](#compatibilidad)): «cuando pase X, si se cumple Y, haz Z», declarado en YAML.
Es la versión propia de la fábrica de *ConditionalEvents*, con un lenguaje de expresiones real, control de flujo,
variables persistentes y herramientas de diagnóstico. Specs: [001](../../docs/sdd/specs/001-omnievents-nucleo/spec.md) (núcleo) y
[006](../../docs/sdd/specs/006-omnievents-herramientas-de-autor/spec.md) (herramientas de autor),
[007](../../docs/sdd/specs/007-omnievents-esquema-y-referencia/spec.md) (esquema para el editor y referencia generada),
[008](../../docs/sdd/specs/008-omnievents-disparador-de-estado/spec.md) (disparador de estado, sin sondeo),
[009](../../docs/sdd/specs/009-omnievents-variables-caducidad-y-objetos/spec.md) (variables que caducan y variables en bloques, entidades e ítems),
[010](../../docs/sdd/specs/010-omnievents-pruebas-de-reglas/spec.md) (pruebas de reglas en el YAML: `tests:` y `/omni test`),
[011](../../docs/sdd/specs/011-omnievents-paquetes-plantillas-version/spec.md) (paquetes, plantillas y versión),
[012](../../docs/sdd/specs/012-omnievents-integraciones-con-costos/spec.md) (costos todo o nada, Vault, WorldGuard y Citizens),
[013](../../docs/sdd/specs/013-omnievents-menus-y-formularios-bedrock/spec.md) (menús de inventario y formularios de Bedrock),
[014](../../docs/sdd/specs/014-omnievents-paridad-conditionalevents/spec.md) (paridad con ConditionalEvents) y
[015](../../docs/sdd/specs/015-omnievents-paridad-conditionalevents-2/spec.md) (paridad con ConditionalEvents, segunda parte).

**[Referencia completa](../../docs/omnievents/referencia.md)**: cada opción, disparador, acción y función, generada del
código (nunca queda desactualizada). **[Autocompletado en VS Code](#editor-autocompletado-y-errores-en-vs-code)** con
el esquema JSON.

![El ejemplo bienvenida: anuncio de primera entrada en el chat y el título «Bienvenido · 2 jugadores en línea»](../../docs/img/omnievents/ejemplo-bienvenida.png)

**[Galería](../../docs/omnievents/galeria.md)**: una imagen por disparador, acción, subcomando y ejemplo, generada en el juego. No son capturas del
cliente oficial: son un render de lo que recibió un cliente real (un bot) del servidor, con la fuente y el HUD de
Minecraft 1.21.4 ([spec 004](../../docs/sdd/specs/004-fabrica-capturas-en-juego/spec.md)).

## Compatibilidad
| Servidor | Estado | Evidencia |
|---|---|---|
| **Paper 1.21.4** (build 232, Java 24) | Verificada (1.10.0) | Compuertas 1, 2 y 3a: 191 unitarias, servidor real y 55/55 escenarios en el juego dos veces (2026-10-25, specs [001](../../docs/sdd/specs/001-omnievents-nucleo/verify.md), [002](../../docs/sdd/specs/002-fabrica-pruebas-en-juego/verify.md), [005](../../docs/sdd/specs/005-omnievents-comandos-sin-resync/verify.md), [006](../../docs/sdd/specs/006-omnievents-herramientas-de-autor/verify.md), [007](../../docs/sdd/specs/007-omnievents-esquema-y-referencia/verify.md), [008](../../docs/sdd/specs/008-omnievents-disparador-de-estado/verify.md), [009](../../docs/sdd/specs/009-omnievents-variables-caducidad-y-objetos/verify.md), [010](../../docs/sdd/specs/010-omnievents-pruebas-de-reglas/verify.md), [011](../../docs/sdd/specs/011-omnievents-paquetes-plantillas-version/verify.md), [012](../../docs/sdd/specs/012-omnievents-integraciones-con-costos/verify.md), [013](../../docs/sdd/specs/013-omnievents-menus-y-formularios-bedrock/verify.md), [014](../../docs/sdd/specs/014-omnievents-paridad-conditionalevents/verify.md) y [015](../../docs/sdd/specs/015-omnievents-paridad-conditionalevents-2/verify.md)) |
| **Paper 26.1.2** (build 74, Java 25) | Verificada (1.10.0) | 55/55 escenarios en el juego dos veces con el mismo jar (2026-10-25, specs [002](../../docs/sdd/specs/002-fabrica-pruebas-en-juego/verify.md), [005](../../docs/sdd/specs/005-omnievents-comandos-sin-resync/verify.md), [006](../../docs/sdd/specs/006-omnievents-herramientas-de-autor/verify.md), [007](../../docs/sdd/specs/007-omnievents-esquema-y-referencia/verify.md), [008](../../docs/sdd/specs/008-omnievents-disparador-de-estado/verify.md), [009](../../docs/sdd/specs/009-omnievents-variables-caducidad-y-objetos/verify.md), [010](../../docs/sdd/specs/010-omnievents-pruebas-de-reglas/verify.md), [011](../../docs/sdd/specs/011-omnievents-paquetes-plantillas-version/verify.md), [012](../../docs/sdd/specs/012-omnievents-integraciones-con-costos/verify.md), [013](../../docs/sdd/specs/013-omnievents-menus-y-formularios-bedrock/verify.md), [014](../../docs/sdd/specs/014-omnievents-paridad-conditionalevents/verify.md) y [015](../../docs/sdd/specs/015-omnievents-paridad-conditionalevents-2/verify.md)) |
| Paper 1.21.5 – 1.21.11 | Sin verificar | Debería funcionar (misma API, sin NMS), pero no se probó |
| Paper 26.2 y 26.3 | Sin verificar | El bot de pruebas (mineflayer 4.39) llega hasta 26.1 |
| Paper 1.21.3 o anterior | No soportada | Se compila contra la API de 1.21.4 |
| Spigot, CraftBukkit | No soportada | Usa la API Adventure de Paper |
| Folia | No soportada | Planificada para una spec futura |

- **Java:** el jar necesita Java 21 o más; usa el Java que pida tu versión de Paper (las pruebas en 26.x usaron Java 25).
- **Dependencias opcionales** (`softdepend`; sin ellas OmniEvents funciona y las reglas que las usan avisan al cargar):

  | Plugin | Para qué | Probado en la suite (2026-10-25) |
  |---|---|---|
  | Vault + una economía | `cost.money`, `give_money`, `take_money`, `money()` | Vault 1.7.3 con EssentialsX 2.21.0 (Paper 1.21.4) y 2.22.0 (26.1.2) |
  | WorldGuard (con WorldEdit) | `region_enter`, `region_leave`, `in_region()`, `regions()` | WorldGuard 7.0.13 + WorldEdit 7.3.19 (1.21.4); WorldGuard 7.0.18 + WorldEdit 7.4.6 (26.1.2) |
  | Citizens | `npc_click`, `npc_left_click`, `is_npc()` | Citizens 2.0.37-b3760 (1.21.4) y 2.0.44-b4268 (26.1.2) |
  | Floodgate (con Geyser) | Menús como formulario de botones para Bedrock, `is_bedrock()` | Floodgate 2.2.5-b141 con Geyser-Spigot 2.6.2-b796 (1.21.4) y 2.10.1-b1184 (26.1.2), con un cliente de Bedrock 1.21.70 y 26.30 (bedrock-protocol) |
  | PlaceholderAPI | `%placeholders%` y la expansión `%omni_…%` | Probada a mano con la 2.12.3 en Paper 1.21.4: `%omni_state_<regla>%` y `%omni_var_<n>%` responden; no está en la suite |

  Otras economías de Vault (CMI…) y otras versiones de estos plugins: sin verificar.
- Para verificar otra versión: `npm --prefix tests-e2e test -- --paper <versión> --version-bot <versión> --java-servidor <JDK>`
  (ver [verificacion.md](../../docs/sdd/verificacion.md#3a-en-el-juego-automática)).

## Instalación
1. Copia `OmniEvents-x.y.z.jar` a `plugins/` (opcionales: PlaceholderAPI, Vault con un plugin de economía, WorldGuard,
   Citizens y Floodgate; ver [Integraciones](#integraciones-costos-vault-worldguard-y-citizens) y [Menús](#menús-de-inventario-y-formularios-de-bedrock)).
2. Arranca el servidor: se crean `config.yml`, `messages.yml` y ejemplos en `plugins/OmniEvents/rules/ejemplos/`.
3. Edita o agrega archivos en `rules/` y usa `/omni reload`.

¿Vienes de ConditionalEvents? `/omni import conditionalevents` convierte tus eventos a `rules/importado/` ([qué se
traduce](#desde-conditionalevents)).

## Editor: autocompletado y errores en VS Code
Con la extensión **YAML** de Red Hat, pon esta línea al inicio de cada archivo de reglas (los ejemplos ya la traen):
```yaml
# yaml-language-server: $schema=https://raw.githubusercontent.com/leonardos4enz/fabrica-de-plugins/main/docs/omnievents/omnievents.schema.json
```
Al escribir `trigger: blo` te sugiere `block_break`, `block_place`… con su descripción; lo mismo con las opciones de la
regla y las acciones (con su forma y sus claves: `then`, `do`, `as`…), y al pasar el ratón ves qué hace cada una. Se
marcan como error, antes de cargar: una opción o acción desconocida (`cooldwon`, `mesage`), un mapa de condición sin
`expr` ni `any`, `any: 0`, `budget: 5` sin unidad, un `timer` sin `every` o una hora `25:00`.
- Las **expresiones** dentro de los textos, los materiales, los sonidos y los efectos no se revisan en el editor
  (dependen de la versión de Paper): para eso sigue `/omni validate`.
- El editor espera los nombres en minúsculas (`message:`); el plugin también acepta `Message:`.
- `/omni schema` escribe `plugins/OmniEvents/omnievents.schema.json` y `referencia.md` con lo que tiene **tu**
  servidor, incluidas las acciones y funciones que agreguen otros plugins. Para usarlo en lugar del publicado:
  `# yaml-language-server: $schema=../omnievents.schema.json` (desde un archivo de `rules/`; en subcarpetas, agrega `../`).
  Solo escribe esos dos archivos: nunca toca tus reglas.
- Desarrollo: `./gradlew :omnievents:schema` regenera `docs/omnievents/`; una prueba falla si quedan desactualizados.

## Una regla
```yaml
diamantes:
  trigger: block_break                 # cuándo
  worlds: [world]
  conditions:                          # si (todas deben cumplirse)
    - "block.type in ['DIAMOND_ORE', 'DEEPSLATE_DIAMOND_ORE']"
    - "player.gamemode == 'survival' && !player.hasPermission('staff')"
  cooldown: 10s
  actions:                             # haz
    - set: "pvar.diamantes += 1"
    - if: "pvar.diamantes % 10 == 0"
      then:
        - broadcast: "<aqua>{player.name}</aqua> ya minó {pvar.diamantes} diamantes"
  else:
    - actionbar: "<gray>Solo cuentan los diamantes"
  on_cooldown:
    - message: "<red>Espera {var.cooldown_left}s"
```

Así se ve el ejemplo `diamantes_contados`, que hace lo mismo (cuenta los diamantes en `pvar` y los muestra en la barra):

![El ejemplo diamantes_contados: la barra de acción dice «Diamantes: 1» después de romper una mena](../../docs/img/omnievents/ejemplo-diamantes_contados.png)

## Opciones de una regla
Imágenes de `cooldown`, `on_cooldown`, `limit` y `on_limit`: [galería](../../docs/omnievents/galeria.md#opciones-de-una-regla).

| Opción | Qué hace |
|---|---|
| `trigger` | Disparador (`/omni triggers` los lista) |
| `enabled` | `false` la apaga sin borrarla |
| `priority`, `ignore_cancelled` | Prioridad del oyente; no correr si otro plugin canceló el evento |
| `worlds`, `permission`, `bypass_permission` | Filtros rápidos |
| `conditions` | Una expresión o una lista (todas); cada una puede llevar su `deny` y un grupo `any`/`of` pide «N de M» ([abajo](#condiciones-con-deny-y-n-de-m)) |
| `actions`, `else` | Qué hacer si se cumplen / si no |
| `cooldown`, `cooldown_scope` | `30s`, `5m`, `1h30m`, `2d`, `20t`; `player` o `global` |
| `cooldown_bypass` | Permiso que salta el enfriamiento; sin la opción, `omnievents.bypasscooldown.<regla>`. Cuenta solo si está **dado** al jugador (un OP sin él no lo salta; para eso, `cooldown-bypass-op` en `config.yml`) |
| `limit`, `limit_scope`, `limit_reset` | Usos máximos; `never`, `daily`, `weekly`, `monthly` o una duración |
| `on_cooldown`, `on_limit` | Acciones cuando está en enfriamiento o sin usos |
| `every`, `per_player` | Para `timer` |
| `at`, `timezone` | Para `clock` (`["08:00", "20:30"]`) |
| `command` | Para `command`: `name`, `aliases`, `permission`, `description`, `usage`, `tab` |
| `event`, `player_from` | Para `custom`: clase del evento y ruta al jugador |
| `move_mode` | Para `player_move`: `block` (defecto), `chunk` o `any` |
| `args` | Para `call`: parámetros (llegan como `var.<nombre>`) |
| `when`, `on_enter`, `on_exit`, `check_every`, `watch`, `initial`, `per_player` | Para `state` ([abajo](#disparador-de-estado-trigger-state)) |
| `budget` | Presupuesto de tiempo: `0.5ms` o `200us`; avisa en consola si el promedio reciente lo pasa ([abajo](#rendimiento-omni-cpu-y-budget)) |
| `tests` | Pruebas de la regla con `given` y `expect` ([abajo](#pruebas-de-reglas-tests-y-omni-test)) |
| `template`, `extends`, `with`, `params` | Plantillas y parámetros `${nombre}` ([abajo](#paquetes-plantillas-y-versión)) |
| `cost`, `on_cost_fail` | Lo que cuesta la regla, todo o nada (dinero, niveles, experiencia, ítems, variables) y qué hacer si no alcanza ([abajo](#integraciones-costos-vault-worldguard-y-citizens)) |

## Condiciones con deny y «N de M»
Cada condición puede ser un texto (como siempre) o un mapa con `expr` y sus propias acciones `deny`. Si falla, corren
solo sus `deny` (no el `else`); una condición sin `deny` que falla corre el `else` de la regla, como antes. Un grupo
`any: N` con `of: [...]` se cumple si al menos N de sus condiciones se cumplen (se puede anidar). Ejemplo completo:
[`rules/ejemplos/herramientas.yml`](src/main/resources/rules/ejemplos/herramientas.yml).
```yaml
tienda_espada:
  trigger: command
  command: {name: espada}
  conditions:
    - "player != null"                       # forma corta: si falla, corre el else
    - expr: "player.level >= 5"
      deny:
        - message: "<red>Necesitas nivel 5 (tienes {player.level})."
    - any: 2                                 # al menos 2 de estas 3
      of: ["perm(omnievents.vip)", "has_item(EMERALD, 1)", "player.world.time > 12000"]
      deny:
        - message: "<red>Necesitas 2 de 3: ser VIP, llevar una esmeralda o que sea de noche."
  actions: [...]
  else: [...]
```
`any` debe ser un entero entre 1 y el número de condiciones de `of`; un `deny` dentro de `of` no se usa (aviso al
cargar).

## Disparador de estado (`trigger: state`)
«Cuando X **pasa a ser** verdad» en lugar de «cada X segundos, revisa». `on_enter` corre una vez cuando `when` pasa a
ser verdad para un jugador, y `on_exit` una vez cuando deja de serlo; mientras no cambie, no corre nada. No hace
falta una variable de «ya avisé». Ejemplos completos: [`rules/ejemplos/estados.yml`](src/main/resources/rules/ejemplos/estados.yml).
```yaml
vida_baja:
  trigger: state
  when: "player.health <= 6"          # o una lista de condiciones (todas)
  cooldown: 20s                       # frena on_enter, y entonces tampoco corre su on_exit
  on_enter:
    - title: "<red>¡Peligro!;<gray>Busca refugio"
  on_exit:
    - actionbar: "<green>A salvo tras {var.state_seconds}s"
```
- **Sin sondeo.** Al cargar, el motor deduce de qué depende `when` y solo lo revisa con esos eventos: vida → daño,
  curación y reaparición; hambre; ubicación, bloque, bioma, mundo e `in_area()` → moverse **de bloque**,
  teletransporte y cambio de mundo; nivel y experiencia; modo de juego; agacharse, correr, volar; armadura; ítem en
  mano e inventario (`has_item()`); conectados (`online`); clima (`world.storm`); `pvar.x` y `gvar.x` cuando una
  acción o `/omni var` las cambian; e `in_state('otra')` cuando cambia la otra. Varios eventos del mismo jugador en
  un tick se juntan en **una** revisión, al final del tick (el daño ya restó la vida). `/omni info vida_baja` dice
  qué dedujo y qué eventos escucha.
- **Lo que no se puede deducir** (placeholders, `perm()`, la hora, `world.time`/`world.day`, `player.op`, métodos,
  funciones de otros plugins) hace que la regla **no cargue** hasta que declares `check_every: 5s`: el costo del
  sondeo queda a la vista. `watch: [player_interact]` agrega disparadores a los deducidos. El azar (`chance`,
  `random`) en `when` es error.
- Los cambios hechos por comandos de otros plugins o por `/give`, `/clear` no lanzan eventos: para el inventario
  y lo que cambie así, agrega `check_every` o `watch`.
- **Al entrar al servidor** con el estado ya verdadero, corre `on_enter`; `initial: silent` no avisa (y entonces
  tampoco su `on_exit`). Al salir se olvida, sin `on_exit`. `/omni reload` conserva el estado de las reglas cuyo
  `when`, `worlds` y `per_player` no cambiaron; una regla nueva o cambiada toma el estado actual sin disparar.
- `permission`, `bypass_permission`, `limit` y `cooldown` frenan `on_enter` como en cualquier regla; `on_exit`
  solo corre si corrió el `on_enter` de esa entrada. `worlds` es parte del estado (fuera de esos mundos es falso).
- **Estado del servidor:** `per_player: false` lleva un solo estado sin jugador (`gvar`, `online`, clima o
  `in_state` de otro estado del servidor), p. ej. `when: "gvar.evento == true && online >= 2"` con
  `on_enter: [broadcast: ...]`. Toma su valor al cargar, sin disparar.
- **Desde otras reglas:** `in_state('vida_baja')` (y `%omni_state_vida_baja%`) dice si el jugador está en el estado.
  Un nombre fijo que no es una regla `state` es error al cargar.
- **Diagnóstico:** `/omni debug vida_baja` muestra cada entrada o salida con los valores de `when`; `/omni check
  vida_baja Ana` da el valor ahora, el estado guardado y qué correría; `/omni state vida_baja` lista quién está dentro
  y desde cuándo; `/omni stats` cuenta revisiones y cambios. `/omni run vida_baja Ana [--simular]` corre su `on_enter`.
- El estado vive en memoria (una entrada por jugador dentro): no se guarda en disco ni cambia tus datos.

## Variables que caducan y variables de objetos
Desde la 1.4.0 ([spec 009](../../docs/sdd/specs/009-omnievents-variables-caducidad-y-objetos/spec.md)). Ejemplos
completos: [`rules/ejemplos/variables.yml`](src/main/resources/rules/ejemplos/variables.yml).
```yaml
racha_sube:
  trigger: player_kill
  conditions: "target.type == 'ZOMBIE'"
  actions:
    - set: "pvar.racha = default(pvar.racha, 0) + 1"
      expire: 2m                                  # se pierde tras 2 min sin matar otro
    - actionbar: "Racha x{pvar.racha} · {round(ttl(pvar.racha))}s"

racha_perdida:
  trigger: var_expire                             # corre cuando caduca
  variables: [racha]
  actions:
    - message: "<red>Perdiste tu racha de {value}."

cofre_con_dueno:
  trigger: block_place
  conditions: "block.type == 'CHEST'"
  actions:
    - set: "block.data.dueño = player.name"       # guardada en el bloque
```
- **`expire: <duración>`** en un `set` de `pvar`, `gvar` u `objeto.data`: la variable se borra sola pasado ese tiempo
  (`10m`, `1h`, o una plantilla como `"{gvar.minutos}m"`). Cada `set` con `expire` vuelve a contar; un `set` sin él
  quita la caducidad. Sobrevive a reinicios: lo que caducó con el servidor apagado ya no está al volver. En `var` es
  error (no vive lo bastante).
- **`ttl(pvar.x)`** (también `gvar.x` y `objeto.data.x`): segundos que le quedan; `-1` si no caduca; nulo si no existe.
- **`trigger: var_expire`** corre cuando caduca una `pvar` de un jugador conectado (con `player`) o una `gvar` (sin
  jugador). Raíces: `variable`, `value` (lo último que valía) y `scope` (`pvar`/`gvar`); `variables: [..]` lo limita.
  Los `trigger: state` que leen esa variable se revisan al caducar (su `on_exit` corre). No sondea a nadie: una cola
  ordenada por hora que se mira una vez por segundo. Una `pvar` de alguien desconectado caduca sin aviso.
- **`objeto.data.nombre`** lee y escribe una variable de cualquier bloque, entidad (también jugadores) o ítem:
  `block.data.dueño`, `target.data.marcado`, `player.item.data.ligada`; `unset: "block.data.dueño"` la borra. Vive en el
  `PersistentDataContainer` de Paper: la del ítem **viaja con el ítem** (si cambia de manos, la lleva), la de la
  entidad va con la entidad y la del bloque en su chunk. Guarda números, textos y booleanos (lo demás, como texto).
  Sobre algo que no es bloque, entidad ni ítem, `.data` sigue siendo la propiedad de antes. Para escribir en un ítem
  usa `player.item` (u `offhand`) o el `item` de un evento que sea el del inventario (romper, colocar, golpear, clic):
  en otros (consumir, craftear, tirar) el `item` del evento puede ser una copia y lo que guardes ahí se pierde (la
  suite solo probó `player.item`).
- Las de un bloque **se borran cuando el bloque deja de estar**: romperlo, explosiones, fuego, hojas que se secan,
  criaturas que lo cambian y pistones que lo mueven (después de las reglas del evento, que aún las leen). Esos oyentes
  solo se registran si alguna regla escribe `.data` (`/omni stats` dice si están activos).
- Las de objetos caducan al leerse (no avisan: no se puede recorrer cada chunk e ítem), y un estado que use `.data` o
  `ttl()` necesita `check_every`.
- **Cuidado con creativo:** un jugador en creativo puede fabricar un ítem con cualquier dato; no confíes en
  `item.data` para ellos. Guardar datos distintos en dos ítems iguales hace que dejen de apilarse.
- `/omni data [bloque|entidad|mano]` muestra las variables de lo que miras o de tu mano, con lo que les queda;
  `/omni var list Ana` también dice cuánto le queda a cada una, y `/omni var set Ana bono 2 --expire 1h` asigna con
  caducidad.
```yaml
# config.yml (claves nuevas en 1.4.0; si faltan se usan estos valores)
object-data:
  max-text-length: 256   # largo máximo de un texto guardado en un objeto
  max-vars: 32           # variables por objeto
```
- **Actualizar desde 1.3.0 o anterior:** la primera vez que arranca, la 1.4.0 copia `plugins/OmniEvents/data/` a
  `data/respaldo-formato1-<fecha>/` y desde entonces escribe los archivos con `format: 2` (una sección nueva,
  `expire`). Tus datos cargan igual; si vuelves a una versión anterior, usa ese respaldo. Un archivo de un formato
  más nuevo que el que entiende el plugin no se sobrescribe.

## Pruebas de reglas (`tests:` y `/omni test`)
Desde la 1.5.0 ([spec 010](../../docs/sdd/specs/010-omnievents-pruebas-de-reglas/spec.md)). Cada regla puede llevar
sus casos de prueba: el contexto que entra (`given`) y lo que esperas (`expect`). Corren **sin servidor ni
jugadores y sin tocar nada real**, así que te enteras si una regla dejó de hacer lo que hacía al cambiarla, al
actualizar el plugin o Paper. Los ejemplos de `rules/ejemplos/` traen los suyos.
```yaml
diamantes_contados:
  trigger: block_break
  conditions: ["block.type == 'DIAMOND_ORE'", "player.gamemode == 'survival'"]
  actions:
    - set: "pvar.diamantes += 1"
    - actionbar: "<aqua>Diamantes: {pvar.diamantes}"
    - console: "give {player.name} emerald 1"
  tests:
    - name: suma uno                     # opcional
      given:                             # rutas con puntos: las raíces del disparador y sus propiedades
        player.name: Ana
        player.gamemode: SURVIVAL
        block.type: DIAMOND_ORE
        pvar.diamantes: 9                # variables iniciales (pvar, gvar, var, block.data.x)
      expect:
        fired: true                      # corrieron sus actions
        pvar.diamantes: 10               # null = que no exista
        messages: ["Diamantes: 10"]      # contiene, en orden, sin formato ni mayúsculas
        commands: ["give Ana emerald"]
    - name: la piedra no cuenta
      given: {block.type: STONE}
      expect: {fired: false, branch: none}
```
- **`given`:** cada clave es una ruta (`block.type`, `player.level`, `event.damage`, `player.location.y`) con su
  valor; también `pvar.x`, `gvar.x`, `var.x`, `<raíz>.data.x` y placeholders (`"%vault_eco_balance%": 100`). Un texto
  en mayúsculas (`SURVIVAL`, `DIAMOND_ORE`) se compara como en el servidor, sin mayúsculas. Lo que no das vale nulo.
- **`expect`:** `fired`, `branch` (`actions`, `else`, `deny`, `deny conditions[1]`, `none`; `on_enter` en un
  `state`), variables, `messages` (message, broadcast, actionbar, title…), `commands` (console, command), `actions`
  (`give_item`, o `"sound: LEVELUP"` con su texto), `cancelled` y `assert` (expresiones que deben dar verdadero).
- **Qué corre:** condiciones, sus `deny` y el `else`, y las acciones de flujo (`if`, bucles, `random` con la semilla
  del caso, `seed`, `stop`, `call` a otra regla, que también se prueba). `set`, `unset` y `cancel` cambian solo el
  caso. Lo demás (mensajes, comandos, ítems, mundo) se **registra** con su texto armado y no se aplica; `delay` no
  espera y `chance` no se sortea (corre).
- **Qué no se prueba:** `worlds`, `permission`, `bypass_permission`, `cooldown` y `limit` (la traza lo dice); `to:` y
  `for_each` no resuelven destinatarios. Las funciones que miran el servidor dan nulo, falso o 0 (`cooldown()`,
  `uses()`, `in_state()`, `ttl()` de lo que no diste); `online()`, `player()` y `perm()` leen el servidor de verdad (en
  gradle no hay). Un método de un objeto falso (`player.hasPermission(…)`) es un error del caso.
- **`/omni test [regla]`** (permiso `omnievents.command.test`, también desde la consola y sin jugadores): `✔ regla
  3/3`, y por cada caso que falla qué esperaba, qué salió y la traza (`pvar.diamantes: esperaba 10, dio 9`).
- **Al arrancar y en cada `reload`** corren todas: la consola dice `Pruebas de reglas: 12/12 pasan` o avisa de cada
  una que falla. Una prueba mal escrita se informa como cualquier error de configuración (con ruta y «¿quisiste
  decir…?») y no impide que la regla cargue.
```yaml
# config.yml (clave nueva en 1.5.0; si falta se usa este valor)
tests:
  on-load: true    # false: las pruebas solo corren con /omni test
```

## Expresiones
- Lógica: `&&` `and`, `||` `or`, `!` `not`, paréntesis, `c ? a : b`.
- Comparación: `==` `!=` `<` `<=` `>` `>=`, `~=` (igual sin mayúsculas), `contains`, `startsWith`, `endsWith`,
  `matches` (regex), `in`; negaciones `!contains`, `not in`…
- Aritmética: `+ - * / %` (con texto, `+` concatena). Listas `[1, 2]`, índices `args[0]`.
- Datos: `player.health`, `player.location.block.type`, `item.name`, `target.type`, `event.damage`… Cualquier
  getter de la API sirve en estilo `snake_case` (`event.final_damage` → `getFinalDamage()`); los métodos que
  modifican están bloqueados.
- Variables: `var.x` (de la ejecución), `pvar.x` (del jugador, persistente), `gvar.x` (del servidor, persistente) y
  `objeto.data.x` (guardada en un bloque, entidad o ítem; ver [abajo](#variables-que-caducan-y-variables-de-objetos)).
  `ttl(pvar.x)` da los segundos que le quedan a una variable con `expire`.
- Placeholders: `%vault_eco_balance% > 100` (con PlaceholderAPI; si el resultado es un número, se compara como número).
- Funciones: `random(a,b)` `chance(p)` `min` `max` `abs` `round(x,d)` `floor` `ceil` `sqrt` `pow` `clamp` `len`
  `lower` `upper` `trim` `replace` `substr` `split` `join` `str` `num` `int` `bool` `strip` `format` `default(a,b)`
  `if(c,a,b)` `papi(t[,jugador])` `online(n)` `player(n)` `players()` `perm(p)` `now()` `hour()` `minute()`
  `weekday()` `date(patrón)` `cooldown(regla)` `uses(regla)` `distance(a,b)` `in_area(mundo,x1,y1,z1,x2,y2,z2)`
  `has_item(material[,n])` `pvar_of(jugador,nombre)` `in_state(regla)` `ttl(variable)` `size(x)`, y desde la 1.10.0
  `pick(lista)` (uno al azar: `pick(players()).name`), `block_at(mundo,x,y,z)` (el bloque, o nulo si el chunk no está
  cargado) e `is_number(x)`.

Firma y descripción de cada función: [referencia](../../docs/omnievents/referencia.md#funciones). Prueba cualquier expresión en el juego: `/omni eval player.location.biome`.

![Salida de /omni eval con tres expresiones, su resultado y su tipo](../../docs/img/omnievents/comando-eval.png)

## Textos
`{expresión}` interpola, `%placeholder%` pasa por PlaceholderAPI, y se aceptan MiniMessage, `&a` y `&#RRGGBB`.
Lo que escribe el jugador e interpolas con `{}` nunca se interpreta como MiniMessage (no puede inyectar `<click>`),
y en comandos pierde los saltos de línea.

## Acciones
Dos formas equivalentes: `- message: "hola"` o `- "message: hola"`. Modificadores en cualquier acción:
```yaml
- message: "<gold>¡Evento!"
  to: all                  # self | target | all | world:<w> | radius:<n> | nearby:<n> | player:<nombre> | expresión
  where: "player.level >= 10"   # filtro por destinatario
  chance: 25               # porcentaje
  when: "hour() >= 20"     # guarda
```
Control de flujo: `if/then/else`, `repeat/do` (`var.i`), `while/do`, `for_each/as/do`, `random` (ramas con `weight`),
`delay`, `wait_ticks` (en ticks), `stop`, `call`, `set`, `unset`, `no_cooldown`, `reset_cooldown` ([imágenes](../../docs/omnievents/galeria.md#acciones--control-de-flujo)).

| Grupo (enlace a sus imágenes) | Acciones |
|---|---|
| [Mensajes](../../docs/omnievents/galeria.md#acciones--mensajes) | `message` `centered_message` (cada línea centrada en el chat) `broadcast` `actionbar` `title` (`título;subtítulo;in;stay;out`) `bossbar` (`texto;segundos;color;progreso`) `sound` (`SONIDO;vol;tono`) `stop_sound` `console_message` `log` |
| [Comandos](../../docs/omnievents/galeria.md#acciones--comandos) | `console` `command` `chat` `send_to_server` |
| [Jugador](../../docs/omnievents/galeria.md#acciones--jugador) | `teleport` (`mundo;x;y;z;yaw;pitch`, `~` relativo) `heal` `damage` `feed` `set_food` `gamemode` `fly` `effect` (`EFECTO;segundos;nivel;partículas`) `remove_effect` `fire` `freeze` `velocity` `launch` `give_item` `drop_item` `remove_item` (`MATERIAL;cantidad[;name:…][;lorecontains:…]`: por material, aunque el ítem tenga nombre, o solo los de ese nombre o lore) `remove_item_slot` (`CASILLA;cantidad`) `set_caught_item` (`player_fish`) `clear_inventory` `close_inventory` `give_exp` `give_level` `give_money` `take_money` (Vault) `kick` |
| [Mundo](../../docs/omnievents/galeria.md#acciones--mundo) | `set_block` (`MATERIAL[datos]`: `OAK_STAIRS[facing=north]`) `lightning` `explosion` (`potencia;fuego;romper`) `particle` (`PARTÍCULA;n;dx;dy;dz;vel[;r;g;b]`, con `location`, `force`, `for_player`) `summon` (texto o mapa, abajo) `firework` (abajo) |
| [Evento](../../docs/omnievents/galeria.md#acciones--evento) | `cancel` `set_event` (`propiedad = expresión`) `set_damage` (`10` o `150%`) `set_message` `set_join_message` `set_quit_message` `set_death_message` (`none` la oculta) `keep_inventory` `cancel_drops` `deny_join` |

| `title` | `bossbar` |
|---|---|
| ![Título «¡Victoria!» con el subtítulo «Ganaste la ronda 3»](../../docs/img/omnievents/accion-title.png) | ![Bossbar roja «El Guardián del Bosque» al 65 %](../../docs/img/omnievents/accion-bossbar.png) |

Descripción, forma del valor, claves y un ejemplo de cada acción: [referencia](../../docs/omnievents/referencia.md#acciones).

Fuegos artificiales (forma de ConditionalEvents; sin daño salvo `damage:true`):
```yaml
- to: all
  firework: "colors:RED,#FFAA00 type:STAR power:1 fade:WHITE flicker:true trail:true"   # type: BALL, BALL_LARGE, STAR, BURST, CREEPER
```

Ítems: `give_item: "DIAMOND;2;<aqua>Nombre;<gray>Lore 1|<gray>Lore 2"`, en mapa (`material`, `amount`, `name`,
`lore`, `model`, `enchants`, `glow`, `unbreakable`, `hide_flags`, y desde la 1.10.0 `flags`, `durability`, `item_model`,
`skull_owner`, `skull_texture`) o `saved:<nombre>` (guardado con `/omni item save`). `give_item` en mapa acepta además
`slot` (0 a 40 o `HAND`, `OFF_HAND`, `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS`) y `slot_replace`:
```yaml
- give_item:
    material: DIAMOND_SWORD
    name: "<aqua>Espada VIP"
    flags: HIDE_ENCHANTS, HIDE_ATTRIBUTES
    durability: 100            # daño ya gastado
    enchants: ["SHARPNESS:3"]
    slot: 0                    # si está ocupada va al inventario, salvo slot_replace: true
- give_item: {material: PLAYER_HEAD, skull_owner: "{player.name}", slot: HELMET}
- remove_item: "PAPER;1;name:Ticket VIP"     # solo los papeles con ese nombre (sin colores)
- remove_item_slot: "OFF_HAND;1"
```

Invocar con nombre, vida y equipo (`summon` en mapa; el equipo no se suelta al morir):
```yaml
- summon:
    entity: ZOMBIE
    location: "~3;~;~"
    name: "<red>Guardián"
    health: 40
    amount: 2                  # máximo 50
    scale: 1.5
    equipment: "DIAMOND_HELMET,none,none,IRON_BOOTS"   # casco,peto,pantalones,botas
    hand_equipment: "IRON_SWORD,SHIELD"
```|<gray>Lore 2"`, en mapa (`material`, `amount`, `name`,
`lore`, `model`, `enchants`, `glow`, `unbreakable`, `hide_flags`) o `saved:<nombre>` (guardado con `/omni item save`).

## Disparadores
| Grupo (enlace a sus imágenes) | Disparadores |
|---|---|
| [Jugador](../../docs/omnievents/galeria.md#disparadores--jugador) | `player_join` (`first_join`) `player_quit` `player_pre_login` (`name`, `uuid`, `ip`) `player_respawn` `player_death` (`killer`, `cause`) `player_chat` (`message`) `player_command` (`cmd`, `args`) `player_move` `player_teleport` `player_world_change` `player_sneak` `player_sprint` `player_jump` `player_level_change` `player_gamemode_change` `player_toggle_flight` `player_bed_enter` `player_fish` `player_regain_health` `player_food_change` `player_advancement` `player_swap_hands` `player_armor_change` `player_portal` `player_kick` `player_resource_pack` |
| [Combate](../../docs/omnievents/galeria.md#disparadores--combate) | `player_damage` (`cause`, `damager`) `player_attack` (`target`) `player_kill` (`target`) `player_shoot` `projectile_hit` |
| [Bloques](../../docs/omnievents/galeria.md#disparadores--bloques) | `block_break` `block_place` `block_interact` (`action`: `RIGHT_CLICK`, `SHIFT_LEFT_CLICK`…) `sign_change` (`lines`) `bucket_fill` `bucket_empty` |
| [Ítems](../../docs/omnievents/galeria.md#disparadores--ítems) | `player_interact` `item_interact` `item_consume` `item_craft` `item_drop` `item_pickup` `item_held` `item_enchant` `item_break` `item_repair` (yunque o afiladora: `repair_type`, `rename_text`, `level_cost`, `base_item`, `addition`) |
| [Inventario](../../docs/omnievents/galeria.md#disparadores--inventario) | `inventory_open` `inventory_close` `inventory_click` (`slot`, `click`, `title`, `cursor`) |
| [Entidades](../../docs/omnievents/galeria.md#disparadores--entidades) | `entity_interact` `entity_spawn` `entity_death` |
| [Servidor](../../docs/omnievents/galeria.md#disparadores--servidor) | `console_command` `server_start` `server_stop` `timer` `clock` `call` `command` `custom` `state` ([arriba](#disparador-de-estado-trigger-state)) |
| Integraciones ([abajo](#integraciones-costos-vault-worldguard-y-citizens)) | `region_enter` `region_leave` (`region`; WorldGuard) `npc_click` `npc_left_click` (`npc`, `target`; Citizens) |

Siempre disponibles: `player`, `event`, `world`, `location`, `item` (mano principal), `server`, `online`, `rule`.
Las raíces de cada disparador y si se puede cancelar: [referencia](../../docs/omnievents/referencia.md#disparadores).

## Integraciones: costos, Vault, WorldGuard y Citizens
Desde la 1.7.0 ([spec 012](../../docs/sdd/specs/012-omnievents-integraciones-con-costos/spec.md)). Los tres plugins son
**opcionales** (`softdepend`): sin ellos OmniEvents carga igual y una regla que los usa carga con un aviso
(«necesita Vault, que no está instalado») y no hace esa parte. `/omni stats` dice qué integraciones detectó.

**Costos todo o nada.** `cost` se revisa después de las condiciones, el límite y el enfriamiento. Si todo alcanza se
cobra y corren las acciones; si algo falta **no se cobra nada**, no cuenta como uso ni activa el enfriamiento y corre
`on_cost_fail`. Si un cobro falla a mitad (la economía lo rechaza, el inventario cambió), lo ya cobrado se devuelve
(los ítems vuelven tal cual, con su nombre y encantamientos).
```yaml
tienda_arco:
  trigger: command
  command: comprar_arco
  cost:
    money: 500                    # Vault (EssentialsX, CMI…)
    items: "DIAMOND;3"            # o una lista: ["DIAMOND;3", "STICK;1"]; por material, sin la armadura
    levels: 5                     # niveles de experiencia
    exp: 100                      # puntos de experiencia
    pvar.fichas: 1                # variables numéricas (pvar o gvar); nula cuenta como 0
  actions:
    - give_item: "BOW;1;<aqua>Arco de la tienda"
    - message: "<green>Compraste el arco. Te quedan {money_format(money())}."
  on_cost_fail:
    - message: "<red>Te falta: {var.cost_missing}"     # «200 de dinero, 2 × DIAMOND»; var.cost_missing_list: [money, items]
```
- Cada cantidad es una expresión (`money: "var.precio * 2"`) y se evalúa **una vez**: lo cobrado es lo cotizado.
- `cost` no sirve en disparadores asíncronos (`player_chat`, `player_pre_login`): cobrar necesita el hilo principal
  (usa `call` a una regla `call` con el costo). Sin jugador (`server_start`, `timer` global) solo cobra `gvar`.
- `/omni check <regla> <jugador>` muestra cada parte (`✘ costo items DIAMOND: 1 / 3`) y la rama `on_cost_fail`;
  `/omni run … --simular` no cobra.
- En `tests:` el costo se cobra sobre el contexto falso: `given: { money: 800, inventory: { DIAMOND: 5 }, player.level: 6 }`
  y `expect: { branch: on_cost_fail }` cuando falta algo. Nada se cobra de verdad.

**Dinero (Vault).** `give_money: 100` y `take_money: "var.precio"` (si no alcanza, es un error y no retira nada);
`money()` es el saldo y `money_format(n)` lo escribe con la moneda de la economía. La economía se busca al usarla:
sirve aunque tu plugin de economía arranque después de OmniEvents.

**Regiones (WorldGuard).** `region_enter` y `region_leave` corren por cada región en la que el jugador entra o de la
que sale (al moverse de bloque, al teletransportarse, al entrar al servidor y al cambiar de mundo). `region.id`,
`region.priority`; `cancel` deja al jugador donde estaba. `in_region(id)` y `regions()` (en estas reglas, miran el
destino). Solo se escucha el movimiento si hay reglas de región activas.
```yaml
zona_vip:
  trigger: region_enter
  conditions: ["region.id == 'vip'", "!perm('rango.vip')"]
  actions: [cancel, message: "<red>Esa zona es solo para VIP."]
```

**NPC (Citizens).** `npc_click` (clic derecho) y `npc_left_click` (golpe), con `npc.id`, `npc.name` y
`npc.entity`; se pueden cancelar. `is_npc(entidad)` sirve en cualquier regla (`entity_interact`, `player_attack`).
```yaml
guia:
  trigger: npc_click
  conditions: "npc.name == 'Guía'"
  cooldown: 1h
  actions:
    - message: "<yellow>[Guía]</yellow> Hola, {player.name}. Estás en: {regions()}"
    - give_money: 10
```
Ejemplo completo: `rules/ejemplos/integraciones.yml`.

## Menús de inventario y formularios de Bedrock
Desde la 1.8.0 ([spec 013](../../docs/sdd/specs/013-omnievents-menus-y-formularios-bedrock/spec.md)). Una entrada de
`rules/` con `menu:` (en vez de `trigger:`) es un menú; se abre con la acción `open_menu` desde cualquier regla.
**Cada clic es un cuerpo de regla**: `conditions` con `deny`, `cost` todo o nada, `cooldown`, `limit`, `actions` y
`tests:`, sin aprender otro lenguaje. Los ítems del menú no se pueden sacar (todo clic y arrastre se cancela, también
el shift-clic y las teclas de número), y el menú se reconoce por dentro, no por su título.
```yaml
abrir_tienda:
  trigger: command
  command: tienda
  actions:
    - open_menu: tienda              # o { menu: tienda, args: { descuento: 10 } } → var.descuento

tienda:
  menu:
    title: "<dark_green>Tienda de {player.name}"
    rows: 3                          # 1 a 6
    fill: GRAY_STAINED_GLASS_PANE    # casillas vacías
    permission: tienda.usar          # requisito para abrir: permission, conditions (con deny), cost…
    on_open: ["sound: UI_BUTTON_CLICK"]
    on_close: []
    bedrock: { content: "Elige qué comprar" }
    items:
      espada:
        slot: 11                     # o [11, 12] o "0-8"
        item: "DIAMOND_SWORD;1;<aqua>Espada;<gray>5 esmeraldas"    # como give_item, con {expresiones}
        left_click:                  # también click, right_click, shift_click (gana el más específico)
          cost: { items: "EMERALD;5" }
          actions: ["give_item: DIAMOND_SWORD"]
          on_cost_fail: ["message: <red>Te falta {var.cost_missing}"]
          close: true                # cierra el menú después de las acciones
          tests:
            - given: { click: left, inventory: { EMERALD: 6 } }
              expect: { branch: actions, actions: [give_item] }
        right_click:
          actions: ["message: Cuesta 5 esmeraldas"]
      vip_bloqueado:                 # dos ítems en la misma casilla: se ve el primero visible
        slot: 15
        view: "!perm('rango.vip')"
        item: "BARRIER;1;<red>Solo VIP"
      vip:
        slot: 15
        item: "NETHER_STAR;1;<gold>Kit VIP"
        click: { limit: 1, limit_reset: daily, actions: ["give_item: GOLDEN_APPLE;3"] }
```
- En un clic, `menu` es el nombre del menú, `slot` la casilla, `click` el tipo (`left`, `right`, `shift_left`,
  `shift_right`, `middle`, `number`, `drop` o `bedrock`) y `item` el ítem; los `args` de `open_menu` son `var.<nombre>`.
- El título se evalúa al abrir; nombre, lore y `view` de los ítems, al abrir y otra vez **después de cada clic** (un
  contador o un saldo se ven al día). `close_menu` cierra el menú; `/omni menu open <jugador> <menú>` lo abre desde la consola.
- Los colores van **fuera** de las `{expresiones}`: lo que sale de una expresión se escapa (para que un jugador no pueda
  meter etiquetas), así que `{listo ? '<green>Sí' : '<red>No'}` mostraría `<green>` literal. Para un texto que cambia de
  color, dos ítems en la misma casilla con `view` (como `diario` y `diario_espera` en el ejemplo de la demo).
- Los enfriamientos y límites de cada clic se guardan como los de cualquier regla y siguen al reconectar (desde la 1.8.1).
- Cada clic es una regla con el nombre de su ruta (`tienda.menu.items.espada.left_click`): sale en `/omni list`,
  `/omni stats`, `/omni debug` y `/omni test`. Un `open_menu` a un menú que no existe es un error al cargar.
- `/omni reload` cierra los menús abiertos (sin `on_close`). Sin menús no hay oyentes de inventario.

**Bedrock (Geyser + Floodgate).** Con Floodgate instalado, a un jugador de Bedrock el mismo menú le llega como un
**formulario de botones** nativo: el título, `bedrock.content` y un botón por cada ítem visible con clic, en orden de
casilla (texto: el nombre del ítem sin formato, o `bedrock.button`; imagen: `bedrock.image`, una URL o una ruta de
textura). Pulsar un botón corre `click` (o `left_click`) con `click == 'bedrock'` y el formulario vuelve con los
valores nuevos, salvo con `close: true` o si las acciones abren o cierran otro menú; cerrarlo corre `on_close`. Solo
vale la respuesta del último formulario enviado. `bedrock: { enabled: false }` les muestra el inventario (Geyser lo
traduce) e `is_bedrock()` dice si el jugador es de Bedrock. Sin Floodgate todo funciona igual y sin avisos.

Ejemplo completo: `rules/ejemplos/menus.yml` (una tienda con `cost` y un selector de kits con `view`).

## Paquetes, plantillas y versión
Desde la 1.6.0 ([spec 011](../../docs/sdd/specs/011-omnievents-paquetes-plantillas-version/spec.md)). Una carpeta de
`rules/` con un `pack.yml` es un **paquete**: sus reglas se llaman `paquete:regla`, así que se puede copiar la carpeta
de otro servidor sin que sus nombres choquen con los tuyos. Sin `pack.yml` todo sigue como antes. Ejemplo completo:
`rules/ejemplos/recompensas/`.
```yaml
# rules/recompensas/pack.yml (esquema: docs/omnievents/omnievents-pack.schema.json)
name: recompensas                 # opcional: por defecto, el nombre de la carpeta (minúsculas, números, _ y -)
version: 1.1.0                    # x.y.z
description: Fichas por comando
author: Leo
enabled: true                     # false: sus reglas cargan desactivadas
params: { moneda: pvar.fichas }   # ${moneda} en cualquier regla del paquete
requires:
  omnievents: ">=1.6.0"           # >=, >, <=, <, =, separadas por coma: ">=1.2.0, <2.0.0"
  packs: { plantillas: ">=1.0.0" }
migrations:                       # corren una vez al subir a esa versión
  "1.1.0":
    - set: "gvar.temporada = 2"
    - log: "recompensas {var.from} → {var.to}"
```
```yaml
# rules/recompensas/reglas.yml → recompensas:base (plantilla), recompensas:diaria, recompensas:avisar
base:
  template: true                  # no corre sola ni necesita trigger: la usan otras con extends
  params: { cantidad: 1, espera: 1h }
  trigger: command
  cooldown: ${espera}             # un texto que es solo ${x} toma el valor con su tipo
  actions:
    - set: "${moneda} = default(${moneda}, 0) + ${cantidad}"
    - call: avisar                # busca recompensas:avisar y, si no existe, una regla global avisar
diaria:
  extends: base                   # copia las claves de base; las que escribe diaria las reemplazan enteras
  with: { cantidad: 10, espera: 1d }
  command: fichasdiarias
avisar:
  trigger: call
  actions: ["message: <green>Ahora tienes {${moneda}} fichas."]
```
- **Nombres:** desde una regla de un paquete, un nombre sin paquete (`call`, `cooldown()`, `uses()`, `in_state()`,
  `extends`) se busca primero en el paquete y luego entre las globales; `otro:regla` es absoluto. Los comandos
  (`/omni info recompensas:diaria`, `call`, `test`…) usan el nombre completo y lo autocompletan. Dentro de un paquete
  una clave con `:` es un error; fuera de un paquete solo avisa (se reserva para paquetes).
- **Plantillas:** `template: true` + `extends: nombre`. Se puede heredar en cadena hasta 8 niveles; un ciclo, una
  plantilla que no existe o heredar de una regla que no es plantilla son errores con la ruta y «¿quisiste decir?».
  `tests:` no se hereda (cada regla trae las suyas, y pueden usar `${x}`).
- **Parámetros `${nombre}`:** se reemplazan al cargar en todos los textos de la regla, buscando en `with`, en
  `params` de la regla y de sus plantillas, y en `params` del paquete, en ese orden. Un parámetro que no existe es un
  error (`recompensas:diaria.actions[1].message: parámetro desconocido ${cantida} (¿quisiste decir 'cantidad'?)`);
  `\${` deja `${` tal cual. Solo se reemplaza en reglas de un paquete, que usan `extends` o que declaran `params`: una
  regla de antes con `${` en un texto no cambia.
- **Versión y migraciones:** el plugin recuerda la última versión cargada de cada paquete en `data/packs.yml`. La
  primera vez solo la guarda; al subir de versión corren, en orden, las `migrations` mayores que la guardada y menores
  o iguales que la nueva (acciones de servidor, con `var.from` y `var.to`), y la consola dice
  `Paquete recompensas: 1.0.0 → 1.1.0 (1 migraciones)`. Bajar de versión avisa y no corre nada. Si `requires` no se
  cumple, el paquete no carga y el error dice qué falta y qué versión hay.
- **`/omni pack list|info|enable|disable <paquete>`** (permiso `omnievents.command.pack`): la lista con versión,
  reglas, plantillas y errores; el detalle con parámetros, requisitos, migraciones y versión guardada; activar o
  desactivar todas sus reglas hasta el próximo `reload` (para dejarlo fijo, `enabled: false` en `pack.yml`).
- **Mover una regla a un paquete** le cambia el nombre (`diaria` → `recompensas:diaria`): sus enfriamientos, usos y
  estado guardados quedan con el nombre viejo.

## Desde ConditionalEvents
La [spec 014](../../docs/sdd/specs/014-omnievents-paridad-conditionalevents/spec.md) comparó OmniEvents fila por fila
con el jar de ConditionalEvents 4.65.1 (51 eventos, 53 acciones, destinos, variables, opciones y comandos) y la
[015](../../docs/sdd/specs/015-omnievents-paridad-conditionalevents-2/spec.md) cerró lo que quedaba. Resumen:

| De ConditionalEvents | En OmniEvents | `/omni import` |
|---|---|---|
| Los 49 eventos con equivalente (de 51), incluidos `item_repair`, `citizens_right_click_npc`, `wgevents_region_*`, `player_statistic`, `player_change_air` y `player_tab_complete` | El mismo disparador o su par (`player_leave` → `player_quit`, `repetitive` → `timer`…) | Se traduce, con las variables de cada evento |
| `centered_message`, `firework`, `json_message`, `title`, `actionbar`, `playsound` (también con ubicación), `give_item` y `drop_item` (con `slot`, `flags`, `durability`, `item_model`, `skull_owner`…), `remove_item` (con `name:` y `lorecontains:`), `remove_item_slot`, `summon` (con `custom_name`, `health`, `amount`, `equipment`…), `particle` (con `location` y `force`), `set_block` (con `block_data`), `set_item`, `tab_complete`, `keep_items`, `set_event_xp`, `execute_action_group`, `throw_*` y las demás acciones comunes | Su acción (`set_item` → `set_caught_item`; `execute_action_group` → `random` con `weight`) | Se traduce |
| `%random_player%`, `%randomword_a-b%`, `%is_number_x%`, `%block_at_x_y_z_mundo%` | `pick(players()).name`, `pick([...])`, `is_number(x)`, `block_at(mundo, x, y, z).type` | Se traduce |
| `to_all`, `to_target`, `to_world`, `to_player`, `to_range` (con su booleano), `to_condition` | `to` y `where` | Se traduce; dentro de `to`, `%player%` es el jugador del evento (`source`) y `%to:x%` el destinatario |
| `execute grupo{%v%=x}`, grupos `cooldown`/`one_time`, `prevent_cooldown_activation` | `if`/`else`, `on_cooldown`/`on_limit`, `no_cooldown` | Se traduce |
| `cooldown` y `conditionalevents.bypasscooldown.<evento>` | `cooldown` y `cooldown_bypass` | El permiso de CE sigue sirviendo |
| `/ce reset <jugador>|* <evento>|all`, `/ce interrupt`, `silent:true` | `/omni cooldown reset <regla|*> [jugador|*|global]` (y `limit reset`), `/omni interrupt <regla|*> [jugador]`, `-s` | — ||* <evento>\|all` | `/omni cooldown reset <regla\|*> [jugador\|*\|global]` (y `limit reset`) | — |
| `remove_item` con `datavalue:` (1.12), `custom_model_component_data_*`, `vector` | No: legado de versiones viejas; componentes sin API estable; sin documentar en CE | Queda «REVISAR» con una nota |
| `player_command_as_op`, ProtocolLib/PacketEvents, DiscordSRV | No: escalada de permisos; leer paquetes; otra integración | Queda «REVISAR» con el motivo |

## Comandos (`/omni`, alias `/oe`)
`reload` · `validate` · `list [filtro]` · `info <regla>` · `enable|disable <regla>` · `debug <regla|all|off|export>` ·
`check <regla> [jugador]` · `test [regla]` · `pack list|info|enable|disable [paquete]` · `menu list|open <jugador> <menú>|close <jugador>` · `run <regla> <jugador> [--simular]` · `state <regla>` · `cpu [on|off|reset]` ·
`eval <expr>` · `call <regla> [jugador] [k=v…]` · `interrupt <regla|*> [jugador]` (corta lo que falta de las ejecuciones que esperan un `delay`) · `var get|set|del|list <jugador|global> [nombre] [valor] [--expire 10m]` ·
`data [bloque|entidad|mano]` ·
`cooldown reset <regla|*> [jugador|*|global]` · `limit reset <regla|*> [jugador|*|global]` · `stats` · `triggers` · `actions` ·
`item save|delete|list` · `import conditionalevents` · `selftest` · `schema` (esquema JSON y referencia, [arriba](#editor-autocompletado-y-errores-en-vs-code)).
`-s` en `call`, `enable`, `disable` e `interrupt` no manda la respuesta (para usarlos desde `console:` en otras reglas).
Permisos: `omnievents.command.<sub>` (por defecto solo OP) o `omnievents.command.*`. Cómo se ve cada uno: [galería](../../docs/omnievents/galeria.md#comandos-de-omni).

`enable|disable` duran hasta el próximo `reload` y se pueden encadenar sin costo: si la regla no es un comando, no se
toca el árbol de comandos de los jugadores. Si es una regla `trigger: command`, su comando desaparece del
autocompletado y deja de funcionar («comando desconocido») sin desregistrarse, y vuelve al reactivarla
([spec 005](../../docs/sdd/specs/005-omnievents-comandos-sin-resync/spec.md)).

## Diagnóstico
- `/omni debug mi_regla`: en cada disparo ves cada condición con sus valores (`player.level (3) >= 10 → false`),
  la rama que se tomó y cada acción.
- `/omni check mi_regla Ana`: revisa la regla **ahora** para Ana sin ejecutar nada: mundo, permisos, **todas** las
  condiciones con sus valores (también las que vienen después de la que falla, y cuántas se cumplen de un grupo
  `any`), usos y enfriamiento, y la rama que correría (`→ correría: deny de conditions[1] (1 acciones)`). Si la regla
  usa datos de un evento (`block`, `message`…), avisa que valen nulo en la revisión.
- `/omni run mi_regla Ana --simular`: recorre la regla como un disparo real y **lista** cada acción, en orden, sin
  aplicarla (`• mi_regla.actions[0] message: …`). Los `if`, bucles, `when`, `to:` y `random` se evalúan con los
  valores actuales; `delay` no espera y `call` se simula por dentro. No cuenta usos ni enfriamientos ni cambia
  variables: una condición que dependa de un `set` anterior ve el valor de antes.
- `/omni run mi_regla Ana` (sin `--simular`): la dispara de verdad, como si la activara Ana, con cualquier disparador
  (queda en la consola quién la corrió).
- `/omni debug export`: guarda las últimas trazas de `debug` y simulaciones en `plugins/OmniEvents/trazas/<fecha-hora>.txt`,
  con las versiones, los problemas de la última carga y el YAML de cada regla que aparece. Sin red: compártelo tú si
  pides ayuda (revísalo antes: lleva nombres y mensajes de jugadores).
- `/omni stats`: disparos, aciertos, errores y tiempo promedio y máximo por regla; oyentes y tareas activos (también
  los de estado), y revisiones, cambios y jugadores dentro de cada regla `state`.
- Los errores de configuración dicen archivo, ruta y causa, con «¿quisiste decir…?».

![Salida de /omni debug al romper una mena de diamante: cada condición con sus valores, la rama y cada acción](../../docs/img/omnievents/comando-debug.png)

![Salida de /omni validate con un disparador mal escrito y la sugerencia «¿quisiste decir player_join?»](../../docs/img/omnievents/comando-validate.png)

## Rendimiento: /omni cpu y budget
- `/omni cpu on` enciende el perfilador; `/omni cpu` muestra, de mayor a menor, cada regla con su tiempo promedio por
  disparo (µs) y el % estimado del hilo principal, sus 3 condiciones o acciones más caras, y el costo por disparador.
  `off` lo apaga (lo medido se conserva) y `reset` lo reinicia. Mide 1 de cada `profiler.sample-every` disparos de cada
  regla (10 por defecto); apagado no mide nada.
- `budget: 0.5ms` en una regla: si su promedio reciente pasa el presupuesto, avisa en la consola, como mucho una vez
  cada `profiler.budget-warn-every` (5 min). No cuenta los primeros 10 disparos (calentamiento de Java) y avisa desde el
  disparo 20. `/omni cpu` lista las reglas con presupuesto y si lo pasan, aun con el perfilador apagado.
```yaml
# config.yml (claves nuevas en 1.1.0; si faltan se usan estos valores)
profiler:
  sample-every: 10
  budget-warn-every: 5m
debug:
  export-buffer: 200      # trazas que guarda /omni debug export
```

## PlaceholderAPI
`%omni_var_<n>%` · `%omni_gvar_<n>%` · `%omni_cooldown_<regla>%` · `%omni_uses_<regla>%` · `%omni_state_<regla>%` (`true`/`false`) · `%omni_enabled_<regla>%` (`true`/`false`).

## API
```java
OmniEventsAPI.registerFunction("doble", 1, 1, (ctx, a) -> Values.num(a[0]) * 2);
OmniEventsAPI.registerAction("mi_accion", args -> Action.of(ctx -> ctx.requirePlayer().sendMessage("hola")));
// Con documentación para /omni schema (sin ella aparecen en «otros plugins» con una descripción genérica):
OmniEventsAPI.registerFunction("doble(x)", "El doble de x.", 1, 1, (ctx, a) -> Values.num(a[0]) * 2);
OmniEventsAPI.registerAction("saludar", new Actions.Doc("mi plugin", "Saluda al jugador.", "texto", Actions.Shape.SCALAR,
        Map.of(), Map.of(), "saludar: hola"), args -> Action.of(ctx -> ctx.requirePlayer().sendMessage("hola")));
OmniEventsAPI.registerProperty(Player.class, "clan", p -> MiPlugin.clanDe(p));
OmniEventsAPI.callRule("recompensa", jugador, Map.of("cantidad", 5));
```

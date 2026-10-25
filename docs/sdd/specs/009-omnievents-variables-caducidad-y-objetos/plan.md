# Plan 009 — Diseño técnico

## 1. Arquitectura

### 1.1 Caducidad en `DataStore` (H1, H3 de la 001 siguen igual)
- `DataStore.Holder` gana `expires` (nombre → hora de caducidad en ms de época) y una API que la respeta:
  `get(nombre)` (nulo si ya caducó, aunque el barrido no haya pasado), `put(nombre, valor, caducaEn)` (0 = no
  caduca; quita una caducidad anterior), `remove(nombre)`, `expiresAt(nombre)` y `live()` (copia de las vivas). Todo
  el código que leía `vars()` directo (`ExecContext`, `pvar_of`, `/omni var`, PlaceholderAPI) pasa por esa API.
- El reloj es inyectable (`LongSupplier`, `System::currentTimeMillis` por defecto) para probar sin esperar.
- **Cola de caducidad:** `PriorityBlockingQueue<Pending(at, dueño, nombre)>` (dueño = UUID del jugador o nulo para
  el servidor). Se agrega al hacer `put` con caducidad y al cargar un archivo con caducidades vivas (puede ser en el
  hilo de pre-login: la cola es concurrente). `sweep()` corre en el hilo principal: saca las entradas vencidas y,
  si siguen siendo la caducidad vigente de un holder cargado (una entrada vieja de un `set` posterior o de un
  jugador que salió se descarta), borra la variable, marca el holder y devuelve `Expired(scope, dueño, nombre,
  valor)`. Sin entradas vencidas cuesta un `peek`.
- Al cargar un archivo, las variables ya caducadas se descartan (H1 CA3).

### 1.2 Barrido y disparador `var_expire` (H2)
- `OmniEvents` corre `sweep()` cada 20 ticks. Por cada `Expired`: `StateService.varChanged(...)` (los `state` que
  la leen se revisan, H2 CA2) y, para cada regla `var_expire` que acepta esa variable, `rules.fire(...)` con raíces
  `variable`, `value`, `scope`, y `player`/`world`/`location` si es una `pvar` de un jugador conectado. Una `pvar`
  de alguien que no está conectado no dispara (no hay a quién).
- `TriggerType.Kind.VAR_EXPIRE`, disparador `var_expire` (grupo «servidor»). Opción `variables` (lista de
  nombres; sin ella, todas), validada al cargar como lista de textos.

### 1.3 Archivos de `data/`: formato 2 y migración (H4)
```yaml
format: 2
vars:
  racha: 3.0
expire:            # nuevo: nombre → hora de caducidad (ms de época)
  racha: 1761400000000
cooldowns: { … }   # sin cambios
uses: { … }        # sin cambios
```
- Un archivo sin `format` es de formato 1 (1.0–1.3) y carga igual (sección `expire` ausente = nada caduca).
- **Respaldo único:** al construir `DataStore`, si no existe la marca `data/.formato-2` y hay datos (`global.yml`
  o algo en `players/`), copia `data/` (sin otros respaldos) a `data/respaldo-formato1-<aaaaMMdd-HHmmss>/` y escribe
  la marca. Si la copia falla, se avisa en el log y **no** se escribe la marca (se reintenta al siguiente arranque);
  los datos no se tocan (los archivos de formato 1 se reescriben recién al guardar, y siguen siendo legibles por la
  1.3 salvo la caducidad).
- Un archivo con `format` mayor que 2 se carga en modo solo lectura: se avisa y el holder nunca se escribe (H4 CA3).

### 1.4 Variables de objetos (`data/ObjectData`, H3)
- Soporta `Block`, `Entity` (incluye jugadores) e `ItemStack` no vacío. Contenedor:
  - entidad: `entity.getPersistentDataContainer()`;
  - ítem: `stack.editMeta(m -> …)` (Paper; el cambio queda en el ítem si es el del inventario, como `player.item`
    o el ítem del evento que es un espejo del inventario);
  - bloque: el PDC de su chunk, clave `omnievents:b.<x>.<y>.<z>` (coordenadas del bloque).
- Dentro, un `TAG_CONTAINER` con `v` (valores) y `e` (caducidades, `LONG`). Cada variable es una clave
  `omnievents:<nombre codificado>`. Las claves de Bukkit solo admiten `[a-z0-9._-/]`, así que el nombre se
  codifica de forma reversible: `[a-z0-9_]` tal cual, una mayúscula → `.` + minúscula, cualquier otro carácter
  (`ñ`, `á`) → `-<hex>-`. Tipos: número → `DOUBLE`, texto → `STRING`, booleano → `BOOLEAN`; otro valor se guarda
  como texto (`Values.str`).
- Límites (`config.yml`): `object-data.max-text-length` (256 por defecto) y `object-data.max-vars` (32 por
  defecto, por objeto). Pasarse → `ActionException` con el motivo (H3 CA4).
- Caducidad: al leer, una variable vencida vale nulo; se borra en la siguiente escritura al mismo objeto (leer
  puede pasar en el hilo del chat, que no debe escribir).
- **Limpieza de bloques** (`data/BlockDataCleaner`, oyente registrado solo si alguna regla escribe `.data`, H3 CA3):
  a prioridad `MONITOR` con `ignoreCancelled`, borra la clave del bloque en `BlockBreakEvent`, `BlockExplodeEvent`
  y `EntityExplodeEvent` (cada bloque de la lista), `BlockBurnEvent`, `LeavesDecayEvent`, `BlockFadeEvent`,
  `EntityChangeBlockEvent`, `BlockPistonExtendEvent` y `BlockPistonRetractEvent` (bloques movidos). Con la prioridad
  `MONITOR` las reglas del evento ya leyeron los datos.

### 1.5 Lenguaje de expresiones
- `Node.DataRef(target, nombre)`: el analizador convierte `X.data.<nombre>` (sin paréntesis después) en
  `DataRef`. Al evaluar: si `X` es bloque, entidad o ítem → `EvalContext.objectVar(X, nombre)`; si no, lo de antes
  (`Properties.read(Properties.read(X, "data"), nombre)`), para no romper `event.data.algo` de otros plugins.
- `ttl(variable)` es una función perezosa: recibe el nodo. Solo acepta `VarRef` de `pvar`/`gvar` o `DataRef`; lo
  demás es error al cargar con la forma correcta. Llama al servicio `ttl` del contexto.
- `StateDeps`: `DataRef` y `ttl()` son no deducibles (dependen del objeto y del reloj): un `state` que los usa pide
  `check_every`. `Expression.collect` recorre el objetivo de `DataRef`.

### 1.6 Acciones
- `set`: el patrón actual (`var|pvar|gvar.nombre op expr`) o, si no, `<expresión>.data.<nombre> op expr` (el
  objeto es una expresión compilada al cargar; el primer `.data.` seguido de un nombre y un operador separa el
  objeto del resto). Clave hermana `expire` (plantilla de duración; fija → se valida al cargar; en `var` → error).
- `unset`: también `<expresión>.data.<nombre>`.
- `ExecContext.setVar(scope, nombre, valor, caducaEn)` mantiene el aviso a `StateService.varChanged` (spec 008).
- Una acción que escribe `.data` marca la regla (`Rule.writesObjectData`); `OmniEvents.reload` registra o quita el
  `BlockDataCleaner` según haya alguna.

### 1.7 Comandos
- `/omni var list|get` muestran `(caduca en 4m 12s)`; `/omni var set <quién> <nombre> <valor…> [--expire 10m]`.
- `/omni data [bloque|entidad|mano]` (por defecto: lo que mira, o la mano): lista las variables con su caducidad.
  Solo jugadores; permiso `omnievents.command.data` (`op`), autocompletado.

### 1.8 Documentación (spec 007)
- `var_expire` con `.doc`, `RuleOptions` (`variables` solo para `var_expire`), `Actions.Doc` de `set` con la clave
  `expire` y la forma `.data`, `ttl` en `Functions`. La forma `.data` se explica en la descripción de `set` y en el
  README (no es una función ni una opción). `./gradlew :omnievents:schema`.

## 2. Contrato de configuración
```yaml
# rules/ejemplos/variables.yml
racha_sube:
  trigger: player_kill
  conditions: "target.type == 'ZOMBIE'"
  actions:
    - set: "pvar.racha = default(pvar.racha, 0) + 1"
      expire: 2m                                   # H1: se pierde tras 2 min sin matar
    - actionbar: "<gold>Racha x{pvar.racha} <gray>· {round(ttl(pvar.racha))}s"

racha_perdida:
  trigger: var_expire                              # H2
  variables: [racha]
  actions:
    - message: "<red>Perdiste tu racha de {value}."

cofre_con_dueno:
  trigger: block_place
  conditions: "block.type == 'CHEST'"
  actions:
    - set: "block.data.dueño = player.name"        # H3: vive en el chunk; se borra al romperse

cofre_ajeno:
  trigger: player_interact
  conditions:
    - "block != null && block.type == 'CHEST'"
    - expr: "default(block.data.dueño, player.name) == player.name"
      deny:
        - cancel
        - message: "<red>Este cofre es de {block.data.dueño}."

ligar:
  trigger: command
  command: ligar
  conditions:
    - expr: "player.item.type != 'AIR'"
      deny: [message: "<red>Toma un ítem en la mano."]
  actions:
    - set: "player.item.data.dueño = player.name"   # viaja con el ítem
    - message: "<green>Ligado a {player.item.data.dueño}."
```
`config.yml`:
```yaml
object-data:
  max-text-length: 256   # largo máximo de un texto guardado en un bloque, entidad o ítem
  max-vars: 32           # variables por objeto
```

## 3. STRIDE
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| **S**uplantación | Un jugador en creativo puede crear un ítem con componentes arbitrarios (también el PDC) y fingir `item.data.ligada`. | Documentado en el README: no confiar en datos de ítems para jugadores en creativo; los de bloques y entidades no los edita el jugador. |
| **T**ampering | Un archivo de `data/` editado a mano con una caducidad no numérica. | Se ignora esa caducidad (la variable queda sin caducar) y se avisa; el resto carga (II.6). |
| **R**epudio | Asignaciones por comando. | `/omni var set` sigue registrando quién y qué en el log. |
| **I**nformación | Ver datos de objetos ajenos. | `/omni data` solo con `omnievents.command.data` (`op`). |
| **D**enegación de servicio | Una regla que guarda textos enormes o miles de variables en un ítem (paquetes grandes) o en un chunk. | Límites por objeto (`max-text-length`, `max-vars`); bloques limpiados al romperse; la cola de caducidad se mira con `peek` y solo procesa lo vencido. |
| **D**enegación de servicio | Muchas `pvar` con `expire` muy corto. | Una entrada por `set`; las viejas se descartan al sacarlas; el barrido es 1 vez por segundo. |
| **E**levación | Texto del jugador guardado en un objeto y luego interpolado. | Sigue las reglas de escape de la 001 (II.2): un valor es dato, no MiniMessage ni comando. |
| Pérdida de datos | Volver a la 1.3.0 tras actualizar. | Respaldo único de `data/` antes de la primera escritura; un formato más nuevo no se sobrescribe. |

## 4. Pruebas
- Unitarias (`data/DataStoreTest`, carpeta temporal y reloj falso): caducidad al leer, `put` sin caducidad la
  quita, persistencia de `expire`, descarte al cargar, `sweep` (vigente, vieja, jugador descargado), formato 1 →
  respaldo y marca, respaldo no se repite, formato 3 no se sobrescribe.
- `data/ObjectDataTest`: codificación de nombres (ida y vuelta, `ñ`, mayúsculas), límites.
- `DatosObjetoExprTest`: `X.data.y` → `DataRef`, respaldo a la propiedad `data`, `ttl` con `pvar`/`gvar`/`.data` y
  error con otra cosa. `VariablesCargaTest`: `expire` en `var` es error, duración inválida, `set` de `.data`,
  `var_expire` con `variables`; `.data` y `ttl` no deducibles en un `state`. `SchemaTest` al día.
- `/omni selftest` (compuerta 2): variables en un ítem y en el chunk del spawn sobre el PDC real.
- e2e (`tests-e2e/omnievents`): caducidad con aviso `var_expire` y `on_exit` de un estado; `.data` en un bloque
  (se lee, se borra al romperlo); `.data` en el ítem de la mano (viaja con el ítem); `/omni data`.

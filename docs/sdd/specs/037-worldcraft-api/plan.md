# Plan 037 — API para otros plugins (Worldcraft 0.13.0)

Spec: [spec.md](spec.md) · Respuestas del dueño: solo la API Java y el evento; sin CoreProtect ni puente con WorldEdit.
Solo API de Paper 1.21.4, sin NMS. [ADR 0008](../../../adr/0008-api-de-worldcraft.md): qué es contrato y cómo cambia.

## 1. Diseño

### 1.1 El contrato (`dev.leo.worldcraft.api`, solo tipos de Bukkit y del JDK)
- `WorldcraftApi` (interfaz): `static get()` (por el `ServicesManager`; sin Worldcraft habilitado, `IllegalStateException`
  con un texto claro), `apiVersion()` (= `API_VERSION` = 1), `pluginVersion()`, `selection(Player)` →
  `Optional<Selection>`, `select(Player, World, BlockVector, BlockVector)`, `clearSelection(Player)`, `edit(Plugin)` →
  `EditBuilder`.
- `Selection` (interfaz): `world()`, `shape()` (`cuboid`, `poly`, …), `min()`, `max()` (`BlockVector`), `volume()`,
  `contains(x, y, z)`. Es una foto: no cambia si el jugador marca otra cosa después.
- `EditBuilder`: `as(Player)`, `in(World, BlockVector, BlockVector)`, `in(Selection)`, `mask(String)`; y lo que la
  corre: `set(String)`, `replace(String, String)`, `paste(String esquema, World, BlockVector, int giro, boolean aire)`,
  `undo(int)`. Cada una devuelve `CompletableFuture<EditResult>`.
- `EditResult` (record): `id` (número en el historial, −1 si no corrió), `kind`, `changed`, `status` (`DONE`,
  `CANCELLED`, `REFUSED`) y `reason` (por qué se rechazó: límite, mundo, evento, API apagada). Un patrón, máscara o
  esquema mal escrito completa el futuro **con excepción** (`IllegalArgumentException` con el mensaje de siempre): es
  un error del que programa; un rechazo (límites, evento) es un resultado.
- `event.WorldcraftEditEvent` (cancelable, en el hilo principal): `getActor()` (el jugador o la consola; null si es un
  plugin a su nombre), `getActorId()`, `getActorName()`, `getPlugin()` (el que la pidió por la API, o null), `getKind()`,
  `getDetail()`, `getWorld()`, `getMinimum()`, `getMaximum()`, `getVolume()`, `overlaps(World, BlockVector, BlockVector)`,
  `setCancelled`, `setCancelReason(String)`. `event.WorldcraftEditDoneEvent`: lo mismo sin cancelar, más `getId()`,
  `getChanged()` y `isCompleted()`.

### 1.2 Una sola puerta (`Edits`)
- `request` (comandos) separa sus revisiones en `refusal(op, lim, owner)` (mundo, tope, bloques prohibidos, entidades,
  obras a la vez) que devuelve el mensaje o null. Después de los límites y **antes** de la vista previa y de la
  confirmación, `allowed(...)` dispara `WorldcraftEditEvent`; cancelada, «Another plugin stopped this edit[: motivo]».
- `submit(Caller, Op)` (API): las mismas revisiones y el mismo evento; sin vista previa ni confirmación; corre con
  `run(...)` y completa el futuro al terminar. `Caller` = dueño (UUID), nombre, el `CommandSender` que recibe mensajes
  (el jugador con `as`, o null), los límites ya resueltos y el plugin.
- `run` y `undo` reciben un `Consumer` de fin (el futuro) y disparan `WorldcraftEditDoneEvent`. `undo` dispara
  `WorldcraftEditEvent` (`undo`/`redo`, la caja de la obra) antes de cada obra que deshace; cancelada, se para ahí.
- Un plugin a su nombre: dueño `UUID.nameUUIDFromBytes("worldcraft-plugin:" + nombre)`, nombre `#Nombre`; límites
  `limits.resolve(permiso → grupo ∈ api.plugin_groups)` (`default` siempre); sin mensajes en el chat (solo el futuro).
- Desde otro hilo, cada llamada pasa al hilo principal (`runTask`); `selection`/`select` piden el hilo principal.

### 1.3 Las obras de la API (`paper/Api`)
- `set`/`replace`: lo mismo que `/wc set` y `/wc replace` (`RegionSource` sobre la región: caja o la selección de
  cualquier forma; la máscara con `maskNear`). Una máscara que mira alrededor (`above`, `exposed`…) se rechaza con
  `IllegalArgumentException` (necesita leer antes; fuera de alcance).
- `paste`: `Schematics.loadClipboard(nombre)` (el mismo camino que `/wc schem load`, extraído: disco y NBT en su hilo,
  la paleta en el principal), girado `giro` grados (múltiplo de 90), y la obra de `/wc paste` (`PasteOp`, extraída del
  comando: bloques, datos, entidades del esquema).
- `undo(n)`: las últimas n del dueño, como `/wc undo n`; el resultado suma los bloques.

### 1.4 Plugin de ejemplo (`plugins/worldcraft-api-example`, `WorldcraftApiExample` 1.0.0)
- `plugin.yml` con `depend: [Worldcraft]`; `compileOnly(project(":worldcraft"))`. Comando `/wcx` (`worldcraftexample.use`,
  OP): `selection`, `select <x1> <y1> <z1> <x2> <y2> <z2>`, `fill <patrón>` (como el jugador, sobre su selección),
  `box <mundo> <x1> <y1> <z1> <x2> <y2> <z2> <patrón>` y `paste <esquema> <x> <y> <z> [giro]` (como el plugin, también
  desde la consola), `undo`, `reload`. Responde con el `EditResult`.
- `config.yml`: `protected:` (zonas con nombre, mundo, `from`, `to`) — un oyente de `WorldcraftEditEvent` cancela lo
  que las toque («This area is protected by WorldcraftApiExample (spawn)»); `log_edits` — un oyente de
  `WorldcraftEditDoneEvent` lo anota en la consola.
- `:worldcraft:dist` también arma su jar (`finalizedBy`); la suite lo instala con `jarsExtra` (el más nuevo de `dist/`).

## 2. Contrato de configuración (`config.yml`)
```yaml
# Other plugins (minigames, islands, protection) can use Worldcraft's API. Their edits go through the same limits,
# history and undo as yours. Events let a protection plugin stop any edit (commands too).
api:
  enabled: true           # false = other plugins cannot edit through Worldcraft (events still fire)
  plugin_groups: []       # limit groups for edits a plugin makes on its own (not as a player); "default" always applies
```
`plugin_groups` con un grupo que no existe avisa con «did you mean»; claves desconocidas igual. Un `config.yml` de la
0.12.0 sin la sección carga igual (todo de fábrica).

## 3. STRIDE
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| **E**levación: un jugador usa un plugin con la API para saltar sus límites | Obras enormes o bloques prohibidos | `as(player)` resuelve **sus** límites (mundos, tope, prohibidos, obras a la vez); a nombre del plugin, los grupos que el dueño pone en `api.plugin_groups` (de fábrica solo `default`) |
| **D**enegación: un plugin encola miles de obras o una enorme | Tick largo, disco del historial | El mismo planificador (8 ms por tick para todo el plugin, turnos), `max_jobs` del grupo, tope de bloques, historial con `max_per_player` |
| **T**ampering: un plugin con la API pisa una zona protegida | Daño | El evento se dispara para toda obra (también las de la API y `undo`): un plugin de protección la cancela |
| **T**: `paste("../../x")` | Leer fuera de la carpeta | El mismo `SchemFiles.clean` que `/wc schem load` (sin `/`, `\` ni `..`) |
| **R**epudio | — | Toda obra queda en el historial con su dueño (el jugador o `#Plugin`); `WorldcraftEditDoneEvent` deja que otro plugin la anote |
| **I**nformación | — | La selección solo la lee código que ya corre en el servidor (un plugin puede todo de por sí) |
| Un oyente que tira una excepción | Obra a medio pedir | Bukkit atrapa la excepción del oyente y la anota; la obra sigue como si no la hubiera cancelado |

## 4. Pruebas
- Unitarias: `ApiTest` (Java puro donde se puede: `EditResult`, el dueño de un plugin, la caja de solapamiento del
  evento, los grupos de `plugin_groups`, que el paquete `api` solo use tipos de Bukkit y del JDK), `ConfigTest` (sección
  `api`), `CommandTest` (mensajes nuevos). Una prueba que la puerta dispara el evento antes de la vista previa necesita
  el servidor: va a la 2 y a la 3a.
- Compuerta 2: `selftest` con la API (el servicio registrado, una obra a nombre de Worldcraft hecha y deshecha, una
  cancelada por un oyente de prueba sin tocar nada).
- Suite 3a: con el plugin de ejemplo: la selección leída y puesta por la API; `fill` como Ana (en su historial, `/wc undo`
  la deshace); `box` y `paste` de un esquema como el plugin (desde la consola), un patrón mal escrito, el tope de
  `default`, `undo`; la zona protegida detiene `/wc set` (también con `--preview`), `/wc undo` y la API, y fuera de la zona
  todo sigue.

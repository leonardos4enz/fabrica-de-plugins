# Plan 012 — Diseño técnico

## 1. Arquitectura

### 1.1 Integraciones opcionales (`hook/`, constitución VI.2)
Cada integración es una interfaz propia de OmniEvents con una implementación «nula» y otra que habla con el plugin.
La implementación real es la única clase que nombra clases del otro plugin y solo se instancia si
`getPlugin("<Plugin>") != null` al habilitar. Así OmniEvents carga igual sin ellos y las unitarias usan dobles.

| Interfaz | Real | Qué hace |
|---|---|---|
| `hook/Economy` | `hook/VaultEconomy` (VaultAPI 1.7.1, `compileOnly`) | `available()`, `balance(p)`, `withdraw(p, n)`, `deposit(p, n)`, `format(n)`. El proveedor se busca en el `ServicesManager` **en cada uso** (una búsqueda en un mapa): una economía que se registra después de OmniEvents (EssentialsX sin orden de carga con nosotros) funciona |
| `hook/Regions` | `hook/WorldGuardRegions` (worldguard-bukkit 7.0.13, `compileOnly`) | `at(Location)`: ids de las regiones aplicables, sin `__global__`, ordenados por prioridad descendente |
| `hook/Npcs` (sin dependencia de compilación) | — | `isNpc(Entity)`: metadato `NPC` que pone Citizens. `info(Entity)`: mapa `{id, name, entity}`; el id se lee de `CitizensAPI.getNPCRegistry().getNPC(e).getId()` por reflexión cacheada (el repositorio Maven de Citizens está detrás de un desafío de Cloudflare y no sirve para gradle). Sin Citizens, el id es nulo |

`OmniEvents` expone `economy()` y `regions()`; `plugin.yml` suma `softdepend: [PlaceholderAPI, Vault, WorldGuard,
Citizens]`. `/omni stats` gana una línea «Integraciones: Vault (EssentialsX), WorldGuard, Citizens» con lo detectado.

### 1.2 Dinero (`Actions`, `Functions`)
- `give_money: <expresión>` y `take_money: <expresión>` (grupo «jugador»): cantidad > 0 o error de la acción;
  `take_money` revisa el saldo primero y falla sin retirar si no alcanza. Sin economía: `ActionException` con el texto
  de H1 CA2. Ambas en `ActionNode.SANDBOXED`: en una prueba mueven el saldo del `Sandbox`.
- Funciones `money()` (saldo del jugador o nulo) y `money_format(n)` (texto de la economía; sin economía, el número).
  Las dos van por `ctx.service("money")` / `service("money_format", n)`; en el `Sandbox`, el saldo de `given.money`.
- Al cargar, una regla que usa `give_money`, `take_money`, `money()` o `cost.money` sin Vault instalado da un aviso
  (no error): `RuleLoader` recibe un `Set<String>` con las integraciones presentes (vacío en las unitarias salvo que la
  prueba lo pase).

### 1.3 Costos (`rule/Cost.java`, nuevo; `RuleManager`, `RuleTester`, `RuleLoader`)
`Cost` es una lista ordenada de partes compiladas al cargar:

| Clave | Parte | Tiene | Cobra | Devuelve |
|---|---|---|---|---|
| `money` | expresión | `economy.balance(p) >= n` | `withdraw` (si falla, error) | `deposit` |
| `levels` | expresión entera | `p.getLevel() >= n` | `setLevel(l - n)` | `setLevel(+n)` |
| `exp` | expresión entera | `calculateTotalExperiencePoints() >= n` | `setExperienceLevelAndProgress(t - n)` | restablece el total anterior |
| `items` | `MATERIAL;cantidad` o lista de esos | cuenta por material (inventario y mano secundaria, sin armadura, como `remove_item`) | quita y **guarda copias de las pilas quitadas** | devuelve esas pilas (lo que no cabe cae al suelo) |
| `pvar.x`, `gvar.x` | expresión | valor numérico `>= n` (nulo = 0) | `x -= n` | vuelve al valor anterior |

Cantidad y material de `items` son plantillas (`{expr}` permitido); las cantidades se evalúan **una sola vez** en la
cotización (`Cost.Quote`) y lo cobrado es exactamente lo cotizado.

Flujo en `RuleManager.run`: mundo → permisos → condiciones → límite → enfriamiento → **costo** → `registerUsage` →
actions. Con costo:
1. `quote(ctx)` evalúa cada parte (un error de expresión es un error de la regla y no se cobra nada).
2. Si falta algo: `var.cost_missing` («200 de dinero, 2 × DIAMOND», con `money_format`), `var.cost_missing_list`
   (`[money, items]`), traza `✘ costo: …` y corre `on_cost_fail` (o nada). No cuenta uso ni enfriamiento.
3. Si alcanza: `charge(quote, ctx)` cobra en orden; cada parte cobrada apila su devolución. Si una falla (la economía
   rechaza, el inventario cambió entre la cotización y el cobro, el jugador se desconectó), se corren las devoluciones
   en orden inverso, `var.cost_missing` dice qué falló y corre `on_cost_fail`. Una devolución que falla va al log como
   `SEVERE` con el jugador, la regla y la cantidad (H2 CA3).
4. Cobrar exige el hilo principal: `cost` en un disparador asíncrono es error al cargar, y si un `call` llega desde
   otro hilo el costo falla con aviso en el log (nunca se cobra fuera del hilo principal).

`check` (`/omni check`) agrega una línea por parte (`✔ money: 800 / 500`, `✘ items DIAMOND: 1 / 3`) y la rama
`on_cost_fail`. Una simulación (`--simular`) cotiza y traza «se cobraría …» sin cobrar.

En `RuleTester.walk`, después de las condiciones, el costo se cotiza y se cobra sobre el `Sandbox` (`Cost` pregunta a
`ctx.sandbox()`): `given.money` es el saldo, `player.level` y `player.total_exp` del mapa del jugador, `given.inventory`
(`{DIAMOND: 3}`) los ítems y las variables las del caso. Al cobrar se descuentan ahí (para `assert` y `pvar.x` en
`expect`). Si falta algo la rama es `on_cost_fail` (aunque esté vacía, para que la prueba lo diga). `RuleTester`
reconoce `money`, `inventory` y `regions` como claves especiales de `given` y `testRoot` las acepta.

Validación al cargar (`regla.cost.<clave>`): clave desconocida («¿quisiste decir?», sugerencias entre `money`,
`levels`, `exp`, `items` y `pvar.`/`gvar.`), cantidad constante negativa o cero, material constante desconocido,
`cost` que no es mapa, disparador asíncrono, y partes de jugador (todas menos `gvar`) en un disparador sin jugador.
`on_cost_fail` sin `cost` es un aviso.

### 1.4 Regiones (`trigger/RegionService.java`, nuevo)
- Disparadores `region_enter` y `region_leave` (`Kind.REGION`, grupo «integraciones», raíces `player`, `world`,
  `location`, `region`, `event`). `region` es un mapa `{id, priority, world}`.
- `RegionService.register(rules)`: si hay reglas de región activas y `regions().available()`, escucha
  `PlayerMoveEvent` (solo cambio de bloque), `PlayerTeleportEvent`, `PlayerJoinEvent`, `PlayerChangedWorldEvent` y
  `PlayerQuitEvent` (limpia). Guarda por jugador el conjunto de regiones; en cada evento calcula el nuevo, dispara
  `region_leave` por cada región que sale y luego `region_enter` por cada una que entra (orden de prioridad), con el
  evento de Bukkit como `event`. Si una regla cancela el evento (`cancel`), el conjunto no se actualiza: el jugador
  sigue fuera (o dentro). Al recargar, el conjunto se recalcula sin disparar.
- `in_region('id')` y `regions()` por `ctx.service`; en el `Sandbox`, de `given.regions` (lista). Sin WorldGuard:
  falso y lista vacía; las reglas de región dan un aviso al cargar.
- Sin reglas de región no hay oyentes (III.1); `/omni stats` cuenta los de `RegionService` en «oyentes».

### 1.5 NPC (`Triggers`, `hook/Npcs`)
- `npc_click`: `PlayerInteractEntityEvent` en la mano principal con objetivo NPC. `npc_left_click`:
  `EntityDamageByEntityEvent` con atacante jugador (directo) y objetivo NPC. Raíces `npc` (mapa `{id, name,
  entity}`) y `target` (la entidad). Ambos cancelables. Se registran siempre (el filtro solo mira el metadato); sin
  Citizens nunca coinciden y la regla da un aviso al cargar.
- `is_npc(entidad)`: `Npcs.isNpc`; en el `Sandbox`, un mapa de `given` con `npc: true`.

### 1.6 Registro, esquema y referencia
- `RuleOptions`: `cost` (`Kind.COST`, nuevo: mapa con `money`, `levels`, `exp`, `items` y `pvar.*`/`gvar.*` por
  `patternProperties`) y `on_cost_fail` (`ACTIONS`); `COST_KEYS` con la descripción de cada clave. `TEST_KEYS.given`
  menciona `money`, `inventory` y `regions`; `EXPECT_KEYS.branch` suma `on_cost_fail`.
- `Triggers` con `doc` de los cuatro disparadores; `Actions.Doc` de `give_money` y `take_money`; `Functions` con
  `money`, `money_format`, `in_region`, `regions`, `is_npc`. `SchemaGenerator` y `ReferenceGenerator` aprenden
  `Kind.COST`; `./gradlew :omnievents:schema` regenera.

## 2. Contrato de configuración
```yaml
tienda_espada:
  trigger: command
  command: comprar_espada
  cost:
    money: 500                    # Vault
    items: "DIAMOND;3"            # o una lista: ["DIAMOND;3", "STICK;1"]
    levels: 5                     # niveles de experiencia
    exp: 100                      # puntos de experiencia
    pvar.fichas: 1                # variables numéricas (pvar o gvar)
  actions:
    - give_item: "DIAMOND_SWORD;1;<aqua>Espada de la tienda"
    - message: "<green>Compraste la espada. Te quedan {money_format(money())}"
  on_cost_fail:
    - message: "<red>Te falta: {var.cost_missing}"

bienvenida_spawn:
  trigger: region_enter
  conditions: "region.id == 'spawn'"
  actions: [actionbar: "<gold>Bienvenido al spawn"]

zona_vip:
  trigger: region_enter
  conditions: ["region.id == 'vip'", "!perm('rango.vip')"]
  actions: [cancel, message: "<red>Solo VIP"]

guia:
  trigger: npc_click
  conditions: "npc.name == 'Guía'"
  actions:
    - message: "<yellow>[Guía] Hola, {player.name}. Estás en {regions()}"
    - give_money: 10
```
| Clave | Dónde | Tipo | Validación |
|---|---|---|---|
| `cost` | regla | mapa | claves `money`, `levels`, `exp`, `items`, `pvar.x`, `gvar.x`; disparador síncrono; con jugador salvo `gvar` |
| `cost.money`, `levels`, `exp`, `pvar.x`, `gvar.x` | `cost` | expresión | constante > 0 (`levels`, `exp` enteros) |
| `cost.items` | `cost` | `MATERIAL;cantidad` o lista | material conocido si es constante; cantidad > 0 |
| `on_cost_fail` | regla | acciones | aviso sin `cost`; `var.cost_missing`, `var.cost_missing_list` |
| `give_money`, `take_money` | acción | expresión | aviso sin Vault |
| `region_enter`, `region_leave` | `trigger` | — | aviso sin WorldGuard |
| `npc_click`, `npc_left_click` | `trigger` | — | aviso sin Citizens |
| `given.money`, `given.inventory`, `given.regions` | `tests` | número, mapa material → cantidad, lista | — |

Sin cambios en `config.yml` ni en `data/`. Una regla sin `cost` sigue el mismo camino que en 1.6.0 (IV.5).

## 3. Hilos y rendimiento
- Todo se compila al cargar (III.2): partes del costo, materiales y cantidades constantes. Cotizar es evaluar unas
  expresiones y, con `items`, recorrer 41 casillas del inventario.
- Cobrar y devolver solo en el hilo principal (III.3). Vault: las economías comunes responden en el hilo principal
  desde memoria; no se cachea el saldo.
- `RegionService` solo con reglas de región activas; en `PlayerMoveEvent` mira solo los cambios de bloque y hace una
  consulta a WorldGuard (la misma que hace WorldGuard en su propio oyente de movimiento).
- Los disparadores de NPC filtran por un metadato (un `HashMap`); la reflexión del id se resuelve una vez.

## 4. Persistencia
Sin datos nuevos. Las variables que cobra `cost` se escriben con `ctx.setVar` (marca sucios y avisa a los estados como
cualquier `set`).

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación | Un jugador hace clic en una entidad con el nombre de un NPC | `npc_click` exige el metadato `NPC` de Citizens; `npc.name` es del NPC, no de un ítem o cartel del jugador |
| Manipulación | El jugador suelta los ítems entre la cotización y el cobro para pagar menos | Cotizar y cobrar pasan en el mismo tick en el hilo principal; si al cobrar falta algo, se devuelve todo y corre `on_cost_fail` |
| Manipulación | Una cantidad negativa en `cost` o `take_money` regala dinero | Constantes ≤ 0 son error al cargar; en ejecución, una cantidad ≤ 0 es error de la acción o del costo y no se cobra |
| Repudio | Se cobró y la acción no se entregó, o una devolución falló | Traza en `/omni debug`; las devoluciones fallidas van al log (`SEVERE`) con jugador, regla y cantidad |
| Divulgación | `/omni check` muestra el saldo del jugador | Permiso `omnievents.command.check` (`op`), sin cambios |
| Denegación de servicio | Reglas de región con mucho movimiento | Solo cambios de bloque, una consulta por evento y oyentes solo con reglas activas |
| Elevación de privilegios | Un cobro fuera del hilo principal corrompe el inventario | `cost` asíncrono es error al cargar y el cobro se niega fuera del hilo principal |
| Elevación de privilegios | La reflexión sobre Citizens expuesta a la configuración | La reflexión es interna (una llamada fija para el id), no se expone a las expresiones (II.1) |

## 6. Pruebas
- **Unitarias** (sin servidor): `Cost` con un `Economy` falso y un `Sandbox` (todo o nada, devolución en orden inverso
  cuando falla el retiro, cantidades evaluadas una vez, `cost_missing`), validación de `cost` con rutas, avisos sin
  integraciones, `tests:` con `given.money`/`inventory`/`regions` y rama `on_cost_fail`, funciones nuevas en el
  `Sandbox`, el ejemplo con sus pruebas, esquema regenerado.
- **En el juego** (compuerta 3a): la suite instala, solo en el servidor de prueba y con sha256 verificado, Vault,
  EssentialsX, WorldEdit, WorldGuard y Citizens (versión por versión de Paper, `tests-e2e/lib/plugins-extra.mjs`).
  Escenarios: costo que alcanza (dinero de EssentialsX y diamantes cobrados) y que no (nada cobrado, `on_cost_fail`
  con el texto), `give_money`/`money()`, entrar y salir de una región creada por consola (`rg define` con las
  coordenadas en un archivo de regiones escrito antes de arrancar) y cancelar la entrada, y clic derecho e izquierdo en
  un NPC creado por consola.

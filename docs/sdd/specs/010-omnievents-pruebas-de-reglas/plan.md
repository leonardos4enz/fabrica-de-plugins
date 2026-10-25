# Plan 010 — Diseño técnico

## 1. Arquitectura

### 1.1 Una caja de arena en el contexto (`action/Sandbox.java`, nuevo)
La prueba recorre la regla con el mismo código que un disparo real (condiciones, `Execution`, `ActionNode`), pero con
un `Sandbox` colgado del `ExecContext`. Así lo que se prueba es el motor de verdad, no una copia.
- `Sandbox` guarda el estado falso del caso: `pvars`, `gvars` (con su caducidad, para `ttl()`), las variables de
  objetos (`IdentityHashMap<objeto, mapa>`), los placeholders dados, los **efectos** registrados
  (`Effect(tipo, ruta, texto)`), `cancelled`, los errores de las acciones, un `Random` con semilla, los topes de
  bucles y de `call`, y el acceso a las demás reglas (para `call`).
- `ExecContext` gana el campo `sandbox` (lo heredan `child` y `forCall`). Con él:
  - `getVar`/`setVar` leen y escriben en el `Sandbox` (no en `DataStore`; no avisa a `StateService`);
  - `objectVar` y las nuevas `objectGet`/`objectSet` (que usan `set` y `unset` en lugar de `plugin.objectData()`)
    van a las variables de objetos del `Sandbox`; un objeto de `given` (un mapa) admite `.data`;
  - `service`: `ttl` desde el `Sandbox`; `cooldown`, `uses` → 0; `in_state`, `pvar_of` → falso/nulo;
  - `placeholder` devuelve lo dado o el texto; `resolve("server")` nulo, `online` lo dado o 0; `world()` nulo;
  - `now()` (nuevo) da la hora para `expire` sin `plugin.data()`.
- `Execution` gana un constructor de prueba `Execution(Sandbox, Rule, depth, Trace)` sin plugin: no mira el hilo
  (todo corre en el que llama), los topes salen del `Sandbox`, un error va al `Sandbox` y a la traza (no al log ni a
  `Stats`), y una espera (`delay`, `wait_ticks`) se anota y no se programa, como en la simulación.
- `ActionNode` gana `preview` (plantilla del valor de texto de la acción, compilada al cargar; nula si el valor no es
  texto o no compila). En una prueba (`exec.testing()`):
  - `when` se evalúa; `chance` no se sortea (la acción corre y la traza lo anota);
  - las acciones de flujo, `call`, `set`, `unset` y `cancel` **corren** (las tres últimas tocan solo el `Sandbox`);
  - las demás se **registran**: `Effect(tipo, ruta, texto)` con `preview` armada (modo comando para `console` y
    `command`, texto sin formato para el resto) o, sin `preview`, su `source`. Con `to:` se anota el destino sin
    resolverlo. No se aplica nada.
  - `random` usa el `Random` del `Sandbox` (semilla 0, o la del caso); `for_each` no da vueltas (lo anota).
- `call` (acción) en una prueba usa `Sandbox.call`: busca la regla en las cargadas y la recorre igual (condiciones y
  rama), con el tope de `max-call-depth`.
- `cancel` en una prueba fija `Sandbox.cancelled` con el valor de su expresión.

### 1.2 Casos de prueba (`rule/RuleTest.java`, nuevo) y su carga
- `RuleTest(name, path, Map<String,Object> given, Expect expect, long seed)`;
  `Expect(Boolean fired, String branch, Map<String,Object> vars, List<String> messages, List<String> commands,
  List<String> actions, Boolean cancelled, List<Expression> asserts)`.
- `RuleLoader.parse` lee `tests:` **después** de compilar la regla y de `stopCollecting` (las expresiones de
  `assert` no cuentan para las dependencias de un estado). Un caso con errores se descarta con su error en
  `Problems` (`regla.tests[1].expect.mensajes`), pero la regla carga (H1 CA3). Validación:
  - claves del caso: `name`, `given`, `expect`, `seed`; de `expect`: las de H2 o una variable;
  - raíz de cada clave de `given` y de cada variable de `expect`: una raíz del disparador, `pvar`, `gvar`, `var`, un
    `args` de la regla `call`, `online`, o un placeholder `%…%`; otra cosa → error con «¿quisiste decir?»;
  - `branch`: `actions`, `else`, `deny`, `deny <ruta>` o `none` (`on_enter` para un state);
  - `assert`: expresiones que compilan.
- `Rule.tests` (lista inmutable). `RuleOptions`: opción `tests` (`Kind.TESTS`) y los registros `TEST_KEYS` y
  `EXPECT_KEYS` con su descripción (de ahí salen el esquema y la referencia).

### 1.3 El corredor (`rule/RuleTester.java`, nuevo)
`RuleTester(Function<String, Rule> rules, int maxLoops, int maxCallDepth)`; `run(Rule)` → `List<Result>` y
`runAll(Collection<Rule>)` → `Summary`. No necesita `OmniEvents` (las unitarias lo usan con `new Actions(null)`).
1. `given` → raíces: `block.type: X` arma mapas anidados (`block` → `{type: X}`); `pvar.x`/`gvar.x` van al
   `Sandbox`, `var.x` a las locales, `<raíz…>.data.x` a las variables del objeto de esa ruta, `%x%` a los
   placeholders, `online` a su valor. Los números de YAML se normalizan como en el motor (`Values.normalize`).
2. Recorre la regla: (state: su `when`; si no, `conditions`) con `Condition.test` y la traza; la primera que falla
   corre su `deny` o el `else`; si pasan todas, `actions` (`on_enter`). `worlds`, `permission`, `bypass_permission`,
   `cooldown` y `limit` se saltan y la traza lo dice una vez.
3. Compara con `expect`: `fired`, `branch`, cada variable (`Values.looseEquals`; `null` = no existe), `messages`,
   `commands` y `actions` (contiene, en orden, sin formato, sin mayúsculas), `cancelled`, `assert` (sobre el
   contexto final). Un error de una acción o de una condición siempre es una falla del caso («error en
   actions[0]: …»), aunque lo demás coincida, para no ocultarlo.
4. `Result(rule, case, ok, failures, trace)`. Un caso tarda microsegundos; un tope de 10 000 pasos por caso (los
   bucles ya tienen tope) evita que un caso congele el hilo.

### 1.4 Comando, carga y gradle
- `/omni test [regla]` (`omnievents.command.test`, `op`): por regla `✔ regla: 3/3` o `✘ regla: 1/3`; por caso que
  falla, `✘ «nombre»`, cada diferencia (`pvar.diamantes: esperaba 10, dio 9`) y la traza. Al final
  `N pruebas: P pasan, F fallan · R reglas sin pruebas`. Con una regla sin pruebas lo dice y muestra un ejemplo.
  Autocompletado: reglas con pruebas.
- Al cargar y en `reload` (`OmniEvents.reload`), si `tests.on-load` (por defecto `true`) y hay pruebas, corren
  todas y se escribe en el log una línea `Pruebas de reglas: 12/12 pasan` o, si fallan, un aviso por caso
  (`La prueba «x» de la regla 'y' falla: … (/omni test y)`). `Settings.testsOnLoad`.
- `EjemplosPruebasTest` (unitaria) carga `src/main/resources/rules/ejemplos/` y exige que todas las pruebas de los
  ejemplos pasen y que haya al menos 10 casos en 4 archivos de ejemplo (H4 CA2).

## 2. Contrato de configuración
```yaml
diamantes:
  trigger: block_break
  conditions: ["block.type == 'DIAMOND_ORE'"]
  actions:
    - set: "pvar.diamantes += 1"
    - message: "<aqua>Llevas {pvar.diamantes} diamantes"
    - console: "give {player.name} emerald 1"
  tests:                                  # nuevo: lista de casos
    - name: suma uno                      # opcional (sin él: «caso 1»)
      given:                              # contexto falso (sin servidor)
        player.name: Ana
        block.type: DIAMOND_ORE
        pvar.diamantes: 9
      expect:
        fired: true
        pvar.diamantes: 10
        messages: ["Llevas 10"]           # contiene, en orden, sin formato
        commands: ["give Ana emerald"]
    - name: otra piedra no cuenta
      given: { block.type: STONE }
      expect: { fired: false, branch: none, pvar.diamantes: null }
```
| Clave | Dónde | Tipo | Validación |
|---|---|---|---|
| `tests` | regla | lista de casos | cada caso es un mapa |
| `name` | caso | texto | — |
| `given` | caso | mapa ruta → valor | raíz conocida (del disparador, `pvar`, `gvar`, `var`, `args`, `online`, `%…%`) |
| `seed` | caso | entero | semilla de `random` (0 por defecto) |
| `expect` | caso | mapa | obligatorio; claves de abajo o variables |
| `fired` | `expect` | booleano | — |
| `branch` | `expect` | texto | `actions`, `else`, `deny`, `deny conditions[i]`, `none`, `on_enter` |
| `pvar.x`, `gvar.x`, `var.x`, `<raíz>.data.x` | `expect` | valor o `null` | raíz conocida |
| `messages`, `commands`, `actions` | `expect` | texto o lista de textos | — |
| `cancelled` | `expect` | booleano | — |
| `assert` | `expect` | expresión o lista | compila |

### config.yml (clave nueva, con valor por defecto si falta)
```yaml
tests:
  # Las pruebas de las reglas (tests:) corren al arrancar y en cada reload; las que fallan se avisan en la consola.
  on-load: true
```
`config-version` sigue en 1: un `config.yml` viejo carga con el valor por defecto (IV.5). Sin cambios en `data/`.

## 3. Hilos y rendimiento
- `/omni test` y la corrida al cargar van en el hilo principal (como `reload`): sin E/S ni red; cada caso son
  microsegundos y los bucles tienen tope. Ningún disparo real cambia: sin oyentes nuevos (III.1) y sin costo
  mientras nadie prueba.
- `ActionNode.preview` se compila al cargar (III.2). En un disparo real no se usa.

## 4. Persistencia
Ninguna. El `Sandbox` vive lo que dura el caso; ni `DataStore`, ni el PDC, ni `Stats`, ni el perfilador, ni los
estados se tocan.

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación | Una prueba con `given: {player.name: Admin}` se hace pasar por otro | El jugador es un mapa de valores: no tiene permisos ni identidad real; las acciones no se aplican a nadie |
| Manipulación | Una prueba cambia datos reales (variables, ítems, mundo) | Con `Sandbox`, `set`/`unset` escriben solo en él y el resto se registra sin correr; unitaria y escenario e2e comprueban que una `pvar` real no cambia tras `/omni test` |
| Repudio | — | `/omni test` no cambia nada que haya que rastrear |
| Divulgación | La salida muestra valores de las reglas | Solo con `omnievents.command.test` (`op`); son las reglas del propio dueño |
| Denegación de servicio | Un caso con un `while` infinito o un `call` recursivo congela la carga | Topes de bucles y `call` de `config.yml`, y 10 000 pasos por caso; un error cuenta como falla y sigue con el siguiente |
| Elevación de privilegios | Una prueba ejecuta comandos de consola | `console` y `command` solo se registran con su texto; nunca se despachan (prueba unitaria) |

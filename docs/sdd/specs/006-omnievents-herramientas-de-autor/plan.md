# Plan 006 — Diseño técnico

## 1. Arquitectura

### 1.1 Condiciones con `deny` y grupos `any` (H1, H2)
- `rule/Condition.java` (nuevo, `sealed`): `Single(path, Expression expr, List<ActionNode> deny)` y
  `AnyOf(path, int min, List<Condition> of, List<ActionNode> deny)`. Métodos:
  - `boolean test(ExecContext, Consumer<String> trace, String ind)`: evalúa y, si hay traza, agrega la línea con
    los valores (`Expression.explain`). `AnyOf` corta en cuanto llega a `min` o ya no puede llegar; la traza lista lo
    evaluado y `al menos 2 de 3 → 2`.
  - Con `all = true` (para `/omni check`) evalúa **todo** sin cortar.
  - `source()`: texto para `/omni info` (`any 2 de [a, b, c]`).
- `RuleLoader.parseConditions` reemplaza el bucle actual: un texto compila como hoy (`Single` sin `deny`); un mapa
  lee `expr` **o** `any`+`of`, y `deny` con el mismo `ActionParser` de la regla (las raíces de sus expresiones se
  validan como las demás). Claves desconocidas → aviso con `Suggest.hint`; `deny` dentro de `of` → aviso y se ignora.
- `Rule.conditions` pasa a `List<Condition>`. `RuleManager.run`: al fallar una condición, si tiene `deny` corre
  `deny` (traza `→ deny de conditions[i]`), si no, `else` como hoy. `Rule.actionCount()` suma los `deny`.
- El importador de ConditionalEvents no cambia (produce textos).

### 1.2 Revisión, simulación y ejecución manual (H3, H4)
- **`RuleManager.check(rule, ctx)`** → `List<String>`: mundo, permiso, bypass, `Condition.explainAll` de cada
  condición, usos (`currentUses` solo lee… salvo reiniciar una ventana vencida, que no se escribe en la revisión: se
  usa una variante de solo lectura) y enfriamiento; al final la rama que correría y su número de acciones. No llama a
  `registerUsage` ni toca `Stats`. Las raíces del disparador (`trigger.roots()`) que usan las condiciones y no están
  en el contexto se listan como aviso.
- **Simulación**: `Trace` gana el indicador `simulation`. `RuleManager.run` con una traza de simulación:
  - no cuenta `Stats`, no llama a `registerUsage` y no envía la traza a los que depuran;
  - crea la `Execution` con la misma traza; `Execution.simulating()` = `trace != null && trace.simulation()`.
  - `ActionNode` gana `control` (acciones de flujo y `call`) y `source` (texto corto del YAML, ≤ 120 caracteres).
    En simulación, `ActionNode.execute` evalúa `when`, anota `chance` sin sortear, resuelve `to:`/`where` y lista
    `• ruta tipo: texto`; solo las acciones `control` corren (`if`, `repeat`, `while`, `for_each`, `random`, `stop`,
    `no_cooldown`, `call`). Una espera (`delay`, `wait_ticks`) se anota y no se programa.
  - `call` llama a `RuleManager.call` con la misma traza: la regla llamada también se simula.
- **`/omni run <regla> <jugador> [--simular]`**: arma el contexto del jugador (`player`, `world`, `location`,
  `item`, `sender`) y llama a `RuleManager.fire`-sin-evento (`run`) o a la simulación. La salida de la simulación se
  manda al que escribió el comando (y queda en el búfer de exportación).
- `/omni check` y `/omni run` usan el mismo armado de contexto que `/omni eval` (`senderContext`), con el jugador
  pedido en lugar del que escribe.

### 1.3 Exportar la traza (H5)
- `Debugger` guarda las trazas enviadas (y las simulaciones) en un búfer circular (`ArrayDeque`, tope
  `debug.export-buffer`, por defecto 200) con su hora.
- `/omni debug export`: en el hilo principal copia el búfer, la última `Problems`, las versiones y el YAML
  (`section.saveToString` de una `YamlConfiguration` armada con la sección) de cada regla en las trazas; en un hilo
  asíncrono escribe `trazas/<aaaa-MM-dd_HH-mm-ss>.txt` (temporal + `ATOMIC_MOVE`; sufijo `-2`… si ya existe) y
  responde en el hilo principal con la ruta.

### 1.4 Perfilador y presupuesto (H6, H7)
- `debug/Profiler.java` (nuevo): `enabled`, `sampleEvery`, `startedNanos`, contador atómico. `sample()` → `true`
  1 de cada N disparos si está encendido (si está apagado, un `volatile boolean` y nada más). Acumula en
  `ConcurrentHashMap<regla, RuleProfile>` (`LongAdder` de nanos y conteo, total y por parte: `conditions[i]`,
  `actions[2].then[0]`…, la ruta del nodo sin el nombre de la regla).
- `RuleManager.run`: si `sample()`, mide cada condición y el total del disparo; la `Execution` hereda el indicador y
  mide cada `node.execute` (también las que se reanudan tras un `delay`).
- Estimación: `% del hilo principal = Σ(nanos medidos × N) / nanos desde que se encendió × 100`. Con 20 TPS es el %
  de cada tick de 50 ms. Las reglas de chat asíncrono cuentan aparte (no gastan el hilo principal); se marcan.
- **Presupuesto**: `Rule.budgetNanos` (0 = sin presupuesto) y `debug/Budget.java`: ignora los primeros 10 disparos,
  luego promedio móvil exponencial (α = 0,2). Al cerrar cada disparo (el tiempo de `Stats` que ya se mide siempre),
  si van ≥ 20 disparos, el promedio pasa el presupuesto y pasó `profiler.budget-warn-every` desde el último aviso de
  esa regla, avisa en la consola. `/omni cpu` muestra el promedio reciente de cada regla con presupuesto.

### 1.5 Comandos y permisos
`/omni check`, `/omni run`, `/omni cpu` con `omnievents.command.check|run|cpu` (`default: op`, hijos de
`omnievents.command.*`). `debug export` usa el permiso de `debug`. Autocompletado: reglas, jugadores, `--simular`,
`on|off|reset`, `export`.

## 2. Contrato de configuración
### Reglas
```yaml
tienda_espada:
  trigger: call
  budget: 0.5ms                    # nuevo (H7): ms, µs o us; avisa si el promedio reciente lo pasa
  conditions:
    - "player != null"             # forma corta: sigue igual (H1 CA3)
    - expr: "player.level >= 10"   # nuevo (H1): mapa con expr y deny
      deny:
        - message: "<red>Necesitas nivel 10 (tienes {player.level})."
    - any: 2                       # nuevo (H2): al menos 2 de las de of
      of:
        - "player.op"
        - "pvar.compras >= 3"
        - expr: "player.world.time > 12000"
      deny: [message: "<red>Te falta cumplir 2 de: ser OP, 3 compras o que sea de noche."]
  actions: [...]
  else: [...]                      # corre si falla una condición sin deny (como hoy)
```
| Clave | Dónde | Tipo | Validación |
|---|---|---|---|
| `expr` | elemento de `conditions` u `of` | texto (expresión) | compila; obligatorio si no hay `any` |
| `any` | elemento | entero | 1 ≤ `any` ≤ tamaño de `of`; exige `of` |
| `of` | elemento con `any` | lista de condiciones | no vacía |
| `deny` | elemento de `conditions` | lista de acciones | como `actions`; dentro de `of` se ignora con aviso |
| `budget` | regla | `0.5ms`, `200us`, `200µs` | número > 0 con unidad |

### config.yml (claves nuevas, con valor por defecto si faltan)
```yaml
profiler:
  sample-every: 10        # /omni cpu mide 1 de cada N disparos
  budget-warn-every: 5m   # cada cuánto se repite el aviso de budget de una misma regla
debug:
  export-buffer: 200      # trazas que guarda /omni debug export
```
`config-version` sigue en 1: no hay migración; un `config.yml` viejo carga con los valores por defecto (IV.5).

## 3. Hilos y rendimiento
- Todo en el hilo principal salvo lo que ya era asíncrono (chat): el `Profiler` y `Budget` usan atómicos. El búfer de
  trazas se protege con `synchronized`.
- Apagado, el perfilador cuesta una lectura `volatile` por disparo. Encendido, 1 de cada N disparos paga un
  `nanoTime` por condición y acción.
- `Budget` cuesta una multiplicación por disparo solo en reglas con `budget`.
- E/S de disco (exportación) solo en un hilo asíncrono (III.2).

## 4. Persistencia
Sin cambios en `data/`. Los archivos de `trazas/` no se borran solos (el dueño los borra); se escriben de forma
atómica (II.6).

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación | `/omni run` dispara una regla «como» otro jugador | Solo con `omnievents.command.run` (`op`); queda en el log de consola quién la corrió y para quién |
| Manipulación | Una simulación cambia datos | En simulación solo corren acciones de flujo y `call` (que también simula); `set`, ítems y comandos se listan. Prueba unitaria y escenario e2e comprueban que las variables no cambian |
| Repudio | No queda rastro de una ejecución manual | `/omni run` (real) escribe en la consola quién, qué regla y para quién |
| Divulgación | La traza exportada lleva nombres de jugadores, mensajes de chat y el YAML de reglas | Se guarda solo en la carpeta del plugin, sin red; exportar exige el permiso de `debug` (`op`). Se documenta para revisar antes de compartir |
| Denegación de servicio | Muchos `export` seguidos o un búfer enorme llenan el disco o la memoria | Búfer con tope; un export a la vez (si hay uno en curso, se avisa); escribir es asíncrono |
| Denegación de servicio | El perfilador encendido pesa en servidores grandes | Muestreo 1 de N; apagado por defecto y al reiniciar |
| Elevación de privilegios | Expresiones de `check` con efectos | El lenguaje es de solo lectura (II.1); `check` no ejecuta acciones |

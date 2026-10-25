# Plan 005 — Diseño técnico

## 1. Arquitectura
Todo el cambio está en `trigger/RuleCommands.java`, `command/OmniCommand.java` (`toggle`) y `OmniEvents.reload()`.

**Causa (medida en Paper 1.21.4 build 232).** `CommandMap.register` y `Command.unregister` modifican el árbol de
Brigadier del servidor. `syncCommands()` y `Player.updateCommands()` mandan el árbol a los jugadores armándolo en el
pool «Paper Async Command Builder», que recorre ese mismo árbol. El código anterior, en cada `toggle`:
desregistraba todo (`unregister` → `syncCommands` + `updateCommands` por jugador), volvía a registrar
(`register` → otra vez las dos). Son 4 envíos por jugador y 2 mutaciones del árbol; el segundo `toggle` muta el árbol
mientras el hilo de Paper todavía lo recorre → `ConcurrentModificationException`. Además `syncCommands()` de
CraftServer ya llama a `sendCommands` por cada jugador, así que el `updateCommands()` de después duplicaba el envío.

**Diseño.**
- `RuleCommands.Spec` (record): `ruleName`, `name`, `aliases`, `description`, `usage`, `permission`, `tab`. Se
  construye con `Spec.of(ruleName, sección de la regla)`, función pura (probada en unitarias). La igualdad de dos
  listas de `Spec` decide si el conjunto cambió.
- `RuleCommand` guarda su `Spec` y sobrescribe `testPermissionSilent(sender)`: falso si su regla ya no existe o está
  desactivada. Paper filtra el árbol que manda a cada jugador con ese método (`BukkitCommandNode`), y lo usa para
  decidir si el comando se puede ejecutar: un comando oculto es «desconocido» sin sacarlo del árbol.
- `register()` (arranque y `reload`): deseado = comandos de las reglas comando **habilitadas**. `refresh()`
  (`toggle`): deseado = los ya registrados cuya regla sigue existiendo con la misma `Spec` + los de reglas comando
  habilitadas. En ambos, `reconcile(deseado)`:
  - si la lista de `Spec` es igual a la registrada: no se toca el mapa; si cambió qué comandos ve el jugador (el
    conjunto de nombres visibles), se manda el árbol una vez por jugador (`updateCommands`); si no, nada;
  - si es distinta: se desregistra y se registra (una mutación por cambio real) y se manda el árbol **una vez**:
    `syncCommands()` si existe, `updateCommands()` por jugador solo si no existe.
- `unregister()` (apagado): igual que antes, con un solo envío.
- `reload()` deja de llamar a `unregister()` antes de cargar: `register()` concilia contra lo registrado. El
  `RuleCommand` busca su regla por nombre en cada ejecución, así que sigue sirviendo con las reglas nuevas.

## 2. Contrato de configuración
Sin cambios. `trigger: command` y `command: { name, aliases, permission, description, usage, tab }` como en la 001.
Posible diferencia visible (sin comprobar): un comando desactivado con `/omni disable` podría seguir en `/help` hasta el próximo
`reload` (Bukkit no tiene forma de ocultarlo de ahí sin desregistrarlo); no se puede usar ni autocompletar.

## 3. Hilos y rendimiento
Todo corre en el hilo principal (comandos de consola o del jugador). Un `toggle` de una regla común pasa de 4 envíos
del árbol por jugador y 2 mutaciones a 0 y 0; el de una regla comando ya registrada, a 1 y 0.

## 4. Persistencia
Sin cambios.

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación | — | Sin cambios de identidad |
| Manipulación | Un jugador usa un comando de una regla desactivada que sigue registrado | `testPermissionSilent` falla (Paper no lo ejecuta) y `execute` vuelve a comprobar `enabled` como antes |
| Repudio | — | `/omni enable|disable` siguen registrándose en el log de la consola |
| Divulgación | El comando oculto aparece en `/help` | Solo muestra nombre y descripción, que ya eran públicos; se documenta |
| Denegación de servicio | Muchos `enable`/`disable` (p. ej. una regla que se activa a sí misma en bucle) llenan el pool de Paper y el log | Sin cambio de conjunto no hay envío ni mutación: el costo de un `toggle` es el de re-registrar oyentes |
| Elevación de privilegios | Ocultar no debe saltarse el permiso declarado | `testPermissionSilent` devuelve `false` si la regla está desactivada y, si no, llama al de Bukkit (permiso declarado) |

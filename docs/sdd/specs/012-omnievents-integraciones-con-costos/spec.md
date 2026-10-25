# Spec 012 — OmniEvents: integraciones con costos

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 001, 006, 007, 010, 011 ·
**Origen:** propuesta P9 de la [investigación de plugins exitosos](../../../investigacion/plugins-exitosos.md)
(§4 y §6) y la spec de integraciones del roadmap (Vault, WorldGuard, Citizens), elegidas por el dueño (2026-10-24)

## Problema
Cobrar algo por una regla (una tienda, un teletransporte de pago, un NPC que vende) hoy se escribe con condiciones y
comandos de consola: `conditions: "%vault_eco_balance% >= 500"` y luego `console: "eco take {player.name} 500"`. Si el
jugador tiene el dinero pero no los ítems, o si un comando falla a mitad, ya se le cobró una parte: no hay «todo o
nada». ConditionalEvents tiene el mismo problema; Citizens sí tiene costos en sus comandos de NPC. Tampoco hay forma de
reaccionar a entrar o salir de una región de WorldGuard (ConditionalEvents necesita WorldGuardEvents, otro plugin) ni
al clic sobre un NPC de Citizens sin escribir un `custom` con la clase del evento.

## Historias
**H1 — Dinero con Vault.** Como *administrador*, quiero dar, quitar y leer el dinero del jugador con la economía que ya
tengo (EssentialsX, CMI…), sin comandos de consola.
- CA1: Si Vault y un plugin de economía están instalados, la acción `give_money: <expresión>` deposita y
  `take_money: <expresión>` retira (si no alcanza, es un error de la acción y no retira nada). `money()` devuelve el
  saldo del jugador y `money_format(n)` el texto con la moneda de la economía.
- CA2: Sin Vault (o sin economía), las reglas cargan igual con un aviso por regla que lo usa; al correr, la acción es
  un error claro («no hay economía: instala Vault y un plugin de economía») y `money()` vale nulo. La economía se busca
  al usarla, no solo al cargar: una economía que se registra después de OmniEvents funciona.

**H2 — Costos todo o nada.** Como *administrador*, quiero declarar lo que cuesta una regla y que se cobre entero o
nada, con un mensaje que diga qué falta.
- CA1: `cost:` es un mapa con `money`, `levels`, `exp`, `items` (`MATERIAL;cantidad` o una lista) y variables
  (`pvar.x`, `gvar.x`), cada uno con una expresión. Se revisa después de las condiciones, el límite y el
  enfriamiento; si todo alcanza, se cobra y corren las acciones.
- CA2: Si algo no alcanza, no se cobra nada, no cuenta como uso ni activa el enfriamiento y corre `on_cost_fail` (o
  nada). `var.cost_missing` es el texto de lo que falta («200 de dinero, 2 × DIAMOND») y `var.cost_missing_list` la
  lista de claves que faltan (`[money, items]`).
- CA3: Si un cobro falla después de que otros se hicieron (la economía rechaza el retiro, el inventario cambió), se
  devuelve lo ya cobrado en orden inverso y corre `on_cost_fail`; si una devolución falla, se registra en el log con
  el jugador y la cantidad.
- CA4: Una cantidad negativa, un material que no existe o una clave desconocida son errores al cargar con la ruta
  (`regla.cost.items`) y «¿quisiste decir?». `cost` en un disparador asíncrono (`player_chat`, `player_pre_login`) o
  sin jugador (`timer` global, `server_start`) es un error al cargar; las variables de `gvar` sí sirven sin jugador.
- CA5: `/omni check` muestra cada parte del costo con lo que tiene y lo que pide (`✘ money: 300 / 500`) y la rama
  `on_cost_fail`; `/omni run --simular` no cobra. En `tests:` (spec 010) el costo se cobra sobre el Sandbox: el saldo
  sale de `given: { money: 500 }`, los niveles de `player.level`, la experiencia de `player.total_exp`, los ítems de
  `inventory: { DIAMOND: 3 }` y las variables de `pvar.x` / `gvar.x`; la rama es `on_cost_fail` cuando falta algo.
  Nada se cobra de verdad.

**H3 — Regiones de WorldGuard.** Como *administrador*, quiero que una regla corra al entrar o salir de una región, y
preguntar en qué región está el jugador.
- CA1: Con WorldGuard instalado, `trigger: region_enter` y `trigger: region_leave` corren por cada región en la que el
  jugador entra o de la que sale (al moverse de bloque, al teletransportarse, al entrar al servidor y al cambiar de
  mundo); `region.id` es la región y `cancel` en `region_enter` impide la entrada (se cancela el movimiento).
- CA2: `in_region('id')` es verdadero si el jugador está en esa región; `regions()` es la lista de ids donde está.
- CA3: Sin WorldGuard, las reglas cargan con un aviso y nunca corren; `in_region` vale falso y `regions()` una lista
  vacía. Solo se escucha el movimiento si hay reglas de región activas (constitución III.1).

**H4 — NPC de Citizens.** Como *administrador*, quiero reaccionar al clic de un jugador sobre un NPC.
- CA1: `trigger: npc_click` (clic derecho) y `trigger: npc_left_click` (clic izquierdo, golpe) corren cuando el
  objetivo es un NPC de Citizens; `npc.id`, `npc.name` y `npc.entity` lo describen; se pueden cancelar.
- CA2: `is_npc(entidad)` dice si una entidad es un NPC (sirve en `entity_interact`, `player_attack`…).
- CA3: Sin Citizens, las reglas cargan con un aviso y nunca corren; `is_npc` vale falso.

**H5 — Documentado, validado y compatible.** Como *administrador*, quiero autocompletado y referencia para lo nuevo y
saber con qué versiones se probó.
- CA1: `cost`, `on_cost_fail`, las acciones, los disparadores y las funciones nuevas están en su registro (esquema
  JSON y referencia regenerados).
- CA2: Los ejemplos traen una tienda con `cost` y `on_cost_fail`, una regla de región y una de NPC, con `tests:`.
- CA3: `plugin.yml` declara `softdepend: [PlaceholderAPI, Vault, WorldGuard, Citizens]`; el README del plugin lista
  las dependencias opcionales con las versiones probadas.

## Fuera de alcance
- Acciones que crean o cambian regiones o NPC (`npc_say`, `region_flag`…); banderas propias de WorldGuard.
- Cuentas bancarias de Vault, permisos o chat de Vault; economías sin Vault.
- `cost` en `deny`, `else` o en acciones sueltas (`take_money` cubre el cobro dentro de una lista de acciones).
- Regiones globales (`__global__`) en `region_enter`/`region_leave`.

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Número de spec? | 012: la 003 está reservada (paridad con ConditionalEvents, otra sesión) |
| 2 | ¿Alcance? (estimado: Vault + `cost` ≈ 1 sesión; WorldGuard y Citizens ≈ 1 sesión cada uno) | Las cuatro: Vault, `cost`, WorldGuard y Citizens |
| 3 | ¿Cómo se prueba Vault? | Proveedor falso en las unitarias; en el servidor de prueba, Vault 1.7.3 y EssentialsX (2.21.0 en 1.21.4, 2.22.0 en 26.1.2) |
| 4 | ¿Se bajan los jars de prueba? | Sí, todos, solo al servidor de prueba (fuera del repo, con sha256): Vault, EssentialsX, WorldEdit 7.4.6, WorldGuard (7.0.13 en 1.21.4, 7.0.18 en 26.1.2) y Citizens 2.0.44-b4268. Para compilar: VaultAPI (jitpack.io) y WorldGuard (maven.enginehub.org), `compileOnly` |
| 5 | ¿Recorte de WorldGuard y Citizens? | Mínimo útil: disparadores de región e `in_region`/`regions`; disparador de NPC e `is_npc`; sin acciones sobre regiones o NPC |
| 6 | ¿Versión y capturas? | 1.7.0, sin capturas |

Decisión del plan (no cambia lo aprobado): el clic de un NPC son dos disparadores, `npc_click` (derecho) y
`npc_left_click` (izquierdo), porque cada disparador escucha un solo evento de Bukkit.

## Preguntas abiertas
- (ninguna)

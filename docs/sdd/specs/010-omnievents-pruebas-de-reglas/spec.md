# Spec 010 — OmniEvents: pruebas de reglas en el YAML

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 001, 006, 007, 009 ·
**Origen:** propuesta P7 de la [investigación de plugins exitosos](../../../investigacion/plugins-exitosos.md)
(§4 y §6), elegida por el dueño (2026-10-24)

## Problema
Una regla de OmniEvents se prueba hoy jugando: se rompe el bloque, se mira el chat y se revisa la variable. Las
herramientas de la 006 (`/omni check`, `/omni run --simular`) ayudan, pero dependen del estado del momento (un
jugador conectado, sus variables reales) y no dejan nada escrito. Al cambiar una regla, actualizar el plugin o
Paper, nadie vuelve a probar lo que ya andaba: «se rompió al actualizar y no me enteré» es la queja número uno del
ecosistema, y ningún rival de ConditionalEvents trae pruebas declaradas junto a la regla.

## Historias
**H1 — Declarar pruebas junto a la regla.** Como *administrador*, quiero escribir en la regla casos de prueba con
lo que entra y lo que espero, para que quede escrito qué hace.
- CA1: La regla acepta `tests:`, una lista de casos. Cada caso tiene `name` (opcional), `given` (el contexto) y
  `expect` (lo esperado).
- CA2: `given` arma un contexto falso, sin servidor: claves con puntos para las raíces del disparador y sus
  propiedades (`block.type: DIAMOND_ORE`, `player.name: Ana`, `player.level: 12`, `event.damage: 5`,
  `message: "hola"`), y para variables iniciales (`pvar.x`, `gvar.x`, `var.x`, `<raíz>.data.x`). Una raíz que no se
  da vale nulo, como en `/omni check`.
- CA3: Errores de forma se informan al cargar con archivo y ruta (`tests[1].expect.mensajes` → «¿quisiste decir
  messages?»): clave desconocida, `given` o `expect` que no son mapas, valor de tipo equivocado. Una prueba mal
  escrita no impide que la regla cargue.

**H2 — Qué se puede esperar.** Como *administrador*, quiero comprobar el resultado de la regla, no solo si corrió.
- CA1: `fired: true|false`: si corrieron las acciones principales (`actions`).
- CA2: `branch:` la rama que corrió: `actions`, `else`, `deny` (o `deny conditions[1]`) o `none`.
- CA3: Variables al terminar: `pvar.x: 10`, `gvar.x`, `var.x`, `<raíz>.data.x`; `null` exige que no exista.
- CA4: `messages:` textos que deben aparecer (en orden, como subtexto del texto sin formato) entre los mensajes que
  la regla mandaría (message, broadcast, actionbar, title…); `commands:` lo mismo con los comandos (`console`,
  `command`); `actions:` lo mismo con cualquier acción registrada (`give_item`, o `sound: LEVELUP` para exigir
  también su texto); `cancelled: true|false` si cancela el evento.
- CA5: `assert:` una expresión o una lista que debe dar verdadero sobre el estado final (`"pvar.d >= 10"`).

**H3 — Correr las pruebas sin efectos.** Como *administrador*, quiero `/omni test [regla]` para correr las pruebas
en el servidor y ver cuáles fallan y por qué.
- CA1: Recorre la regla como un disparo (condiciones con sus `deny` y `else`, acciones) sobre el contexto falso.
  Corren las acciones de flujo (`if`, bucles, `random` con semilla fija, `stop`, `call` a otra regla, que también se
  prueba); `set` y `unset` cambian solo las variables del caso; el resto (mensajes, comandos, ítems, mundo) se
  **registra** con su texto ya armado y no se aplica. `delay` no espera. Ningún dato real cambia.
- CA2: Por caso: `✔`/`✘`, y en el fallo qué se esperaba, qué salió y la traza de la regla (como `/omni debug`).
  Al final: casos que pasaron, fallaron y reglas sin pruebas.
- CA3: Corre desde la consola y sin jugadores conectados. Permiso `omnievents.command.test` (`op` por defecto).
- CA4: `/omni test` sin regla corre todas; con una regla desconocida, «¿quisiste decir…?».

**H4 — Que las pruebas avisen solas.** Como *dueño del servidor*, quiero enterarme si una prueba deja de pasar al
recargar o actualizar.
- CA1: Al arrancar y en cada `reload`, las pruebas corren; si alguna falla, un resumen va a la consola (cuántas y
  cuáles, con «mira /omni test <regla>»). Si todas pasan, una línea. `tests.on-load: false` en `config.yml` lo apaga.
- CA2: `./gradlew build` corre las pruebas de los ejemplos que trae el plugin (la fábrica las usa como CI).
- CA3: Una regla `trigger: state` se prueba con su `when`: si se cumple, corre `on_enter` (`fired: true`).

## Fuera de alcance
- El mundo de verdad: un bloque, un ítem o un jugador real en el contexto; `to:`, `for_each` sobre jugadores
  conectados y `where` se registran sin resolver destinatarios (`for_each` no da vueltas).
- `worlds`, `permission`, `bypass_permission`, `cooldown` y `limit` de la regla: la prueba los salta (lo dice la
  traza). `cooldown()`, `uses()` e `in_state()` dan 0 o falso; `online()`, `player()` y `perm()` leen el servidor de
  verdad (solo leen; en gradle no hay servidor). Un método de un objeto falso (`player.hasPermission(…)`) es un error.
- Placeholders de otros plugins (PlaceholderAPI): valen lo que diga `given` (`"%vault_eco_balance%": 100`) o su texto.
- `on_exit` de una regla state y las ramas `on_cooldown` y `on_limit`.

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Número de spec? | 010: la 003 está reservada (paridad con ConditionalEvents, otra sesión) |
| 2 | ¿Alcance? | Núcleo: `given` con propiedades y variables; `expect` con `fired`, `branch`, variables, `messages`, `commands`, `cancelled` y `assert`. Sin permisos, mundos, enfriamientos ni límites falsos (≈ 1 sesión) |
| 3 | ¿Cuándo corren? | Con `/omni test [regla]` y al cargar o recargar (resumen en consola solo si alguna falla; se apaga en `config.yml`); `gradlew` prueba los ejemplos |
| 4 | ¿Cómo se comparan los mensajes? | Contiene, en orden, sin formato |
| 5 | ¿Versión y capturas? | 1.5.0, sin capturas |

## Preguntas abiertas
- (ninguna)

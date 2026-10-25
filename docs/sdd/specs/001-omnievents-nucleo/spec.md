# Spec 001 — OmniEvents: núcleo de eventos condicionales

**Estado:** Implementado y verificado (compuertas 1, 2 y 3a) · **Plugin:** OmniEvents · **Depende de:** — ·
**Origen:** pedido del dueño (2026-10-24): «investigar ConditionalEvents y hacer una versión propia, mucho mejor»

## Problema
Los administradores de servidores quieren reaccionar a lo que pasa en el juego («cuando un jugador rompe un diamante
en el mundo minero, si no es VIP y lleva más de 10 en el día, avísale y no le des la recompensa») **sin programar**.
ConditionalEvents (Ajneb97) resuelve parte, pero (ver [roadmap](../../roadmap.md#diagnóstico-de-conditionalevents-lo-que-hacemos-mejor)):
- las condiciones no admiten paréntesis, `not` ni mezclar `and`/`or`; la aritmética es una opción aparte;
- las acciones son listas planas: un `if` se simula con grupos `execute` y no hay bucles ni variables propias;
- `one_time` y `cooldown` son rígidos (sin ventanas de reinicio, el OP los salta siempre);
- las variables de cada evento son una lista fija; los eventos de otros plugins requieren cadenas de métodos;
- cuando algo no funciona, el administrador no sabe **por qué**;
- escucha todos los eventos aunque no se usen.

## Historias

**H1 — Reglas declaradas en archivos.** Como *administrador*, quiero escribir reglas en archivos YAML dentro de
`plugins/OmniEvents/rules/` (con subcarpetas) para organizarlas por tema.
- CA1: Cada clave de primer nivel de un archivo es una regla con nombre único en todo el plugin; un nombre repetido
  es un error que nombra los dos archivos.
- CA2: Una regla tiene `trigger` (requerido), `conditions`, `actions`, `else` y opciones; `enabled: false` la apaga.
- CA3: `/omni reload` recarga todo sin reiniciar; las reglas válidas siguen funcionando aunque otro archivo tenga
  errores, y los datos persistentes no se pierden.
- CA4: Al primer arranque se crean `config.yml`, `messages.yml` y ejemplos en `rules/ejemplos/` que funcionan tal cual.

**H2 — Lenguaje de condiciones completo.** Como *administrador*, quiero escribir condiciones como expresiones.
- CA1: Operadores `&&`/`and`, `||`/`or`, `!`/`not`, comparaciones `== != < <= > >=`, `~=` (igual sin distinguir
  mayúsculas), `contains`, `startsWith`, `endsWith`, `matches` (regex), `in`, con sus negaciones (`!contains`, `not in`…).
- CA2: Aritmética `+ - * / %` con precedencia estándar, paréntesis, ternario `c ? a : b`, listas `[1, 2]`, índices `x[0]`.
- CA3: Precedencia: `a || b && c` es `a || (b && c)`; `!a == b` es `(!a) == b`; la aritmética antes que las comparaciones.
- CA4: Funciones incorporadas: matemáticas, texto, aleatorio, tiempo, jugadores en línea, permisos, PlaceholderAPI.
- CA5: Un error de sintaxis se informa al cargar con archivo, regla, ruta y columna; la regla no se carga.
- CA6: `conditions` puede ser una expresión o una lista (todas deben cumplirse).
- CA7: Comparar con nulo no revienta: `null < 5` es falso, `null == null` es verdadero, una propiedad de nulo es nulo.

**H3 — Acceso a cualquier dato del evento.** Como *administrador*, quiero leer propiedades del jugador, del bloque,
del ítem, de la víctima o del propio evento con la misma sintaxis.
- CA1: `player.health`, `player.location.block.type`, `item.name`, `target.type`, `event.damage`, `event.cause`.
- CA2: Las propiedades se resuelven con alias legibles (`player.level`, `item.lore`) y, si no hay alias, con los
  métodos de lectura de la API (`event.final_damage` → `getFinalDamage()`), sin poder invocar métodos que modifiquen.
- CA3: Los valores de enumeraciones (`GameMode.SURVIVAL`, `Material.STONE`) se comparan con texto sin distinguir
  mayúsculas: `player.gamemode == 'survival'`.
- CA4: Al cargar se advierte una variable raíz que el disparador no ofrece, con sugerencia.

**H4 — Acciones con control de flujo.** Como *administrador*, quiero acciones con lógica.
- CA1: `if`/`then`/`else`, `repeat` (n veces), `while` (con tope de iteraciones), `for_each` (sobre jugadores o una
  lista), `random` (ramas ponderadas), `delay`, `stop` y `call` (ejecutar otra regla con argumentos).
- CA2: Un `delay` dentro de un `if` o de un `repeat` pausa solo esa ejecución, sin bloquear el servidor.
- CA3: Cualquier acción acepta modificadores: `to` (destinatarios: `self`, `target`, `all`, `world:<w>`,
  `radius:<n>`, `player:<nombre>`), `where` (filtro por destinatario), `chance` (porcentaje) y `when` (guarda).
- CA4: Se aceptan las dos formas de escribir una acción: `- message: "hola"` y `- "message: hola"` (la de ConditionalEvents).
- CA5: Los textos interpolan expresiones con `{expr}` y placeholders `%papi%`, con MiniMessage y códigos `&`.
- CA6: Texto que viene del jugador e interpolado con `{}` no se interpreta como MiniMessage; en comandos se eliminan
  saltos de línea y `§`.

**H5 — Catálogo amplio de acciones.**
- CA1: Mensajes: `message`, `broadcast`, `actionbar`, `title`, `bossbar`, `sound`, `console_message`, `log`.
- CA2: Comandos: `console`, `command`, `chat`, `send_to_server`.
- CA3: Jugador: `teleport`, `heal`, `damage`, `feed`, `gamemode`, `effect`, `remove_effect`, `fire`, `velocity`,
  `launch`, `give_item`, `remove_item`, `clear_inventory`, `close_inventory`, `give_exp`, `give_level`, `kick`, `fly`.
- CA4: Mundo: `set_block`, `lightning`, `particle`, `summon`, `drop_item`, `explosion` (sin dañar bloques por defecto).
- CA5: Evento: `cancel`, `set_event` (propiedad = expresión), `set_damage`, `set_message`, `set_join_message`,
  `set_quit_message`, `set_death_message`, `keep_inventory`, `cancel_drops`, `deny_join`.
- CA6: Una acción desconocida es un error al cargar con «¿quisiste decir…?».

**H6 — Disparadores.** Como *administrador*, quiero reaccionar a muchos eventos.
- CA1: Al menos 45 disparadores de jugador, bloques, ítems, inventario, combate, entidades y servidor (lista en el plan).
- CA2: `timer` (cada intervalo, por jugador o del servidor), `clock` (a horas del día, con zona horaria),
  `server_start`, `server_stop`, `call` (solo por `call` o `/omni call`).
- CA3: `command`: registra un comando real con alias, permiso, descripción y autocompletado por argumento; lo puede
  usar la consola.
- CA4: `custom`: cualquier evento de Bukkit o de otro plugin por nombre de clase, con el jugador por propiedad.
- CA5: Solo se registra un oyente para un evento si alguna regla habilitada lo usa; `reload` no duplica oyentes.
- CA6: Filtros rápidos por regla: `worlds`, `permission`, `bypass_permission`, `priority`, `ignore_cancelled`.

**H7 — Variables persistentes.** Como *administrador*, quiero guardar datos sin otro plugin.
- CA1: `var.x` (de la ejecución), `pvar.x` (del jugador) y `gvar.x` (del servidor) se leen en expresiones y se
  asignan con `set: "pvar.monedas += 5"` (`=`, `+=`, `-=`, `*=`, `/=`) o se borran con `unset`.
- CA2: `pvar` y `gvar` sobreviven a reinicios; el guardado es asíncrono, periódico y atómico.
- CA3: Con PlaceholderAPI: `%omni_var_<n>%`, `%omni_gvar_<n>%`, `%omni_cooldown_<regla>%`, `%omni_uses_<regla>%`.
- CA4: `/omni var get|set|del|list` para jugadores (en línea o no) y globales.

**H8 — Enfriamientos y límites.**
- CA1: `cooldown: 30s` por jugador o global (`cooldown_scope`), con `on_cooldown` (acciones; `var.cooldown_left`).
- CA2: `limit: N` usos por jugador o global con `limit_reset: never|daily|weekly|monthly|<duración>` y `on_limit`.
  `limit: 1` equivale a `one_time` de ConditionalEvents.
- CA3: Persisten tras reinicios. El OP no los salta salvo `cooldown-bypass-op: true` o `bypass_permission`.
- CA4: La acción `no_cooldown` evita que la ejecución actual active el enfriamiento y cuente el uso.

**H9 — Diagnóstico.**
- CA1: `/omni debug <regla|all|off>` muestra en vivo, a quien lo pide, cada disparo: filtros, cada condición con los
  valores comparados y su resultado, la rama y las acciones ejecutadas.
- CA2: `/omni eval <expresión>` evalúa con el contexto de quien la escribe.
- CA3: `/omni stats` muestra por regla disparos, aciertos, tiempo promedio y máximo, y los oyentes y tareas activos.
- CA4: `/omni validate` informa todos los errores y advertencias sin recargar.
- CA5: Errores en tiempo de ejecución (p. ej. división por cero) no revientan el evento: se registran con regla y
  acción, y se avisan a los que depuran.

**H10 — Importar de ConditionalEvents.**
- CA1: `/omni import conditionalevents` lee `plugins/ConditionalEvents/` y escribe reglas equivalentes en
  `rules/importado/`, sin tocar el original.
- CA2: Las condiciones con `execute <grupo>` se convierten en `if`/`else` en orden; los operadores y variables
  conocidos se traducen; lo no traducible queda comentado como `# REVISAR`.

**H11 — Comandos y permisos.**
- CA1: `/omni` (alias `/oe`) con `reload, validate, list, info, enable, disable, debug, eval, call, var, cooldown,
  limit, stats, import, triggers, item, selftest`, autocompletado y ayuda.
- CA2: Cada subcomando exige `omnievents.command.<sub>` (por defecto solo OP); sin permiso no aparece al autocompletar.

**H12 — API para otros plugins.**
- CA1: Otro plugin puede registrar acciones, funciones del lenguaje y disparadores, y ejecutar una regla `call`.

## Fuera de alcance (siguientes specs)
- Integraciones nativas con Vault, WorldGuard y Citizens (se usan hoy vía PlaceholderAPI y `console`).
- Editor en el juego; almacenamiento SQL para redes; Folia.

## Decisiones (CLARIFY)
Propuestas por la IA con valores por defecto razonables; **pendientes de que el dueño las acepte**.
| # | Pregunta | Propuesta |
|---|---|---|
| 1 | Nombre del plugin | **OmniEvents**, comando `/omni` (alias `/oe`, `/omnievents`) |
| 2 | Plataforma | Paper 1.21.4+, Java 21 (ADR-0002). Sin Spigot ni versiones antiguas |
| 3 | Compatibilidad con archivos de ConditionalEvents | Por importador (H10), no leyéndolos en vivo: el formato propio es más expresivo |
| 4 | ¿El OP salta enfriamientos? | No por defecto (en ConditionalEvents sí); configurable |
| 5 | Idioma de mensajes | Español por defecto, traducible en `messages.yml` |
| 6 | Métodos que modifican en expresiones | Prohibidos por defecto (constitución II.1); `expressions.allow-unsafe-methods` |

## Preguntas abiertas
- Ninguna bloqueante.

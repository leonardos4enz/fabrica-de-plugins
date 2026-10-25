# Spec 008 — OmniEvents: disparador de estado

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 001, 006, 007 ·
**Origen:** propuesta P6 de la [investigación de plugins exitosos](../../../investigacion/plugins-exitosos.md)
(§4 y §6), elegida por el dueño (2026-10-24)

## Problema
Muchas reglas no quieren reaccionar a *un evento* sino a *un estado*: «cuando la vida baje de 3 corazones», «mientras
esté en el agua», «cuando su racha llegue a 10», «al salir de la zona». Hoy hay dos caminos y los dos son malos:
- **Sondear** con un `timer` cada X ticks y llevar a mano una variable para no repetir el aviso. Es lo que hacen
  ConditionalEvents (`repetitive`) y MythicMobs (`onTimer`), y es la mayor causa de lag de esos plugins.
- **Adivinar los eventos**: escribir una regla por cada evento que puede cambiar el estado (daño, curación,
  reaparición…), cada una con la misma condición y una variable de «ya avisé». Se olvida alguno y el aviso falla.

Ningún plugin rival tiene disparadores por flanco («cuando X **pasa a ser** verdad / deja de serlo»).

## Historias
**H1 — Disparador por flanco.** Como *administrador*, quiero declarar un estado con `trigger: state` y `when:`, y
acciones `on_enter` (pasa a ser verdad) y `on_exit` (deja de serlo), sin escribir variables de control.
- CA1: `when` es una expresión del lenguaje (o una lista de condiciones, como `conditions`, que deben cumplirse
  todas). El estado es **por jugador conectado**; con `per_player: false` es uno solo para el servidor (H6).
- CA2: `on_enter` corre una vez cuando el estado pasa de falso a verdadero para ese jugador; `on_exit` una vez
  cuando pasa de verdadero a falso. Mientras no cambie, no corre nada (no se repite).
- CA3: `worlds` forma parte del estado (fuera de esos mundos el estado es falso); `permission` y
  `bypass_permission`, `cooldown`, `limit` y sus acciones `on_cooldown`/`on_limit` se aplican como en cualquier
  regla al **entrar**. `on_exit` corre solo si corrió el `on_enter` de esa entrada (CLARIFY 2): nunca se ve
  «A salvo» sin haber visto «¡Peligro!».
- CA4: `on_exit` recibe `var.state_seconds`: cuánto duró el estado.
- CA5: Errores de forma al cargar, con archivo y ruta: falta `when`, faltan `on_enter` y `on_exit` a la vez;
  `conditions`, `actions` o `else` en una regla `state` son error y dicen qué opción va (`when`, `on_enter`).

**H2 — Sin sondeo: el motor deduce de qué depende.** Como *dueño del servidor*, quiero que el estado se revise solo
cuando puede haber cambiado, sin un temporizador por jugador.
- CA1: Al cargar, el motor recorre la expresión y deduce de qué eventos depende: vida → daño, curación,
  reaparición; hambre → cambio de hambre; ubicación, bloque, bioma, mundo, `in_area` → moverse de bloque,
  teletransporte, cambio de mundo, reaparición; nivel y experiencia; modo de juego; agacharse, correr, volar;
  ítem en mano y armadura; `pvar`/`gvar` → cuando una acción o `/omni var` cambian **esa** variable.
- CA2: Solo se registran los oyentes que piden las reglas `state` que hay (constitución III.1). Varios eventos del
  mismo jugador en el mismo tick se juntan en **una** revisión, al final del tick (después de que el cambio se
  aplicó: el daño ya restó vida).
- CA3: Si la expresión usa algo cuya dependencia no se puede deducir (un placeholder, `perm()`, la hora,
  `world.time`, una propiedad sin tabla), la regla **no carga** sin `check_every: <duración>` (respaldo por intervalo), y el
  error dice qué partes no se pudieron deducir (CLARIFY 3). `watch: [disparadores]` agrega eventos a mano (los nombres de `/omni triggers`).
- CA4: `/omni info <regla>` dice de qué depende (eventos deducidos, variables, `check_every`) y qué partes no se
  pudieron deducir. Una expresión con azar (`random`, `chance`) es error: un estado no puede ser aleatorio.

**H3 — Ciclo de vida del estado.** Como *administrador*, quiero que el estado se comporte bien al entrar, salir,
morir y recargar.
- CA1: Al entrar un jugador se evalúa su estado; si ya es verdadero corre `on_enter` (CLARIFY 1). Con
  `initial: silent` toma el estado sin correr nada (y entonces tampoco correrá su `on_exit`). Al salir, su estado se
  olvida (memoria) y no corre `on_exit`.
- CA2: `/omni reload` conserva el estado de las reglas que siguen existiendo con el mismo `when` (no se repite
  `on_enter`); una regla nueva o con `when` cambiado toma el estado actual sin disparar.
- CA3: El estado vive solo en memoria: no cambia los datos persistentes.
- CA4: `/omni enable|disable <regla>` apaga y prende la regla; al apagarla se olvida el estado.

**H4 — Diagnóstico.** Como *administrador*, quiero ver el estado y probarlo con las herramientas de la 006.
- CA1: `/omni debug <regla>` muestra cada revisión que cambia el estado (`✔ entra`/`✘ sale`, con los valores) y
  la rama que corre.
- CA2: `/omni check <regla> [jugador]` dice el valor actual de `when`, el estado guardado y qué correría
  (`on_enter`, `on_exit` o nada). `/omni run <regla> <jugador> [--simular]` corre o simula `on_enter`.
- CA3: `/omni stats` y `/omni cpu` cuentan las revisiones y los cambios por regla.

**H5 — Consultar el estado desde otras reglas.** Como *administrador*, quiero usar el estado en otras reglas.
- CA1: La función `in_state(regla)` devuelve si el jugador del contexto está en ese estado (para un estado del
  servidor, si está activo). Una regla desconocida o que no es `state` es error al cargar si el nombre es un texto
  fijo; si no, vale falso.
- CA2: `%omni_state_<regla>%` (PlaceholderAPI) devuelve `true` o `false`.
- CA3: Un estado puede depender de otro con `in_state`: se revisa cuando el otro cambia. Depender de sí mismo es error.
- CA4: `/omni state <regla>` lista los jugadores en el estado y desde cuándo (o si el estado del servidor está activo);
  permiso propio `omnievents.command.state` (`op` por defecto), con autocompletado de las reglas `state`.

**H6 — Estados del servidor.** Como *administrador*, quiero estados que no son de un jugador («hay evento»,
«hay más de 10 conectados», «está lloviendo»).
- CA1: Con `per_player: false` el estado es uno para el servidor; `on_enter` y `on_exit` corren una vez, sin
  jugador (las acciones usan `to:` como en un `timer` del servidor).
- CA2: Se deducen `gvar`, el número de conectados (`online`, `players()`, `online(nombre)`), el clima del mundo
  principal (`world.storm`, `world.thunder`) e `in_state` de otro estado del servidor. Usar `player`, `pvar` o
  algo del jugador es error al cargar.
- CA3: Toma su valor al cargar el plugin o la regla, sin disparar.

**H7 — Documentación generada.** El disparador y sus opciones (`when`, `on_enter`, `on_exit`, `check_every`,
`watch`, `initial`, `per_player`) y la función `in_state` se registran con descripción (spec 007) y aparecen en el esquema y en la referencia; un ejemplo nuevo en
`rules/ejemplos/estados.yml`.

## Fuera de alcance
- Estados de entidades que no son jugadores y de bloques.
- Guardar el estado entre reinicios.
- Deducir dependencias de placeholders de otros plugins (van con `check_every`).

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Número de spec? | 008: la 003 está reservada (paridad con ConditionalEvents, otra sesión) |
| 2 | Si un jugador entra con el estado ya verdadero, ¿corre `on_enter`? | Sí; `initial: silent` para no avisar |
| 3 | Si `on_enter` no corre (enfriamiento, límite, permiso), ¿corre `on_exit` al salir? | No: van en pareja, `on_exit` solo si corrió su `on_enter` |
| 4 | Si no se puede deducir de qué depende la expresión | Error al cargar que pide `check_every` (el costo del sondeo queda a la vista) |
| 5 | ¿Extras? | Los tres: `in_state` (y su placeholder), estados del servidor (`per_player: false`) y `/omni state` |
| 6 | ¿Versión del plugin? | 1.3.0 (capacidad nueva, la configuración anterior sigue cargando) |
| 7 | ¿Capturas? | No (lección de la 004) |

## Preguntas abiertas
- (ninguna)

# Spec 037 — Worldcraft: API para otros plugins

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a) (ver [verify](verify.md)) · CLARIFY respondido por el dueño (2026-11-02: solo la API Java y el evento; **nada de CoreProtect**
y **ningún puente con WorldEdit**: «no quiero que la gente tenga WorldEdit también») · **Plugin:** Worldcraft (`/wc`,
0.12.0 → 0.13.0) · **Depende de:** [025](../025-worldcraft-nucleo/spec.md) (motor, límites, historial, deshacer),
[027](../027-worldcraft-esquemas-schem/spec.md) (esquemas), [030](../030-worldcraft-selecciones-no-cuboides/spec.md)
(selecciones) · **Origen:** la partición aceptada por el dueño en la 029 (fila «final», costo M).
**Investigación:** [paridad con WorldEdit y FAWE](../../../investigacion/paridad-worldedit-fawe.md) §3 y §4.2 (fila «final»).

## Problema
- Un minijuego que reinicia su arena, un plugin de islas que pega una isla nueva o un plugin de protección que no quiere
  que se edite su zona hoy no pueden hablar con Worldcraft: o tocan bloques uno por uno en el hilo principal (lo que la
  025 vino a evitar) o dependen de WorldEdit.
- Un plugin de protección no se entera de las obras de Worldcraft: `/wc set` pasa por encima de cualquier zona.
- Lo que otro plugin pida tiene que pasar por lo mismo que un comando: límites por grupo, 8 ms por tick, historial y
  deshacer. Una API no puede ser un atajo alrededor del motor.

## Costo estimado (lección 004)
| Pieza | Costo | Decisión del dueño |
|---|---|---|
| API Java: leer y poner la selección de un jugador; encolar `set`, `replace` y pegar un `.schem` por el motor, a nombre de un jugador (sus límites y su historial) o del plugin; deshacer; resultado con un `CompletableFuture` | ~30 % | **Entra** |
| Eventos `WorldcraftEditEvent` (cancelable, antes de toda obra, también las de comandos y `undo`) y `WorldcraftEditDoneEvent` (al terminar) | ~10 % | **Entra** |
| Plugin de ejemplo en el repositorio (usa la API y protege una zona con el evento) y pruebas en el juego | ~15 % | (lo propone la IA: es la prueba y la documentación para desarrolladores) |
| Registro en CoreProtect | ~25 % | **Fuera** («nada de CoreProtect») |
| Puente de selección con WorldEdit para `/rg define` | ~15 % | **Fuera** (el dueño no quiere que haga falta WorldEdit) |
| Respetar regiones de WorldGuard u otros plugins de protección | ~25 % | Fuera (el evento lo deja hecho a los plugins de protección) |

## Historias

**H1 — Encontrar la API.** Un plugin con `depend: [Worldcraft]` (o `softdepend`) la pide.
- CA1: `WorldcraftApi.get()` la devuelve (también por el `ServicesManager` de Bukkit); sin Worldcraft habilitado da
  un error claro, no un `NullPointerException`. `apiVersion()` dice la versión del contrato (1).
- CA2: Solo el paquete `dev.leo.worldcraft.api` (y `api.event`) es el contrato: lo demás puede cambiar sin aviso.
- CA3: Con `api.enabled: false` en `config.yml`, toda llamada que edita falla con «the server owner turned the API
  off» (los eventos siguen: protegen igual).

**H2 — La selección de un jugador.**
- CA1: `selection(player)` da la forma (`cuboid`, `poly`, `ellipsoid`, …), el mundo, las esquinas mínima y máxima, el
  volumen y si un bloque está adentro; vacío si no tiene una selección completa.
- CA2: `select(player, world, a, b)` le marca una caja (con su contorno, como `/wc pos1`/`pos2`);
  `clearSelection(player)` la borra.

**H3 — Editar desde otro plugin.** `api.edit(plugin)` arma una obra: dónde (una caja o la selección de un jugador, de
cualquier forma), opcionalmente `as(player)` y una máscara.
- CA1: `set(patrón)`, `replace(máscara, patrón)` y `paste(esquema, lugar, giro, aire)` usan el mismo lenguaje de
  patrones, máscaras y `.schem` que los comandos (`"50% stone, 50% andesite"`, `"#logs"`, la carpeta de esquemas). Un
  patrón o máscara mal escrito falla al pedirla con el mismo mensaje («did you mean») y no edita nada.
- CA2: Es una obra del motor: tandas de 8 ms, turnos con las demás, historial en disco y deshacer. No pide `/wc
  confirm` (la pidió un plugin). Devuelve un `CompletableFuture<EditResult>`: el número de la obra, los bloques
  cambiados y si terminó, se canceló o se rechazó (con el motivo).
- CA3: `as(player)`: con los límites del jugador (tope de bloques, mundos, bloques prohibidos, obras a la vez), en su
  historial (`/wc undo` del jugador la deshace) y con sus mensajes. Sin `as`, a nombre del plugin
  (`#NombreDelPlugin` en el historial), con los límites de los grupos de `api.plugin_groups` y sin mensajes en el chat.
- CA4: `undo(n)` deshace las últimas n obras de ese jugador o de ese plugin.
- CA5: Se puede llamar desde cualquier hilo: la obra empieza en el hilo principal.

**H4 — Enterarse y detener.**
- CA1: `WorldcraftEditEvent` se dispara **antes de toda obra** (comandos, pinceles, herramientas, API, `undo` y
  `redo`) con quién la pide, qué (`set`, `paste`, …), el detalle, el mundo, la caja que puede tocar y el volumen. Si un
  plugin la cancela, la obra no empieza y quien la pidió ve «Another plugin stopped this edit» con el motivo que ese
  plugin dio. Una obra con vista previa se detiene antes de mostrarla.
- CA2: `WorldcraftEditDoneEvent` se dispara al terminar (o cancelarse) con el número, los bloques cambiados y el estado.

**H5 — Plugin de ejemplo** (`plugins/worldcraft-api-example`, `WorldcraftApiExample`, en inglés).
- CA1: Comandos `/wcx selection`, `/wcx select`, `/wcx fill <patrón>` (como el jugador), `/wcx box <mundo> <x1> … <z2>
  <patrón>` y `/wcx paste <esquema> <x> <y> <z> [giro]` (como el plugin), `/wcx undo` (lo último del plugin).
- CA2: Zonas protegidas en su `config.yml`: cancela toda obra de Worldcraft que las toque, con el motivo. Con
  `log_edits: true` anota en la consola cada obra terminada.
- CA3: Su README explica cómo usar la API (dependencia, `plugin.yml`, ejemplos de código).

**H6 — Seguridad, configuración y documentación.**
- CA1: Sección `api` en `config.yml` (`enabled`, `plugin_groups`) revisada por `/wc validate`; un `config.yml` de la
  0.12.0 sin ella carga igual.
- CA2: README de Worldcraft (sección *For plugin developers* con ejemplos), README raíz, tabla de paridad y roadmap.

## Fuera de alcance (al [roadmap](../../roadmap.md))
CoreProtect y el puente con WorldEdit (decisión del dueño, 2026-11-02), respetar regiones de WorldGuard, GriefPrevention
u otros (un plugin de protección puede hacerlo con el evento), máscaras que miran alrededor (`above`, `exposed`, …) desde
la API, formas, terreno y el resto de comandos como métodos (se agregan cuando otro plugin los pida), publicar la API en un
repositorio Maven.

## Decisiones (CLARIFY)
| # | Pregunta | Propuesta de la IA | Respuesta del dueño (2026-11-02) |
|---|---|---|---|
| 1 | ¿Qué piezas entran? (API y evento ~40 %, CoreProtect ~25 %, puente con WorldEdit ~15 %, respetar regiones ~25 %) | API y evento, CoreProtect y puente; regiones después | **Solo la API Java y el evento** |
| 2 | ¿Bajar CoreProtect CE 24.1 al Paper de prueba? | Sí | **No** |
| 3 | El puente con WorldEdit: ¿mandar sus comandos o usar su API GPL? | Mandar sus comandos | **«No quiero que la gente tenga WorldEdit también»**: sin puente |
| 4 | En CoreProtect, ¿a nombre de quién? | Del jugador | **«Nada de CoreProtect»** |

Decisiones de la IA: la API es un paquete propio y estable (`dev.leo.worldcraft.api`, [ADR 0008](../../../adr/0008-api-de-worldcraft.md))
que usa solo tipos de Bukkit en su contrato; todo lo que se pide pasa por la misma puerta que los comandos (`Edits`), así
que límites, máscara de bloques prohibidos, tope por tick, historial y deshacer son los de siempre; el evento se dispara en
esa puerta, después de los límites (un rechazo barato no molesta a los plugins) y antes de la vista previa; un plugin edita
con los grupos de `api.plugin_groups` (de fábrica solo `default`, como un jugador común); el plugin de ejemplo es un
proyecto más de la fábrica para que la suite lo compile y lo pruebe.

## Preguntas que se le hicieron al dueño
1. ¿Qué piezas entran? (API y evento · CoreProtect · puente con WorldEdit · respetar regiones; varias)
2. ¿Apruebas bajar CoreProtect CE 24.1 al Paper de prueba? (sí · no)
3. El puente con WorldEdit: ¿mandar sus comandos o usar su API? (comandos · API)
4. En CoreProtect, ¿a nombre de quién quedan las obras? (del jugador · siempre #worldcraft)

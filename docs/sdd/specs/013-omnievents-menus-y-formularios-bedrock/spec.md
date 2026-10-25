# Spec 013 — OmniEvents: menús de inventario y formularios de Bedrock

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 001, 006, 007, 010, 011, 012 ·
**Origen:** propuesta P10 de la [investigación de plugins exitosos](../../../investigacion/plugins-exitosos.md) (§4 y §6),
elegida por el dueño (2026-10-24); n.º 8 de «Siguientes specs» del [roadmap](../../roadmap.md)

## Problema
Un menú de inventario (una tienda, un selector de kits, un panel de teletransportes) hoy necesita otro plugin
(DeluxeMenus, CommandPanels) con su propio lenguaje de requisitos, o una regla `inventory_click` que compara el título
y la casilla a mano y no impide que el jugador se lleve los ítems. ConditionalEvents no tiene menús. Y los jugadores de
Bedrock que entran por Geyser ven el inventario traducido, incómodo en el teléfono, cuando Floodgate permite mostrarles
un formulario nativo de botones.

## Historias
**H1 — Menú declarado.** Como *administrador*, quiero declarar un menú en YAML y abrirlo desde cualquier regla.
- CA1: Una entrada de `rules/` con `menu:` (en vez de `trigger:`) es un menú: `title`, `rows` (1–6, 3 por defecto),
  `fill` (material para las casillas vacías) e `items`, un mapa nombre → ítem con `slot` (número, lista o rango
  `"0-8"`) y el ítem como en `give_item` (`"MATERIAL;cantidad;nombre;lore"` o un mapa con `material`, `name`, `lore`,
  `amount`, `glow`…). Título, nombre y lore aceptan `{expresiones}`: el título se evalúa al abrir y los ítems, al abrir
  y otra vez después de cada clic.
- CA2: La acción `open_menu: <menú>` (o `{menu, args}`, con los `args` como `var.<nombre>` en el menú y sus clics)
  lo abre al jugador; `close_menu` lo cierra. `/omni menu open <jugador> <menú>` y `/omni menu close <jugador>` hacen
  lo mismo desde la consola; `/omni menu list` lista los menús.
- CA3: `permission`, `conditions` (con su `deny`) del menú son el requisito para abrirlo: si falla, no se abre y corre
  el `deny` (o nada). `on_open` y `on_close` son acciones al abrir y al cerrar.
- CA4: `view: <expresión>` en un ítem decide si se muestra; si dos ítems comparten casilla, se muestra el primero
  visible (así un ítem «bloqueado» puede reemplazar al normal).
- CA5: Los ítems del menú no se pueden sacar, mover ni reemplazar: todo clic y arrastre con el menú abierto se cancela
  (también el shift-clic desde el inventario propio y las teclas de número).

**H2 — El clic es una regla.** Como *administrador*, quiero que un clic tenga condiciones, costo y enfriamiento como
cualquier regla, sin aprender otro lenguaje.
- CA1: `click`, `left_click`, `right_click` y `shift_click` en un ítem son cuerpos de regla: `conditions` (con `deny`),
  `else`, `cost` y `on_cost_fail` (spec 012), `cooldown`/`on_cooldown`, `limit`/`on_limit`, `actions` y `tests`. El
  más específico gana (`right_click` antes que `click`).
- CA2: En el clic, `menu` es el nombre del menú, `slot` la casilla, `click` el tipo (`left`, `right`, `shift_left`,
  `shift_right`, `middle`, `number`, `drop` o `bedrock`), `item` el ítem mostrado y `var.<arg>` los argumentos.
  `close: true` en el clic cierra el menú después de las acciones.
- CA3: Cada clic es una regla con el nombre de su ruta (`tienda.items.espada.click`): aparece en `/omni list`,
  `/omni stats`, `/omni debug` y `/omni test`, y sus `tests:` corren como los de cualquier regla (`given: { click: right }`).

**H3 — Formularios de Bedrock.** Como *administrador* de un servidor con Geyser y Floodgate, quiero que los jugadores de
Bedrock vean el menú como un formulario nativo, sin declararlo dos veces.
- CA1: Si Floodgate está instalado y el jugador es de Bedrock, `open_menu` le manda un formulario de botones: el título
  del menú, `bedrock.content` como texto y un botón por cada ítem visible que tenga clic (en orden de casilla), con el
  nombre del ítem sin formato o `bedrock.button` del ítem, y `bedrock.image` (URL o ruta de textura) si la tiene.
- CA2: Pulsar un botón corre el clic del ítem (`click`, o `left_click` si no hay `click`) con `click == 'bedrock'`;
  después el formulario se vuelve a mostrar con los valores nuevos, salvo con `close: true` o si las acciones abrieron
  o cerraron un menú. Cerrar el formulario corre `on_close`.
- CA3: `bedrock: false` en un menú muestra el inventario también a Bedrock (Geyser lo traduce). `is_bedrock()` dice si
  el jugador es de Bedrock (falso sin Floodgate). Sin Floodgate todo funciona igual que en Java y no hay avisos.
- CA4: La respuesta del formulario llega por la red: se valida (botón dentro de rango, jugador conectado, el formulario
  es el último que se le mandó) y se pasa al hilo principal antes de correr nada.

**H4 — Validado y con herramientas.** Como *administrador*, quiero que un menú mal escrito me diga dónde está el error.
- CA1: Al cargar se valida con ruta y «¿quisiste decir?»: claves desconocidas del menú, del ítem y de `bedrock`; `rows`
  fuera de 1–6; casillas fuera del menú; material constante desconocido; dos ítems en la misma casilla sin `view` en el
  primero (aviso: el segundo nunca se ve); `open_menu` con un nombre constante que no es un menú.
- CA2: `/omni reload` cierra los menús abiertos (sus definiciones cambiaron) sin correr `on_close`; un jugador que sale
  con el menú abierto no deja nada colgado. Sin menús no hay oyentes de inventario (constitución III.1).

**H5 — Documentado y compatible.** Como *administrador*, quiero autocompletado y referencia y saber con qué se probó.
- CA1: `menu` y sus claves, `open_menu`, `close_menu`, `menu_click` e `is_bedrock` están en su registro (esquema JSON
  y referencia regenerados).
- CA2: Los ejemplos traen una tienda con `cost` y un selector con `view`, con `tests:` en sus clics.
- CA3: `plugin.yml` suma `floodgate` a `softdepend`; el README del plugin lista Geyser y Floodgate con las versiones
  probadas.

## Fuera de alcance
- Paginación, ítems generados de una lista (`for_each` en ítems) y refresco periódico (`refresh:`): una spec propia.
- Formularios de Bedrock con campos (texto, deslizadores) o modales de sí/no; menús de almacén donde el jugador deja
  ítems; cabezas con la textura de un jugador.
- Abrir un menú desde un disparador asíncrono sin pasar al hilo principal (el motor ya pasa las acciones que no son
  seguras, plan 001 §3).

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Número de spec? | 013: la 003 está reservada (paridad con ConditionalEvents, otra sesión) |
| 2 | ¿Alcance? (estimado: estáticos ≈ 1 sesión larga; refresco +0.3; paginación +1) | Menús estáticos con requisitos; clic como mini-regla con `cost` de la 012 |
| 3 | ¿Bedrock en esta spec? | Sí, con Geyser y Floodgate en el servidor de prueba y un bot de Bedrock |
| 4 | ¿Dónde se declaran? | En `rules/`, entrada con `menu:` (mismo cargador, paquetes y plantillas) |
| 5 | ¿Descargas? | Aprobadas, solo al servidor de prueba y a `tests-e2e` (fuera del repo, sha256 fijado): Geyser-Spigot 2.6.2 b796 (1.21.4) y 2.10.1 b1184 (26.1.2), floodgate-spigot 2.2.5 b141 y el paquete npm `bedrock-protocol` 3.59.0. OmniEvents habla con Floodgate por reflexión, sin dependencia de compilación |
| 6 | ¿Cómo se ve en Bedrock? | Botones automáticos (título y un botón por ítem con clic), ajustables con `bedrock:` |
| 7 | ¿Versión y capturas? | 1.8.0, sin capturas |

## Preguntas abiertas
- (ninguna)

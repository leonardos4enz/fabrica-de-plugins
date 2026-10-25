# Plan 013 — Diseño técnico

## 1. Arquitectura

### 1.1 Un menú es una entrada de `rules/` (`menu/Menu.java`, `menu/MenuLoader.java`)
`RuleLoader.loadFolder` ya junta las entradas de cada archivo con su paquete y resuelve `extends` y `${parámetros}`
(spec 011). Después de expandir una entrada, si tiene `menu:` la compila `MenuLoader` en lugar de `parse`:

| Pieza | Qué es |
|---|---|
| `Menu` | nombre (con paquete, `tienda` o `pack:tienda`), archivo, título (`Template`), filas, `fill`, `permission`, condiciones (`Condition`, con `deny`), `on_open`, `on_close`, `bedrock` (`content`, `enabled`) e ítems en orden |
| `Menu.Item` | clave, casillas, `ItemSpec` (el de `give_item`, ahora público), `view` (`Expression` o null), clics por tipo y `bedrock` (`button`, `image`), `close` |
| clic | una `Rule` normal con `trigger: menu_click`, compilada por `RuleLoader.parse` con el cuerpo del clic. Se llama como su ruta (`tienda.items.espada.click`), va al mapa de reglas y por eso tiene condiciones, `deny`, `cost`, enfriamiento, límite, estadísticas, traza y `tests:` sin código nuevo |

`loadFolder` devuelve las reglas (con los clics) y `menus()` deja los menús, como `packs()`. Los nombres de menú
comparten el espacio de nombres de las reglas (la clave de primer nivel ya es única).

Validación al cargar (rutas `tienda.menu.items.espada.slot`…): claves desconocidas de `menu`, del ítem, de `bedrock` y
de cada clic (`RuleOptions.MENU_KEYS`, `MENU_ITEM_KEYS`, `MENU_BEDROCK_KEYS`, con «¿quisiste decir?»), `rows` 1–6,
casillas `0..rows*9-1` (número, lista o `"a-b"`), material constante desconocido, `trigger` dentro de un clic (error:
lo pone el menú), raíces de las expresiones del título, ítems y `view` (`player`, `world`, `location`, `menu`, `var`).
Casilla repetida sin `view` en el primero: aviso. `open_menu` con nombre constante: `ActionParser` anota la referencia
y `RuleLoader` la busca al terminar la carpeta (con la búsqueda relativa al paquete de la 011): si no existe, error en
la regla que la usa.

### 1.2 Disparador y acciones (`Triggers`, `Actions`)
- `menu_click` (`Kind.MENU`, grupo «menús», raíces `player`, `world`, `location`, `menu`, `slot`, `click`, `item`):
  solo lo usan los clics; una regla suelta con `trigger: menu_click` es error («va dentro de un menú»).
- `open_menu: <menú>` o `{menu, args}` (grupo «jugador»): `MenuService.open(jugador, menú, args, ctx)`. Los `args` se
  evalúan al abrir y viven en el menú abierto como `var.<nombre>`. `close_menu`: cierra el menú de OmniEvents que tenga
  abierto (otro inventario no lo toca). Ambas en `SANDBOXED`: en una prueba quedan como efecto (`expect.actions`).
- `is_bedrock()` (`Functions`): `ctx.service("is_bedrock")`; en el `Sandbox`, `given.bedrock`.

### 1.3 Menú abierto en Java (`menu/MenuService.java`, `menu/MenuHolder.java`)
- `MenuHolder implements InventoryHolder`: menú, jugador, args, casilla → ítem mostrado. Así se reconoce el inventario
  sin comparar títulos (un cofre renombrado con el mismo título no es un menú: STRIDE).
- Abrir: permiso → condiciones (si falla una, su `deny` o nada) → `on_open` → se arma el inventario: por cada ítem en
  orden, `view` y, en sus casillas libres, el `ItemStack` de `ItemSpec.build`; `fill` en lo que quede. El contexto del
  menú es un `ExecContext` con `player`, `world`, `location`, `menu` y los args como locales.
- `InventoryClickEvent` (prioridad `LOWEST`, aunque otro lo haya cancelado) con el menú arriba: **siempre se cancela**.
  Si la casilla es del menú y tiene un ítem con clic para ese tipo, corre la regla del clic con `RuleManager.fire`.
  Después, si sigue abierto, se vuelve a dibujar (los `{…}` muestran los valores nuevos). `close: true` cierra.
  `InventoryDragEvent` sobre el menú se cancela. Cerrar (`InventoryCloseEvent`) corre `on_close`.
- Tipo de clic: `LEFT` → `left`, `RIGHT` → `right`, `SHIFT_LEFT`/`SHIFT_RIGHT`, `MIDDLE`, `NUMBER_KEY` → `number`,
  `DROP`/`CONTROL_DROP` → `drop`. Gana `left_click`/`right_click`/`shift_click` (ambos shift) y si no, `click`.
- Abrir o cerrar un inventario dentro de un `InventoryClickEvent` está prohibido en Bukkit: `open_menu` y `close_menu`
  se pasan al tick siguiente cuando corren dentro de un clic (y siempre si no es el hilo principal).
- Los oyentes (clic, arrastre, cierre, salida del jugador) se registran solo si hay menús (III.1). `reload` cierra los
  menús abiertos sin `on_close` (las definiciones cambiaron) y vuelve a registrar.

### 1.4 Bedrock (`hook/Forms.java`, `hook/FloodgateForms.java`)
- `Forms`: `isBedrock(Player)` y `send(Player, title, content, List<Button>, IntConsumer onButton, Runnable onClose)`.
  `NONE` sin Floodgate. `FloodgateForms` habla con la API de Floodgate y Cumulus **por reflexión** (sin dependencia de
  compilación, como Citizens en la 012): `FloodgateApi.getInstance()`, `isFloodgatePlayer(UUID)`,
  `SimpleForm.builder()…title/content/button/validResultHandler/closedOrInvalidResultHandler/build`,
  `sendForm(UUID, Form)`; los métodos se resuelven una vez al habilitar con el cargador de clases de Floodgate.
- `MenuService.open` con un jugador de Bedrock (y `bedrock.enabled` del menú): mismo permiso, condiciones y `on_open`;
  los botones son los ítems visibles **con clic** en orden de casilla (texto: `bedrock.button` o el nombre del ítem sin
  formato; imagen: `bedrock.image`, URL si empieza con `http`, si no ruta de textura). Se guarda por jugador el
  formulario vigente (un número que crece) con la lista de botones.
- La respuesta llega en el hilo de red de Floodgate: se pasa al hilo principal; se descarta si el jugador ya no está,
  si no es el formulario vigente o si el botón está fuera de rango (H3 CA4). Corre el clic con `click: bedrock`; si el
  menú no se cerró ni se abrió otro, se manda otra vez. Cerrar el formulario corre `on_close`.

### 1.5 Herramientas
- `/omni menu list` (menús con archivo, filas e ítems), `/omni menu open <jugador> <menú>`, `/omni menu close
  <jugador>`; permiso `omnievents.command.menu` (`op`). `/omni stats` suma «menús: N (abiertos: M)» y
  «Integraciones: …, Floodgate».
- `RuleTester`: `given.bedrock` (booleano) y las raíces de `menu_click` (`click`, `slot`, `menu`) en `given`.

### 1.6 Registro, esquema y referencia
`RuleOptions`: opción `menu` (`Kind.MENU`) con `MENU_KEYS`, `MENU_ITEM_KEYS`, `MENU_BEDROCK_KEYS`, `MENU_CLICK_KEYS`
(`click`, `left_click`, `right_click`, `shift_click`) y `close`. El esquema acepta en primer nivel una regla o un menú
(`required: [menu]`); cada clic es un cuerpo de regla sin `trigger`. La referencia gana una sección «Menús».
`./gradlew :omnievents:schema` regenera.

## 2. Contrato de configuración
```yaml
tienda:
  menu:
    title: "<dark_green>Tienda · {money_format(money())}"
    rows: 3
    fill: GRAY_STAINED_GLASS_PANE
    permission: tienda.usar           # opcional
    conditions:                        # requisito para abrir (con su deny)
      - expr: "player.world.name != 'arena'"
        deny: ["message: <red>No en la arena"]
    on_open: ["sound: UI_BUTTON_CLICK"]
    on_close: []
    bedrock:
      content: "Elige qué comprar"
    items:
      espada:
        slot: 11                       # o [11, 12] o "0-8"
        item: "DIAMOND_SWORD;1;<aqua>Espada;<gray>500 monedas|<gray>Clic derecho: ver precio"
        bedrock: { button: "Espada (500)", image: "textures/items/diamond_sword" }
        click:
          cost: { money: 500 }
          actions: ["give_item: DIAMOND_SWORD"]
          on_cost_fail: ["message: <red>Te falta {var.cost_missing}"]
          close: true
          tests:
            - given: { money: 800 }
              expect: { branch: actions, actions: [give_item] }
        right_click:
          actions: ["message: Cuesta 500"]
      vip_bloqueado:
        slot: 15
        view: "!perm('rango.vip')"
        item: "BARRIER;1;<red>Solo VIP"
      vip:
        slot: 15
        item: "NETHER_STAR;1;<gold>Kit VIP"
        click: { cooldown: 1d, actions: ["call: kit_vip"] }

abrir_tienda:
  trigger: command
  command: tienda
  actions: ["open_menu: tienda"]       # o { open_menu: { menu: tienda, args: { descuento: 10 } } }
```
| Clave | Dónde | Tipo | Validación |
|---|---|---|---|
| `menu` | entrada | mapa | claves de `MENU_KEYS`; sin `trigger` en la misma entrada |
| `title` | `menu` | plantilla | — (vacío por defecto) |
| `rows` | `menu` | entero | 1–6 (3) |
| `fill` | `menu` | material | conocido |
| `permission`, `conditions`, `on_open`, `on_close` | `menu` | texto, condiciones, acciones | como en una regla |
| `bedrock.content`, `bedrock.enabled` | `menu` | plantilla, booleano | — |
| `items.<nombre>.slot` (`slots`) | ítem | número, lista o `"a-b"` | dentro del menú |
| `items.<nombre>.item` | ítem | como `give_item` | material constante conocido |
| `items.<nombre>.view` | ítem | expresión | raíces del menú |
| `click`, `left_click`, `right_click`, `shift_click` | ítem | cuerpo de regla | sin `trigger`; claves de regla |
| `…click.close` | clic | booleano | — |
| `items.<nombre>.bedrock.button`, `.image` | ítem | plantilla, texto | — |
| `open_menu` | acción | nombre o `{menu, args}` | menú existente si es constante |
| `close_menu` | acción | — | — |
| `given.bedrock` | `tests` | booleano | — |

Sin cambios en `config.yml` ni en `data/`. Un archivo de 1.7.0 carga igual (IV.5): `menu` era una opción desconocida.

## 3. Hilos y rendimiento
- Todo se compila al cargar (III.2): plantillas, `view`, `ItemSpec`, clics. Abrir un menú evalúa unas plantillas y arma
  hasta 54 `ItemStack`; un clic corre una regla y vuelve a armar el inventario (igual de barato).
- Bukkit solo en el hilo principal (III.3): la respuesta de Bedrock llega en otro hilo y se pasa al principal.
- Oyentes de inventario solo con menús (III.1): `/omni stats` los cuenta en «oyentes».

## 4. Persistencia
Sin datos nuevos. Un enfriamiento o límite de un clic se guarda como el de cualquier regla, por su nombre de ruta.

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación | Un jugador renombra un cofre con el título del menú para disparar sus clics | El menú se reconoce por su `InventoryHolder` propio, no por el título |
| Suplantación | Un cliente de Bedrock manda la respuesta de un formulario viejo o de otro menú | Solo vale la respuesta del formulario vigente de ese jugador (número por jugador) y un botón dentro de rango |
| Manipulación | Sacar los ítems del menú (shift-clic, teclas de número, arrastrar, doble clic que junta) | Todo clic y arrastre con el menú arriba se cancela en `LOWEST`, aunque otro plugin lo descancele después se vuelve a cancelar en `MONITOR` |
| Manipulación | Texto del jugador en el título o el lore (`{player.name}` con etiquetas) | Las plantillas escapan lo interpolado como en `message` (II.2) |
| Repudio | Se cobró en el clic y no se entregó | El clic es una regla: traza en `/omni debug`, estadísticas, y `cost` todo o nada con devolución (012) |
| Divulgación | `/omni menu open` a otro jugador | Permiso `omnievents.command.menu` (`op`) |
| Denegación de servicio | Clics muy rápidos con un menú pesado | Cada clic arma a lo sumo 54 ítems compilados; `cooldown` por clic disponible |
| Elevación de privilegios | La reflexión sobre Floodgate expuesta a la configuración | La reflexión es interna y fija (II.1); las expresiones solo ven `is_bedrock()` |
| Elevación de privilegios | Respuesta de Bedrock procesada en el hilo de red | Se pasa al hilo principal antes de leer o tocar nada (III.3) |

## 6. Pruebas
- **Unitarias** (sin servidor): carga de menús (estructura, casillas y rangos, errores con ruta y «¿quisiste decir?»,
  avisos de casilla repetida, `open_menu` a un menú que no existe, menús en paquetes con `extends`), clics como reglas
  (nombre, `tests:` con `click`, `cost` y `given.bedrock`), disposición del menú con `view` y el primer visible por
  casilla, botones de Bedrock (orden, texto sin formato, solo ítems con clic), tipo de clic → clic que corre,
  validación de la respuesta de un formulario, el ejemplo con sus pruebas, esquema regenerado.
- **En el juego** (compuerta 3a, con Geyser y Floodgate): Ana abre la tienda con un comando, ve el título y los ítems,
  compra con clic izquierdo (cobra y entrega), clic derecho muestra el precio, shift-clic no saca nada, un ítem con
  `view` falso no se ve, cerrar corre `on_close`. Un jugador de Bedrock (`bedrock-protocol`) recibe el formulario con
  sus botones, pulsa uno (corre el clic y vuelve el formulario con el valor nuevo) y lo cierra (`on_close`).

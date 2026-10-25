# Verify 013 — OmniEvents: menús de inventario y formularios de Bedrock

**Fecha:** 2026-10-25 · **Versión:** OmniEvents 1.8.0

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ 169/169 (14 nuevas) | `./gradlew build`: `MenusTest` (13: el menú se compila con sus reglas de apertura, clics y cierre; disposición con `view` y el primer visible por casilla; botones de Bedrock solo con clic, en orden de casilla, texto sin formato o `bedrock.button`; el clic más específico gana y Bedrock usa `click` o `left_click`; `close` solo en su clic; tipos de clic de Bukkit, sin doble clic; `tests:` de los clics con `click`, `cost` e `inventory`; `is_bedrock()` con `given.bedrock`; errores con ruta y «¿quisiste decir?»; casilla repetida sin `view` es aviso; `open_menu` a un menú que no existe deja fuera la regla o el menú entero; raíces de título, ítems y `view`; menús en un paquete con `extends`, `${parámetros}` y búsqueda relativa; el ejemplo con sus pruebas) y `FormulariosTest` (respuesta de Bedrock: solo la del formulario vigente y con un botón en rango). El ejemplo `menus.yml` (5 casos) pasa en `EjemplosPruebasTest` y valida contra el esquema regenerado. Arnés: 21/21 (2 nuevas en `lib/plugins-extra.test.mjs`: `ponerClave` y `configurarGeyser`) |
| 2. Servidor real (1.21.4) | ✔ | `compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni test" "omni pack list" "omni menu list" "omni reload" "omni stats" "omni reload" "omni stats"`: «Integraciones detectadas: Vault, WorldGuard, Citizens, Floodgate»; 52 reglas (con las de los dos menús de ejemplo), 0 errores, 0 avisos; autoprueba en verde; «25/25 pasan» (entre ellas `tienda.menu.items.espada.left_click 2/2`, `tienda.menu.items.pan.click` y `kits.menu.items.vip.click`); `menu list`: «Menús (2, 0 abiertos)»; dos `reload` con 11 oyentes, 3 temporizadores, 8 oyentes y 1 tarea de estado, 10 comandos, 5 oyentes de regiones y 5 de menús (iguales); segundo arranque igual; log sin errores |
| 3a. En el juego, Paper 1.21.4 (build 232, Java 24) | ✔ 44/44 dos veces | 2 escenarios nuevos (`013 …`) + los 42 anteriores. Con Geyser-Spigot 2.6.2-b796 y Floodgate 2.2.5-b141 además de los plugins de la 012; jugador de Bedrock 1.21.70 (bedrock-protocol 3.59.0) |
| 3a. En el juego, Paper 26.1.2 (build 74, Java 25) | ✔ 44/44 dos veces | Mismo jar; Geyser-Spigot 2.10.1-b1184 y Floodgate 2.2.5-b141; jugador de Bedrock 26.30 |

### Escenarios nuevos
| ID | Qué prueba |
|---|---|
| 013 H1 CA1 · H1 CA4 · H1 CA5 · H2 CA1 · H2 CA2 | Ana usa `/e2emenu` (`open_menu` con `args: { precio: 5 }`): `on_open` dice `bedrock false`; la ventana tiene el título, los ítems con su nombre y el relleno; la TNT con `view: false` no se ve y su casilla la toma el ítem siguiente. El clic izquierdo cobra 5 esmeraldas y entrega la espada; el segundo no alcanza («e2e falta 4 × EMERALD») y no cobra; el clic derecho corre su propio cuerpo con `click` y `slot`; el contador se vuelve a dibujar («e2e clics 1») y su `pvar` vale 1; shift-clic, tomar el relleno o un ítem no sacan nada; el botón con `close: true` corre su clic, cierra y corre `on_close`; al final Ana no tiene ningún ítem del menú |
| 013 H3 CA1 · H3 CA2 · H3 CA4 | Un jugador de Bedrock entra por Geyser y Floodgate (`.Beb_e2e`); `/omni run e2e_abrir_menu` le manda un formulario con el título, `bedrock.content` con el arg y tres botones (solo los ítems visibles con clic, en orden de casilla; `bedrock.button` con su expresión); `on_open` dice `bedrock true`. El botón del contador corre su clic y el formulario vuelve con «e2e contar (1)»; contestar el formulario viejo no corre nada; el botón con `close: true` corre su clic y `on_close` sin volver a mandar el formulario; cerrar otro con la X corre `on_close` |

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (menú, `open_menu`/`close_menu`, requisito, `view`, ítems que no salen) | Escenario 013 de Java, `MenusTest.H1_*`, compuerta 2 (`menu list`) |
| H2 (el clic es una regla) | Escenario 013 de Java, `MenusTest.H2_*`, `tests:` de los clics en el ejemplo y en la compuerta 2 |
| H3 (Bedrock) | Escenario 013 de Bedrock en las dos versiones, `MenusTest.H1_CA4_H3_CA1_*` y `H3_CA3_*`, `FormulariosTest` |
| H4 (validación, `reload`, oyentes solo con menús) | `MenusTest.H4_*`, compuerta 2 (dos `reload` iguales, 5 oyentes de menús con los ejemplos) |
| H5 (registro, esquema, ejemplos, `softdepend`, README) | `SchemaTest` (esquema al día, los ejemplos validan), `EjemplosPruebasTest`, `plugin.yml`, README |

No se probó en el juego: `permission`, `conditions` y `cost` para abrir (es la misma regla que corre cualquier disparo; lo
cubren las unitarias de la apertura), `/omni reload` con un menú abierto (se cierra con `silent`; la compuerta 2 recarga sin
jugadores), `bedrock.image` (el cliente de prueba no dibuja) y `bedrock: { enabled: false }`. Riesgo bajo.

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Media (arnés) | Desde la 012, el servidor de prueba mandaba métricas de bStats (WorldEdit, WorldGuard, EssentialsX… lo traen activado): telemetría que la constitución (II.4) pide apagada | `configurarGeyser` pone `enabled: false` en `plugins/bStats/config.yml` y apaga las métricas de Geyser; prueba en `plugins-extra.test.mjs`, que falla con el arnés anterior (nada lo apagaba) |
| F-02 | Baja (arnés) | Con el RakNet en JavaScript (`jsp-raknet`) el cliente de Bedrock no conectaba a Geyser («Connect timed out») | El cliente usa el RakNet nativo (el de bedrock-protocol por defecto); lección en `CLAUDE.md` |

### Después del cierre: OmniEvents 1.8.1 (servidor de demostración, 2026-10-25)
Un servidor de demostración con un paquete de reglas grande (lobby, menús, tienda, kits, trivia, NPC) y un bot que lo
recorrió encontraron dos bugs. Se corrigieron en la 1.8.1.

| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-03 | Alta | Los enfriamientos y usos de un clic de menú se perdían al salir y volver a entrar: el nombre de la regla lleva puntos (`demo:principal.menu.items.diario.click`), YAML lo guarda en `data/players/` como secciones anidadas y `DataStore` solo leía el primer nivel. Un diario o un kit diario se cobraba de nuevo reconectándose | `DataStore` lee las claves completas (`getKeys(true)`): los archivos ya guardados se leen bien sin migrar. `DataStoreTest.F03_enfriamientos_y_usos_de_reglas_con_puntos_persisten` falló con la 1.8.0 (comprobado) y pasa con la corrección |
| F-04 | Media | `has_item('COMPASS')` no contaba una brújula con nombre: `containsAtLeast` compara el ítem exacto (como `removeItem` en 002 F-02), y la regla de bienvenida volvía a dar la brújula en cada entrada | `has_item` cuenta por material, como `cost.items` (`PlayerPurse.count`). El escenario `013 F-04` (2 diamantes con nombre → `has_item('DIAMOND', 2)`) dio `false` con el jar 1.8.0 (comprobado) y pasa con el 1.8.1 |

Nota (no es bug): un color dentro de una `{expresión}` (`{x ? '<red>a' : '<green>b'}`) se ve literal, porque lo que
sale de una expresión se escapa (constitución II.2). Va fuera de las llaves o en dos ítems con `view`; lo dice el README.

Compuertas de la 1.8.1 (2026-10-25): unitarias 170/170; compuerta 2 limpia (52 reglas, 0 errores, 0 avisos, 25/25
pruebas, dos `reload` iguales); 45/45 escenarios dos veces en Paper 1.21.4 y otras dos en 26.1.2.

En el desarrollo, el menú de la suite se llamó primero `e2e_tienda` y chocó con una regla de la spec 006: el cargador lo
dijo con su error («el nombre de regla 'e2e_tienda' ya existe en e2e/e2e.yml»). No es un bug: se renombró a `e2e_menu_tienda`.

## Revisión exploratoria (3b, agente)
- **Compatibilidad (IV.5):** sin menús nada cambia (los 42 escenarios anteriores pasan igual) y no hay oyentes de
  inventario. Un archivo de 1.7.0 carga igual.
- **Seguridad:** el menú se reconoce por su `InventoryHolder`, no por el título; todo clic y arrastre con el menú arriba se
  cancela en `LOWEST` y otra vez en `MONITOR`; la respuesta de Bedrock se pasa al hilo principal y solo vale la del
  formulario vigente; la reflexión sobre Floodgate es interna y fija. `/omni menu` pide `omnievents.command.menu` (`op`).
- **Rendimiento:** abrir y redibujar arman a lo sumo 54 ítems compilados al cargar; la recarga tarda lo mismo que en la 012.
- **Límites conocidos (documentados):** el título no se redibuja después de un clic (solo los ítems); en Bedrock solo se
  ven los ítems con clic; sin paginación, ítems de listas ni refresco periódico (roadmap: «menús dinámicos»).
- **Terceros:** Geyser baja el jar del cliente de Minecraft de Mojang en su primer arranque (para sus traducciones), solo
  en el servidor de prueba.

## README
- `plugins/omnievents/README.md`: sección «Menús de inventario y formularios de Bedrock», comandos (`/omni menu`),
  instalación y Compatibilidad (1.8.0; Floodgate y Geyser con las versiones probadas).
- `README.md` raíz: fila de OmniEvents en 1.8.0 y cómo la suite baja Geyser y Floodgate y conecta un jugador de Bedrock.
  Roadmap e índice (`docs/sdd/README.md`) con la 013. Esquemas y referencia regenerados con `./gradlew :omnievents:schema`.
- Capturas: ninguna nueva (CLARIFY 7).

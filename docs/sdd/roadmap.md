# Roadmap de la fábrica

## Estado
| Spec | Estado |
|---|---|
| 001 OmniEvents: núcleo | Verificado: unitarias 38/38, servidor real y en el juego (3a) aprobados ([verify](specs/001-omnievents-nucleo/verify.md)) |
| 002 Fábrica: pruebas en el juego | Verificado: `npm --prefix tests-e2e test` 21/21 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/002-fabrica-pruebas-en-juego/verify.md)) |
| 004 Fábrica: capturas en el juego | Verificado: `npm --prefix tests-e2e run capturas` genera la [galería de OmniEvents](../omnievents/galeria.md) ([verify](specs/004-fabrica-capturas-en-juego/verify.md)). Desde ahora, capturas solo puntuales (lección del dueño) |
| 005 OmniEvents: enable/disable sin re-sincronizar comandos | Verificado: 1.0.1, F-01 de la 004 resuelto; `npm --prefix tests-e2e test` 22/22 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/005-omnievents-comandos-sin-resync/verify.md)) |
| 006 OmniEvents: herramientas de autor (P1, P2, P4) | Verificado: 1.1.0; 62 unitarias; 27/27 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/006-omnievents-herramientas-de-autor/verify.md)) |
| 007 OmniEvents: esquema JSON y referencia generada (P3) | Verificado: 1.2.0; esquema y [referencia](../omnievents/referencia.md) generados del registro; 73 unitarias; 27/27 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/007-omnievents-esquema-y-referencia/verify.md)) |
| 008 OmniEvents: disparador de estado (P6) | Verificado: 1.3.0; `trigger: state` con `on_enter`/`on_exit` sin sondeo, `in_state`, estados del servidor y `/omni state`; 94 unitarias; 34/34 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/008-omnievents-disparador-de-estado/verify.md)) |
| 009 OmniEvents: variables con caducidad y en objetos (P5) | Verificado: 1.4.0; `expire` y `ttl()`, `trigger: var_expire`, `objeto.data.nombre` en bloques, entidades e ítems (PDC), `/omni data` y migración de `data/` con respaldo; 113 unitarias; 37/37 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/009-omnievents-variables-caducidad-y-objetos/verify.md)) |
| 010 OmniEvents: pruebas de reglas en el YAML (P7) | Verificado: 1.5.0; `tests:` con `given` y `expect` sin servidor ni efectos, `/omni test`, pruebas al cargar y de los ejemplos en gradle; 123 unitarias; 38/38 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/010-omnievents-pruebas-de-reglas/verify.md)) |
| 011 OmniEvents: paquetes, plantillas y versión (P8) | Verificado: 1.6.0; carpetas con `pack.yml` y nombres `paquete:regla` con búsqueda relativa, `template`/`extends` con parámetros `${nombre}`, `requires` y `migrations` al subir de versión (`data/packs.yml`), `/omni pack`; 143 unitarias; 39/39 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/011-omnievents-paquetes-plantillas-version/verify.md)) |
| 012 OmniEvents: integraciones con costos (P9, Vault, WorldGuard, Citizens) | Verificado: 1.7.0; `cost` todo o nada con `on_cost_fail` y devolución, dinero de Vault, `region_enter`/`region_leave` e `in_region()`, `npc_click`/`npc_left_click` e `is_npc()`; 155 unitarias; 42/42 dos veces en Paper 1.21.4 y en 26.1.2 con EssentialsX, WorldGuard y Citizens ([verify](specs/012-omnievents-integraciones-con-costos/verify.md)) |
| 013 OmniEvents: menús de inventario y formularios de Bedrock (P10) | Verificado: 1.8.1 (F-03 y F-04 corregidos tras el cierre); entradas `menu:` con `open_menu`/`close_menu`, `view`, clics como reglas (`conditions`, `cost`, `cooldown`, `tests`), ítems que no se pueden sacar, formulario de botones para Bedrock con Floodgate e `is_bedrock()`, `/omni menu`; 170 unitarias; 45/45 dos veces en Paper 1.21.4 y en 26.1.2 con Geyser, Floodgate y un jugador de Bedrock ([verify](specs/013-omnievents-menus-y-formularios-bedrock/verify.md)) |
| 014 OmniEvents: paridad con ConditionalEvents | Verificado: 1.9.0; tabla completa contra el jar de CE 4.65.1, `centered_message`, `firework`, `item_repair`, permiso `omnievents.bypasscooldown.<regla>` y `cooldown_bypass`, `/omni cooldown|limit reset` con `*`, importador que traduce todo lo que tiene equivalente; 182 unitarias; 49/49 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/014-omnievents-paridad-conditionalevents/verify.md)) |
| 015 OmniEvents: paridad con ConditionalEvents, segunda parte | Verificado: 1.10.0; `give_item` con `slot`, `flags`, `durability`, `item_model`, `skull_owner`; `remove_item` con `name:`/`lorecontains:` y `remove_item_slot`; `summon` en mapa; `json_message` propia; `tab_complete`; disparadores `player_statistic`, `player_air_change`, `player_tab_complete` e `item_mend`; `pick`, `block_at`, `is_number`; `/omni interrupt` y `-s`; `%omni_enabled_r%`; importador sin «REVISAR» salvo lo que no tiene equivalente; 191 unitarias; 55/55 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/015-omnievents-paridad-conditionalevents-2/verify.md)) |
| 016 OmniEvents: paridad con ConditionalEvents, tercera parte (detalles y ProtocolLib) | Verificado: 1.11.0; `actionbar` con duración, `effect` con ícono, `firework` con `shot_direction`; funciones `world`, `location`, `near`, `slice`, `relative`, `looking_block`/`looking_entity`, `is_outside`, `random_last` y `block.states`; el importador traduce todas las variables pedidas (F-01 `%damage%` y F-02 `%block_data%` corregidos); `OmniRuleFireEvent` para otros plugins; `player_receive_chat` con ProtocolLib opcional ([ADR 0004](../adr/0004-protocollib-opcional.md)); 200 unitarias; 57/57 dos veces en Paper 1.21.4 y en 26.1.2 con ProtocolLib ([verify](specs/016-omnievents-paridad-conditionalevents-3/verify.md)) |
| 017 OmniEvents: documentación pública en inglés | Verificado: wiki en inglés en `docs/site/` (12 páginas) para GitBook (`SUMMARY.md`, `.gitbook.yaml`) y Zensical (`mkdocs.yml`); `DocsSiteTest` carga cada regla de ejemplo, compara los dos índices y exige todos los disparadores, acciones y funciones; 203 unitarias; `zensical build` sin avisos ([verify](specs/017-omnievents-documentacion-publica-en-ingles/verify.md)) |
| 018 OmniEvents: minijuegos con la configuración actual | Verificado: paquete de ejemplo `parkour` (apagado por defecto) hecho solo con reglas: placas de inicio, checkpoint y meta sin coordenadas, caídas, tiempo límite, salir y reiniciar, récord personal y del servidor, limpieza al desconectarse; `/parkour construir`; guía «Cómo hacer un minijuego» en el README y en `docs/site/`; sin capacidades nuevas (decisión del dueño); 203 unitarias; 58/58 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/018-omnievents-minijuegos-con-configuracion/verify.md)) |
| 019 OmniEvents: ejemplos en inglés y regiones de WorldGuard | Verificado: 1.12.0; paquete `examples` en `rules/examples-en/` (apagado por defecto) con bienvenida, recompensa diaria con racha, zonas sin plugins, eventos comunes y regiones de WorldGuard y un ejemplo de cada plugin opcional (Vault, Citizens, PlaceholderAPI, Floodgate, ProtocolLib), cada regla con `tests:` (53 casos); F-01: el jar no copiaba el `parkour` ni `estilo_ce.yml`; F-02: una regla apagada ya no avisa de la integración que le falta; 207 unitarias; 59/59 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/019-omnievents-ejemplos-en-ingles-y-regiones/verify.md)) |
| 020 OmniEvents: página de presentación con imágenes | Verificado: logo nuevo y póster en pixel art para SpigotMC, Modrinth y Hangar (7 franjas de 1280 px, 2 GIF con 4 escenas nuevas en inglés), elegido por el dueño entre tres direcciones; descripciones en BBCode y Markdown con transcripción; `PresentacionTest`; sin capturas de más ([verify](specs/020-omnievents-pagina-de-presentacion/verify.md)) |
| 021 SkinStatues: estatuas de bloques con la skin de un jugador (plugin nuevo) | Verificado: 0.1.0, el segundo plugin de la fábrica y el primero en inglés ([ADR 0005](../adr/0005-plugins-en-ingles.md)); `/statue build <name> <height> [pose]` con skin de `skins/` o de Mojang (red apagada por defecto), voxelización inversa con las dos capas unidas, 6 poses con codo y rodilla sin grietas, capa, paletas como lista de bloques permitidos (145 bloques con su color medido), construcción por tandas, diario para `undo`/`remove` que devuelven el terreno; F-01 a F-03 corregidos; 40 unitarias; 10/10 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/021-skinstatues-nucleo/verify.md)) |
| 022 SkinStatues: más poses, poses propias fáciles y coordenadas | Verificado: 0.2.0; 28 poses incorporadas (22 nuevas, ajustadas con la vista previa) que se leen del jar y una copia de referencia (`builtin-poses.yml`), `extends` ángulo por ángulo y `mirror` en `poses.yml`, `/statue build … --at x y z --facing --world` (con `~`; desde la consola), chunks cargados con `getChunkAtAsync` y tickets; 51 unitarias; 14/14 dos veces en Paper 1.21.4 y en 26.1.2; F-01 corregido ([verify](specs/022-skinstatues-poses-y-coordenadas/verify.md)) |
| 023 SkinStatues: estatuas con el link de una skin | Verificado: 0.3.0; `/statue build <link> <height> …` como SkinsRestorer, solo una skin válida (PNG 64×64 o 64×32 de hasta 1 MB, tamaño leído antes de decodificar), solo `https` a sitios públicos (sin localhost, LAN ni reservadas, también tras redirecciones), `network.urls` apagado por defecto y permiso `skinstatues.build.url`, `allowed_hosts`, modelo detectado con `--slim`/`--classic`, caché por huella, `url:sitio#huella` en `list`/`info` sin guardar el link; la suite sirve las skins con un HTTPS local; 65 unitarias; 18/18 dos veces en Paper 1.21.4 y en 26.1.2 ([verify](specs/023-skinstatues-skin-por-link/verify.md)) |
| 024 SkinStatues: multimedia y página de presentación | Verificado: logo y póster por capas como el de la 020 (7 franjas de 1280 px y un GIF de 10 cuadros) con escenas en un mundo de terreno normal: dos estatuas de 100 bloques, una fila de 6 poses, la construcción, por nombre y por link y tres paletas, con skins de youtubers elegidos por el dueño (bajadas a la caché, nunca al repo); BBCode, Markdown y `publicar.md` y la publicación en Modrinth (en revisión), Hangar y SpigotMC; `PresentacionTest` ([verify](specs/024-skinstatues-pagina-de-presentacion/verify.md)) |
| 025 Blockwright: un editor de mundo que no traba el servidor (plugin nuevo, nombre provisional) | Verificado en 1.21.4: 0.1.0, el tercer plugin (nombre provisional, en inglés); [investigación](../investigacion/editores-de-mundo.md) de WorldEdit/FAWE; obras por tandas con 8 ms por tick para todo el plugin y turnos entre jugadores, vista previa con bloques falsos solo para quien edita y `confirm`, contorno con partículas, varita marcada, deshacer en disco y seguro (salta lo cambiado después), patrones y máscaras legibles con «did you mean», presets, límites por grupo con bloques prohibidos, copiar/cortar/pegar/girar/voltear con cofres y carteles; F-01 a F-03 corregidos; 45 unitarias; 8/8 dos veces en Paper 1.21.4; 26.1.2 sin verificar; decisiones de CLARIFY pendientes del dueño ([verify](specs/025-blockwright-nucleo/verify.md)) |

## Por qué empezamos con OmniEvents
OmniEvents es la versión propia de *ConditionalEvents* (Ajneb97): «cuando pase X, si se cumple Y, haz Z». Es el
plugin más general posible y su motor (expresiones, plantillas, acciones, persistencia) es la base que reutilizarán
los siguientes plugins de la fábrica.

## Diagnóstico de ConditionalEvents (lo que hacemos mejor)
| Limitación de ConditionalEvents | Respuesta en OmniEvents | Spec |
|---|---|---|
| Una línea de condición no mezcla `and` y `or`; no hay paréntesis ni `not` | Lenguaje de expresiones completo: `&&`, `\|\|`, `!`, paréntesis, ternario, aritmética, listas, funciones | 001 |
| Las matemáticas en condiciones requieren una opción por evento | La aritmética es nativa del lenguaje | 001 |
| Las acciones son una lista plana; los grupos `execute` emulan un `if` | `if`/`else`, `repeat`, `while` acotado, `for_each`, `random` ponderado, `delay`, `stop`, `call` dentro de las acciones | 001 |
| Sin variables propias (dependen de otros plugins) | Variables temporales, por jugador y globales, persistentes, expuestas a PlaceholderAPI | 001 |
| `one_time` y `cooldown` son fijos (OP los salta, sin ventana de reinicio) | Enfriamientos por jugador o globales y límites de usos con reinicio diario, semanal o por duración; persistentes | 001 |
| Variables de evento fijas por tipo; eventos personalizados con cadenas de métodos | Acceso por propiedades a cualquier objeto (`event.damage`, `player.location.block.type`) con reflexión cacheada y solo de lectura | 001 |
| Comandos propios = interceptar `player_command` | Disparador `command` que registra comandos reales con alias, permiso y autocompletado | 001 |
| Diagnóstico limitado | `/omni debug` muestra en vivo por qué cada condición pasó o falló; `/omni eval`; `/omni stats` con tiempos por regla | 001 |
| Un solo `deny` por evento; para decir qué falta hay que duplicar eventos | `deny` por condición y grupos «al menos N de M» | 006 |
| No se puede probar un evento sin dispararlo | `/omni check` (condiciones con valores, sin efectos), `/omni run --simular` y `/omni debug export` | 006 |
| Sin medición de costo | `/omni cpu` por regla, condición y acción (muestreo) y `budget` con aviso | 006 |
| Sin ayuda al escribir el YAML; la documentación se queda atrás | Esquema JSON para VS Code (autocompletado y errores) y referencia generados del código, con prueba que falla si se desactualizan | 007 |
| Lo que recibe el jugador en el chat solo se lee con ProtocolLib y una regla por evento | `player_receive_chat` con ProtocolLib opcional: solo escucha si una regla lo usa, texto no confiable y tope contra bucles | 016 |
| Lo que depende de un estado («cuando la vida baja de 3») se sondea con `repetitive` y una variable de «ya avisé» | `trigger: state` con `on_enter`/`on_exit`: el motor deduce de qué eventos depende y solo revisa ahí; `check_every` declarado para lo demás | 008 |
| Variables sin caducidad y solo en el jugador o el servidor; los datos de un ítem o un bloque necesitan otro plugin | `expire:` con aviso `var_expire` sin sondeo, `ttl()`, y `objeto.data.nombre` en el PDC de bloques, entidades e ítems | 009 |
| Una regla se prueba jugando; al actualizar nadie vuelve a probar lo que ya andaba | `tests:` en la regla (`given` y `expect`) que corren sin servidor ni efectos con `/omni test`, al cargar y en gradle | 010 |
| Sin espacios de nombres: dos reglas `diaria` chocan; lo que se repite se copia y pega; una configuración compartida no dice su versión ni qué necesita | Paquetes (`pack.yml`, `paquete:regla`), plantillas con `extends` y `${parámetros}`, `requires` y migraciones al subir de versión | 011 |
| Cobrar algo es encadenar condiciones y comandos de consola: si uno falla a mitad, ya se cobró una parte; las regiones de WorldGuard necesitan otro plugin | `cost:` todo o nada con `on_cost_fail` y devolución (Vault, niveles, ítems, variables), `region_enter`/`region_leave`, `npc_click` | 012 |
| Nadie sabía qué le faltaba a OmniEvents frente a CE; el mensaje centrado, los fuegos artificiales, reparar ítems y el permiso por evento para el enfriamiento no existían | Comparación fila por fila con el jar (51 eventos, 53 acciones), lo que faltaba y un importador que traduce todo lo que tiene equivalente | 014 |
| Sin menús: hace falta DeluxeMenus u otro plugin con su propio lenguaje; los jugadores de Bedrock ven el inventario traducido | `menu:` con clics que son reglas (`cost`, `cooldown`, `tests`) y el mismo menú como formulario nativo de Bedrock con Floodgate | 013 |
| Errores de configuración poco precisos | Validación al cargar con archivo, ruta y «¿quisiste decir?»; `/omni validate` | 001 |
| Oyentes siempre registrados | Solo se escucha un evento si alguna regla lo usa | 001 |
| Un minijuego necesita otro plugin (o un desarrollador) | Un parkour completo de ejemplo hecho solo con reglas y la guía del patrón para armar el propio | 018 |
| Los ejemplos vienen activos y en un solo idioma; para probar uno hay que tocar el servidor | Un paquete de ejemplos en inglés, apagado hasta activarlo, con pruebas en cada regla y ejemplos de WorldGuard, Vault, Citizens, PlaceholderAPI, Floodgate y ProtocolLib que cargan sin esos plugins | 019 |
| La página de presentación entra por los ojos (capturas, tarjetas) | La nuestra también, y además dice qué tiene OmniEvents que ConditionalEvents no, con una comparación honesta y ejemplos grabados en el juego | 020 |
| — | Importador de configuraciones de ConditionalEvents | 001 |

## Diagnóstico de WorldEdit y FAWE (lo que hace mejor Blockwright)
De la [investigación de editores de mundo](../investigacion/editores-de-mundo.md) (spec 025).
| Limitación de WorldEdit / FAWE | Respuesta en Blockwright | Spec |
|---|---|---|
| WorldEdit edita en el hilo principal: una obra de un millón de bloques congela o tumba el servidor; FAWE depende de acceso interno al servidor (luz, cofres, bloques fantasma) | Obras por tandas con un tope de ms por tick para todo el plugin, reloj en cada bloque, turnos entre jugadores, chunks asíncronos; solo API de Paper | 025 |
| Se aplica a ciegas; ver la selección exige un mod (CUI) | `--preview` con bloques falsos que solo ve quien edita y `confirm`; confirmación obligatoria sobre 20 000 bloques; contorno con partículas sin mod | 025 |
| Historial en memoria (15 pasos), se pierde al reiniciar; deshacer pisa lo que otro construyó después | Historial en disco por jugador (sobrevive a reinicios y caídas), deshacer seguro que salta lo cambiado después y lo cuenta; moderadores deshacen a otros | 025 |
| Sintaxis críptica (`%`, `#`, `^`, `=`), errores que no dicen qué corregir | `70% stone, 30% andesite`, `*_wool, !red_wool`, `not`, `with`; errores antes de tocar nada con «did you mean»; autocompletado en medio de una lista; presets | 025 |
| Sin topes de fábrica (`maxChangeLimit: -1`) | Grupos por permiso con tope bajo de fábrica, obras a la vez, mundos y bloques prohibidos (bloques de comandos, roca madre, portales) | 025 |
| Dos obras que se cruzan dejan bloques fantasma (FAWE) | Una obra que se cruza con otra más vieja espera | 025 |
| Cualquier hacha de madera es varita | Solo la varita marcada que da `/bwr wand` | 025 |
| Esquemas, pinceles, formas, WorldGuard, rollback por zona | Pendiente (abajo) | — |

## Siguientes specs (propuestas, en orden)
Elegidas por el dueño tras la [investigación de plugins exitosos](../investigacion/plugins-exitosos.md) (§6; todas
menos el editor visual), una por sesión. El número de cada una se asigna al abrirla:
1. ~~**OmniEvents: herramientas de autor** (P1, P2, P4)~~: hecha en la [006](specs/006-omnievents-herramientas-de-autor/spec.md).
2. ~~**OmniEvents: esquema JSON y referencia generada** (P3)~~: hecha en la [007](specs/007-omnievents-esquema-y-referencia/spec.md). Las specs siguientes documentan su sintaxis nueva al registrarla (descripción en `Triggers`, `Actions`, `Functions` o `RuleOptions`) y regeneran con `./gradlew :omnievents:schema`.
3. ~~**OmniEvents: disparador de estado** (P6)~~: hecha en la [008](specs/008-omnievents-disparador-de-estado/spec.md).
4. ~~**OmniEvents: variables con caducidad y en bloques, entidades e ítems** (P5)~~: hecha en la [009](specs/009-omnievents-variables-caducidad-y-objetos/spec.md).
5. ~~**OmniEvents: pruebas de reglas en el YAML** `tests:` y `/omni test` (P7)~~: hecha en la [010](specs/010-omnievents-pruebas-de-reglas/spec.md).
6. ~~**OmniEvents: paquetes, plantillas y versión** (P8)~~: hecha en la [011](specs/011-omnievents-paquetes-plantillas-version/spec.md).
7. ~~**OmniEvents: integraciones con costos**: Vault, `cost:` todo o nada (P9), WorldGuard y Citizens~~: hecha en la [012](specs/012-omnievents-integraciones-con-costos/spec.md).
8. ~~**OmniEvents: menús de inventario y formularios de Bedrock** (P10)~~: hecha en la [013](specs/013-omnievents-menus-y-formularios-bedrock/spec.md).

Otras pendientes:
- **Fábrica: servidor simulado** — MockBukkit para probar en unitarias lo que hoy solo se ve en el servidor real (F-04, F-05 de la 001).
  Con la suite en el juego (002) es menos urgente: queda para la persistencia y los casos de bordes.
- **Fábrica: más escenarios en el juego** — las acciones y disparadores sin escenario (riesgos de la 002) y
  PlaceholderAPI con su jar en el servidor de prueba.
- **OmniEvents: más integraciones** — lo que la 012 dejó fuera: acciones sobre NPC (`npc_say`, `npc_teleport`) y regiones (banderas), bancos de Vault.
- ~~**OmniEvents: paridad con ConditionalEvents, segunda parte**~~: hecha en la [015](specs/015-omnievents-paridad-conditionalevents-2/spec.md) (Mending como `item_mend`: CE no lo tiene).
- ~~**OmniEvents: paridad con ConditionalEvents, tercera parte** (detalles y ProtocolLib)~~: hecha en la [016](specs/016-omnievents-paridad-conditionalevents-3/spec.md). Queda fuera de CE: `player_command_as_op`, DiscordSRV, `datavalue:`, `custom_model_component_data_*`, `vector` y unas pocas variables (`%player_attack_cooldown%`, `%format_remove_…%`, `%item_meta%`…).
- **OmniEvents: menús dinámicos** — lo que la 013 dejó fuera: paginación, ítems generados de una lista, refresco
  periódico y formularios de Bedrock con campos o de sí/no.
- **OmniEvents: referencia en inglés generada** — lo que la 017 dejó fuera: traducir las ~330 descripciones del registro y generar la referencia en inglés en `docs/site/`, con una prueba que falle si falta una traducción; y páginas propias para estados, menús, paquetes, integraciones, `tests:` y la API.
- **OmniEvents: instalar ejemplos nuevos en un servidor existente** — lo que la 019 dejó fuera: el jar solo copia los
  ejemplos si `rules/` no existe, así que un paquete nuevo (como `examples-en`) no llega a quien actualiza; hoy se copia a
  mano. Por ejemplo, `/omni examples install <paquete>` o copiar los paquetes de ejemplo que falten, sin pisar nada.
- **OmniEvents: editor en el juego** — inventario para ver, activar y probar reglas sin editar YAML.
- **OmniEvents: almacenamiento SQL** — MySQL/MariaDB para redes con varios servidores (variables compartidas).
- **SkinStatues: armadura y objetos en las manos** (pedido del dueño, fuera de la 021): diseños propios por material y,
  si el dueño pone un resource pack suyo en la carpeta del plugin, sus texturas (no se redistribuyen).
- **SkinStatues: lluvia de ideas** (spec 022 H4; el dueño elige de aquí las próximas). Costo aproximado: bajo (horas),
  medio (una spec normal), alto (varias specs o investigación).

  | Idea | Qué es | Costo |
  |---|---|---|
  | Vista previa | `/statue preview …` dibuja el contorno con partículas o *block displays* unos segundos; `/statue confirm` la construye | Medio |
  | Pedestal | `--pedestal <bloque>`: una base rellena bajo los pies (tapa el hueco de `offset_up`), opcionalmente con una placa con el nombre | Bajo |
  | Nombre flotante | Un *text display* con el nombre del jugador sobre o frente a la estatua; `remove`/`undo` lo quitan | Bajo |
  | La skin del que miras | `/statue build @looking 32`: el jugador al que apunta la mira (y `@online` para todos en fila) | Bajo |
  | Filas y galerías | `/statue row Ana,Beto,Caro 32 --spacing 4`: varias estatuas alineadas, deshechas juntas | Medio |
  | 8 direcciones y giro libre | `--facing north_east` o `--yaw 30`: el voxelizador ya acepta cualquier giro; falta probar la cáscara y el autocompletado | Bajo–medio |
  | Rellenas | `--solid`: macizas por dentro (más bloques, más resistentes a explosiones) | Bajo |
  | Tramado de colores | *Dithering* (Floyd–Steinberg sobre la textura) para degradados con paletas chicas (solo lana) | Medio |
  | Armadura y objetos en las manos | Ver arriba | Alto |
  | Pose desde un soporte de armadura | `/statue pose save <nombre>` mirando un *armor stand* posado: guarda sus ángulos en `poses.yml` (el dueño prefirió los números en la 022) | Medio |
  | Palabras en vez de grados | `right_arm: up` / `forward` / `side` como atajos de los ángulos | Bajo |
  | Editor de poses en el juego | Menú o comandos (`/statue pose set <p> right_arm pitch 45`) con vista previa al cambiar | Alto |
  | Mover, girar o cambiar de pose | `/statue move <id> ~ ~ ~5`, `/statue repose <id> <pose>`: quitar y volver a construir guardando el terreno | Medio |
  | Escenas | `scenes.yml` con varias estatuas y poses a la vez («podio de 3», «apretón de manos») | Medio |
  | Límites por permiso y costo | Alto máximo por `skinstatues.height.<n>`, cuántas estatuas por jugador, costo con Vault | Medio |
  | Respetar regiones | Con WorldGuard (opcional), no construir donde quien la pide no puede construir | Medio |
  | Estatuas que brillan | Bloques de luz dentro del hueco (o `--glow`) para que se vean de noche | Bajo |
  | Exportar a esquema | `/statue export <id>`: un `.schem` de WorldEdit para pegarla en otro servidor | Medio |
  | Integración con OmniEvents | Una acción `statue_build` para reglas (la estatua del ganador de un minijuego, del jugador del mes) | Medio |
  | Escala libre | `--scale 2` (bloques por píxel) en vez de alto | Bajo |
  | Otras figuras | Modelos de criaturas (zombie, aldeano) con su textura del resource pack del dueño | Alto |
  | Captura en el juego para el README | La infraestructura de la 004 es de la galería de OmniEvents; hoy el README usa la hoja de la vista previa | Bajo |
- **Blockwright: lo que dejó fuera la 025** (el dueño elige el orden; costo aproximado bajo/medio/alto):

  | Idea | Qué es | Costo |
  |---|---|---|
  | Esquemas `.schem` | Importar y exportar el formato abierto Sponge v3 (el de WorldEdit), carpeta `schematics/`, `/bwr schem save|load|list`, pegar con vista previa | Medio |
  | Respetar regiones | WorldGuard (y otros) opcional: la escritura ya pasa por una sola puerta (`EditWorld.set`); sin permiso de construir ahí, se salta y se cuenta | Medio |
  | Formas | `sphere`, `cyl`, `pyramid`, huecas o llenas, con el mismo motor | Bajo–medio |
  | Pinceles | Varita de pincel: esfera, suavizar, pintar, con tamaño y patrón; vista previa siguiendo la mira | Alto |
  | Rollback por zona y tiempo | Como CoreProtect `u: t: r:` sobre el historial en disco, con vista previa | Medio |
  | `stack`, `move`, `smooth`, `naturalize` | Las otras operaciones comunes | Bajo–medio |
  | Gizmos | Mover y girar lo pegado con *block displays* antes de confirmar (como Axiom, sin mod) | Alto |
  | Selecciones no cuboides | Polígono, esfera, cilindro | Medio |
  | Entidades y más datos de bloque | Copiar soportes, marcos, cabezas, estandartes, generadores | Medio–alto |
  | Página de presentación y publicación | Como la 020 y la 024 | Medio |
  | Folia | Motor por región | Alto |
- Después: el siguiente plugin de la fábrica, reutilizando el motor de expresiones de OmniEvents como biblioteca
  común (`libs/`), con su propio ADR.

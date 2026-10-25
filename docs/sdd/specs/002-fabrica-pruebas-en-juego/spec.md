# Spec 002 — Fábrica: pruebas automáticas en el juego

**Estado:** Implementado y verificado · **Plugin:** fábrica (lo usa OmniEvents) · **Depende de:** 001 ·
**Origen:** pedido del dueño (2026-10-24): automatizar la compuerta 3 «sin que se conecte una persona, como hace
Playwright en ERP AI»

## Problema
La compuerta 3 de VERIFY (revisión en el juego) necesita hoy a una persona conectada al servidor de prueba. Por eso
la spec 001 quedó con criterios sin probar: las acciones sobre el jugador y el mundo, los eventos que solo produce
un jugador (entrar, chatear, romper, golpear, usar un comando) y las variables, enfriamientos y límites por jugador.
Cada cambio futuro en OmniEvents (y en los siguientes plugins) volvería a necesitar a esa persona, y lo que no se
repite en cada cambio se rompe sin que nadie lo vea.

En ERP AI, Playwright maneja un navegador real contra el sistema real, los datos de apoyo se preparan por API y la
prueba solo ejercita por la interfaz lo que se quiere comprobar. Aquí el equivalente es un **cliente de Minecraft
automático (bot)** contra un **servidor Paper real**, con la preparación por **consola**.

## Historias

**H1 — Un comando corre toda la revisión en el juego.** Como *desarrollador de la fábrica*, quiero un único comando
que arranque el servidor de prueba, conecte jugadores automáticos, corra los escenarios y apague, para repetir la
compuerta 3 en cada cambio sin una persona.
- CA1: `npm --prefix tests-e2e test` compila el plugin (salvo que se pida no hacerlo), lo instala en el servidor de
  prueba, arranca Paper, corre todos los escenarios y apaga el servidor aunque un escenario falle.
- CA2: El resultado es un reporte por escenario (nombre con el ID del criterio que cubre, p. ej. `H5 CA1`), con
  código de salida distinto de cero si alguno falla, y el log del servidor queda guardado para revisarlo.
- CA3: La suite parte siempre del mismo estado: borra los datos de los jugadores de prueba (inventario, variables,
  enfriamientos y usos) antes de arrancar; dos corridas seguidas dan el mismo resultado.
- CA4: Al terminar, el servidor de prueba queda como estaba: las reglas propias de la suite se retiran.
- CA5: La suite falla si el log del servidor tiene excepciones o errores del plugin, aunque los escenarios pasen.

**H2 — Seguro por diseño.** Como *dueño*, quiero que el servidor de prueba no se pueda usar desde fuera.
- CA1: La suite se niega a arrancar si el EULA no fue aceptado por el dueño en `run/eula.txt`.
- CA2: Antes de arrancar fuerza `server-ip=127.0.0.1` y `online-mode=false` en el servidor de prueba (los bots no
  tienen cuenta); nunca toca otro servidor ni otra carpeta que `plugins/<plugin>/run/`.
- CA3: Sin secretos: los bots no usan cuentas de Microsoft ni contraseñas.

**H3 — Preparar por consola, probar por el jugador.** Como *desarrollador*, quiero preparar el estado por consola
(`op`, `give`, `setblock`, `gamemode`, `/omni var set`) y ejercitar por el bot solo lo que se prueba (chatear,
romper un bloque, golpear, escribir un comando), para que cada escenario sea corto y no dependa de otros.
- CA1: Una ayuda manda un comando de consola y espera su respuesta en el log.
- CA2: Ayudas del bot: esperar un mensaje de chat, una barra de acción, un título o un ítem del inventario que cumpla
  un patrón, con tiempo límite y un error que diga qué se esperaba y qué llegó.
- CA3: Las reglas que necesita la suite viven en `tests-e2e/` como YAML de OmniEvents (configuración, no código) y
  se copian al servidor de prueba al arrancar.

**H4 — Cubrir lo pendiente de la spec 001.** Como *dueño*, quiero que la suite cubra los criterios de la 001 que la
compuerta 2 no podía probar, con un escenario por criterio:
- CA1: H5 CA1 mensajes al jugador (`message`, `broadcast`, `actionbar`, `title`, `bossbar`, `sound`,
  `console_message`, `log`).
- CA2: H5 CA2 comandos (`console`, `command`, `chat`).
- CA3: H5 CA3 acciones de jugador (`teleport`, `heal`, `damage`, `feed`, `gamemode`, `effect`, `remove_effect`,
  `give_item`, `remove_item`, `clear_inventory`, `give_exp`, `give_level`, `fly`, `kick`).
- CA4: H5 CA4 mundo (`set_block`, `summon`, `drop_item`, `explosion` sin dañar bloques, `lightning`, `particle`).
- CA5: H5 CA5 evento (`cancel`, `set_damage`, `set_message`, `set_join_message`, `set_quit_message`).
- CA6: H6 CA1 eventos de jugador: `player_join` (ejemplo `bienvenida`), `player_chat`, `block_break`,
  `player_attack`, `player_quit` y comandos de reglas usados por un jugador (`monedas`, `spawnvip`).
- CA7: H7 variables por jugador (`pvar`) asignadas por reglas y por `/omni var set`, transferidas entre dos jugadores.
- CA8: H8 enfriamiento por jugador (`on_cooldown` con el tiempo restante) y límite diario (`limit: 1`,
  `limit_reset: daily`).
- CA9: Filtro de chat asíncrono: el mensaje filtrado se cancela (nadie lo ve), el autor recibe el aviso y las
  acciones de mundo de esa regla corren en el hilo principal sin errores.
- CA10: Escape de MiniMessage: un jugador que escribe `<red>`, `<click:…>` o `&c` en el chat ve su texto literal
  cuando una regla lo repite con `{message}`, y no ejecuta nada. (`§` no se puede escribir en el chat: el cliente y
  el servidor lo rechazan; su escape lo cubren las unitarias de la 001.)
- CA11: `delay` pausa solo esa ejecución: la acción posterior llega después del tiempo indicado y el servidor sigue
  respondiendo mientras tanto.

**H5 — Versión actual de Paper (opcional).** Como *dueño*, quiero saber si el plugin compilado contra 1.21.4 funciona
en la versión más reciente de Paper que soporta el bot.
- CA1: La suite acepta la versión de Paper por parámetro y usa otro servidor de prueba (`run-<versión>/`), con el jar
  bajado de la API Fill y su sha256 verificado; la versión del bot y el Java del servidor también son parámetros.

## Fuera de alcance
- Pruebas visuales (capturas de pantalla, partículas, sonidos): se comprueba que la acción corre sin error en el log y,
  donde el protocolo lo permite (sonido, partícula), que el paquete llega al bot.
- La revisión exploratoria (compuerta 3b) sigue siendo humana o de un agente que maneja el bot; esta spec automatiza
  la parte repetible (3a).
- PlaceholderAPI (H7 CA3 de la 001) y la API para otros plugins (H12): necesitan otros plugins en el servidor.

## Decisiones (CLARIFY)
| # | Pregunta | Decisión |
|---|---|---|
| 1 | Herramienta del bot | `mineflayer` 4.39.x (Node 22): soporta 1.21.4 y 26.1 (pedido del dueño) |
| 2 | Corredor de pruebas | Corredor propio para los escenarios (un servidor compartido, orden fijo, revisión del log por escenario, filtro por ID); `node:test` para las unitarias del arnés. Sin dependencias extra |
| 3 | Dónde vive | `tests-e2e/` en la raíz: servirá para los siguientes plugins de la fábrica |
| 4 | Estado inicial | Se borran los datos de los bots (jugador, variables) en cada corrida; el mundo se reutiliza |
| 5 | ¿Reglas de prueba o ejemplos? | Las dos: los ejemplos del plugin se prueban tal cual (`bienvenida`, `monedas`, `filtro_palabras`…) y las acciones que no usan los ejemplos se ejercitan con reglas propias de la suite |

## Preguntas abiertas
- Ninguna bloqueante.

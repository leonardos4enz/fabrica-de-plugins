# Verificación 002 — Fábrica: pruebas automáticas en el juego

**Fecha:** 2026-10-24 · **Resultado:** Aprobada

| Compuerta | Resultado |
|---|---|
| Unitarias | Arnés: **4/4** (`node --test lib/*.test.mjs`). OmniEvents: `gradlew build` en verde (38/38) con los arreglos F-01 y F-02 |
| Servidor real | Compuerta 2 de OmniEvents repetida tras los arreglos: carga 0/0, `validate` 0/0, `selftest` 12/12, `reload` ×2 sin fugas (9 oyentes, 3 temporizadores, 2 comandos), sin errores |
| En el juego (3a, esta spec) | `npm --prefix tests-e2e test`: **21/21 dos veces seguidas** en Paper 1.21.4 build 232 (≈46 s cada una, compilando), log sin errores; **21/21 dos veces** en Paper 26.1.2 build 74 (Java 25, bot 26.1) |
| Exploratoria | Las corridas con fallos durante el desarrollo de la suite sirvieron de revisión: cinco hallazgos, abajo |

## Cobertura
| Criterio | Prueba |
|---|---|
| H1 CA1 un comando | `npm --prefix tests-e2e test`: compila (`gradlew :omnievents:dist`), instala, arranca, corre y apaga |
| H1 CA2 reporte | `✔/✘ <ID> — <nombre> (ms)`, resumen, código de salida 1 si algo falla; `run/e2e.log` |
| H1 CA3 estado inicial | `servidor.test.mjs` «olvidar jugadores…»; dos corridas seguidas con el mismo resultado (primera vez de Ana en ambas) |
| H1 CA4 deja el servidor como estaba | Tras la corrida, `rules/` solo tiene `ejemplos` e `importado`; `ops.json` vacío |
| H1 CA5 errores del log | Revisión del log por escenario y al final; detectó F-01 como error (`erroresDelPlugin`) |
| H2 CA1 EULA | `servidor.test.mjs` «H2 CA1…» |
| H2 CA2 solo localhost | `servidor.test.mjs` «H2 CA2…»; `server.properties` de `run/` con `server-ip=127.0.0.1` |
| H2 CA3 sin secretos | Bots `auth: 'offline'`; revisión del diff |
| H3 CA1–CA2 ayudas | `Servidor.consola/esperarLog/sincronizar`, `Jugador.esperar/nunca/hasta` (usadas por todos los escenarios) |
| H3 CA3 reglas como configuración | `tests-e2e/omnievents/reglas/e2e.yml` |
| H4 CA1 (001 H5 CA1) mensajes | `H5 CA1 — mensajes…`; `H6 CA1 · H5 CA1… player_join` (título del ejemplo) |
| H4 CA2 (001 H5 CA2) comandos | `H5 CA2 — comandos…` |
| H4 CA3 (001 H5 CA3) jugador | Cuatro escenarios `H5 CA3` (salud/hambre; teleport/gamemode/fly; efectos; ítems/experiencia) y `H5 CA3 — kick` |
| H4 CA4 (001 H5 CA4) mundo | `H5 CA4 — mundo…`, `H5 CA4 — explosion no rompe bloques…` |
| H4 CA5 (001 H5 CA5) evento | `cancel` (bloque_protegido), `set_damage` (vaca), `set_message`, `set_join_message` (bienvenida), `set_quit_message` (despedida) |
| H4 CA6 (001 H6 CA1) eventos de jugador | `player_join` ×2, `player_chat` ×4, `block_break` ×2, `player_attack`, `player_quit`, comandos `monedas` y `spawnvip` |
| H4 CA7 (001 H7) pvar | `diamantes_contados`, `filtro_palabras` (`avisos`), `monedas` con `/omni var set` y `call` entre dos jugadores |
| H4 CA8 (001 H8) enfriamiento y límite | `spawnvip` (`on_cooldown` con segundos restantes), `recompensa_diaria` (una sola vez al volver a entrar) |
| H4 CA9 chat asíncrono | `H4 CA9 — filtro de chat asíncrono…`: nadie ve el mensaje, aviso al autor, `kick` por consola desde el chat sin errores |
| H4 CA10 escape | `H4 CA10 — escape de MiniMessage…` (mensaje y barra) y `menciones` (barra de otro jugador) |
| H4 CA11 delay | `H4 CA11 — delay en spawnvip…`: llega a los ≥2.8 s y la consola responde durante la espera |
| H5 CA1 otra versión | `--paper 26.1.2 --version-bot 26.1 --java-servidor <JDK 25>`; sha256 verificado `1d70b1da…95e5f7` |

## Hallazgos
| ID | Severidad | Descripción | Evidencia | Prueba de regresión | Estado |
|---|---|---|---|---|---|
| F-01 | Media | **OmniEvents.** El ejemplo `filtro_palabras` expulsa por consola y después hace `set: pvar.avisos = 0`; esa asignación fallaba con «el jugador ya no está conectado», así que los avisos nunca se reiniciaban y el jugador quedaba expulsado al primer insulto de cada sesión siguiente | Corridas 1 y 2: `Ana_e2e.avisos = 3` (y `nulo`) y el aviso `Error en regla 'filtro_palabras' … (set)` en el log | Escenario `H4 CA9 · H7 CA1 — filtro de chat asíncrono…` (exige `avisos = 0` y log sin errores; falló con el código anterior en las corridas 1 y 2). Arreglo: `ExecContext.setVar` ya no exige que el jugador siga conectado; sus datos siguen cargados hasta el tick siguiente a la salida, que los guarda | Resuelto |
| F-02 | Media | **OmniEvents.** `remove_item: DIAMOND;2` no quitaba ítems con nombre o lore (los que da el propio plugin con `give_item`): usaba `removeItem` de Bukkit, que solo quita ítems idénticos | Corrida 2: «no se cumplió 1 diamante tras remove_item DIAMOND;2» con 3 diamantes «Diamante e2e» | Escenario `H5 CA3 — jugador: give_item…, remove_item…` (falló con el código anterior en la corrida 2). Arreglo: quitar por material en el inventario y la mano secundaria | Resuelto |
| F-03 | Baja | **Suite.** La consola atiende los comandos en orden pero sin esperar: la preparación (`clear`, `kill`, `fill`) se ejecutaba después de la acción del bot y borraba su resultado | Corrida 1: «Removed 5 item(s)» después de `/e2e items` | `Servidor.sincronizar()` (espera la respuesta de `seed`) al final de cada preparación | Resuelto |
| F-04 | Baja | **Suite.** mineflayer 4.39 no lee la barra de acción de Paper (paquete `set_action_bar_text`), muestra el texto firmado en vez del cambiado por `set_message` y nombra mal los sonidos de 1.21.4 | Corrida 1: barras vacías; `[megáfono]` no llegaba; `experience_orb` llegaba como `experience_bottle.throw` | `lib/bot.mjs` lee los paquetes directamente; los sonidos se reconocen por su tono | Resuelto |
| F-05 | Media | **Suite.** En un servidor nuevo los ejemplos de OmniEvents no existían (el plugin solo los crea si `rules/` no existe y la suite la creaba antes) y, en uno usado, podían quedar viejos respecto al código. En 26.1 los datos del jugador viven en `world/players/`, así que «primera vez» fallaba | Corrida en 26.1.2: `/monedas` desconocido; luego «¡Hola de nuevo…!» en una primera vez | `suite.copiar` toma los ejemplos del código fuente en cada corrida; `olvidarJugadores` y su unitaria cubren el formato de 26.1 | Resuelto |

## Riesgos residuales
- Partículas, sonidos y relámpagos se comprueban por el paquete que recibe el bot, no por cómo se ven o suenan.
- Si una regla asigna `pvar` a un jugador que salió **después** de que sus datos se guardaron (p. ej. tras un `delay`),
  se vuelven a cargar del disco; si coincidiera con la escritura de la salida, podría leer un archivo viejo.
- `fire`, `velocity`, `launch`, `close_inventory`, `send_to_server`, `set_death_message`, `keep_inventory`,
  `cancel_drops` y `deny_join` no tienen escenario todavía; los 45 disparadores tampoco (se cubren los de jugador más
  usados).
- PlaceholderAPI (001 H7 CA3) y la API para otros plugins (001 H12) siguen sin probar: necesitan otros plugins.
- Sin integración continua: la suite necesita el EULA del dueño y un JDK local; se corre a mano antes de cada commit.

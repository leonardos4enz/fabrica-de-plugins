# Spec 018 — OmniEvents: minijuegos con la configuración actual (parkour de ejemplo)

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 001, 008, 010, 011 ·
**Origen:** pedido del dueño (2026-10-27): «configura un minijuego completo de un jugador con el plugin en el server de
pruebas». En CLARIFY lo precisó: «quiero un minijuego que alguien que vaya a descargar el plugin pueda hacer» y «lo que
quiero es que puedan hacer minijuegos con la config actual, no con nuevas». La 003 sigue reservada; esta va como 018.

## Problema
Quien descarga OmniEvents ve ejemplos sueltos (una bienvenida, un comando, un temporizador), pero nada le muestra que
con esas mismas piezas se arma un **juego entero**: empezar, reglas, ganar y perder, salir, récord y qué pasa si el
jugador se desconecta. Sin un ejemplo completo y una guía, el dueño de un servidor no sabe por dónde empezar y piensa
que necesita otro plugin de minijuegos.

## Historias
**H1 — Un parkour que el dueño arma a su gusto, sin coordenadas.** Como *dueño de un servidor*, quiero un minijuego
de ejemplo que funcione en cualquier mapa que yo construya, para tenerlo en minutos y adaptarlo.
- CA1: El paquete `parkour` viene con los ejemplos del jar (`rules/ejemplos/parkour/`) **desactivado**
  (`enabled: false` en `pack.yml`): no cambia nada en un servidor hasta que el dueño lo activa (`enabled: true` o
  `/omni pack enable parkour`).
- CA2: El recorrido se marca con bloques, no con coordenadas: una placa de inicio, placas de checkpoint y una placa de
  meta, cuyos materiales son parámetros de `pack.yml` (`inicio`, `checkpoint`, `meta`). Cualquier recorrido que use
  esas placas es un parkour.
- CA3: `/parkour construir` (solo OP o `omnievents.parkour.admin`) arma delante del jugador un recorrido de prueba con
  inicio, saltos, un checkpoint y la meta, para probarlo al instante.
- CA4: Todo se ajusta con parámetros de `pack.yml`: tiempo límite, cuántos bloques de caída devuelven al checkpoint y
  los materiales. Ninguna regla tiene un número del juego escrito a mano.

**H2 — Una partida completa.** Como *jugador*, quiero entender en todo momento qué pasa.
- CA1: **Empezar:** pisar la placa de inicio en supervivencia o aventura empieza la partida (título, sonido, mensaje
  con las reglas); volver a pisarla la reinicia. En creativo, espectador o volando no empieza y dice por qué.
- CA2: **Durante:** la barra de acción muestra el tiempo, el checkpoint y las caídas cada segundo.
- CA3: **Checkpoint:** pisar una placa de checkpoint guarda ese punto (una sola vez por placa en la misma partida) y lo
  dice con el tiempo parcial.
- CA4: **Caída:** caer más de `caida` bloques por debajo del último punto devuelve al jugador a él y suma una caída.
- CA5: **Ganar:** pisar la meta termina la partida: tiempo final, caídas, título, fuegos artificiales y, si es su mejor
  tiempo, «nuevo récord personal»; si es el mejor del servidor, se anuncia a todos.
- CA6: **Perder:** pasar el tiempo límite, morir, cambiar de modo de juego, ponerse a volar o teletransportarse por
  otro medio (comando, perla) termina la partida sin récord y dice el motivo.
- CA7: **Salir y reiniciar:** `/parkour salir` abandona la partida; `/parkour reiniciar` vuelve a la placa de inicio y
  empieza de cero; `/parkour checkpoint` vuelve al último punto (cuenta como caída).

**H3 — Récord y marcador persistentes.** Como *jugador*, quiero ver mi mejor tiempo y el del servidor.
- CA1: El mejor tiempo personal, las partidas jugadas y las ganadas se guardan por jugador y sobreviven a un reinicio.
- CA2: El récord del servidor (tiempo y nombre) se guarda globalmente; `/parkour record` muestra los dos.
- CA3: Los valores se pueden mostrar en un marcador de otro plugin con PlaceholderAPI (`%omni_var_…%`, `%omni_gvar_…%`),
  y la guía lo dice.

**H4 — No se rompe.** Como *dueño*, quiero que una desconexión o un reinicio no deje partidas a medias.
- CA1: Si el jugador se desconecta en plena partida, la partida se cancela (sin récord) y al volver no queda nada a medias.
- CA2: Si el servidor se cae con una partida empezada, al entrar el jugador la partida vieja se limpia y se le avisa.
- CA3: Fuera de una partida, pisar un checkpoint o la meta no hace nada (salvo un aviso de cómo empezar).

**H5 — Probado y documentado.** Como *dueño*, quiero confiar en el ejemplo y aprender a hacer el mío.
- CA1: Las reglas del paquete traen `tests:` (gradle las corre con los demás ejemplos).
- CA2: Un escenario de la suite (`tests-e2e/omnievents/`) juega una partida de punta a punta con un bot: empezar,
  checkpoint, caída, ganar con récord, perder por tiempo, salir y desconexión.
- CA3: Una guía «Cómo hacer un minijuego» en el README del plugin y una página en inglés en `docs/site/` explican el
  patrón (estado en `pvar`, una regla por evento, `call` para lo común, temporizador por jugador, limpieza al salir) y lo
  que **no** se puede hacer con la configuración actual, con la forma de resolverlo.

## Fuera de alcance
- Capacidades nuevas (decisión 2). Si para el juego faltara algo, se documenta como límite y se resuelve con lo que hay.
  Un bug que aparezca sí se corrige, como bug y con su prueba.
- Juegos de varios jugadores con fases, equipos o arenas; guardar y devolver el inventario; ranking de los N mejores;
  reiniciar el mapa. Van a la guía como límites, con la forma de resolverlo hoy.
- Instalar el juego en `run-demo` (decisión 3).

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Qué juego? (tesoro, parkour, oleadas; propuso Tetris, un lienzo tipo wplace y ajedrez) | «quiero un minijuego que alguien que vaya a descargar el plugin pueda hacer»; luego «sin preferencia» entre tesoro, ajedrez, lienzo y parkour. Se eligió el **parkour**: el más pedido en servidores y el único que el dueño arma en su propio mapa sin coordenadas (las placas marcan el recorrido). Tetris no: es la copia de un juego concreto. |
| 2 | ¿Capacidades nuevas para minijuegos (partidas, inventario, marcador, ranking)? | «lo que quiero es que puedan hacer minijuegos con la config actual, no con nuevas». **Ninguna capacidad nueva**: la spec demuestra y documenta lo que ya hay. |
| 3 | ¿Dónde se instala? | Pedido de la sesión que abrió esta: el servidor de la suite. Sin permiso explícito no se toca `run-demo`; el dueño no lo dio. Se prueba en la suite en 1.21.4 y 26.1.2 y va como ejemplo en el jar para cualquier servidor. |
| 4 | ¿El mapa? | Lo construye el dueño con las placas; `/parkour construir` arma uno de prueba con `set_block` relativo al jugador (vive en el repositorio). |

## Preguntas abiertas
- (ninguna)

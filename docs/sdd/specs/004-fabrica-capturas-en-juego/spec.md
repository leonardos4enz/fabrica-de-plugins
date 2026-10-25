# Spec 004 — Fábrica: documentación con imágenes generadas en el juego

**Estado:** Implementado y verificado · **Plugin:** fábrica (la usa OmniEvents) · **Depende de:** 001, 002 ·
**Origen:** pedido del dueño (2026-10-24): «cada disparador, acción, comando y ejemplo con una imagen de cómo se ve en
el juego»

## Problema
El README de OmniEvents describe más de cien capacidades solo con texto. Un dueño de servidor que no conoce el plugin
no sabe cómo se ve un `title`, una `bossbar` o la salida de `/omni debug` hasta instalarlo y probarlo. Las capturas
hechas a mano envejecen: cada versión cambia algún texto y nadie las rehace.

La suite en el juego (spec 002) ya hace que un jugador automático (bot) viva cada capacidad contra un Paper real y
registra todo lo que recibe. Esa misma corrida puede dejar una imagen de lo que vio el bot, generada de nuevo en cada
versión.

## Historias

**H1 — Un comando genera todas las imágenes.** Como *desarrollador de la fábrica*, quiero un comando que arranque el
servidor de prueba, haga que un bot viva cada capacidad del plugin y guarde una imagen por capacidad, para que la
documentación nunca quede vieja.
- CA1: `npm --prefix tests-e2e run capturas` compila el plugin (salvo `--sin-build`), lo instala en el servidor de
  prueba con las reglas de demostración, arranca Paper, recorre las escenas y apaga aunque una escena falle.
- CA2: Las imágenes van a `docs/img/<plugin>/` con un nombre estable por capacidad (`accion-title.png`), en PNG y
  recortadas a la zona que importa, para cuidar el tamaño del repositorio.
- CA3: El servidor de prueba queda como estaba: se retiran las reglas de demostración, el OP de los bots y el
  escenario construido.
- CA4: `--solo <patrón>` regenera solo algunas escenas.

**H2 — Lo que se ve es lo que recibió un cliente real.** Como *dueño de servidor que lee el README*, quiero que cada
imagen muestre lo que de verdad manda el plugin, no un dibujo inventado.
- CA1: Chat, barra de acción, títulos, bossbars, HUD del jugador (vida, comida, experiencia, hotbar, efectos),
  inventarios, pantallas de expulsión y salidas de `/omni` se dibujan a partir de los componentes de texto y el estado
  que recibió el bot, con la fuente, los sprites y el idioma del cliente de Minecraft instalado.
- CA2: El mundo (bloques y criaturas) es una vista 3D desde los ojos del bot construida con los chunks y entidades
  que le mandó el servidor.
- CA3: Lo que la vista no puede mostrar con fidelidad (relámpagos, partículas, sonidos, ítems tirados) no se dibuja:
  la galería lo dice solo con texto y el motivo. Nunca una imagen inventada.
- CA4: Cada imagen de la galería dice que es un render de lo que recibió un cliente real del servidor, no una captura
  del cliente oficial.
- CA5: Los assets de Minecraft (fuente, sprites, texturas, idioma) se leen del cliente instalado del dueño y nunca
  se copian al repositorio; sin el cliente, el comando se niega y dice qué falta.

**H3 — Una imagen por capacidad.** Como *dueño*, quiero que la galería cubra todo lo que ofrece el plugin.
- CA1: Cada disparador que lista `/omni triggers`, cada acción que lista `/omni actions` y cada subcomando de `/omni`
  tiene una escena con imagen o una entrada de solo texto con su motivo.
- CA2: Cada regla de ejemplo del plugin (`src/main/resources/rules/ejemplos/`) tiene su imagen, ejercitada tal cual.
- CA3: El comando falla si el plugin tiene un disparador, acción o subcomando sin entrada en la galería (cobertura
  comprobada contra el servidor vivo, no contra una lista escrita a mano).
- CA4: La galería se genera sola (`docs/<plugin>/galeria.md`): por grupo, cada capacidad con su imagen, qué la produjo
  (la regla de demostración en YAML o el comando) y el texto alternativo.

**H4 — Reproducible.** Como *desarrollador*, quiero que regenerar las imágenes sin cambios en el plugin no cambie el
repositorio, para que un diff de imágenes signifique algo.
- CA1: Dos corridas seguidas dan las mismas imágenes; el comando compara cada imagen con la anterior y reporta
  `igual`, `cambió` o `nueva`.
- CA2: Las escenas que cambian por naturaleza (tiempos de `/omni stats`, criaturas con IA) se declaran como
  `varía` y no cuentan como diferencia inesperada; una imagen igual a la anterior no se reescribe.
- CA3: Las escenas dan el mismo resultado con o sin `--solo`: cada una prepara su propio estado.

**H5 — Documentación al día.** Como *dueño*, quiero ver las imágenes donde leo.
- CA1: El README del plugin muestra las imágenes principales (ejemplos, mensajes, diagnóstico) y cada tabla de
  capacidades enlaza a su sección de la galería.
- CA2: `CLAUDE.md` y `verificacion.md` piden regenerar las capturas al cerrar cada spec que cambie algo visible.

## Fuera de alcance
- Capturas del cliente oficial: necesitan la cuenta del dueño (credenciales que la fábrica no maneja), no se
  regeneran solas y nunca salen iguales dos veces. Un mod de cliente queda para una spec futura si hace falta.
- Animaciones (GIF/video) y sonido.
- Otras versiones de Paper: el visor 3D llega hasta 1.21.4; las capturas se generan en 1.21.4.
- PlaceholderAPI y la API para otros plugins (no tienen imagen propia).

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Dónde van las imágenes? | Galería aparte (`docs/omnievents/galeria.md`) con una imagen por capacidad; el README muestra las principales y enlaza a la galería |
| 2 | ¿Foto del cliente oficial o render? | Render automático desde lo que recibe el bot («solo el bot»), tras evaluar un mod de cliente y el modo demo con el cliente del dueño; se aprobó con un prototipo de 6 imágenes |
| 3 | Fuente y HUD | Los del cliente instalado (`.minecraft/versions/1.21.4/1.21.4.jar` y el índice de assets), leídos solo en la PC, como hace Minecraft Animator con sus texturas; idioma `es_mx` |
| 4 | Mundo en 3D | `prismarine-viewer` en Chrome sin interfaz (`puppeteer-core` con el Chrome instalado). Solo dibuja de y = 0 a 255: las escenas se montan en una plataforma en y = 100. Del Animator se toma la idea de leer los assets locales, no su motor |
| 5 | Disparadores difíciles (pesca, portal, encantar, paquete de recursos, pre-login, server_stop) | Se intentan montando la escena por consola; si un cliente real no puede mostrarlo de forma fiable, solo texto con el motivo |

## Preguntas abiertas
- Ninguna bloqueante.

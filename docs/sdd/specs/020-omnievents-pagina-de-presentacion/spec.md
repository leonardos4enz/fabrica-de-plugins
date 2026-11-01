# Spec 020 — OmniEvents: página de presentación con imágenes

**Estado:** Implementado y verificado · **Plugin:** OmniEvents (y la fábrica: fuentes HTML
de imágenes) · **Depende de:** 004, 017, 019 · **Origen:** pedido del dueño (2026-11-01) tras comparar la página de
OmniEvents en SpigotMC, Modrinth y Hangar (solo texto) con la de ConditionalEvents (casi todo imágenes).

## Problema
Quien llega a la página de OmniEvents en SpigotMC, Modrinth o Hangar ve un muro de texto. La de ConditionalEvents, el
plugin con el que se compara, entra por los ojos: logo, ejemplos con captura del juego, tarjetas de funciones y una
franja de «cómo empezar». OmniEvents hace más (expresiones completas, depuración con valores, pruebas en el YAML,
menús que se vuelven formularios de Bedrock, paquetes, autocompletado en VS Code, importador de ConditionalEvents),
pero la página no lo muestra. Y el logo actual (`{o}` sobre naranja) le parece aburrido al dueño.

## Historias
**H1 — Un logo nuevo.** Como *dueño*, quiero un logo más atractivo que se reconozca en chico (ícono de 64 px en las
listas de SpigotMC, Modrinth y Hangar) y en grande.
- CA1: Antes de hacerlo hay 2 o 3 direcciones de diseño en bocetos (PNG) y el dueño elige una.
- CA2: El logo sale de una fuente HTML/SVG versionada en `docs/site/assets-src/` y se exporta a PNG de 512 px (y se
  comprueba legible a 64 px).
- CA3: No usa logos, texturas ni marcas de otros plugins ni de Mojang.

**H2 — Una página que entra por los ojos y dice por qué es mejor.** Como *dueño de un servidor que compara plugins*,
quiero ver de un vistazo qué hace OmniEvents y qué tiene que ConditionalEvents no.
- CA1: Un banner nuevo, en el estilo elegido.
- CA2: Cuatro ejemplos en inglés con captura del juego: bienvenida, `/daily` con racha, región de WorldGuard y NPC de
  Citizens (ajustable según el diseño elegido).
- CA3: Una tarjeta «tu YAML → lo que pasa en el juego» y una de `/omni debug` con los valores en cada condición.
- CA4: Unas tres tarjetas de funciones: expresiones y control de flujo; integraciones; pruebas y paquetes.
- CA5: El mensaje nombra lo que ConditionalEvents no tiene (depuración con valores, `tests:`, menús como formularios
  de Bedrock, paquetes y plantillas, VS Code) y que `/omni import` trae una configuración de ConditionalEvents. Cada
  afirmación está respaldada por una spec verificada (tabla del [roadmap](../../roadmap.md)). Sin copiar su estilo,
  sus textos ni sus imágenes, y sin logos de otros plugins (sus nombres en texto, sí).
- CA6: Una franja «How to start» (instalar, activar el paquete de ejemplos, editar, `/omni reload`).

**H3 — Solo las imágenes necesarias.** Como *dueño*, no quiero repetir el costo de la galería de la 004.
- CA1: Cada imagen sale de una fuente versionada: HTML en `docs/site/assets-src/` o una escena en
  `tests-e2e/omnievents/capturas.mjs` generada con `--solo`. No se regenera la galería.
- CA2: Se reutiliza lo que sirva; las escenas nuevas son solo las que pide el diseño elegido, en inglés.
- CA3: Uno o dos GIF cortos, solo si el diseño elegido los pide, armados con `ffmpeg` a partir de cuadros.

**H4 — Las descripciones de las tres páginas.** Como *dueño*, quiero pegar la descripción nueva sin rehacerla.
- CA1: BBCode para SpigotMC y Markdown para Modrinth y Hangar, en el repositorio.
- CA2: Las imágenes se enlazan por su URL de GitHub (`raw.githubusercontent.com/.../main/...`), como el banner actual.
- CA3: La descripción incluye la transcripción en texto de lo que dicen las imágenes (Spoiler en SpigotMC; sección
  plegable o texto en Markdown), para buscadores y lectores de pantalla.
- CA4: Publicar o editar esas páginas lo hace el dueño, o la IA solo con su OK explícito.

## Fuera de alcance
- Publicar la 1.12.0 o editar las páginas de SpigotMC, Modrinth y Hangar sin el OK del dueño.
- Una imagen por capacidad, regenerar la galería de la 004 o traducirla al inglés.
- Capacidades nuevas del plugin.
- Cambiar el diseño del sitio de documentación (`docs/site/`) más allá de su logo y su banner.

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Una imagen por capacidad o un subconjunto? | Subconjunto (pedido del dueño, lección de la 004) |
| 2 | ¿Qué dirección de diseño (logo, banner y tarjetas)? | B, «Bloque» (bocetos en [bocetos/](bocetos/)), muy de Minecraft; armada como un póster por capas con decoradores propios (b2), tras revisar los de la página de ConditionalEvents |
| 3 | ¿Capturas de `/omni debug` en inglés? | No: los mensajes del plugin están en español; la tarjeta muestra el formato como texto |

## Preguntas abiertas
- (ninguna)

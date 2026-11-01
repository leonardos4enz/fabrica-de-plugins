# Spec 024 — SkinStatues: multimedia y página de presentación

**Estado:** Implementado y verificado; la publicación la hace el dueño · aprobada por el dueño (2026-11-01, CLARIFY) · **Plugin:** SkinStatues 0.3.0 (sin cambios de
código) y la fábrica (fuentes HTML de imágenes, escenas de capturas) · **Depende de:** 004, 020, 021, 022, 023 ·
**Origen:** pedido del dueño (2026-11-01): «¿puedes preparar la multimedia para publicar el plugin en Spigot, Modrinth y
la otra? Igual que con el plugin de OmniEvents, al estilo Minecraft […]. Quiero imágenes de youtubers favoritos de habla
hispana o inglesa, con estructuras de preferencia de 100 de altura en mundos reales generados para que se vea la
inmensidad de las estatuas, enfatizando que se puede de cualquier personaje».

## Problema
SkinStatues 0.3.0 está verificado pero no tiene página en SpigotMC, Modrinth ni Hangar, ni logo, ni imágenes del juego:
solo la hoja de poses de la vista previa (`docs/img/skinstatues/poses.png`). Lo que lo vende es visual (una estatua de
100 bloques en un paisaje, de cualquier skin, en cualquier pose) y hoy no se ve en ningún lado.

## Historias
**H1 — Logo e ícono.** Como *dueño*, quiero un logo de SkinStatues de la misma familia que el de OmniEvents (020).
- CA1: Sale de una fuente HTML/SVG versionada y se exporta a PNG de 512 px, legible a 64 px (ícono de las listas).
- CA2: Pixel art propio (una cabeza o figura de bloques); sin texturas, logos ni marcas de Mojang ni de otros plugins.

**H2 — Estatuas gigantes en un mundo real.** Como *dueño de un servidor que mira plugins*, quiero ver la escala.
- CA1: Una estatua de ~100 bloques de alto sobre un mundo de terreno normal generado (no plano), vista de lejos.
- CA2: Una fila de 4–5 estatuas con skins y poses distintas: «cualquier personaje, cualquier pose».
- CA3: Un GIF de una estatua construyéndose y una vista de cerca con la segunda capa en relieve.
- CA4: Una franja «by name or by link» (`/statue build <name>` y `/statue build https://…`) con las paletas.
- CA5: Las skins son de youtubers de habla hispana e inglesa elegidos por el dueño (decisión 1).

**H3 — Solo las imágenes necesarias (lección 004).**
- CA1: Cada imagen sale de una fuente versionada: HTML en `docs/site/assets-src/` o una escena en
  `tests-e2e/skinstatues/presentacion.mjs` generada con `--solo`.
- CA2: Las skins de los youtubers se bajan una vez a `tests-e2e/.cache/` y **no** se copian al repositorio.
- CA3: El servidor de capturas es una carpeta propia (`run-capturas/`, ignorada por git) con un mundo de terreno normal;
  el `run/` del dueño (su mundo y sus 7 estatuas) no se toca.

**H4 — Lo necesario para publicar en los tres sitios.** Como *dueño*, quiero crear los proyectos y pegar todo sin rehacerlo.
- CA1: BBCode para SpigotMC y Markdown para Modrinth y Hangar, con las imágenes por URL de `raw.githubusercontent.com`
  y la transcripción en texto (Spoiler / `<details>`).
- CA2: Resumen corto, etiquetas/categorías, versiones de Paper verificadas, changelog de la 0.3.0 y un paso a paso de
  creación de cada proyecto, en el repositorio.
- CA3: Cada afirmación está respaldada por una spec verificada (021–023).
- CA4: La página dice que el plugin no está afiliado a los creadores cuyas skins se muestran.
- CA5: Crear los proyectos y subir lo hace el dueño (decisión 4); la IA no publica nada.

## Fuera de alcance
- Capacidades nuevas del plugin (la fila de estatuas se arma con varios `/statue build`; `/statue row` sigue en el roadmap).
- Una imagen por pose o por capacidad (la hoja de poses de la 022 ya las muestra).
- Publicar o crear los proyectos en los sitios.

## Decisiones (CLARIFY, 2026-11-01)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Skins de youtubers reales en la página oficial? (se le advirtió que puede leerse como respaldo y que un creador puede pedir que se quite) | Sí, sin pedir permiso, asumiendo el riesgo; la página lleva «not affiliated». Elegidos: Technoblade, ElRichMC, LTeZla, Vegetta777, Willyrex, elrubius, Mikecrack, Dream, GeorgeNotFound, Sapnap, DanTDM, Quackity, Bobicraft (Quackity se cambió por DanTDM: su skin actual es Mickey Mouse, verify F-01) |
| 2 | ¿Qué escenas? | Las cuatro propuestas: gigante de 100 en paisaje, fila de poses, GIF construyéndose (y de cerca), nombre y link con paletas |
| 3 | ¿Estilo? | El póster por capas de la 020 (Press Start 2P, franjas de 1280 px), con logo propio |
| 4 | ¿Publicación? | La IA deja todo listo en el repositorio; el dueño crea los proyectos y sube |

## Preguntas abiertas
- (ninguna)

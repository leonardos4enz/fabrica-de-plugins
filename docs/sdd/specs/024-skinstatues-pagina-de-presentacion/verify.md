# Verificación 024

Fecha: 2026-11-01 · SkinStatues 0.3.0 sin cambios de código (la spec agrega imágenes, descripciones y escenas al arnés de capturas).

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ OmniEvents 209/209, SkinStatues 69/69 | `./gradlew build` en verde; nueva `PresentacionTest` de SkinStatues (4 casos) |
| Arnés (`npm --prefix tests-e2e run test:arnes`) | ✔ 21/21 | `capturas.mjs` con `carpetaServidor`, `prepararCarpeta`, `antesDeArrancar`/`alTerminar`, `visor`; `Camara` con distancia y tamaño |
| Póster de OmniEvents sin cambios | ✔ | `npm --prefix tests-e2e run presentacion` sin argumento: `git status` sin cambios en `docs/site/pages/assets/` |
| 2 y 3a (servidor real y suite en el juego) | No aplica | El jar no cambió. Las escenas corrieron la 0.3.0 en Paper 1.21.4 real (`run-capturas/`): estatuas de 100, paletas, skin por link con el HTTPS local de la 023 y `statue remove` al terminar cada escena |

## Imágenes producidas (solo las escenas elegidas por el dueño)
| Pieza | Archivo | Fuente |
|---|---|---|
| Logo (512 px, legible a 64 y 32 px) | `docs/img/skinstatues/presentacion/logo.png` | `docs/site/assets-src/skinstatues.html` |
| 6 franjas PNG y 1 GIF (1280 px; el GIF de 10 cuadros, 234 KB) | `docs/img/skinstatues/presentacion/` | ídem + `npm --prefix tests-e2e run presentacion -- --plugin skinstatues` |
| 5 escenas, 16 fotos de 1280×720 | `docs/img/skinstatues/presentacion/escenas/` | `tests-e2e/skinstatues/presentacion.mjs` con `--plugin skinstatues --escenas presentacion --solo` |

Mundo: semilla `8678942899319966093`, terreno normal (llano junto a un río, colinas y bosque), regenerado en cada corrida.
Escenas: `giant` (Technoblade y ElRichMC de 100), `row` (Vegetta777, Willyrex, elrubius, Mikecrack, Dream y GeorgeNotFound
de 44 con seis poses), `build` (ElRichMC de 56 por capas, y de cerca), `name-link` (DanTDM por nombre, LTeZla por link) y
`palettes` (Bobicraft en lana, concreto y terracota). Las 13 skins se bajaron de Mojang a `tests-e2e/.cache/skins/`
(`skins-youtubers.mjs`) y no están en el repositorio.

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (logo) | CA1: `skinstatues.html` → `logo.png` 512 px, revisado a 64 y 32 px; CA2: cabeza de bloques con casco (la skin `Example`) sobre pedestal, dibujada en el HTML |
| H2 (escala) | CA1: `giant` / franja 2; CA2: `row` / franja 3; CA3: `build` y `build-close` / franja 4 (GIF); CA4: `name-link` y `palettes` / franja 5; CA5: skins de la lista del dueño (Quackity cambiado por DanTDM, F-01) |
| H3 (solo lo necesario) | Corridas con `--solo`; 5 escenas; servidor `run-capturas/` (puerto 25597); `run/` del dueño sin tocar; skins solo en la caché |
| H4 (publicación) | CA1–CA4: `docs/skinstatues/presentacion/` y `PresentacionTest` (mismas imágenes en el mismo orden, que existan, [IMG] en una línea, transcripción y «not affiliated» visibles); CA3: textos revisados contra el README y las specs 021–023; CA5: no se publicó nada |

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Media (contenido) | La skin actual de la cuenta Quackity es Mickey Mouse: un personaje de Disney en la página oficial suma un riesgo de marca aparte del de los youtubers | Se cambió por DanTDM (de la misma lista del dueño, sin usar); escena `name-link` regenerada. Lección: mirar la skin actual de cada cuenta |
| F-02 | Baja (arnés) | En `correrEscena`, el `dormir(timeoutMs)` del `Promise.race` nunca se cancelaba: con una escena larga, node seguía vivo hasta el tope aunque Paper ya estuviera apagado | `setTimeout` con `clearTimeout` en el `finally`. Sin prueba automática (el arnés no tiene una de tiempos); se vio al terminar las corridas de 15 min |
| F-03 | Baja (arnés) | El visor de las capturas cargaba 3 chunks fijos: una estatua de 100 de lejos no se veía | `Camara(…, { distancia })` (12) y `view-distance` 12 en `run-capturas` |
| F-04 | Baja (arnés) | `tp` con coordenadas enteras centra en el bloque: esperar la posición exacta del bot nunca se cumplía | Coordenadas con decimales |
| F-05 | Baja (arnés) | Tras un `tp`, el visor tarda ~20 s en armar 12 chunks: los primeros cuadros del GIF mostraban las colinas apareciendo | Espera de 20 s antes de la primera foto |

Lecciones en `CLAUDE.md` (spec 024).

## Riesgo aceptado por el dueño
Las imágenes muestran skins de youtubers reales sin su permiso (decisión 1). Las dos descripciones dicen, fuera de la
transcripción, que el plugin no está afiliado a ellos ni a Mojang; `publicar.md` explica qué hacer si alguien pide quitar
una imagen. Los nombres no van en el título, el resumen ni las etiquetas.

## Publicación
Pendiente del dueño (decisión 4), con `docs/skinstatues/presentacion/publicar.md`. Lo que debe confirmar en cada sitio:
categorías, que el slug `skinstatues` esté libre y si SpigotMC ya ofrece 26.1 en la lista de versiones. Las imágenes
se ven en las páginas cuando este commit está en `main`.

## README
Raíz: estructura (`docs/<plugin>/presentacion/`) y sección «Página de presentación» con los comandos de SkinStatues. Plugin:
cabecera del póster y enlaces a las imágenes y las descripciones. Roadmap, índice de specs y `CLAUDE.md`. Capturas: solo
las 5 escenas de esta spec, con `--solo`.

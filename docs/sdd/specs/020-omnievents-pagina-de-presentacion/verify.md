# Verificación 020

Fecha: 2026-11-01 · OmniEvents 1.12.0 sin cambios de código (la spec cambia imágenes, descripciones y el arnés de capturas).

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ 209/209 | `./gradlew build` en verde; nueva `PresentacionTest` (2 casos) |
| Arnés (`npm --prefix tests-e2e run test:arnes`) | ✔ 21/21 | `plugins-extra.mjs` con filtro y `capturas.mjs` con `--escenas` |
| 2 y 3a (servidor real y suite en el juego) | No aplica | El jar no cambió. Las escenas nuevas corrieron el paquete `examples-en` tal cual en Paper 1.21.4 con WorldEdit, WorldGuard y Citizens: bienvenida, `/daily` con racha y enfriamiento, regiones `mine` y `vip`, y el NPC «Guide» respondieron lo esperado |
| Galería de la 004 sin cambios | ✔ | `npm run capturas -- --sin-build --solo "^ejemplo-bienvenida$"` → «igual»: el arnés sigue funcionando con la configuración de siempre |

## Imágenes producidas (solo las del diseño elegido)
| Pieza | Archivo | Fuente |
|---|---|---|
| Logo (512 px) y banner del sitio (1600×500) | `docs/site/pages/assets/logo.png`, `banner.png` | `docs/site/assets-src/presentacion.html` |
| 5 franjas PNG y 2 GIF (1280 px; 194 KB y 256 KB) | `docs/site/pages/assets/presentacion/` | ídem + `npm --prefix tests-e2e run presentacion` |
| 4 escenas en inglés, 10 cuadros | `docs/img/omnievents/presentacion/` | `tests-e2e/omnievents/presentacion.mjs` con `--escenas presentacion --solo` |

No se regeneró la galería ni se hizo una imagen por capacidad. `/omni debug` va como texto (decisión 3).

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (logo) | CA1: bocetos A, B y C, elección del dueño (B) y su versión por capas (b2); CA2: `presentacion.html` → `logo.png` 512 px, revisado a 64 y 32 px en los bocetos; CA3: revisión: bloque, minerales y texturas dibujados en el HTML (ruido con semilla), sin logos ajenos |
| H2 (página) | CA1–CA4 y CA6: las 7 franjas; CA5: tabla revisada fila por fila contra el roadmap (001, 006, 009, 010, 011, 012, 013, 007) y una fila honesta a favor de ConditionalEvents (Spigot y versiones anteriores a 1.21.4); la frase dice «Built to replace», no «everything it does», porque la 016 deja fuera unas pocas cosas |
| H3 (solo lo necesario) | Corridas con `--solo`; 4 escenas, 10 cuadros; la galería sigue igual |
| H4 (descripciones) | CA1–CA3: `docs/omnievents/presentacion/` y `PresentacionTest` (mismas imágenes en el mismo orden, que existan, y que lo que cita la transcripción lo digan las reglas); CA4: no se publicó nada |

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Baja (texto) | En `modrinth-hangar.md`, una cita de la transcripción («Try /daily, /shop and /coins.») quedó partida por un salto de línea: un buscador o un lector de pantalla no la encontraría entera | `PresentacionTest` la detectó (falló con ese texto); se unió la línea |
| F-02 | Baja (arnés) | El visor 3D no dibuja jugadores ni aldeanos: el NPC no salía en la captura; y dibuja al comerciante ambulante siempre mirando al sur | NPC `--type WANDERING_TRADER` y la cámara al sur. Lección en `CLAUDE.md` |
| F-03 | Baja (arnés) | A 4 bloques el clic al NPC no llega (alcance de interacción de 3 bloques), y `activateEntity` gira la cámara: los cuadros del GIF saltaban | NPC a 2,8 bloques y vuelta a la mirada anterior antes de cada foto |
| F-05 | Baja (texto) | En SpigotMC, cada salto de línea entre las [IMG] dejaba una franja blanca entre las imágenes | Las 7 [IMG] en una sola línea del BBCode |
| F-04 | Baja (diseño) | Silkscreen y Pixelify Sans se leían mal en los títulos («OHNIEVENTS», «Ɛ») | Press Start 2P |

## Publicación (con el OK del dueño, 2026-11-01)
- **Modrinth:** versión 1.12.0 (Paper; 1.21.4 y 26.1.2) y descripción nueva. El proyecto sigue «Under review».
- **Hangar:** versión 1.12.0 (Release, Paper 1.21.4–26.1.2, mismas dependencias opcionales) y página principal nueva.
- **SpigotMC:** actualización 1.12.0 con su changelog y descripción nueva. SpigotMC pone un <br> por cada salto de línea entre [IMG]: el BBCode lleva las 7 imágenes en una sola línea (F-05). Aun así queda una línea fina entre franjas: es el espaciado de SpigotMC para imágenes en línea.
- Los íconos de las tres páginas siguen siendo el logo viejo.

## Pendiente (fuera de alcance)
- `docs/omnievents/galeria.md` está atrasada respecto de las reglas de ejemplo (le faltan los `tests:` de `diamantes_contados`):
  cualquier corrida de la galería la reescribe. No es de esta spec.

## README
Raíz: estructura y sección «Página de presentación» (cómo regenerar escenas, piezas y descripciones). Plugin: párrafo con
la página de presentación y su cabecera. Roadmap, índice de specs y `CLAUDE.md` (lecciones de la 020). Capturas: solo las 4
escenas nuevas de la presentación, con `--solo`.

# Plan 024 — Multimedia y página de presentación de SkinStatues

## 1. Póster (decisión 3)
Misma técnica que la [020](../020-omnievents-pagina-de-presentacion/plan.md): franjas de 1280 px que, apiladas, se leen
como un corte del mundo (cielo → roca madre). Press Start 2P (OFL) en los títulos; texturas y bloques dibujados en el HTML.

| # | Franja | Capa | Tipo |
|---|---|---|---|
| 1 | Cabecera: logo, nombre, «Any player. Any skin. Any size.» | cielo | PNG |
| 2 | «100 blocks tall»: la estatua gigante en el paisaje, de lejos | cielo → pasto | PNG |
| 3 | «Any player, any pose»: la fila de youtubers | pasto → tierra | PNG |
| 4 | «Watch it build»: GIF de la construcción y de cerca el relieve de la segunda capa | tierra | GIF |
| 5 | «By name or by link» y las paletas (lana, concreto, terracota) | tierra → piedra | PNG |
| 6 | Tarjetas: hueca, dos capas, 28 poses y poses propias, deshacer devuelve el terreno, sin lag, consola y `--at` | piedra → pizarra | PNG |
| 7 | «How to start» como barra de acceso rápido | roca madre | PNG |

Además `logo.png` (512 px). Fuente: `docs/site/assets-src/skinstatues.html` (`?pieza=<id>`, `?cuadro=<n>`). Salida:
`docs/img/skinstatues/presentacion/` (franjas, GIF y logo), generada por `tests-e2e/presentacion.mjs` con un argumento
de plugin, sin cambiar lo que produce para OmniEvents.

## 2. Escenas en el juego
`tests-e2e/skinstatues/presentacion.mjs` con `capturas.mjs --plugin skinstatues --escenas presentacion --solo …`:
- Servidor en `plugins/skinstatues/run-capturas/` (Paper 1.21.4), mundo generado con semilla fija y terreno normal;
  `build.blocks_per_tick` bajo solo para los cuadros del GIF.
- Skins de los youtubers: se bajan una vez de la API de Mojang a `tests-e2e/.cache/skins/` y se copian a
  `plugins/SkinStatues/skins/` del servidor de capturas (la escena no sale a internet). El link de la franja 5 se sirve
  con el HTTPS local de la 023.
- Las estatuas se montan con `--at` desde la consola y el bot mira con prismarine-viewer: el visor dibuja de y = 0 a 255
  (una estatua de 100 desde y ≈ 70 entra) y no dibuja jugadores.
- Cuadros y fotos en `docs/img/skinstatues/presentacion/escenas/`.

| Escena | Contenido |
|---|---|
| `giant` | Una estatua de 100 en el paisaje, cámara lejos y baja |
| `row` | 5 estatuas de ~48 con poses distintas |
| `build` | Cuadros de una estatua apareciendo + foto de cerca del relieve |
| `name-link` | Por nombre y por link; tres paletas |

## 3. Textos
`docs/skinstatues/presentacion/`: `spigotmc.bbcode`, `modrinth-hangar.md` y `publicar.md` (resumen, etiquetas,
versiones, changelog, paso a paso por sitio y la línea «not affiliated»).

## 4. Pruebas
- `PresentacionTest` de SkinStatues: las imágenes que nombran las dos descripciones existen y están en el mismo orden.

## 5. STRIDE
| Amenaza | Respuesta |
|---|---|
| Suplantación (marcas ajenas, respaldo de creadores) | Sin logos ni texturas de Mojang; skins de youtubers solo por decisión del dueño, con «not affiliated» |
| Divulgación (skins o secretos en el repo) | Skins en `tests-e2e/.cache/` (ignorada); revisión antes del commit |
| Alteración (mundo del dueño) | Servidor de capturas aparte (`run-capturas/`); `run/` no se toca |
| Repudio, DoS, elevación | No aplica: no cambia el plugin |
| Publicación sin permiso | La IA no sube nada; el dueño crea los proyectos |

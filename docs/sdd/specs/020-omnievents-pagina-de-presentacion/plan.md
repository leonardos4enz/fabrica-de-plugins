# Plan 020 — Página de presentación con imágenes

## 1. Diseño elegido (CLARIFY 2)
Propuesta **B, póster por capas** ([boceto](bocetos/b2-poster.png)): la página es un corte del mundo. Al bajar se pasa
por cielo, pasto, tierra, piedra, pizarra y roca madre. Cada franja es una imagen de 1280 px de ancho y, apiladas sin espacio, se leen
como un solo póster (la técnica de la página de ConditionalEvents, con decoradores propios):

| # | Franja | Capa | Tipo |
|---|---|---|---|
| 1 | Cabecera: logo, nombre, frase, «todo lo de ConditionalEvents y además…» | cielo → pasto | PNG |
| 2 | «See it in game»: bienvenida y `/daily` con racha | tierra | GIF |
| 3 | Región de WorldGuard y NPC de Citizens | tierra → piedra | GIF |
| 4 | «Only in OmniEvents»: seis tarjetas con mineral (debug, `tests:`, expresiones, menús → Bedrock, paquetes, VS Code) | piedra → pizarra | PNG |
| 5 | «Your YAML → the game» | pizarra | PNG |
| 6 | «Coming from ConditionalEvents?» con tabla ✔/— | pizarra → roca madre | PNG |
| 7 | «How to start» como barra de acceso rápido de 4 casillas | roca madre | PNG |

Además: el logo nuevo (bloque isométrico en pixel art, 512 px) y el banner del sitio (`banner.png`, 1600×500, la
cabecera en ese formato). Sin texturas, fuentes ni logos de Mojang ni de otros plugins: el ruido de píxeles sale de una semilla y
los minerales y el bloque se dibujan en SVG. La letra pixelada es Press Start 2P (Google Fonts, OFL).

## 2. Fuentes y salida
- `docs/site/assets-src/presentacion.html`: las siete franjas, el logo y el banner en un solo HTML. Con `?pieza=<id>` muestra
  una sola pieza; con `?cuadro=<n>`, el cuadro n de los GIF.
- `tests-e2e/presentacion.mjs` (`npm --prefix tests-e2e run presentacion`): abre el HTML con puppeteer-core (el
  navegador de las capturas), saca cada pieza con su tamaño exacto y arma los GIF con `ffmpeg`: un cuadro por paso
  con las dos escenas a la vez, una paleta por GIF y sin tramado. Salida en `docs/site/pages/assets/presentacion/`; `logo.png` y `banner.png`
  se reemplazan en `docs/site/pages/assets/` (los usa el sitio de la 017).
- Las imágenes se enlazan por `https://raw.githubusercontent.com/leonardos4enz/fabrica-de-plugins/main/docs/site/pages/assets/presentacion/…`.

## 3. Escenas en inglés (solo las necesarias)
Un archivo de escenas aparte, `tests-e2e/omnievents/presentacion.mjs`, para no tocar la galería de la 004:
- `capturas.mjs` acepta `--escenas presentacion` (por defecto `capturas`). Una configuración sin `galeria` ni `cobertura`
  no escribe la galería ni mide cobertura.
- `ctx.cuadro()` guarda la foto actual como un cuadro numerado de la escena (`<id>-1.png`, `<id>-2.png`…). La foto
  final queda como `<id>.png`.
- `cfg.pluginsExtra` (expresión regular) instala solo esos plugins de terceros de `plugins-extra.mjs` (con su sha256
  fijado): WorldEdit, WorldGuard y Citizens. Sin EssentialsX, porque no hace falta Vault y reemplaza comandos de
  Minecraft.
- Se copia solo el paquete `examples-en` y se activa con `/omni pack enable examples` antes de que entre Ana: así
  vive su primera vez.

| Escena | Cuadros |
|---|---|
| `en-welcome` | 1) anuncio y kit; 2) título «Welcome!» |
| `en-daily` | 1) `/daily` con racha (variable preparada); 2) segundo `/daily` con el enfriamiento |
| `en-region` | 1) frente a la mina; 2) dentro: «Mining boost on» y Haste; 3) VIP sin permiso: «Only VIP players…» |
| `en-npc` | 1) el NPC Guide; 2) clic: saludo y pan; 3) golpe: «Guide says: hey, that hurts!» |

`/omni debug` no lleva captura: los mensajes del plugin están en español y la tarjeta muestra el formato como texto.
Salida: `docs/img/omnievents/presentacion/` (cuadros y foto final; permiten rehacer los GIF sin servidor).

## 4. Descripciones
`docs/omnievents/presentacion/spigotmc.bbcode` y `docs/omnievents/presentacion/modrinth-hangar.md`: las imágenes en
orden, los enlaces y un Spoiler (BBCode) o un `<details>` (Markdown) con la transcripción de cada imagen. Cada
afirmación sale de una spec verificada.

## 5. Pruebas
- `DocsSiteTest` (o una prueba nueva en gradle): cada imagen que nombran las dos descripciones existe en el repositorio
  y las dos descripciones enlazan las mismas imágenes en el mismo orden.
- La tabla de comparación solo lleva filas del [roadmap](../../roadmap.md) (revisión en `verify.md`).

## 6. STRIDE
| Amenaza | Respuesta |
|---|---|
| Suplantación (logos o marcas ajenas) | Ningún logo de otros plugins ni texturas de Mojang; sus nombres solo en texto |
| Alteración (plugins de terceros en el servidor de capturas) | Los mismos jars aprobados con sha256 fijado (`plugins-extra.mjs`) |
| Divulgación (secretos en las descripciones) | Revisión antes del commit; las descripciones no llevan credenciales |
| Repudio, DoS, elevación | No aplica: no cambia el plugin |
| Publicación sin permiso | Subir las descripciones o la 1.12.0 solo con el OK explícito del dueño |

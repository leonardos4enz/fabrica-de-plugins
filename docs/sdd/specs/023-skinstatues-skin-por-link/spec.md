# Spec 023 — SkinStatues: estatuas con el link de una skin

**Estado:** Implementado y verificado (compuertas 1, 2 y 3a en Paper 1.21.4 y 26.1.2) · aprobada por el dueño (2026-11-01) · **Plugin:** SkinStatues (0.2.0 → 0.3.0) · **Depende de:**
[021](../021-skinstatues-nucleo/spec.md), [022](../022-skinstatues-poses-y-coordenadas/spec.md) ·
**Origen:** pedido del dueño (2026-11-01): «quisiera que se pueda hacer mediante link de skin así como SkinsRestorer,
que puedes usar skins con un link, nada más validar que sea un formato de skin válido».

## Problema
Hoy la skin de una estatua sale de `skins/<name>.png` (hay que subir el archivo al servidor) o de la cuenta de Mojang
por su nombre. Una skin que no es de ninguna cuenta (la de un evento, un personaje, una que alguien hizo en un editor y
subió a Imgur) obliga a entrar a la carpeta del servidor. SkinsRestorer deja usar una skin por su link; el dueño quiere
lo mismo para las estatuas, con la condición de que solo se acepte una skin válida.

Bajar algo de un link que escribe un jugador es la parte delicada: el servidor no debe poder usarse para pedir cosas a
su propia red (localhost, el router, otros servicios de la LAN), ni bajar archivos enormes, ni quedar colgado.

## Historias

**H1 — Construir con el link de una skin.** Como *administrador*, quiero escribir el link de una skin en vez del nombre
de una cuenta.
- CA1: `/statue build <link> <height> [pose] …` acepta un link `https://…` a la imagen PNG de una skin en el mismo lugar
  que el nombre; todo lo demás (altura, pose, paleta, `--at`, `--facing`, `--world`, `--no-overlay`, consola) funciona
  igual que con un nombre. Una estatua con link no lleva capa (cape).
- CA2: Un link `http://` se pide por `https://` (como hace el plugin con las texturas de Mojang); si el sitio no contesta
  por `https`, el mensaje lo dice.
- CA3: El modelo (brazos de 4 o de 3 píxeles) se **detecta** de la imagen: una skin de 64×64 con transparente la zona
  que solo usan los brazos anchos es delgada; si no, clásica; la antigua de 64×32 siempre es clásica. `--slim` y
  `--classic` fuerzan el modelo (también con un nombre o un archivo de `skins/`).
- CA4: Se activa con `network.urls: true` en `config.yml`, **apagado por defecto** (constitución II.4) e independiente
  de `network.mojang`. Apagado, el mensaje dice qué opción activar y no se pide nada a la red.
- CA5: Usar un link pide además el permiso `skinstatues.build.url` (`default: op`): el dueño puede dar `build` sin dar
  links.
- CA6: Lo bajado se guarda en caché por link durante `network.cache_hours`: la segunda estatua con el mismo link no
  vuelve a pedirlo. Si el sitio deja de contestar, la copia vencida sigue sirviendo (como con Mojang).

**H2 — Solo una skin válida y de un sitio público.** Como *dueño del servidor*, quiero que un link no pueda usarse para
dañar el servidor ni su red.
- CA1: La imagen se valida antes de usarla: que sea PNG (firma y cabecera), de **64×64 o 64×32** (leído de la cabecera
  antes de decodificar, contra las bombas de descompresión) y de 1 MB como mucho (se deja de leer al pasar el tope).
  Cualquier otra cosa (una página HTML, un JPG, una imagen de otro tamaño, un archivo vacío) da un mensaje claro y no
  construye nada.
- CA2: Solo se aceptan links `https` al puerto estándar, sin usuario ni contraseña en el link, de 512 caracteres como
  mucho y con los caracteres de una URL; lo demás se rechaza sin pedir nada a la red.
- CA3: No se pide nada a una dirección que no sea pública: `localhost`, redes privadas y de la LAN (10/8, 172.16/12,
  192.168/16, IPv6 locales), enlace local, CGNAT, multidifusión y reservadas, escritas como IP o como un nombre que
  resuelve a ellas. Una redirección se sigue (3 como mucho) solo si su destino cumple lo mismo.
- CA4: `network.allowed_hosts` en `config.yml` restringe los sitios (`i.imgur.com`, `*.imgur.com`,
  `textures.minecraft.net`); vacía (por defecto) acepta cualquier sitio público. Un sitio fuera de la lista se rechaza
  diciendo cuáles se aceptan.
- CA5: Cada pedido tiene un tiempo de espera (8 s); un sitio que no contesta, contesta un error (404, 500…) o manda
  demasiados datos da un mensaje claro y el servidor sigue normal (nada en el hilo principal).
- CA6: El plugin solo baja la imagen: no manda cookies ni datos del servidor (solo un `User-Agent`), no ejecuta nada y
  la imagen no se guarda fuera de `cache/`.

**H3 — De dónde salió la skin, sin publicar el link.** Como *dueño del servidor*, quiero saber qué estatuas salieron de
un link sin que el link entero quede guardado ni a la vista.
- CA1: `/statue list` e `/statue info` muestran una estatua de link como `url:<sitio>#<huella>` (p. ej.
  `url:i.imgur.com#3f9a1c2e`): el sitio y una huella corta del link (los 8 primeros caracteres de su SHA-256).
- CA2: El link entero no se escribe en `data/statues.yml`, en el log del servidor ni en los nombres de archivo de la
  caché (que usan la huella); los mensajes de la construcción muestran el sitio, no el link.

## Fuera de alcance (y por qué)
- Capa (cape) por link, y links dentro de `skins/<name>.yml`: no se pidieron; quedan para cuando se pidan.
- Subir la skin a MineSkin u otro servicio (lo que hace SkinsRestorer para cambiar la skin del jugador): la estatua solo
  necesita la imagen.
- Skins de alta resolución (128×128 o más): no son un formato de skin del juego (H2 CA1).

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Sintaxis? | El link en lugar del nombre: `/statue build <link> <height> …`; el modelo se detecta y `--slim`/`--classic` lo fuerzan |
| 2 | ¿De qué sitios? | Cualquiera público (como SkinsRestorer), bloqueando localhost, IPs privadas y redirecciones hacia ellas; `allowed_hosts` para restringir (vacía = todos) |
| 3 | ¿Cómo se activa? | `network.urls: false` por defecto, aparte de `network.mojang`, y permiso `skinstatues.build.url` (op) |
| 4 | ¿Qué muestran `list` e `info`? | Solo el sitio y una huella corta; la URL entera no se guarda ni va al log |

## Preguntas abiertas
- (ninguna: aprobada el 2026-11-01)

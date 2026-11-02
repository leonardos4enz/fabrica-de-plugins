# Plan 035 — Lo de FAWE para crear (Worldcraft 0.11.0)

Spec: [spec.md](spec.md) · Respuestas del dueño: las cuatro piezas; imagen desde la carpeta y desde un link apagado de
fábrica; de pie o acostada según la mirada; la tabla de colores de SkinStatues. Solo API de Paper 1.21.4, sin NMS.
La red se copia de SkinStatues 023 ([ADR 0007](../../../adr/0007-red-segura-copiada.md)).

## 1. Diseño

### 1.1 Colores (paquete `color`, Java puro)
- `Lab` (copiado de SkinStatues): CIELAB D65 y ΔE76².
- `BlockColors`: la tabla `bloque → #RRGGBB` (de `block-colors.yml` del jar, medida por SkinStatues en 1.21.4) con los
  cambios de `config.yml` (`colors.remove`, `colors.add`); solo los bloques que el servidor conoce. `nearest(rgb)` (el más
  parecido en Lab, con caché por color de 24 bits, seguro entre hilos), `colorOf(material)` (o −1), `darker(material)` y
  `lighter(material)`: el bloque con menos diferencia de tono (a, b) y luminosidad entre 3 y 25 por debajo (o por
  encima), prefiriendo 10 menos; `multiply`, `average` y `desaturate(rgb, %)` como FAWE (multiplicar canal por canal, el
  promedio, acercar cada canal a la luminancia `0.3r + 0.59g + 0.11b`). `parse(texto)`: `#rgb`, `#rrggbb`, `rrggbb` o uno
  de los 16 nombres de los tintes (con «did you mean»).
- Patrones (`Parser`): un término `#color[…]`, `#darken`, `#lighten`, `#saturate[…]`, `#desaturate[n]`,
  `#averagecolor[…]` (también sin corchetes: `#color red`, no; solo con corchetes, como FAWE). `#color` es un estado fijo; los
  otros son un **cambio sobre el bloque actual** (`Pattern.changes`, el mecanismo de `^tipo` de la 029): material → color
  → bloque nuevo, o null (se deja) si el material no está en la tabla. Se mezclan con pesos. `Pattern` gana `also`: los
  estados que un cambio por color puede poner (toda la tabla), para los bloques prohibidos del grupo. Un `#palabra` que no
  es un color sigue diciendo que los tags van en máscaras, con «did you mean» si se parece a uno de color.

### 1.2 Minerales (`create/Ores`, Java puro)
- `vein(cx, cy, cz, size, random, out)`: la veta del juego (`OreFeature`): un segmento de largo `size/8` con ángulo al azar
  y esferas a lo largo con radio `((sin(πt)+1)·r·size/16 + 1)/2`. `place(región, Spec, semilla, Work)`: por cada chunk de
  la caja, `per_chunk` intentos, cada uno con `rarity` %; centro al azar en el chunk y una y uniforme entre `min_y` y
  `max_y` (∩ la caja); solo los puntos dentro de la región. Devuelve los puntos y cuántas vetas.
- `ore <máscara> <patrón> [tamaño] [frecuencia] [rareza] [y mín] [y máx]`: `Pick` = el patrón donde pasa la máscara
  (estado, como FAWE), si no `KEEP`. `ores [máscara]`: cada mineral de `ores.list` con su semilla; `Pick` = el mineral, o
  su versión deepslate si el bloque actual pasa `ores.deepslate_in`; solo donde pasa la máscara (de fábrica `ores.mask`).
  Varios minerales en el mismo bloque: el primero de la lista (los puntos se juntan en un mapa punto → mineral).

### 1.3 Cuevas (`create/Caves`, Java puro)
- Ruido de `formula/Noise3` (se vuelve público). Escala `k = frecuencia / 40 / 24` (bloques → ruido; y × 1,5 para que
  los túneles vayan más bien de lado). Túnel donde `n1² + n2² < t²` con `t = tamaño · k · 0,45` (medio ancho) (dos superficies donde el
  ruido vale cero se cruzan en una línea: las cuevas «espagueti» del juego). `--rooms`: también donde un ruido de un
  cuarto de la frecuencia pasa de 0,45 («queso»). Los parámetros se ajustaron mirando cortes con jshell y `CreateTest` (fracción
  tallada y que los túneles se conectan).
- `carve(región, tamaño, frecuencia, salas, semilla, Work)` → puntos; el plazo de `/wc timeout` (el mismo hilo que las
  fórmulas). `Pick`: si el bloque actual pasa `caves.mask` → aire, o lava si `y ≤ caves.lava_y`; si no, `KEEP`.

### 1.4 Imagen (`create/ImageBlocks` Java puro; `net/*` copiado de SkinStatues; `paper/Images`)
- `ImageBlocks.scale(argb, w, h, lado)`: promedio por área (con el alfa como peso) hasta que el lado más largo sea `lado`;
  `blocks(argb, colores)` → índice de la tabla o −1 (alfa < 128). `Placement`: origen, eje `u` (columnas, de izquierda a
  derecha para quien mira) y eje `v` (filas, de arriba abajo), con `at(col, fila)` → x, y, z.
  - De pie (`--wall`, o mirando a menos de 45° abajo): la dirección cardinal `d` de la mirada; `u` = la derecha de `d`
    (`(−dz, 0, dx)`), las filas bajan desde `pies.y + alto − 1` hasta `pies.y`, el plano a 2 bloques delante, centrado.
  - Acostada (`--floor`, o mirando más de 45° abajo): en la capa de los pies (`pies.y`), la fila de abajo a 1 bloque
    delante y la de arriba la más lejana (`v` = −`d`), `u` = la derecha de `d`, centrada.
- `ImageSource` (en `paper/Images`): un `PointsSource` con los píxeles no transparentes; `Pick` = el estado del píxel.
- Lectura (`ImageBlocks.read(bytes, maxLado)`): `ImageIO` con el lector del formato (PNG, JPG, GIF: el primer cuadro);
  el tamaño de la cabecera antes de decodificar (cada lado ≤ 4096; si no, «too big»); los bytes con tope
  `images.max_file_kb`.
- `net/ImageLink`, `net/LinkPolicy`, `net/LinkClient`: los de SkinStatues 023 (link validado y normalizado a https, solo
  direcciones públicas en cada salto, 3 redirecciones, puerto 443, `allowed_hosts`, 8 s, tope de bytes, `User-Agent:
  Worldcraft`, `Accept: image/*`). `-Dworldcraft.test.localLinks=true` (solo por la línea de comandos de Java, para la
  suite) deja pedir al HTTPS local; el plugin lo avisa en el log al arrancar.
- `paper/Images`: un hilo propio `Worldcraft-Images` (una descarga lenta no frena las fórmulas): bajar o leer el archivo,
  decodificar, escalar, elegir los bloques; de vuelta en el hilo principal, la obra.
- Archivos: `images/<nombre>` con nombre de `[A-Za-z0-9._-]+` y extensión `png|jpg|jpeg|gif` (sin `/`, `\` ni `..`); la ruta
  se resuelve y se exige que quede dentro de la carpeta. `image list` los dice.

### 1.5 Comandos (`command/CreateCommands`)
- `caves [tamaño] [frecuencia] [--rooms] [--preview]`, `ore <máscara> <patrón> [tamaño] [frecuencia] [rareza] [y mín]
  [y máx] [--preview]`, `ores [máscara] [--preview]` (acepta `-d`/`-b` de FAWE sin efecto: lo hace solo),
  `image|img <archivo|link|list> [tamaño] [--floor|--wall] [--preview]`. Todo sobre la selección (030) salvo `image`, que va
  donde está el jugador (solo en el juego). Mismo camino que `generate`: tope del grupo con lo que se evalúa, cálculo
  fuera del hilo, `Edits.request` con `PointsSource`, los estados que pone para los bloques prohibidos.
- Ayuda (`help.<sub>` con ejemplo), autocompletado (números, máscaras, bloques, archivos de la carpeta, `--floor`,
  `--wall`, `--rooms`), y en los patrones `#color[`, `#darken`, `#lighten`, `#saturate[`, `#desaturate[`, `#averagecolor[`.

## 2. Contrato de configuración (`config.yml`)
```yaml
# Spec 035: block colors for /wc image and the color patterns (#color[red], #darken, #lighten, #saturate[#ff0000]...).
# The built-in table has 145 blocks measured in Minecraft 1.21.4 (wool, concrete, terracotta, wood, stone...).
colors:
  remove: []              # blocks never to use, like [gold_block, diamond_block]
  add: {}                 # block: "#RRGGBB" - a new block, or another color for one in the table

# Spec 035: /wc ore and /wc ores.
ores:
  mask: "stone, deepslate, andesite, diorite, granite, tuff"   # where /wc ores puts ores (when no mask is given)
  deepslate_in: "deepslate, tuff"                               # where the deepslate version goes
  max_size: 64            # the biggest vein /wc ore makes (1..256)
  list:                   # name: block, deepslate version, blocks per vein, tries per chunk (16×16), heights
    coal:     { block: coal_ore,     deepslate: deepslate_coal_ore,     size: 17, per_chunk: 20, min_y: 0,   max_y: 192 }
    iron:     { block: iron_ore,     deepslate: deepslate_iron_ore,     size: 9,  per_chunk: 10, min_y: -24, max_y: 56 }
    copper:   { block: copper_ore,   deepslate: deepslate_copper_ore,   size: 10, per_chunk: 16, min_y: -16, max_y: 112 }
    gold:     { block: gold_ore,     deepslate: deepslate_gold_ore,     size: 9,  per_chunk: 4,  min_y: -64, max_y: 32 }
    redstone: { block: redstone_ore, deepslate: deepslate_redstone_ore, size: 8,  per_chunk: 4,  min_y: -64, max_y: 15 }
    lapis:    { block: lapis_ore,    deepslate: deepslate_lapis_ore,    size: 7,  per_chunk: 2,  min_y: -64, max_y: 64 }
    diamond:  { block: diamond_ore,  deepslate: deepslate_diamond_ore,  size: 8,  per_chunk: 7,  min_y: -64, max_y: 16 }
    emerald:  { block: emerald_ore,  deepslate: deepslate_emerald_ore,  size: 3,  per_chunk: 3,  min_y: -16, max_y: 320 }

# Spec 035: /wc caves.
caves:
  mask: "#base_stone_overworld, #dirt, gravel, sand, sandstone, calcite, *_ore"   # what caves may cut through
  lava_y: -55             # at this height and below, a cave fills with lava (-2048 = never)

# Spec 035: /wc image.
images:
  folder: images          # under plugins/Worldcraft/
  max_size: 256           # most blocks on the long side (1..1024)
  max_file_kb: 2048       # biggest file, from the folder or a link (1..20480)
  links: false            # /wc image https://...: OFF by default (the server downloads from the internet)
  allowed_hosts: []       # sites allowed for links; empty = any public site; "*.imgur.com" also allows i.imgur.com
```
Un valor fuera de rango avisa y usa el de fábrica (constitución I.3); una clave desconocida avisa con «did you mean»; una
máscara o un bloque que no existe avisa con su ruta y usa el de fábrica (o se omite, en `colors` y `ores.list`); un
mineral sin `deepslate` usa el mismo bloque; `min_y > max_y` se omite. Un `config.yml` de la 0.10.0 sin estas secciones
carga igual. `plugin.yml`: permiso `worldcraft.image.link` (`default: op`).

## 3. STRIDE
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| **S**uplantación / SSRF: `/wc image https://192.168.1.1/…`, un nombre que resuelve a la LAN, un sitio que redirige a `localhost` | Atacar la red local del dueño desde el servidor | `images.links` apagado de fábrica; permiso `worldcraft.image.link` (OP); solo direcciones públicas comprobadas en **cada** salto (todas las del nombre); 3 redirecciones; puerto 443; sin usuario en el link; `allowed_hosts` |
| **S**: *DNS rebinding* | Lo mismo, entre la comprobación y la conexión | Igual que SkinStatues: la comprobación llena la caché de DNS de Java que usa `HttpClient` (30 s); riesgo residual si el dueño la apaga |
| **T**ampering: `/wc image ../../config.yml`, `..\server.properties` | Leer archivos fuera de la carpeta | Nombre `[A-Za-z0-9._-]+` con extensión de imagen, sin `..`; la ruta resuelta tiene que quedar dentro de `images/` |
| **T**: cuevas o minerales que rompen construcciones ajenas | Daño | Solo en la selección; las cuevas solo cortan lo de `caves.mask` (roca, tierra), los minerales solo donde pasa la máscara; las puertas de toda obra (mundos, tope, bloques prohibidos con todos los estados que se pueden poner, `gmask`, historial, `undo`) |
| **I**nformación: la IP del servidor llega a un sitio de terceros; el link queda en archivos | Privacidad | Apagado de fábrica; el historial guarda `url:sitio#huella`, no el link; el link no va al log |
| **D**enegación: imagen enorme o «bomba» (PNG de 60 000 × 60 000 en pocos KB), sitio lento | Memoria, hilo colgado | Tope de bytes antes de leer (`max_file_kb`), tamaño de la cabecera antes de decodificar (4096 por lado), 8 s por pedido, hilo propio fuera del principal, `max_size` de bloques |
| **D**: cuevas en una selección enorme; minerales con frecuencia enorme | Ticks largos, memoria | El volumen evaluado cuenta para el tope del grupo; cálculo fuera del hilo con el plazo de `/wc timeout`; `max_size` de la veta, frecuencia ≤ 1000 y rareza ≤ 100; lo que se pone va por el motor (8 ms por tick) |
| **D**: `#darken` en una obra grande | Ticks largos | El cambio por color se recuerda por estado (memo de la 029): una búsqueda por material distinto |
| **E**levación: un jugador con `worldcraft.use` baja de internet | Usar el servidor como descargador | `worldcraft.image.link` aparte, `default: op` |
| **R**epudio | — | Cada obra queda en el historial con el detalle (archivo o `url:sitio#huella`, parámetros) |

## 4. Pruebas
- Unitarias: `ColorTest` (tabla, `nearest` exacto en los colores medidos, `darker`/`lighter`, `multiply`/`average`/
  `desaturate`, nombres y «did you mean», `remove`/`add`), `ColorPatternTest` (patrones, mezcla, prohibidos con `also`,
  *presets*, errores), `CreateTest`: vetas (del tamaño pedido y conectada, rareza 0 = nada, alturas, solo en la región, misma
  semilla = lo mismo), cuevas (fracción tallada, conectada, `--rooms` talla más, tamaño mayor talla más, plazo),
  imagen (escala por área, transparencia, de pie y acostada no espejada ni al revés en las 4 direcciones,
  cabecera enorme rechazada sin decodificar), `LinkTest` (copiado de SkinStatues: SSRF, redirecciones, tope, sitios),
  `ConfigTest` (las cuatro secciones), `CommandTest` (ayuda y mensajes).
- Compuerta 2: `selftest` (un color, una veta, una cueva y una imagen de 2×2 en memoria) y los comandos desde la consola.
- Suite 3a: colores (`#color`, `#darken`, `#desaturate` sobre lana) y minerales (`ore`, `ores` con deepslate) sobre
  roca puesta **en el aire** (034 F-01: no tapar el pasto); cuevas con y sin `--rooms` en un bloque de piedra en el aire;
  imagen de la carpeta de pie y acostada (sobre una plataforma en el aire), desde el HTTPS local con `images.links`, y
  apagada; `undo`.

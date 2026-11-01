# Plan 021 — Diseño técnico de SkinStatues

## 1. Arquitectura
Paquete `dev.leo.skinstatues`. El núcleo (skin, modelo, colores) es Java puro, sin Bukkit, para probarlo en unitarias;
solo `build/`, `command/` y la clase principal tocan la API de Paper.

| Paquete | Clases | Qué hace |
|---|---|---|
| `skin` | `Skin`, `SkinPng`, `SkinRepository`, `MojangClient`, `SkinException` | Imagen ARGB 64×64 (la de 64×32 se convierte como el cliente: miembros izquierdos espejados y el truco de transparencia de Notch), modelo clásico/delgado y capa. Lee `skins/`, la caché y Mojang |
| `model` | `PlayerModel`, `Segment`, `Pose` (con `Part` y `Angles`), `Voxelizer`, `VoxelGrid`, `Vec3`, `Mat3`, `Transform` | Geometría del jugador en píxeles, poses y **voxelización inversa** (ver §1.1) |
| `color` | `Lab`, `BlockPalette` | CIELAB, paletas con bloques permitidos y el bloque más parecido (con caché por color de 24 bits) |
| `config` | `Settings`, `PoseLoader`, `BlockCatalog` (colores y paletas), `Issues` (con «did you mean»), `LoadedConfig`, `Messages` | Lectura y validación de los cuatro YAML; errores con archivo, ruta, causa y «did you mean» (Levenshtein) |
| `statue` | `Statues` (construir, quitar y `selftest`, con sus trabajos), `StatuePlan`, `TickWorker`, `StatueStore`, `Journal` | Cola de trabajos con presupuesto por tick, diario de bloques anteriores y el índice de estatuas |
| `command` | `StatueCommand` | `/statue` y su autocompletado filtrado por permiso |

### 1.1 Voxelización inversa (el corazón)
El modelo está en **píxeles de skin** con el origen entre los pies, `y` hacia arriba y la cara hacia `+z`. Cada parte
es una caja con su pivote (valores de Minecraft):

| Parte | Caja (x, y, z) | Pivote | UV base / 2.ª capa |
|---|---|---|---|
| Cabeza | −4..4, 24..32, −4..4 | (0, 24, 0) | (0,0) / (32,0) |
| Cuerpo | −4..4, 12..24, −2..2 | (0, 12, 0) cadera | (16,16) / (16,32) |
| Brazo derecho | −8..−4 (delgado −7..−4), 12..24, −2..2 | (−5, 22, 0) | (40,16) / (40,32) |
| Brazo izquierdo | 4..8 (delgado 4..7) | (5, 22, 0) | (32,48) / (48,48) |
| Pierna derecha | −4..0, 0..12, −2..2 | (−2, 12, 0) | (0,16) / (0,32) |
| Pierna izquierda | 0..4 | (2, 12, 0) | (16,48) / (0,48) |
| Capa | −5..5, 8..24, −3..−2 (espalda) | (0, 24, −2) | capa 64×32, caja en (0,0) de 10×16×1 |

En vez de «dibujar» cada caja, se recorre cada bloque del mundo dentro de la caja envolvente (ya girada y escalada) de
cada segmento y se lleva su centro **de vuelta** al espacio local de la parte: así cualquier giro sale sin huecos y
cada bloque sabe qué píxel de la skin le toca.

- **Jerarquía:** el giro del cuerpo (pivote en la cadera) arrastra cabeza, brazos y capa; las piernas son
  independientes. Transformación de un punto local: `R_facing · s · (T_cuerpo · T_parte · T_codo) + base`.
- **Codo y rodilla sin grietas (H4 CA2):** cada brazo y pierna son dos segmentos de 6 px. La mitad de abajo gira
  `elbow`/`knee` alrededor del eje x en la articulación; cada mitad se extiende sin fin hacia la articulación y se corta
  con el **plano bisector** del ángulo (inglete): la unión de las dos es un codo cerrado, sin solaparse por dentro ni
  abrirse por fuera, para cualquier ángulo de 0 a 150°. La coordenada `v` de la textura se recorta al rango de su mitad.
- **Hueca (H1 CA4):** un bloque es de la superficie si su centro está dentro de la caja y a menos de `e` de una cara
  real (no de la del inglete): `e` = 1 bloque si el segmento está alineado con los ejes y √3 bloques si está girado
  (así la cáscara no deja agujeros de lado a lado). Dimensiones de menos de un bloque (la capa a escala chica) se
  ensanchan a un bloque para que no desaparezcan.
- **Color:** la cara más cercana del punto decide qué región de la skin se lee (convención de UV de Minecraft:
  `u` de izquierda a derecha visto de frente para cada cara, `v` hacia abajo; arriba con `v` hacia el frente, abajo al
  revés). Con escala < 1 se promedian los píxeles de la huella del bloque sobre la cara (H1 CA3); los píxeles
  transparentes de la primera capa no existen (la 64×32 se vuelve opaca como en el cliente).
- **Segunda capa (H3):** grosor `t = max(overlay_depth · s, 1)` bloques por fuera de la cara de la primera, sin hueco. Un
  punto fuera de la caja base pero dentro de la inflada toma el píxel de la 2.ª capa de la cara de la que más sale (si
  es transparente, prueba las otras caras de las que sale). Si `overlay_depth · s < 0.5` no hay espacio propio y la 2.ª
  capa **pinta** la primera en esa cara (H3 CA3). Umbral de opacidad `overlay_alpha` (H3 CA2).
- **Prioridades:** primera capa (3) > capa/cape (2) > segunda capa (1): donde dos segmentos caen en el mismo bloque gana
  la de mayor prioridad y, a igual prioridad, la que está más cerca de su superficie. Así la chaqueta no tapa el brazo.
- **Altura (H4 CA6):** tras voxelizar, si algo quedó bajo `y = 0` se sube toda la estatua. La escala es
  `s = altura / 32` bloques por píxel.
- **Orientación:** la cara hacia donde mira el jugador, redondeado a N/S/E/O (giro de 0/90/180/270°).

Prueba clave de «sin huecos»: a escala 4, un relleno por inundación (6-vecinos) desde el centro de cada segmento,
por bloques vacíos, no debe llegar al borde de la caja envolvente (cáscara cerrada), en todas las poses de ejemplo.

### 1.2 Colores
`block-colors.yml` (id → `#RRGGBB`) se genera **una vez** con `./gradlew :skinstatues:blockColors -Pmc=<jar>` desde el
`.jar` del cliente instalado del dueño (`%APPDATA%\.minecraft\versions\1.21.4\1.21.4.jar`): promedio de la textura
lateral de cada bloque (primer cuadro si es animada). Al repositorio solo van los números, **nunca** las texturas
(como las capturas de la 004). Bloques incluidos: sólidos, sin gravedad, sin redstone y de un color parejo (lana,
concreto, terracota, maderas, piedras y sus pulidas, deepslate, minerales, bloques de cobre **encerados**, etc.).
`BlockPalette` convierte a CIELAB (D65) y elige la menor ΔE76 entre los bloques permitidos; guarda el resultado por
color de 24 bits.

### 1.3 Red (H2, constitución II.4)
Solo si `network.mojang: true`. `MojangClient` usa `java.net.http.HttpClient` (asíncrono, tiempo de espera 8 s):
1. `https://api.mojang.com/users/profiles/minecraft/<name>` → uuid (204/404 = no existe);
2. `https://sessionserver.mojang.com/session/minecraft/profile/<uuid>` → propiedad `textures` (base64 → JSON con
   `SKIN.url`, `SKIN.metadata.model`, `CAPE.url`);
3. las PNG desde `textures.minecraft.net`. Cualquier otra URL (o `http`) se pasa a `https` y se rechaza si el host no es
   ese. Tope de 1 MB por respuesta; la PNG se valida (64×64, 64×32 o capa 64×32/22×17·n) antes de decodificarla.
Resultado en `cache/<name>.png`, `cache/<name>.json` (uuid, modelo, hora) y `cache/<name>-cape.png`; vale
`network.cache_hours` (24). Sin red, la caché vencida igual se usa si existe.

## 2. Contrato de configuración
Archivos en `plugins/SkinStatues/`, en inglés (ADR 0005). Se crean al primer arranque si no existen.

```yaml
# config.yml
min_height: 8              # blocks
max_height: 128
max_blocks: 400000         # per statue
offset_up: 2               # feet this many blocks above the player (so nobody gets trapped)
default_pose: standing
default_palette: all
overlay:
  enabled: true
  depth: 0.5               # skin pixels outside the first layer (vanilla hat is 0.5)
  alpha_threshold: 128     # 0-255: a second-layer pixel counts as solid from this alpha
cape: true
build:
  blocks_per_tick: 4000
  max_ms_per_tick: 10
network:
  mojang: false            # download skins and capes from Mojang by account name
  cache_hours: 24
```

```yaml
# poses.yml — angles in degrees, -180..180
# pitch: + raises an arm/leg forward (head: + looks down; body: + leans forward)
# yaw:   + turns to the statue's left        roll: + moves an arm/leg outward (head/body: + tilts to its left)
# elbow / knee: 0..150, how much the lower half bends (elbow forward, knee backward)
waving:
  head: { pitch: -5, yaw: 10 }
  right_arm: { pitch: 20, roll: 150, elbow: 40 }
  cape: { pitch: 8 }
```
Partes: `head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`, `cape`. Claves: `pitch`, `yaw`, `roll`;
`elbow` en brazos, `knee` en piernas. `standing` es la pose vacía y siempre existe (aunque `poses.yml` no la tenga).

```yaml
# palettes.yml — whitelist of block ids; "*" wildcard, "-id" excludes
all: ["*"]
wool: ["*_wool"]
concrete: ["*_concrete"]
terracotta: ["terracotta", "*_terracotta", "-*_glazed_terracotta"]
```
```yaml
# block-colors.yml — generated from block textures, editable
white_wool: "#E9ECEC"
```
`skins/<name>.png` (+ opcional `skins/<name>.yml` con `model: slim|classic` y `cape: <file>.png`, solo nombres de
archivo dentro de `skins/`). Viene `skins/Example.png` con su capa `Example-cape.png`: personaje original dibujado por
código (`ExampleSkinMain`, en pruebas), con las dos capas y la capa, nada de Mojang.

Comando `/statue` (alias `/statues`):
| Subcomando | Permiso |
|---|---|
| `build <name> <height> [pose] [--palette <p>] [--no-overlay] [--no-cape]` | `skinstatues.build` |
| `undo`, `cancel` | `skinstatues.build` |
| `remove <id>` | `skinstatues.remove` (y `.others` para las de otro) |
| `list [page]`, `info <id>`, `poses`, `palettes` | `skinstatues.build` |
| `reload`, `validate`, `selftest` | `skinstatues.admin` |
`validate` y `selftest` (también desde la consola) sirven a la compuerta 2: el primero lista los problemas de la
configuración; el segundo construye una estatua de la skin de ejemplo a altura 16 junto al punto de aparición del mundo
principal, comprueba que cada bloque es el planeado, la quita y comprueba que el terreno quedó igual.

`messages.yml`: textos en inglés con MiniMessage y marcadores `<name>`, `<id>`, `<blocks>`… El nombre del jugador ya
viene validado (`[A-Za-z0-9_]{1,16}`) y además se inserta con `Placeholder.unparsed` (II.2).

## 3. Hilos y rendimiento
- **Hilo principal:** comando (validar argumentos, posición, tope del mundo), `TickWorker` (cada tick, hasta
  `blocks_per_tick` bloques o `max_ms_per_tick`, lo que llegue antes; revisa el reloj cada 256 bloques), lectura del
  bloque anterior + `setBlockData(…, false)` (sin física: el agua o la arena vecinas no se mueven a mitad de obra).
- **Asíncrono:** red, lectura de PNG y de la caché, voxelización y paleta (`StatuePlan`: lista de posiciones relativas
  y bloque), escritura del diario y del índice (un solo hilo de E/S en orden).
- **Compilar al cargar (III.2):** poses en matrices, paletas en listas de bloques con su Lab; nada de YAML en el tick.
- **Topes (III.4):** `max_height`, `max_blocks`, alto del mundo, 1 MB por descarga, un trabajo a la vez en la cola
  (las demás estatuas esperan su turno; máx. 20 en espera).
- Sin oyentes de eventos: el plugin solo registra el comando y la tarea del tick, que se detiene sola con la cola vacía
  (III.1).

## 4. Persistencia
- `data/statues.yml`: índice (`next_id` y por id: jugador, altura, pose, paleta, mundo, base, orientación, quién, cuándo,
  bloques, estado `building|done|cancelled`). Escritura atómica: temporal + `ATOMIC_MOVE` (II.6).
- `data/statues/<id>.journal`: diario binario **solo de agregar**, escrito por tandas desde el hilo de E/S: cabecera
  (versión 1), entradas de diccionario (`BlockData` como texto) y registros `(dx, dy, dz, anterior, puesto)`. Se escribe
  **antes** de poner cada tanda en el mundo (el bloque anterior se lee en el mismo tick): si el servidor se cae a mitad,
  lo ya puesto está en el diario. Al leer, una cola truncada se ignora. (Implementado: la tanda se encola en el hilo de
  E/S en el mismo tick en que se pone; ver riesgos de verify.md.)
- `remove`/`undo` recorren el diario: si el bloque actual sigue siendo el puesto, ponen el anterior; si no, lo cuentan
  como «cambiado por otro» (H8 CA3). Al terminar se borra el diario y la entrada del índice.
- `/statue reload` no toca `data/` (I.4).

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación | Una respuesta falsa hace que se baje una «skin» de otro sitio | Solo `https` y los tres hosts de Mojang; nombre validado antes de armar la URL |
| Manipulación | `skins/../../server.properties` o un `.yml` de skin con ruta | Nombres por regex; `cape:` solo nombre de archivo en `skins/`; rutas resueltas y comprobadas dentro de la carpeta |
| Manipulación | Una estatua pisa una casa | Diario de bloques anteriores; `remove` los devuelve; sin física al poner y quitar |
| Repudio | ¿Quién puso esta estatua? | El índice guarda uuid y nombre de quien la construyó y la hora; `list`/`info` lo muestran |
| Divulgación | Se filtra la IP del servidor a terceros | La red está apagada por defecto; solo se habla con Mojang |
| Denegación de servicio | Estatua enorme o muchas a la vez bajan el TPS; PNG gigante (bomba de descompresión) | Topes de altura, bloques y cola; presupuesto por tick; tamaño de respuesta y dimensiones de la PNG antes de decodificar; cálculo fuera del hilo principal |
| Elevación de privilegios | Un jugador construye o quita estatuas de otros | Todos los permisos `default: op`; `remove.others` aparte; autocompletado filtrado |

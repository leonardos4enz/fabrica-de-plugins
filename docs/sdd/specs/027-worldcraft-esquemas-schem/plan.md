# Plan 027 — Esquemas `.schem` en Worldcraft

## 1. Arquitectura
Paquete nuevo `dev.leo.worldcraft.schem`, **Java puro** (se prueba sin servidor), escrito desde la
[especificación pública de Sponge](https://github.com/SpongePowered/Schematic-Specification) (v2 y v3) y la del formato NBT
del juego. No se abre código de WorldEdit ni de FAWE (GPLv3) ni se enlaza su API (investigación §4). WorldEdit solo se usa
como **herramienta de prueba** en el servidor de la suite (ya aprobado por el dueño en la 012), para comprobar que los dos
leen los archivos del otro.

| Clase | Qué hace |
|---|---|
| `schem.Nbt` | Etiquetas NBT como tipos de Java (`Map` ordenado, `TagList`, `Byte`… `long[]`); lector con topes (profundidad 512, largos contra lo que queda del archivo) y escritor; `gunzip` con tope de bytes descomprimidos (contra bombas de compresión) y `gzip` |
| `schem.VarInts` | Codifica y decodifica el `varint[]` de Sponge (7 bits por byte, el bit alto dice «sigue») |
| `schem.Schematic` | El esquema leído: medidas, `Offset`, `DataVersion`, paleta (`String[]`), índices por posición (`x + z·W + y·W·L`), datos de bloque por índice, entidades (solo cuántas), nombre, autor y fecha |
| `schem.SchemFormat` | `read(bytes, topes)` para v1, v2 y v3 y `write(Schematic)` en v3 |
| `schem.TileData` | Los datos de bloque en el portapapeles en su forma estándar: `'N'` + `DataVersion` + `{Id, Data}` en NBT; de qué tipo son (contenedor, cartel, otro) |
| `schem.TextNbt` | Un texto del juego entre JSON (Adventure/Gson) y NBT (los carteles de 1.21.5+ guardan el texto como NBT) |
| `schem.SchemBlocks` | Cada estado de la paleta a un estado de este servidor: tal cual, por un nombre cambiado conocido, quitando propiedades que no existen o, si nada sirve, aire; con el informe |
| `schem.SchemConvert` | Esquema ↔ portapapeles: `toClipboard` (con la tabla que da `SchemBlocks`) y `fromClipboard` |
| `schem.SchemFiles` | La carpeta propia y las de solo lectura: nombres seguros, resolver sin salir de la carpeta, listar, escribir de forma atómica, papelera y su limpieza |
| `edit.TaskJob` | Un trabajo del planificador de la 025 que hace pasos sueltos dentro del presupuesto por tick (convertir datos de bloque con la API de Paper sin pasar del tope de 8 ms del plugin) |
| `paper.TileNbt` | Lo que necesita a Paper: ítems ↔ NBT con `ItemStack.serializeAsBytes`/`deserializeBytes` (el juego actualiza los ítems viejos con su `DataVersion`), carteles ↔ NBT, y poner un `'N'` en un bloque |
| `paper.Schematics` | Une todo: los cuatro subcomandos, el hilo `Worldcraft-Schematics` y el paso por el hilo principal |

### 1.1 Cargar (`/wc schem load`)
1. Hilo principal: nombre válido, una operación de esquemas a la vez por jugador.
2. Hilo `Worldcraft-Schematics`: resolver el archivo (carpeta propia y luego las de solo lectura), tope de KB, leer,
   descomprimir con tope, NBT, versión, medidas contra `max_blocks` **antes de reservar**, paleta, `varint`, datos de
   bloque y entidades → `Schematic`.
3. Hilo principal: `SchemBlocks` traduce la paleta (pocas entradas) a números de la `StateTable`.
4. Hilo `Worldcraft-Schematics`: los índices → estados del portapapeles (es el mismo orden: `(y·L + z)·W + x`), los datos
   de bloque quedan como `'N'` sin convertir.
5. Hilo principal: el portapapeles de la sesión y el mensaje con el informe.
Los datos de bloque se convierten **al pegar**, dentro del trabajo del motor y de su presupuesto: `PaperWorld.restoreTile`
entiende `'N'` además de los `'C'`/`'S'` de la 025 (contenedor: los ítems por `Slot`; cartel: las dos caras, brillo y cera;
otro: no se pone, se cuenta al cargar).

### 1.2 Guardar (`/wc schem save`)
1. Hilo principal: portapapeles presente, nombre válido, permiso de `--overwrite`. Si está girado o volteado, el estado
   girado de cada estado distinto (memorizado, con `BlockData.rotate`); el texto de cada estado distinto para la paleta.
2. `TaskJob` en el planificador: los datos de bloque que no están en la forma estándar del servidor (`'C'`/`'S'` copiados
   del mundo, o `'N'` de un esquema de otra versión) pasan a `'N'` con la versión del servidor, por pasos dentro del
   presupuesto por tick.
3. Hilo `Worldcraft-Schematics`: girar el arreglo (`Clipboard.baked`), paleta e índices, `varint`, NBT v3, GZip; escribir en
   un temporal y moverlo (`ATOMIC_MOVE`); si ya existe y no hay `--overwrite`, nada se toca.
4. Hilo principal: mensaje.

### 1.3 Dónde se pega (Offset)
El portapapeles de la 025 guarda la esquina mínima relativa a quien copió. Al guardar, `Offset` = esa esquina (como pide
v3: se suma a la posición donde se pega). Al leer: v3 usa `Offset`; v2 y v1 usan `Metadata.WEOffsetX/Y/Z` si están (los
archivos de WorldEdit 7.0–7.2 guardan ahí lo relativo y en `Offset` la posición absoluta del mundo) y si no, `Offset`.

### 1.4 Formato escrito (v3)
```
"" (raíz) { Schematic: { Version: 3, DataVersion: <del servidor>, Width/Height/Length: short sin signo,
  Offset: int[3], Metadata: { Name, Author, Date: long (ms), Worldcraft: { Version } },
  Blocks: { Palette: { "minecraft:stone": 0, … }, Data: byte[] (varint),
            BlockEntities: [ { Pos: int[3], Id: "minecraft:chest", Data: { Items: [...] } }, … ] } } }
```
Contenedor: `Items` con `Slot` (byte) y el ítem como lo guarda el juego (`id`, `count`, `components`). Cartel:
`front_text`/`back_text` con `messages` (4), `has_glowing_text`, y `is_waxed`; cada línea en JSON (texto) hasta 1.21.4
y en NBT desde 1.21.5 (`DataVersion` 4325). Al leer también `Text1..Text4` (antes de 1.20).

### 1.5 Nombres cambiados entre versiones
`grass` → `short_grass` (1.20.3), `grass_path` → `dirt_path` (1.17), `chain` ↔ `iron_chain` (1.21.9) y la vuelta de
cada uno: solo se prueban si el nombre original no existe en este servidor. La lista vive en `SchemBlocks`; agregar uno
es una línea.

## 2. Contrato de configuración
`config.yml` (sección nueva; un archivo de la 0.2.0 sin ella carga con estos valores):
```yaml
schematics:
  max_file_kb: 10240        # tamaño máximo de un .schem (1..1048576)
  max_blocks: 5000000       # bloques (ancho × alto × largo) de un esquema que se carga (1..100000000)
  trash_days: 7             # lo borrado se queda en schematics/.trash/ estos días (0..3650; 0 = vaciar al arrancar)
  # Carpetas de solo lectura (relativas a la carpeta del servidor): las de WorldEdit y FAWE de fábrica.
  extra_folders: [plugins/WorldEdit/schematics, plugins/FastAsyncWorldEdit/schematics]
```
El tope descomprimido es `4 × max_blocks + 32 MB` (lo que ocupan los índices en el peor caso más los datos de bloque).
Carpeta propia: `plugins/Worldcraft/schematics/` (fija).

Comandos (`/wc schem …`, alias `/wc schematic …`):
| Subcomando | Permiso |
|---|---|
| `schem list [página] [filtro]` | `worldcraft.schematic` |
| `schem load <nombre>` | `worldcraft.schematic` |
| `schem save <nombre> [--overwrite]` | `worldcraft.schematic` (y `worldcraft.schematic.delete` para `--overwrite`) |
| `schem delete <nombre>` | `worldcraft.schematic.delete` |

Nombres: segmentos `[A-Za-z0-9_+-][A-Za-z0-9_.+-]{0,63}` separados por `/` (hasta 4), sin `.schem` (si se escribe, se
quita). En la consola, los mismos comandos (el portapapeles de la consola).

## 3. Hilos y rendimiento
- **Hilo principal:** comandos, traducir la paleta, girar el estado de cada estado distinto, poner el portapapeles; los
  datos de bloque con la API de Paper, solo dentro del planificador (pegar, `TaskJob`), con el tope de 8 ms de la 025.
- **Hilo `Worldcraft-Schematics`** (uno, daemon, aparte del de historial para no atrasar los diarios): disco, GZip, NBT,
  `varint`, armar y girar arreglos. Nada de Bukkit.
- **Topes (III.4):** `max_file_kb`, `max_blocks` (de la cabecera, antes de reservar), descomprimido, profundidad del NBT,
  largos de listas y arreglos contra lo que queda del archivo, 5 000 archivos al listar, una operación por jugador.
- **Memoria:** un esquema de 5 M de bloques: índices (20 MB) y el portapapeles (20 MB) mientras se carga; queda el
  portapapeles.

## 4. Persistencia
- Escritura atómica (temporal + `ATOMIC_MOVE`, II.6). Borrar = mover a `.trash/<nombre>-<fecha>.schem`; al arrancar se
  vacía lo de más de `trash_days`.
- Las carpetas de `extra_folders` nunca se escriben.
- `reload` no toca `schematics/`.

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación | Alguien guarda un esquema con el nombre de otro y lo pisa | Un nombre que existe no se pisa sin `--overwrite` (permiso de borrar) |
| Manipulación | Un nombre `../../server.properties` o un enlace en la carpeta escribe o lee fuera | Nombres con lista blanca, sin `..`, `\` ni absolutos; la ruta real debe quedar dentro de la carpeta (también la de la carpeta del archivo al escribir) |
| Manipulación | Un esquema trae bloques de comandos, portales o roca madre | Pegar pasa por `blocked_blocks` del grupo como cualquier pegado (025) |
| Manipulación | Un texto del archivo (nombre, autor, bloque desconocido) se lee como MiniMessage | Todo valor se inserta escapado (II.2) |
| Repudio | ¿Quién guardó esto? | `Metadata.Author` y la fecha en el archivo; el pegado queda en el historial de la 025 |
| Divulgación | Leer archivos del servidor con `load` | Solo `.schem` dentro de las carpetas de esquemas |
| Denegación de servicio | Un archivo de 1 GB, una bomba de GZip, un NBT con millones de niveles, una cabecera de 65 535³ bloques, listas con largos mentirosos | Tope de KB antes de leer, tope descomprimido, profundidad 512, largos contra lo que queda, `max_blocks` antes de reservar; todo fuera del hilo principal |
| Denegación de servicio | Miles de cofres que convertir de una vez | La conversión va por pasos en el planificador (presupuesto por tick) |
| Denegación de servicio | Llenar el disco guardando | Permiso propio `worldcraft.schematic`; el portapapeles ya tiene el tope del grupo; tope de `max_blocks` |
| Elevación de privilegios | Usar esquemas sin permiso | `worldcraft.schematic` y `worldcraft.schematic.delete`, `default: op`; ayuda y autocompletado filtrados |

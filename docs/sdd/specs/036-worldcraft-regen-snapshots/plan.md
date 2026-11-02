# Plan 036 — `regen` y copias de seguridad (Worldcraft 0.12.0)

Spec: [spec.md](spec.md) · Respuestas del dueño: `regen` y restaurar desde copias, sin las criaturas de la copia; copias en
carpetas y `.zip`; sin `snapshot create`. Solo API de Paper 1.21.4, sin NMS.

## 1. Diseño

La idea: las dos piezas terminan en un **portapapeles** del tamaño de la caja de la selección (solo las posiciones de la
selección; lo demás «sin leer», que `paste` salta) y se pegan con `PasteSource` en el mismo lugar, con el aire
(`--air`) y los biomas como `paste --biomes` (033). Así la vista previa, los límites (también los bloques prohibidos con
los estados del portapapeles), la máscara global, las tandas de 8 ms, el historial y `undo` son los de siempre, sin
código nuevo en el motor.

### 1.1 `regen` (`paper/Regen`)
- **Mundo temporal.** `WorldCreator.copy(mundo)` (semilla, ambiente, tipo, generador y proveedor de biomas del mundo —
  también los de otro plugin—, estructuras) con el nombre `worldcraft_regen_<n>`, `keepSpawnLoaded(FALSE)` (no se carga el
  *spawn*: crear el mundo no frena el servidor por los chunks) y, si se pide, otra semilla o un `BiomeProvider` de un solo
  bioma. Antes de crearlo se hace la carpeta con un archivo de marca (`worldcraft-temporary-world.txt`); después
  `setAutoSave(false)` y las reglas `doMobSpawning`, `doDaylightCycle`, `doWeatherCycle`, `randomTickSpeed 0` en apagado
  (en 1.21.4 con los nombres viejos; nada se mueve mientras está cargado).
- **Lectura.** El mismo camino que `copy --biomes` (`Edits.readClip`, nuevo: lee al portapapeles sin tocar el del
  jugador): `PaperWorld` pide cada chunk con `getChunkAtAsync` y en el mundo temporal eso **genera** el chunk fuera del
  hilo del servidor; la lectura espera sin gastar el tick. Los cofres sin abrir de las estructuras guardan su tabla de
  botín (`PaperWorld.tile` ahora devuelve la tabla de un `Lootable` vacío, como las plantillas de la 033: también lo
  aprovechan `copy`, `cut` y `stack`).
- **Obra.** `Op("regen", "seed …[ biome …]", mundo, caja, volumen, PasteSource(clip, minX, minY, minZ, aire=true), …)`
  con `BiomeWork` si `--biomes` o un bioma.
- **Uno a la vez.** Un `Regen` por plugin: si hay uno generando, «another regen is running». El mundo temporal queda
  cargado `regen.keep_seconds` (de fábrica 60) por si la siguiente `regen` usa la misma semilla, el mismo mundo y el mismo
  bioma; si no, se descarga (`unloadWorld(w, false)`) y su carpeta se borra en el hilo de E/S. Al arrancar se borran las
  carpetas `worldcraft_regen_*` **con la marca** que quedaron de un apagón; al apagar, se descarga y se borra.
- **Topes.** El volumen cuenta para el tope del grupo; los chunks de la caja, hasta `regen.max_chunks`.
- **Semilla** (`RegenSeed`, Java puro): ninguna = la del mundo; `random`/`-r` = al azar; un entero = ese; otra palabra
  = `String.hashCode()` (como el juego con `level-seed`). `-b` = `--biomes`.

### 1.2 Copias (paquete `snapshot`, Java puro)
- `SnapshotRepo.list(carpeta, mundo)`: cada carpeta o `.zip` de `snapshots.folder` que tenga el mundo
  (`<copia>/<mundo>/region/` o, en un `.zip`, `<mundo>/region/` o `<algo>/<mundo>/region/`). Fecha: la del nombre si la
  tiene (`2026-11-02`, `2026-11-02-14-30`, `2026-11-02_14-30-00`, `2026-11-02T14.30`…), si no la de modificación. Orden: la
  más nueva primero. `before`/`after` con la fecha del jugador (`2026-11-02` o `2026-11-02 14:30`, hora del servidor).
- `RegionSource` (dos formas, la misma interfaz `ChunkReader`): una carpeta (`RandomAccessFile`: solo la cabecera de 8 KiB
  y los sectores del chunk) o una entrada de un `.zip` (`ZipFile`: el archivo de región entero en memoria, hasta
  `MAX_REGION_BYTES` = 64 MiB, cortando al leer aunque la entrada diga otro tamaño). Formato del juego: posición de 3
  bytes + cantidad de sectores de 1 en la cabecera; en el sector, largo de 4 bytes y compresión de 1 (1 gzip, 2 zlib, 3
  nada, 4 LZ4 = no soportada; +128 = el chunk está en `c.<x>.<z>.mcc` al lado). Lo descomprimido, hasta 32 MiB por chunk
  (una bomba de compresión se corta).
- `ChunkNbt.decode(raw)`: `DataVersion` (< 2860 = de antes de 1.18 → «old»), `Status` (`full`/`minecraft:full`; si no →
  sin terminar), `sections` (`Y`, `block_states` con `palette` de `Name` + `Properties` y `data` empaquetado: `bits =
  max(4, ⌈log2 n⌉)`, `64 / bits` valores por `long` sin partir entre dos; sin `data` = toda la sección el primero),
  `biomes` (`palette` de nombres, `bits = ⌈log2 n⌉`, celdas de 4×4×4), `block_entities` (`id`, `x`, `y`, `z` y el resto son
  los datos). Un valor fuera de la paleta o un largo equivocado → el chunk se cuenta como roto y se deja.
- `Restore.read(región, lector, biomas)` (en el hilo de esquemas): por cada chunk de la caja, lee y decodifica; arma una
  paleta global de textos de estado (`minecraft:oak_log[axis=y]`, propiedades en orden), los estados de la caja como
  índice en esa paleta (−1 = sin restaurar: fuera de la región, chunk que falta, viejo, sin terminar o roto), los datos de
  bloque como `TileData(DataVersion del chunk, id, datos)` (la forma de los `.schem`: las versiones viejas las actualiza el
  juego) y, si se piden, los biomas de cada bloque. Cuenta por paleta (para el informe), chunks que faltan, viejos, sin
  terminar, rotos y con LZ4.
- En el hilo principal: `SchemBlocks.resolve` (las mismas traducciones de nombres de la 027); lo desconocido queda en −1
  (**se deja**, no aire) y se cuenta; los biomas que el servidor no tiene, sin bioma. Después `Clipboard.of(…)` y la obra
  `Op("restore", "<copia>", …)` como en 1.1.
- Sesión: `Session.snapshot` (el nombre elegido, por jugador). Sin elegir, la más nueva («Using the newest backup: …»).

### 1.3 Comandos (`command/RestoreCommands`)
- `regen [semilla|random] [bioma] [--biomes] [--preview]` (también `-b`, `-r`), permiso `worldcraft.regen`.
- `snapshot|snap list [página]`, `use <nombre|latest>`, `sel <número>`, `before <fecha>`, `after <fecha>`;
  `restore [copia] [--biomes] [--preview]` (también `-b`), permiso `worldcraft.snapshot`.
- Ayuda (`help.<sub>` con ejemplo), autocompletado (copias, `latest`, `random`, biomas, opciones), mensajes en
  `messages.yml`. Desde la consola, sobre la selección de la consola (como las demás obras).

## 2. Contrato de configuración (`config.yml`)
```yaml
# Spec 036: /wc regen - the selection back to how the world generator made it.
regen:
  max_chunks: 256         # most chunks one regen may generate (1..4096)
  keep_seconds: 60        # the temporary world stays loaded this long for the next regen (0..600), then it is deleted

# Spec 036: /wc snapshot and /wc restore - put back what a backup of the world had.
snapshots:
  folder: ""              # where your backups are. Each backup is a folder or a .zip with the world folders inside,
                          # like backups/2026-11-02-14-30/world/region/r.0.0.mca. Empty = off. Relative to the server folder.
  max_chunks: 1024        # most chunks one restore may read (1..16384)
```
Un valor fuera de rango avisa y usa el de fábrica (constitución I.3); una clave desconocida avisa con «did you mean»;
una carpeta que no existe avisa en `validate` (no es un error: el dueño la puede crear después). Un `config.yml` de la
0.11.0 sin estas secciones carga igual. `plugin.yml`: permisos `worldcraft.regen` y `worldcraft.snapshot` (`default: op`).

## 3. STRIDE
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| **T**ampering: `/wc snapshot use ../../world`, `restore ..\plugins` | Leer archivos fuera de la carpeta de copias | `use` y `restore` solo aceptan un nombre **de la lista** (lo que hay en la carpeta, sin `/`, `\` ni `..`); el mundo es el del jugador (un nombre de mundo cargado); la ruta resuelta tiene que quedar dentro de la carpeta |
| **T**: borrar una carpeta que no es el mundo temporal (un apagón, un nombre que coincide) | Perder un mundo del dueño | Solo se borra una carpeta `worldcraft_regen_*` **con la marca** que Worldcraft escribe antes de crearla, y nunca un mundo cargado que no sea el temporal |
| **T**: `regen`/`restore` sobre construcciones ajenas | Daño | Solo en la selección; las puertas de toda obra (mundos, tope, bloques prohibidos con todos los estados del portapapeles, `gmask`, historial, `undo`); permisos aparte, de fábrica solo OP |
| **I**nformación: restaurar una zona de una copia vieja (un cofre de otro) | Ver lo que tenía otro | Permiso `worldcraft.snapshot` (OP) y los mundos del grupo; queda en el historial con la copia usada |
| **D**enegación: `.zip` o chunk «bomba», región enorme, NBT profundo | Memoria, hilo colgado | 64 MiB por región dentro de un `.zip`, 32 MiB por chunk descomprimido, el lector NBT de la 027 (profundidad 512, largos contra lo que queda), `max_chunks`; todo en el hilo de esquemas, fuera del principal |
| **D**: `regen` grande, muchas seguidas | Memoria de un mundo más, generación lenta | Una a la vez; `regen.max_chunks`; el volumen cuenta para el tope; los chunks se generan fuera del hilo (`getChunkAtAsync`) y la escritura va por el motor (8 ms por tick); el mundo temporal no carga el *spawn*, no guarda y se descarga a los `keep_seconds` |
| **D**: un mundo temporal que dispara otros plugins (Multiverse, mapas) | Ruido, archivos de más | Nombre fijo con prefijo (`worldcraft_regen_`), documentado en el README para excluirlo; se descarga y se borra |
| **E**levación: un jugador con `worldcraft.use` regenera o restaura | Deshacer el trabajo de otros a lo grande | `worldcraft.regen` y `worldcraft.snapshot` aparte, `default: op` |
| **R**epudio | — | Cada obra queda en el historial con el detalle (semilla y bioma, o el nombre de la copia) |

## 4. Pruebas
- Unitarias (`SnapshotTest`, con un escritor de `.mca` de prueba): la paleta empaquetada (4, 5 y 9 bits, sin partir
  entre `long`s, sección de un solo estado sin `data`), los biomas por celda, los datos de bloque, `Status` sin terminar,
  `DataVersion` viejo, las cuatro compresiones (gzip, zlib, nada, LZ4 → no soportada) y el `.mcc` de al lado, una cabecera
  cortada y un chunk roto (no revientan), una bomba de compresión cortada, `.zip` (con y sin carpeta de más) y carpetas,
  fechas desde el nombre y desde el archivo, `before`/`after`, nombres con `..`; `Restore.read` en una selección
  cilíndrica (solo adentro), un chunk que falta (−1) y la paleta global; la semilla (`random`, número,
  palabra como el juego), los chunks de la caja, qué carpetas se borran (solo con la marca). `ConfigTest` (dos secciones),
  `CommandTest` (ayuda y mensajes).
- Compuerta 2: `save-all flush` y `selftest`: el chunk del *spawn* leído del `.mca` que escribió Paper es igual al del
  mundo (bloque por bloque); un mundo temporal plano generado en el momento tiene roca madre y pasto donde van, y cuánto
  tardó en crearse; la carpeta se borra. Comandos desde la consola.
- Suite 3a: `regen` en el aire (la piedra puesta vuelve a ser aire; mundo plano), con `--biomes` sobre un desierto puesto
  con `setbiome` (vuelve a llanura), `undo`; con el suelo incluido (una caja con piedra en la capa de pasto vuelve a pasto
  y tierra; `finally` repara la capa); restaurar desde una copia en carpeta y en `.zip` que hace la suite (`save-all
  flush` y copiar los `.mca`): una construcción con un cofre con diamantes en el aire, borrada, vuelve con su contenido;
  `snapshot list|use|sel|before|after`, un nombre mal escrito sugiere; Beto sin permiso.

# Plan 031 — Entidades en Worldcraft (0.7.0)

Spec: [spec.md](spec.md) · Respuestas del dueño: todo entra; `copy` sin entidades salvo `--entities`; `butcher` como
WorldEdit; `worldcraft.butcher` y `worldcraft.remove`. Sin NMS: solo API de Paper 1.21.4.

## 1. Diseño

### 1.1 Lo que da Paper (verificado con `javap` contra `paper-api-1.21.4`)
| Necesidad | API |
|---|---|
| La entidad entera, con lo que lleva montado | `Bukkit.getUnsafe().serializeEntity(e, EntitySerializationFlag.PASSENGERS)`: NBT binario con `DataVersion` |
| Recrearla (el juego actualiza datos viejos) | `deserializeEntity(bytes, world, preserveUUID=false, preservePassengers=true)` + `Entity#spawnAt(Location)` |
| Girar la pared de marcos y cuadros | `Hanging#getFacing()` / `setFacingDirection(cara, true)` |
| Encontrarlas | `Chunk#isEntitiesLoaded()`, `Chunk#getEntities()`, `Bukkit.getEntity(UUID)` |

`createSnapshot`/`getAsString` dan SNBT de texto: harían falta un lector y un escritor de SNBT para los `.schem`. Con
`serializeEntity` el NBT ya es binario y lo lee el `Nbt` de la 027: se ahorra esa pieza.

### 1.2 Java puro (paquete `entity`)
- `EntityCopy(type, x, y, z, yaw, pitch, nbt, contains, turn)`: una entidad copiada. Posición y mirada relativas al
  origen del portapapeles (o absolutas en el diario); `nbt` el binario de Paper; `contains` los tipos que lleva (ella, lo
  montado y el bloque de un `falling_block`) para los prohibidos; `turn` la transformación que falta aplicar a la cara de
  un colgante. `transformed(Transform)`: posición `M·(p − ½) + ½` (el mismo giro que el bloque que la contiene), mirada
  girando el vector `(−sin yaw, cos yaw)` y la inclinación negada si se voltea en y.
- `Kind` (jugador, hostil, mascota, npc, gólem, animal, ambiente, agua, con nombre, soporte, ítem, proyectil, xp, bote,
  carro, tnt, bloque que cae, cuadro, marco) y `EntityInfo(uuid, type, x, y, z, kinds)`: lo que el lado de Paper dice de
  cada una (con `instanceof`).
- `Butcher(flags)`: decide con los `Kind` (orden: jugador nunca; soporte → `--armorstands`; npc → `--npcs`; con nombre →
  `--named`; mascota; hostil → sí; gólem; agua; ambiente; animal; otra criatura → `--friendly`). `RemoveType`: los tipos
  de `remove` y el id de una entidad.
- `EntityWorld` (interfaz, como `EditWorld`): `ready(cx, cz)` (pide el chunk y sus entidades sin bloquear), `list(cx, cz)`,
  `copy(uuid)`, `remove(uuid)`, `spawn(EntityCopy)` → uuid nuevo. En Paper `PaperEntities`; en las unitarias, en memoria.
- `EntityStage` (una etapa por tandas, mira el reloj en cada entidad, lección 025 F-01):
  - `Scan(region, filtro, quitar, tope)`: chunk por chunk espera a que estén cargados, lista, copia (y quita); lo
    copiado queda en `result()` y cada quitada va al diario.
  - `Spawn(lista)`: crea cada una (esperando su chunk) y la anota en el diario.
  - `Kill(uuids)`: quita las que sigan existiendo (deshacer); las que no, `skipped`.

### 1.3 El motor (`EditJob`, `Edits`)
- `EditJob` gana `before` y `after` (`EntityStage`, opcionales): corre `before`, luego los bloques, luego `after`, todo en
  el mismo presupuesto por tick y el mismo diario. Orden pensado para los colgantes: lo que se quita va **antes** de
  tocar los bloques (un marco sin pared se cae) y lo que se crea **después** (ya hay pared). La vista previa no corre
  etapas.
- `Edits.Op` gana `entities` (`EntityWork`: las dos etapas, cuántas se esperan crear y la nota). `request` revisa
  `max_entities` y `blocked_entities` antes de pedir confirmación. `copy` corre una etapa `Scan` sin quitar en el trabajo
  de lectura.
- `butcher` y `remove`: una obra sin bloques (fuente vacía con la caja del radio) y una etapa `Scan` que quita.

### 1.4 El diario (`Journal`)
Registros nuevos (los viejos siguen igual; un diario de la 030 se lee igual):
| Tipo | Contenido |
|---|---|
| `ENTITY_OUT` (5) | uuid, posición, mirada, tipo, NBT: una entidad que la obra quitó |
| `ENTITY_IN` (6) | lo mismo de una que creó (copiada **después** de crearla: la cara y la mirada ya puestas) |
| `REMAP` (7) | uuid viejo → nuevo: deshacer o rehacer recrea con otro UUID y lo agrega al final del diario |

Deshacer: `Kill(IN)` → bloques → `Spawn(OUT)` (+ `REMAP`). Rehacer: `Kill(OUT)` → bloques → `Spawn(IN)` (+ `REMAP`).
Los uuid se siguen por la cadena de `REMAP`. `Journal.reopen(file)` agrega al final sin reescribir la cabecera.

### 1.5 Esquemas
- Guardar (v3): `Entities` = `{Pos: [x, y, z] relativa a la esquina mínima, Id, Data}`; `Data` = el NBT sin `UUID` ni
  `DataVersion` (el esquema lleva el suyo), con `Pos` y `TileX/Y/Z` (o `block_pos`) relativos a quien copió y coherentes:
  WorldEdit pega un colgante con la diferencia entre los dos (verify F-01). Al cargar, `TileX/Y/Z` va junto a la `Pos`
  nueva (F-02).
- Cargar v2 (`{Pos, Id, …datos}`) y v3 (`{Pos, Id, Data}`): se arma el NBT con `id` y el `DataVersion` del esquema, sin
  `UUID`, y se lee con `deserializeEntity` al pegar (el juego lo actualiza). La mirada sale de `Rotation`. Lo que no se
  puede leer se cuenta.
- La convención de `Pos` se comprueba en la suite con WorldEdit 7.3.19 (guardar con uno, cargar con el otro, en los dos
  sentidos).

## 2. Contrato de configuración
```yaml
entities:
  butcher_radius: 32      # /wc butcher sin radio (1..max_radius)
  max_radius: 128         # el radio más grande de butcher y remove (1..512)
limits:
  default:
    max_entities: 200     # entidades que una obra puede crear o quitar (0 = ninguna)
    blocked_entities: [command_block_minecart, spawner_minecart, tnt, tnt_minecart, falling_block, wither, ender_dragon]
  builder:
    max_entities: 2000
  admin:
    max_entities: 20000
    blocked_entities: []
```
`/wc validate` avisa un valor fuera de rango y un tipo de entidad que no existe (con «did you mean»). Permisos nuevos:
`worldcraft.butcher`, `worldcraft.remove` (de fábrica OP).

## 3. STRIDE
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| **E**levación: un `.schem` (o un portapapeles) con un carro con bloque de comandos o un `falling_block` de bloque de comandos | Comandos de consola, bloques prohibidos colados | `blocked_entities` revisa la entidad, lo montado y el bloque que cae; el bloque de un `falling_block` pasa también por `blocked_blocks` |
| **D**enegación: pegar 100 000 entidades o un `stack` de 1000 copias con un corral | Tick trabado, servidor caído | `max_entities` por grupo antes de empezar; etapas por tandas con el reloj en cada entidad; `butcher` corta en el tope |
| **D**enegación: esquema con NBT de entidad enorme o muy anidado | Memoria | Tope de archivo (027), `Nbt.MAX_DEPTH`, tope por entidad de 2 MB en el diario (como los datos de bloque) |
| **T**ampering: deshacer recrea entidades sobre lo que otro cambió | Duplicados | `Kill` solo quita la entidad con ese uuid (seguida por `REMAP`) y del mismo tipo; si no está, la salta y la cuenta |
| **T**ampering: `butcher` mata mascotas, aldeanos o NPC de otros | Pérdidas | De fábrica solo hostiles; con nombre, mascotas, npc (y los de Citizens, `metadata NPC`) solo con su opción; nunca jugadores; se deshace |
| **R**epudio | ¿Quién borró el corral? | Va al historial con dueño, tipo y conteo (`/wc history`), igual que los bloques |
| **I**nformación | — | Nada nuevo sale del servidor |
| **S**uplantación | — | Permisos de Bukkit; la consola usa `--at`/`--world` |

## 4. Pruebas
- Unitarias (mundo en memoria): `EntityCopy.transformed` (girar 90/180/270, voltear, la cara, ida y vuelta), `Butcher` con
  cada opción, `RemoveType` y su «did you mean», `Journal` con los registros nuevos y la cadena de `REMAP`, un diario de la
  030 que se sigue leyendo, `EditJob` con etapas (orden quitar → bloques → crear, cancelar a mitad), copiar/pegar/cortar/
  mover/apilar con entidades en el mundo de prueba, deshacer y rehacer (con la entidad que ya no existe), límites y
  prohibidos (también montados), esquemas v2 y v3 con entidades (ida y vuelta), configuración.
- Compuerta 2: `selftest` con un soporte con armadura y un marco con ítem: copiar, pegar girado, deshacer.
- 3a: escenarios con bots y WorldEdit 7.3.19 (abajo, en tasks).

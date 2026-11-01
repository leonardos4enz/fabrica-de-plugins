# Plan 022 — Poses incorporadas, herencia y espejo, y coordenadas en el comando

Sobre el diseño de la [021](../021-skinstatues-nucleo/plan.md): el voxelizador, la paleta, el diario y la cola no
cambian. Cambian la carga de poses (`config`), el comando (`command`) y el lugar donde se construye (`statue`).

## 1. Poses

### 1.1 Incorporadas + propias (H1 CA2–CA4)
- `src/main/resources/builtin-poses.yml`: las 28 poses del plugin (las 6 de la 0.1.0 y 22 nuevas). **No** se copia
  como archivo editable: `LoadedConfig.load` lo recibe ya leído del jar (`getResource`) y lo carga primero.
- `poses.yml` del dueño se carga encima: un nombre nuevo agrega una pose; uno que ya existe **reemplaza** la
  incorporada entera (no se mezcla ángulo por ángulo: para eso está `extends`). Si la pose del dueño es idéntica a la
  incorporada (el `poses.yml` de la 0.1.0 trae las 6 de entonces), no cuenta como reemplazo.
- Al arrancar y en cada `reload`, `SkinStatues` escribe `builtin-poses.yml` en la carpeta del plugin si su contenido
  difiere del jar (escritura atómica, II.6). Su cabecera dice que se reescribe y que una pose se cambia copiándola a
  `poses.yml`. Ese archivo de la carpeta **no se lee** (es solo referencia): editarlo no tiene efecto, lo dice la
  cabecera.
- `LoadedConfig` gana `poseOrigins: Map<String, Origin>` con `BUILTIN`, `CUSTOM` u `OVERRIDE` para `/statue poses`.
- `poses.yml` nuevo: la guía en comentarios (tabla de ángulos con ejemplo, convenciones de signos, `extends`,
  `mirror`, dónde ver las incorporadas) y dos poses propias de ejemplo (`my_wave` con `extends`, `my_point_left` con
  `extends` + `mirror`). Un servidor que ya tiene `poses.yml` lo conserva tal cual.

### 1.2 `extends` y `mirror` (H2)
`PoseLoader` pasa a dos etapas:
1. **Leer** cada archivo a definiciones crudas: `RawPose(name, file, extendsName, mirror, Map<Part, double[4]>)`, donde
   un ángulo no escrito es `NaN` (hereda). Errores de parte, clave, número y rango como en la 0.1.0. Claves reservadas
   en la pose: `extends` (texto) y `mirror` (booleano); `mirror` sin `extends` es error.
2. **Resolver** sobre el mapa unido (incorporadas, luego las del dueño encima), con memo y pila para ciclos:
   `resolve(p) = override(mirror ? flip(resolve(padre)) : resolve(padre), propios(p))`; sin `extends`, el padre es
   `standing` (todo 0). Profundidad máxima 8 (III.4). Si el padre falta, tiene errores o hay ciclo, la pose se omite con
   el error en su archivo y ruta (`<pose>.extends`).

`flip`: intercambia `right_arm`↔`left_arm` y `right_leg`↔`left_leg`; en todas las partes niega `yaw`; en `head`,
`body` y `cape` niega también `roll` (en brazos y piernas `roll` ya es «hacia afuera» de cada lado: se conserva);
`pitch` y el doblez quedan igual. Prueba: con una skin de un solo color, el espejo de la estatua de una pose
asimétrica es la estatua de su pose `mirror` (bloque por bloque, reflejando x).

### 1.3 Poses nuevas (H1 CA1, CA5, CA6)
Se ajustan a ojo con `./gradlew :skinstatues:preview` (que ahora lee `builtin-poses.yml`, acepta `-Ponly=a,b` para
iterar y `-Pgrid=true` para la hoja de todas las poses en isométrica con su nombre). La hoja se guarda en
`docs/img/skinstatues/poses.png` para el README (imagen generada sin servidor; no es una captura del juego).
`VoxelizerTest` deja de copiar las poses a mano y carga `builtin-poses.yml`: la cáscara cerrada, «nada bajo los pies» y
el codo sin grietas se comprueban en las 28.

## 2. Coordenadas (H3)

### 2.1 Argumentos
`BuildArgs` (Java puro, probado en unitarias) analiza lo que sigue a `<height>`:
```
[pose] [--palette <p>] [--no-overlay] [--no-cape] [--at <x> <y> <z>] [--facing north|south|east|west] [--world <w>]
```
- Coordenada: `123`, `-4.5` (se redondea hacia abajo al bloque), `~`, `~5`, `~-2.5`. Tope |x|, |z| ≤ 30 000 000.
- Resultado: pose, paleta, flags, `Coord[3]` (relativa o absoluta) o null, `facing` (0/90/180/270 o null), mundo o null;
  o un error con su clave de mensaje y sus valores (`missing-coords`, `invalid-coord`, `invalid-facing`…).

### 2.2 Dónde y hacia dónde (`StatueCommand.build`)
| Quien la pide | Sin `--at` | Con `--at` |
|---|---|---|
| Jugador | Como la 0.1.0: bloque del jugador + `offset_up`, mirando hacia donde mira | `~` relativo a su bloque; los pies en `(x, y, z)` sin `offset_up` |
| Consola (u otro sin posición) | Error: «from the console, use --at x y z» | Solo absolutas (`~` es error); mundo `--world` o el principal |

`--facing` gana sobre la mirada; desde la consola, sin `--facing`, sur (0°). `--world` desconocido: error con «did you
mean». Fuera del borde del mundo (`WorldBorder.isInside` de la base): error. `Statues.build` recibe ya un
`Target(world, x, y, z, facing)` y un `Requester(uuid, name)` (la consola: `Statues.CONSOLE`, «Console»); `undo` desde la
consola usa `lastBy(CONSOLE)`.

### 2.3 Chunks (H3 CA6)
`ChunkHold` (paquete `statue`): para el rectángulo de bloques del plan (o del diario al quitar), pide cada chunk con
`World.getChunkAtAsync` (Paper los carga o genera fuera del hilo principal y completa en él), y cuando están todos
pone un `addPluginChunkTicket` en cada uno. Lo pide el trabajo al pasar a ser el actual de la cola (`start()`), y no
pone ni quita bloques hasta que están todos (`ready()`): así solo la estatua en curso sostiene chunks. Al terminar,
cancelarse o apagarse, `release()` quita los tickets. Así `getBlockAt` nunca carga un chunk en el tick y un chunk lejano no se descarga a mitad
de obra. Tope: los chunks de un plan de `max_blocks` (una estatua de 128 ocupa ≈ 5×5 chunks). Se usa también al
construir donde está el jugador (sin costo: ya están cargados) y al quitar.

## 3. Contrato de configuración (cambios)
```yaml
# poses.yml (del dueño) — además de lo de la 0.1.0:
my_wave:
  extends: waving          # start from another pose (built-in or yours)
  right_arm: { elbow: 90 } # only what changes, angle by angle
my_point_left:
  extends: pointing
  mirror: true             # flip left <-> right, then apply what is written here
  head: { pitch: 10 }
```
`builtin-poses.yml` (de referencia, se reescribe). `config.yml` y los demás archivos no cambian.

Comando:
| Subcomando | Cambio |
|---|---|
| `build <name> <height> [pose] … [--at x y z] [--facing <dir>] [--world <w>]` | Nuevos `--at`, `--facing`, `--world`; desde la consola con `--at` |
| `undo` | También desde la consola (su última estatua) |
| `poses` | Incorporadas y propias por separado; reemplazadas marcadas |

Mensajes nuevos en `messages.yml` (en inglés): `missing-coords`, `invalid-coord`, `console-relative`, `console-needs-at`,
`invalid-facing`, `unknown-world`, `outside-border` y el nuevo formato de `poses` (con `list` para un `messages.yml` viejo). Sale `players-only`. Un `messages.yml` de
la 0.1.0 sin esas claves usa las del jar (ya lo hace `Messages`).

## 4. Hilos y rendimiento
- Análisis de argumentos y resolución de poses: en el comando y al cargar (sin YAML en el tick, III.2).
- Carga de chunks: asíncrona en Paper; el trabajo entra a la cola cuando se completan todas (en el hilo principal).
- Los tickets se quitan siempre (fin, cancelar, apagar): `finally` en el cierre de cada trabajo.

## 5. Amenazas (STRIDE, lo nuevo)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Denegación de servicio | `--at 29999999 …` o una estatua en terreno sin generar: generar chunks traba el servidor | Generación asíncrona de Paper; tope de coordenadas; borde del mundo; una estatua a la vez en la cola |
| Denegación de servicio | Tickets olvidados mantienen chunks cargados para siempre | `release()` en todo cierre de trabajo y en `shutdown`; los tickets de plugin se van al apagar el servidor |
| Denegación de servicio | Una herencia en ciclo o muy profunda cuelga la carga | Detección de ciclos y profundidad máxima 8 |
| Elevación de privilegios | Un jugador construye en otro mundo o lejos | Mismo permiso `skinstatues.build` (`op`); es lo que el dueño pidió (CLARIFY 3) |
| Manipulación | Texto en `--world` o en las coordenadas llega a un mensaje | Se inserta como texto plano (`Placeholder.unparsed`, II.2); nombres de mundo solo de los cargados |
| Repudio | ¿Quién construyó desde la consola? | El índice guarda «Console» como autor; `list`/`info` lo muestran |

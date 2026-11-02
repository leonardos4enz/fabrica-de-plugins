# Plan 028 — Formas, región, utilidades y navegación en Worldcraft

## 1. Arquitectura
Nada nuevo en el motor. Cada comando arma una `EditSource` (qué posiciones y qué poner en cada una) y la pide con
`Edits.request`, que ya trae límites, bloques prohibidos, `--preview`/`confirm`, historial en disco, deshacer seguro y
tandas dentro de los 8 ms por tick (spec 025). Lo que cambia es la forma:

| Tipo | Comandos | Cómo |
|---|---|---|
| **Columnas calculadas** (sin leer el mundo) | `sphere`, `hsphere`, `cyl`, `hcyl`, `pyramid`, `hpyramid`, `cone` | `region.Solid`: por cada columna (x, z) del rectángulo, el intervalo de y que ocupa la forma. Hueca = el sólido menos su interior **erosionado** t veces (§1.1). `edit.SolidSource` recorre las columnas en orden de chunks |
| **Caja con máscara** (sin leer) | `center`, `removenear`, `replacenear`, `removeabove`, `removebelow`, `extinguish` | `RegionSource` de la 025 sobre una caja calculada |
| **Columna de arriba abajo** (sin leer) | `naturalize`, `thaw`, `green` | `ColumnSource` de la 026: cada columna se recorre de arriba abajo de una vez, así el estado actual alcanza para saber la profundidad desde la superficie |
| **Leer y después poner** | `stack`, `move`, `hollow`, `line`, `drain`, `fixwater`, `fixlava`, `fill` | Primero un trabajo de **lectura** por tandas (como `copy`) a una foto (`Clipboard`); el cálculo (inundación, distancias) corre en el hilo de cálculo; después la obra normal con lo calculado (`edit.PointsSource`, `edit.StackSource`, `edit.MoveSource`) |
| **Suelo leído** (`ColumnScan` de la 026) | `overlay`, `snow`, `smooth` | La lectura del suelo ya existe: `overlay` y `snow` ponen encima de la cima de cada columna; `smooth` usa el suavizado del pincel sobre toda la selección (no derrite árboles ni casas) |
| **Sin obra** | `ascend`, `descend`, `ceil`, `thru`, `jumpto`, `unstuck`, `up` | Teletransporte en el hilo principal (`paper.Navigation`); `up` y `ceil` sin vuelo ponen un vidrio como obra de un bloque (§1.4) |

Los comandos nuevos van en clases propias del paquete `command` (`ShapeCommands`, `RegionCommands`, `UtilityCommands`,
`NavigationCommands`) para no engordar `WorldcraftCommand`, que solo los despacha, arma la ayuda y el autocompletado.

### 1.1 Formas (geometría propia)
- **Esfera / elipsoide** de radios (a, b, c) con centro en un bloque: una columna a distancia (dx, dz) ocupa
  `|dy| ≤ ⌊(b + ½)·√(1 − (dx/(a+½))² − (dz/(c+½))²)⌋`. El medio bloque hace que el radio 1 sea una cruz de 7 bloques
  (como el de WorldEdit) y que la forma sea simétrica.
- **Cilindro** de radios (a, c) y alto h: las columnas dentro de la elipse, de y a y + h − 1 (h < 0: hacia abajo).
- **Pirámide** de tamaño s: la columna a distancia de Chebyshev d ≤ s − 1 ocupa de y a y + (s − 1 − d).
- **Cono** de radio r y alto h: la columna a distancia d ≤ r + ½ ocupa de y a y + ⌊h·(1 − d/(r + ½))⌋ − 1 (al menos el
  bloque de la base; h < 0, invertido).
- **Hueca:** un bloque es interior si él y sus 6 vecinos están dentro. Con los intervalos por columna eso es una
  intersección de intervalos: interior(c) = [lo+1, hi−1] ∩ intervalo(4 vecinos). Repetirlo t veces da el grosor t; la
  cáscara es el intervalo menos el interior (a lo más dos tramos por columna). Sin huecos en diagonal (la vecindad de 6 lo
  garantiza) y en O(columnas · t). El cilindro hueco de WorldEdit no tiene tapas: `hcyl` erosiona solo de costado.
- **Dónde:** centradas en el bloque de los pies del jugador; `--raised` pone el pie de la esfera en los pies; el
  cilindro, la pirámide y el cono empiezan en los pies. Desde la consola, `--at x,y,z` y `--world` (como el terreno).
- **Límites antes de calcular:** el volumen se estima con la fórmula (4/3·π·abc, π·ac·h, …) y se compara con el grupo; el
  radio no pasa de `shapes.max_radius`. Así una esfera de radio 5000 se rechaza sin reservar nada.

### 1.2 Leer y después poner
- `Edits.read(who, world, box, done)`: el `copy` de la 025 generalizado (la foto no va al portapapeles). `copy` pasa a
  usarlo.
- `stack n dir`: la foto de la selección; la obra cubre la caja de las n copias seguidas y cada posición mira la foto en
  `posición mod tamaño`. Cuenta n · volumen. Sin aire salvo `--air`; `--select` deja seleccionada la última copia.
- `move n dir [relleno]`: la foto; la obra recorre **dos cajas** (destino y lo que queda del origen), así un `move 500`
  no visita lo de en medio. `EditSource.chunks()` (nuevo, por defecto los de `bounds()`) da la lista de chunks real para
  pedirlos por adelantado. Lo que queda atrás: aire o el patrón. Cofres y carteles viajan.
- `hollow [t] [patrón]`: en la foto, inundación desde el aire del borde de la caja (lo de afuera); después distancia
  desde ese aire hacia adentro por los bloques; los de distancia > t pasan al patrón (aire de fábrica).
- `line patrón [grosor]`: entre la esquina 1 y la 2, n = máx(|dx|, |dy|, |dz|) pasos redondeados; con grosor, una esfera
  de ese radio en cada punto. No necesita foto: posiciones calculadas, en orden de chunks (`PointsSource`).
- `drain r`, `fixwater r`, `fixlava r`, `fill patrón r [prof]`: foto de la caja del radio alrededor del jugador; el líquido
  (o el aire) más cercano conectado dentro de la esfera de radio r; `PointsSource` con lo encontrado.
  - `drain`: agua o lava (lo que haya más cerca) → aire; `--waterlogged` también le saca el agua a escaleras y losas.
  - `fixwater`/`fixlava`: el líquido corriente conectado, a la altura del punto de partida o debajo, pasa a fuente.
  - `fill`: el aire conectado desde los pies, a la altura de los pies o debajo, hasta `prof` (de fábrica, el radio);
    `--recursive` sin la profundidad.

### 1.3 Columna de arriba abajo
`ColumnSource` recorre cada columna completa de arriba abajo antes de pasar a la siguiente, así una variable por columna
alcanza:
- `naturalize`: desde el primer bloque natural (`grass_block`, `dirt`, `coarse_dirt`, `stone`, como WorldEdit) de la
  columna: profundidad 0 pasto, 1–3 tierra, más abajo piedra; solo en esos bloques.
- `thaw`: el primer bloque no aire de la columna: nieve → aire, hielo → agua.
- `green`: el primer bloque no aire: tierra → pasto.

`overlay` y `snow` necesitan poner **encima** de la cima, que ya se pasó al bajar: usan `ColumnScan` (la lectura del suelo
de la 026) con «suelo» = todo lo que no es cobertura (`terrain.cover`) ni tronco u hoja (`terrain.buryable`), y ponen en
cima + 1 solo si ahí hay cobertura o aire. `snow` congela el agua de la cima.

### 1.4 Navegación
`paper.Navigation`, solo jugadores, con `worldcraft.navigate`. «Libre» = el bloque de los pies y el de la cabeza son
atravesables (`Block.isPassable`) y el de abajo no. `ascend`/`descend` buscan el siguiente lugar libre hacia arriba o
abajo (n veces); `ceil` sube hasta el bloque bajo el techo; `thru` atraviesa en la dirección de la mirada (hasta 32
bloques); `jumpto` va al bloque que mira (hasta `terrain.reach`) y queda encima; `unstuck` sube hasta un lugar libre.
`up n` y `ceil` dejan al jugador en el aire: si puede volar se le activa el vuelo; si no, se pone **un vidrio real** bajo
los pies como una obra de un bloque (historial y `undo`, como WorldEdit). Un bloque falso no sirve: el servidor no lo ve
y echaría al jugador por volar.

## 2. Contrato de configuración
```yaml
shapes:
  max_radius: 200        # el radio más grande de una forma (además del tope de bloques del grupo)
utilities:
  max_radius: 64         # drain, fixwater, fill, removenear, snow… (la foto de un radio 64 son ~2 M de bloques)
```
Rangos: `shapes.max_radius` 1–1000, `utilities.max_radius` 1–256. Sin la sección se usan esos valores (quien actualiza
desde 0.3.0 no tiene que tocar nada). Permiso nuevo `worldcraft.navigate` (`default: op`). Mensajes nuevos en
`messages.yml` (inglés) y la ayuda de cada comando con su ejemplo. Versión 0.4.0.

## 3. Hilos y rendimiento
- Todo lo que pone bloques va por el motor (8 ms por tick, turnos entre jugadores). Las lecturas también.
- La inundación, las distancias de `hollow` y los intervalos de las formas se calculan en el hilo de cálculo (el del
  terreno), nunca en el principal. Las formas: O(columnas · grosor).
- La memoria de una foto es 4 bytes por bloque; la caja de la foto no pasa del tope del grupo (se revisa antes de leer).

## 4. Persistencia
Nada nuevo: lo que se pone queda en el historial en disco de la 025.

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Manipulación | Una forma, `stack` o `fill` con bloques prohibidos (bloques de comandos, roca madre) | Los estados del patrón pasan por `blocked_blocks` del grupo; `stack` y `move` revisan los de la foto |
| Manipulación | `move`/`stack` sobre una construcción ajena | Igual que `paste`: deshacer seguro y el historial dice quién fue |
| Repudio | ¿Quién vació el lago? | Cada comando es una entrada del historial con su detalle (`drain r=20`) |
| Denegación de servicio | `/wc sphere stone 100000`, `stack 10000`, `drain 10000` | Volumen estimado contra el grupo antes de calcular; `shapes.max_radius`, `utilities.max_radius`; la foto no pasa del tope del grupo |
| Denegación de servicio | Una inundación de millones de bloques | Acotada por la esfera del radio; corre en el hilo de cálculo |
| Elevación de privilegios | Atravesar paredes hacia zonas cerradas (cárceles, bóvedas) | `worldcraft.navigate` aparte, `default: op` |
| Elevación de privilegios | `up` para subirse a donde no se llega | El vidrio es una obra: límites, mundos permitidos e historial; el vuelo solo se activa si ya podía volar |

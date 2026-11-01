# Plan 026 — Terreno en Worldcraft

## 1. Arquitectura
Todo lo nuevo del terreno es **Java puro** en el paquete `terrain` y se prueba con el mundo en memoria de la 025. Lo que
cambia en el mundo pasa por el motor de la 025 como cualquier obra: `EditSource` → `EditJob` → `Scheduler`, con diario,
límites, vista previa y `confirm`. Nada de NMS. Los algoritmos (ruido, fBm, crestas, suavizado, talud) se escriben desde
cero a partir de su descripción (investigación §7): no se abre código GPL ni de Axiom mientras se escribe el propio.

| Clase | Qué hace |
|---|---|
| `terrain.Noise` | Ruido de gradiente 2D con semilla (hash de la esquina → gradiente, curva quíntica), `fbm`, `ridged` (octavas multiplicadas por la anterior) y alabeo de dominio |
| `terrain.HeightField` | El suelo leído: por columna, altura del suelo natural, bloque de superficie y el de debajo, la planta más alta y si hay un obstáculo (construcción o árbol) |
| `terrain.ColumnScan` | Lee ese suelo como un trabajo `READ` del motor: columna por columna de arriba abajo, se detiene en el suelo |
| `terrain.Style`, `Painter` | Capas de material (de la superficie hacia adentro) con reglas por altura relativa, pendiente y y; cada bloque es un `Pattern` |
| `terrain.MountainShape` | La montaña: picos (uno central y otros alrededor), perfil de campana, crestas, alabeo del contorno, cráter; normaliza para que la cima mida `height` |
| `terrain.IslandShape` | La isla: dos caras por columna (cima casi plana, base en cono invertido rugosa), estalactitas, raíces y árboles |
| `terrain.Brush`, `BrushSettings` | Los modos del pincel sobre el mapa de alturas (subir, bajar, suavizar con gaussiana, aplanar, erosión térmica, pintar) y sus ajustes en texto |
| `terrain.ColumnSource` (+ `HeightSource`, `IslandSource`) | El `EditSource` por columnas: rango de y por columna, qué poner en cada y, y la **piel** para la vista previa |
| `terrain.TerrainConfig` | `terrain.yml` y la sección `terrain` de `config.yml`, validados |
| `paper.Terrain` | Une todo en el servidor: dónde (mira, pies, `at=`), leer el suelo, calcular en otro hilo, pedir la obra con vista previa, `reroll` |
| `paper.BrushTool` | El ítem pincel (marca en el PDC con sus ajustes), clics, círculo de partículas |

### 1.1 Flujo de una montaña
1. El comando lee opciones (`clave=valor`, tamaño, estilo) con «did you mean»; decide el centro (mira hasta `reach`, o los pies,
   o `at=`) y rechaza antes de leer si la estimación (π·r²·h/3) pasa del doble de `max_blocks`.
2. `ColumnScan` lee el suelo del cuadrado del radio, de `y + height` hacia abajo hasta 48 bloques bajo el centro, por tandas.
3. En el hilo `Worldcraft-Terrain`: `MountainShape.plan` da la altura nueva de cada columna y su pendiente.
4. De vuelta en el hilo principal: un `Edits.Op` con el volumen exacto, el `HeightSource` (y su piel para la vista previa) y
   los estados que puede poner (bloques prohibidos); `Edits.request` con vista previa. `reroll` repite 3–4 con otra semilla
   sin volver a leer.

### 1.2 Reglas por columna
- **Generar** (montaña): altura nueva `t = max(g, base + m(x, z))`. Se llena `(g, t]` solo donde hay aire, plantas, agua o
  árboles (`cover` + `buryable`). Una columna con un obstáculo (cualquier otro bloque sobre el suelo: una casa) **no se
  toca**. Material: `Painter` con profundidad `t − y`, altura relativa `(y − base) / height` y la pendiente.
- **Conservar** (pincel `raise`, `lower`, `smooth`, `flatten`, `erode`): al subir, el viejo superficie pasa a ser el de debajo
  y la nueva cima lleva el de superficie; al bajar, lo que sobra se vuelve aire (también las plantas de encima), la nueva
  cima lleva el de superficie y la siguiente el de debajo. Solo se tocan bloques de suelo, plantas y aire. Columnas con
  obstáculo (casa, árbol) o sin suelo: no se tocan.
- **Pintar**: las capas de arriba (o solo la cima, con un patrón) donde hay suelo; con fuerza < 1 se salta una parte de las
  columnas al azar (determinista), más hacia el borde.
- **Isla**: de la base a la cima, profundidad desde la cima; raíz colgante bajo las columnas delgadas del borde; árboles
  como bloques sueltos sobre la cima. Solo en aire o plantas.

### 1.3 La piel (vista previa)
Un bloque de la columna se ve si es la cima, la base (en la isla) o si alguna de las 4 columnas vecinas no tiene bloque a
esa altura. La vista previa recorre solo esos; la obra, todo. Mismo `Painter` y semilla: lo que se ve es lo que se pone.

### 1.4 Pendiente
`atan(√(dx² + dz²))` en grados con diferencias centrales a 2 columnas (más suave que mirar al vecino: un escalón de 1 en
una ladera suave no es 45°).

### 1.5 Cambios al motor de la 025
- `Edits.Op` agrega `preview` (fuente para la vista previa; null = la misma), `note` (semilla y medidas) y `rerollable`
  (botón `[Another]`); el constructor anterior sigue.
- `Edits.scan(...)`: un trabajo `READ` como `copy`, con aviso al terminar.
- `Session` guarda la última receta de terreno (para `reroll`).
- Nada más del motor cambia.

## 2. Contrato de configuración
`config.yml` (sección nueva; un archivo de la 0.1.0 sin ella carga con estos valores):
```yaml
terrain:
  preview_first: true   # mountain e island muestran la vista previa y esperan /wc confirm (--now la salta)
  reach: 200            # hasta dónde se busca el bloque al que apunta la mira (1..512)
  max_radius: 160       # radio máximo de montañas e islas (4..512)
  ground: "#dirt, #sand, #base_stone_overworld, …"   # qué es suelo natural (máscara)
  cover: "air, #replaceable, #flowers, #saplings"    # lo que hay encima y se puede tapar o quitar
  buryable: "#logs, #leaves"                         # lo que una montaña puede enterrar (el pincel no)
  brush:
    item: minecraft:golden_shovel
    max_radius: 16      # 1..64
    range: 200          # 1..512
    show: true          # el círculo de partículas
```
`terrain.yml` (nuevo):
```yaml
sizes:
  mountain: { small: {radius: 20, height: 26}, medium: {radius: 32, height: 40}, large: {radius: 56, height: 70}, huge: {radius: 90, height: 110} }
  island:   { small: {radius: 8}, medium: {radius: 16}, large: {radius: 28}, huge: {radius: 45} }
defaults:
  mountain: { style: alpine, peaks: 3, roughness: 0.5, ridges: 0.6, sharpness: 1.2, crater: 0 }
  island:   { style: meadow, depth: 0.9, relief: 0.08, spikes: 0.5, roots: 0.5, trees: 2 }
styles:
  alpine:
    description: "Snowy peaks, rocky slopes and a grassy foot"
    blend: 0.06          # hace irregular el borde entre reglas por altura (0..0.3)
    options: {}          # opciones del generador que trae el estilo (volcano: {crater: 0.3})
    layers:              # de la superficie hacia adentro; la última llena el resto
      - thickness: 1
        rules:
          - {block: snow_block, height_above: 0.78}
          - {block: "70% stone, 30% andesite", slope_above: 55}
          - {block: grass_block}
      - thickness: 3
        rules: [{block: dirt}]
      - rules: [{block: "85% stone, 15% andesite"}]
    hanging: hanging_roots        # isla: raíces
    tree: {log: oak_log, leaves: oak_leaves}   # isla: árboles (las hojas se ponen persistentes)
```
Rangos: `radius` 4..`max_radius`; `height` 4..384; `peaks` 1..7; `roughness`, `ridges`, `spikes`, `roots` 0..1;
`sharpness` 0.5..3; `crater` 0..0.6; `depth` 0.2..2; `relief` 0..0.3; `trees` 0..12; reglas: `height_*` 0..1,
`slope_*` 0..90, `y_*` −2048..2048; `thickness` 1..64. Claves y estilos desconocidos avisan con «did you mean»; un estilo
o una regla inválidos se omiten; si falta el estilo por defecto se usa el primero válido.

Comandos (permiso `worldcraft.use`):
| Subcomando | Qué hace |
|---|---|
| `mountain [size\|radius] [style] [clave=valor…] [--now]` | Montaña con vista previa |
| `island [size\|radius] [style] [clave=valor…] [--now]` (alias `floating_island`) | Isla flotante con vista previa |
| `reroll` | La última montaña o isla con otra semilla |
| `brush [mode] [radius] [strength] [patrón] [style=…] [mask=…]` | Da o ajusta el pincel |
| `styles` | Lista los estilos |

Pincel en el ítem: PDC `worldcraft:brush` con `mode=smooth;radius=6;strength=0.5;style=…;pattern=…;mask=…`.

## 3. Hilos y rendimiento
- **Hilo principal:** comandos, rayo de la mira, el trabajo de lectura y el de la obra (por tandas, presupuesto de la 025),
  el círculo de partículas (cada 4 ticks, solo a quien tiene el pincel en la mano, tope de 64 partículas).
- **Hilo `Worldcraft-Terrain`** (uno, daemon): el ruido y el cálculo de alturas (Java puro, sin Bukkit). El resultado vuelve al
  hilo principal con `runTask`.
- **Topes (III.4):** `max_radius`, `brush.max_radius`, ventana de lectura (altura + 48), `trees` ≤ 12, erosión ≤ 10 pasadas,
  y los de la 025 (`max_blocks`, `max_jobs`, vista previa).
- **Memoria:** arreglos por columna (radio 160: 103 000 columnas × ~6 enteros ≈ 2,5 MB, solo mientras se calcula y en la
  receta para `reroll`).

## 4. Persistencia
Nada nuevo en disco salvo `terrain.yml`: las obras usan el historial de la 025. Los ajustes del pincel viajan en el ítem.

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación | Una pala de oro cualquiera funciona como pincel | Solo el ítem con la marca del plugin (PDC) |
| Manipulación | Una montaña entierra una casa; el pincel come una construcción | Las columnas con obstáculos (lo que no es suelo, planta ni árbol) no se tocan; el pincel tampoco toca árboles |
| Manipulación | Un estilo pone bloques prohibidos | Los estados de un estilo pasan por `blocked_blocks` del grupo como cualquier patrón |
| Manipulación | Un texto de opción o de un estilo se lee como MiniMessage | Todo valor se inserta escapado (II.2) |
| Repudio | ¿Quién hizo esta montaña? | Historial de la 025 con tipo `mountain`, opciones y semilla |
| Divulgación | La vista previa la ven otros | Bloques falsos solo al que la pidió (025) |
| Denegación de servicio | Un radio enorme lee o calcula millones de columnas; clics del pincel sin parar | `max_radius`, ventana de lectura, rechazo temprano por estimación, `max_blocks`/`max_jobs`; una pasada a la vez por jugador; el cálculo fuera del hilo principal |
| Elevación de privilegios | Usar el terreno sin permiso | `worldcraft.use` (`default: op`) en comandos, pincel y autocompletado |

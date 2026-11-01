# Investigación: terreno fácil para novatos y potente para expertos (montañas, islas flotantes, pinceles)

**Fecha:** 2026-11-01 · **Para:** el dueño de la fábrica · **Estado:** insumo para la spec de terreno de Worldcraft (nada implementado)

Objetivo: que un novato haga una montaña o una isla flotante con un comando, sin que el plugin parezca «solo para novatos»:
el experto debe poder controlar semilla, octavas, alturas y máscaras. Fuentes: documentación oficial (WorldEdit, WorldPainter,
FAVS, repos de ruido), guías de comunidad (ManaCube, wikis de servidores) y documentación de técnicas de terreno
(Red Blob Games, World Machine, Blender). Lo secundario o visto solo en el extracto del buscador va marcado.

**Límites de la investigación.**
- **Reddit no se pudo abrir** (la herramienta lo bloquea: «unable to fetch from www.reddit.com») y las búsquedas no devolvieron
  hilos. **No hay aquí voces de usuarios de Reddit**; lo de «con qué batallan» sale de guías, wikis de servidores y foros.
- **PlanetMinecraft dio 403** (tutorial de islas flotantes); **SpigotMC** no se intentó abrir (falla con frecuencia) y de goBrush
  solo hay listados en sitios espejo (9minecraft, mc-mod.net), no su página ni repositorio.
- **ManaCube wiki** (guía de terraformado) devolvió solo el título al fetch; se cita por el extracto del buscador, marcado.
- **Documentación de FAVS:** la página de pinceles de entorno no trae los de terreno (erode, blend, melt…); los nombres salen de
  guías de terceros y pueden diferir entre versiones. **No se pudo verificar la sintaxis ni los argumentos actuales de FAVS.**
- **Axiom:** la ficha de Modrinth y la web oficial dan la lista de herramientas, pero **no** el comportamiento ni los parámetros
  (el extracto del buscador habló de «ruido» y «pendiente»; la ficha no los menciona).
- **Islas flotantes:** no hay fuente buena sobre la forma ideal (las páginas de resultados son spam autogenerado). La forma que se
  propone en §2.2 es **propuesta propia**, no hallazgo.
- **Parámetros de ruido «razonables»** (octavas, lacunaridad, ganancia) salen en parte de Red Blob Games y en parte de conocimiento
  general; están marcados. No se midió nada en un servidor.
- Las cifras de descargas y licencias vienen de [editores-de-mundo.md](editores-de-mundo.md) (misma fecha) salvo lo indicado.

## 1. Con qué batallan los constructores al hacer terreno

### 1.1 WorldEdit
- **Los comandos de terreno son pocos y exigen escribir bien los argumentos.** `//smooth [iterations] [mask]`: la máscara
  define qué bloques cuentan como «suelo» para el mapa de alturas, y la propia documentación pone `//smooth 1
  grass_block,dirt,stone` como ejemplo para suavizar «solo terreno natural»
  ([comandos](https://worldedit.enginehub.org/en/latest/commands/)). Sin esa máscara el mapa de alturas cuenta cualquier sólido
  (árboles, construcciones). Es una trampa para un novato: el comando funciona «bien» y destroza lo que no era terreno.
- `//generate` y `//deform` aceptan una **expresión matemática**; sin banderas las coordenadas se normalizan a −1..1 sobre la
  selección, y la propia página avisa «puede que haya que escalar x, y, z según el tamaño de la selección»
  ([generación](https://worldedit.enginehub.org/en/latest/usage/generation/)). Los ejemplos oficiales son senos y árboles
  retorcidos: **no hay ruido** (Perlin/simplex) nativo en las expresiones. Hacer una montaña natural con `//generate` es,
  en la práctica, escribir un trozo de matemáticas a mano.
- `//deform` además tiene un issue de caídas: [#2812](https://github.com/EngineHub/WorldEdit/issues/2812) «Some //deform commands
  cause servers to crash» (ya citado en la investigación anterior).
- Los pinceles `sphere`, `smooth`, `gravity`, `raise`, `lower` son los de terreno
  ([comandos](https://worldedit.enginehub.org/en/latest/commands/)). `/brush smooth [radius] [iterations] [mask]` y
  `//naturalize` («tres capas de tierra encima, roca debajo») son lo más cercano a «pintar por capas». No hay pintura por
  pendiente ni por altura.
- **Flujo típico de novato** según una guía de ManaCube (solo extracto del buscador): hacer la forma bruta con el pincel de
  esfera, pasar el pincel de suavizar, y «una combinación de suavizado con pincel y a mano funciona mejor». La misma guía avisa
  que **suavizar de más da artefactos** y que suavizar las esquinas puede dejar el terreno «poco natural» por exceso de
  irregularidad ([guía](https://wiki.manacube.com/creative/terraforming-for-beginners),
  [hilo](https://forums.manacube.com/threads/terraforming-with-worldedit-for-beginners.18061/)).
- El suavizado de WorldEdit es un **mapa de alturas con núcleo gaussiano** (visto en el código del fork Plex-FAWE: `SmoothBrush`
  / filtro de alturas que busca, por columna, el bloque más alto que cumple la máscara; extracto del buscador, repositorio de un
  tercero: [commit](https://git.telesphoreo.me/Plexus/Plex-FAWE/commit/7acd006b2b70e18b34377381be4ede0a30e7c54b)). Eso explica
  su fallo conocido: **solo mueve la superficie en vertical por columna; no entiende salientes, cuevas ni paredes verticales**.
  (Interpretación propia a partir de cómo funciona un mapa de alturas.) Otro commit del mismo fork añadió una bandera `-n`
  («solo bloques naturales»), señal de que la máscara era una queja real
  ([commit](https://git.telesphoreo.me/Plexus/Plex-FAWE/commit/243d6476ac358d19511c616a8618cf7ffa1ae16f)).

### 1.2 VoxelSniper / FastAsyncVoxelSniper
- Existe una familia de pinceles de terreno: **melt** (quitar de forma orgánica), **fill** (añadir), **lift** (subir) y
  **smooth** (suavizar bordes irregulares), con la forma `/b e melt`, `/b e smooth`; el **blend ball** (`/b bb`) es el que se
  recomienda para suavizar bordes duros («como el pincel de erosión suave con esteroides», según un foro); y **overlay**
  (`/b over d2`) pinta una capa superficial de un bloque «hasta una profundidad dada»
  ([guía de wiki](https://wiki.manacube.com/creative/voxelsniper),
  [referencia MC Middle-earth](https://www.mcmiddleearth.com/wiki/voxel-reference-guide-tutorials/); ambos terceros, vistos solo
  en el extracto del buscador).
- **Los nombres y la sintaxis cambian entre versiones**: la wiki de PirateMC avisa que VoxelSniper 5 reorganizó cómo se
  diseñan y acceden los pinceles ([wiki](https://wiki.piratemc.com/VoxelSniper)), y la referencia de MC Middle-earth marca
  varios como «no disponible» o «próximamente». El nombre cripto (`/b e melt`, `/b bb`, `/b over d2`) es en sí el obstáculo:
  nada en el comando dice qué hace.
- La documentación oficial de FAVS ([gitbook](https://intellectualsites.gitbook.io/fastasyncvoxelsniper/)) no lista en su
  página de «Environment Brushes» ninguno de esos pinceles de terreno (solo árboles, nieve y clonado); hay que buscarlos en otra
  sección. Es una queja de descubribilidad en sí misma.
- FAVS anuncia «snipe brush» y «herramientas de selección suave» para terraformar, y se activa con una flecha (reemplazar) y
  pólvora (añadir) ([README](https://github.com/IntellectualSites/FastAsyncVoxelSniper)).

### 1.3 goBrush / goPaint
- goBrush: «construir montañas con mapas de alturas», **18 pinceles de mapa de alturas gratuitos** y la posibilidad de crear los
  propios; se activa con un trozo de pedernal: clic izquierdo abre un menú (GUI), clic derecho coloca el mapa de alturas;
  integra deshacer/rehacer con WorldEdit; el menú tiene activar/desactivar y máscara
  ([listado](https://www.9minecraft.net/?p=411573), [Planet Minecraft](https://www.planetminecraft.com/blog/builder-s-tools-gobrush-plugin/);
  secundarias, repetidas en sitios espejo). Los mapas de alturas son imágenes en escala de grises, como las de WorldPainter.
- Es el único del grupo que trata el pincel como **producto con menú**, no como línea de comandos: es el antecedente más directo
  del pincel con interfaz que se propone aquí.
- **No se pudo abrir la ficha primaria de goBrush ni su repositorio:** la licencia queda **sin verificar** (ver §6).
  goPaintAdvanced (reemplazo de goPaint) es GPL-3.0 según la ficha de Modrinth citada en la investigación previa.

### 1.4 WorldPainter (programa externo)
- Es lo que usan los que se toman en serio el terreno; sus rasgos, según la API de scripting oficial: mapas de alturas de **16
  bits**, mapeo de rango con `fromLevels(0, 65535).toLevels(0, 255)` y terrenos personalizados en `.terrain`
  ([scripting](https://www.worldpainter.net/trac/wiki/Scripting/API?version=11)). La web oficial lo describe como «esculpir el
  terreno y pintar materiales, árboles, nieve y hielo» y lo licencia **GPL v3** ([worldpainter.net](https://www.worldpainter.net/)).
- Un clon dentro del juego (mod, tercero) describe las ideas que la gente espera: pincel con **restricción de pendiente**
  («pendiente mayor/menor que N grados»), herramienta **Mountain** («esculpe picos y cordilleras»), pinceles de formas
  *Circle, Dome, Cone, Plateau, Mountain, Noise* y objetos personalizados desde `.schematic`
  ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/world-painter); secundario, no es el WorldPainter oficial).
- Lo difícil: es **otro programa**, hay que exportar el mundo y recargarlo; no sirve para retocar un servidor vivo. El dueño lo
  dijo: «lo que la gente batalla al usar WorldEdit más allá del plugin».

### 1.5 Axiom (mod de Fabric)
- Herramientas listadas en la web oficial: Noise Painter, Biome Painter, Elevation, Flatten, Slope, Smooth, Distort, Roughen,
  Shatter, Extrude ([axiom.moulberry.com](https://axiom.moulberry.com/)); la ficha habla de «Heightmap Tools» que modifican el
  terreno por capas y «Manipulation Tools» para suavizar ([Modrinth](https://modrinth.com/mod/axiom)).
- **Licencia: «All Rights Reserved» + EULA**; el multiplayer exige licencia comercial para quien opera el servidor, y los
  servidores privados necesitan lista blanca ([Modrinth](https://modrinth.com/mod/axiom)). Es mod de cliente: justo lo que el
  dueño no quiere exigir a sus jugadores. No se puede copiar nada de él.
- Su valor para el diseño: demuestra que **las herramientas de terreno con vista previa y sin comando** se ven más fáciles que
  cualquier comando. Detalles de parámetros: sin verificar.

### 1.6 Resumen de los dolores

| Dolor | Dónde se ve |
|---|---|
| Argumentos crípticos (`/b e melt`, `/b bb`, `//smooth 1 grass_block,dirt,stone`) | §1.1, §1.2 |
| Suavizar «derrite» o aplana todo; suavizar de más da artefactos | §1.1 |
| Suavizar toma como suelo cualquier sólido salvo que se dé máscara | §1.1 |
| No hay ruido en `//generate`; escalar x, y, z a mano | §1.1 |
| Los nombres de pinceles cambian entre versiones | §1.2 |
| Sin pintura por pendiente/altura en el plugin | §1.1, §1.4 |
| El mejor flujo es un programa externo o un mod de cliente | §1.4, §1.5 |
| Sin forma «de una orden» para una isla o una montaña | §1.1, §2.2 |

## 2. Cómo se hacen bien

### 2.1 Montañas (técnica de mapa de alturas)
Se calcula una altura por columna (x, z) y luego se rellena de arriba a abajo. Ingredientes:

- **fBm** (movimiento browniano fraccionario): suma de octavas de ruido. Convención de Red Blob Games: amplitudes que se
  reducen a la mitad (`1, 1/2, 1/4, 1/8…`) y frecuencias que se duplican; normalizar dividiendo entre la suma de amplitudes;
  descorrelacionar las octavas con desplazamientos o semillas distintas
  ([Red Blob Games](https://www.redblobgames.com/maps/terrain-from-noise/)). Con lacunaridad 2 y ganancia 0,5 se corresponde con
  H = 1; el ejemplo de la documentación de `ambient` usa 8 octavas y esos mismos valores por defecto
  ([ambient](https://ambient.data-imaginist.com/reference/ridged.html)); el rango «5–8 octavas, lacunaridad ~2, ganancia ~0,5»
  es el **punto de partida habitual (conocimiento general, no verificado en una fuente única)**.
- **Redistribución por potencia:** `elevación = (e · fudge)^exponente`; exponentes mayores que 1 empujan las alturas medias hacia
  valles y dejan picos escasos (la demo usa fudge ≈ 1,2 y exponentes 1, 3 y 5)
  ([Red Blob Games](https://www.redblobgames.com/maps/terrain-from-noise/)). Es el control más barato para pasar de «colinas
  gordas» a «montaña con valles».
- **Ruido en crestas (ridged):** `2·(0,5 − |0,5 − ruido|)` por octava, y las octavas altas se **multiplican por las anteriores**,
  de modo que el detalle solo aparece donde ya es alto: «montañas rugosas, valles lisos»
  ([Red Blob Games](https://www.redblobgames.com/maps/terrain-from-noise/)). Variante equivalente: `1 − |ruido|`, elevada al
  cuadrado ([Book of Shaders, vía buscador](https://git.blob42.xyz/Archives/thebookofshaders/blame/commit/2e3eab82667ea2be5aa5851a5d6368faea14977a/13/README.md)).
  El repositorio de OpenSimplex2 recomienda **OpenSimplex2S** para ruido en crestas (si se pasa cada capa por `abs(x)`)
  ([KdotJPG](https://github.com/KdotJPG/OpenSimplex2)).
- **Alabeo de dominio (*domain warping*):** `f(p) = fbm(p + fbm(p + fbm(p)))` rompe la regularidad y da formas «tectónicas»
  (atribuido a Inigo Quilez, 2002, vía [Book of Shaders](https://git.blob42.xyz/Archives/thebookofshaders/src/commit/5f142f5dcce9aeb601648f3e6f6f07cdcb4439f7/13);
  la página original de Quilez no se abrió). FastNoiseLite ya trae dos alabeos de dominio
  ([FastNoiseLite](https://github.com/Auburn/FastNoiseLite)).
- **Máscara de borde** para que la montaña muera en el contorno de la zona pedida: mezcla `e = lerp(e, 1 − d, mix)` con
  `d` = distancia normalizada al centro; la «euclídea²» `d = min(1, (nx² + ny²)/√2)` da formas redondas, y se puede probar
  con exponentes 4 o 6 o aplicarla solo a las octavas bajas
  ([Red Blob Games](https://www.redblobgames.com/maps/terrain-from-noise/)).
- **Capas de material por altura y pendiente** (propuesta propia, cómo se hace en la práctica en mapas de alturas):
  altura relativa `h/H` → piedra y nieve arriba (p. ej. nieve sobre ~0,8, la tabla de biomas de Red Blob usa umbrales 0,3/0,6/0,8
  que «hay que ajustar por proyecto»); pendiente = diferencia máxima con las 4 u 8 columnas vecinas → pasto/tierra donde es plano,
  piedra donde es empinado. WorldPainter y su clon ofrecen justo «pendiente mayor/menor que N grados» (§1.4).

### 2.2 Islas flotantes
No hay fuente fiable (ver límites). Lo que sí está documentado son los bloques de construcción: **máscara radial** con caída
suave (`smoothstep` entre dos radios, o `d³`) más un segundo ruido que rompe el contorno, y un perfil vertical separado
([ejemplos de generadores](https://docs.rs/crate/terrain-generator/0.3.1/source/src/base.rs); extracto del buscador). Guías de
constructores (de baja calidad, solo extracto) coinciden en: **cima plana**, **no dejar la base como disco plano**, esculpir
cuevas y jardines colgantes en la base, **material de piedra** debajo y evitar la simetría
([ejemplo](https://old.fbpe.org/floating-island-tutorial.html)).

**Forma propuesta (propia), por columna (x, z) con `r` = distancia normalizada al centro y `n` ruido 2D:**

| Parte | Fórmula / idea | Valor de partida |
|---|---|---|
| Contorno | `r' = r · (1 + 0,25·n_bajo)` (ruido de baja frecuencia en el radio) | amplitud 15–30 % |
| Cima | `techo = y0 + 0,08·R·ruido_fbm(x,z)` , plana o abombada; vale 0 fuera de `r' = 1` | relieve ≤ 8 % del radio |
| Borde de cima | caída `smoothstep(0,85; 1; r')` en los últimos 15 % | acantilado corto |
| Base | profundidad `P(r') = P0·(1 − r'^k)` con k≈1,5–2 (cono invertido) o `√(1 − r'²)` (cuenco) | P0 ≈ 0,6–1,0 · R |
| Detalle de base | `P · (1 + 0,35·ridged(x,z))` + estalactitas: columnas aleatorias con `P·(1,3..1,8)` y radio 1–2 | 5–12 % de columnas |
| Material | 1 pasto, 3 tierra, resto piedra/andesita, mezcla con el patrón `70% stone, 30% andesite` | — |
| Extras opcionales | raíces colgantes (cadenas de `hanging_roots`), cascada que cae al vacío, árbol y lago en la cima | por bandera |

Por qué columnas y no 3D: una isla de «cima plana + base cónica» es **un mapa de alturas de dos caras** (techo y suelo por
columna). Es barato, determinista con una semilla y sin 3D denso; la densidad 3D solo sería necesaria para arcos o cuevas
(roadmap, no núcleo). El End vanilla usa dos funciones de densidad (isla central y exteriores) según un listado de mods, sin
fórmulas publicadas ([mado-hosting](https://www.mado-hosting.com/minecraft-mods/en-sityfunction); secundario); no se encontró
una fuente que describa su forma real.

### 2.3 Ríos, caminos y suavizado
- **Tallar a lo largo de un trazo:** propuesta propia: para cada columna cerca de la polilínea, `altura_nueva =
  lerp(altura, nivel_del_agua − profundidad, caída(distancia))` con una caída suave de las orillas; el trazo se pone con puntos
  de la mirada. Ningún editor del grupo lo documenta como comando simple; se menciona en WorldPainter como capas
  (§1.4, sin detalle).
- **Suavizado:** la solución estándar es una media (caja) o gaussiana sobre el **mapa de alturas**, no sobre voxels 3D; así lo
  hace el `//smooth` de WorldEdit (§1.1). Para no «derretir»: limitar por iteración (p. ej. no mover una columna más de ±1–2),
  respetar la máscara de suelo y **mezclar** `h' = lerp(h, media, fuerza)` en vez de sustituir. Radio y fuerza deben ser los
  dos mandos visibles (propuesta propia).
- **Paredes verticales feas:** un mapa de alturas por columna crea pasos de 1 bloque o acantilados de varios; la salida
  práctica es un paso de **erosión térmica** después (§2.4).

### 2.4 Erosión
- **Térmica:** mueve material cuando la pendiente supera un ángulo de reposo (*talus*); es «un proceso de relajación muy fácil
  de implementar»; en la vida real el ángulo es de unos **30–40°** y los valores más bajos se parecen más a arena
  ([World Machine](https://help.world-machine.com/topic/device-thermalerosion/)); el orden habitual es térmica primero e hidráulica
  después ([Hesiod](https://hesioddoc.readthedocs.io/en/latest/guides/erosion/)). Un proyecto aficionado fija el talud en
  4/ancho_del_mapa (no es estándar) ([Morningside, vía buscador](https://github.com/vrum/fractalterraingeneration/blob/master/wiki/Erosion_Models.wiki)).
  En bloques, el equivalente natural es **diferencia máxima de altura entre vecinos** (p. ej. 1 = 45°, 2 = más empinado): se
  reparte el exceso entre los vecinos más bajos, N iteraciones. (Propuesta propia.)
- **Hidráulica:** simulación de gotas (disolver, transportar, depositar); la fuente no da un tutorial completo y es la parte más
  cara: va al roadmap.
- En el mundo de los pinceles, la erosión se llama *melt/fill/lift* (§1.2): la gente sabe que existe y la quiere; la versión
  Worldcraft sería un pincel `erode` con dos mandos (fuerza e iteraciones).

## 3. Técnicas que sirven en un plugin sin mod

| Técnica | Cómo se hace en Paper | Notas |
|---|---|---|
| Ruido | OpenSimplex2 / OpenSimplex2S, o FastNoiseLite (Java), en 2D por columna | OpenSimplex2 es CC0; FastNoiseLite es MIT ([KdotJPG](https://github.com/KdotJPG/OpenSimplex2), [FastNoiseLite](https://github.com/Auburn/FastNoiseLite)) |
| API de Bukkit | `PerlinOctaveGenerator` / `SimplexOctaveGenerator` ya vienen en `org.bukkit.util.noise` ([javadoc](https://jd.papermc.io/paper/1.13/org/bukkit/util/noise/OctaveGenerator.html)); solo octavas simples, sin ridged ni alabeo | Útil como respaldo; su licencia no se verificó (§6) |
| Mapa de alturas | `int[][]` por zona; leer la superficie existente con la **máscara de suelo** | Es lo que usa `//smooth` (§1.1) |
| Pincel con rayo | `rayTraceBlocks` desde los ojos, hasta ~100–200 bloques | — |
| Vista previa | `Player.sendBlockChange`/`sendMultiBlockChange` con el contorno o la superficie nueva | Ya existe en Worldcraft y es lo que ve solo quien edita |
| Menú de ajustes | Inventario con ítems (semilla, escala, altura, material) o diálogos de Paper | goBrush abre su menú con clic izquierdo (§1.3) |
| Presets | YAML con los parámetros, listados por nombre en `/wc preset` | Encaja con el principio de configuración de la constitución |
| Mapas de alturas desde imagen | PNG en escala de grises → alturas; 16 bits como en WorldPainter (§1.4) | `javax.imageio` ya está en el JDK |

Vista previa de una montaña grande: la vista con bloques falsos de toda la montaña es cara; viable **dibujar la silueta**
(rejilla de puntos, huella) y dejar la montaña completa para `confirm`. (Propuesta propia; hay que medirlo.)

## 4. Qué esperan los experimentados

Basado en lo que ofrecen los programas externos y mods (§1.4, §1.5) y en lo que piden los parámetros del ruido (§2.1); lo de
Reddit/Discord no se pudo ver, así que es lista **inferida**, no medida:

| Expectativa | Qué implica |
|---|---|
| Control de **semilla** y repetir con otra semilla | `seed=` explícita; `/wc again` o `reroll` con la semilla siguiente y deshacer lo anterior |
| **Octavas, frecuencia (escala), ganancia, lacunaridad** | claves con nombre en el preset, no en un comando |
| **Altura** mínima/máxima, exponente de redistribución | `height`, `power` (§2.1) |
| **Cresta** (ridged) y **alabeo** | tipos de ruido seleccionables: `fbm`, `ridged`, `warped` |
| **Mezcla por pendiente/altura** con umbrales | tabla de capas con `min_slope`, `max_slope`, `min_height` |
| **Máscaras** en cada herramienta | las de Worldcraft ya existen: reutilizarlas |
| **Presets propios exportables** | YAML en `plugins/Worldcraft/presets/`; `export`/`import` por archivo |
| **Deshacer** y rehacer | ya existe, en disco |
| **Rendimiento** | obras por tandas (ya existe); cálculo del mapa fuera del hilo principal |
| **Mapas de alturas de imagen** | PNG de goBrush/WorldPainter (§1.3, §1.4) |

Regla de diseño que sale de aquí: el **mismo preset YAML** sirve de «un clic» al novato y de panel de mandos al experto;
`/wc mountain` sin argumentos usa defaults buenos y `/wc mountain seed=42 octaves=7 power=2.2 snow=0.8` los expone.

## 5. Dolor → propuesta para Worldcraft

| # | Dolor (fuente) | Propuesta | Nivel | Prioridad | Costo |
|---|---|---|---|---|---|
| 1 | Hacer una montaña natural exige matemáticas ([generación](https://worldedit.enginehub.org/en/latest/usage/generation/)) | `/wc mountain [tamaño] [preset]`: fBm+ridged+potencia, capas por altura/pendiente | novato + experto | núcleo | medio |
| 2 | Sin comando para islas ([§1](#1-con-qué-batallan-los-constructores-al-hacer-terreno), búsquedas sin guía) | `/wc floating_island [radio]` con la forma de §2.2 | novato | núcleo | medio |
| 3 | Suavizar toma cualquier sólido y «derrite» ([comandos](https://worldedit.enginehub.org/en/latest/commands/), [ManaCube](https://wiki.manacube.com/creative/terraforming-for-beginners)) | Pincel `smooth` sobre mapa de alturas con máscara de suelo automática («solo natural») y fuerza | ambos | núcleo | medio |
| 4 | Pinceles crípticos y que cambian ([wiki PirateMC](https://wiki.piratemc.com/VoxelSniper)) | Un solo pincel de terreno con modos con nombre: `smooth`, `raise`, `lower`, `paint` | novato | núcleo | medio |
| 5 | Sin pintura por pendiente/altura ([§1.4](#14-worldpainter-programa-externo)) | `paint` por reglas: plano → pasto, empinado → piedra, alto → nieve | ambos | núcleo | medio |
| 6 | Experto quiere semilla/octavas/umbrales ([§4](#4-qué-esperan-los-experimentados)) | Presets YAML con todas las claves + `seed=` y `reroll` | experto | núcleo | bajo |
| 7 | Vista previa de terreno nuevo (Axiom la tiene, [Modrinth](https://modrinth.com/mod/axiom)) | Contorno o silueta con bloques falsos y `confirm` (ya existen) | ambos | núcleo | bajo |
| 8 | Menú de pincel ([goBrush](https://www.9minecraft.net/?p=411573)) | Menú de inventario para radio, fuerza, preset | novato | después | medio |
| 9 | Paredes verticales/acantilados de 1 bloque ([§2.3](#23-ríos-caminos-y-suavizado)) | `/wc erode` térmico (talud en bloques, N iteraciones) | ambos | después | medio |
| 10 | Ríos y caminos ([§2.3](#23-ríos-caminos-y-suavizado)) | `/wc river` por trazo de la mirada con orillas suaves | ambos | después | alto |
| 11 | Mapas de alturas ([goBrush](https://www.9minecraft.net/?p=411573), [WorldPainter](https://www.worldpainter.net/trac/wiki/Scripting/API?version=11)) | Cargar PNG gris como forma de montaña/pincel | experto | después | medio |
| 12 | Erosión hidráulica ([§2.4](#24-erosión)) | Simulación de gotas | experto | después | alto |
| 13 | Árboles y objetos sobre el terreno nuevo (clon de WorldPainter: objetos desde schematic) | Esparcir objetos por reglas | ambos | después | alto |
| 14 | Ríos/islas con cuevas y arcos (necesitan 3D) | Densidad 3D | experto | después | alto |

## 6. Núcleo sugerido para una sola spec

Alcanzable en una sesión (lección «proporción costo/valor» de la CLAUDE.md): **cuatro cosas, un solo motor de alturas**.

1. **Motor compartido:** ruido 2D (OpenSimplex2S propio, CC0) con `fbm`, `ridged`, `power`; semilla; cálculo del mapa fuera
   del hilo principal y aplicación por tandas con las obras que ya existen.
2. **`/wc mountain`:** parámetros `size`, `height`, `seed`, `octaves` (def. 6), `power` (def. ~2), `ridged` (0–1),
   capas por altura (nieve > 0,8) y por pendiente (pasto en plano, piedra empinado). Vista previa de silueta + `confirm`.
3. **`/wc floating_island`:** la forma de §2.2 (cima plana, base cónica con k≈1,5–2, estalactitas, contorno con ruido).
4. **Pincel de terreno (`/wc terrain`)** con modos `smooth`, `raise`, `lower`, `paint-slope`; radio y fuerza; máscara de suelo
   automática. Pintura: reglas por pendiente/altura.
5. **Presets en YAML** (`mountain`, `volcano`, `snowy_peak`, `hills`, `island_small`, `island_big`) que se pueden copiar,
   editar y listar; el mismo formato lo usan los comandos y el pincel.

**Fuera de la spec (al roadmap):** erosión (térmica primero, hidráulica después), ríos/caminos, menús de inventario,
mapas de alturas de imagen, objetos esparcidos, islas con cuevas/arcos (3D), exportar/importar presets.
Un criterio de cierre sugerido: una montaña y una isla se ven bien **con defaults** y con tres semillas distintas, y la suite
mide que ningún tick supera el presupuesto (lección 025 F-01).

## 7. Riesgos legales

| Proyecto | Licencia | Qué se puede hacer | Fuente |
|---|---|---|---|
| WorldEdit | GPL-3.0-only | **No copiar código** ni portar clases; ideas y algoritmos genéricos (mapa de alturas, media gaussiana) sí | [Modrinth](https://modrinth.com/plugin/worldedit) |
| FAWE | GPL-3.0-only | Igual | [Modrinth](https://modrinth.com/plugin/fastasyncworldedit) |
| FastAsyncVoxelSniper | GPL-3.0-only | Igual; los nombres `melt/fill/lift` son ideas, no código | [Modrinth](https://modrinth.com/plugin/fastasyncvoxelsniper) |
| goPaintAdvanced | GPL-3.0-only | Igual | [Modrinth](https://modrinth.com/plugin/gopaintadvanced) |
| goBrush | **sin verificar** (no se abrió la ficha primaria) | Tratar como cerrado: no copiar ni sus mapas de alturas | — |
| WorldPainter | GPL v3 | No copiar código; técnica y formatos (PNG, `.terrain`) son ideas | [worldpainter.net](https://www.worldpainter.net/) |
| Axiom | «All Rights Reserved» + EULA | **No copiar nada**; no depender de él | [Modrinth](https://modrinth.com/mod/axiom) |
| OpenSimplex2 (KdotJPG) | **CC0-1.0** en la mayoría de los archivos; algunos de un directorio son UNLICENSE (dominio público) | Se puede usar y modificar sin atribución obligatoria; comprobar el archivo exacto antes de copiarlo | [repo](https://github.com/KdotJPG/OpenSimplex2) |
| FastNoiseLite | MIT | Usable con aviso de copyright (se incluye el texto de la licencia) | [repo](https://github.com/Auburn/FastNoiseLite) |
| `org.bukkit.util.noise` | no verificado (la API de Bukkit/Paper es de otro régimen) | Es API del servidor, no se copia: se llama en tiempo de ejecución | [javadoc](https://jd.papermc.io/paper/1.13/org/bukkit/util/noise/OctaveGenerator.html) |

Reglas para la spec:
- **Escribir los algoritmos desde cero** a partir de las descripciones (fBm, ridged, media de alturas, talud): son técnicas, no
  expresión protegida. No abrir el código GPL mientras se escribe el propio, y anotarlo en el plan.
- **Mapas de alturas e imágenes de terceros** (los 18 de goBrush, packs de creadores): no se incluyen en el jar; los PNG propios
  se generan con el ruido del plugin.
- **OpenSimplex2:** preferir copiar un solo archivo CC0 y dejar el aviso de procedencia en el encabezado; o reimplementar con
  FastNoiseLite (MIT) y copiar su licencia en `LICENSE-THIRD-PARTY`.
- **Nombres de comandos y pinceles:** no son protegibles en general, pero evitar copiar la **documentación** (texto) de
  terceros; los README son propios.
- Nada de esto es asesoría legal; si el plugin se publica en Modrinth o Hangar, revisar sus reglas de licencias.

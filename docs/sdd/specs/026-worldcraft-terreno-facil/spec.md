# Spec 026 — Worldcraft: terreno fácil para el novato y fino para el experto

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a, 12/12 dos veces) (ver [verify](verify.md)) · CLARIFY: propuesta de la IA pendiente del dueño · **Plugin:** Worldcraft ·
**Depende de:** [025](../025-worldcraft-nucleo/spec.md) (motor por tandas, vista previa y `confirm`, deshacer, límites) ·
**Origen:** pedido del dueño (2026-11-01): «quisiera que fuera muy muy superior, muy, y que sea super facil construir, que
alguien novato pueda hacer cosas increibles como montañas facilmente o islas, modificar terreno, etc, lo que la gente
batalla al usar world edit mas alla del plugin, sino el como crear». Después: «no quisiera que el plugin se vea básico como
solo para novatos, también para los experimentados»; con islas se refiere a **islas flotantes**.
**Investigación:** [terreno para constructores](../../../investigacion/terreno-para-constructores.md) (con qué batallan en
WorldEdit, VoxelSniper, goBrush, WorldPainter y Axiom; cómo se hacen bien montañas, islas, suavizado, erosión y pintura por
pendiente; con fuentes).

## Problema
Con WorldEdit lo difícil no es el comando sino **cómo crear** (investigación §1):
- Una montaña natural exige escribir matemáticas en `//generate` (senos, escalas a mano) o dominar los pinceles de
  VoxelSniper, de nombres crípticos que cambian entre versiones. No hay un «hazme una montaña».
- No existe nada para una isla flotante: se arma a mano, y la base suele quedar como un disco plano.
- `//smooth` toma cualquier sólido como suelo (árboles, casas) y «derrite»; no hay pintura por pendiente (pasto en lo plano,
  piedra en lo empinado, nieve arriba): eso está en WorldPainter, un programa aparte, o en Axiom, un mod de pago para el
  multijugador.
- El experto quiere lo contrario del novato: semilla, alturas, rugosidad, capas de material por altura y pendiente,
  máscaras y sus propios estilos guardados.

## Dos niveles
- **Novato:** una palabra da algo bonito: `/wc mountain`, `/wc island`, `/wc brush smooth`. Buenos valores por defecto,
  vista previa antes de tocar nada, botones de «construir / otra / cancelar» en el chat.
- **Experto:** lo mismo con todo a la vista y opcional, sin estorbar al novato: `/wc mountain radius=70 height=90 peaks=3
  roughness=0.7 seed=42 style=alpine`, estilos propios en `terrain.yml` (capas por altura y pendiente), máscaras en el
  pincel, la semilla en cada mensaje para repetir un resultado.

## Historias

**H1 — Una montaña de un comando (novato).** Como *constructor novato*, quiero una montaña bonita sin saber nada de ruido.
- CA1: `/wc mountain` pone una montaña **donde miro** (el bloque de suelo al que apunta la mira, hasta `terrain.reach`
  bloques) o, si no miro a nada, bajo mis pies. Tamaños por palabra: `small`, `medium` (de fábrica), `large`, `huge`
  (radio y altura en `terrain.yml`); un número es el radio.
- CA2: Se ve natural con los valores de fábrica: picos con crestas (no un cono liso), el contorno irregular y la base que
  se funde con el suelo que ya hay (no un escalón). Materiales del estilo de fábrica `alpine`: nieve arriba, piedra en lo
  empinado, pasto y tierra en lo plano, piedra adentro.
- CA3: **Solo agrega**: pone bloques donde hay aire, plantas o agua; no pisa casas, árboles ni otra cosa que ya esté.
- CA4: **Primero se ve**: la montaña aparece como vista previa (bloques falsos, solo para mí) y el chat ofrece
  `[Build]` `[Another]` `[Cancel]` (clicables: `/wc confirm`, `/wc reroll`, `/wc cancel`). `/wc reroll` hace otra con
  otra semilla, sin volver a apuntar. La vista previa muestra la **piel** (lo que se ve desde fuera), así una montaña
  grande entra en el tope de la vista previa.
- CA5: Lo que se construye es exactamente lo que mostró la vista previa.

**H2 — Una isla flotante de un comando (novato).** Como *constructor novato*, quiero islas flotantes que no parezcan un
disco.
- CA1: `/wc island` (alias `/wc floating_island`) pone una isla **en el aire delante de mí**: el centro a una distancia
  que no me toca; si vuelo, la cima a la altura de mis pies, y si estoy en el suelo, lo bastante alta para que flote
  sobre mí (con sus estalactitas). Tamaños `small`, `medium` (de fábrica), `large`, `huge`, o un número.
- CA2: Forma: cima casi plana con relieve suave, contorno irregular, base en cono invertido rugosa (más honda en el
  centro), estalactitas que cuelgan debajo y raíces colgantes cerca del borde. Capas del estilo `meadow`: pasto, tierra,
  piedra con algo de andesita; con árboles pequeños (tronco y hojas que no se caen).
- CA3: Solo pone bloques en el aire; nada de la isla toca el suelo si se pidió en el aire libre.
- CA4: Vista previa, botones y `reroll` como H1 CA4–CA5.

**H3 — Un pincel de terreno (novato).** Como *constructor*, quiero modificar el terreno a mano alzada.
- CA1: `/wc brush [mode] [radius] [strength]` me da (o ajusta, si ya lo tengo en la mano) el **pincel de terreno**, un ítem
  marcado como la varita: un ítem igual sin marca no es pincel. Su nombre dice modo, radio y fuerza.
- CA2: Clic derecho = una pasada donde miro (hasta `terrain.brush.range` bloques). Clic izquierdo = el siguiente modo. Mientras
  lo tengo en la mano veo un **círculo de partículas** del tamaño del pincel donde caería (solo yo).
- CA3: Modos: `raise` (sube), `lower` (baja), `smooth` (suaviza), `flatten` (aplana a la altura del centro), `erode`
  (erosión: los escalones de más de un bloque se derrumban hacia abajo) y `paint` (pinta la superficie). Todos trabajan sobre
  el **mapa de alturas** del suelo natural: árboles, plantas y construcciones no cuentan como suelo (no se «derriten»).
- CA4: Al subir o bajar, la superficie conserva su material (el pasto sigue siendo pasto arriba, la tierra debajo);
  lo que crece encima (plantas) no queda flotando.
- CA5: Cada pasada es una obra del motor (límites, historial, `undo`); una pasada en marcha no se encima con otra mía (los
  clics mientras tanto se ignoran).

**H4 — Control fino (experto).** Como *constructor experimentado*, quiero dirigir el resultado.
- CA1: Opciones `clave=valor` en cualquier orden, todas opcionales, con autocompletado y «did you mean»:
  - montaña: `radius`, `height`, `peaks` (1–7), `roughness` (0–1), `ridges` (0–1), `sharpness` (0,5–3), `crater` (0–0,6),
    `seed`, `style`;
  - isla: `radius`, `depth` (hondura de la base respecto al radio), `relief`, `spikes` (0–1), `roots` (0–1), `trees` (0–12),
    `seed`, `style`;
  - las dos: `at=x,y,z` (con `~`) y `world=<mundo>` (también desde la consola) y `--now` (sin vista previa).
- CA2: La misma semilla con las mismas opciones da exactamente lo mismo; cada mensaje dice la semilla
  (`seed=…`) para repetirlo.
- CA3: **Estilos** en `terrain.yml`: capas de material de la superficie hacia adentro, cada capa con reglas por **altura
  relativa** (0 = pie, 1 = cima), **pendiente** en grados y altura absoluta; cada bloque es un patrón de Worldcraft
  (`70% stone, 30% andesite`). Un estilo puede traer opciones (`volcano` trae `crater`) y los bloques de estalactitas,
  raíces y árboles. `/wc styles` los lista. Errores al cargar con archivo, ruta y «did you mean»; un estilo malo se omite.
- CA4: Pincel con `mask=` (solo cambia columnas cuya superficie pasa la máscara), con un patrón o `style=` para `paint`,
  y con fuerza de 0,1 a 1.
- CA5: Tamaños de fábrica en `terrain.yml` (`sizes`), editables por el dueño.

**H5 — Todo pasa por el motor de la 025.** Como *dueño del servidor*, quiero que el terreno no rompa nada.
- CA1: Montañas, islas y pinceles son obras como `set`: por tandas con el presupuesto por tick, en el historial, con
  `undo`/`redo`, `cancel`, `jobs` y los límites del grupo (`max_blocks`, `max_jobs`, mundos, bloques prohibidos).
- CA2: Leer el suelo (para mezclar la montaña con el terreno o para el pincel) también va por tandas; el cálculo del
  ruido se hace **fuera del hilo principal**.
- CA3: Una obra demasiado grande para el grupo se rechaza antes de tocar nada y dice cuántos bloques son y el tope (el
  tamaño `medium` cabe en el grupo `default` de fábrica).
- CA4: El radio tiene tope (`terrain.max_radius`, `terrain.brush.max_radius`) para que leer el suelo nunca sea enorme.

**H6 — Administración.** Como *dueño del servidor*, quiero configurarlo y entenderlo.
- CA1: `terrain.yml` se crea al primer arranque, en inglés y comentado, con estilos de ejemplo (`alpine`, `desert`,
  `volcano`, `hills`, `meadow`, `snowy`, `end`); carga sin avisos; `reload` y `validate` lo incluyen.
- CA2: Sección `terrain` en `config.yml` (vista previa primero, alcance, topes, máscara de suelo) validada con rangos.
- CA3: `help` de cada subcomando nuevo con un ejemplo; autocompletado de tamaños, estilos, modos y claves.
- CA4: Mensajes nuevos en `messages.yml` (MiniMessage, traducibles), con los botones clicables.

## Fuera de alcance (al [roadmap](../../roadmap.md))
- Ríos y caminos a lo largo de un trazo; cordilleras y colinas sobre toda la selección; mapas de alturas desde imágenes PNG.
- Erosión hidráulica (gotas); cuevas, arcos y salientes (densidad 3D); cascadas y lagos en la isla.
- Menú de inventario para el pincel (hoy: comando y clic izquierdo); varias pasadas del pincel juntas en una sola entrada del
  historial (hoy cada pasada es una obra).
- Objetos esparcidos (árboles grandes, rocas, esquemas) por reglas; biomas.
- Exportar e importar estilos como archivos sueltos.

## Decisiones (CLARIFY)
Propuesta de la IA (2026-11-01), pendiente del dueño.
| # | Pregunta | Propuesta |
|---|---|---|
| 1 | Alcance (lección 004) | Montaña, isla flotante, pincel con 6 modos, estilos en YAML, `reroll`. Ríos, menú del pincel e imágenes, después |
| 2 | Nombres de los comandos | `/wc mountain`, `/wc island` (alias `floating_island`), `/wc brush`, `/wc reroll`, `/wc styles`: palabras en inglés, sin símbolos |
| 3 | ¿Vista previa siempre? | Sí para montaña e isla (`terrain.preview_first: true`; `--now` la salta). El pincel no: su vista previa es el círculo |
| 4 | Opciones del experto | `clave=valor` (legibles y autocompletables), no banderas con símbolos |
| 5 | ¿La montaña pisa lo que hay? | No: solo llena aire, plantas y agua. El pincel `lower` sí quita suelo natural (es su trabajo) |
| 6 | Ítem del pincel | Pala de oro marcada (la varita es hacha de oro) |
| 7 | Tamaños de fábrica | Montaña `small` r 20 · `medium` r 32 · `large` r 56 · `huge` r 90; isla `small` r 8 · `medium` r 14 · `large` r 24 · `huge` r 40; `medium` cabe en 50 000 bloques |
| 8 | Ruido | Propio (gradiente 2D con fBm, crestas y alabeo), escrito desde cero: nada de código GPL (WorldEdit, FAVS, goBrush, WorldPainter) ni de Axiom |
| 9 | Árboles en la isla | Sí, pequeños y hechos por el plugin (tronco + hojas persistentes), dentro de la misma obra (se deshacen con ella) |
| 10 | Permiso | El mismo `worldcraft.use`: el terreno es editar |

## Preguntas que solo el dueño puede responder
1. ¿Te gustan los nombres `/wc mountain`, `/wc island` y `/wc brush`, o prefieres otros (por ejemplo `/wc terrain …`)?
2. ¿La vista previa con botones antes de construir es lo que quieres para el novato, o prefieres que se construya al
   momento y se deshaga con `undo`?
3. ¿Qué estilos te gustaría de fábrica además de alpino, desierto, volcán, colinas, pradera, nevado y End?
4. Lo siguiente del terreno: ¿ríos, menú del pincel, cordilleras sobre la selección o mapas de alturas desde imágenes?

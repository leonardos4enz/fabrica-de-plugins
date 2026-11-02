# Spec 035 — Worldcraft: lo de FAWE para crear (cuevas, minerales, imágenes, colores)

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a) (ver [verify](verify.md)) · CLARIFY respondido por el dueño (2026-11-02: las cuatro piezas; imagen desde la carpeta y desde
un link apagado de fábrica; de pie o acostada según la mirada; la tabla de colores de SkinStatues) · **Plugin:**
Worldcraft (`/wc`, hoy 0.10.0 → 0.11.0) · **Depende de:** [025](../025-worldcraft-nucleo/spec.md) (motor, deshacer,
límites), [029](../029-worldcraft-paridad-worldedit-2/spec.md) (patrones que cambian el bloque actual, `^tipo`),
[030](../030-worldcraft-selecciones-no-cuboides/spec.md) (selecciones), [034](../034-worldcraft-formulas/spec.md) (hilo de
cálculo, `Noise3`, `/wc timeout`); de SkinStatues, [023](../023-skinstatues-skin-por-link/spec.md) (descarga segura por
link) y la tabla de colores de bloques de la [021](../021-skinstatues-nucleo/spec.md) · **Origen:** la partición aceptada
por el dueño en la 029 (035 = lo de FAWE para crear, marcada «M–A»).
**Investigación:** [paridad con WorldEdit y FAWE](../../../investigacion/paridad-worldedit-fawe.md) §2.3 (`//caves`,
`//ore`, `//ores`, `//img`), §2.12 (`#color`, `#averagecolor`, `#saturate`, `#desaturate`, `#darken`, `#lighten`) y §4.2
(fila 035).

## Problema
- FAWE trae generadores que WorldEdit no tiene y que los constructores usan para mapas de supervivencia y de
  aventura: cuevas en un terreno hecho a mano (`//caves`), minerales repartidos como en el juego (`//ore`, `//ores`) y una
  imagen construida con bloques (`//img`, solo desde imgur). Además, patrones que eligen el bloque por su color
  (`#color[red]`, `#darken`, `#saturate`…) para pintar o sombrear lo construido. Worldcraft no tiene nada de eso.
- Una imagen desde un link es una petición de red que sale del servidor: sin las guardas de SkinStatues (solo sitios
  públicos, tope de tamaño, apagado de fábrica) sirve para atacar la red local del dueño (SSRF) o para tumbar el
  servidor con una imagen enorme.
- Todo tiene que pasar por el motor de la 025: 8 ms por tick, vista previa, deshacer en disco y límites por grupo.

## Costo estimado (lección 004)
| Pieza | Costo |
|---|---|
| Tabla de colores (la de SkinStatues, editable) y patrones por color | ~20 % |
| `ore` y `ores` (vetas como las del juego, deepslate según la roca) | ~20 % |
| `caves` (túneles y salas con ruido 3D, calculadas fuera del hilo del servidor) | ~20 % |
| `image` desde la carpeta y desde un link (red segura), de pie o acostada | ~25 % |
| Pruebas en el juego y documentación | ~15 % |

Una spec larga (como la 033 o la 034). El dueño eligió las cuatro piezas; `#anglecolor` (que mira la pendiente del
terreno) va al roadmap.

## Historias

**H1 — Patrones por color.** Como *constructor*, quiero elegir bloques por su color.
- CA1: `#color[red]`, `#color[#3a7bd5]` pone el bloque de la tabla más parecido a ese color (como lo ve el ojo: CIELAB).
  Los 16 colores de los tintes del juego tienen nombre (`white`, `light_gray`, `red`…).
- CA2: Sobre el bloque que ya está (como `^tipo` de la 029): `#darken` y `#lighten` (un bloque de color parecido, más
  oscuro o más claro), `#desaturate[50]` (le quita ese % de color; de fábrica 100), `#saturate[#ff0000]` (lo tiñe:
  multiplica los colores, como FAWE) y `#averagecolor[#ff0000]` (el promedio de los dos). Un bloque que no está en la tabla
  se deja como está.
- CA3: Se mezclan con lo demás (`50% #darken, 50% stone`), sirven en todos los comandos con patrón, en los pinceles y en
  los *presets*. Todo error se dice antes de tocar el mundo con «did you mean» (`#colr`, un color que no existe).
- CA4: La tabla son los 145 bloques que midió SkinStatues; el dueño quita bloques o agrega o cambia colores en
  `config.yml` (sección `colors`). Un bloque prohibido al grupo (`blocked_blocks`) nunca se pone.

**H2 — Minerales.** `/wc ore <máscara> <patrón> [tamaño] [frecuencia] [rareza] [y mín] [y máx]` y `/wc ores [máscara]`.
- CA1: `ore` reparte vetas del patrón en la selección (cualquier forma, 030), solo donde pasa la máscara (como FAWE):
  `frecuencia` intentos por chunk (de 16×16), cada uno con `rareza` % de salir, de `tamaño` bloques, entre las dos alturas.
  De fábrica: tamaño 8, frecuencia 10, rareza 100, toda la altura de la selección.
- CA2: `ores` reparte los minerales del juego (carbón, hierro, cobre, oro, redstone, lapislázuli, diamante, esmeralda)
  con sus tamaños, cantidades y alturas, en la roca de la máscara (de fábrica la piedra del mundo). En deepslate y tuff
  pone la versión deepslate del mineral (FAWE pide `-d`). La lista se cambia en `config.yml` (sección `ores`).
- CA3: Se calcula fuera del hilo del servidor y es una obra del motor: límites del grupo, máscara global, `--preview`,
  tandas de 8 ms, historial y `undo`. Dice cuántas vetas y bloques puso.

**H3 — Cuevas.** `/wc caves [tamaño] [frecuencia] [--rooms]`.
- CA1: Talla una red de túneles que se cruzan en la selección (ruido 3D, como las cuevas «espagueti» del juego desde
  1.18): `tamaño` el ancho de los túneles (1–10, de fábrica 3), `frecuencia` cuántos (1–100, de fábrica 40). `--rooms`
  agrega salas grandes (las «queso» del juego). Cada vez es distinta (otra semilla).
- CA2: Solo talla lo que pasa la máscara de cuevas (`caves.mask`, de fábrica roca, tierra y grava: no rompe
  construcciones, agua ni bedrock). Por debajo de `caves.lava_y` lo tallado se llena de lava, como el juego.
- CA3: Se calcula fuera del hilo del servidor con el tope de tiempo de `/wc timeout` y es una obra del motor (H2 CA3).

**H4 — Imagen.** `/wc image <archivo|link> [tamaño] [--floor|--wall]` (alias `img`).
- CA1: Construye la imagen (PNG, JPG o GIF) con los bloques de la tabla de colores, cada píxel el bloque más parecido;
  lo transparente se deja como está. `tamaño` es el lado más largo en bloques (de fábrica el de la imagen, como mucho
  `images.max_size`); se reduce promediando los píxeles.
- CA2: Mirando abajo (más de 45°) la imagen va **acostada** en el suelo desde los pies del jugador hacia donde mira (como
  FAWE); si no, va **de pie** como un mural frente al jugador, a 2 bloques, centrada en él y desde la altura de sus pies.
  `--floor` y `--wall` la fuerzan. Una imagen nunca queda al revés ni espejada para quien la mira al construirla.
- CA3: Un archivo es un nombre de `plugins/Worldcraft/images/` (sin rutas: `../` no sale de la carpeta), con
  autocompletado y «did you mean». `/wc image list` los lista.
- CA4: Un link (`https://…`) solo si el dueño lo enciende (`images.links`, **apagado de fábrica**) y con el permiso
  `worldcraft.image.link` (de fábrica solo OP). La descarga es la de SkinStatues 023: solo sitios públicos (nunca la red
  local ni el propio servidor, en cada redirección), puerto 443, hasta 3 redirecciones, `images.allowed_hosts`,
  tope de `images.max_download_kb` y de píxeles antes de decodificar, 8 s, fuera del hilo del servidor.
- CA5: Es una obra del motor (H2 CA3), con `--preview` para verla antes de ponerla.

**H5 — Seguridad, ayuda y documentación.**
- CA1: Secciones `colors`, `ores`, `caves` e `images` en `config.yml`, revisadas por `/wc validate` (fuera de rango
  avisa y usa el de fábrica; claves desconocidas con «did you mean»). Un `config.yml` de la 0.10.0 sin ellas carga igual.
- CA2: `/wc help <subcomando>` con ejemplo para cada uno nuevo; autocompletado de opciones, archivos y colores.
- CA3: README de Worldcraft («Coming from WorldEdit/FAWE», configuración, cómo actualizar), README raíz, tabla de
  paridad y roadmap.

## Fuera de alcance (al [roadmap](../../roadmap.md))
`#anglecolor` (mira la pendiente del terreno), tramado (*dithering*) de la imagen, imágenes en pinceles (FAWE `/brush
image`, mapas de alturas), los gusanos de cuevas viejos de FAWE (otros parámetros: `systemFrequency`, `pocketChance`…),
`//blob` como comando (ya está `/wc brush rock`), caché de imágenes descargadas.

## Decisiones (CLARIFY)
| # | Pregunta | Propuesta de la IA | Respuesta del dueño (2026-11-02) |
|---|---|---|---|
| 1 | ¿Qué entra? (colores ~20 %, minerales ~20 %, cuevas ~25 %, imagen ~30 %) | Las cuatro; `#anglecolor` al roadmap | **Las cuatro** |
| 2 | ¿De dónde sale la imagen? | La carpeta siempre; un link solo si el dueño lo enciende, con la red segura de SkinStatues | **Link y carpeta** |
| 3 | ¿Cómo se pone la imagen? | Según la mirada: abajo acostada, al frente de pie | **Según la mirada** |
| 4 | ¿Qué bloques usan los colores? | La tabla de SkinStatues (145), editable | **La tabla de SkinStatues** |

Decisiones de la IA: las cuevas usan ruido 3D (túneles donde dos ruidos valen casi cero, como el juego desde 1.18) y no
los gusanos de FAWE (más simple de calcular por bloque, con el `Noise3` de la 034, y se ve como las cuevas actuales);
`ores` elige deepslate según la roca en vez de pedir `-d`; el código de red se copia de SkinStatues a Worldcraft (cada
plugin es un jar aparte) — ver [ADR 0007](../../../adr/0007-red-segura-copiada.md).

## Preguntas que se le hicieron al dueño
1. ¿Qué entra? (las cuatro · sin las cuevas · sin la imagen · solo colores y minerales)
2. ¿De dónde sale la imagen? (link y carpeta · solo la carpeta · solo link)
3. ¿Cómo se pone la imagen en el mundo? (según la mirada · solo en el suelo)
4. ¿Qué bloques usan los colores? (la tabla de SkinStatues · medir más bloques)

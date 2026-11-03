# Spec 040 — Worldcraft: lo que le falta de WorldEdit 7.3.19 (los 28 «not yet»)

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a) (ver [verify](verify.md)) · CLARIFY respondido por el dueño (2026-11-03, abajo) · **Plugin:** Worldcraft (0.14.0 → 0.15.0)
· **Depende de:** [025](../025-worldcraft-nucleo/spec.md) (motor), [030](../030-worldcraft-selecciones-no-cuboides/spec.md)
(selección convexa), [032](../032-worldcraft-pinceles-y-herramientas/spec.md) (pinceles y herramientas),
[033](../033-worldcraft-biomas-arboles-estructuras/spec.md) (árboles capturados, biomas), [035](../035-worldcraft-creacion-fawe/spec.md)
(carpeta `images/`), [036](../036-worldcraft-regen-snapshots/spec.md) (copias), [038](../038-worldcraft-publicacion/spec.md)
(el recuento), [039](../039-worldcraft-comandos-con-dos-barras/spec.md) (los `//`) · **Origen:** la tabla de la 038: 28
comandos de WorldEdit 7.3.19 están «not yet» en [commands.md](../../../worldcraft/presentacion/commands.md).

## Problema
Worldcraft tiene 129 de los 171 comandos de WorldEdit 7.3.19. De los 42 que faltan, 4 no se harán a propósito, 5 no hacen
falta y 5 están «en parte»; quedan 28 que nadie hizo todavía. Quien viene de WorldEdit escribe `//curve`, `//fillr`,
`/brush snow`, `/range`, `/chunkinfo` y recibe «Unknown command».

## CLARIFY (respuestas del dueño, 2026-11-03)
| Pregunta | Respuesta |
|---|---|
| ¿Qué grupos? (a) construir, (b) pinceles, (c) herramientas y selección, (d) información; se recomendó a+c+d | **Los cuatro** |
| (durante la spec) `/wc up` | «el comando up haz que agregue un bloque de cristal abajo del usuario para que sea piso» (también en creativo) |
| `//feature` y `/brush feature`: Paper no tiene API para *features*. ¿Fuera, mundo temporal o NMS? | **NMS** («total no todo se rompería, solo lo no compatible»): aislado en una clase, por reflexión, y si una versión nueva lo rompe solo esos comandos dicen «not available on this server version» ([ADR 0009](../../../adr/0009-nms-aislado.md)) |

## Costo estimado (lección 004)
| Grupo | Comandos | Costo |
|---|---|---|
| (a) construir | `//curve`, `//snowsmooth`, `//fillr`, `//feature`, `//update` | M (feature y update con el puente NMS: M) |
| (b) pinceles | `snow`, `snowsmooth`, `biome`, `morph`, `dilate`, `heightmap`, `feature`, `apply … item`, `paint … item` | M–A |
| (c) herramientas y selección | `/toggleplace`, `/placement`, `/toggleeditwand`, `/range`, `/tracemask`, `/tool stacker` | B–M |
| (d) información | `/chunkinfo`, `/listchunks`, `/searchitem`, `/schem formats`, `/worldedit version`, `report`, `trace`, `tz` | B |

## Historias

**H1 — Construir (grupo a).**
- CA1: `/wc curve <patrón> [grosor] [--hollow]` (`//curve -h`): una curva suave (Catmull-Rom, la de WorldEdit con tensión,
  sesgo y continuidad 0) que pasa por los puntos de la selección convexa o de polígono, en el orden en que se marcaron, con
  el grosor como en `line`; `--hollow` deja solo la cáscara. Con otra selección dice que hace falta `convex` o `poly`.
- CA2: `/wc snowsmooth [pasadas] [--mask <máscara>]` (`//snowsmooth -m`): alisa la nieve de la selección contando las capas
  como alturas de octavos de bloque; solo cambia capas de nieve (pone, quita o cambia cuántas), nunca el suelo.
- CA3: `/wc fillr <patrón> <radio> [hondo]` (`//fillr`): rellena el hueco desde los pies hacia los lados y hacia abajo,
  dentro de una **esfera** del radio (la de `/wc fill` es un cilindro), solo el aire conectado.
- CA4: `/wc feature <nombre> [--preview]` (`//feature`): uno de los *features* del juego (`minecraft:mossy_rock`,
  `amethyst_geode`, `huge_red_mushroom`…, con autocompletado) donde se pone una forma (los pies o la esquina 1, H3 CA1),
  capturado sin tocar el mundo y puesto por el motor (vista previa, límites, deshacer). Uno que no cabe lo dice; uno que
  escribe directo en el chunk (los de minerales) se rechaza y sugiere `/wc ore`.
- CA5: `/wc update` (`//update`): recalcula la forma de cada bloque de la selección según sus vecinos (vallas, muros,
  paneles, escaleras, redstone) y pone por el motor los que cambian (deshacer).
- CA6: Si el puente NMS no anda en este servidor, `feature`, `brush feature` y `update` dicen «not available on this
  server version» y el resto de Worldcraft funciona igual; el selftest y `/wc version` dicen si está.

**H2 — Pinceles (grupo b).** Cada uno es un pincel de la 032 (ítem de la mano, límites de quien hace clic, máscara `mask=`,
deshacer), con el nombre de WorldEdit.
- CA1: `/wc brush snow [radio] [--stack]`: nieve sobre lo que está en la esfera donde mira (como `/wc snow`); `--stack`
  (`-s` de WorldEdit) suma una capa sobre la nieve que ya hay.
- CA2: `/wc brush snowsmooth [radio] [pasadas]`: la nieve de la esfera, alisada como en H1 CA2.
- CA3: `/wc brush biome [radio] <bioma>`: el bioma en las celdas de la esfera (como `setbiome`).
- CA4: `/wc brush morph [radio] [caras-erosión] [pasadas-erosión] [caras-dilatar] [pasadas-dilatar]` y `dilate [radio]
  [caras] [pasadas]` (como WorldEdit; `erode` ya es el pincel de terreno de la 026): erosionar quita lo sólido con al
  menos N caras al aire; dilatar llena el aire con al menos N caras sólidas con el bloque más común de alrededor.
- CA5: `/wc brush heightmap <imagen> [radio] [alto] [--erase] [--flatten]`: una imagen gris de `images/` (la carpeta de
  la 035) estirada sobre el círculo: blanco sube `alto` bloques, negro nada; `--erase` baja; `--flatten` solo hasta esa
  altura. Copia el bloque de la cima.
- CA6: `/wc brush apply [sphere|cube|cyl] [radio] item <ítem>` y `/wc brush paint [forma] [radio] [densidad] item <ítem>`:
  usar un ítem en cada bloque de la forma (o en una parte al azar, `paint`). Lo que hace cada ítem sale de una tabla en
  `item-uses.yml` (azada → tierra de cultivo, pala → camino, hacha → tronco sin corteza, panal → cobre encerado, harina de
  hueso → pasto y flores encima, mechero → fuego encima…), que el dueño puede ampliar; un ítem que no está lo dice y lista
  los que hay. `/wc brush paint` sin `item` sigue siendo el pincel de terreno.
- CA7: `/wc brush feature <nombre> [radio] [densidad]`: *features* en el círculo donde mira (como `brush forest`).

**H3 — Herramientas y selección (grupo c).**
- CA1: `/wc placement <player|pos1|pos2|center> [x,y,z]` y `/wc toggleplace` (pies ↔ esquina 1): dónde ponen las formas
  (`sphere`, `cyl`, `pyramid`, `cone`), las utilidades alrededor (`fill`, `fillr`, `drain`, `snow`…) y `feature`; el
  desplazamiento se suma. Sin la esquina, lo dice. Solo en memoria, como el portapapeles.
- CA2: `/wc toggleeditwand`: la varita deja de seleccionar (se usa como un hacha normal) o vuelve.
- CA3: `/wc range <bloques>`: hasta dónde mira el pincel o la herramienta de la mano (tope `tools.max_range`).
- CA4: `/wc tracemask <máscara>|off`: el pincel de la mano apunta al primer bloque que cumple la máscara (el agua, `!air`,
  `#leaves`…) en vez del primer bloque sólido.
- CA6 (pedido del dueño durante la spec): `/wc up` (y `ceil`) pone **siempre** un vidrio bajo los pies, también en creativo
  (antes solo si el jugador no podía volar), como `/up` de WorldEdit: sirve de piso. Va por la obra de un bloque, solo si
  ese lugar está libre, y `/wc undo` lo quita.
- CA5: `/wc tool stacker [alcance] [máscara]` (de fábrica 10 y aire): clic derecho repite el bloque hacia la cara que se
  tocó, clic izquierdo hacia el otro lado, hasta el alcance o el primer bloque que no cumple la máscara.

**H4 — Información (grupo d).**
- CA1: `/wc chunkinfo`: el chunk donde está, su archivo de región (`r.X.Z.mca`) y dónde está dentro del chunk.
- CA2: `/wc listchunks [página]`: los chunks de la selección con su archivo de región, por páginas.
- CA3: `/wc searchitem <texto> [página] [--blocks|--items]` (`-b`, `-i`): los ítems y bloques cuyo nombre lo contiene.
- CA4: `/wc schem formats`: los formatos que lee y escribe (Sponge v1, v2, v3; escribe v3) y las carpetas de WorldEdit/FAWE.
- CA5: `/wc version`: Worldcraft, Paper, Java y si están los *features*; `/wc report` (administración) escribe
  `reports/report-<fecha>.txt` con eso, los mundos, los plugins, la configuración, los límites y las obras, y dice dónde
  (no lo sube a ningún lado); `/wc trace [on|off]`: tras cada obra propia dice cuántos bloques cambió, cuántos ya eran
  así, cuántos saltó por una máscara, cuánto tardó y en cuántos ticks; `/wc tz [zona]`: la zona horaria de las fechas de
  las copias (`snapshot list|before|after`).

**H5 — Lo de siempre.**
- CA1: Cada comando nuevo tiene ayuda con ejemplo, autocompletado, su `//` (la 039, también los nombres de una barra de
  WorldEdit: `/toggleplace`, `/placement`, `/range`, `/tracemask`, `/chunkinfo`, `/listchunks`, `/searchitem`, `/tz`), y
  errores con «did you mean».
- CA2: `commands.md`, las descripciones y el póster dicen los números nuevos; `PresentacionTest` los ata a la tabla.
- CA3: README de Worldcraft y raíz, roadmap; capturas: ninguna nueva (lección 004).

## Fuera de alcance
Las 4 «no, a propósito» (`/cs`, `/.s`, `/schem share`, `/delchunks`), lo «en parte» de la 038 y lo propio de FAWE.

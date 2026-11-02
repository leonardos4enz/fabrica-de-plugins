# Spec 028 — Worldcraft: paridad con WorldEdit y FAWE (formas, región, utilidades y navegación)

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a, 18/18 dos veces) (ver [verify](verify.md)) · CLARIFY respondido por el dueño (alcance: A + B + C + E) · **Plugin:** Worldcraft ·
**Depende de:** [025](../025-worldcraft-nucleo/spec.md) (motor por tandas, vista previa, deshacer, límites, patrones y
máscaras), [026](../026-worldcraft-terreno-facil/spec.md) (suelo natural, pincel), [027](../027-worldcraft-esquemas-schem/spec.md) ·
**Origen:** pregunta del dueño (2026-11-01): «¿Worldcraft ya tiene todos los comandos de WorldEdit y FAWE, o en qué siguen
siendo superiores?».
**Investigación:** [paridad con WorldEdit y FAWE](../../../investigacion/paridad-worldedit-fawe.md): tabla fila por fila
sacada del código de WorldEdit 7.3.19 (170 comandos) y FAWE 2.16.2 (271), con permiso del dueño. Es el mismo modelo
que la spec 014 de OmniEvents.

## Problema
- Worldcraft cubre cerca del 20 % de los comandos de WorldEdit: lo básico (seleccionar, rellenar, reemplazar, copiar,
  pegar, esquemas, deshacer). En eso es mejor: sin lag, con vista previa, deshacer en disco y seguro, límites y
  sintaxis legible. Además tiene montañas, islas y un pincel de terreno que WorldEdit no tiene.
- Un constructor que viene de WorldEdit busca enseguida `//sphere`, `//cyl`, `//stack`, `//move`, `//smooth`,
  `//overlay`, `//drain`, `/ascend`, `/thru`… y no los encuentra. Para eso **tiene que volver a WorldEdit**, y ahí no
  tiene vista previa, ni límites, ni deshacer después de un reinicio.
- Hay cosas que Worldcraft no puede reemplazar todavía: **WorldGuard y PlotSquared necesitan el jar de WorldEdit**
  (investigación §3). Esta spec no lo resuelve; lo deja en el roadmap con su costo.

## Propuesta de núcleo (para que el dueño elija)
Cada comando nuevo usa el motor de la 025: tandas dentro de los 8 ms por tick, límites del grupo, bloques prohibidos,
`--preview` y `confirm`, deshacer en disco, mensajes en inglés con «did you mean». Lo que falta es la **forma**: qué
bloques recorre cada comando y qué pone en cada uno. Grupos de la investigación §4:

| Grupo | Comandos | Costo | Recomendación |
|---|---|---|---|
| A. Formas | `sphere`, `hsphere`, `cyl`, `hcyl`, `pyramid`, `hpyramid`, `cone` | Bajo–medio | **Sí** |
| B. Región | `stack`, `move`, `overlay`, `naturalize`, `smooth`, `hollow`, `center`, `line` | Medio | **Sí** |
| C. Utilidades | `drain`, `fixwater`, `fixlava`, `fill`, `removenear`, `replacenear`, `removeabove`, `removebelow`, `extinguish`, `snow`, `thaw`, `green` | Bajo | **Sí** |
| E. Navegación | `unstuck`, `ascend`, `descend`, `ceil`, `thru`, `jumpto`, `up` | Bajo | **Sí** |
| D. Selección e info | `hpos1/2`, `chunk`, `outset`, `inset`, `count`, `distr`, `clearclipboard`, `clearhistory`, `paste --origin/--select`, `copy --mask` | Bajo | Si alcanza |
| F. Máscaras y patrones | `above`/`below`, `exposed`, `surface`, `solid`, `50% chance`; `^tipo` en patrones; `gmask` | Bajo–medio | Si alcanza |

Estimación: A + B + C + E es una spec normal (unos 34 comandos, la mayoría formas nuevas de `EditSource` y su prueba).
D y F suman la mitad de eso. **Recomendación: A + B + C + E**, y D y F en la siguiente spec si no alcanzan en esta
sesión (lección 004).

## Historias

**H1 — Formas (grupo A).** Como *constructor*, quiero hacer esferas, cilindros, pirámides y conos sin seleccionar nada.
- CA1: `/wc sphere <patrón> <radio>` (o `rx,ry,rz` para un elipsoide) centrada donde estoy; `hsphere` hueca, de un bloque
  de grueso (`--thickness n`). `--raised` la apoya en el suelo, como el `-r` de WorldEdit.
- CA2: `/wc cyl <patrón> <radio>[,<radio z>] [alto]` y `hcyl`; `/wc pyramid <patrón> <tamaño>` y `hpyramid`;
  `/wc cone <patrón> <radio> <alto>`. Una altura negativa crece hacia abajo.
- CA3: Las formas son simétricas y sin agujeros en el borde (prueba: contar bloques por capa contra la fórmula, y que
  una hueca no tenga huecos vista desde fuera, como la prueba de cáscara de SkinStatues).
- CA4: Todo lo de la 025: `--preview`, confirmación sobre `confirm.above_blocks`, límites, bloques prohibidos, deshacer.
  Un radio que excede el grupo se rechaza antes de calcular nada.

**H2 — Operaciones de región (grupo B).** Como *constructor*, quiero repetir, mover y acabar lo que tengo seleccionado.
- CA1: `/wc stack [n] [dirección]` repite la selección n veces hacia donde miro (o `up`, `north`…); `--select` deja
  seleccionada la última copia; sin aire copiado salvo `--air`.
- CA2: `/wc move [n] [dirección] [patrón de relleno]` mueve el contenido; lo que queda atrás se llena con aire (o con el
  patrón); `--select` mueve también la selección. Cofres y carteles viajan con su contenido.
- CA3: `/wc overlay <patrón>` pone el patrón encima del bloque más alto de cada columna (no sobre plantas ni hojas:
  usa `terrain.cover` como en la 026).
- CA4: `/wc naturalize` deja pasto arriba, 3 de tierra y piedra debajo, solo en pasto, tierra y piedra (como WorldEdit:
  la arena de un desierto no se vuelve pasto). Pintar con un estilo ya lo hace el pincel `paint` de la 026.
- CA5: `/wc smooth [pasadas]` suaviza la altura de la selección **solo sobre el suelo natural**, igual que el pincel
  `smooth` de la 026 (no derrite árboles ni casas).
- CA6: `/wc hollow [grosor] [patrón]` vacía lo que hay dentro de un objeto cerrado; `/wc center <patrón>` el bloque (o
  los 2–8) del centro; `/wc line <patrón> [grosor]` una línea entre la esquina 1 y la 2.
- CA7: Vista previa, límites, deshacer como en la 025. `move` y `stack` cuentan para el límite lo que escriben.

**H3 — Utilidades alrededor (grupo C).** Como *constructor*, quiero arreglar lo que hay cerca sin seleccionar.
- CA1: `/wc drain <radio>` quita el agua y la lava conectadas al líquido más cercano (`--waterlogged` también saca el
  agua de escaleras y losas); `fixwater`/`fixlava <radio>` dejan quieto el líquido que corre.
- CA2: `/wc fill <patrón> <radio> [profundidad]` rellena el pozo donde estoy, sin subir de mis pies; de fábrica llega tan
  hondo como el radio (como `fillr`).
- CA3: `/wc removenear <máscara> [radio]`, `/wc replacenear <radio> <máscara> with <patrón>`, `/wc removeabove [tamaño]
  [alto]`, `/wc removebelow`.
- CA4: `/wc extinguish [radio]`, `/wc snow [radio]`, `/wc thaw [radio]`, `/wc green [radio]`.
- CA5: Cada una es una edición normal: límites, `--preview`, deshacer. El radio tiene tope (`utilities.max_radius`, 64 de fábrica). El líquido se busca por tandas, sin trabar
  el servidor.

**H4 — Moverse al construir (grupo E).** Como *constructor*, quiero subir, bajar y atravesar sin romper nada.
- CA1: `/wc ascend [n]` y `/wc descend [n]` me llevan al piso libre de arriba o de abajo; `/wc ceil [espacio]` al techo;
  `/wc thru` al otro lado de la pared que miro; `/wc jumpto` al bloque que miro (hasta `terrain.reach`); `/wc unstuck`
  fuera del bloque en el que quedé.
- CA2: `/wc up <n>` me sube n bloques. Si puedo volar, se me activa el vuelo; si no, se pone un vidrio bajo mis pies,
  como en WorldEdit, pero como una obra de un bloque: queda en el historial y `undo` lo quita. Con `ceil` pasa lo mismo.
  (Un bloque falso, visible solo para mí, no sirve: el servidor no lo ve y me echaría por volar.)
- CA3: Solo jugadores, con `worldcraft.navigate` (op de fábrica). Fuera del vidrio de `up` y `ceil`, ningún comando de
  navegación cambia el mundo.

**H5 — Selección e información (grupo D, si alcanza).** `hpos1/hpos2` (la esquina en el bloque que miro), `chunk`,
`outset`/`inset`, `count <máscara>`, `distr` (los 10 bloques más comunes con %, por tandas), `clearclipboard`,
`clearhistory` (pide confirmar), `paste --origin` (en el lugar donde se copió) y `--select`, `copy --mask <máscara>`.

**H6 — Máscaras y patrones (grupo F, si alcanza).** En máscaras: `above <máscara>`, `below <máscara>`, `exposed`,
`surface`, `solid` y `50% chance`. En patrones: `^oak_stairs` (cambia el tipo y conserva el estado) y `^[waterlogged=false]`.
Más `/wc gmask <máscara>` (máscara para todas mis ediciones hasta `gmask off`). Todo con «did you mean» y autocompletado.

**H7 — Documentación de la comparación.** Como *dueño*, quiero saber qué tiene Worldcraft frente a WorldEdit sin leer
el código.
- CA1: El README de Worldcraft tiene una sección «Coming from WorldEdit» con la equivalencia de comandos (`//sphere` →
  `/wc sphere`) y lo que falta, con enlace a la investigación.
- CA2: El roadmap tiene lo que quedó fuera, con su costo.

## Fuera de alcance (al [roadmap](../../roadmap.md))
De la investigación §4, con costo: selecciones no cuboides (M); entidades en copiar, pegar y esquemas, `butcher`,
`remove` (M–A); biomas (M); pinceles de construcción (esfera, cilindro, portapapeles, dispersar) y herramientas de un
clic (árbol, relleno, info, superpico) (M); **API Java, eventos, CoreProtect y puente de selección con WorldGuard** (M);
rollback por zona y tiempo e `inspect` (M); árboles, *features* y estructuras (M); expresiones (`//generate`, `//deform`,
`=máscara`) (A); cuevas, minerales e imágenes de FAWE (M–A); `regen` y snapshots (A).
No se hará: tocar la luz directamente (NMS), `delchunks`, CraftScripts, macros, pinceles que corren comandos, subir a
sitios externos sin permiso del dueño.

## Decisiones (CLARIFY)
Propuesta de la IA (2026-11-01), pendiente del dueño.
| # | Pregunta | Propuesta |
|---|---|---|
| 1 | Alcance (lección 004) | A + B + C + E en esta spec; D y F en la siguiente si no alcanzan |
| 2 | Nombres | Bajo `/wc`, con los nombres de WorldEdit sin barras (`/wc sphere`, `/wc stack`, `/wc up`), para que quien viene de WorldEdit los adivine. Sin comandos sueltos (`/up`, `/thru`): chocan con EssentialsX y otros (investigación de la 025 §5) |
| 3 | Navegación | Permiso propio `worldcraft.navigate`; el vidrio de `up` y `ceil` es una obra de un bloque (un bloque falso echaría al jugador por volar); quien puede volar, vuela |
| 4 | Formas | Centradas en los pies del jugador, como WorldEdit; con `--raised` se apoyan en el suelo |
| 5 | Código | Formas y operaciones escritas desde cero (geometría propia); el código de WorldEdit y FAWE solo se usó para el inventario de la investigación |
| 6 | Compatibilidad | Paper 1.21.4 verificado con la suite; 26.1.2 sigue sin verificar (como la 025–027) |

## Respuestas del dueño (2026-11-01)
1. **Alcance:** A + B + C + E (formas, región, utilidades y navegación). D y F, a la siguiente.
2. **Nombres:** solo dentro de `/wc`.
3. **Navegación:** permiso propio `worldcraft.navigate`, op de fábrica.
4. **Lo siguiente:** la API para otros plugins, «pero no perder de vista entidades ni selecciones no cuboides».

## Preguntas que se le hicieron al dueño
1. **¿Qué grupos hacemos ahora?** Recomiendo formas, región, utilidades y navegación (A, B, C y E). Selección e
   información (D) y las máscaras nuevas (F) quedarían para la siguiente.
2. **¿Los comandos nuevos van solo dentro de `/wc`** (`/wc up 5`), o quieres además comandos cortos sueltos (`/up 5`) que
   se puedan apagar si chocan con otro plugin?
3. **¿Quién puede usar la navegación** (subir, atravesar paredes)? ¿Los mismos que editan, o también otros jugadores?
4. **Lo siguiente después de esta:** ¿la API para otros plugins (que WorldGuard use la selección de Worldcraft y que
   CoreProtect registre las ediciones) o las entidades en copiar y pegar?

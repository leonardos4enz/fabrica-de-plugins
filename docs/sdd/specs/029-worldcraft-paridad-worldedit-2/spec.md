# Spec 029 — Worldcraft: paridad con WorldEdit y FAWE, segunda parte (tabla verificada contra los jars y partición)

**Estado:** CLARIFY respondido por el dueño (029 = selección, información y máscaras; orden 030–036 aceptado) · **Plugin:** Worldcraft (`/wc`, hoy 0.4.0) ·
**Depende de:** [025](../025-worldcraft-nucleo/spec.md) (motor por tandas, vista previa, deshacer, límites, patrones y
máscaras), [026](../026-worldcraft-terreno-facil/spec.md), [027](../027-worldcraft-esquemas-schem/spec.md) (esquemas),
[028](../028-worldcraft-paridad-worldedit/spec.md) (formas, región, utilidades y navegación) ·
**Origen:** pedido del dueño (2026-11-01): cubrir **la totalidad** de los comandos de WorldEdit y FAWE, menos la API
para otros plugins (al final del roadmap), y confirmar contra los jars que no falte ninguno.
**Investigación:** [paridad con WorldEdit y FAWE](../../../investigacion/paridad-worldedit-fawe.md), corregida en esta
spec (§0 y las filas marcadas **029**).

## Problema
- La tabla de la 028 salió del **código fuente** de WorldEdit 7.3.19 y FAWE 2.16.2. Nadie había mirado los **jars**
  que corren en un servidor: si el código fuente tenía comandos comentados o el jar registra otros, la tabla mentía.
- El dueño quiere la totalidad. Con la 0.4.0, Worldcraft cubre cerca del 40 % de WorldEdit; lo que falta (selecciones
  que no son cajas, entidades, biomas, pinceles, árboles, fórmulas, lo de FAWE, `regen`, snapshots) no entra en una spec.
- Deuda de la 028: su `tasks.md` dice que hay pruebas unitarias de `center` y `fixwater`, y no las hay (la prueba
  `H2_CA6_line_y_center` solo prueba `line`; `center` y `fixwater` se calculan dentro de `BuildCommands` y solo se
  probaron en la compuerta 2).
- La spec chica de la 027 (decidida por el dueño) sigue pendiente: `/wc schem import` y el permiso aparte para guardar.

## Verificación contra los jars (hecha en esta sesión)
Tres fuentes cruzadas (detalle en la investigación §0):
1. **Bytecode** (`javap -v` sobre cada clase de los jars): las anotaciones `@Command`, `@Switch`, `@ArgFlag` y `@Arg`,
   las clases de máscaras, patrones y transformaciones con sus prefijos y el enum de selectores. WorldEdit: **171**
   comandos. FAWE: 269 anotados, **244 reales** (`AnvilCommands` y `ListFilters` no son comandos activos).
2. **Código fuente** de las mismas etiquetas: FAWE tiene 4 comandos **comentados** que la 028 contaba (`//lazycut`,
   `loadbrush`, `masks`, `transforms`); WorldEdit, 2 que solo existen en su versión de línea de comandos.
3. **Servidor real** (Paper 1.21.4, uno a la vez): todo lo del bytecode está registrado; `/anvil` de FAWE no existe;
   FAWE no tiene `//pos`, `//update`, `/placement` ni el grupo `/tool` (las herramientas van sueltas y dentro de `/brush`).

Resultado: faltaban en la tabla una docena de comandos (`/brush none`, `/tool none`, `brush apply|paint item`,
`/brush pull`, `/snapshot use|sel|before|after`, `/worldedit version|trace|cui|tz`…), 3 selectores de FAWE (`hull`,
`list`, `magic`), la máscara `^[estado]` de WorldEdit, varias máscaras y patrones de FAWE (`~2d`, `&`, `#nox`, `#mask`,
`#solidspread`…) y ~40 flags que cambian lo que hace un comando.
Sobraban 4. El jar de FAWE quedó fijado con su sha256 en `tests-e2e/lib/plugins-extra.mjs` (aprobado por el dueño),
como plugin alternativo que solo se instala si una suite lo pide (choca con WorldEdit).

## Partición propuesta (para que el dueño elija)
Costo: **B** horas · **M** una spec normal · **A** más de una spec o con riesgo de que Paper no lo permita.

| Spec | Grupo | Qué entra | Costo | Recomendación |
|---|---|---|---|---|
| **029** | Selección, información y máscaras + la spec chica de la 027 + la deuda de la 028 | ver H1–H5 abajo | M | **Esta** |
| 030 | Selecciones que no son cajas | polígono, elipsoide, esfera, cilindro, convexa, `extend`; la «mágica» de FAWE; contorno de cada forma; los comandos de región sobre cualquier forma | M | Siguiente (el dueño pidió no perderlas de vista) |
| 031 | Entidades | soportes, marcos, cuadros y criaturas en `copy`/`paste`/`stack`/`move` y en los `.schem`; `butcher`, `remove`; deshacer que las devuelve | M | Paper 1.21.4 las copia sin NMS (`Entity#createSnapshot`, `EntityFactory`) |
| 032 | Pinceles de construcción y herramientas | pincel de esfera, cilindro, portapapeles, dispersar, rocas, gravedad; herramientas de un clic (árbol, relleno, reemplazo, info, superpico) | M | Reusa el pincel de la 026 |
| 033 | Biomas, árboles y estructuras | `biomeinfo`, `setbiome`, máscara de bioma, biomas al copiar y pegar; `forest`, `flora`, `forestgen`; estructuras del juego | M | `generateTree` de Paper deja pasar cada bloque por el motor (vista previa y deshacer) |
| 034 | Fórmulas | `generate`, `deform`, `=máscara`, `calculate` | A | Un lenguaje de fórmulas con tope de tiempo |
| 035 | Lo de FAWE para crear | cuevas, minerales, imágenes desde un link, patrones por color | M–A | |
| 036 | `regen` y snapshots | terreno original y copias de seguridad | A | La API de Paper no regenera: mundo temporal con la misma semilla |
| final | API para otros plugins | | M | Decisión del dueño (028) |

Estimación: 029–033 son cinco specs normales; 034–036, tres o cuatro más. Recomendación: la 029 con selección,
información y máscaras (lo que más se escribe al construir y lo que el motor ya casi resuelve), y seguir en el orden de
la tabla.

## Historias (si el dueño elige la recomendación)

**H1 — Selección e información.** Como *constructor*, quiero ajustar y medir la selección sin moverme.
- CA1: `/wc hpos1` y `hpos2` ponen la esquina en el bloque que miro (hasta `terrain.reach`), sin estar a alcance de clic.
- CA2: `/wc chunk` selecciona el chunk donde estoy, de lo más bajo a lo más alto del mundo; `--chunk-coords x z` uno dado;
  `--expand` agrega el chunk a la selección que ya hay (como `-s`).
- CA3: `/wc outset <n>` y `/wc inset <n>` agrandan o achican hacia todos los lados; `--horizontal` y `--vertical` como
  `-h` y `-v`. Una selección que se achica de más se rechaza con un mensaje claro.
- CA4: `/wc trim [máscara]` ajusta la selección a los bloques que cumplen la máscara (de fábrica, todo menos aire).
- CA5: `/wc count <máscara>` dice cuántos bloques la cumplen; `/wc distr [--states] [--clipboard]` los 10 más comunes con
  su % (y «… y N más»). Los dos leen por tandas, sin trabar, y respetan los límites de lectura.
- CA6: `/wc clearclipboard` y `/wc clearhistory` (pide `confirm`, porque borra el historial en disco).
- CA7: `/wc paste --origin` pega donde se copió; `--select` deja seleccionado lo pegado; `--only-select` selecciona sin
  pegar (`-n`). `/wc copy --mask <máscara>` y `cut --mask` copian solo lo que cumple la máscara (el resto, aire).

**H2 — Máscaras con vecinos y probabilidad.** Como *constructor*, quiero decir «la piedra que tiene aire encima».
- CA1: En toda máscara: `above <máscara>` (el bloque de abajo cumple…, es decir, está encima de), `below <máscara>`,
  `exposed` (toca aire por alguna cara), `surface` (sólido con aire encima), `solid` y `50% chance`. Se combinan con lo
  de hoy: `stone above grass_block`, `!exposed`, `stone, 30% chance`.
- CA2: Lo que mira vecinos lee la foto de la obra (`Edits.read`), no el mundo a mitad de edición: el resultado no depende
  del orden en que se escriben los bloques.
- CA3: `^[estado]` como máscara: cualquier bloque con ese estado (`^[waterlogged=true]`).
- CA4: «did you mean» y autocompletado para las palabras nuevas; errores antes de tocar nada.

**H3 — Patrones que conservan.** Como *constructor*, quiero cambiar las escaleras de roble a piedra sin girarlas.
- CA1: `^stone_stairs` cambia el tipo y conserva el estado que el tipo nuevo acepte (`facing`, `half`, `waterlogged`).
- CA2: `^[waterlogged=false]` cambia solo esa propiedad y deja el tipo.
- CA3: Se mezclan con porcentajes: `50% ^stone_stairs, 50% ^cobblestone_stairs`.

**H4 — Máscara global.** `/wc gmask <máscara>` se aplica a todas mis ediciones (además de la máscara de cada comando)
hasta `/wc gmask off`; `/wc gmask` sin nada dice cuál tengo. Cada obra la menciona al confirmar, para que no sorprenda.

**H5 — Esquemas: importar y permiso de guardar (la spec chica de la 027).**
- CA1: `/wc schem import` copia los esquemas de las carpetas de WorldEdit y FAWE a `plugins/Worldcraft/schematics/` sin
  borrar los originales ni pisar uno que ya exista (lo dice); `--overwrite` sí pisa.
- CA2: `worldcraft.schematic` solo lista y carga; guardar pide `worldcraft.schematic.save` (op de fábrica). Quien tenía
  `worldcraft.schematic` y guardaba lo pierde: el README lo avisa en «Upgrading».
- CA3: La ayuda muestra `schem` y `schematic`.

**H6 — Deuda de la 028.** Pruebas unitarias de `center` (1, 2, 4 u 8 bloques según la paridad de cada lado) y de
`fixwater`/`fixlava` (el líquido que corre conectado queda quieto; no cruza a otro lago ni sube del más alto), moviendo
el cálculo a funciones que se puedan probar sin Bukkit. `tasks.md` de la 028 se corrige.

**H7 — Documentación.** La tabla verificada en `docs/investigacion/`; el README de Worldcraft («Coming from WorldEdit»)
con lo nuevo; el roadmap con la partición.

## Fuera de alcance (al [roadmap](../../roadmap.md))
La partición de arriba (030–036 y la API al final), más lo que el dueño no pidió ahora: rollback por zona y tiempo e
`inspect`, multi-portapapeles y pegar al azar, `curve`, `snowsmooth`, `fall`, `fixblocks`, `update`, `revolve`, más
patrones y transformaciones de FAWE, `//limit` personal, `toggleplace`.
No se hará: luz directa (NMS), `delchunks`, CraftScripts, macros, pinceles que corren comandos, `//test`, subir a sitios
externos sin permiso del dueño, `AnvilCommands` de FAWE (no los registra).

## Decisiones (CLARIFY)
Propuesta de la IA (2026-11-01); respuestas del dueño abajo.
| # | Pregunta | Propuesta |
|---|---|---|
| 1 | Partición y orden | 029 selección, información y máscaras; después 030 selecciones, 031 entidades, 032 pinceles, 033 biomas y árboles, 034 fórmulas, 035 FAWE, 036 `regen`/snapshots, API al final |
| 2 | Spec chica de la 027 | Dentro de la 029 (es baja y toca lo mismo: comandos y permisos) |
| 3 | Permiso de guardar | `worldcraft.schematic.save` aparte, op de fábrica; quien solo tenía `worldcraft.schematic` deja de poder guardar |
| 4 | Nombres | Bajo `/wc`, con los nombres de WorldEdit y flags largos legibles (`--horizontal` en lugar de `-h`) |

## Respuestas del dueño (2026-11-01)
1. **Esta spec:** selección, información y máscaras (la recomendación), con la spec chica de la 027 y la deuda de la 028.
2. **Orden:** el propuesto: 030 selecciones que no son cajas, 031 entidades, 032 pinceles, 033 biomas y árboles,
   034 fórmulas, 035 lo de FAWE, 036 `regen` y snapshots, la API al final.
3. **Permiso de guardar:** aparte (`worldcraft.schematic.save`, op de fábrica), sin heredarse de `worldcraft.schematic`;
   el README avisa al actualizar.

## Preguntas que se le hicieron al dueño
1. ¿Con cuál grupo empezamos en la 029? (selección y máscaras · selecciones no cuadradas · entidades · pinceles)
2. ¿El orden de las que siguen te parece bien?
3. Con el permiso nuevo para guardar, quien hoy tiene `worldcraft.schematic` deja de poder guardar. ¿Lo hacemos así?

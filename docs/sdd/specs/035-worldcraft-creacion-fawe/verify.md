# Verify 035 — Lo de FAWE para crear (Worldcraft 0.11.0)

**Fecha:** 2026-11-02 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar (como la
025–034). Alcance elegido por el dueño en el CLARIFY: las cuatro piezas; imagen desde la carpeta y desde un link apagado
de fábrica; de pie o acostada según la mirada; la tabla de colores de SkinStatues.

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew build`: en verde para los tres plugins (OmniEvents 209, SkinStatues 69, Worldcraft **277** = 252 de la 034 + 25 nuevas: `ColorTest` 5, `ColorPatternTest` 5, `CreateTest` 7, `LinkTest` 6, `ConfigTest` 1, `CommandTest` 1). La tabla del jar son los 145 bloques de SkinStatues; cada color medido es su propio bloque; nombres de tintes, `#rgb`, «did you mean»; `#darken`/`#lighten` más oscuro o más claro del mismo tono (ninguno más oscuro que el más oscuro); multiplicar, promedio y desaturar como FAWE; los patrones sobre el bloque que ya está (fuera de la tabla se deja; sin saber qué hay, se deja), mezclados, en *presets*, y los bloques prohibidos ven toda la tabla (`Pattern.also`); la veta del tamaño pedido sin desparramarse, frecuencia, rareza (0 = nada, 50 % ≈ la mitad), alturas, solo en la región (también un cilindro), la misma semilla da lo mismo, plazo; cuevas con una fracción tallada de cuevas, conectadas (el grupo más grande junta buena parte), lo angosto dentro de lo ancho, más frecuencia más túneles, `--rooms` agrega, otra semilla otra cueva, plazo; la imagen leída (PNG, JPG, GIF), achicada promediando con el alfa como peso, agrandada al más cercano, lo transparente se deja, una PNG que dice 60 000 × 60 000 en la cabecera se rechaza sin decodificar; de pie y acostada en las 4 direcciones sin espejar ni al revés; el link: normalizado, etiqueta sin el link, nada a la red local (por IP, por nombre ni por redirección), sitios permitidos, tope de bytes, redirecciones, la opción de prueba; las cuatro secciones de `config.yml` (un `config.yml` de la 0.10.0 sin avisos, `remove`/`add` con «did you mean», minerales malos omitidos, una carpeta con `..` no sale, sitios inválidos) y un *preset* `#darken`; la ayuda de los 4 subcomandos y las claves de los mensajes |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24, con WorldEdit 7.3.19), `config.yml` de fábrica: «Configuration OK». `selftest`: «Selftest creation passed: 145 block colors (#color[red] = red_wool, #darken of light_gray_wool = tuff), 40 ore veins, a cave of 3939 blocks and a 2×2 picture» (ImageIO y el registro reales). Desde la consola en una caja de piedra de 16³ en el aire: `caves 3 60 --rooms` = 933 bloques (`count air` = 933), `ores` = 22 vetas de oro, redstone, lapislázuli y diamante (solo los de esa altura), 69 bloques (`count *_ore` = 69), `ore stone gold_block 8 10 100` = 22 (`count gold_block` = 22), `set #color[red]` = lana roja (4096), `replace red_wool with #darken` (0 de lana roja después), «Unknown color pattern '#colr' (did you mean '#color'?)», `image list` (vacía), `image` desde la consola («use it in the game»), `caves 30` («The size goes from 1 to 10»), `undo 7` y `count !air` = 0. Logs sin errores en los dos arranques |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **39/39 tres veces seguidas** (36 de antes + 3 nuevos; después de arreglar F-01 y reparar el suelo), tick más largo de la obra de 36 000 bloques 8 ms, con el HTTPS local de la 023 (`-Dworldcraft.test.localLinks=true` solo por la línea de comandos) |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1 | 3a (`set #color[red]` pone `red_wool`); `ColorTest`, `ColorPatternTest`; compuerta 2 |
| H1 CA2 | 3a (`replace red_wool with #lighten` cambia los 9 y no queda lana roja); compuerta 2 (`#darken`); `ColorPatternTest` (`#darken`, `#lighten`, `#saturate`, `#averagecolor`, `#desaturate`, fuera de la tabla) |
| H1 CA3 | 3a («'rde' is not a color … did you mean 'red'», «Unknown color pattern '#colr' (did you mean '#color'?)»); `ColorPatternTest` (mezcla, *preset*, errores) |
| H1 CA4 | `ConfigTest.spec035…` (`colors.remove`, `colors.add`); `ColorPatternTest` (los prohibidos ven toda la tabla) |
| H2 CA1 | 3a (`ore stone gold_block 8 10 100`: oro en la piedra y nada en el deepslate; `undo`); `CreateTest`; compuerta 2 |
| H2 CA2 | 3a (`ores`: solo los minerales de esa altura —oro, redstone, lapislázuli, diamante—; en el deepslate solo la versión deepslate y en la piedra solo la común); compuerta 2 |
| H2 CA3 | 3a (el mensaje con las vetas, la obra, `undo 2` deja 0 minerales); compuerta 2 |
| H3 CA1 | 3a (`caves 8 100 --rooms` talla más de 100 bloques); `CreateTest` (túneles conectados, ancho, frecuencia, salas, otra semilla); compuerta 2 |
| H3 CA2 | 3a (la columna de vidrio queda entera: 11 de 11; en y = -55, `caves.lava_y`, lo tallado es lava y arriba aire; aire + lava = lo tallado) |
| H3 CA3 | 3a (la obra y `undo`: 0 de aire y lava); `CreateTest` (plazo); el motor de siempre (`Edits.request`) |
| H4 CA1 | 3a (la foto de 4×3 con dos píxeles transparentes pone 10 bloques: los colores exactos de la tabla y aire donde era transparente); `CreateTest` (escala, transparencia, formatos) |
| H4 CA2 | 3a (de pie frente a Ana mirando al sur: la izquierda de la foto al este, la fila de arriba arriba, a 2 bloques y centrada; acostada sobre un vidrio mirando abajo: la fila de arriba lo más lejos); `CreateTest` (las 4 direcciones) |
| H4 CA3 | 3a (`image list`, «There is no image 'e2f.png' … (did you mean 'e2e.png'?)», `../config.yml` «is not an image file name») |
| H4 CA4 | 3a (apagado de fábrica: «Images from links are off»; encendido con `wc reload`: baja del HTTPS local, la etiqueta `url:127.0.0.1#xxxxxxxx` en el mensaje y el historial; un 404 lo dice); `LinkTest` (SSRF, sitios, topes, redirecciones); compuerta 2 |
| H4 CA5 | 3a (la obra y `undo` de cada imagen) |
| H5 CA1 | `ConfigTest.spec035…`; compuerta 2 («Configuration OK») |
| H5 CA2 | `CommandTest.spec035…` (ayuda con ejemplo de los 4); autocompletado en el código (`CreateCommands.complete`, `#color[`… en los patrones) |
| H5 CA3 / README | README de Worldcraft (sección *Caves, ores, pictures and colors*, cuatro líneas arriba, patrones, «Coming from WorldEdit» con dos filas, configuración, cómo actualizar desde la 0.10.0, permiso `worldcraft.image.link`, compatibilidad 0.11.0, «Optional» con la red, «Not yet» sin cuevas, minerales ni imágenes), README raíz (fila 0.11.0), tabla de paridad (5 filas), roadmap, [ADR 0007](../../../adr/0007-red-segura-copiada.md). Sin capturas nuevas |

## Hallazgos
| # | Qué pasó | Arreglo | Prueba |
|---|---|---|---|
| F-01 | Armado de la suite (no del plugin): el mural de pie del escenario de imagen se construía con la fila de abajo en y = -60, encima del pasto del mundo plano; tapado unos segundos dos veces por corrida, un pasto se volvió tierra y la **corrida siguiente** falló en la 033 (`replace $desert and grass_block` = 47 en vez de 48). Se repitió la 034 F-01 (y la 029) en un escenario nuevo | Todo el escenario de imagen va sobre un vidrio en el aire (y = -51); el suelo del servidor de prueba se reparó con `forceload add` + `fill … grass_block replace dirt` (1 bloque). La 033, al fallar **después** de su `replace … with sand`, dejó 47 de arena en el suelo (su `finally` no la devuelve) y la corrida siguiente falló en H1 y en la 033: se reparó toda la capa del área de trabajo (48 de arena y 12 de tierra a pasto) | Con el escenario anterior: 39/39 y después 38/39 (la segunda corrida falla); con el arreglo y el suelo reparado, 39/39 dos veces seguidas, y después la capa de pasto sin nada que reparar |

## Riesgos
- Las cuevas no son las de FAWE (gusanos con otros parámetros: `systemFrequency`, `pocketChance`…): `caves [tamaño]
  [frecuencia]` se parece en el uso, no en la forma. Se calibraron mirando cortes (≈2 % del volumen de fábrica, ≈4 % con
  `--rooms`).
- La red se copió de SkinStatues (ADR 0007): un arreglo de seguridad hay que hacerlo en los dos plugins.
- *DNS rebinding*: el mismo riesgo residual que la 023 si el dueño apaga la caché de DNS de Java.
- La tabla de colores se midió en 1.21.4; en 26.x los bloques nuevos no están (se pueden agregar en `colors.add`).
- El suelo del mundo plano de `plugins/worldcraft/run/` se gasta despacio con escenarios **anteriores** a la 035: en tres
  corridas verdes se juntaron 20 bloques de tierra en la capa de pasto (x 40–89); la suite lo tolera, pero acumulado
  hace fallar la 033 o H1. Se reparó con `forceload add` + `fill … grass_block replace dirt`; queda como seguimiento
  (que cada escenario viejo deje el pasto como era, o reparar la capa al empezar la suite).
- Paper 26.1.2 sin verificar.

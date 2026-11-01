# Verificación 026 — Worldcraft: terreno fácil para el novato y fino para el experto

**Fecha:** 2026-11-01 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar; decisiones de
CLARIFY pendientes del dueño · **Versión:** Worldcraft 0.2.0

| Compuerta | Resultado |
|---|---|
| Unitarias | `./gradlew :worldcraft:build` en verde: **66 pruebas** (20 nuevas: formas 8, obras de terreno con el mundo en memoria 7, configuración/opciones/pincel 5; más las 46 de la 025, con `CommandTest` y `ConfigTest` al día) |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24), actualizando desde la 0.1.0 (un `config.yml` sin sección `terrain`: `terrain.yml` se creó y todo cargó con «Configuration OK»). Desde la consola: `wc styles` (7), `wc mountain medium at=200,-61,200 seed=42` (37 416 bloques, tick más largo 8,2 ms), `wc island large at=300,-20,200 seed=7 trees=4` (35 476, 8,2 ms), `wc mountain huge at=500,-61,500 seed=3` pidió confirmación (811 675 bloques) y con `confirm` terminó en 4 s; `wc undo` los devolvió todos; `wc mountain peeks=3` → «did you mean 'peaks'»; `wc island` sin `at=` → «From the console, say where»; `reload` 0/0. Sin errores en el log en los dos arranques. F-04 salió de aquí |
| En el juego (3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **12/12 dos veces seguidas** (8 de la 025 + 4 nuevos) en Paper 1.21.4 con el jar 0.2.0, en el servidor propio `plugins/worldcraft/run/` (puerto 25599 libre antes; nunca el `run/` de SkinStatues). Tick más largo: montaña 3,4 y 2,7 ms, isla 3,7 y 2,8 ms; la obra de 36 000 de la 025, 8 ms. Log sin errores del plugin (solo los avisos que provocan los escenarios a propósito) |
| Revisión exploratoria (3b) | Formas ajustadas a ojo con `./gradlew :worldcraft:terrainPreview` (PNG de montañas e islas corridas por el motor en el mundo en memoria): la primera montaña salía casi toda roca y se le dio pasto a las laderas bajas. Sin una persona jugando: queda para el dueño (ver riesgos) |
| README | Del plugin: sección «Terrain: easy for beginners, precise for experts» (los tres comandos, el pincel, las opciones `clave=valor`, la semilla, estilos con un ejemplo, seguridad), `terrain` en Configuration, «Not yet» y Compatibility con 0.2.0. Raíz: fila de Worldcraft (0.2.0, terreno). Sin capturas (lección 004; se proponen con la página de presentación) |

## Cobertura
| Criterio | Prueba |
|---|---|
| H1 CA1 | 3a «una montaña donde mira Ana» (desde un pilar, la mira cae a 48 bloques: la montaña aparece ahí); `TerrainConfigTest` (tamaños y radio por número) |
| H1 CA2 | `ShapeTest.H1_CA2_…` (cima = height, nada lejos del radio, laderas irregulares); `TerrainEditTest.H1_CA3_…` (nieve en la cima); 3a (pasto y piedra en la montaña construida) |
| H1 CA3 | `TerrainEditTest.H1_CA3_H5_CA1_…` (la casa sigue y su columna no se toca; el suelo que había no se pisa); 3a (la tabla de roble sigue, aire encima) |
| H1 CA4 | `ShapeTest.H1_CA4_la_piel_…` (la piel es parte de la obra, menos de la mitad, y tiene todas las cimas); 3a (solo Ana la ve, botones, `reroll` cambia la forma, el mundo no cambia) |
| H1 CA5 | `TerrainEditTest.H1_CA5_…`; 3a (lo que ve Beto tras `confirm` = lo que mostró la vista previa, 121 columnas) |
| H2 CA1 | 3a «una isla flotante delante de Ana» (a 16 bloques al sur, sobre su cabeza) |
| H2 CA2 | `ShapeTest.H2_CA2_…` (cima casi plana, honda al centro, más delgada al borde, raíces, árboles, estalactitas); 3a (pasto y piedra, hojas persistentes) |
| H2 CA3 | `ShapeTest.H2_CA3_…` (una isla que llega al suelo no pisa el pasto ni la tierra; hojas persistentes); 3a (de y = -60 a -57 todo aire) |
| H2 CA4 | 3a (vista previa, `confirm`, `undo`) |
| H3 CA1 | `TerrainConfigTest.H3_CA1_H4_CA4_…` (palabras y claves, ida y vuelta en el ítem); 3a (`/wc brush raise 3 1` da la pala; `/wc brush smooth 4 1` la ajusta en la mano) |
| H3 CA2 | 3a (clic derecho = pasada; clic izquierdo = «Brush lower» en la barra; partículas para Ana, ninguna para Beto) |
| H3 CA3 | `TerrainEditTest` (suavizar baja un pico y no toca el árbol ni la casa; aplanar; erosión derrumba un escalón de 8); 3a (suavizar baja el bulto) |
| H3 CA4 | `TerrainEditTest.H3_CA4_…` (pasto arriba, tierra abajo, viejo pasto enterrado como tierra, la flor no flota); 3a (mismo orden de bloques tras subir) |
| H3 CA5 | Por diseño (`Terrain.stroke` ignora clics con una lectura o una obra en marcha); 3a (`undo 2` deshace las dos pasadas) |
| H4 CA1 | `TerrainConfigTest.H4_CA1_…` (tamaño + estilo + clave, el estilo trae su cráter, rangos, tope de config, sugerencias; `at=` con `~`); `ShapeTest.H4_CA1_crater_y_picos`; 3a (errores con sugerencia, autocompletado de `style=` y `trees=`) |
| H4 CA2 | `ShapeTest.H4_CA2_…`; 3a (dos montañas con `seed=9` desde la consola dan las mismas alturas) |
| H4 CA3 | `TerrainConfigTest.H4_CA3_…` (rutas y sugerencias; lo malo se omite); 3a (`terrain.yml` con un error y un aviso; `validate` dice la ruta) |
| H4 CA4 | `TerrainEditTest.H4_CA4_…` (pintar por pendiente con estilo; la máscara deja la arena) |
| H4 CA5 | `TerrainConfigTest` (tamaños de `terrain.yml`) |
| H5 CA1 | `TerrainEditTest.H1_CA3_H5_CA1_…` (deshacer con el diario deja todo igual); 3a (tick más largo ≤ 12 ms, `undo`); compuerta 2 (811 675 bloques) |
| H5 CA2 | `TerrainEditTest.H5_CA2_…` (la lectura distingue suelo, plantas, árboles y construcciones, por tandas con el motor); el cálculo en `Worldcraft-Terrain` por diseño |
| H5 CA3 | `ShapeTest.H5_CA3_…` (medium cabe en 50 000); 3a (Beto, con `default`, no puede `mountain huge`) |
| H5 CA4 | `TerrainConfigTest` (radio 900 fuera de rango con el tope 160); 3a (`radius=900`) |
| H6 CA1 | `TerrainConfigTest.H6_CA1_…` (sin avisos, 7 estilos); `ConfigTest.H8_CA3_…` (con `terrain.yml`); compuerta 2 (actualizar desde 0.1.0) |
| H6 CA2 | `TerrainConfigTest.H4_CA3_…` (sección `terrain`: rango, clave desconocida, máscara inválida) |
| H6 CA3 | `CommandTest.H8_CA5_…` (32 subcomandos con ayuda y ejemplo); 3a (autocompletado) |
| H6 CA4 | `CommandTest.H8_CA5_…` (cada mensaje que nombra el código existe, YAML estricto); 3a (botones `[Build]  [Another]  [Cancel]`) |

## Hallazgos
| ID | Severidad | Descripción | Evidencia | Prueba de regresión | Estado |
|---|---|---|---|---|---|
| F-01 | Alta | Un jugador en el suelo que pedía `/wc island` la recibía con la cima a la altura de sus pies: casi toda bajo tierra, y como la isla solo llena aire, no aparecía nada | Al escribir el escenario 3a de la isla (revisión del diseño) | 3a «una isla flotante delante de Ana, sobre su cabeza» (no se corrió con el código anterior: se corrigió antes de la primera corrida) | Resuelto: en el suelo la cima va a `depth·r·1,7 + 6` sobre los pies (flota con sus estalactitas); volando, a la altura de los pies |
| F-02 | Baja | Los avisos de la sección `terrain` de `config.yml` decían `config.yml › reach` en vez de `terrain.reach` (el lector partía de la sección) | Unitaria | `TerrainConfigTest.H4_CA3_…` (falló con el código anterior) | Resuelto: se lee desde la raíz con la ruta completa |
| F-03 | Media | `/wc brush 70% grass_block, 30% podzol` tomaba «70%» como la fuerza y pintaba «grass_block, 30% podzol» | Unitaria | `TerrainConfigTest.H3_CA1_H4_CA4_…` (falló con el código anterior) | Resuelto: un porcentaje seguido de un bloque es el peso del patrón |
| F-04 | Baja | Una montaña de más de 50 000 bloques con `--now` decía «run it again with --preview», que el terreno no tiene | Compuerta 2 | Sin prueba (texto) | Resuelto: mensaje propio `terrain-confirm-needed` |

## Medidas
| Obra | Bloques | Resultado |
|---|---|---|
| `mountain medium` (compuerta 2, primera obra tras arrancar) | 37 416 | Menos de 1 s; tick más largo 8,2 ms |
| `island large` (compuerta 2) | 35 476 | Menos de 1 s; 8,2 ms |
| `mountain huge` (compuerta 2, otro arranque, primera obra) | 811 675 | 4 s; tick más largo 12,8 ms (el pico en frío de la 025 F-04) |
| Montaña r 12 e isla small (3a, dos corridas) | ~3 000–6 000 | Tick más largo 2,7–3,7 ms |

## Riesgos residuales
- **Picos en frío (025 F-04).** La primera obra grande tras arrancar tuvo un tick de 12,8 ms (presupuesto 8); con el código
  caliente, 2,7–8,2 ms.
- **Cada pasada del pincel es una obra** del historial: pintar un rato llena `history.max_per_player` (30) y saca obras viejas
  (roadmap: juntar las pasadas).
- **Mapa de alturas:** sin cuevas, arcos ni salientes; una montaña sobre un terreno con salientes los toma como suelo arriba.
- **Columnas con construcciones** no se tocan: una montaña sobre un pueblo deja huecos con la forma de las casas (se ven en
  la vista previa).
- La vista previa son bloques falsos (como en la 025): se cancelan o vencen igual.
- Sin verificar en Paper 26.1.2; sin revisión de una persona jugando: la forma «bonita» la juzga el dueño.

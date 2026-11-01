# Verificación 022 — SkinStatues: más poses, poses propias fáciles y coordenadas en el comando

**Fecha:** 2026-11-01 · **Resultado:** Verificado (compuertas 1, 2 y 3a en Paper 1.21.4 y 26.1.2; 3b con la vista previa)

| Compuerta | Resultado |
|---|---|
| Unitarias | `./gradlew build` en verde; SkinStatues: 51 pruebas (eran 40): voxelizador 17, configuración 12, argumentos de `build` 4 (nueva), skins 6, Mojang con red falsa 6, diario e índice 3, recursos al día 3 |
| Servidor real (carga, validate, selftest, reload, consola) | `bash tools/compuerta-servidor.sh skinstatues "statue validate" "statue selftest" "statue poses" "statue build Example 16 t_pose --at 200 -60 200 --facing west" "statue build Example 16" "statue list" "statue reload" "statue reload" "statue info 1"` en Paper 1.21.4 (build 232), **sin** `plugins/SkinStatues/` (lo que crea el jar solo): «Configuration OK: 30 poses», `selftest` pasa, `builtin-poses.yml` escrito, la estatua desde la consola en 200 -60 200 mirando al oeste («Built by Console»), sin `--at` la consola recibe el uso; dos `reload` sin errores; segundo arranque sin excepciones |
| En el juego (3a) | `npm --prefix tests-e2e test -- --plugin skinstatues`: **14/14 dos veces seguidas en Paper 1.21.4** (build 232, Java 24; la primera con un mundo nuevo, así los chunks de x = z = 4000 no existían) **y en 26.1.2** (build 74, Java 25, bot 26.1), con el jar final (después de F-01); el log sin errores del plugin |
| Revisión exploratoria (3b) | Las 22 poses nuevas se ajustaron a ojo con `./gradlew :skinstatues:preview -Ponly=…` (frente, costado, espalda, isométrica a 48 de alto): `saluting` y `relaxing` cambiaron de ángulos tras verlas (la mano no llegaba a la frente; las manos quedaban sobre los hombros, no detrás de la cabeza). F-01 salió de la suite (una corrida interrumpida dejó una estatua en el mundo) |
| README (capacidades, Compatibilidad, tabla raíz) | README del plugin: imagen de las 28 poses (`docs/img/skinstatues/poses.png`, de la vista previa, sin servidor), lista de poses, sección «Where the statue goes» (`--at`, `~`, `--facing`, `--world`, consola), guía de `poses.yml` con tabla de ángulos, `extends` y `mirror`, comandos y Compatibilidad (0.2.0 en 1.21.4 y 26.1.2). README raíz (fila de SkinStatues), roadmap (estado y lluvia de ideas) e índice. Sin capturas en el juego |

## Cobertura
| Criterio | Prueba |
|---|---|
| H1 CA1 | `ConfigTest.H4_CA3_y_H9_CA3…` (28 incorporadas); 3a «--at relativo… (pose nueva t_pose)» (los brazos a lo ancho); hoja de la vista previa |
| H1 CA2 | `ConfigTest.H1_CA2_CA3_las_incorporadas_vienen_del_jar…`; compuerta 2 sin carpeta (30 poses con el `poses.yml` nuevo) y 3a «poses incorporadas y propias…» (con el `poses.yml` de la 0.1.0 siguen las 28) |
| H1 CA3 | `ConfigTest.H1_CA2_CA3…` (reemplazo y orden), `H1_CA3_el_poses_yml_de_la_0_1_0_carga_sin_errores_ni_reemplazos`; 3a «poses incorporadas y propias…» (0 errores, «Yours: none»; `t_pose*` reemplazada) |
| H1 CA4 | 3a «poses incorporadas y propias…» (`builtin-poses.yml` editado vuelve a escribirse en el `reload`, con su cabecera); compuerta 2 (se crea al primer arranque) |
| H1 CA5 | `VoxelizerTest.H4_CA2_y_H1_CA4_cascara_cerrada_en_todas_las_poses` (ahora las 28, a 128 de alto, 2 orientaciones), `spec022_H1_CA5_ninguna_pose_incorporada_baja_de_los_pies` |
| H1 CA6 | README (imagen y lista); la imagen la genera `./gradlew :skinstatues:preview -Pgrid=true` |
| H2 CA1 | `ConfigTest.H2_CA1_extends_cambia_angulo_por_angulo_y_el_padre_puede_ir_despues`, `H4_CA3_y_H9_CA3…` (`my_wave`); 3a «poses incorporadas y propias…» (`mas_codo`) |
| H2 CA2 | `ConfigTest.H2_CA2_mirror_voltea_la_pose_heredada_y_luego_aplica_lo_escrito`, `VoxelizerTest.spec022_H2_CA2_el_espejo_de_una_pose_es_la_estatua_reflejada` (las 28, bloque por bloque); 3a «poses incorporadas y propias…» (`pointing` apunta al oeste y `apunta_izq` al este, con los mismos bloques) |
| H2 CA3 | `ConfigTest.H2_CA3_herencias_que_no_se_resuelven…` (padre que no existe con sugerencia, ciclo, a sí misma, padre con errores, `mirror` sin `extends` o con texto, `extends` que no es un nombre, 9 niveles sí falla y 8 no); 3a «poses incorporadas y propias…» (`huerfana` y `circulo`: 2 errores y las demás cargan) |
| H2 CA4 | `ConfigTest.H4_CA3_y_H9_CA3…` (los dos ejemplos de `poses.yml` cargan); `poses.yml` y README |
| H2 CA5 | 3a «poses incorporadas y propias…» (`/statue poses`: incorporadas, `t_pose*`, propias); compuerta 2 |
| H3 CA1 | `BuildArgsTest.H3_CA1_at_absoluto_relativo_y_con_decimales`; 3a «--at relativo…» (pies en y = -60 sin `offset_up`, nada fuera del lugar, `info` dice el lugar) y «desde la consola…» (absolutas) |
| H3 CA2 | `BuildArgsTest.H3_CA2_facing_y_world`; 3a «--at relativo…» (la capa al sur mirando al norte, `facing north`) y «desde la consola…» (`facing east`) |
| H3 CA3 | `BuildArgsTest.H3_CA3_sin_opciones_todo_como_antes`; 3a de la 021 sin cambios (2 bloques arriba, mirando hacia donde mira) |
| H3 CA4 | 3a «desde la consola…» (`--world world`, «Built by Console», `statue undo` de la consola); compuerta 2 |
| H3 CA5 | `BuildArgsTest.H3_CA5_errores_con_su_mensaje`; 3a «desde la consola…» (sin `--at`, `~`, mundo con «did you mean», coordenada y dirección inválidas, fuera del borde) |
| H3 CA6 | 3a «desde la consola…» (x = z = 4000 en un mundo nuevo, sin jugadores cerca: termina con todos los bloques; después Paper descarga el chunk, así que los tickets se soltaron, y `undo` lo vuelve a cargar y devuelve el terreno) |
| H3 CA7 | 3a «--at relativo…» (`--facing` sugiere las 4 direcciones, `--at` sugiere `~`, `--` sugiere `--at`, `--facing`, `--world`) |
| H4 CA1 | `roadmap.md` («SkinStatues: lluvia de ideas», 22 ideas con su costo) |

## Hallazgos
| ID | Severidad | Descripción | Evidencia | Prueba de regresión | Estado |
|---|---|---|---|---|---|
| F-01 | Baja | Construir donde ya están esos mismos bloques (una estatua igual en el mismo lugar) decía «240 block(s) with contents (chests, signs, furnaces…) were left untouched»: el contador de bloques con contenido también sumaba los que ya eran el bloque pedido. Venía de la 0.1.0 | Suite 3a: una corrida interrumpida dejó la estatua de x = 4000 en el mundo y la siguiente dijo «finished (0 blocks)» y «240 block(s) with contents» | 3a «022 F-01 — construir donde ya están esos mismos bloques no dice que había cofres» (verificado: falla con el jar anterior al arreglo, «recibió chat que no debía llegar») | Resuelto: solo cuenta los que no se tocaron y no son el bloque pedido |

Durante la implementación, la prueba de cáscara cerrada falló en `dabbing`: la cara se mete en el brazo, así que el
«interior» del brazo no está vacío. No es un defecto (dos partes se cruzan, como en `arms_crossed`): la prueba salta
esos puntos (exige que de pie no pase y que sean menos del 5 %) y sigue comprobando la cáscara en todos los demás.

## Medidas (vista previa, skin de ejemplo con segunda capa y capa)
| Alto | Bloques (según la pose) |
|---|---|
| 32 | 1 700 – 1 960 (`lying` mide 10 de alto; `sitting_floor`, 25) |
| 48 | 4 130 – 4 730 |

## Riesgos residuales
- Un jugador puede construir con `--at ~ ~ ~` sobre sí mismo y quedar dentro de la estatua (con `--at` no hay
  `offset_up`, es lo pedido); el README dice que `y` es el bloque de los pies.
- `--world` sin `--at` construye en esas mismas coordenadas del jugador en el otro mundo.
- Las poses se ajustaron con la skin de ejemplo; con otras skins (brazos delgados) las manos pueden quedar a un
  píxel de donde se ven en la hoja.
- Generar chunks lejanos depende de Paper (`getChunkAtAsync`); en Spigot no se soporta (constitución VI).
- Los mismos de la 021 (diario en el mismo tick, la red probada solo con respuestas falsas).

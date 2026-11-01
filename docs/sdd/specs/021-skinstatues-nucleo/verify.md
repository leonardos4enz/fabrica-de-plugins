# Verificación 021 — SkinStatues: estatuas de bloques con la skin de un jugador

**Fecha:** 2026-11-01 · **Resultado:** Verificado (compuertas 1, 2 y 3a; 3b con vista previa y bots)

| Compuerta | Resultado |
|---|---|
| Unitarias | `./gradlew build` en verde; SkinStatues: 40 pruebas (voxelizador 15, configuración 7, skins 6, Mojang con red falsa 6, diario e índice 3, recursos al día 3) |
| Servidor real (carga, validate, selftest, reload) | `bash tools/compuerta-servidor.sh skinstatues "statue validate" "statue selftest" "statue list" "statue reload" "statue reload" "statue poses" "statue palettes"` en Paper 1.21.4 (build 232): sin excepciones en los dos arranques; `selftest` construye 240 bloques que reemplazan terreno y lo devuelve exacto. El primer intento mostró F-02; con el jar final, «Configuration OK» sin notas y los mismos resultados |
| En el juego (3a) | `npm --prefix tests-e2e test -- --plugin skinstatues`: **10/10 dos veces seguidas en Paper 1.21.4** (build 232, Java 24) **y en 26.1.2** (build 74, Java 25, bot 26.1), con el jar final (después de F-03); el log sin errores del plugin |
| Revisión exploratoria (3b) | Vista previa sin servidor de las 6 poses (`./gradlew :skinstatues:preview`, frente, costado, espalda e isométrica) y los bots en el juego: F-01 y F-03 salieron de ahí |
| README (capacidades, Compatibilidad, tabla raíz) | README del plugin nuevo, en inglés (ADR 0005), con cada capacidad y su ejemplo y Compatibilidad (1.21.4 y 26.1.2 verificadas); fila de SkinStatues en el README raíz; roadmap e índice. Sin capturas en el juego (la infraestructura de la 004 es de la galería de OmniEvents: se propone aparte) |

## Cobertura
| Criterio | Prueba |
|---|---|
| H1 CA1 | `VoxelizerTest.H1_CA1_alto_y_ancho_de_pie`, `H1_CA1_la_altura_pedida_escala_la_figura`; 3a «build: 2 bloques arriba…» (alto 16 anunciado y visto) |
| H1 CA2 | `VoxelizerTest.H1_CA2_mira_hacia_donde_mira_el_jugador`; 3a «build…» (pies en y+2, nada debajo, capa al norte mirando al sur) y «skin local… mirando al este» (8×32×14) |
| H1 CA3 | `VoxelizerTest.H1_CA3_escala_chica_promedia_los_pixeles` |
| H1 CA4 | `VoxelizerTest.H1_CA4_hueca_con_la_skin_en_la_superficie`, `H4_CA2_y_H1_CA4_cascara_cerrada_en_todas_las_poses` (inundación desde dentro de cada segmento, 6 poses × 2 orientaciones, alto 128); 3a «build…» |
| H1 CA5 | `MojangClientTest.H1_CA5_nombre_invalido_no_llega_a_la_red`; 3a «nombres, alturas…» (`<red>x;op`) |
| H1 CA6 | 3a «una estatua que no entra hasta el techo del mundo» (a y = 252 dice 68) y «nombres, alturas…» (500, `alto`) |
| H1 CA7 | 3a «build…» (número de bloques anunciado = bloques vistos; número de la estatua) |
| H2 CA1 | `SkinTest` (64×64, 64×32 con espejo y truco de Notch, capa 64×32 y 22×17), `MojangClientTest.H2_CA1_archivo_local_con_opciones`; 3a «build…» (skin de `skins/Example.png`) y «skin local de brazos delgados» |
| H2 CA2 | `MojangClientTest.H2_CA2_skin_modelo_y_capa_por_nombre`, `H2_CA2_cache_y_red_apagada` (caché, caché vencida sin red) |
| H2 CA3 | `MojangClientTest.H2_CA2_cache_y_red_apagada` (con la red apagada no se pregunta a nadie); 3a «nombres…» (mensaje con la carpeta y la opción) |
| H2 CA4 | `SkinTest.H2_CA4_imagenes_invalidas`, `MojangClientTest.H2_CA4_cuenta_inexistente_y_sin_respuesta` |
| H2 CA5 | `MojangClientTest.H2_CA5_solo_texturas_de_mojang` |
| H3 CA1 | `VoxelizerTest.H3_CA1_segunda_capa_pegada_sin_hueco`, `H3_CA1_a_32_de_alto_el_sombrero_sale_en_relieve_y_mide_32` (F-03), `H3_la_chaqueta_no_tapa_el_brazo`; 3a «pose, paleta solo de lana…» (casco arriba y un bloque afuera a cada lado) |
| H3 CA2 | `VoxelizerTest.H3_CA2_transparente_no_pone_nada_y_el_umbral_cuenta` |
| H3 CA3 | `VoxelizerTest.H3_CA3_a_escala_chica_la_segunda_capa_pinta_la_primera` |
| H3 CA4 | `VoxelizerTest.H3_CA4_sin_segunda_capa`; 3a «skin local…» (`--no-overlay`) |
| H4 CA1 | `ConfigTest.H4_CA4_poses_con_errores…` (pose válida con sus ángulos); 3a «pose, paleta…» (`victory`: brazos al costado de la cabeza) |
| H4 CA2 | `VoxelizerTest.H4_CA2_y_H1_CA4_cascara_cerrada_en_todas_las_poses` (también desde la articulación misma) |
| H4 CA3 | `ConfigTest.H4_CA3_y_H9_CA3_los_archivos_que_trae_el_plugin_cargan_sin_errores` (6 poses) |
| H4 CA4 | `ConfigTest.H4_CA4_poses_con_errores_se_omiten_y_las_demas_cargan` |
| H4 CA5 | 3a «sin permiso…» (autocompletado de poses) y «nombres…» (`wavng` → `waving`) |
| H4 CA6 | `VoxelizerTest.H4_CA6_lo_mas_bajo_queda_en_los_pies` |
| H5 CA1 | `VoxelizerTest.H5_capa_en_la_espalda_e_inclinada`, `H5_CA1_capa_delgada_muestra_la_cara_de_afuera` (F-01); 3a «build…» (capa verde atrás) |
| H5 CA2 | `VoxelizerTest.H5_capa_en_la_espalda_e_inclinada` |
| H5 CA3 | `VoxelizerTest.H5_capa_en_la_espalda_e_inclinada`; 3a «skin local…» (`--no-cape`) |
| H6 CA1 | `ResourcesTest.block_colors_tiene_todos_los_bloques_del_generador` (145 bloques) |
| H6 CA2 | `ConfigTest.H6_CA2_comodines_exclusiones_e_ids`; 3a «pose, paleta solo de lana…» (solo `*_wool` en el mundo) |
| H6 CA3 | 3a «pose, paleta…» (`--palette wool`) y «nombres…» (`wol` → `wool`) |
| H6 CA4 | `ConfigTest.H6_CA4_elige_entre_los_permitidos_como_lo_ve_el_ojo`, `ResourcesTest.H1_la_estatua_de_ejemplo_usa_la_paleta…` |
| H6 CA5 | `ConfigTest.H6_CA5_ids_desconocidos_con_sugerencia_y_paleta_vacia`, `H6_colores_invalidos_y_bloques_de_otra_version` |
| H7 CA1 | 3a «construcción por tandas…» (con `blocks_per_tick: 20`: barra de acción con el avance) |
| H7 CA2 | Por diseño (`TickWorker`, un trabajo a la vez, máx. 20 en espera, `max_blocks`); sin prueba de dos estatuas simultáneas (riesgo residual) |
| H7 CA3 | 3a «construcción por tandas…» (`/statue cancel`, `info` dice cancelled, `remove` devuelve lo puesto) |
| H8 CA1 | `StoreTest.H8_CA1_el_diario_guarda_lo_que_habia_con_su_estado`; 3a «reemplaza terreno…» (ladrillos y escalera `half=top` vuelven, el cofre con diamantes no se toca) |
| H8 CA2 | 3a «undo quita la última estatua» y «reemplaza terreno…» (`remove`) |
| H8 CA3 | 3a «reemplaza terreno…» (el bloque de oro que puso otro se queda; «1 were left») |
| H8 CA4 | `StoreTest.H8_CA4_una_cola_truncada…`, `H8_CA4_el_indice_sobrevive_y_lo_interrumpido_queda_cancelado`; compuerta 2 (segundo arranque) |
| H8 CA5 | 3a «build…» y «undo…» (`/statue list`); «construcción por tandas…» (`/statue info`) |
| H9 CA1 | 3a «sin permiso…» (Beto) |
| H9 CA2 | `ConfigTest.H9_CA2_config_con_valores_invalidos…`; 3a «construcción por tandas…» (`poses.yml` roto: se conservan las 6 poses) |
| H9 CA3 | `ResourcesTest.H9_CA3_la_skin_de_ejemplo_es_la_que_dibuja_el_generador`, `ConfigTest.H4_CA3_y_H9_CA3…`; 3a «build…» (Example sin activar la red) |
| H9 CA4 | 3a «sin permiso…» (Beto no ve subcomandos; Ana sí) |
| H9 CA5 | Compuerta 2 y 3a «validate sin errores ni notas y selftest» |

## Hallazgos
| ID | Severidad | Descripción | Evidencia | Prueba de regresión | Estado |
|---|---|---|---|---|---|
| F-01 | Media | A escalas no enteras la capa queda de un bloque de grueso y el centro de ese bloque está más cerca de la cara interior: desde atrás se veía la textura de adentro (sin la «S» de la capa de ejemplo) | Vista previa de las poses a 48 de alto: la espalda de `standing` lisa, la «S» solo con la capa inclinada | `VoxelizerTest.H5_CA1_capa_delgada_muestra_la_cara_de_afuera` (verificado: falla sin el arreglo, 0 bloques de la cara de afuera) | Resuelto: con menos de 2 bloques de grueso el color sale de la cara de afuera |
| F-02 | Baja | `glowstone` y `sea_lantern` estaban en el catálogo pero Paper no los cuenta como opacos: cada arranque y cada `reload` avisaban «not a solid full block» | `run1.log` de la compuerta 2 («Config note: block-colors.yml › glowstone…») | 3a «validate sin errores ni notas…» exige «Configuration OK» (con el catálogo anterior, `validate` listaba 2 notas). Sin unitaria: `Material.isOccluding()` necesita el registro del servidor | Resuelto: fuera del generador y de `block-colors.yml` (145 bloques) |
| F-03 | Media | A 32 de alto con segunda capa la estatua medía 31 y el casco salía pintado, no en relieve: descontar el bloque del sombrero bajaba la escala a 31/32 y la decisión «relieve o pintada» se tomaba con esa escala, justo bajo el umbral | `./gradlew :skinstatues:preview -Pheight=32`: «standing: 1274 blocks, height 31» | `VoxelizerTest.H3_CA1_a_32_de_alto_el_sombrero_sale_en_relieve_y_mide_32` (verificado: falla sin el arreglo, «expected 32 but was 31») | Resuelto: se decide con la escala pedida, antes del descuento |

Durante la implementación (antes de la 3b) la prueba de cáscara cerrada encontró un agujero en el brazo de pie a escala
3,9375: un centro de bloque caía justo en el plano de la cara de arriba. La condición de la cáscara pasó a ser inclusiva
(comentario en `Voxelizer`); la prueba lo cubre.

## Medidas (vista previa, skin de ejemplo con segunda capa y capa)
| Alto | Bloques (según la pose) | Tiempo con `blocks_per_tick: 4000` |
|---|---|---|
| 32 | 1 750 – 1 950 | menos de 1 s |
| 128 | 46 750 – 50 050 | unos 13 s |

## Riesgos residuales
- El diario se escribe en el hilo de E/S en el mismo tick en que se ponen los bloques: si el servidor se cae en ese
  instante, la última tanda (hasta `blocks_per_tick` bloques) podría quedar sin registrar y no se devolvería.
- Las poses que levantan los brazos pasan un poco del alto pedido (victory a 128: 131); el alto es el de la figura de pie
  y la comprobación del techo del mundo usa el alto real.
- H7 CA2 (varias estatuas en cola) está cubierto por diseño, no por un escenario.
- La red (`network.mojang`) se probó con respuestas falsas, no contra Mojang de verdad (el servidor de prueba no sale a
  internet).

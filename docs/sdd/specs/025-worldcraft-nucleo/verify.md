# Verificación 025 — Blockwright: un editor de mundo que no traba el servidor

**Fecha:** 2026-11-01 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar; decisiones de CLARIFY pendientes del dueño

| Compuerta | Resultado |
|---|---|
| Unitarias | `./gradlew :blockwright:build` en verde: 45 pruebas (motor con mundo en memoria 15, patrones y máscaras 9, historial 5, planificador 5, configuración 4, región 4, comando 4) |
| Servidor real (compuerta 2) | En un servidor propio, `plugins/blockwright/run/` (Paper 1.21.4 build 232, Java 24; no el de SkinStatues con el mundo del dueño): `bash tools/compuerta-servidor.sh blockwright "bwr validate" "bwr selftest" "bwr pos1 -50 -60 -50" "bwr pos2 49 -51 49" "bwr set 70% stone, 30% glass" "bwr confirm" "bwr jobs" … "bwr history" "bwr limits" "bwr set stnoe" "bwr reload"`: sin errores en los dos arranques; «Configuration OK»; selftest «set 216 blocks, undo, redo and undo; the terrain is as before»; la obra de 100 000 bloques pidió confirmación y terminó en menos de un segundo; `reload` sin avisos. Segundo arranque: el historial siguió (#2, #3) y `bwr undo` devolvió los 100 000 bloques. F-01 y F-02 salieron de aquí |
| En el juego (3a) | `npm --prefix tests-e2e test -- --plugin blockwright`: **8/8 dos veces seguidas en Paper 1.21.4** (build 232, Java 24, bot 1.21.4), con el jar final, en el servidor propio `plugins/blockwright/run/` (puerto 25599 libre antes de empezar); el log sin errores del plugin. La primera corrida falló entera por F-03 y la segunda 7/8 por una prueba que no esperaba a la consola (lección 002 F-03, corregida en la suite) |
| Revisión exploratoria (3b) | Con la consola del servidor real (compuerta 2) y los bots: tiempos por tick, mensajes, sugerencias; F-01 y F-02 salieron de ahí. Sin una persona jugando: queda para el dueño (ver riesgos) |
| README (capacidades, Compatibilidad, tabla raíz) | README del plugin nuevo en inglés (ADR 0005) con cada capacidad y su ejemplo y Compatibilidad (1.21.4 verificada; 26.1.2 sin verificar); fila de Blockwright en el README raíz, comandos de las compuertas y estructura; roadmap (diagnóstico de WorldEdit/FAWE y lo pendiente) e índice. Sin capturas (lección 004: se proponen con la página de presentación) |

## Cobertura
| Criterio | Prueba |
|---|---|
| H1 CA1 | 3a «la varita marca esquinas…» (solo la varita marcada; un hacha de oro común no marca) |
| H1 CA2 | 3a «la varita…» (clic izquierdo y derecho; el pasto sigue ahí) |
| H1 CA3 | 3a «la varita…» (mensaje con 5×1×3 y 15 bloques; partículas para Ana, ninguna para Beto a 7 bloques) |
| H1 CA4 | `RegionTest.H1_CA4_agrandar_achicar_y_mover_con_palabras`, `H1_CA4_hacia_donde_mira_el_jugador`; 3a «los errores…» (`left`) |
| H1 CA5 | `CommandTest.H1_CA5_coordenadas_relativas`; compuerta 2 (`pos1 x y z` desde la consola) |
| H1 CA6 | 3a «la varita…» (`/bwr sel`); compuerta 2 |
| H2 CA1 | `ParserTest.H2_CA1_…` (3), `CommandTest.H2_CA1_opciones_en_cualquier_lugar…`; 3a «set con mezcla…» (entre 35 % y 65 % de piedra) |
| H2 CA2 | `ParserTest.H2_CA2_mascaras_…`, `H2_CA2_la_mascara_compilada_…`; 3a «set con mezcla…» (`replace glass with oak_planks`) |
| H2 CA3 | `ParserTest.H2_CA3_errores_claros_con_sugerencia`, `H2_CA3_F02_…`; `CommandTest` (`--previwe`); 3a «los errores dicen qué corregir y no tocan nada»; compuerta 2 (`stnoe`) |
| H2 CA4 | `ParserTest.H2_CA4_presets_por_nombre`, `ConfigTest` (preset inválido se omite); 3a autocompletado de `$castle_wall` |
| H2 CA5 | `ParserTest.H2_CA5_…`, `EditTest.H4_CA3_la_vista_previa_muestra_exactamente_lo_que_hace_la_obra`; 3a «una obra grande…» (la obra pone lo que mostró la vista previa; dos vistas previas, dos mezclas) |
| H2 CA6 | 3a «sin permiso…» (`stone,grass_block`, `#logs`, `$castle_wall`) |
| H3 CA1 | `EditTest.H3_CA1_…` (3); 3a «set con mezcla…» (set 500, faces 170 hueca, replace solo el vidrio) |
| H3 CA2 | `EditTest.H3_CA2_copiar_y_pegar_…`, `H3_CA2_cortar_copia_y_deja_aire`; 3a «copiar, girar y pegar…» (el oro donde iba aire copiado sigue) |
| H3 CA3 | `EditTest.H3_CA3_girar_mueve_los_bloques_y_gira_su_estado` (también voltear de arriba abajo); 3a (escalera `north` → `east`) |
| H3 CA4 | `EditTest.H3_CA2_copiar_y_pegar_…` (datos del cofre); 3a (el cofre pegado tiene 5 diamantes) |
| H4 CA1 | 3a «una obra grande espera confirmación…» (36 000 bloques; nada cambia sin `confirm`); compuerta 2 (100 000) |
| H4 CA2 | `EditTest.H4_CA2_la_vista_previa_se_corta_en_el_tope_y_lo_dice`; 3a (Ana ve 36 000 bloques falsos, Beto ninguno, el mundo sigue en aire) |
| H4 CA3 | `EditTest.H4_CA3_…`; 3a (lo que ve Beto tras `confirm` = lo que mostró la vista previa a Ana) |
| H4 CA4 | 3a (`cancel`: Ana vuelve a ver aire). Vencimiento y salida del juego: por diseño (`Edits.expire`, `quit`), sin escenario |
| H5 CA1 | `SchedulerTest.H5_CA1_…`, `EditTest.H5_CA1_F01_…`; 3a (tick más largo de la obra de 36 000 bloques ≤ 12 ms); compuerta 2 (`jobs`) |
| H5 CA2 | `SchedulerTest.H5_CA2_una_operacion_enorme_no_deja_esperando_a_la_chica_de_otro` |
| H5 CA3 | `EditTest.H5_CA3_espera_un_chunk_que_no_esta_cargado…`, `RegionTest.H5_CA3_…` |
| H5 CA4 | `EditTest.H5_CA4_cancelar_deja_lo_hecho_deshacible`, `SchedulerTest.cancelar_y_apagar` |
| H5 CA5 | Por diseño: `setBlockData(…, false)` (sin física); 3a (la arena de la mezcla no cae: 36 000 bloques vistos) |
| H5 CA6 | `SchedulerTest.H5_CA6_dos_operaciones_que_se_cruzan_no_corren_a_la_vez` |
| H6 CA1 | `EditTest.H6_CA1_…`, `HistoryTest.H6_CA1_y_CA7_…`; 3a «set con mezcla…» (undo, redo, history, `undo 2`) |
| H6 CA2 | `HistoryTest.H6_CA2_…` (3); compuerta 2 (segundo arranque: el historial siguió y `undo` funcionó) |
| H6 CA3 | `EditTest.H6_CA3_deshacer_no_pisa_lo_que_otro_cambio_despues`; 3a (el oro puesto después se queda; «1 block(s) were left alone») |
| H6 CA4 | `EditTest.H6_CA4_…`; 3a (cut y undo devuelven el cofre con sus 5 diamantes) |
| H6 CA5 | `HistoryTest.H6_CA5_limpieza_por_cantidad_y_por_dias` |
| H6 CA6 | 3a «con los límites de default…» (Ana ve y deshace lo de Beto; Beto no puede con lo de Ana) |
| H6 CA7 | `HistoryTest.H6_CA2_y_CA7_sobrevive_al_reinicio_y_lo_que_estaba_en_marcha_queda_cortado` |
| H7 CA1 | `ConfigTest.H7_CA1_con_varios_grupos_gana_lo_mas_generoso` |
| H7 CA2 | 3a «con los límites de default…» (100 000 rechazado con el tope y el grupo) |
| H7 CA3 | `ConfigTest.H7_CA1_…`; 3a (`10% bedrock` rechazado) |
| H7 CA4 | `ConfigTest.H7_CA1_…` (mundos) |
| H7 CA5 | Por diseño (`Edits.busy`); sin escenario |
| H7 CA6 | 3a (`/bwr limits` de Beto); compuerta 2 |
| H8 CA1 | 3a «sin permiso…» (Beto) |
| H8 CA2 | `ConfigTest.H8_CA2_…` (2); 3a «validate y selftest…» (config roto: 1 error y 1 aviso con «did you mean») |
| H8 CA3 | `ConfigTest.H8_CA3_los_archivos_que_trae_el_plugin_cargan_sin_avisos`; compuerta 2 («Configuration OK») |
| H8 CA4 | `CommandTest.H8_CA5_…` (cada mensaje existe); 3a (Beto no ve subcomandos) |
| H8 CA5 | `CommandTest.H8_CA5_cada_subcomando_tiene_ayuda_y_cada_mensaje_existe`; 3a (`help replace`) |
| H8 CA6 | Compuerta 2 y 3a «validate y selftest…» |

## Hallazgos
| ID | Severidad | Descripción | Evidencia | Prueba de regresión | Estado |
|---|---|---|---|---|---|
| F-01 | Alta | El trabajo miraba el reloj cada 64 bloques: con bloques lentos (código frío, luz) un tick llegaba a 23,5 ms con 8 de presupuesto | Compuerta 2: `bwr jobs` «max 23.5 ms (budget 8 ms)» | `EditTest.H5_CA1_F01_no_se_pasa_del_plazo_aunque_cada_bloque_sea_lento` (verificado: falla con el código anterior, «se pasó hasta 32.0 ms») | Resuelto: el reloj se mira en cada bloque (unos 25 ns). Con el código caliente, el tick más largo fue 5,2 ms |
| F-02 | Baja | `stnoe` sugería `snow` (empate a distancia 2 con `stone` en el registro real) | Compuerta 2: «Unknown block 'stnoe' (did you mean 'snow'?)» | `ParserTest.H2_CA3_F02_letras_cambiadas_de_lugar_sugieren_la_palabra_correcta` (verificado: falla con el código anterior) | Resuelto: dos letras vecinas cambiadas de lugar cuentan 1 (Damerau) |
| F-03 | Alta | Una edición de `messages.yml` dejó `jobs-line:"…"` sin espacio: el YAML no cargaba y **todos** los mensajes salían «Missing message». La unitaria de mensajes no lo vio porque `YamlConfiguration.loadConfiguration` se traga el error y devuelve un archivo vacío | 3a, primera corrida: 0/8 con «Missing message 'no-permission'»; `e2e.log`: «Cannot load pluginsBlockwrightmessages.yml» | `CommandTest.H8_CA5_…` ahora lee el archivo con `loadFromString` (estricto); verificado: con el archivo roto falla («found character '%' that cannot start any token») | Resuelto |

## Medidas
| Obra | Bloques | Resultado |
|---|---|---|
| `set 70% stone, 30% glass` (compuerta 2, código caliente) | 100 000 | Menos de 1 s; tick más largo del plugin 4–5 ms |
| `set 50% dirt, 50% sand` con vista previa (3a) | 36 000 | Tick más largo de la obra: 8,1 y 8,0 ms (presupuesto 8 ms; el escenario acepta hasta 12) |

## Riesgos residuales
- **Picos en frío.** El primer tick tras arrancar el servidor puede pasar del presupuesto (en una corrida se midió un
  máximo de 144 ms en una obra de 27 bloques, sin repetirse): carga de clases, primer guardado YAML, primer MiniMessage o
  una pausa del recolector dentro de ese tick. `/bwr jobs` muestra el máximo desde el arranque y el tick más largo de la
  última obra.
- El diario se escribe en el hilo de E/S en el mismo tick en que se ponen los bloques (como la 021): si el servidor se
  cae en ese instante, la última tanda podría no quedar en el diario.
- La vista previa son bloques falsos del cliente: si el jugador rompe o mira de cerca un bloque falso, el cliente puede
  corregirlo antes de tiempo. Si vence o se cancela, se le devuelven los reales.
- Vencimiento de lo pendiente, salir con una vista previa abierta, `max_jobs` y obras en chunks sin cargar (más allá de
  la prueba con un mundo en memoria): por diseño, sin escenario en el juego.
- Solo contenedores y carteles viajan con sus datos (cabezas, estandartes, generadores, libros en atriles, no: roadmap).
- Sin verificar en Paper 26.1.2 (la suite tiene `--paper 26.1.2`; no se corrió por tiempo).
- Sin revisión exploratoria de una persona: las decisiones de CLARIFY son propuestas de la IA pendientes del dueño.

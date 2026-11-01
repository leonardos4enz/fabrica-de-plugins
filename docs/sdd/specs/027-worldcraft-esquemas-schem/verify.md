# Verificación 027 — Worldcraft: esquemas `.schem` de WorldEdit

**Fecha:** 2026-11-01 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar; decisiones de
CLARIFY pendientes del dueño · **Versión:** Worldcraft 0.3.0

| Compuerta | Resultado |
|---|---|
| Unitarias | `./gradlew :worldcraft:build` en verde: **94 pruebas** (28 nuevas: NBT y `varint` 7, formatos v1/v2/v3 6, esquema ↔ portapapeles con el mundo en memoria 5, carpetas y nombres 6, pasos por tick y textos 3, configuración 1; `CommandTest` con `schem`) |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24), actualizando desde la 0.2.0 (`config.yml` y `messages.yml` viejos: la sección `schematics` y los mensajes nuevos salen de los valores de fábrica). Desde la consola: `validate` OK; un cofre con 5 diamantes, un cartel «Hola/mundo» y una escalera copiados, girados 90 y guardados (`gate/test`, 1 KB); guardar otra vez sin `--overwrite` se rechaza; `list` con subcarpeta; `load` («2 chest(s), container(s) or sign(s) keep their contents») y `paste` en otro lugar: `data get` muestra el cofre girado con sus 5 diamantes y el cartel con su texto; un GZip que no es NBT y un archivo crudo dan «not an NBT file» sin tocar el portapapeles; `../server` no es un nombre válido; `lod` → «did you mean 'load'»; `undo`; `delete` a la papelera. Log sin errores en los dos arranques. F-01 salió de aquí |
| En el juego (3a) | `npm --prefix tests-e2e test -- --plugin worldcraft` con **WorldEdit 7.3.19** en el servidor de prueba: **15/15 dos veces seguidas** (12 de la 025 y la 026 + 3 nuevos) en Paper 1.21.4, servidor propio `plugins/worldcraft/run/` (puerto 25599 libre antes). WorldEdit guarda un `.schem` v3 (de fábrica) y uno v2 (`sponge.2`) y Worldcraft los ve en `list` como `[WorldEdit]`, los carga y los pega con cofre, escalera y cartel en el mismo lugar relativo; Worldcraft guarda uno normal y uno girado y WorldEdit (`//schem load`, `//paste`) los pega igual, con los ítems del cofre. La primera corrida falló un escenario por el texto de `//copy` de WorldEdit («6 blocks affected»), corregido en la suite |
| Revisión exploratoria (3b) | Con la consola del servidor real y los bots, mirando los mensajes y los tiempos por tick (`wc jobs`). Sin una persona jugando: queda para el dueño (ver riesgos) |
| README | Del plugin: sección «Schematics (.schem): coming from WorldEdit» con los cuatro comandos, la carpeta de WorldEdit, lo que se pega y lo que no, bloques viejos, seguridad y rendimiento; `schematics` en Configuration, permisos nuevos, «Not yet» y Compatibility con 0.3.0. Raíz: fila de Worldcraft (0.3.0, esquemas) y la suite con WorldEdit. Sin capturas (lección 004) |

## Cobertura
| Criterio | Prueba |
|---|---|
| H1 CA1 | `SchemFormatTest.H1_CA1_v3_escrito_y_leido_igual`, `SchemConvertTest.H1_CA1_CA2_CA3_…` (girado: igual que pegar el portapapeles girado), `…el_aire_que_falta…`; 3a «un esquema guardado por Worldcraft (girado)…» (WorldEdit pega la escalera mirando al este) |
| H1 CA2 | `SchemConvertTest.H1_CA1_CA2_CA3_…`; 3a (WorldEdit lo pega en el mismo lugar relativo a Ana) |
| H1 CA3 | `SchemConvertTest.H1_CA1_CA2_CA3_…` (datos del cofre); compuerta 2 (cofre y cartel); 3a (WorldEdit pega el cofre con 5 diamantes y el cartel con su texto) |
| H1 CA4 | `SchemFilesTest.H1_CA4_no_pisa_sin_overwrite…`; compuerta 2; 3a («already exists… --overwrite») |
| H1 CA5 | Compuerta 2 y 3a (nombre, tamaño, bloques y KB); por diseño (hilo `Worldcraft-Schematics`, `TaskJob`) |
| H2 CA1 | `SchemConvertTest.H2_CA1_un_esquema_con_offset_se_pega_donde_dice`; 3a (cargar y `paste` de lo de WorldEdit, `undo`) |
| H2 CA2 | `SchemFormatTest` (v1, v2 con `WEOffset`, v3); 3a (v3 y v2 hechos por WorldEdit 7.3.19, «Sponge v3» y «Sponge v2» en el mensaje) |
| H2 CA3 | `SchemConvertTest.H2_CA3_…` (lo que no se pega se cuenta y se conserva con su versión de datos); 3a (cofre y cartel de WorldEdit) |
| H2 CA4 | `SchemConvertTest.H2_CA4_desconocidos_a_aire_nombres_cambiados_y_propiedades_que_no_existen` |
| H2 CA5 | Por diseño (`schem-note-entities`, `schem-note-newer`); sin escenario |
| H2 CA6 | Compuerta 2 (mensaje con tamaño y notas) |
| H3 CA1 | `SchemFilesTest.H3_CA1_CA4_…`; 3a (`[WorldEdit]` en la lista, `load` desde esa carpeta) |
| H3 CA2 | `SchemFilesTest.H4_CA2_…` (la de WorldEdit no se borra); 3a («is in the WorldEdit folder: Worldcraft only reads there» y el archivo sigue) |
| H3 CA3 | `SchemFilesTest.H3_CA3_sin_carpeta_de_worldedit_no_pasa_nada`; compuerta 2 (sin WorldEdit, sin avisos) |
| H3 CA4 | `SchemFilesTest.H3_CA1_CA4_…` (`castles/tower`); compuerta 2 (`gate/test`) |
| H4 CA1 | 3a (`list e2e_we` con filtro); compuerta 2 |
| H4 CA2 | `SchemFilesTest.H4_CA2_borrar_va_a_la_papelera_y_se_vacia_despues`; 3a (el archivo pasa a `.trash/` y `load` ya no lo encuentra) |
| H4 CA3 | 3a (Beto: `load e2e_` sugiere el propio y el de WorldEdit; sin permiso no ve `delete`) |
| H5 CA1 | `SchemFilesTest.H5_CA1_…` (nombres y un enlace que sale); compuerta 2 y 3a (`../server`, `C:/Windows/win`) |
| H5 CA2 | `NbtTest.H5_CA2_…` (largo mentiroso, profundidad, bomba de GZip), `SchemFormatTest.H5_CA2_una_cabecera_enorme…` |
| H5 CA3 | `NbtTest.H5_CA3_…`, `SchemFormatTest.H5_CA3_…`; compuerta 2 y 3a (archivo roto: el portapapeles sigue y se pega lo de antes) |
| H5 CA4 | 3a (Beto, grupo `default`, carga un esquema con un bloque de comandos y `paste` lo rechaza) |
| H5 CA5 | 3a (Beto con `worldcraft.schematic` sin `.delete`: `delete` y `--overwrite` rechazados) |
| H5 CA6 | Por diseño (`Schematics.busy`, mensaje `schem-busy`); sin escenario |
| H6 CA1 | `ConfigTest.spec027_H6_CA1_…`; compuerta 2 (un `config.yml` de la 0.2.0) |
| H6 CA2 | `CommandTest.H8_CA5_…` (ayuda de `schem` y cada mensaje existe) |

## Hallazgos
| ID | Severidad | Descripción | Evidencia | Prueba de regresión | Estado |
|---|---|---|---|---|---|
| F-01 | Media | El primer `copy` con un cofre tras arrancar hizo un tick de 18–20 ms (presupuesto 8): serializar ítems por primera vez es código frío (como la 025 F-04), y guardar un esquema con cofres pasa por ahí | Compuerta 2: `wc jobs` «max 18.4 ms» justo después del primer `copy`; guardar y pegar después, 6–7 ms | Sin unitaria (código frío del servidor real, como la 025 F-04): compuerta 2 en frío tras el cambio: el máximo desde el arranque tras el primer `copy` con cofre bajó de 18,4 a 3,7 ms | Resuelto: `Edits.warmUp()` serializa un ítem y hace la ida y vuelta de la forma de la 025 a la estándar al habilitar |
| F-02 | Baja (suite) | La suite esperaba «were copied» de `//copy` y WorldEdit 7.3.19 dice «6 blocks affected» | 3a, primera corrida del escenario | La suite acepta los dos textos | Resuelto |

## Riesgos residuales
- Datos de bloque que no son contenedores ni carteles (cabezas, estandartes, generadores) no se pegan; se conservan al volver
  a guardar, pero con la versión de datos del archivo original (si es vieja, quedan sin actualizar). Entidades y biomas no
  se cargan. El color de tinte de un cartel no viaja (tampoco en la 025).
- Los bloques de mods o de versiones más nuevas se cargan como aire (el mensaje los nombra). Los nombres cambiados que se
  traducen son solo los de la lista (`SchemBlocks.RENAMED`).
- Un esquema grande se carga entero en memoria (5 M de bloques ≈ 40 MB mientras se carga); el tope es `max_blocks`.
- Sin verificar en Paper 26.1.2 ni con FAWE (se leen sus archivos por formato, sin probar con el plugin instalado).
- Sin revisión exploratoria de una persona: las decisiones de CLARIFY son propuestas de la IA pendientes del dueño.

# Verificación 007 — OmniEvents: esquema JSON y referencia generada

**Fecha:** 2026-10-24 · **Resultado:** Aprobada · **Versión:** OmniEvents 1.2.0

| Compuerta | Resultado |
|---|---|
| Unitarias | `gradlew build`: **73/73** (11 nuevas en `SchemaTest`). Los ejemplos, ahora con la línea `# yaml-language-server`, cargan con 0 errores y 0 avisos y validan contra el esquema |
| Servidor real (carga, validate, selftest, reload) | `compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni reload" "omni reload" "omni stats" "omni schema" "omni schema"`: carga 1.2.0 con 21 reglas, `validate` sin errores, `selftest` 12/12, dos `reload` con 8 oyentes · 3 temporizadores · 3 comandos. Los dos `schema` seguidos responden con las rutas y la línea para pegar, y no dejan `.tmp`. El `omnievents.schema.json` que escribe el servidor es **idéntico byte a byte** al publicado en `docs/omnievents/` (el registro vivo coincide con el de la tarea Gradle). Segundo arranque sin errores |
| En el juego (3a) | `npm --prefix tests-e2e test`: **27/27 dos veces** en Paper 1.21.4 build 232 (56 s y 55 s). Paper 26.1.2 (Java 25, bot 26.1): **27/27 dos veces seguidas** (53 s y 52 s, build 74). Antes: un primer intento no arrancó porque el servidor de otro proyecto (UANL) ocupaba el puerto 25599, y la corrida previa a las dos buenas dio 26/27 por `player_attack + set_damage`: el bot no recibió la barra «e2e golpe a COW» en 8 s. Esta spec no toca el combate ni las acciones, solo su documentación. Sin escenarios nuevos: ninguna capacidad de la 007 necesita un jugador (plan §4); la suite corre como regresión del cambio en los registros |
| Revisión exploratoria (3b) | Los ejemplos y los casos que deben marcarse, validados también con **Ajv 8 y el parser `yaml` 2** (expresiones regulares de JavaScript y YAML 1.2, como la extensión de Red Hat en VS Code): los 7 ejemplos están bien, los 5 casos malos se marcan y `200µs` y `to_world: world: broadcast:` pasan. La referencia se leyó completa: dio F-01 y F-02 |
| README (capacidades, Compatibilidad, tabla raíz) | OmniEvents: sección nueva «Editor: autocompletado y errores en VS Code», enlaces a la referencia en Expresiones, Acciones y Disparadores, `schema` en Comandos, API con documentación y Compatibilidad 1.2.0. Raíz: versión 1.2.0, enlace a la referencia y estructura de `docs/omnievents/`. `CLAUDE.md`: comando `:omnievents:schema`. Sin capturas nuevas (decisión 6) |

## Cobertura
| Criterio | Prueba |
|---|---|
| H1 CA1 | `H1_CA5_marca_lo_que_el_cargador_rechaza` (`priority: urgente`, duración inválida, hora `25:00`); el esquema lista cada opción de `RuleOptions` con su descripción |
| H1 CA2 | Esquema: `trigger` es `enum` con `enumDescriptions` del registro; `H2_CA1_la_referencia_lista_todo` |
| H1 CA3 | `H1_CA3_el_ejemplo_de_cada_accion_y_su_forma_corta_validan` (cada ejemplo, la forma corta de cada acción, cada alias, prefijos `to_all:`/`to_world:`) |
| H1 CA4 | `H1_CA4_sintaxis_de_la_006` (`expr`/`deny`, `any` anidado, `budget: 200us`); `herramientas.yml` valida |
| H1 CA5 | `H1_CA5_marca_lo_que_el_cargador_rechaza`: 16 casos (opción, disparador y acción desconocidos, clave que la acción no usa, `to` en flujo, condición sin `expr` ni `any`, `any: 0`, `any` sin `of`, `budget: 5`, `timer` sin `every`, `command` sin nombre…) |
| H1 CA6 | `H1_CA6_las_expresiones_no_se_validan_y_listan_las_funciones` |
| H1 CA7 | `H1_CA7_los_ejemplos_del_plugin_validan`, `H1_CA7_lo_que_produce_el_importador_valida`; revisión 3b con Ajv |
| H2 CA1 | `H2_CA1_la_referencia_lista_todo` (cada disparador, acción, función, opción y alias) |
| H2 CA2 | `H2_CA2_todo_lo_registrado_tiene_descripcion`, `H2_CA2_cada_ejemplo_de_accion_carga_sin_problemas_y_usa_sus_claves`, `H2_CA2_publicados_al_dia` (falla con un `docs/omnievents/referencia.md` modificado: comprobado agregándole una línea) |
| H3 CA1 | `./gradlew :omnievents:schema` escribe los dos archivos sin servidor |
| H3 CA2 | Compuerta 2 (`omni schema` dos veces, mensaje con rutas y línea, archivos idénticos a los publicados); escritura en `runTaskAsynchronously` con temporal y `ATOMIC_MOVE`; permiso `omnievents.command.schema` en `plugin.yml` |
| H3 CA3 | `H3_CA3_lo_que_registra_otro_plugin_aparece` (acción y función sin documentación: aparecen en «Otros plugins» con la descripción genérica, y el esquema las acepta) |

## Hallazgos
| ID | Severidad | Hallazgo | Estado |
|---|---|---|---|
| F-01 | Baja | En la referencia, los textos con `<nombre>` (`var.<nombre>`, `world:<mundo>`) se veían como etiquetas HTML y GitHub los borraba | Resuelto: fuera de los bloques de código, `<` se escribe `&lt;`. Prueba: `H2_CA2_publicados_al_dia` fija el texto generado |
| F-02 | Baja | Los grupos de acciones salían en orden alfabético de su primera acción («Mensajes» antes que «Flujo») | Resuelto: siguen el orden del registro (flujo, mensajes, comandos, jugador, mundo, evento, otros plugins). Fijado por `H2_CA2_publicados_al_dia` |

Los dos hallazgos son del generador nuevo, no de código que ya existía: no había una versión anterior en la que la
prueba debiera fallar. La referencia publicada fija el texto, y cualquier cambio rompe `H2_CA2_publicados_al_dia`.

## Riesgos y pendientes
- El escenario `H6 CA1 · H5 CA5 — player_attack + set_damage` falló una vez en 26.1.2 (el bot no registró el golpe a
  la vaca en 8 s; la vaca ya tiene `NoAI`) y pasó en las dos corridas siguientes y en las dos de 1.21.4. No se
  investigó la causa: queda para la spec de la fábrica «más escenarios en el juego» (posible carrera entre el bot y el
  servidor al apuntar).
- La validación usa `com.networknt:json-schema-validator` (expresiones de Java) en las unitarias. VS Code usa
  JavaScript: por eso la revisión 3b repitió los casos con Ajv. Si una spec agrega un patrón con sintaxis que solo
  entiende uno de los dos (lookbehind, `\p{…}`), conviene volver a correr esa revisión.
- La línea `$schema` de los ejemplos apunta a `main` en GitHub: el esquema publicado puede ir por delante del jar
  instalado en un servidor viejo. Para el esquema exacto del servidor está `/omni schema`.
- Nombres en mayúsculas (`Message:`, `trigger: BLOCK_BREAK`): el cargador los acepta y el editor los marca. Está
  documentado. Aceptarlos en el editor con patrones sin distinguir mayúsculas haría el esquema mucho más grande.
- Sin probar en VS Code a mano. Lo cubren el mismo formato (draft-07) y la validación con Ajv.

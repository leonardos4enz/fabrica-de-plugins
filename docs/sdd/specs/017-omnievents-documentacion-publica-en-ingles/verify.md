# Verificación 017 — Documentación pública en inglés

**Fecha:** 2026-10-27 · **Resultado:** Aprobada

| Compuerta | Resultado |
|---|---|
| Unitarias | `./gradlew build`: 203 pruebas, 0 fallas (200 de antes + 3 de `DocsSiteTest`) |
| Sitio estático | `zensical build -f docs/site/mkdocs.yml` (Zensical 0.0.73): «No issues found»; `zensical serve` revisado en el navegador local (barra lateral, tablas, bloques de código, búsqueda) a 800 y 1280 px |
| GitBook | Publicada el 2026-10-27 en https://leonardos4enz.gitbook.io/leonardos4enz-docs/ (Git Sync del espacio «Documentation» con la rama `docs`, carpeta `docs/site`, dirección GitHub → GitBook; GitBook no hizo commits). `SUMMARY.md` y `.gitbook.yaml` siguen el formato de Git Sync; la prueba exige que coincidan con el `nav:` |
| Servidor real (compuerta 2) y en el juego (3a) | No aplican: la spec no cambia el código del plugin (solo pruebas y la entrada `docs/site` de la tarea `test`). Los ejemplos de las páginas se cargan con el mismo `RuleLoader` que usa el servidor |
| Revisión exploratoria (3b) | Lectura de las 12 páginas contra la referencia generada y el código (ver hallazgos) |
| README | Raíz: fila de OmniEvents con el enlace a la documentación en inglés, `docs/site/` en la estructura y la sección «Documentación pública en inglés» (previsualizar y publicar con GitBook o GitHub Pages). Plugin: enlace a la documentación en inglés. Compatibilidad sin cambios (el jar no cambió). Sin capturas nuevas |

## Cobertura
| Criterio | Prueba o evidencia |
|---|---|
| H1 CA1 | `docs/site/pages/omnievents/`: `README.md`, `installation.md`, `first-rule.md`, `rule-options.md`, `triggers.md`, `conditions.md`, `actions.md`, `variables.md`, `commands.md`, `migrating-from-conditionalevents.md`, `more-features.md`, `faq.md` (unas 9000 palabras) |
| H1 CA2 | Texto escrito desde el README, la referencia y el código de OmniEvents; de la wiki de CE solo se tomó la idea de páginas por tema. El ejemplo de CE de la página de migración es propio |
| H1 CA3 | `DocsSiteTest.H1_CA3_los_ejemplos_de_reglas_cargan_y_sus_pruebas_pasan`: 24 bloques de reglas (de 25 bloques ```yaml), 0 problemas, y las pruebas `tests:` de los ejemplos pasan |
| H2 CA1 | `docs/site/.gitbook.yaml` (`root: ./pages/`) y `docs/site/pages/SUMMARY.md` |
| H2 CA2 | `docs/site/mkdocs.yml` + `zensical build` y `zensical serve` |
| H2 CA3 | `DocsSiteTest.H2_CA3_los_indices_coinciden_y_los_enlaces_existen` (también los anclajes `#…` de los enlaces internos) |
| H2 CA4 | README raíz, sección «Documentación pública en inglés» |
| H3 CA1 | `DocsSiteTest.H3_CA1_disparadores_acciones_y_funciones_estan_todos` (75 disparadores, 72 acciones y 65 funciones del registro contra las tablas) |

**La prueba falla con lo que debe atrapar** (se rompió cada cosa en una copia y se restauró): `trigger: player_jion` en
`first-rule.md` → «disparador desconocido 'player_jion' (¿quisiste decir 'player_join'?)»; dos entradas del `nav:`
intercambiadas → «SUMMARY.md y el nav de mkdocs.yml deben listar las mismas páginas en el mismo orden»; la fila de
`item_mend` borrada de `triggers.md` → «disparadores sin documentar en inglés: [item_mend]».

## Hallazgos
| ID | Severidad | Descripción | Evidencia | Prueba de regresión | Estado |
|---|---|---|---|---|---|
| F-01 | Media | Gradle no volvía a correr `DocsSiteTest` al cambiar solo `docs/site/`: la tarea `test` quedaba «al día» y la prueba de rotura pasó en verde sin correr | Primera prueba de rotura sin fallas | `inputs.dir("docs/site")` en la tarea `test` de `plugins/omnievents/build.gradle.kts`; la prueba de rotura repetida falla como debe | Corregido |
| F-02 | Baja | Primer borrador con datos inexactos frente a la referencia: `lightning` sin daño, `set_block` con coordenadas, `gamemode` en mayúsculas, `%target:…%` de CE (es PlaceholderAPI del objetivo, no `target.name`), `/omni data` y `--simular` | Lectura contra `referencia.md`, el importador y `OmniCommand` | Revisión manual (no se puede probar el texto) | Corregido |
| F-03 | Baja | Un menú de ejemplo se carga como varias reglas (`shop.menu`, `shop.menu.items.sword.click`): la prueba los contaba como «no cargó» | Primera corrida de `DocsSiteTest` | La prueba acepta la regla o sus rutas `nombre.` | Corregido |

## Riesgos residuales
- Las descripciones en inglés de disparadores, acciones y funciones se escribieron a mano: la prueba exige que estén
  todos, no que la descripción siga siendo exacta si una spec cambia su comportamiento. La referencia generada en
  inglés queda para otra spec (roadmap).
- Los mensajes del plugin, los ejemplos de `rules/ejemplos/` y la referencia generada siguen en español; las páginas lo
  dicen.
- Zensical es joven (0.0.73) y Material for MkDocs termina su mantenimiento en noviembre de 2026; el contenido es
  Markdown simple, así que cambiar de herramienta solo toca `mkdocs.yml`.
- No se probó el render en GitBook (falta la cuenta del dueño). Riesgo bajo: GitBook muestra Markdown estándar y las
  páginas no usan bloques propios de ninguna herramienta.

# Plan 017 — Documentación pública en inglés

## 1. Estructura (el contrato)
```
docs/site/
  .gitbook.yaml          # GitBook Git Sync: root ./pages/, summary SUMMARY.md
  mkdocs.yml             # Zensical / MkDocs Material: docs_dir pages, nav igual a SUMMARY.md
  pages/
    README.md            # inicio (GitBook usa README.md como primera página; MkDocs, como index)
    SUMMARY.md           # índice de GitBook (la barra lateral)
    omnievents/
      README.md          # qué es OmniEvents, por qué, compatibilidad
      installation.md
      first-rule.md
      rule-options.md
      triggers.md        # todos los disparadores del registro, por grupo
      conditions.md      # condiciones, deny, «N de M», expresiones y todas las funciones
      actions.md         # modificadores, flujo y todas las acciones
      variables.md
      commands.md        # comandos y permisos
      migrating-from-conditionalevents.md
      more-features.md   # estados, menús, paquetes, integraciones, tests:, diagnóstico, API (resumen + enlaces)
      faq.md
```
- **Markdown simple**: tablas, listas, bloques ```yaml y citas `>`. Nada de bloques propios de GitBook
  (`{% hint %}`) ni de MkDocs (`!!! note`), para que las dos herramientas lo muestren igual.
- **Enlaces relativos** entre páginas (`triggers.md#combat`). Lo que vive fuera de `docs/site/` (README del plugin,
  referencia en español, ejemplos) se enlaza con la URL de GitHub (`https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/...`),
  porque GitBook y el sitio estático solo publican `pages/`.
- **Ejemplos de reglas en inglés** (nombres y mensajes); cada bloque ```yaml cuyo primer nivel son reglas (cada valor
  es un mapa con `trigger`, `type`, `menu` o `template`) es una regla completa y la prueba la carga. Los fragmentos
  (una lista de acciones, un `config.yml`) no son reglas y no se cargan.
- Los mensajes del plugin (`messages.yml`, la consola) están en español: las páginas lo dicen donde se ve una salida.

## 2. Pruebas (gradle, `:omnievents:test`)
`DocsSiteTest` en `plugins/omnievents/src/test/java/dev/leo/omnievents/schema/` (junto a la prueba de la referencia):
- **H1 CA3:** extrae los bloques ```yaml de `docs/site/pages/omnievents/*.md`. Cada bloque que es un conjunto de reglas
  se escribe en una carpeta temporal propia y se carga con `RuleLoader.loadFolder`: 0 problemas. Después
  `RuleTester.runAll` y 0 fallas. Exige un mínimo de bloques cargados para que la prueba no pase vacía si cambia el
  formato.
- **H2 CA3:** lee los enlaces `[t](ruta)` de `SUMMARY.md` y las rutas del `nav:` de `mkdocs.yml` (líneas `- Título: ruta.md`);
  deben ser la misma lista en el mismo orden. Cada `.md` de `pages/` (menos `SUMMARY.md`) está en el índice. Cada
  enlace relativo de cada página apunta a un archivo que existe.
- **H3 CA1:** compara los nombres en código (`` `nombre` ``) de las tablas de `triggers.md`, `actions.md` y la tabla
  de funciones de `conditions.md` con `Triggers`, `Actions` y `Functions` del registro. Falta uno o sobra uno →
  falla, con la lista. Los disparadores internos de menús (`menu_open`, `menu_click`, `menu_close`, que no se escriben
  a mano) y los alias se tratan como en la referencia generada.

## 3. Vista local
- `pip install zensical` (en un entorno virtual) y `zensical serve -f docs/site/mkdocs.yml` o `zensical build`.
  La compuerta de esta spec es `zensical build` sin errores ni avisos de enlaces.
- Sin Zensical, también funciona `pip install mkdocs-material` y `mkdocs serve -f docs/site/mkdocs.yml`.
- El sitio generado (`docs/site/site/`) es un artefacto: va a `.gitignore`.

## 4. Publicación (la hace el dueño)
- **GitBook:** crear el espacio, Integrations → GitHub Sync, repositorio `leonardos4enz/fabrica-de-plugins`, rama
  `main`, *project directory* `docs/site`. GitBook lee `.gitbook.yaml` y publica en `<nombre>.gitbook.io`.
- **GitHub Pages:** un workflow que corre `zensical build` y publica `docs/site/site/`. No se agrega en esta spec,
  porque activar Pages lo decide el dueño; el README raíz dice cómo.

## 5. STRIDE
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| Spoofing | Que el sitio parezca la wiki oficial de ConditionalEvents | Texto propio; se nombra a CE solo como origen de la migración |
| Tampering | Que una edición en el editor de GitBook vuelva como commit a `main` sin revisión | El dueño decide al conectar Git Sync; el README le recomienda una rama o revisar los commits de GitBook. Las pruebas corren sobre lo que entra |
| Repudiation | — | Los commits de GitBook quedan con su autor |
| Information disclosure | Publicar algo privado del repositorio | Solo se publica `docs/site/pages/`; las páginas no tienen IP, rutas locales ni nombres de jugadores reales |
| Denial of service | — | Sitio estático |
| Elevation of privilege | Un ejemplo que enseñe a dar permisos de más | Los ejemplos de permisos usan `omnievents.command.<sub>` y avisan que `*` es para administradores |

## 6. Derechos de autor
No se copia ni se traduce texto de la wiki de ConditionalEvents. Solo se toma la idea de la estructura: páginas por
tema y una barra lateral con instalación, eventos, condiciones, acciones, variables y comandos. Las tablas de
equivalencias (`%player%` → `{player.name}`) describen OmniEvents.

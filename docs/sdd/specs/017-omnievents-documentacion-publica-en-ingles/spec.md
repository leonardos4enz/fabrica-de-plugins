# Spec 017 — OmniEvents: documentación pública en inglés (sitio estilo GitBook)

**Estado:** Implementado y verificado · **Plugin:** OmniEvents (y la fábrica: carpeta `docs/site/`) · **Depende de:** 007 ·
**Origen:** el dueño preguntó cómo funciona GitBook, si cobra y si se puede tener una documentación como la wiki de
ConditionalEvents (https://ajneb97.gitbook.io/conditionalevents), en inglés claro (2026-10-27). La 003 sigue reservada.

## Problema
Toda la documentación de OmniEvents está en español y repartida en un README de 750 líneas, una referencia generada
y una galería. Para el dueño de un servidor que no habla español, OmniEvents no tiene documentación. Quien viene de
ConditionalEvents espera una wiki con páginas cortas y una barra lateral (instalación, primer evento, cada tema en su
página), y no un README largo.

## Investigación (2026-10-27)
| Opción | Precio | Dominio | Anuncios | Vista local |
|---|---|---|---|---|
| GitBook Free | $0, 1 usuario, Git Sync incluido | solo `*.gitbook.io` | no | no (el CLI está abandonado) |
| GitBook Sponsored (open source) | $0, se pide a soporte | propio | opcionales | no |
| GitBook Essential | $65 por sitio al mes (anual) + $12 por usuario | propio | no | no |
| Zensical / MkDocs Material + GitHub Pages | $0 (repositorio público) | `*.github.io` o propio | no | sí |
| Docusaurus + GitHub Pages | $0 | propio | no | sí (Node/React) |
| Read the Docs Community | $0 | `*.readthedocs.io` o propio | sí ($5 al mes sin ellos) | sí |

Material for MkDocs está en mantenimiento (correcciones hasta noviembre de 2026); su sucesor, Zensical, lee el mismo
`mkdocs.yml`. Fuentes: gitbook.com/pricing, gitbook.com/solutions/open-source, la página de precios de Read the Docs y
el anuncio de mantenimiento de Material.

## Historias
**H1 — Una wiki en inglés.** Como *dueño de un servidor que lee inglés*, quiero páginas cortas con una barra lateral
para instalar OmniEvents y escribir mi primera regla sin leer español.
- CA1: `docs/site/` tiene, en inglés, las páginas del primer alcance: inicio, instalación, primera regla, opciones de
  una regla, disparadores, condiciones y expresiones, acciones, variables, comandos y permisos, migrar desde
  ConditionalEvents (`/omni import`) y preguntas frecuentes.
- CA2: el texto es propio (ni copiado ni traducido de la wiki de ConditionalEvents); solo se toma la idea de la
  estructura (páginas por tema, barra lateral).
- CA3: cada ejemplo de regla completa de las páginas carga sin errores ni avisos con el cargador del plugin, y sus
  `tests:`, si los tiene, pasan (prueba en gradle).

**H2 — GitBook o sitio estático con el mismo contenido.** Como *dueño de la fábrica*, quiero publicar con GitBook gratis
o con GitHub Pages sin reescribir nada.
- CA1: `docs/site/SUMMARY.md` y `docs/site/.gitbook.yaml` dejan la carpeta lista para Git Sync de GitBook.
- CA2: `docs/site/mkdocs.yml` arma el mismo sitio con Zensical (o MkDocs Material) y se previsualiza en local.
- CA3: una prueba falla si `SUMMARY.md` y la navegación de `mkdocs.yml` no listan las mismas páginas en el mismo orden,
  si una página no está en el índice o si un enlace interno apunta a un archivo que no existe.
- CA4: el README raíz explica cómo previsualizar el sitio y cómo conectar GitBook o GitHub Pages; crear cuentas,
  conectar servicios y publicar lo hace el dueño.

**H3 — Que no se quede atrás.** Como *dueño*, quiero enterarme si una spec agrega un disparador, una acción o una
función y la documentación en inglés no lo menciona.
- CA1: una prueba falla si un disparador, una acción o una función del registro no aparece en su página
  (`triggers.md`, `actions.md`, `conditions.md`), o si una de esas páginas nombra algo que el registro no tiene.

## Fuera de alcance
- La referencia completa generada en inglés: unas 330 descripciones del registro traducidas, con una prueba de
  traducciones faltantes. Queda como spec propia; las páginas en inglés enlazan la referencia en español.
- Páginas propias en inglés para estados, menús, paquetes y plantillas, integraciones, `tests:`, diagnóstico y API.
  En este alcance van como un resumen en «More features» con enlace a la sección del README.
- Traducir `messages.yml` (los mensajes del plugin siguen en español) y los ejemplos de `rules/ejemplos/`.
- Capturas nuevas (la galería sigue en español, lección de la 004).
- Crear la cuenta de GitBook, conectar Git Sync o publicar GitHub Pages.

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Formato: GitBook, MkDocs/Zensical o ambos? | Ambos: Markdown simple, con `SUMMARY.md` para GitBook y `mkdocs.yml` para la vista local y GitHub Pages; una prueba mantiene iguales los dos índices |
| 2 | ¿Carpeta? | `docs/site/` (deja lugar a otros plugins y a otros idiomas) |
| 3 | ¿Alcance? | Guías a mano del primer alcance; la referencia generada en inglés va en otra spec |

## Preguntas abiertas
- (ninguna)

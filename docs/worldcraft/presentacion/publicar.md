# Publicar Worldcraft 0.13.0 en SpigotMC, Modrinth y Hangar (spec 038)

Todo lo que hace falta para crear los tres proyectos y para las próximas versiones. Las descripciones están al lado:
[`spigotmc.bbcode`](spigotmc.bbcode) (SpigotMC) y [`modrinth-hangar.md`](modrinth-hangar.md) (Modrinth y Hangar); la
lista de todos los comandos frente a WorldEdit y FAWE, en [`commands.md`](commands.md). `PresentacionTest` de Worldcraft
comprueba que las dos descripciones enlacen las mismas imágenes en el mismo orden, que existan, que digan «not
affiliated» y que la cantidad de comandos (102) sea la del código.

> **Antes de pegar:** las imágenes se enlazan por `raw.githubusercontent.com/…/main/docs/img/worldcraft/presentacion/`.
> Hasta que el commit de la spec 038 esté en `main` en GitHub, las páginas mostrarán imágenes rotas. Primero push,
> después publicar.

## Las cifras (y cómo rehacerlas)
- **102** comandos de `/wc`: las entradas de `help:` de `messages.yml` menos los atajos (`sp`). Si se agrega un comando,
  `PresentacionTest` falla hasta cambiar el número en las descripciones, `commands.md` y el póster.
- **171** de WorldEdit 7.3.19 y **244** de FAWE 2.16.2: las anotaciones `@Command` del bytecode (`javap -v` sobre las
  clases `*Commands` de cada jar; FAWE trae 265, de las que 21 son `/anvil`, que no se registra). **129** y **142**: los
  que Worldcraft tiene igual o mejor, comando por comando en `commands.md` (spec 038, §1 del plan). Al subir de
  versión con comandos nuevos, actualizar esa tabla y los números (los «en parte» no cuentan).

## Riesgos
- Nombrar a WorldEdit y FAWE para compararse es lo normal en estas páginas, pero la comparación tiene que ser justa: cada
  fila del póster y de la tabla se puede defender con `commands.md` y la [investigación](../../investigacion/paridad-worldedit-fawe.md).
  Si un autor o un moderador marca una fila como incorrecta, corregirla (no discutir) y volver a pegar.
- La línea «not affiliated» con EngineHub, IntellectualSites y Mojang va visible; no quitarla.
- No usar «WorldEdit» en el **título** ni como etiqueta (parece suplantación y atrae reportes): sí en el texto.

## Datos comunes
| Campo | Valor |
|---|---|
| Nombre | Worldcraft |
| Slug | Hangar `Worldcraft`; Modrinth `worldcraft-editor` (`worldcraft` estaba tomado) |
| Publicado (spec 038) | [SpigotMC](https://www.spigotmc.org/resources/worldcraft.140223/) · [Hangar](https://hangar.papermc.io/leonardos4enz/Worldcraft) · [Modrinth](https://modrinth.com/plugin/worldcraft-editor) |
| Resumen (Modrinth y Hangar, 73 caracteres) | `Lag-free world editing: preview, undo after restarts, terrain in one word.` |
| Tag line (SpigotMC) | El mismo resumen |
| Versión | `0.13.0` (canal **Release**: verificada con las compuertas de la 025–037) |
| Jar | `JAVA_HOME="C:\Program Files\Java\jdk-24" ./gradlew :worldcraft:dist` → `dist/Worldcraft-0.13.0.jar`. En `dist/` hay versiones viejas y `WorldcraftApiExample-1.0.0.jar`: **subir solo `Worldcraft-0.13.0.jar`** |
| Plataforma | Paper (no Spigot, no Folia) |
| Versiones de Minecraft a marcar | **1.21.4** (la única verificada). No marcar otras |
| Java | 21+ |
| Dependencias | Ninguna |
| Lado | Solo servidor |
| Licencia | MIT (`LICENSE` de la raíz del repositorio, © 2026 Leonardo Saenz) |
| Ícono | `docs/img/worldcraft/presentacion/logo.png` (512 px) |
| Código fuente | https://github.com/leonardos4enz/fabrica-de-plugins |
| Issues | https://github.com/leonardos4enz/fabrica-de-plugins/issues |
| Wiki / documentación | https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/plugins/worldcraft/README.md |
| Galería (Modrinth y Hangar) | `escenas/terrain.png`, `escenas/shapes.png`, `escenas/image.png` y `4-compared.png` |
| Categorías | Modrinth **utility** y **worldgen**; Hangar **World Management**; SpigotMC **Spigot → World Management** |

## Changelog
Para pegar en la versión 0.13.0 (en inglés, como el plugin). Es la primera publicación: va un resumen de la historia.

```markdown
## 0.13.0 (first public release)
- An API for other plugins: `WorldcraftApi.get()` reads and sets selections and runs `set`, `replace`, `paste` and
  `undo` through the same engine (limits, batches, history, undo); `WorldcraftEditEvent` (cancellable) before every
  edit and `WorldcraftEditDoneEvent` after it.

## Before 0.13.0 (in development)
- 0.12: `/wc regen` and `/wc snapshot` + `/wc restore` from backups (folders and .zip).
- 0.11: FAWE's caves, ores, pictures (`/wc image`) and color patterns (`#color`, `#darken`…).
- 0.10: formulas like WorldEdit's (`/wc generate`, `deform`, `=y<64` masks).
- 0.9: biomes, the game's trees and structure templates.
- 0.8: 12 building brushes, 9 one-click tools and the super pickaxe.
- 0.7: entities in copy, paste, stack, move and schematics; `butcher` and `remove`.
- 0.6: selections that are not boxes (polygon, ellipsoid, sphere, cylinder, convex, magic).
- 0.4–0.5: shapes, region tools, fixes around you, moving around, masks that look around, `gmask`.
- 0.3: WorldEdit `.schem` files (v1, v2, v3), read and written, with chests and signs.
- 0.2: `/wc mountain`, `/wc island` and the terrain brush.
- 0.1: the engine: edits in batches inside a time budget, preview, undo on disk, plain-words patterns, limits.
```

## Paso a paso por sitio

### Modrinth (https://modrinth.com)
1. **Create a project** → tipo **Plugin**, nombre `Worldcraft`, URL `worldcraft`, resumen de la tabla.
2. **Description:** pegar `modrinth-hangar.md` entero (el comentario `<!-- … -->` no se ve).
3. **Icon:** `logo.png`. **Gallery:** las imágenes de la tabla.
4. **Tags:** **utility** y **worldgen**. Entorno: solo servidor.
5. **Links:** Source, Issues y Wiki. **License:** MIT.
6. **Versions → Create version:** `Worldcraft-0.13.0.jar`, `0.13.0`, Release, loader **Paper** (quitar Purpur/Folia/
   Bukkit si los propone), Minecraft **1.21.4**, sin dependencias, changelog de arriba.
7. Declaración de contenido hecho con IA, como en SkinStatues (código, recursos y texto). **Submit for review.**
   Si la lista dice «link could not be verified» con enlaces a GitHub que sí abren: Modrinth los revisa desde tu
   navegador con la API de GitHub (60 consultas por hora); esperar la hora que da `https://api.github.com/rate_limit`
   (`reset`) y recargar (verify 038 F-05).

### Hangar (https://hangar.papermc.io)
1. **New project** → dueño: tu usuario, nombre `Worldcraft`, resumen, categoría **World Management**, licencia MIT,
   enlaces; palabras clave sin «WorldEdit» (`world editing`, `building`, `terrain`, `schematic`).
2. **Page:** pegar `modrinth-hangar.md`. **Icon:** `logo.png`.
3. **Versions → New version:** Release, `0.13.0`, **Paper 1.21.4**, `Worldcraft-0.13.0.jar`, sin dependencias, changelog.

### SpigotMC (https://www.spigotmc.org/resources/)
1. **Post Resource** → **Spigot** → **World Management**. Subir archivo (no premium).
2. **Title:** `Worldcraft`. **Tag line:** el resumen. **Version string:** `0.13.0`.
3. **Description:** pegar `spigotmc.bbcode` en modo **BBCode** (botón `[ ]`). Las 7 `[IMG]` van en **una sola línea**
   (020 F-05).
4. **Native Minecraft version / Tested:** **1.21**.
5. **Source code:** GitHub. **File:** `Worldcraft-0.13.0.jar`. **Icon:** marcar «Upload resource icon»: se pide
   **después** de guardar; `logo.png` reducido a 96 px.

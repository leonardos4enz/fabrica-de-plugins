# Publicar SkinStatues 0.3.0 en SpigotMC, Modrinth y Hangar (spec 024)

Todo lo que hace falta para crear los tres proyectos. Publicados el 2026-11-01 (ver el verify de la spec 024); esta guía queda
para las próximas versiones. Las descripciones están al lado: [`spigotmc.bbcode`](spigotmc.bbcode) (SpigotMC) y
[`modrinth-hangar.md`](modrinth-hangar.md) (Modrinth y Hangar). `PresentacionTest` de SkinStatues comprueba que las dos
enlazan las mismas imágenes, en el mismo orden, que existan, y que digan «not affiliated».

> **Antes de pegar:** las imágenes se enlazan por `raw.githubusercontent.com/…/main/docs/img/skinstatues/presentacion/`.
> Hasta que el commit de la spec 024 esté en `main` en GitHub, las páginas mostrarán imágenes rotas. Primero push, después
> publicar.

## Riesgo de las skins de youtubers (decisión 1)
El póster muestra skins reales de Technoblade, ElRichMC, LTeZla, Vegetta777, Willyrex, elrubius, Mikecrack, Dream,
GeorgeNotFound, DanTDM y Bobicraft, **sin pedirles permiso**, por decisión del dueño.
Tenerlo presente:
- Puede leerse como que esos creadores recomiendan el plugin. Las dos descripciones llevan visible: «Skins shown belong to
  their respective creators; SkinStatues is not affiliated with or endorsed by them or by Mojang.» No quitarla.
- Un creador (o su equipo) puede pedir que se quite su imagen, y un moderador de SpigotMC, Modrinth o Hangar puede pedir
  cambios por uso de nombres o imagen de terceros. Si pasa: quitar la imagen o rehacer la escena con otra skin (o con
  `Example`) y volver a pegar la descripción; conviene contestar rápido.
- Technoblade falleció en 2022: su skin es un homenaje frecuente, pero alguien puede encontrarlo de mal gusto en un
  anuncio. Si hay quejas, la franja 2 se puede rehacer con otra skin.
- No usar esos nombres en el **título, el resumen ni las etiquetas** (eso sí parece respaldo y atrae reportes): solo en
  las imágenes y su transcripción.
- Modrinth revisa a mano cada proyecto nuevo («Under review»); es posible que pregunten por esto.

## Datos comunes
| Campo | Valor |
|---|---|
| Nombre | SkinStatues |
| Slug sugerido | `skinstatues` (Modrinth y Hangar; si está tomado: `skin-statues`) |
| Resumen (Modrinth, ≤ 80; tiene 77) | `Build block statues of any player's skin, up to 128 blocks tall, in 28 poses.` |
| Tag line (SpigotMC) | `Block statues of any player's skin, any size, in 28 poses. By name or link.` (75 caracteres) |
| Descripción (Hangar, el campo corto) | El mismo resumen de Modrinth |
| Versión | `0.3.0` (canal **Release**; es la primera publicación, pero está verificada) |
| Jar | `JAVA_HOME="C:\Program Files\Java\jdk-24" ./gradlew :skinstatues:dist` → `dist/SkinStatues-0.3.0.jar`. En `dist/` también están la 0.1.0 y la 0.2.0: **subir solo la 0.3.0** |
| Plataforma | Paper (no Spigot, no Folia) |
| Versiones de Minecraft a marcar | **1.21.4** y **26.1.2** (las únicas verificadas: suite 18/18 dos veces en cada una, 2026-11-01). No marcar otras |
| Java | 21+ (Paper 26.x necesita Java 25) |
| Dependencias | Ninguna (ni obligatorias ni opcionales) |
| Lado | Solo servidor |
| Licencia | MIT (`LICENSE` de la raíz del repositorio, © 2026 Leonardo Saenz) |
| Ícono | `docs/img/skinstatues/presentacion/logo.png` (512 px) |
| Código fuente | https://github.com/leonardos4enz/fabrica-de-plugins |
| Issues | https://github.com/leonardos4enz/fabrica-de-plugins/issues |
| Wiki / documentación | https://github.com/leonardos4enz/fabrica-de-plugins/blob/main/plugins/skinstatues/README.md |
| Galería (Modrinth y Hangar, opcional) | `2-giant.png`, `3-any-player.png` y `5-name-or-link.png` de la misma carpeta; la hoja de poses `docs/img/skinstatues/poses.png` |

## Changelog
Para pegar en la versión 0.3.0 (en inglés, como el plugin). Como es la primera publicación, va también la historia.

```markdown
## 0.3.0
- **Skins from a link**, like SkinsRestorer: `/statue build https://i.imgur.com/abc123.png 32`. Only a real skin PNG
  (64×64 or 64×32, up to 1 MB) from a public https site; local and private network addresses are refused, redirects
  included. Off by default: `network.urls: true` in config.yml, plus the `skinstatues.build.url` permission (op).
- `network.allowed_hosts` restricts the sites (`i.imgur.com`, `*.imgur.com`…); empty allows any public site.
- Arm model detected from the image; `--slim` / `--classic` force it for any skin.
- Links are cached for `network.cache_hours` and are not stored: `/statue list` and `/statue info` show
  `url:<site>#<fingerprint>`.

## 0.2.0
- 22 new poses (28 in total), with elbows and knees: running, saluting, thinking, arms_crossed, dabbing, t_pose,
  sitting_floor, lying, archer…
- Built-in poses are read from the jar, so updates bring new ones; `builtin-poses.yml` is a copy for reference.
- Your own poses the easy way in `poses.yml`: `extends` another pose and change only what is different, or flip it
  with `mirror`.
- `--at <x> <y> <z>` (with `~`) and `--facing`; build from the console with `--at` and `--world`. Far chunks are loaded
  in the background first.

## 0.1.0
- `/statue build <name> <height> [pose]`: a hollow block statue of a player's skin, from 8 to 128 blocks tall.
- Both skin layers joined (the second in relief), the cape, classic and slim arms.
- Skins from `plugins/SkinStatues/skins/` or from Mojang by account name (`network.mojang`, off by default).
- Palettes of allowed blocks (all, wool, concrete, terracotta) and colors matched the way the eye sees them.
- Built off the main thread, in batches per tick, with a progress bar; `undo` and `remove` restore the terrain.
```

## Paso a paso por sitio

### Modrinth (https://modrinth.com)
1. **Create a project** → tipo **Plugin**, nombre `SkinStatues`, URL `skinstatues`, visibilidad pública, resumen de
   la tabla.
2. **Description:** pegar `modrinth-hangar.md` entero (el comentario `<!-- … -->` del principio no se ve, pero se
   puede borrar).
3. **Icon:** `logo.png`. **Gallery** (opcional): las imágenes de la tabla.
4. **Tags:** categorías **decoration** y **utility** (confirmar; otras posibles: *worldgen* no aplica, *social* tampoco).
   Entorno: **server-side only** (en la versión nueva del formulario: «Server», no requerido en el cliente).
5. **Links:** Source, Issues y Wiki de la tabla. **License:** MIT.
6. **Versions → Create version:** subir `SkinStatues-0.3.0.jar`, número `0.3.0`, canal Release, loaders **Paper** (si
   Modrinth propone también Purpur/Folia al detectar el `plugin.yml`, dejar solo Paper; **no** marcar Folia ni Spigot/
   Bukkit), Minecraft **1.21.4** y **26.1.2**, sin dependencias, changelog de arriba (solo la sección 0.3.0 o todo).
7. **Submit for review.** Queda «Under review» hasta que un moderador lo apruebe.

### Hangar (https://hangar.papermc.io)
1. **New project** → dueño: tu usuario, nombre `SkinStatues`, descripción corta (el resumen), categoría
   **Miscellaneous** (confirmar; alternativas que existen en Hangar: *World Management*, *Gameplay*, *Admin Tools*),
   licencia **MIT**, enlaces de fuente, issues y wiki.
2. **Page:** pegar `modrinth-hangar.md` en la página principal.
3. **Icon:** `logo.png` (en *Settings*).
4. **Versions → New version:** canal **Release**, versión `0.3.0`, plataforma **Paper** con `1.21.4` y `26.1.2` (Hangar
   puede mostrar las versiones agrupadas; marcar solo esas dos), subir `SkinStatues-0.3.0.jar`, sin dependencias,
   changelog de arriba.
5. Publicar. Hangar puede pasar el proyecto por revisión antes de que sea visible para todos.

### SpigotMC (https://www.spigotmc.org/resources/)
1. **Post Resource** → **Spigot** → categoría **Fun** (confirmar; alternativa: *Misc*; también existen *Tools and
   Utilities* y *World Management*). Tipo: subir archivo (no premium).
2. **Title:** `SkinStatues`. **Tag line:** la de la tabla (SpigotMC limita su largo; si la corta, usar el resumen de
   Modrinth recortado). **Version string:** `0.3.0`.
3. **Description:** pegar `spigotmc.bbcode` en el editor **en modo BBCode** (el botón `[ ]`), no en el visual. Las 7
   `[IMG]` van en **una sola línea** a propósito (020 F-05: un salto entre ellas deja franjas blancas). La descripción
   dice que el plugin es solo para Paper; SpigotMC lo permite, como con OmniEvents.
4. **Native Minecraft version / Tested Minecraft versions:** marcar **1.21** (cubre la 1.21.4). Si la lista ya tiene
   **26.1**, marcarla también; si no, la descripción ya dice 26.1.2 (confirmar lo que ofrece el formulario).
5. **Source code:** el enlace de GitHub. **Support / Donation:** opcional.
6. **File:** `SkinStatues-0.3.0.jar`. **Icon:** `logo.png` (SpigotMC lo recorta a cuadrado y lo achica; ya es cuadrado).
7. Publicar. El primer «update» se usa luego para el changelog de las versiones siguientes.

## Qué tiene que confirmar el dueño
- Las categorías: Modrinth *decoration* + *utility*; Hangar *Miscellaneous*; SpigotMC *Fun*. Son las que existen según lo
  conocido de cada sitio, pero los formularios cambian: mirar la lista al crear el proyecto.
- Que el slug `skinstatues` esté libre en Modrinth y Hangar.
- El largo máximo del tag line en SpigotMC y las versiones que ofrece su lista (si ya está 26.1).
- La licencia MIT (la del repositorio); el plugin no tiene un `LICENSE` propio.
- Que el texto final del póster coincida con la transcripción (si otro cambio del póster cambia una frase, actualizar
  la sección de esa imagen en los dos archivos).
- El riesgo de las skins de youtubers (arriba).

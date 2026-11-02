# Fábrica de plugins de Minecraft

Plugins para **Paper** (versiones verificadas por plugin, abajo) construidos con *Spec-Driven Development*: cada funcionalidad nace como una spec
verificable (historias y criterios de aceptación), pasa por un plan con análisis de amenazas, se implementa tarea por
tarea y no se da por terminada sin una fase de verificación en un servidor real.

## Plugins
| Plugin | Qué hace | Versión | Paper verificado | Java |
|---|---|---|---|---|
| [OmniEvents](plugins/omnievents/README.md) ([Hangar](https://hangar.papermc.io/leonardos4enz/OmniEvents)) | Eventos condicionales: «cuando pase X, si se cumple Y, haz Z». Versión propia y ampliada de ConditionalEvents ([documentación en inglés](https://leonardos4enz.gitbook.io/leonardos4enz-docs/) ([fuente](docs/site/pages/omnievents/README.md)), [galería](docs/omnievents/galeria.md), [referencia](docs/omnievents/referencia.md), autocompletado en VS Code, pruebas de reglas con `/omni test`, paquetes y plantillas, costos todo o nada con Vault, regiones de WorldGuard, NPC de Citizens, menús de inventario con formularios de Bedrock, el chat que recibe el jugador con ProtocolLib, un [minijuego de parkour de ejemplo](plugins/omnievents/README.md#cómo-hacer-un-minijuego) hecho solo con reglas, un [paquete de ejemplos en inglés](plugins/omnievents/README.md#ejemplos-en-inglés-examples-en) para principiantes (con ejemplos de WorldGuard, Vault, Citizens, PlaceholderAPI, Floodgate y ProtocolLib) y paridad comprobada con ConditionalEvents, que `/omni import` traduce casi entera) | 1.12.0 | 1.21.4 y 26.1.2 ([detalle](plugins/omnievents/README.md#compatibilidad)) | 21+ |
| [SkinStatues](plugins/skinstatues/README.md) | Estatuas de bloques con la skin de un jugador: `/statue build <name> <height> [pose]`, a la altura pedida y donde se diga (`--at x y z`, `--facing`, también desde la consola), con la segunda capa en relieve, 28 poses con codos y rodillas y poses propias (`extends`, `mirror`), la capa, paletas de bloques permitidos y `undo` que devuelve el terreno. Skin de un PNG propio, de Mojang o de un link `https` como SkinsRestorer (solo una skin válida de un sitio público; la red, apagada por defecto). En inglés ([ADR 0005](docs/adr/0005-plugins-en-ingles.md)) | 0.3.0 | 1.21.4 y 26.1.2 ([detalle](plugins/skinstatues/README.md#compatibility)) | 21+ |
| [Worldcraft](plugins/worldcraft/README.md) | Editor de mundo mejorado frente a WorldEdit y FAWE ([investigación](docs/investigacion/editores-de-mundo.md)): obras por tandas con un tope de milisegundos por tick para todo el plugin (sin lag), vista previa con bloques falsos que solo ve quien edita y `confirm`, contorno de la selección con partículas sin mod, deshacer en disco que sobrevive a reinicios y no pisa lo que otro construyó después, patrones y máscaras legibles con «did you mean», límites por grupo de permisos y bloques prohibidos de fábrica, copiar/cortar/pegar/girar/voltear con cofres y carteles. **Terreno fácil** ([investigación](docs/investigacion/terreno-para-constructores.md)): `/wc mountain` y `/wc island` (islas flotantes) de una palabra con vista previa y botones `[Build] [Another] [Cancel]`, pincel de terreno (subir, bajar, suavizar, aplanar, erosionar, pintar) que no toca árboles ni construcciones, estilos con capas por altura y pendiente en `terrain.yml` y todas las opciones `clave=valor` con semilla para el experto. **Esquemas `.schem` de WorldEdit**: `/wc schem save|load|list|delete` lee Sponge v1–v3 (WorldEdit 6 y 7, FAWE) y escribe v3, con la carpeta de WorldEdit leída donde está, cofres y carteles con su contenido y comprobado en los dos sentidos con WorldEdit 7.3.19. **Lo que más se usa de WorldEdit** ([comparación fila por fila](docs/investigacion/paridad-worldedit-fawe.md)): formas (`sphere`, `cyl`, `pyramid`, `cone`, huecas), `stack`, `move`, `overlay`, `naturalize`, `smooth`, `hollow`, `center`, `line`, utilidades alrededor (`drain`, `fixwater`, `fill`, `removenear`, `snow`, `thaw`, `green`…) y navegación (`ascend`, `descend`, `thru`, `jumpto`, `up`…), todo con vista previa y deshacer; **selección e información** (`hpos`, `chunk`, `outset`, `inset`, `trim`, `count`, `distr`), máscaras que miran alrededor (`stone above grass_block`, `exposed`, `surface`, `30% chance`), `^tipo` que conserva el giro, máscara global `gmask` y `/wc schem import`; **selecciones que no son cajas** (`/wc sel poly|ellipsoid|sphere|cyl|convex|extend|magic` con los clics de WorldEdit, el contorno de cada forma con partículas y todos los comandos de región sobre la forma; la tabla de paridad, verificada contra los jars de WorldEdit 7.3.19 y FAWE 2.16.2, parte lo que falta en las specs 031–036). Solo API de Paper. En inglés |  0.6.0 | 1.21.4 ([detalle](plugins/worldcraft/README.md#compatibility)) | 21+ |

«Verificado» quiere decir que el plugin pasó la suite en el juego en esa versión; las demás están «sin verificar» en
el README de cada plugin.

## Estructura
```
CLAUDE.md                 guía para sesiones de IA (leer primero)
docs/sdd/                 constitución, metodología, verificación, roadmap, plantillas y specs
docs/adr/                 decisiones de arquitectura
docs/investigacion/       investigaciones para decidir specs (plugins exitosos; editores de mundo, terreno y paridad con WorldEdit para Worldcraft)
docs/<plugin>/galeria.md  una imagen por capacidad, generada en el juego (spec 004); imágenes en docs/img/<plugin>/
docs/site/                documentación pública en inglés para GitBook y Zensical (spec 017): pages/, SUMMARY.md, mkdocs.yml,
                          assets-src/ (fuentes HTML de los logos, el banner y las páginas de presentación, specs 020 y 024)
docs/<plugin>/presentacion/   descripciones para SpigotMC (BBCode) y Modrinth/Hangar (Markdown), specs 020 y 024
                          (SkinStatues: también publicar.md, el paso a paso para crear los tres proyectos)
docs/omnievents/          también referencia.md, omnievents.schema.json y omnievents-pack.schema.json, generados del código (specs 007 y 011)
plugins/<nombre>/         un proyecto Gradle por plugin
tools/nuevo-plugin.ps1    crea un plugin nuevo con su spec
tests-e2e/                pruebas en el juego con bots (compuerta 3a): npm --prefix tests-e2e test
                          capturas de la galería: npm --prefix tests-e2e run capturas
                          y la página de presentación: npm --prefix tests-e2e run presentacion
dist/                     jars generados (gradlew dist)
```

## Compilar
```bash
./gradlew build dist
```
Requiere JDK 21–24 para ejecutar Gradle 8.14.

## Probar
```bash
./gradlew test                                   # 1. unitarias
bash tools/compuerta-servidor.sh omnievents "omni validate" "omni selftest"   # 2. Paper real por consola
bash tools/compuerta-servidor.sh skinstatues "statue validate" "statue selftest"
bash tools/compuerta-servidor.sh worldcraft "wc validate" "wc selftest"
npm --prefix tests-e2e install                   # una vez
npm --prefix tests-e2e test                      # 3a. en el juego, con bots (≈75 s)
npm --prefix tests-e2e test -- --plugin skinstatues   # la suite de SkinStatues (≈20 s)
npm --prefix tests-e2e test -- --plugin worldcraft   # la suite de Worldcraft (con WorldEdit 7.3.19 para los .schem)
npm --prefix tests-e2e test -- --paper 26.1.2 --version-bot 26.1 --java-servidor "C:utajdk-25"   # otra versión
```
El servidor de prueba vive en `plugins/<plugin>/run/` (fuera de git) y el EULA de Minecraft lo acepta el dueño. Para las
integraciones de OmniEvents (spec 012) la suite baja una vez Vault, EssentialsX, WorldEdit, WorldGuard y Citizens de su
release oficial a `tests-e2e/.cache/plugins/` (fuera de git), verifica el sha256 fijado en `tests-e2e/lib/plugins-extra.mjs`
y los copia al servidor de prueba de cada versión de Paper. Para los menús de Bedrock (spec 013) también baja Geyser y
Floodgate, configura Geyser para escuchar solo en `127.0.0.1:19199` y aceptar un cliente sin cuenta de Xbox, y apaga las
métricas de bStats del servidor de prueba; un jugador de Bedrock (`tests-e2e/lib/bedrock.mjs`, con `bedrock-protocol`)
recibe y contesta los formularios. Geyser baja el jar del cliente de Minecraft de Mojang la primera vez (para sus
traducciones). La primera corrida con Geyser genera su configuración: si una corrida avisa de eso, la siguiente ya la usa.
Para `player_receive_chat` (spec 016, [ADR 0004](docs/adr/0004-protocollib-opcional.md)) baja ProtocolLib: el release
5.4.0 de GitHub para Paper 1.21.4 y, como no hay release para 26.1, la build de desarrollo #765 del Jenkins oficial de
dmulloy2 para 26.1.2 (también con su sha256 fijado).
Para las skins por link de SkinStatues (spec 023) la suite no usa internet: arranca un servidor HTTPS en `127.0.0.1` con
un certificado hecho en cada corrida por el `keytool` del JDK del servidor (en `run/e2e-https/`, que se borra al
terminar) y le pasa a Paper, por la línea de comandos de Java, el almacén de confianza con ese certificado y
`-Dskinstatues.test.localLinks=true` (deja al plugin pedir a una dirección local; nunca va en `config.yml`). Una suite
lo pide con `antesDeArrancar` (devuelve los argumentos de Java) y `alTerminar` (`tests-e2e/lib/https-local.mjs`).
Detalle de las compuertas: [verificacion.md](docs/sdd/verificacion.md).


## Documentación pública en inglés (`docs/site/`)
Wiki en inglés para dueños de servidores, en Markdown simple (spec 017), **publicada en https://leonardos4enz.gitbook.io/leonardos4enz-docs/** (GitBook, sincronizada con la rama `docs`). Sirve igual para **GitBook** y para un sitio
estático con **Zensical** (o MkDocs Material): `pages/SUMMARY.md` es el índice de GitBook y el `nav:` de `mkdocs.yml`
el del sitio estático. `DocsSiteTest` (en `./gradlew :omnievents:test`) exige que los dos índices sean iguales, que cada
enlace exista, que cada regla de ejemplo cargue y pase sus `tests:`, y que estén todos los disparadores, acciones y
funciones del registro.

Previsualizar en local (Python 3; el sitio generado, `docs/site/site/`, no va al repositorio):
```bash
pip install zensical
zensical serve -f docs/site/mkdocs.yml
```
Publicar (lo hace el dueño; nada se publica solo):
- **GitBook, gratis** en `<nombre>.gitbook.io` (así está publicada hoy: espacio «Documentation», rama `docs`, carpeta `docs/site`; para actualizar el sitio, pasar `main` a `docs`): crear un espacio, *Integrations → GitHub Sync*, repositorio
  `leonardos4enz/fabrica-de-plugins`, rama `main`, *project directory* `docs/site`. GitBook lee `docs/site/.gitbook.yaml`.
  La sincronización va en las dos direcciones: lo que se edite en GitBook vuelve como commit a esa rama, así que conviene
  sincronizar con una rama propia (p. ej. `docs`) y pasarla a `main` revisada. El plan gratuito no incluye dominio propio.
- **GitHub Pages, gratis y con dominio propio:** un workflow que corra `zensical build -f docs/site/mkdocs.yml` y publique
  `docs/site/site/` (Settings → Pages → GitHub Actions).

## Galería con imágenes
```bash
npm --prefix tests-e2e run capturas                                   # todas las escenas, con compilación
npm --prefix tests-e2e run capturas -- --sin-build --solo "accion-"    # algunas
```
Un bot entra al Paper de prueba, vive cada capacidad del plugin y se guarda un render de lo que recibió (chat, barra de
acción, títulos, HUD, inventarios y una vista 3D del mundo) en `docs/img/<plugin>/`, con la galería en
`docs/<plugin>/galeria.md`. No son capturas del cliente oficial: el render usa la fuente, los sprites y el idioma del
**Minecraft Java 1.21.4 instalado** en la PC (se extraen a `tests-e2e/.cache/`, nunca al repositorio) y necesita Chrome
o Edge. Cada imagen se compara por píxeles con la anterior y solo se reescribe si cambió; las capacidades sin escena se
listan como aviso. Se usa para capturas puntuales (`--solo`), no para regenerar todo en cada spec. Diseño: [spec 004](docs/sdd/specs/004-fabrica-capturas-en-juego/spec.md).

## Página de presentación (SpigotMC, Modrinth, Hangar)
La página de OmniEvents es un póster en pixel art (spec 020): siete franjas de 1280 px que, apiladas sin espacio, se leen
como un corte del mundo (cielo, pasto, tierra, piedra, pizarra, roca madre), con dos GIF de ejemplos grabados en el juego.
Todo sale de fuentes versionadas:
```bash
npm --prefix tests-e2e run capturas -- --escenas presentacion --sin-build --solo "en-npc"   # escenas en inglés (cuadros)
npm --prefix tests-e2e run presentacion                                                    # logo, banner, franjas y GIF
```
- **Escenas** (`tests-e2e/omnievents/presentacion.mjs`): el paquete `examples-en` tal cual, en un Paper con WorldEdit,
  WorldGuard y Citizens (los jars aprobados de `plugins-extra.mjs`). Cada escena guarda sus cuadros en
  `docs/img/omnievents/presentacion/` (`en-npc-1.png`, `en-npc-2.png`…); no toca la galería.
- **Piezas** (`docs/site/assets-src/presentacion.html`): Chrome sin interfaz saca cada una y `ffmpeg` arma los GIF. Salen a
  `docs/site/pages/assets/presentacion/`, y `logo.png` y `banner.png` a `docs/site/pages/assets/`. Sin texturas de
  Mojang ni logos de otros plugins: el ruido de píxeles, el bloque y los minerales se dibujan en el HTML; la letra es
  Press Start 2P (OFL).
- **Descripciones** (`docs/omnievents/presentacion/`): `spigotmc.bbcode` y `modrinth-hangar.md` enlazan las imágenes por
  su URL de GitHub (`raw.githubusercontent.com/.../main/...`) y traen la transcripción en un Spoiler o un `<details>`.
  `PresentacionTest` exige que las dos enlacen las mismas imágenes, que existan y que la transcripción cite lo que dicen
  los ejemplos. Pegarlas en cada página lo hace el dueño.

**SkinStatues** (spec 024) usa el mismo póster con sus propias escenas: estatuas de 100 bloques y una fila de poses con
skins de youtubers, en un mundo de terreno normal con semilla fija.
```bash
node tests-e2e/skinstatues/skins-youtubers.mjs        # baja las skins de Mojang a tests-e2e/.cache/skins/ (nunca al repo)
npm --prefix tests-e2e run capturas -- --plugin skinstatues --escenas presentacion --sin-build --solo "^giant$"
npm --prefix tests-e2e run presentacion -- --plugin skinstatues   # logo, franjas y GIF en docs/img/skinstatues/presentacion/
```
- El servidor de esas escenas es `plugins/skinstatues/run-capturas/` (puerto 25597): se regenera el mundo en cada corrida
  y nunca toca `run/`. Escenas: `giant`, `row`, `build` (10 cuadros y `build-close`), `name-link`, `palettes`.
- `docs/skinstatues/presentacion/publicar.md` trae el resumen, las categorías, el changelog y el paso a paso por sitio.

## Licencia
[MIT](LICENSE). OmniEvents se publica en [Hangar](https://hangar.papermc.io/leonardos4enz/OmniEvents).

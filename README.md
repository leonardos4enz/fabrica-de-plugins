# Fábrica de plugins de Minecraft

Plugins para **Paper** (versiones verificadas por plugin, abajo) construidos con *Spec-Driven Development*: cada funcionalidad nace como una spec
verificable (historias y criterios de aceptación), pasa por un plan con análisis de amenazas, se implementa tarea por
tarea y no se da por terminada sin una fase de verificación en un servidor real.

## Plugins
| Plugin | Qué hace | Versión | Paper verificado | Java |
|---|---|---|---|---|
| [OmniEvents](plugins/omnievents/README.md) | Eventos condicionales: «cuando pase X, si se cumple Y, haz Z». Versión propia y ampliada de ConditionalEvents ([galería](docs/omnievents/galeria.md), [referencia](docs/omnievents/referencia.md), autocompletado en VS Code, pruebas de reglas con `/omni test`, paquetes y plantillas, costos todo o nada con Vault, regiones de WorldGuard, NPC de Citizens y menús de inventario con formularios de Bedrock) | 1.8.1 | 1.21.4 y 26.1.2 ([detalle](plugins/omnievents/README.md#compatibilidad)) | 21+ |

«Verificado» quiere decir que el plugin pasó la suite en el juego en esa versión; las demás están «sin verificar» en
el README de cada plugin.

## Estructura
```
CLAUDE.md                 guía para sesiones de IA (leer primero)
docs/sdd/                 constitución, metodología, verificación, roadmap, plantillas y specs
docs/adr/                 decisiones de arquitectura
docs/investigacion/       investigaciones para decidir specs (p. ej. plugins exitosos y propuestas)
docs/<plugin>/galeria.md  una imagen por capacidad, generada en el juego (spec 004); imágenes en docs/img/<plugin>/
docs/omnievents/          también referencia.md, omnievents.schema.json y omnievents-pack.schema.json, generados del código (specs 007 y 011)
plugins/<nombre>/         un proyecto Gradle por plugin
tools/nuevo-plugin.ps1    crea un plugin nuevo con su spec
tests-e2e/                pruebas en el juego con bots (compuerta 3a): npm --prefix tests-e2e test
                          y capturas de la galería: npm --prefix tests-e2e run capturas
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
npm --prefix tests-e2e install                   # una vez
npm --prefix tests-e2e test                      # 3a. en el juego, con bots (≈75 s)
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
Detalle de las compuertas: [verificacion.md](docs/sdd/verificacion.md).

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

# Fábrica de plugins de Minecraft

Plugins para **Paper** (versiones verificadas por plugin, abajo) construidos con *Spec-Driven Development*: cada funcionalidad nace como una spec
verificable (historias y criterios de aceptación), pasa por un plan con análisis de amenazas, se implementa tarea por
tarea y no se da por terminada sin una fase de verificación en un servidor real.

## Plugins
| Plugin | Qué hace | Versión | Paper verificado | Java |
|---|---|---|---|---|
| [OmniEvents](plugins/omnievents/README.md) | Eventos condicionales: «cuando pase X, si se cumple Y, haz Z». Versión propia y ampliada de ConditionalEvents | 1.0.0 | 1.21.4 y 26.1.2 ([detalle](plugins/omnievents/README.md#compatibilidad)) | 21+ |

«Verificado» quiere decir que el plugin pasó la suite en el juego en esa versión; las demás están «sin verificar» en
el README de cada plugin.

## Estructura
```
CLAUDE.md                 guía para sesiones de IA (leer primero)
docs/sdd/                 constitución, metodología, verificación, roadmap, plantillas y specs
docs/adr/                 decisiones de arquitectura
plugins/<nombre>/         un proyecto Gradle por plugin
tools/nuevo-plugin.ps1    crea un plugin nuevo con su spec
tests-e2e/                pruebas en el juego con bots (compuerta 3a): npm --prefix tests-e2e test
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
El servidor de prueba vive en `plugins/<plugin>/run/` (fuera de git) y el EULA de Minecraft lo acepta el dueño.
Detalle de las compuertas: [verificacion.md](docs/sdd/verificacion.md).

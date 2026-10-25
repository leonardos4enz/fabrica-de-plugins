# Fábrica de plugins de Minecraft

Plugins para **Paper 1.21.4+** construidos con *Spec-Driven Development*: cada funcionalidad nace como una spec
verificable (historias y criterios de aceptación), pasa por un plan con análisis de amenazas, se implementa tarea por
tarea y no se da por terminada sin una fase de verificación en un servidor real.

## Plugins
| Plugin | Qué hace | Estado |
|---|---|---|
| [OmniEvents](plugins/omnievents/README.md) | Eventos condicionales: «cuando pase X, si se cumple Y, haz Z». Versión propia y ampliada de ConditionalEvents | 1.0.0 |

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

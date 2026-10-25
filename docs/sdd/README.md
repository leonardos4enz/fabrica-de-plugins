# Metodología: Spec-Driven Development (SDD) asistido por IA

La fábrica usa **SDD** (al estilo de GitHub Spec Kit) combinado con **pruebas primero en código crítico** y **ADRs**,
igual que el proyecto ERP AI. La IA trabaja contra un documento verificable y no contra una conversación que se
olvida. Cada plugin de la fábrica nace y crece por specs.

## Flujo por cada funcionalidad

```
constitution.md  (principios fijos de la fábrica, se leen siempre)
      │
 1. SPECIFY   specs/NNN-plugin-nombre/spec.md    QUÉ y POR QUÉ. Historias, criterios de aceptación.
      │                                           Sin tecnología.
 2. CLARIFY   sección "Decisiones (CLARIFY)" del spec, resueltas por el dueño.
      │
 3. PLAN      specs/NNN-plugin-nombre/plan.md    CÓMO. Arquitectura, formato de configuración (el contrato),
      │                                           modelo de datos, hilos, análisis de amenazas (STRIDE).
 4. TASKS     specs/NNN-plugin-nombre/tasks.md   Tareas pequeñas, ordenadas, cada una verificable.
      │
 5. IMPLEMENT la IA ejecuta tarea por tarea, marcando [x].
      │
 6. VERIFY    specs/NNN-plugin-nombre/verify.md  Ver verificacion.md:
                 a) unitarias  b) servidor Paper real (carga, ejemplos, comandos de consola)
                 c) en el juego: suite automática con bots (3a) y revisión exploratoria (3b)
                    → cada hallazgo se vuelve prueba
```

Las plantillas de cada documento están en [plantillas/](plantillas/). Un plugin nuevo se crea con
`tools/nuevo-plugin.ps1` (proyecto Gradle + carpeta de spec con las plantillas).

## Numeración
Las specs se numeran en una sola secuencia para toda la fábrica (`001`, `002`, …) y su nombre empieza por el plugin:
`001-omnievents-nucleo`, `002-omnievents-…`, `003-otroplugin-…`. Así el índice cuenta la historia completa.

## Una spec por sesión
Cada spec se trabaja en **su propia sesión de IA, nueva**, de principio a fin (SPECIFY → VERIFY → commit).
Al cerrar la spec:
1. `verify.md` completo y compuertas en verde.
2. Actualizar los README: el del plugin (capacidades y sección **Compatibilidad** con las versiones de Paper
   verificadas y sin verificar) y el raíz (tabla de plugins). Ver [CLAUDE.md](../../CLAUDE.md).
3. Actualizar el estado en el índice de abajo y en [roadmap.md](roadmap.md).
4. Revisión de secretos y artefactos, commit y push.
5. La siguiente spec se abre en una sesión nueva, que arranca leyendo [CLAUDE.md](../../CLAUDE.md).

## Reglas para agentes de IA
- Leer `constitution.md` y el `spec.md` vigente antes de escribir código.
- No implementar nada que no esté en `tasks.md`. Si falta algo, proponer cambio al spec.
- Si el spec es ambiguo, preguntar (fase CLARIFY) en vez de suponer.
- Cada tarea termina con `gradlew build` verde y pruebas pasando.
- Cada criterio de aceptación del spec tiene al menos una prueba (unitaria o de servidor) con su ID en el nombre
  (`H1_CA3_…`) o en la tabla de cobertura de `verify.md`.
- La revisión exploratoria se hace **usando el plugin en un servidor real**, no leyendo código.

## Índice de specs
| # | Spec | Plugin | Estado |
|---|------|--------|--------|
| 001 | [OmniEvents: núcleo (eventos condicionales)](specs/001-omnievents-nucleo/spec.md) | OmniEvents | Verificado · compuertas 1, 2 y 3a aprobadas |
| 002 | [Fábrica: pruebas automáticas en el juego](specs/002-fabrica-pruebas-en-juego/spec.md) | fábrica (OmniEvents) | Verificado · 21/21 en Paper 1.21.4 y 26.1.2 |
| 004 | [Fábrica: documentación con imágenes generadas en el juego](specs/004-fabrica-capturas-en-juego/spec.md) | fábrica (OmniEvents) | Verificado · galería de 147 escenas, dos regeneraciones sin diferencias inesperadas |

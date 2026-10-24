# ADR-0001 — Fábrica de plugins como un solo repositorio multiproyecto Gradle

**Estado:** Aceptado · **Fecha:** 2026-10-24

## Contexto
Vamos a construir varios plugins de Minecraft con la misma metodología (SDD, heredada de ERP AI). Opciones:
(a) un repositorio por plugin, (b) un repositorio con un proyecto Gradle por plugin y convenciones comunes.

## Decisión
Un repositorio (`Minecraft Plugins/`) con `plugins/<nombre>/` como subproyectos Gradle descubiertos automáticamente
por `settings.gradle.kts`. La raíz define las convenciones (Java 21, Paper API, JUnit 5, UTF-8, tarea `dist`).
La documentación SDD (constitución, verificación, specs, ADRs) es **única para toda la fábrica**.

## Consecuencias
- (+) Un plugin nuevo es una carpeta: `tools/nuevo-plugin.ps1` la crea con su spec.
- (+) Una mejora de convenciones o de la constitución aplica a todos.
- (+) Código común (p. ej. el motor de expresiones) se podrá extraer a `libs/` sin cambiar de repositorio.
- (−) Un build roto en un plugin rompe `gradlew build` de la raíz; se compila uno con `gradlew :<plugin>:build`.

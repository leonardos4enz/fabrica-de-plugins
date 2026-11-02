# ADR 0008 — La API de Worldcraft para otros plugins

**Fecha:** 2026-11-02 · **Estado:** Aceptada · **Spec:** [037](../sdd/specs/037-worldcraft-api/spec.md)

## Contexto
Otros plugins (minijuegos, islas, protección) quieren pedirle obras a Worldcraft y enterarse de las suyas. WorldEdit
lo hace con su API (GPL) y FAWE con la misma; WorldGuard, PlotSquared y CoreProtect dependen de ella. Worldcraft es MIT
y la 029 dejó la API para el final. La investigación (§3) proponía además un puente de selección con WorldEdit para
`/rg define` y un registro en CoreProtect; el dueño los descartó: «no quiero que la gente tenga WorldEdit también»,
«nada de CoreProtect».

## Opciones
1. **Un paquete propio y estable** (`dev.leo.worldcraft.api` y `api.event`) que solo usa tipos de Bukkit y del JDK;
   lo demás del plugin queda interno.
2. Exponer las clases internas (`Edits`, `Op`, `Region`) como están.
3. Imitar la API de WorldEdit (mismos nombres) para que los plugins que la usan funcionen con Worldcraft.

## Decisión
La 1. La 2 ata cada cambio interno a otros plugins (el motor cambió en casi cada spec de la 025 a la 036). La 3 copia
el diseño de una API GPL, no la pidió el dueño y no alcanza (WorldGuard usa además sus clases de regiones). Se registra
en el `ServicesManager` de Bukkit y también se da por `WorldcraftApi.get()`.

## Consecuencias
- `WorldcraftApi.API_VERSION` dice la versión del contrato: se suma un método sin cambiar la versión; se cambia o quita
  uno solo con una versión nueva y anotándolo en el README. Una prueba (`ApiTest`) revisa que el paquete no use tipos
  internos.
- Toda obra de la API pasa por la misma puerta que un comando (`Edits`): límites, tope por tick, historial y deshacer.
  No hay atajos.
- WorldGuard y PlotSquared siguen necesitando WorldEdit (dependen de su API, no de la nuestra): el README lo dice.
- Para publicar la API en un repositorio Maven hará falta un artefacto aparte; hoy un desarrollador compila contra el
  jar de Worldcraft (`compileOnly(files(...))`).

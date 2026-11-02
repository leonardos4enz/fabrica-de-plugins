# ADR 0007 — La red segura de SkinStatues se copia a Worldcraft

**Fecha:** 2026-11-02 · **Estado:** Aceptada · **Spec:** [035](../sdd/specs/035-worldcraft-creacion-fawe/spec.md)

## Contexto
`/wc image <link>` (spec 035) baja una imagen de internet. SkinStatues ya resolvió eso en la
[spec 023](../sdd/specs/023-skinstatues-skin-por-link/plan.md): link validado y normalizado a https, solo direcciones
públicas comprobadas en cada redirección, puerto 443, `allowed_hosts`, topes de bytes y de tiempo. Son tres clases
(`SkinLink`, `LinkPolicy`, `LinkClient`, unas 290 líneas) con sus pruebas. Cada plugin de la fábrica es un jar aparte
que el dueño de un servidor instala solo ([ADR 0001](0001-fabrica-multiproyecto.md)).

## Opciones
1. **Copiar las tres clases** a Worldcraft (`dev.leo.worldcraft.net`), con sus pruebas.
2. Un módulo común (`libs/red`) que los dos plugins incluyen con *shading*.
3. Que Worldcraft dependa de SkinStatues.

## Decisión
La 1. La 3 obliga a instalar SkinStatues para usar Worldcraft. La 2 es lo correcto si aparece un tercer plugin con red,
pero hoy agrega un módulo, reubicación de paquetes y un cambio en SkinStatues (que no está en esta spec) por tres clases
que no cambian desde la 023. Igual que la [ADR 0006](0006-formulas-de-worldcraft.md) (fórmulas propias).

## Consecuencias
- Un arreglo de seguridad en la red hay que hacerlo **en los dos plugins**: cada copia dice de dónde viene en su
  comentario y esta ADR lo anota. Si un tercer plugin necesita red, se pasa a la opción 2.
- Las pruebas de SSRF se copian también (`LinkTest` de Worldcraft), así una diferencia se ve en la compuerta 1.

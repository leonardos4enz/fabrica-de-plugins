# ADR-0005 — Los plugins nuevos de la fábrica, en inglés

**Estado:** Aceptado · **Fecha:** 2026-11-01 · **Spec:** [021](../sdd/specs/021-skinstatues-nucleo/spec.md)

## Contexto
La constitución V.1 pedía «mensajes en español claro por defecto». OmniEvents nació así (y después sumó documentación
y ejemplos en inglés: specs 017 y 019) porque su público principal está en SpigotMC, Modrinth y Hangar, que leen en
inglés. Al abrir el segundo plugin (SkinStatues, spec 021) el dueño decidió: «quiero que el plugin esté en inglés, ya
todos los plugins a partir de ahora serán en inglés».

## Decisión
- Desde la spec 021, **todo lo que ve el dueño de un servidor o un jugador** en un plugin nuevo va en inglés: mensajes
  (`messages.yml`), archivos de configuración y sus comentarios, nombres de comandos, opciones, permisos y ejemplos,
  y el README del plugin.
- Los documentos internos de la fábrica (constitución, specs, planes, `verify.md`, ADR, `CLAUDE.md`) siguen en
  español, igual que los comentarios del código.
- OmniEvents no cambia: sigue en español por defecto, con lo que ya tiene en inglés.
- Constitución V.1 queda: «Mensajes en **inglés claro** por defecto (en español los de OmniEvents) y traducibles».

## Consecuencias
- (+) Un solo idioma para el público de las plataformas donde se publica.
- (+) `messages.yml` sigue siendo traducible: un servidor hispano lo traduce sin tocar el código.
- (−) Dos idiomas en el repositorio: el dueño revisa los textos en inglés en el README y en la revisión 3b.

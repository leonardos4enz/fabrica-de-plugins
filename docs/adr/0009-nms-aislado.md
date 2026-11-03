# ADR 0009 — NMS aislado y por reflexión, solo para lo que Paper no da (Worldcraft)

**Fecha:** 2026-11-03 · **Estado:** Aceptada · **Spec:** [040](../sdd/specs/040-worldcraft-lo-que-falta-de-worldedit/spec.md)
· **Cambia:** la constitución VI.2 («sin NMS»)

## Contexto
`//feature` de WorldEdit pone uno de los ~200 *features* del juego (rocas, geodos, hongos gigantes, parches de flores).
Paper 1.21.4 no tiene API para eso (solo `generateTree`, que la 033 ya usa) y WorldEdit lo hace con un adaptador NMS
por versión. Lo mismo `//update` (la forma de vallas, muros y paneles según sus vecinos). La constitución prohíbe NMS
para que un Minecraft nuevo no rompa el plugin. El dueño, sabiendo eso: «mejor un NMS, total no todo se rompería, solo
lo no compatible».

## Opciones
1. Dejarlos fuera (como en la 033).
2. Correr el `/place feature` del juego en un mundo temporal y copiar lo que cambió (sin NMS, caro y lento).
3. NMS por reflexión, **aislado en una clase** (`paper/Nms`), que se prueba al arrancar y, si falla, apaga solo esos
   comandos.

## Decisión
La 3, con estas reglas (que son la excepción a VI.2):
- Solo por reflexión, por nombre (Paper 1.21.4+ corre con los nombres de Mojang; el jar declara
  `paperweight-mappings-namespace: mojang` para que Paper no lo reescriba). Nada del plugin compila contra el servidor.
- Toda la reflexión vive en `paper/Nms`. Si algo no está (versión nueva), `Nms.available()` es falso con el motivo; los
  comandos que lo usan dicen «not available on this server version» y lo demás de Worldcraft no cambia. El arranque
  nunca falla por esto.
- **Nunca escribe en el mundo.** El *feature* corre contra un `WorldGenLevel` de mentira (un `Proxy` de Java) que lee
  del mundo y guarda lo que el juego quiere poner; eso pasa por el motor (vista previa, límites, deshacer). Lo que el
  juego intenta hacer directo en un chunk se corta y el *feature* se rechaza.
- `/wc selftest` y `/wc version` dicen si el puente está.

## Consecuencias
- Un Minecraft nuevo puede apagar `feature`, `brush feature` y `update` hasta que se actualice `Nms`: es lo único
  dependiente de la versión.
- Otros plugins de la fábrica siguen sin NMS; esta excepción es solo para Worldcraft y solo para lo que Paper no da.

## Ampliación (spec 041, 2026-11-03)
El dueño pidió `//fixlighting` por este puente (y que `//removelighting`, `//setblocklight` y `//setskylight` sigan fuera).
Recalcular la luz no pasa por el motor (la luz no es un bloque ni tiene deshacer), así que la regla «nunca escribe en el
mundo» queda así: **el puente nunca pone, quita ni cambia un bloque**; lo único que puede pedir es que el juego vuelva a
calcular la luz de unos chunks cargados, con el mismo método que `/paper fixlight`
(`ThreadedLevelLightEngine.starlight$serverRelightChunks` en 1.21.4, Moonrise/Starlight; `relightChunks` si una versión lo
llama así). También lee el NBT de un bloque (`BlockEntity.saveWithFullMetadata`) para `//nbtinfo`. Las dos cosas se apagan
aparte (`Nms.lightAvailable()`): si faltan, los *features* y `//update` siguen. El selftest comprueba que recalcular la luz
deja la zona igual bloque por bloque.

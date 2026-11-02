# Spec 038 — Worldcraft: página de presentación y publicación

**Estado:** Implementado y verificado · pedido del dueño (2026-11-02, CLARIFY) · **Plugin:** Worldcraft 0.13.0 (sin
cambios de código) y la fábrica (fuente HTML del póster, escenas de capturas) · **Depende de:** 020, 024, 025–037 ·
**Origen:** al preguntarle qué spec sigue tras la 037, el dueño contestó: «Sube el plugin […] a spigot, modrinth y
hangar, menciona toda la cantidad de comandos y comparativa con we y el otro plugin» (WorldEdit y FAWE), y que siguiera
sin él.

## Problema
Worldcraft 0.13.0 está verificado (025–037) pero no está publicado: no tiene página en SpigotMC, Modrinth ni Hangar,
ni logo, ni imágenes. Lo que lo distingue (no traba el servidor, vista previa, deshacer en disco, terreno de una
palabra) y cuánto de WorldEdit y FAWE cubre no se ve en ningún lado. La tabla de paridad
([investigación](../../../investigacion/paridad-worldedit-fawe.md)) quedó escrita con la 0.8.0 y en español: el
número que el dueño quiere mostrar («toda la cantidad de comandos») no existe todavía con la 0.13.0.

## Historias
**H1 — El recuento de comandos, contra los jars.** Como *dueño*, quiero decir cuántos comandos tiene Worldcraft y
cuántos de los de WorldEdit y FAWE cubre, sin inflar nada.
- CA1: El inventario de WorldEdit 7.3.19 y FAWE 2.16.2 sale otra vez del **bytecode** de los jars (`@Command` con
  `javap`, como la 029): tiene que dar 171 y 244 (los de la 029).
- CA2: Cada uno de esos comandos tiene su estado frente a la 0.13.0: lo tiene (igual o mejor), en parte, falta, no se
  hará (con motivo) o no hace falta (Worldcraft no tiene qué ajustar). Se cuenta solo «lo tiene».
- CA3: Los comandos de Worldcraft se cuentan de su código (`/wc help`): los de primer nivel, y contando pinceles,
  herramientas y subcomandos como WorldEdit cuenta los suyos.
- CA4: Una página en inglés en el repositorio con todos los comandos de Worldcraft y la tabla completa de WorldEdit
  y FAWE, enlazada desde las descripciones.
- CA5: Una prueba falla si el número de comandos de las descripciones deja de ser el del código.

**H2 — Logo y póster.** Como *dueño*, quiero la misma familia visual que OmniEvents (020) y SkinStatues (024).
- CA1: Logo de pixel art propio (512 px, legible a 64 px), de una fuente HTML versionada.
- CA2: Franjas de 1280 px que apiladas se leen como un corte del mundo, con una franja de **comparación** con
  WorldEdit y FAWE y una de **comandos** (las cantidades de H1).
- CA3: Sin texturas, logos ni marcas de Mojang, WorldEdit ni FAWE (se los nombra en texto, como comparación).

**H3 — Pocas escenas del juego (lección 004).** Como *dueño de un servidor que mira plugins*, quiero ver qué hace.
- CA1: Tres escenas como mucho, de lo más visible: terreno (montaña e isla flotante), formas y una imagen hecha con bloques (`/wc image`; la vista previa no se distingue en una foto: va en texto).
- CA2: Servidor de capturas propio (`plugins/worldcraft/run-capturas/`, ignorado por git) con mundo de terreno
  normal; el `run/` de la suite no se toca.

**H4 — Publicado en los tres sitios.** Como *dueño*, quiero Worldcraft subido a SpigotMC, Modrinth y Hangar.
- CA1: BBCode para SpigotMC y Markdown para Modrinth y Hangar, con las imágenes por `raw.githubusercontent.com`, la
  transcripción plegada y la línea «not affiliated» con WorldEdit, FAWE y Mojang visible.
- CA2: `publicar.md` con resumen, categorías, versiones, changelog y el paso a paso, para las próximas versiones.
- CA3: Solo se marca la versión de Paper verificada (1.21.4); el resto va como «sin verificar».
- CA4: La IA sube con la sesión del dueño en el navegador, sin escribir credenciales; lo que no se pueda (sin
  sesión, revisión del sitio) queda dicho en `verify.md`.

## Fuera de alcance
- Cambios en el plugin (sigue 0.13.0) y verificar Paper 26.1.2 (queda en el roadmap).
- Una imagen por capacidad (lección 004) y un GIF (la obra por tandas no se aprecia en un GIF corto).
- Traducir la tabla de paridad completa al inglés con todo su detalle: la página de comandos lleva el estado y el
  equivalente, el detalle sigue en la investigación.

## Decisiones (CLARIFY, 2026-11-02)
| # | Pregunta | Decisión |
|---|---|---|
| 1 | ¿Qué spec sigue? | El dueño: publicar Worldcraft en SpigotMC, Modrinth y Hangar con la cantidad de comandos y la comparación con WorldEdit y FAWE |
| 2 | ¿Quién sube? | El dueño lo pidió («Sube el plugin»): la IA, con la sesión del dueño (como al final de la 024) |
| 3 | ¿Estilo y escenas? | El dueño se fue y pidió seguir sin él: la IA elige el póster por capas de la 020/024 y tres escenas (lección 004) |
| 4 | ¿Qué se cuenta como «cubre»? | Solo lo que Worldcraft tiene igual o mejor; «en parte» se dice aparte (para no inflar el número) |

## Preguntas abiertas
- (ninguna)

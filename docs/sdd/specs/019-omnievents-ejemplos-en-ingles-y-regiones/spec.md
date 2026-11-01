# Spec 019 — OmniEvents: paquete de ejemplos en inglés y regiones de WorldGuard

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 008, 010, 011, 012 ·
**Origen:** promesa en el hilo de OmniEvents en SpigotMC (2026-10-31). El usuario MacREpiC pidió más ejemplos para
principiantes (recompensas de bienvenida, eventos de regiones) y se le respondió que la siguiente actualización traería
un paquete de ejemplos en inglés y ejemplos de regiones de WorldGuard. La sesión que abrió esta lo encargó así.

## Problema
Quien descarga OmniEvents de SpigotMC, Hangar o Modrinth lee inglés, pero los ejemplos que el jar deja en
`rules/ejemplos/` están en español. Un principiante no sabe por dónde empezar con lo más común (dar la bienvenida, una
recompensa diaria, una zona segura en el spawn, avisos de chat) ni cómo se usan las regiones de WorldGuard, y no
quiere romper su servidor probando.

## Historias
**H1 — Un paquete de ejemplos en inglés que no cambia nada hasta activarlo.** Como *dueño de un servidor que lee
inglés*, quiero ejemplos listos dentro del jar para copiarlos y adaptarlos.
- CA1: El paquete `examples` viene en el jar en `rules/examples-en/` (con `pack.yml`) y **apagado** (`enabled: false`):
  en un servidor nuevo se crea junto a los ejemplos en español y no hace nada hasta que el dueño lo activa
  (`enabled: true` + `/omni reload`, o `/omni pack enable examples`).
- CA2: Los valores que un dueño querría cambiar (la variable de monedas, cuántas da la recompensa diaria, el mundo y el
  radio del spawn, la región VIP y su permiso, el permiso del staff) son parámetros de `pack.yml`.
- CA3: Cada archivo y cada regla trae un comentario corto en inglés que dice qué hace y qué cambiar.
- CA4: Cada regla que se puede probar trae `tests:` (gradle las corre con los demás ejemplos; `/omni test` en el
  servidor).

**H2 — Lo más pedido por principiantes.** Como *dueño*, quiero los casos comunes resueltos.
- CA1: **Bienvenida:** mensaje de entrada y salida, anuncio y kit de inicio la primera vez, saludo con las monedas al
  volver.
- CA2: **Recompensa diaria:** automática al entrar una vez al día, y `/daily` con enfriamiento de 24 h y una racha que
  se pierde si pasan 48 h sin reclamar (variable con `expire`); recompensa por tiempo jugado.
- CA3: **Zonas sin plugins:** zona del spawn por coordenadas (aviso al entrar y salir) sin daño de caída ni PvP dentro;
  plataformas de salto.
- CA4: **Eventos comunes:** anuncios periódicos, filtro de palabras en el chat, ocultar `/plugins` a quien no es staff,
  coordenadas al morir, aviso de vida baja y anuncio del primer diamante.

**H3 — Regiones de WorldGuard.** Como *dueño con WorldGuard*, quiero ver cómo se usan las regiones.
- CA1: `region_enter` y `region_leave`: saludo y despedida de una región, una zona solo para VIP (`cancel` impide
  entrar) y un efecto mientras se está en una región (se da al entrar y se quita al salir).
- CA2: `in_region()` y `regions()`: una recompensa por estar en una región (temporizador) y `/where` que dice en qué
  regiones estás.
- CA3: **Sin WorldGuard** el paquete carga igual: sin errores, las reglas de región no corren y `in_region()` da falso;
  con el paquete apagado tampoco hay avisos en la consola.

**H4 — Probado y documentado.**
- CA1: Un escenario de la suite activa el paquete con WorldGuard y prueba en el juego la bienvenida, `/daily`, la zona
  del spawn y una región (entrar, VIP bloqueada, `/where`).
- CA2: La página de ejemplos de la documentación en inglés (`docs/site/pages/omnievents/examples.md`) enlaza el paquete
  y explica cómo activarlo; suma un ejemplo de regiones. El README del plugin lo cuenta.

## Fuera de alcance
- Capacidades nuevas en el plugin. Un bug que aparezca se corrige como bug, con su prueba (F-01, F-02).
- Traducir los ejemplos en español ni cambiarlos.
- Copiar los ejemplos nuevos a un servidor que ya tiene `rules/` (el plugin solo los crea la primera vez): se documenta
  cómo traer el paquete a mano; un comando para instalarlo sería una capacidad nueva (va al roadmap).
- Banderas de WorldGuard (van con «más integraciones» del roadmap).

## Decisiones (CLARIFY)
| # | Pregunta | Decisión |
|---|---|---|
| 1 | ¿Nombre y lugar del paquete? | Carpeta `rules/examples-en/`, paquete `examples` (reglas `examples:welcome`…), apagado como el `parkour` de la 018 (encargo: «desactivado por defecto como el parkour»). |
| 2 | ¿Las regiones en un paquete aparte? | No: un archivo `regions.yml` dentro del mismo paquete. Sin WorldGuard carga igual (encargo: «que carguen sin WorldGuard sin romper nada»). |
| 3 | ¿Zonas con `in_area()` o con coordenadas? | Con `player.location.x`/`z` y `abs()`: la misma idea, y se puede probar en `tests:` (`in_area()` mira al jugador real y en `tests:` da falso). |
| 4 | ¿Versión? | 1.12.0: contenido nuevo visible para quien descarga el plugin y dos correcciones. |

## Preguntas abiertas
- (ninguna)

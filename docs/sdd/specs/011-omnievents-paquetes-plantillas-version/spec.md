# Spec 011 — OmniEvents: paquetes, plantillas y versión

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 001, 005, 007, 008, 010 ·
**Origen:** propuesta P8 de la [investigación de plugins exitosos](../../../investigacion/plugins-exitosos.md)
(§4 y §6), elegida por el dueño (2026-10-24)

## Problema
Las reglas de OmniEvents viven todas en un mismo espacio de nombres: dos archivos con una regla `diaria` chocan, y
copiar la configuración de otro servidor obliga a renombrar a mano y a revisar cada `call`. Lo que se repite (diez
recompensas iguales salvo la cantidad) se copia y pega, y un cambio hay que hacerlo diez veces. Tampoco hay forma de
decir «esto es la versión 1.2 de mi sistema de recompensas, necesita OmniEvents 1.6 y el paquete de plantillas»: al
actualizar una configuración compartida nadie sabe qué cambió ni qué hay que ajustar en los datos guardados.
BetonQuest y AuraSkills muestran que las configuraciones compartibles crean un mercado; ConditionalEvents no tiene
espacios de nombres. Los nombres hay que fijarlos antes de que existan paquetes compartidos: después cambiarlos
rompe configuraciones.

## Historias
**H1 — Paquetes con su propio espacio de nombres.** Como *administrador*, quiero agrupar reglas en un paquete para
instalar o compartir una carpeta sin que sus nombres choquen con los míos.
- CA1: Una carpeta dentro de `rules/` con un `pack.yml` es un paquete: `name` (por defecto, el nombre de la
  carpeta), `version`, `description`, `author`, `enabled`, `params`, `requires`, `migrations`. Las reglas de esa
  carpeta (y sus subcarpetas) se llaman `paquete:regla`. Sin `pack.yml` todo sigue como hoy (nombres globales).
- CA2: Dentro de un paquete, un nombre sin paquete (`call: entregar`, `cooldown('diaria')`, `uses(…)`,
  `in_state(…)`, `extends:`) busca primero en el propio paquete y luego entre las reglas globales; `otro:regla` es
  absoluto. Dos paquetes pueden tener una regla `diaria` cada uno.
- CA3: Errores de `pack.yml` con archivo y ruta («¿quisiste decir?»): clave desconocida, nombre inválido, versión que
  no es `x.y.z`, dos paquetes con el mismo nombre, un `pack.yml` dentro de otro paquete. Un paquete con errores no
  carga, el resto sí.
- CA4: Los comandos que reciben una regla (`info`, `enable`, `disable`, `call`, `run`, `check`, `test`, `cooldown`,
  `limit`) aceptan el nombre completo y lo autocompletan.

**H2 — Plantillas y herencia.** Como *administrador*, quiero escribir una regla una vez y reutilizarla con otros
valores.
- CA1: Una regla con `template: true` es una plantilla: no corre ni se valida sola (no necesita `trigger`); otras la
  usan con `extends: nombre` (con la búsqueda de H1 CA2).
- CA2: La regla hija hereda todas las claves de la plantilla y las que escribe las reemplazan enteras. Se puede
  heredar en cadena (una plantilla que extiende otra) hasta 8 niveles; un ciclo o una plantilla que no existe son
  errores con la ruta y «¿quisiste decir?». `tests:` no se hereda.
- CA3: Parámetros: `${nombre}` en cualquier texto de la regla se reemplaza al cargar. El valor sale de `with:` de la
  hija, de `params:` de la plantilla (valores por defecto) o de `params:` del paquete, en ese orden. Un texto que es
  solo `${nombre}` toma el valor con su tipo (número, lista). Un parámetro que no existe es un error; `\${` escribe
  `${` literal.
- CA4: El reemplazo solo se hace en reglas de un paquete, que usan `extends` o que declaran `params`: una regla
  global de antes no cambia aunque tenga `${` en un texto.

**H3 — Versión, requisitos y migraciones.** Como *autor de un paquete*, quiero declarar qué versión es, qué necesita
y qué hacer al actualizar, para que instalar una versión nueva no rompa los datos.
- CA1: `requires:` con `omnievents: ">=1.6.0"` y `packs: { plantillas: ">=1.0.0" }` (comparaciones `>=`, `>`, `<=`,
  `<`, `=`, separadas por coma). Si no se cumple, el paquete no carga y el error dice qué falta y qué versión hay.
- CA2: El plugin recuerda la última versión cargada de cada paquete. Al arrancar o recargar con una versión mayor,
  corre en orden las `migrations:` del paquete con versión mayor que la guardada y menor o igual que la nueva (cada
  una es una lista de acciones de servidor, con `var.from` y `var.to`) y guarda la nueva versión. La primera vez que
  se ve un paquete solo se guarda su versión. Una versión menor (bajar de versión) avisa y no corre nada.
- CA3: La consola dice qué paquete cambió de versión y cuántas migraciones corrieron; un error en una migración se
  registra y no detiene la carga.

**H4 — Ver y manejar paquetes.** Como *administrador*, quiero ver qué paquetes hay y apagar uno entero.
- CA1: `/omni pack list`: cada paquete con versión, reglas, plantillas, si está activo y sus errores.
- CA2: `/omni pack info <paquete>`: descripción, autor, parámetros, requisitos, reglas, versión guardada.
- CA3: `/omni pack enable|disable <paquete>` activa o desactiva todas sus reglas hasta el siguiente `reload` (como
  `/omni enable`); `enabled: false` en `pack.yml` lo deja apagado. Permiso `omnievents.command.pack` (`op`).

**H5 — Documentado y validado como el resto.** Como *administrador*, quiero autocompletado y referencia para lo nuevo.
- CA1: `template`, `extends`, `with` y `params` están en el registro de opciones de regla (esquema JSON y
  referencia); las claves de `pack.yml` en la referencia y en un esquema propio para `pack.yml`.
- CA2: Los ejemplos traen un paquete que funciona tal cual (plantilla, parámetros, `call` relativo) con `tests:`.

## Fuera de alcance
- Descargar, instalar o publicar paquetes desde internet (constitución II.4); exportar a `.zip`.
- Migraciones que renombren variables en los datos de jugadores desconectados; migraciones por jugador.
- Mover una regla existente a un paquete conserva su lógica pero no sus datos (enfriamientos, usos, estado): su
  nombre cambia. Se documenta.
- Heredar de una regla que no es plantilla; mezclar listas al heredar (`actions` se reemplaza entera).
- Prefijo de paquete en los comandos declarados (`command.name` sigue siendo global en Minecraft).

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Número de spec? | 011: la 003 está reservada (paridad con ConditionalEvents, otra sesión) |
| 2 | ¿Alcance? | Todo: paquetes con espacio de nombres, plantillas con `extends` y parámetros, versión con `requires` y migraciones, `/omni pack` (≈ 1 sesión) |
| 3 | ¿Sintaxis de los parámetros? | `${nombre}`, reemplazo de texto al cargar, distinto de `{expr}` |
| 4 | ¿Qué hacen las migraciones? | Acciones de servidor, una vez al cargar la versión nueva, con `var.from` y `var.to`; nada por jugador |
| 5 | ¿Versión y capturas? | 1.6.0, sin capturas |

## Preguntas abiertas
- (ninguna)

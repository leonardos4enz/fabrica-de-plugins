# Spec 009 — OmniEvents: variables con caducidad y en bloques, entidades e ítems

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 001, 007, 008 ·
**Origen:** propuesta P5 de la [investigación de plugins exitosos](../../../investigacion/plugins-exitosos.md)
(§4 y §6), elegida por el dueño (2026-10-24)

## Problema
Las variables de OmniEvents (`pvar`, `gvar`) viven para siempre y solo en el jugador o en el servidor. Eso deja sin
resolver pedidos comunes en ConditionalEvents que hoy necesitan otro plugin o trucos con temporizadores:
- **Rachas y cuentas regresivas:** «la racha se pierde tras 10 minutos sin matar», «el bono dura una hora». Hoy hay
  que guardar la hora a mano, compararla en cada regla y sondear con un `timer` para avisar que se acabó.
- **Datos en el objeto, no en el jugador:** «este cofre es de Leo», «este zombi ya soltó su premio», «esta espada
  está ligada a su dueño». Una `pvar` no viaja con el ítem al cambiar de manos ni sigue a la criatura. Entre los
  rivales, solo Denizen lo tiene.

## Historias
**H1 — Caducidad.** Como *administrador*, quiero que una variable se borre sola pasado un tiempo.
- CA1: `set` acepta la clave hermana `expire: <duración>` (`10m`, `1h`, `30s`, o una plantilla con `{…}`): la
  variable vale lo asignado hasta entonces y después es nula, como si se hubiera hecho `unset`. Cada `set` con
  `expire` vuelve a contar el plazo; un `set` sin `expire` quita la caducidad; `unset` la borra con la variable.
- CA2: Funciona con `pvar`, `gvar` y las variables de objetos (H3). En `var` (de la ejecución) es error al cargar:
  no vive lo bastante para caducar. Una duración que no se entiende o no es positiva es error al cargar (si es
  fija) o de la acción (si sale de una plantilla).
- CA3: La caducidad es persistente: sobrevive a reinicios y recargas; una variable que caducó con el servidor
  apagado o el jugador desconectado ya no existe al volver.
- CA4: `ttl(variable)` devuelve los segundos que le quedan (con decimales), `-1` si existe y no caduca, o nulo si no
  existe. Acepta `pvar.x`, `gvar.x` y `<objeto>.data.x`; otra cosa es error al cargar.
- CA5: `/omni var list|get` muestra cuánto le queda a cada variable que caduca; `/omni var set <quién> <nombre>
  <valor> --expire 10m` asigna con caducidad.

**H2 — Aviso al caducar.** Como *administrador*, quiero reaccionar cuando una variable caduca («se perdió tu racha»).
- CA1: El disparador `var_expire` corre cuando caduca una `pvar` de un jugador conectado (con `player`) o una
  `gvar` (sin jugador). Raíces: `variable` (el nombre), `value` (lo último que valía) y `scope` (`pvar` o `gvar`).
  La opción `variables: [racha, bono]` lo limita a esas variables.
- CA2: Los `trigger: state` que leen esa variable se revisan al caducar (su `on_exit` corre como con un `unset`).
- CA3: La revisión no sondea a cada jugador: una cola ordenada por hora de caducidad que se mira una vez por
  segundo; sin variables por caducar, cuesta una comparación. Las variables de un jugador desconectado caducan sin
  aviso (al volver ya no están).
- CA4: Las variables de objetos (H3) no avisan: caducan al leerse (no se puede recorrer cada chunk e ítem).

**H3 — Variables en bloques, entidades e ítems.** Como *administrador*, quiero guardar datos en el objeto mismo.
- CA1: `<objeto>.data.<nombre>` lee y escribe una variable de cualquier expresión que dé un bloque, una entidad
  (también un jugador) o un ítem: `set: "block.data.dueño = player.name"`, `target.data.marcado`,
  `player.item.data.ligada`, `unset: "block.data.dueño"`. En condiciones y plantillas se lee igual. Sobre algo que no
  es bloque, entidad ni ítem, `.data` conserva el significado anterior (la propiedad `data` del objeto).
- CA2: Los datos viven en el `PersistentDataContainer` de Paper: el del ítem viaja con el ítem (se guarda en el
  ítem); el de la entidad, con la entidad; el del bloque, en su chunk. Sobreviven a reinicios.
- CA3: Los datos de un bloque se borran cuando el bloque deja de estar: romperlo (jugador), explosiones, fuego,
  hojas que se secan, criaturas que lo cambian (endermans, bloques que caen) y pistones que lo mueven. Se borran
  después de las reglas del evento, que aún pueden leerlos. Esos oyentes se registran solo si alguna regla usa `.data`.
- CA4: Valores: número, texto y booleano (lo demás se guarda como texto). Límites en `config.yml`: largo máximo del
  texto y variables por objeto. Pasarse es un error de la acción (en `/omni debug` y en el log) que no corta la regla.
- CA5: Escribir en algo que no es bloque, entidad ni ítem (o en aire, o en un ítem vacío) es un error de la acción.
- CA6: `/omni data <bloque|entidad|mano>` muestra las variables del bloque que mira, la entidad que mira o el
  ítem en la mano del que lo usa (permiso `omnievents.command.data`, `op` por defecto).

**H4 — Migración sin pérdida.** Como *dueño del servidor*, quiero actualizar el plugin sin perder datos.
- CA1: Los archivos de `data/` de la 1.3.0 cargan igual en la nueva versión.
- CA2: La primera vez que arranca la nueva versión con datos de la anterior, copia `data/` entero a
  `data/respaldo-formato1-<fecha>/` antes de escribir nada; cada archivo que guarda lleva `format: 2`.
  Con la copia ya hecha, no se repite.
- CA3: Un archivo de `data/` con un formato más nuevo que el que entiende el plugin no se sobrescribe: se avisa en
  el log y se conserva como estaba (para no perder datos al volver a una versión anterior).

**H5 — Documentación generada.** `expire`, `ttl`, `var_expire` y su opción `variables` y la sintaxis `.data` se
registran con descripción (spec 007), aparecen en el esquema y en la referencia, y hay un ejemplo nuevo en
`rules/ejemplos/variables.yml`.

## Fuera de alcance
- Listas y mapas en variables de objetos (se guardan como texto).
- Aviso al caducar de variables de objetos (caducan al leerse).
- Mover los datos de un bloque con pistones (se borran).
- Estados (`trigger: state`) que dependen de `.data` sin `check_every` (no se puede deducir cuándo cambia).

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Número de spec? | 009 (la 003 está reservada para la paridad con ConditionalEvents) |
| 2 | ¿Qué pasa al caducar? | Disparador `var_expire` y aviso a los `trigger: state`, con una cola barata; las de objetos caducan al leerse |
| 3 | ¿Sintaxis para variables de objetos? | `<objeto>.data.<nombre>` sobre cualquier expresión (no «nbt»: es el PDC) |
| 4 | ¿Datos de un bloque al romperse? | Se borran después de las reglas del evento; pistones también |
| 5 | ¿Migración de `data/`? | Marca `format: 2` y respaldo único de `data/` |
| 6 | ¿Versión del plugin? | 1.4.0 (capacidad nueva; la configuración y los datos anteriores siguen cargando) |
| 7 | ¿Capturas? | No (lección de la 004) |

Costo estimado (lección de la 004): M, una sesión. Se propuso el alcance completo de P5 menos listas en objetos y
aviso de caducidad en objetos, que son lo caro (recorrer chunks) y lo de menos valor.

## Preguntas abiertas
- (ninguna)

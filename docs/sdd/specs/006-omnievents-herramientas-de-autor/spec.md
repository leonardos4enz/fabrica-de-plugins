# Spec 006 — OmniEvents: herramientas de autor

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 001, 002, 005 ·
**Origen:** propuestas P1, P2 y P4 de la [investigación de plugins exitosos](../../../investigacion/plugins-exitosos.md)
(§4 y §6), elegidas por el dueño (2026-10-24)

## Problema
Quien escribe reglas hoy tiene tres huecos:
- **Por qué no se cumplió.** Una regla tiene un solo `else` para todas sus condiciones. Para decirle al jugador
  *qué* le falta («necesitas nivel 10», «te faltan diamantes») hay que repetir las condiciones dentro de `if`
  anidados o partir la regla en varias. Tampoco hay forma corta de decir «al menos 2 de estas 3».
- **Probar sin jugar.** `/omni debug` solo muestra lo que pasa cuando alguien dispara la regla de verdad, con sus
  efectos. No se puede preguntar «¿esta regla se cumpliría ahora para Ana?» ni ver qué acciones correría sin
  correrlas. Y para pedir ayuda, la traza hay que copiarla del chat a mano.
- **Cuánto cuesta.** `/omni stats` da el tiempo promedio por regla, pero no dice qué condición o acción es la cara ni
  cuánto del tick se lleva el plugin. No hay forma de declarar «esta regla no debería pasar de 0,5 ms».

## Historias
**H1 — Acciones propias por condición.** Como *administrador*, quiero que cada condición lleve sus propias acciones
cuando falla, para decirle al jugador exactamente qué le falta sin duplicar reglas.
- CA1: Una condición puede escribirse como mapa `expr: "…"` con `deny: [acciones]`. Si es la primera que falla,
  corren sus `deny` y **no** el `else` de la regla.
- CA2: Una condición que falla y no tiene `deny` corre el `else` de la regla, como hoy.
- CA3: La forma actual (lista de textos, o un texto suelto) carga y se comporta igual que antes. Se pueden mezclar
  textos y mapas en la misma lista.
- CA4: Errores de forma se informan al cargar con archivo y ruta: mapa sin `expr` ni `any`, clave desconocida con
  «¿quisiste decir?», `deny` que no es una lista de acciones válida, expresión inválida.

**H2 — «Al menos N de M».** Como *administrador*, quiero pedir que se cumplan al menos N de varias condiciones.
- CA1: Un elemento `any: N` con `of: [condiciones]` se cumple si al menos N de las de `of` se cumplen. Cada elemento
  de `of` es un texto o un mapa de condición (se puede anidar otro `any`).
- CA2: El grupo puede llevar su propio `deny` (H1 CA1); los `deny` dentro de `of` se ignoran con un aviso.
- CA3: `any` debe ser un entero entre 1 y el tamaño de `of`; si no, es error de carga.
- CA4: `/omni debug` y `/omni check` muestran el grupo con cuántas se cumplieron y el valor de cada una.

**H3 — Revisar una regla ahora, sin efectos.** Como *administrador*, quiero `/omni check <regla> [jugador]` para
saber si la regla se cumpliría en este momento para ese jugador y por qué.
- CA1: Evalúa mundo, permiso, permiso de bypass, **todas** las condiciones (sin cortar en la primera que falla),
  límite de usos y enfriamiento, con el valor de cada una (`✔`/`✘` y los valores como en `/omni debug`).
- CA2: Dice el resultado: qué rama correría (`actions`, el `deny` de qué condición, `else`, `on_limit`,
  `on_cooldown` o nada) y cuántas acciones tiene. No ejecuta ninguna acción, no cuenta usos ni enfriamientos y no
  cambia las estadísticas.
- CA3: Sin jugador usa al que lo escribe (o ninguno, desde la consola). Si la regla usa datos de un evento que no hay
  (`block`, `event`, `message`…), lo avisa: esas raíces valen nulo en la revisión.
- CA4: Regla o jugador desconocido → mensaje claro con «¿quisiste decir?»; permiso propio
  `omnievents.command.check` (`op` por defecto).

**H4 — Simular una regla.** Como *administrador*, quiero `/omni run <regla> <jugador> --simular` para ver qué
acciones correría, en orden, sin aplicarlas.
- CA1: Recorre la regla como un disparo real (condiciones, límite, enfriamiento, rama), y dentro de las acciones
  evalúa `if`, bucles, `when`, `to:` y `random` con los valores actuales; cada acción con efecto se **lista** con su
  texto y no se aplica (mensajes, comandos de consola, variables, ítems, mundo…). `delay` se lista sin esperar y
  `call` se simula por dentro.
- CA2: La simulación no cuenta usos ni enfriamientos, no cambia variables ni estadísticas y no envía nada a nadie.
- CA3: Las asignaciones de variables se listan pero no se aplican: una condición posterior que dependa de ellas ve
  el valor de antes (se documenta).
- CA4: Sin `--simular`, `/omni run <regla> <jugador>` dispara la regla de verdad, como si la activara ese jugador, con
  cualquier disparador (no solo `call`): condiciones, límite, enfriamiento y acciones reales.
- CA5: Permiso propio `omnievents.command.run` (`op` por defecto).

**H5 — Exportar la traza.** Como *administrador*, quiero `/omni debug export` para guardar en un archivo lo que
`/omni debug` mostró y compartirlo al pedir ayuda.
- CA1: Guarda en `plugins/OmniEvents/trazas/<fecha-hora>.txt` las últimas trazas de depuración y simulaciones (hasta
  un tope configurable), con un encabezado: versión del plugin, del servidor y de Java, número de reglas, los
  problemas de la última carga y el YAML de cada regla que aparece en las trazas.
- CA2: No usa la red (constitución II.4). Escribe fuera del hilo principal y de forma atómica; responde con la ruta.
- CA3: Sin trazas guardadas, lo dice y sugiere `/omni debug <regla>`.

**H6 — Perfilador.** Como *dueño del servidor*, quiero `/omni cpu` para ver cuánto tiempo gasta cada regla, cada
condición y cada acción, y cuánto del tick se lleva OmniEvents.
- CA1: `/omni cpu on|off|reset` enciende, apaga y reinicia el perfilador. Apagado no mide nada por condición ni por
  acción (cero costo extra).
- CA2: Encendido, mide 1 de cada N disparos (N configurable) y muestra, ordenado de mayor a menor: por regla el
  tiempo promedio por disparo (µs) y el % estimado del hilo principal; dentro de cada regla, sus condiciones y
  acciones más caras; y el costo agrupado por disparador (los oyentes más caros).
- CA3: Muestra desde cuándo mide y con qué muestreo. Permiso `omnievents.command.cpu` (`op` por defecto).

**H7 — Presupuesto por regla.** Como *administrador*, quiero declarar `budget: 0.5ms` en una regla y que se me avise
si su tiempo promedio lo pasa.
- CA1: `budget` acepta `ms` o `µs`/`us` (`0.5ms`, `200us`); un valor inválido es error de carga.
- CA2: Si el promedio reciente de la regla pasa su presupuesto, se avisa en la consola (con el promedio y el
  presupuesto), como mucho una vez cada cierto tiempo por regla (configurable). No cuenta los primeros disparos
  (calentamiento de la JVM).
- CA3: `/omni cpu` lista las reglas con presupuesto, su promedio reciente y si lo pasan, encendido o apagado el
  perfilador (el tiempo por regla ya se mide siempre).

## Fuera de alcance
- El esquema JSON y la referencia generada (P3): spec siguiente.
- Simular acciones de otros plugins por dentro: un comando de consola se lista, no se corre.
- `/omni check` y `--simular` con un evento real (bloque, mensaje de chat): se revisan con los datos del jugador; las
  raíces del evento valen nulo.
- Subir la traza a un sitio (como `/denizen submit`): sin red.

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Número de spec? | 006: la 003 está reservada (paridad con ConditionalEvents, otra sesión) |
| 2 | ¿El `deny` de una condición reemplaza al `else` de la regla? | Sí: corre solo su `deny`; las condiciones sin `deny` usan el `else` (como DeluxeMenus) |
| 3 | ¿`/omni run` sin `--simular` ejecuta la regla de verdad? | Sí: la dispara como si la activara ese jugador (condiciones, enfriamiento, acciones reales); con `--simular` solo lista |
| 4 | ¿Versión del plugin? | 1.1.0 (capacidades nuevas, la configuración anterior sigue cargando) |
| 5 | ¿Capturas? | No: ninguna imagen nueva (lección de la 004) |

## Preguntas abiertas
- (ninguna)

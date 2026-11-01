# Spec 025 — Worldcraft: un editor de mundo que no traba el servidor

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a, 8/8 dos veces) (ver [verify](verify.md)) · CLARIFY: nombre, comando, confirmación y
publicación decididos por el dueño; lo demás, propuesta de la IA aceptada al revisar · **Plugin:** Worldcraft · **Depende de:** — ·
**Origen:** pedido del dueño (2026-11-01): «necesito un plugin que sea una version mejorada de world edit. Super super
mejorada, todo lo malo del worldedit hacerlo mejor. Investiga primero en la spec 025 y dale, despues vengo».
**Investigación:** [editores de mundo](../../../investigacion/editores-de-mundo.md) (qué se le critica a WorldEdit y a
FAWE, qué hacen bien Axiom, Litematica, CoreProtect y otros, con fuentes).

## Problema
Editar el mundo a lo grande (llenar, reemplazar, copiar y pegar miles de bloques) es lo que más usa un constructor y lo
que más rompe un servidor. Según la investigación:
- **Lag y caídas.** WorldEdit hace toda la obra en el hilo principal: con un millón de bloques el servidor se congela o
  se cae (su propia guía lo admite; la tarea de repartir la obra por tick está abierta desde 2012). FAWE es asíncrono pero
  depende de acceso interno al servidor y tiene sus propios fallos (luz, cofres mal escritos, bloques fantasma cuando
  dos obras se cruzan).
- **Deshacer frágil.** El historial vive en memoria (15 pasos), se pierde al reiniciar, y deshacer pisa lo que otro
  jugador construyó encima después.
- **A ciegas.** No hay vista previa: un `//set` con la selección equivocada se ve cuando ya está hecho. Ver la
  selección exige un mod en el cliente.
- **Sintaxis críptica.** `%`, `#`, `^`, `!` y `=` significan cosas distintas en patrones y máscaras; los errores no
  dicen qué parte está mal.
- **Sin límites de fábrica.** Por defecto no hay tope de bloques: un ayudante con permiso tumba el servidor.

## Historias

**H1 — Seleccionar y ver la selección.** Como *constructor*, quiero marcar dos esquinas y ver la caja sin instalar
nada en mi cliente.
- CA1: `/wc wand` da una varita (por defecto un hacha de oro, `wand.item`). **Solo ese ítem marcado** es varita: un
  hacha cualquiera sigue siendo un hacha.
- CA2: Clic izquierdo con la varita en un bloque = esquina 1; clic derecho = esquina 2. El bloque no se rompe ni se usa.
- CA3: Al poner una esquina el mensaje dice dónde y, con las dos, el tamaño (ancho×alto×fondo) y los bloques. Mientras
  tiene la varita en la mano, el jugador ve el contorno de la caja con partículas **que solo ve él**, con un tope de
  partículas por dibujo (`selection.max_particles`); `/wc sel hide|show` lo apaga y prende.
- CA4: `expand`, `contract` y `shift <bloques> [dirección]` con palabras (`up`, `down`, `north`, `south`, `east`,
  `west` o su inicial); sin dirección, hacia donde mira. `expand vert` llega del fondo al techo del mundo.
- CA5: `pos1`/`pos2` sin argumentos toman la posición del jugador; con `x y z` (y `~`) las coordenadas; desde la consola
  con `--world`. Una esquina en otro mundo borra la anterior.
- CA6: `/wc sel` dice tamaño, bloques, mundo y extremos; `/wc sel clear` la borra.

**H2 — Patrones y máscaras que se leen.** Como *constructor*, quiero escribir qué bloques poner y cuáles cambiar como
lo diría en palabras, y que un error me diga qué corregir.
- CA1: Patrón: un bloque (`stone`, `minecraft:stone`), un bloque con estado (`oak_stairs[facing=north]`) o una mezcla
  con porcentajes (`70% stone, 30% andesite`; sin porcentajes, partes iguales). El patrón puede ocupar varias palabras.
- CA2: Máscara: lista de bloques separada por comas (cualquiera de ellos), comodín (`*_wool`), etiqueta del juego
  (`#logs`), estado parcial (`oak_log[axis=y]`) y exclusión con `!` o `not` (`*_wool, !red_wool`; `!air`). `air`
  incluye el aire de cueva y del vacío.
- CA3: Un error se informa **antes de tocar nada**, diciendo qué parte está mal y «did you mean…» (`stnoe` → `stone`,
  `#lgos` → `#logs`, `--previwe` → `--preview`, un `%` en una máscara, una etiqueta en un patrón, una propiedad o un
  valor que no existe).
- CA4: `presets.yml` declara patrones y máscaras con nombre que se usan con `$nombre`; se validan al cargar.
- CA5: Una mezcla al azar sale igual en la vista previa y en la obra (la misma semilla por operación).
- CA6: El autocompletado sugiere bloques para la última parte de la lista (también tras `70% ` o `,`), presets y, en
  máscaras, etiquetas.

**H3 — Operaciones del núcleo.** Como *constructor*, quiero las operaciones que más se usan.
- CA1: `set <patrón>`, `replace <máscara> with <patrón>` (o sin `with`: la primera palabra es la máscara), `walls
  <patrón>` (las cuatro paredes) y `faces <patrón>` (paredes, piso y techo; hueca).
- CA2: `copy` guarda la selección relativa a donde está el jugador; `cut` copia y deja aire; `paste` pega relativo al
  jugador. **El aire copiado no se pega** (no borra lo que rodea), salvo con `--air`.
- CA3: `rotate <90|180|270|-90>` y `flip [dirección]` giran o reflejan el portapapeles, también el estado de cada
  bloque (escaleras, troncos, puertas; voltear de arriba abajo cambia la mitad de las escaleras y losas).
- CA4: Lo que hay dentro de un contenedor (cofre, barril, horno…) y el texto de un cartel viajan al copiar y pegar y
  vuelven al deshacer.

**H4 — Ver antes de hacer.** Como *constructor*, quiero ver el resultado antes de que cambie el mundo.
- CA1: Con `confirm.enabled` (de fábrica), una operación de más de `confirm.above_blocks` bloques (50 000) no corre sola: dice cuántos bloques y cuánto
  tardará y espera `/wc confirm` (vence a los `confirm.expire_seconds`).
- CA2: Con `--preview`, el resultado se muestra **solo a ese jugador** con bloques falsos (el mundo no cambia; otro
  jugador no ve nada), hasta `preview.max_blocks` bloques (si hay más, lo dice).
- CA3: `/wc confirm` hace exactamente lo que mostró la vista previa (mismo patrón y semilla); la máscara se vuelve a
  evaluar contra el mundo real al poner cada bloque.
- CA4: `/wc cancel`, el vencimiento o salir del juego le devuelven al jugador los bloques reales.

**H5 — Sin lag.** Como *dueño del servidor*, quiero que ninguna obra baje el TPS.
- CA1: El plugin entero usa como mucho `performance.max_ms_per_tick` (8 ms de los 50 de un tick) y
  `max_blocks_per_tick` por tick, repartidos entre todas las obras; se pasa a lo sumo por un bloque (F-01).
  `/wc jobs` muestra el tiempo del último tick, el máximo y el tick más largo de la última obra.
- CA2: El tiempo se reparte por turnos entre los jugadores con obras: una obra enorme no deja esperando a la chica de
  otro.
- CA3: Los chunks sin cargar se cargan sin bloquear el tick y se sostienen mientras la obra los usa; se sueltan al
  terminar.
- CA4: El avance se ve en la barra de acción; `/wc cancel` detiene las obras propias y lo hecho queda deshacible.
- CA5: Sin física durante la obra: la arena no cae y el agua no corre a mitad de camino.
- CA6: Dos obras cuyas cajas se cruzan en el mismo mundo no corren a la vez: la más nueva espera (sin bloques
  fantasma).

**H6 — Deshacer que no se pierde y no pisa a nadie.** Como *constructor y moderador*, quiero deshacer siempre.
- CA1: `undo [n]` y `redo [n]`; `history` lista las últimas obras (número, cuándo, qué, bloques, mundo, estado). Una
  obra nueva descarta lo que se podía rehacer.
- CA2: El historial está en disco: sobrevive a reinicios y a `reload`; un archivo cortado por una caída se lee hasta
  donde llegó.
- CA3: **Deshacer seguro**: un bloque que alguien cambió después de la obra no se toca; el mensaje dice cuántos quedaron.
- CA4: Deshacer devuelve el contenido de contenedores y carteles (H3 CA4).
- CA5: Se guardan `history.max_per_player` obras por jugador y `history.keep_days` días; lo más viejo se borra al
  arrancar.
- CA6: Con `worldcraft.moderate`, `history <jugador>` y `undo|redo --player <jugador>` sobre las obras de otro.
- CA7: Una obra que el servidor cortó al apagarse queda como cortada y se puede deshacer.

**H7 — Límites seguros por permiso.** Como *dueño del servidor*, quiero decidir cuánto puede editar cada uno.
- CA1: Grupos en `config.yml` (`limits`): `default` para todos y otros con `worldcraft.limit.<grupo>`; con varios
  grupos gana lo más generoso (número mayor, todos los mundos, prohibido solo si todos lo prohíben).
- CA2: `max_blocks` por obra: una obra más grande se rechaza **antes de empezar** con su tamaño, el tope y los grupos.
  De fábrica el tope es bajo (50 000 bloques; `builder` 1 000 000).
- CA3: `blocked_blocks` (con la sintaxis de máscaras): de fábrica nadie pone roca madre, barreras, bloques de comandos,
  portales ni generadores salvo el grupo `admin`; vale también al pegar.
- CA4: `worlds`: dónde puede editar el grupo.
- CA5: `max_jobs`: cuántas obras a la vez (en marcha o en espera).
- CA6: `/wc limits` muestra los límites de quien lo pide.

**H8 — Administración.** Como *dueño del servidor*, quiero un plugin claro de configurar y de diagnosticar.
- CA1: Permisos `worldcraft.use`, `worldcraft.moderate` y `worldcraft.admin`, todos `default: op`.
- CA2: `/wc reload` recarga `config.yml`, `messages.yml` y `presets.yml`; un archivo con errores conserva lo que
  funcionaba y dice archivo, ruta y causa con «did you mean»; `/wc validate` lista los problemas.
- CA3: Al primer arranque se crean los archivos, en inglés y comentados, y cargan sin avisos.
- CA4: Mensajes en `messages.yml` (MiniMessage, traducibles); quien no tiene permiso no ve los subcomandos.
- CA5: `/wc help` lista los subcomandos que puede usar quien pregunta y `/wc help <comando>` da su forma y un
  ejemplo.
- CA6: `/wc selftest` (también desde la consola) pone una mezcla en una caja junto al punto de aparición, la deshace, la
  rehace y la deshace, y comprueba cada paso.

## Fuera de alcance (roadmap, ver el [roadmap](../../roadmap.md))
- Esquemas `.schem` (importar y exportar el formato abierto Sponge v3) y una carpeta de esquemas compartida.
- Pinceles (esfera, suavizar, pintar) y formas (esfera, cilindro, pirámide); selecciones no cuboides.
- Respetar regiones de WorldGuard u otros (la escritura ya pasa por una sola puerta: `EditWorld.set`).
- Rollback de cualquier jugador por zona y tiempo (como CoreProtect `u: t: r:`), con vista previa.
- Copiar entidades (soportes, marcos), biomas; `stack`, `move`, `smooth`.
- Gizmos con *block displays* para mover y girar lo pegado; vista previa siguiendo la mira.
- Folia. Atajos `//set` (chocan con WorldEdit si los dos están instalados).

## Decisiones (CLARIFY)
La IA las propuso con el dueño ausente; él respondió después (2026-11-01). Las filas que dicen «Dueño» son suyas; las
demás siguen como propuesta de la IA que el dueño no objetó.
| # | Pregunta | Decisión |
|---|---|---|
| 1 | Nombre del plugin | **Dueño: Worldcraft** (la IA había propuesto Blockwright; el dueño también pensó en Blockshop, «como Photoshop»). Sin coincidencias en Modrinth. Ojo: «Worldcraft» fue el nombre original del editor de mapas de Half-Life (hoy Valve Hammer Editor) y hay juegos de celular con ese nombre; la página de presentación no debe usar marcas ajenas |
| 2 | Comando | **Dueño: `/worldcraft` con alias `/wc`**. No `/b` (es `/brush` de VoxelSniper) ni `//` (choca con WorldEdit instalado al lado). Si otro plugin ya tiene `/wc`, Paper deja `/worldcraft:wc` y `/worldcraft` |
| 3 | Alcance del núcleo (lección 004) | Selección con contorno, 4 operaciones de región + copiar/cortar/pegar/girar/voltear, vista previa y confirmación, motor por tandas, deshacer en disco y seguro, patrones y máscaras legibles, límites por permiso. Esquemas, pinceles, formas y regiones van a specs siguientes |
| 4 | Varita | Hacha de oro marcada (no el hacha de madera de WorldEdit: con los dos plugins, cada uno con la suya) |
| 5 | ¿Pegar el aire copiado? | No por defecto (`--air` para sí): no borra lo que rodea |
| 6 | Tope de tiempo y bloques por tick | 8 ms y 200 000 bloques por tick para todo el plugin |
| 7 | ¿Cuándo pedir confirmación? | **Dueño: desactivable desde `config.yml` (`confirm.enabled`) y a partir de 50 000 bloques**; vista previa de hasta 50 000 |
| 8 | Límites de fábrica | `default` 50 000 bloques y 2 obras; `builder` 1 000 000 y 3; `admin` 10 000 000 y 5 sin bloques prohibidos |
| 9 | Historial | 30 obras por jugador, 7 días |
| 10 | Idioma | Inglés para el jugador y el dueño (ADR 0005); documentos de la fábrica en español |
| 11 | ¿Datos de bloque al deshacer y copiar? | Contenedores y carteles en el núcleo; el resto de datos (cabezas, estandartes, generadores) en el roadmap |

## Respuestas del dueño (2026-11-01)
1. **Nombre:** Worldcraft, comando `/wc`.
2. **Alcance:** quiere que sea «muy muy superior» y sobre todo **fácil de construir**: que un novato haga montañas,
   islas y modifique terreno sin batallar, porque lo difícil de WorldEdit no es el plugin sino *cómo crear*. Eso es una
   capacidad nueva y grande: va como spec 026 (herramientas de terreno para novatos), en su propia sesión.
3. **Migrar desde WorldEdit:** sí («sería bueno ya que WE se usa globalmente»). Worldcraft debe leer (y escribir) los `.schem` de
   WorldEdit para que un servidor se cambie sin perder sus esquemas: spec propia, priorizada en el roadmap.
4. **Confirmación:** desactivable desde `config.yml` y a partir de 50 000 bloques (hecho en esta spec).
5. **Publicación:** todavía no: primero lo quiere probar.

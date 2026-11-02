# Spec 027 — Worldcraft: esquemas `.schem` de WorldEdit (importar y exportar)

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a, 15/15 dos veces) (ver [verify](verify.md)) · CLARIFY: propuesta de la IA pendiente del dueño · **Plugin:** Worldcraft ·
**Depende de:** [025](../025-worldcraft-nucleo/spec.md) (portapapeles, pegar con vista previa, girar, voltear, deshacer, límites) ·
**Origen:** pedido del dueño en el CLARIFY de la 025 (2026-11-01): Worldcraft debe leer y escribir los `.schem` de WorldEdit,
«sería bueno ya que WE se usa globalmente». Un servidor que viene de WorldEdit tiene que poder pasarse a Worldcraft sin
perder sus esquemas.
**Investigación:** [editores de mundo](../../../investigacion/editores-de-mundo.md) §1.5 (esquemas en WorldEdit y FAWE) y §4
(licencias: WorldEdit es GPLv3; el formato Sponge es una especificación pública).

## Problema
- Un servidor con años de WorldEdit tiene cientos de esquemas en `plugins/WorldEdit/schematics/` (casas, árboles,
  arenas, lobbies). Si el editor nuevo no los lee, cambiarse es perderlos: nadie se cambia.
- Un esquema hecho en Worldcraft tiene que poder llevarse a otro servidor que usa WorldEdit (o FAWE, o un sitio de
  esquemas): el formato común es el `.schem` de Sponge.
- Lo que más se le critica a WorldEdit al pegar (investigación §1.5): pegados incompletos, cofres que pierden lo de
  adentro, sin vista previa, y archivos que llegan rotos o enormes sin aviso.

## Historias

**H1 — Guardar lo copiado como esquema.** Como *constructor*, quiero guardar lo que copié en un archivo que entienda
WorldEdit.
- CA1: `/wc schem save <nombre>` guarda el portapapeles en `plugins/Worldcraft/schematics/<nombre>.schem` en el formato
  Sponge v3 (NBT con GZip). Lo girado o volteado se guarda ya girado.
- CA2: Al cargarlo (en Worldcraft o en WorldEdit) queda en el mismo lugar relativo a quien pega que al copiarlo.
- CA3: Lo que hay dentro de los contenedores y el texto de los carteles viaja en el archivo con la forma estándar del juego.
- CA4: Un nombre que ya existe no se pisa: se rechaza y dice cómo reemplazarlo (`--overwrite`, con el permiso de borrar).
- CA5: El mensaje dice nombre, tamaño, bloques y KB. Guardar no traba el servidor (el archivo se arma y escribe fuera del
  hilo principal).

**H2 — Cargar un esquema y pegarlo como siempre.** Como *constructor*, quiero cargar un `.schem` y pegarlo con todo lo de
la 025.
- CA1: `/wc schem load <nombre>` lo pone en mi portapapeles; después `paste`, `paste --preview`, `rotate`, `flip`, `undo`
  funcionan igual que con lo copiado.
- CA2: Lee Sponge v1, v2 y v3: los de WorldEdit 6 y 7 y FAWE. Pega en el mismo lugar relativo que WorldEdit.
- CA3: Los contenedores con sus ítems y los carteles con su texto se pegan con su contenido (los ítems de versiones
  viejas del juego se actualizan). Otros datos de bloque (cabezas, estandartes, generadores…) no se pegan, pero se
  conservan si se vuelve a guardar; el mensaje los cuenta.
- CA4: Un bloque que este servidor no conoce se carga como aire y el mensaje dice cuántos y cuáles (hasta 5 nombres);
  los nombres cambiados entre versiones conocidos (`grass` → `short_grass`, `grass_path` → `dirt_path`, `chain` ↔
  `iron_chain`) se traducen; una propiedad que no existe se quita y el resto del estado se conserva.
- CA5: Entidades y biomas del archivo no se cargan (roadmap) y el mensaje dice cuántas entidades había. Un archivo de una
  versión del juego más nueva que el servidor avisa.
- CA6: El mensaje dice tamaño, bloques y lo de CA3–CA5. Cargar no traba el servidor.

**H3 — Pasarse desde WorldEdit sin perder nada.** Como *dueño de un servidor que viene de WorldEdit*, quiero usar los
esquemas que ya tengo sin moverlos.
- CA1: Las carpetas de esquemas de WorldEdit y FAWE (`schematics.extra_folders`) se leen tal cual: `list` las muestra
  marcadas `[WorldEdit]` y `load` las encuentra (primero la carpeta propia).
- CA2: Esas carpetas son de solo lectura: Worldcraft nunca escribe ni borra ahí.
- CA3: Si WorldEdit no está (o se desinstaló dejando su carpeta), igual se leen; si la carpeta no existe, no hay aviso.
- CA4: Un esquema guardado por WorldEdit en una subcarpeta se nombra con ella (`castles/tower`).

**H4 — Listar y borrar.** Como *constructor*, quiero ver qué esquemas hay y quitar los que sobran.
- CA1: `/wc schem list [página] [filtro]` lista nombre, KB y fecha, 10 por página, ordenados por nombre.
- CA2: `/wc schem delete <nombre>` (permiso propio) lo pasa a `schematics/.trash/` (no se borra al momento); la papelera
  se vacía de lo que tenga más de `schematics.trash_days` días al arrancar. Solo la carpeta propia.
- CA3: El autocompletado sugiere los nombres de los esquemas (de todas las carpetas en `load`, solo de la propia en
  `delete`).

**H5 — Seguro con archivos de cualquiera.** Como *dueño del servidor*, quiero que un archivo raro no rompa nada.
- CA1: Nombres seguros: letras, números, `_`, `-` y `.` (sin empezar por `.`), hasta 3 subcarpetas con `/`; nunca `..`, rutas
  absolutas ni `\`. El archivo real tiene que quedar dentro de la carpeta (un enlace que sale de ella se rechaza).
- CA2: Topes antes de gastar memoria: tamaño del archivo (`schematics.max_file_kb`), bloques del esquema
  (`schematics.max_blocks`, mirado en la cabecera antes de reservar nada), tamaño descomprimido y profundidad del NBT.
- CA3: Un archivo roto, cortado o que no es un esquema se rechaza con un motivo claro y el portapapeles no cambia.
- CA4: Cargar no salta los límites: pegar un esquema pasa por `max_blocks`, `blocked_blocks` y los mundos del grupo
  (un esquema con bloques de comandos no lo pega el grupo `default`).
- CA5: Permisos `worldcraft.schematic` (list, load, save) y `worldcraft.schematic.delete` (delete y `--overwrite`),
  `default: op`.
- CA6: Una operación de esquemas a la vez por jugador.

**H6 — Administración.** Como *dueño del servidor*, quiero configurarlo y entenderlo.
- CA1: Sección `schematics` en `config.yml`, comentada y validada con rangos; un `config.yml` de la 0.2.0 sin ella carga
  con los valores de fábrica; `reload` la aplica.
- CA2: `help schem` con la forma y un ejemplo; mensajes nuevos en `messages.yml` (MiniMessage, traducibles).

## Fuera de alcance (al [roadmap](../../roadmap.md))
- `.schematic` de MCEdit (ids numéricos de antes de 1.13), `.litematic`, `.nbt` de bloques de estructura.
- Entidades (soportes, marcos, cuadros) y biomas del esquema.
- Pegar datos de bloque que no son contenedores ni carteles (cabezas, estandartes, generadores, libros de atriles).
- Pegar uno al azar de una carpeta (WorldEdit #2063); compartir por internet (`//schem share`, necesita red y aprobación).
- Vista en miniatura del esquema antes de cargarlo.

## Decisiones (CLARIFY)
Propuesta de la IA (2026-11-01), pendiente del dueño.
| # | Pregunta | Propuesta |
|---|---|---|
| 1 | Alcance (lección 004) | Guardar, cargar, listar y borrar; v1–v3; contenedores y carteles; carpetas de WorldEdit y FAWE de solo lectura. Entidades, biomas, `.schematic` y `.litematic`, después |
| 2 | Comando | `/wc schem save|load|list|delete` (también `schematic`), como el `//schem` que ya conocen |
| 3 | Formato al guardar | Solo Sponge v3 (el de WorldEdit 7.3+); v2 solo se lee |
| 4 | ¿Leer la carpeta de WorldEdit o copiarla? | Leerla donde está (solo lectura), así no se duplica ni se desincroniza; una copia con un comando, si el dueño la quiere |
| 5 | Borrar | A una papelera que se vacía a los 7 días (II.6: no destructivo por defecto) |
| 6 | Bloques que no existen | Aire, contados y nombrados; nombres cambiados conocidos, traducidos |
| 7 | Topes de fábrica | 10 MB por archivo, 5 000 000 bloques por esquema (20 MB de memoria) |
| 8 | Permisos | `worldcraft.schematic` y `worldcraft.schematic.delete`, aparte de `worldcraft.use` (un ayudante puede pegar sin poder llenar el disco) |
| 9 | Código | Lector y escritor NBT/GZip propios en Java, escritos desde la especificación pública de Sponge; sin código ni API de WorldEdit (GPL) |

## Preguntas que solo el dueño puede responder
1. ¿Quieres un comando que **copie** los esquemas de WorldEdit a la carpeta de Worldcraft (para borrar WorldEdit del todo),
   o te basta con que se lean de donde están?
2. ¿Te sirve `/wc schem …` o prefieres que también exista `/wc schematic …` en la ayuda (hoy es un alias)?
3. ¿Quién debería poder guardar esquemas en tu servidor: los mismos que editan o solo algunos?
4. Lo siguiente de esquemas: ¿entidades (soportes, marcos), `.litematic`, o pegar uno al azar de una carpeta?

## Respuestas del dueño (2026-11-01)
Llegaron después del cierre; se dejan en el roadmap para una spec propia (la 028 ya está abierta para la paridad con
WorldEdit y FAWE).
1. **Copiar:** sí, que se copien: `/wc schem import` copia los esquemas de WorldEdit y FAWE a la carpeta de Worldcraft
   sin borrar los originales.
2. **Los dos:** `/wc schem` y `/wc schematic` (ya funcionan los dos; falta que la ayuda muestre los dos).
3. **Por permiso:** separar guardar de cargar: `worldcraft.schematic` (listar y cargar), `worldcraft.schematic.save`
   (guardar) y `worldcraft.schematic.delete` (borrar y `--overwrite`).
4. Sin decidir: se le explicó la pregunta en palabras simples; queda abierta.

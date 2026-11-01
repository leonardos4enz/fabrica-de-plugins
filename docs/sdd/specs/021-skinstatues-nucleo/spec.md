# Spec 021 — SkinStatues: estatuas de bloques con la skin de un jugador

**Estado:** Implementado y verificado (compuertas 1, 2 y 3a en Paper 1.21.4 y 26.1.2) · aprobada por el dueño (2026-11-01) · **Plugin:** SkinStatues · **Depende de:** — ·
**Origen:** pedido del dueño (2026-11-01): «un plugin que construya personajes de minecraft, yo poner el nombre y que
se construya su monito, el plugin lo transformaría a la altura deseada por el user», con poses configurables (doblar
los brazos…), la capa y la segunda capa de la skin unida a la primera «para que se vea más real».

## Problema
Una estatua gigante de un jugador (para un spawn, un evento, un homenaje) hoy se hace a mano, bloque a bloque, o con
programas externos que generan un esquema y otro plugin que lo pega (WorldEdit). Es lento, no se puede pedir otra
altura sin rehacerla, la segunda capa de la skin (pelo, chaqueta, mangas) se pierde o queda flotando, siempre sale de
pie con los brazos rectos y, si sale mal, no hay un «deshacer» que devuelva el terreno que había.

## Historias

**H1 — Construir la estatua de un jugador.** Como *administrador*, quiero escribir `/statue build <name> <height>
[pose]` para que aparezca en el mundo la figura de ese jugador, con su skin hecha de bloques, del alto que pedí.
- CA1: La figura es la del modelo del jugador (cabeza, cuerpo, dos brazos y dos piernas, en las proporciones de
  Minecraft: 32 píxeles de alto) escalada a `<height>` bloques; los brazos son de 4 o 3 píxeles de ancho según el
  modelo de la skin (clásico o delgado).
- CA2: Los pies quedan **2 bloques arriba** de la posición del jugador (para no quedar atrapado en la estructura),
  centrada sobre él, y la estatua **mira hacia donde mira el jugador** (redondeado a norte, sur, este u oeste). El
  desplazamiento hacia arriba se cambia en `config.yml`.
- CA3: Cada píxel visible de la skin se convierte en el bloque de la paleta cuyo color se parece más; con una altura
  menor que 32 un bloque junta varios píxeles (su color promedio) y con una mayor un píxel ocupa varios bloques.
- CA4: La estatua es **hueca**: solo la superficie con la skin (el grosor mínimo para que no se vea por dentro ni
  queden huecos). Desde fuera se ve igual que rellena.
- CA5: `<name>` solo acepta nombres de cuenta válidos (letras, números y `_`, de 1 a 16); otro texto se rechaza con
  un mensaje y nunca llega a un archivo, a la red ni a un comando.
- CA6: `<height>` entre `min_height` y `max_height` de `config.yml` (8 y 128 por defecto); si la estatua no cabe entre
  el suelo y el techo del mundo en ese lugar, se rechaza diciendo la altura máxima que cabe ahí.
- CA7: Antes de empezar dice cuántos bloques tendrá y cuánto tardará aproximadamente; al terminar, el número de la
  estatua (para deshacerla o quitarla).

**H2 — De dónde sale la skin.** Como *administrador*, quiero usar la skin de una cuenta de Minecraft o un archivo
propio.
- CA1: Si existe `plugins/SkinStatues/skins/<name>.png` se usa ese archivo (skin de 64×64 o la antigua de 64×32);
  `skins/<name>.yml` puede decir `model: slim` y `cape: <file>`.
- CA2: Si no hay archivo y el dueño activó `network.mojang: true` en `config.yml` (**apagado por defecto**,
  constitución II.4), la skin, su modelo (clásico o delgado) y la capa oficial se piden a Mojang por el nombre de la
  cuenta, sin congelar el servidor, y se guardan en caché (`cache/`) para no volver a pedirlas durante
  `network.cache_hours`.
- CA3: Con la red apagada y sin archivo, el mensaje dice exactamente dónde poner el PNG o qué opción activar.
- CA4: Una cuenta que no existe, un servidor de Mojang que no contesta o una imagen inválida (tamaño incorrecto, no
  es PNG, demasiado grande) dan un mensaje claro y no construyen nada; el servidor sigue normal.
- CA5: Solo se baja de los servidores de Mojang (nombre → perfil → textura); el plugin no baja ni ejecuta código.

**H3 — Las dos capas de la skin, unidas.** Como *administrador*, quiero que la segunda capa (sombrero, chaqueta,
mangas, pantalón) se vea en relieve y pegada a la primera, no flotando.
- CA1: Cada píxel opaco de la segunda capa se convierte en bloques justo por fuera de la superficie de la primera,
  sin hueco entre las dos, como el mod *3D Skin Layers*.
- CA2: Un píxel transparente de la segunda capa no pone nada (se ve la primera); uno semitransparente cuenta como
  opaco si pasa del umbral de `config.yml`.
- CA3: Cuando la estatua es tan baja que la segunda capa no tiene espacio propio, su color tapa el de la primera en
  ese lugar (como se ve desde fuera).
- CA4: La segunda capa se puede apagar por estatua (`--no-overlay`) o en `config.yml`.

**H4 — Poses configurables.** Como *administrador*, quiero declarar poses en un archivo (cabeza, cuerpo, brazos y
piernas, con codos y rodillas que se doblan) y elegir una al construir.
- CA1: `poses.yml` declara poses con nombre; cada parte (`head`, `body`, `right_arm`, `left_arm`, `right_leg`,
  `left_leg`) acepta giros en grados (`pitch`, `yaw`, `roll`) y los brazos y piernas además `elbow` / `knee` (cuánto
  se dobla la mitad de abajo).
- CA2: Un brazo o pierna doblado no deja huecos ni grietas en el codo o la rodilla, y la skin sigue en su lugar en
  las dos mitades.
- CA3: El plugin trae poses de ejemplo que funcionan tal cual: `standing` (la de por defecto), `waving`, `walking`,
  `victory` (brazos arriba), `pointing` y `sitting`.
- CA4: Una pose con errores (parte o clave desconocida, ángulo que no es número o fuera de −180…180) se informa al
  cargar con archivo, ruta, causa y «¿quisiste decir…?»; las demás poses siguen cargando.
- CA5: `/statue poses` lista las poses cargadas; un nombre de pose desconocido en `build` sugiere el más parecido.
- CA6: Ninguna parte de la estatua queda por debajo de sus pies (2 bloques arriba del jugador): si la pose baja algo
  (p. ej. `sitting`), la estatua entera se sube lo necesario.

**H5 — La capa (cape).** Como *administrador*, quiero que la estatua lleve la capa del jugador si la tiene.
- CA1: Si la cuenta tiene capa oficial (con la red activada) o `skins/<name>.yml` nombra un archivo de capa, la
  estatua la lleva en la espalda, de los hombros hacia abajo, con su textura de frente y de espaldas.
- CA2: La pose puede inclinar la capa hacia atrás (`cape: { pitch: 10 }`).
- CA3: Sin capa, o con `--no-cape`, no se pone nada en la espalda.

**H6 — Bloques permitidos (paletas).** Como *administrador*, quiero decidir con qué bloques se construye la estatua
(solo lana, solo terracota, todos…) escribiendo sus ids, y que con esos bloques se aproximen los colores.
- CA1: El plugin conoce el color de cada bloque sólido de un color (concreto, lana, terracota, madera, piedras,
  minerales…) en un catálogo de colores (`block-colors.yml`: id de bloque → `#RRGGBB`, medido de su textura y
  editable).
- CA2: `palettes.yml` declara paletas con nombre como **lista de bloques permitidos** (*whitelist*) por id
  (`minecraft:white_wool` o `white_wool`), con comodines (`*_wool`, `*_terracotta`) y con exclusiones (`-pink_wool`).
  Trae `all` (todos los del catálogo, la de por defecto), `wool`, `concrete` y `terracotta`.
- CA3: `default_palette` en `config.yml` dice cuál se usa si no se pide otra, y `/statue build … --palette <name>` usa
  otra; `/statue palettes` las lista con cuántos bloques tiene cada una.
- CA4: El color más parecido se elige solo entre los bloques permitidos y como lo ve el ojo (no solo por la distancia
  de RGB), para que pieles y pelos no salgan de colores raros.
- CA5: Un id que no existe en el servidor, que no está en el catálogo de colores o que no es un bloque sólido, y un
  comodín que no encuentra ninguno, se informan al cargar con archivo, ruta y «¿quisiste decir…?»; una paleta que se
  queda sin bloques no se carga.

**H7 — Construir sin trabar el servidor.** Como *dueño del servidor*, quiero que una estatua grande no baje el TPS.
- CA1: Los bloques se ponen por tandas (`blocks_per_tick` y un tope de milisegundos por tick en `config.yml`), con el
  avance en la barra de acción de quien la pidió; el cálculo de la figura no corre en el hilo principal.
- CA2: Varias estatuas pedidas a la vez se construyen una tras otra; `max_blocks` limita el tamaño de cada una.
- CA3: `/statue cancel` detiene la construcción en curso y deja lo ya puesto deshacible como cualquier estatua.

**H8 — Deshacer y quitar, sin perder el terreno.** Como *administrador*, quiero quitar una estatua y recuperar
exactamente lo que había.
- CA1: Al construir, los bloques que había en cada lugar (de cualquier tipo, con su estado: orientación, agua…) se
  guardan antes de reemplazarlos.
- CA2: `/statue undo` quita la última estatua del que la ejecuta; `/statue remove <id>` una cualquiera (permiso
  aparte para las de otros); los dos devuelven los bloques que había.
- CA3: Un bloque de la estatua que alguien cambió después no se toca al quitarla (no se borra lo que construyó otro);
  el mensaje dice cuántos quedaron.
- CA4: Las estatuas y lo que guardaron sobreviven a un reinicio y a `/statue reload`; los archivos se escriben de
  forma atómica (constitución II.6).
- CA5: `/statue list` muestra las estatuas (número, jugador, altura, pose, lugar, quién la hizo) y `/statue info
  <id>` el detalle.

**H9 — Administración.** Como *dueño del servidor*, quiero un plugin con permisos mínimos, configuración validada y
mensajes en inglés (ADR 0005).
- CA1: Permisos `skinstatues.build`, `skinstatues.remove`, `skinstatues.remove.others` y `skinstatues.admin`, todos
  `default: op`.
- CA2: `/statue reload` recarga `config.yml`, `messages.yml`, `poses.yml`, `palettes.yml`
  y `block-colors.yml`; un archivo con errores
  deja lo que ya funcionaba y dice archivo, ruta y causa.
- CA3: Al primer arranque se crean los archivos de configuración, las poses y la paleta de ejemplo y una skin de
  ejemplo propia (no de Mojang) en `skins/`, con la que una estatua sale sin activar la red.
- CA4: Mensajes en `messages.yml`, en inglés y traducibles, con MiniMessage; un jugador sin permiso no ve los subcomandos en el
  autocompletado.
- CA5: Diagnóstico desde la consola: `/statue validate` lista los problemas de la configuración y `/statue selftest`
  construye y quita una estatua de prueba con la skin de ejemplo, y comprueba que el terreno quedó igual.

## Fuera de alcance (y por qué)
- **Armadura y objetos en las manos:** spec siguiente. Sus texturas son de Mojang y no pueden ir en el jar; se
  dibujarán con diseños propios por material y, si el dueño pone un resource pack suyo en la carpeta del plugin, con
  esas texturas (decisión del dueño, como hace el Minecraft Animator con el Minecraft instalado).
- Ojos, boca o caras personalizadas (lo tiene el Minecraft Animator; el dueño no lo quiere aquí).
- Estatuas rellenas, giros que no sean norte/sur/este/oeste, tramado de colores (*dithering*), entidades de
  visualización en vez de bloques, animaciones y NPC.
- Protección de regiones (WorldGuard) y límites por jugador: solo construye quien tiene el permiso (OP por defecto).
- Spigot y Folia (constitución VI).

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿De dónde sale la skin (y la capa)? | Cuenta de Mojang por nombre, con la red apagada hasta que el dueño la active, **y** un PNG local |
| 2 | ¿Qué entra en esta spec? | Núcleo + poses (codos y rodillas), las 2 capas unidas, capa, altura, paleta, deshacer y límites. Armadura y objetos en la mano en la spec siguiente |
| 3 | ¿Cómo se dibujan armadura y objetos? (spec siguiente) | Diseños propios por material **y** el resource pack del dueño si lo pone en la carpeta del plugin |
| 4 | Nombre y comando | **SkinStatues**, `/statue` |
| 5 | ¿Dónde se construye y hacia dónde mira? | 2 bloques arriba de la posición del jugador (para no quedar atrapado), mirando hacia donde mira el jugador |
| 6 | Si ya hay bloques donde va | Reemplaza y guarda lo que había; `undo`/`remove` lo devuelven |
| 7 | ¿Hueca o rellena? | Hueca primero |
| 8 | ¿Quién puede construir? | Solo OP por defecto (`default: op`); altura máxima en `config.yml` (128) |
| 9 | Ojos y boca personalizados (como el Minecraft Animator) | No |
| 10 | ¿Idioma del plugin? | **Inglés** (mensajes, configuración, comandos, poses, README del plugin), y desde ahora todos los plugins nuevos ([ADR 0005](../../../adr/0005-plugins-en-ingles.md)); los documentos de la fábrica siguen en español |

## Preguntas abiertas
- (ninguna: aprobada el 2026-11-01)

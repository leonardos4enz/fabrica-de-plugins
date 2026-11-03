# Spec 039 — Worldcraft: los comandos de WorldEdit con dos barras (`//set`, `/brush`…)

**Estado:** Implementado y verificado en Paper 1.21.4 (compuertas 1, 2 y 3a; 46/46 dos veces) (ver [verify](verify.md)) · CLARIFY respondido
por el dueño (2026-11-03, abajo) · **Plugin:** Worldcraft (0.13.0 → 0.14.0) · **Depende de:** [025](../025-worldcraft-nucleo/spec.md)
(comandos), [028](../028-worldcraft-paridad-worldedit/spec.md)–[036](../036-worldcraft-regen-snapshots/spec.md) (lo que ya
hace Worldcraft con los nombres de WorldEdit), [038](../038-worldcraft-publicacion/spec.md) (la tabla de equivalencias)
· **Origen:** pedido del dueño tras probar la 0.13.0 en un servidor aparte (`run-juego/`): «quiero que choque con world
edit, osea, quiero que ya todos usen // para worldcraft para los comandos tambien».

## Problema
- Quien viene de WorldEdit escribe `//set stone`, `//copy`, `/brush sphere stone 4`. En Worldcraft eso no existe: hay que
  aprender `/wc set`. En `run-juego/` se arregló a mano con 90 alias de `commands.yml`, solo en ese servidor y sin las
  banderas cortas (`//paste -a`).
- Si WorldEdit o FAWE también están, sus `//` ganan: el dueño quiere que ganen los de Worldcraft.

## CLARIFY (respuestas del dueño, 2026-11-03)
| Pregunta | Respuesta |
|---|---|
| ¿Los `//` vienen prendidos al instalar o se prenden en `config.yml`? | **Prendidos de fábrica**; una opción de `config.yml` los apaga (y `/wc` sigue igual) |
| Con WorldEdit o FAWE instalados, ¿quién se queda con los `//`? | **Worldcraft**: se los quita y lo dice en la consola; una opción se los deja a WorldEdit |
| ¿Los argumentos como en WorldEdit (banderas cortas `-a -e -s`) o solo los nombres? | **Como en WorldEdit**: `//paste -a -e`, `//stack 5 up -s`, `//copy -e`, además de `--air`, `--entities` |

## Costo estimado (lección 004)
| Pieza | Costo |
|---|---|
| Tabla de comandos de WorldEdit (nombres, alias y banderas de los jars 7.3.19 y FAWE 2.16.2) dentro del jar; `//x` para cada `/wc x` | ~25 % |
| Registro con el mapa de comandos de Paper: quitarle los suyos a WorldEdit/FAWE (y devolvérselos), sin tocar los de otros plugins ni los del juego, un solo `syncCommands()` | ~30 % |
| Banderas cortas y valores de fábrica de WorldEdit (`//paste` pega el aire y deja las entidades, como WorldEdit) | ~20 % |
| Pruebas: unitarias de la tabla y la traducción; en el juego con WorldEdit 7.3.19 instalado | ~25 % |

## Historias

**H1 — Los comandos de WorldEdit son de Worldcraft.**
- CA1: Con Worldcraft instalado (y sin cambiar nada) funcionan con dos barras **todos** los comandos de `/wc`
  (`//set`, `//replace`, `//wand`, `//pos1`, `//copy`, `//paste`, `//undo`, `//sphere`, `//generate`, `//mountain`…), salvo
  `reload`, `validate` y `selftest` (siguen solo en `/wc`), y los nombres y alias de WorldEdit y FAWE que Worldcraft tiene:
  los de una barra (`/brush`, `/br`, `/tool`, `/snow`, `/up`, `/butcher`, `/schem`, `/snapshot`, `/sp`, `/ex`…), los alias
  (`//re`, `//g`, `//desel`, `//p`, `//cp`, `//mv`, `//calc`…) y `//` solo (el superpico, como en WorldEdit).
- CA2: Hacen lo mismo que `/wc` con el mismo permiso, mensajes, límites, vista previa e historial. Un jugador sin el
  permiso no los ve en el autocompletado ni en el árbol de comandos. El autocompletado es el de `/wc`.
- CA3: Un nombre que ya tiene otro plugin (no WorldEdit/FAWE) o el juego se respeta: Worldcraft no lo toma y lo dice
  en la consola una vez («kept /remove: it belongs to EssentialsX; use /wc remove»).

**H2 — Con WorldEdit o FAWE instalados.**
- CA1: Con `slash_commands.worldedit: take` (de fábrica) Worldcraft toma los nombres de WorldEdit/FAWE al terminar de
  arrancar el servidor (aunque WorldEdit cargue después) y lo dice en la consola (cuántos y que WorldEdit sigue en
  `/worldedit:/set`). El autocompletado de `//set` es el de Worldcraft, no el de WorldEdit.
- CA2: Con `worldedit: leave`, WorldEdit se queda con los suyos y Worldcraft toma solo los libres; lo dice en la consola.
- CA3: Un jugador con `worldcraft.use` pero sin los permisos de WorldEdit ve igual `//set` (WorldEdit quita del árbol
  del cliente los comandos que cree suyos).
- CA4: Al apagar Worldcraft (o con `enabled: false` y `/wc reload`) WorldEdit recupera sus nombres.

**H3 — Los argumentos como en WorldEdit.**
- CA1: Las banderas cortas de WorldEdit valen con dos barras, juntas o separadas (`-ae` = `-a -e`): `//copy -e -b -m
  <máscara>`, `//paste -a -o -s -n -e -b` (FAWE `-x`), `//stack -s -a -e -b`, `//move -s -a -e -b`, `//chunk -s -c`,
  `//outset -h -v`, `//distr -c -d`, `//drain -w`, `//setbiome -p`, `/biomeinfo -t -p`, `//regen -b -r`, `//generate -h -r -o
  -c`, `//ores -d -b`, `/butcher -p -n -g -a -b -t -f -r -w`, `//sphere -h` (= `//hsphere`), `//cyl -h`, `//pyramid -h`.
- CA2: Los valores de fábrica son los de WorldEdit: `//paste` y `//stack` ponen también el aire y `//paste` no trae las
  entidades del portapapeles; `-a` deja el aire, `-e` trae las entidades (en `/wc` sigue lo de Worldcraft: sin el aire,
  con las entidades). `//replace dirt stone` sin `with` ya funcionaba.
- CA3: Un número negativo o una fórmula (`-5`, `-x>0`) no se toman por banderas; una letra que el comando no tiene se
  deja pasar tal cual (el comando dice qué espera).

**H4 — Configuración, seguridad y documentación.**
- CA1: Sección `slash_commands` en `config.yml` (`enabled`, `worldedit`, `skip`) revisada por `/wc validate`; un
  `config.yml` de la 0.13.0 sin ella carga igual (todo de fábrica). `/wc reload` aplica un cambio sin reiniciar.
- CA2: `/wc selftest` comprueba que `//set` es de Worldcraft (si están prendidos).
- CA3: README de Worldcraft (los `//` arriba, la tabla «Coming from WorldEdit» con `//`), README raíz, página de
  presentación y roadmap. El número de comandos no cambia (102: son los mismos comandos con otro nombre).

## Fuera de alcance (al [roadmap](../../roadmap.md))
Los comandos de WorldEdit que Worldcraft todavía no tiene (`//curve`, `//fall`, `//lay`…: siguen siendo «Unknown
command»), `//pos` con dos puntos, `//undo <n> <jugador>` (en Worldcraft: `--player`), `/mask` y `/size` sueltos (en
Worldcraft van dentro de `/wc brush`), las banderas de WorldEdit que Worldcraft no tiene (`//paste -v`, `//sphere -r`,
`//line -h`, `//snow -s`, `/green -f`, `/up -f -g`), ayudas con `//` en los textos de `messages.yml`.

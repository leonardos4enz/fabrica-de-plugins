# Plan 039 — Los comandos de WorldEdit con dos barras (Worldcraft 0.14.0)

Spec: [spec.md](spec.md) · Respuestas del dueño: prendidos de fábrica, Worldcraft se queda con los `//` aunque esté
WorldEdit, banderas cortas como en WorldEdit. Solo API de Paper 1.21.4, sin NMS.

## 1. Diseño

### 1.1 La tabla (`src/main/resources/slash-commands.yml`, leída del jar)
- Sale del **bytecode** de WorldEdit 7.3.19 y FAWE 2.16.2 (`javap -v`, anotaciones `@Command` con `name`/`aliases` y
  `@Switch`/`@ArgFlag` con su letra; lección 029), cruzada con la tabla «Every WorldEdit command» de la 038: solo los que
  Worldcraft tiene.
- Una entrada por nombre **como se escribe sin la primera barra** (`/set` es `//set`, `brush` es `/brush`, `/` es `//`):
  `to` (el comando de `/wc` con sus primeras palabras: `sel clear`, `tool navwand`, `set air`), `aliases`, `flags`
  (letra → opción de Worldcraft: `entities`; `!air` quita una de fábrica; `/hsphere` cambia de comando; `""` se acepta y
  no hace nada) y `defaults` (opciones de fábrica de WorldEdit: `//paste` lleva `air` y `no-entities`).
- Además, `//x` para **cada** subcomando de `/wc` (`WorldcraftCommand.subcommands()`) salvo `reload`, `validate` y
  `selftest`: lo que dice la tabla manda sobre lo generado. Así un comando nuevo de `/wc` tiene su `//` sin tocar la tabla.
- Va en el jar (no se copia a la carpeta del plugin): un dueño que actualiza recibe la tabla nueva (lección 022/019 F-01).

### 1.2 Traducir (`command/SlashTable`, Java puro)
- `translate(entrada, args)` → los argumentos de `/wc`: las palabras de `to`; cada argumento que es `-letras`
  (`^-[A-Za-z]+$`) y cuyas letras están **todas** en `flags` se cambia por sus opciones (`--entities`, en el mismo lugar:
  `-m <máscara>` queda `--mask <máscara>`); lo demás pasa igual (`-5`, `-x>0`, una letra que no está). Al final, los
  `defaults` que ninguna bandera quitó y que no se escribieron.
- `complete(entrada, args)`: lo mismo sin `defaults` (el autocompletado completa la última palabra).

### 1.3 El registro (`paper/SlashCommands`)
- `SlashCommand extends org.bukkit.command.Command implements PluginIdentifiableCommand` (nombre = la etiqueta,
  `setPermission` = el permiso del subcomando: Paper lo esconde del árbol a quien no lo tiene). `execute` → `translate` y
  `WorldcraftCommand.onCommand(sender, /wc, "wc", args)` (los mismos permisos, mensajes y obras); `tabComplete` →
  `WorldcraftCommand.onTabComplete`. `getPlugin()` = Worldcraft: el oyente de autocompletado de WorldEdit
  (`AsyncTabCompleteListener` mira `getCommandOwner(etiqueta)`) deja de contestar por los nuestros.
- `apply(ajustes)`: arma lo deseado (nada si `enabled: false`; sin lo de `skip`). Si es igual a lo registrado, no hace
  nada (lección 004 F-01). Si cambió: quita los nuestros (por clave, juntando antes: lección 001 F-03) y devuelve a
  WorldEdit lo que le quitamos; después, por cada etiqueta, mira `getKnownCommands().get(etiqueta)`:
  - libre → registra (`register(etiqueta, "worldcraft", cmd)`; también queda `worldcraft:/set`);
  - de WorldEdit/FAWE (su `PluginIdentifiableCommand` o una clave `worldedit:`/`fastasyncworldedit:` con el mismo objeto)
    → con `take`, lo guarda, lo saca de la etiqueta (sigue en `/worldedit:/set`; WorldEdit quita el espacio de nombres en
    `rebuildArguments`) y registra el nuestro; con `leave`, no;
  - de otro plugin, del juego o un alias de `commands.yml` → no lo toca.
  Al final **un** `syncCommands()` (por reflexión, como OmniEvents; sin `updateCommands()` aparte) y una línea en la
  consola: cuántos, cuántos de WorldEdit, y los que quedaron de otros.
- Cuándo: en `onEnable` con `runTask` (el primer tick, cuando ya cargaron todos los plugins, WorldEdit incluido, y ya se
  pusieron los alias de `commands.yml`); en `/wc reload` en el momento; en `onDisable`, `unregisterAll()` (y devuelve los
  de WorldEdit) antes de lo demás, dentro del `try`.
- WorldEdit escucha `PlayerCommandSendEvent` y quita del árbol del cliente lo que cree suyo y el jugador no puede usar
  (`/set` sin `worldedit.region.set`). Un oyente de Worldcraft en `HIGHEST` vuelve a poner las etiquetas nuestras cuyo
  permiso el jugador sí tiene (el árbol del servidor ya las tiene: Paper solo borra lo que falta en la colección).

### 1.4 Mensajes y ayuda
- `help-footer` de `messages.yml`: «Every command also works like in WorldEdit: //set, //copy, /brush…». Un
  `messages.yml` de la 0.13.0 conserva su pie viejo (README, lección 032).
- `selftest`: si están prendidos, `//set` es un `SlashCommand` de Worldcraft (o dice quién lo tiene y por qué).

## 2. Contrato de configuración (`config.yml`)
```yaml
# WorldEdit's commands for Worldcraft: //set, //copy, //wand, /brush, /up... and // before any /wc command (//mountain).
# Short flags work like in WorldEdit (//paste -a -e, //stack 5 up -s). /wc stays as it is.
slash_commands:
  enabled: true           # false = only /wc
  worldedit: take         # with WorldEdit or FastAsyncWorldEdit installed: take (Worldcraft gets //set; WorldEdit's stay
                          # as /worldedit:/set) or leave (WorldEdit keeps them; Worldcraft takes only the free ones)
  skip: []                # names Worldcraft leaves alone, as you type them: ["//up", "/remove"]
```
`worldedit` que no es `take` ni `leave` avisa con «did you mean» y usa `take`; `skip` con un nombre que no está en la
tabla avisa; claves desconocidas igual. Un `config.yml` de la 0.13.0 sin la sección carga igual (todo de fábrica).

## 3. STRIDE
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| **E**levación: `//set` saltea el permiso de `/wc set` | Edición sin permiso | `//x` llama al mismo `onCommand` de `/wc`, que revisa el permiso del subcomando; además `setPermission` lo esconde del árbol |
| **T**ampering: Worldcraft se queda con comandos de otro plugin (`/remove` de un plugin de entidades, `/restore` de otro) | Se rompe el otro plugin | Solo se toman nombres **libres** o de WorldEdit/FAWE; los demás se respetan y se anotan en la consola |
| **T**: el dueño no quiere perder un comando concreto | — | `skip` y `worldedit: leave`; `enabled: false` deja solo `/wc` |
| **D**enegación: registrar en caliente | `ConcurrentModificationException` en `Commands.sendAsync` (004 F-01) | Se registra solo si cambia el conjunto y se manda el árbol **una** vez (`syncCommands`) |
| **R**epudio | — | Paper anota cada comando como se escribió; la obra queda en el historial con su dueño, como con `/wc` |
| **I**nformación | — | La línea de la consola solo dice nombres de comandos y plugins |

## 4. Pruebas
- Unitarias (`SlashTableTest`): la tabla del jar se lee sin avisos; toda entrada apunta a un subcomando de `/wc` que
  existe y cada opción de `flags`/`defaults` es una opción que ese subcomando conoce; `//x` para cada subcomando salvo
  los de administración; las traducciones (`-ae`, `-m mask`, `!air`, `/hsphere`, `-5` y `-x>0` sin tocar, `defaults`,
  `complete` sin `defaults`); los nombres de WorldEdit 7.3.19 de la tabla de la 038 marcados «yes» están todos.
  `ConfigTest`: la sección `slash_commands` (de fábrica, valores malos, `skip` desconocido). Que fallen con el código
  anterior (sin la clase ni la sección).
- Compuerta 2: `selftest` (que `//set` sea de Worldcraft) y la línea de la consola, sin WorldEdit.
- Suite 3a (con WorldEdit 7.3.19 instalado, como desde la 027): los escenarios que usan WorldEdit pasan a
  `worldedit:/pos1`, `worldedit:/copy`, `worldedit:schem`… (prueba también que WorldEdit sigue ahí). Escenarios nuevos:
  `//pos1`, `//pos2`, `//set`, `//replace dirt stone`, `//undo`, `//sel` (autocompletado con `magic`, que WorldEdit no
  tiene), `//copy -e` + `//paste -a`, `//sphere -h`, `/br none`, la línea de la consola «from WorldEdit»; con
  `worldedit: leave` y `/wc reload`, `//pos1` vuelve a ser de WorldEdit y `//mountain` sigue siendo de Worldcraft; Beto
  (solo `worldcraft.use`, sin permisos de WorldEdit) recibe `//set` en el árbol de comandos y lo usa.

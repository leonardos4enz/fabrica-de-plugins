# Plan 011 — Diseño técnico

## 1. Arquitectura

### 1.1 Carga en dos pasos (`rule/RuleLoader.java`, `rule/Pack.java` y `rule/Templates.java`, nuevos)
`RuleLoader.loadFolder` deja de compilar archivo por archivo y pasa por tres fases:
1. **Paquetes.** Recorre `rules/` y marca como paquete cada carpeta con `pack.yml`. Lee cada `pack.yml` a un `Pack`
   (nombre, versión, descripción, autor, `enabled`, `params`, `requires`, migraciones, carpeta, reglas, plantillas).
   Errores con la ruta `pack.yml` + clave: clave desconocida, nombre que no es `[a-z0-9_-]{1,32}`, versión que no es
   `x.y.z`, `params` que no es mapa, nombre repetido, `pack.yml` dentro de otro paquete. Luego `requires` (versión de
   OmniEvents que recibe el cargador y versiones de otros paquetes) hasta un punto fijo: si un paquete requerido no
   carga, el que lo requiere tampoco. Los archivos de un paquete que no carga se saltan con un solo error.
2. **Entradas.** Lee cada archivo de reglas a entradas crudas (`Entry`: nombre completo, archivo, paquete, mapa). En
   un paquete el nombre es `paquete:clave` y una clave con `:` es un error; fuera de un paquete, una clave con `:` es
   un aviso (se reserva para paquetes) y carga como hoy (IV.5). Nombres únicos en todo el plugin, como hoy.
3. **Compilación.** Las entradas con `template: true` quedan como plantillas (no se compilan solas). Para las demás,
   `Templates.expand` arma el mapa final y `parse` lo compila como siempre (con el `Pack` en el contexto). Al final,
   `checkStateRefs` resuelve los `in_state('x')` con la misma búsqueda relativa.

`Templates.expand(entry)`:
- `extends: x` busca la plantilla con la regla de nombres (§1.2) desde el paquete de la regla; una regla que no es
  plantilla, una que no existe (con «¿quisiste decir?»), un ciclo o más de 8 niveles son errores de `regla.extends`.
- Mezcla de arriba hacia abajo: cada nivel reemplaza las claves que escribe, enteras. `template`, `extends`, `with`,
  `params` y `tests` no se heredan.
- Parámetros (si la regla está en un paquete, usa `extends` o declara `params`): `${n}` se reemplaza en todos los
  textos del mapa final (valores, también dentro de listas y mapas; no en las claves). Busca `n` en `with` de la
  regla, `params` de la regla y de cada plantilla (de la más cercana a la más lejana), `params` del paquete de la regla
  y del de cada plantilla. Un texto que es exactamente `${n}` toma el valor con su tipo; `\${` deja `${`. Un nombre
  sin valor → error de la ruta exacta (`regla.actions[1].message`) con «¿quisiste decir?». Los valores no se vuelven a
  reemplazar (sin ciclos).
- `with` sin `extends` es un aviso. El resultado se convierte en `MemoryConfiguration` para `parse`.

### 1.2 Nombres relativos (`Rule.pack`, `RuleManager.resolve`)
- `Rule` gana `pack` (nombre del paquete o `null`) y `shortName`.
- `Rule.resolve(String nombre, Rule desde, Function<String, Rule> buscar)`: con `:` es absoluto; si no, y `desde` es de
  un paquete, prueba `paquete:nombre` y luego `nombre`. `RuleManager.resolve(nombre, desde)` lo usa sobre las cargadas.
- Lo usan: `call` (`RuleManager.call` con la regla del contexto, y el `Sandbox` de las pruebas), `cooldown()`, `uses()`
  e `in_state()` en `ExecContext.service`, las dependencias entre estados en `StateService.register` y
  `checkStateRefs`. `/omni` y PlaceholderAPI reciben nombres completos (sin regla de origen): sin cambios.
- Los datos (enfriamientos, usos, estados) se guardan con el nombre completo: una regla global no cambia de clave.

### 1.3 Versión, requisitos y migraciones (`util/Versions.java`, `rule/PackVersions.java`, nuevos)
- `Versions.parse("1.2.0")` (`x.y.z`, también `x.y`), `compare`, `satisfies(versión, ">=1.2.0, <2.0.0")` con `>=`, `>`,
  `<=`, `<`, `=` y sin operador = `=`. Una restricción inválida es un error de `requires`.
- `migrations:` en `pack.yml`: mapa versión → lista de acciones. Cada una se compila como una regla `call` interna
  (`paquete:@migración-1.2.0`, fuera del índice de reglas) para reusar `ActionParser`, `Execution` y la regla de
  nombres; una versión inválida o una acción con errores es un error del paquete.
- `PackVersions` lee y escribe `data/packs.yml` (`packs: {recompensas: "1.2.0"}`) con temporal + reemplazo atómico.
- En `OmniEvents.reload` (no en `validate`), después de registrar los servicios, se programa para el tick siguiente
  (antes que `server_start` al arrancar): por cada paquete cargado con `enabled: true`, si no hay versión guardada se
  guarda; si la nueva es mayor, corren en orden las migraciones con `guardada < v <= nueva` (`var.from`, `var.to`,
  `world` el primero) y se guarda la nueva; si es menor, aviso y nada. Una línea por paquete que cambió:
  `Paquete recompensas: 1.1.0 → 1.2.0 (2 migraciones)`. Un error en una migración va al log y la siguiente corre.

### 1.4 Comandos
- `/omni pack list`: `● recompensas 1.2.0 · 3 reglas · 1 plantilla` (o `○` inactivo, `✘ no carga: …`).
- `/omni pack info <paquete>`: descripción, autor, carpeta, parámetros, requisitos, migraciones, versión guardada,
  reglas y plantillas.
- `/omni pack enable|disable <paquete>`: `setEnabled` en todas sus reglas y el mismo re-registro que
  `/omni enable` (oyentes, temporizadores, estados, comandos); dura hasta el `reload`.
- Permiso `omnievents.command.pack` (`op`); autocompletado de subcomandos y paquetes. Los demás comandos ya aceptan el
  nombre completo (`recompensas:diaria`): el autocompletado lista los nombres completos.

### 1.5 Registro, esquema y referencia
- `RuleOptions`: `template` (BOOLEAN), `extends` (TEXT), `with` (`Kind.PARAMS`, mapa), `params` (`Kind.PARAMS`).
  `PACK_KEYS` con la descripción de cada clave de `pack.yml` y `REQUIRES_KEYS`.
- El esquema de reglas acepta una regla sin `trigger` si tiene `extends` o `template: true`. Un esquema nuevo
  `omnievents-pack.schema.json` para `pack.yml`, generado de `PACK_KEYS` (en `SchemaFiles.render`). La referencia gana
  la sección «Paquetes y plantillas».

## 2. Contrato de configuración
```yaml
# rules/recompensas/pack.yml
name: recompensas                 # opcional (por defecto, el nombre de la carpeta): [a-z0-9_-]
version: 1.2.0                    # x.y.z (por defecto 1.0.0)
description: Recompensas diarias
author: Leo
enabled: true                     # false: sus reglas cargan desactivadas y no corren migraciones
params: { moneda: pvar.monedas, diaria: 50 }
requires:
  omnievents: ">=1.6.0"
  packs: { plantillas: ">=1.0.0" }
migrations:                       # corren una vez al subir a esa versión (var.from, var.to)
  "1.2.0":
    - log: "recompensas {var.from} → {var.to}"
    - set: "gvar.recompensas_temporada = 2"

# rules/recompensas/reglas.yml (nombres: recompensas:base, recompensas:diaria, recompensas:semanal)
base:
  template: true
  params: { cantidad: 1 }
  trigger: command
  actions:
    - set: "${moneda} += ${cantidad}"
    - message: "<green>+${cantidad}: llevas {${moneda}}"
    - call: entregar               # busca recompensas:entregar y luego entregar
diaria:
  extends: base
  with: { cantidad: "${diaria}" }  # valor de with: texto que se reemplaza con los params del paquete
  command: diaria
  cooldown: 1d
```
| Clave | Dónde | Tipo | Validación |
|---|---|---|---|
| `name` | `pack.yml` | texto | `[a-z0-9_-]{1,32}`, único |
| `version` | `pack.yml` | texto | `x.y.z` o `x.y` |
| `description`, `author` | `pack.yml` | texto | — |
| `enabled` | `pack.yml` | booleano | — |
| `params` | `pack.yml`, regla | mapa nombre → valor | nombres `[A-Za-z0-9_-]` |
| `requires.omnievents` | `pack.yml` | restricción | `>=1.6.0, <2.0.0` |
| `requires.packs` | `pack.yml` | mapa paquete → restricción | el paquete existe y carga |
| `migrations` | `pack.yml` | mapa versión → acciones | versión válida; acciones que compilan |
| `template` | regla | booleano | — |
| `extends` | regla | texto | una plantilla (búsqueda relativa), sin ciclos, ≤ 8 niveles |
| `with` | regla | mapa nombre → valor | aviso sin `extends` |

`with` también admite `${n}`: se reemplaza con el resto de las fuentes (el paquete, la plantilla), nunca consigo mismo.

Sin cambios en `config.yml` ni en `data/` (salvo el archivo nuevo `data/packs.yml`). Una carpeta sin `pack.yml` y una
regla sin `extends`/`params` cargan exactamente como en 1.5.0 (IV.5): no hay nada que migrar.

## 3. Hilos y rendimiento
- Todo se resuelve al cargar (III.2): herencia y parámetros dan un mapa que se compila igual que antes; un disparo no
  ve paquetes ni plantillas. La búsqueda relativa de `call`, `cooldown()`, `uses()` e `in_state()` cuesta a lo sumo una
  búsqueda más en un mapa.
- `data/packs.yml` se lee y escribe en la recarga (como las reglas), con escritura atómica. Las migraciones corren en
  el hilo principal, con los topes de bucles y `call` de siempre (III.4).
- Sin oyentes ni tareas nuevas.

## 4. Persistencia
`data/packs.yml`: `packs: { <paquete>: "<versión>" }`. Temporal + `ATOMIC_MOVE`; si falta o está roto, se toma como
vacío (aviso) y no corre migraciones de más: solo guarda la versión actual. Los enfriamientos, usos y estados de las
reglas de un paquete se guardan con el nombre completo (`recompensas:diaria`).

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación | Un paquete copiado define `vip:dar` o una regla global con el mismo nombre que otra para que un `call` ajeno la use | Lo absoluto (`p:x`) siempre gana; lo relativo solo mira el paquete de la regla y luego lo global; nombres únicos con error al chocar |
| Manipulación | Un parámetro con texto de un jugador abre etiquetas o comandos | Los parámetros se reemplazan al cargar, desde archivos del dueño; lo que llega del jugador sigue pasando por `{expr}` escapado |
| Manipulación | `data/packs.yml` roto hace correr todas las migraciones otra vez | Roto = vacío: solo guarda la versión actual, no corre migraciones |
| Repudio | Una migración cambia variables globales sin rastro | Una línea en el log por paquete actualizado, con versiones y número de migraciones |
| Divulgación | `/omni pack info` muestra parámetros | Permiso `omnievents.command.pack` (`op`) |
| Denegación de servicio | Herencia en ciclo o profunda; un `${}` que se expande sin fin | Ciclos detectados, tope de 8 niveles; los valores de los parámetros no se vuelven a reemplazar |
| Denegación de servicio | Una migración con `while` sin fin al arrancar | Topes de `max-loop-iterations` y `max-call-depth`; el error va al log y la carga sigue |
| Elevación de privilegios | Un paquete compartido trae `console: op …` en una migración | Es configuración del dueño (como cualquier regla); `/omni pack info` lista las migraciones antes de subir la versión; nada se descarga solo (II.4) |

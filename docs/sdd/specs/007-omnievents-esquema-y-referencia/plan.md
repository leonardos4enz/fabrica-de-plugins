# Plan 007 — Diseño técnico

## 1. Arquitectura

### 1.1 Un solo registro, con documentación (H2 CA2)
La documentación vive **en la misma llamada que registra** cada capacidad. Así, agregar algo sin describirlo rompe
una prueba, y nada se documenta a mano en otro lugar.
- **Disparadores** (`TriggerType`): campo `description`, puesto con `.doc("…")` al registrar en `Triggers`. Lo demás
  (grupo, raíces, si se puede cancelar, si es asíncrono) ya está en el registro.
- **Acciones** (`Actions`): `register(nombre, Doc, Factory)`, con `Doc(group, description, value, shape, keys,
  valueKeys, example)`:
  - `value` es el texto de la forma del valor (`MATERIAL;cantidad`);
  - `shape` es `SCALAR` (texto, número o vacío), `ITEM` o `CALL` (texto o mapa con `valueKeys`) y `BRANCHES` (lista
    de ramas `weight`/`do`);
  - `keys` son las claves hermanas que lee la acción (`then`, `else`, `do`, `as`, `location`).

  El grupo sale de la sección de registro (flujo, mensajes, comandos, jugador, mundo, evento).
  `register(nombre, Factory)` sigue siendo la API pública: lo de otros plugins queda en el grupo «otros plugins» con
  una descripción genérica (H3 CA3).
- **Alias** de acciones (`ActionParser.ALIASES`) y de disparadores (hoy un `switch` en `RuleLoader.alias`) pasan a
  mapas públicos de solo lectura.
- **Funciones** (`Functions.Def`): `signature` y `description`. Las internas se registran con
  `doc("round(x[, decimales])", "…", min, max, impl)`, que saca el nombre de la firma. `register(nombre, min, max,
  impl)` sigue siendo la API pública, con firma y descripción genéricas.
- **Opciones de regla**: `rule/RuleOptions.java` (nuevo) tiene la lista ordenada de `Option(name, kind, description,
  values, trigger, aliasOf)`. `RuleLoader.KNOWN_KEYS` se calcula de ella, y `CONDITION_KEYS` y las claves de
  `command` también quedan ahí.

### 1.2 Generadores (paquete `schema`, nuevo)
- Los generadores reciben `Triggers` y `Actions` (las instancias vivas del plugin, o unas nuevas sin servidor) y leen
  `Functions` y `RuleOptions`: disparadores en el orden del registro, acciones y funciones en orden alfabético.
- `SchemaGenerator.generate(triggers, actions)` → JSON Schema draft-07 (Gson con `LinkedHashMap`, impresión bonita y sin
  escapar HTML). Es determinista: la misma entrada da el mismo texto.
- `ReferenceGenerator.generate(triggers, actions)` → Markdown con secciones de opciones de regla, condiciones, disparadores
  por grupo, acciones por grupo (con su ejemplo), modificadores, alias, funciones y raíces comunes. El encabezado
  dice que se genera y con qué comando.
- `SchemaMain.main(carpeta)` escribe los dos archivos (tarea Gradle).
- `SchemaFiles.write(carpeta, mapa)`: escritura atómica (temporal + `ATOMIC_MOVE`, como `TraceExport`) que
  reemplaza el archivo existente.

### 1.3 Forma del esquema
```
raíz: object, additionalProperties: { $ref: rule }        cada clave de primer nivel es una regla
rule: object, required [trigger | type], properties = RuleOptions, additionalProperties: false
      allOf: if trigger = timer → required every; clock → at; command → command; custom → event
conditions: condition | [condition]
condition: string | { expr, deny } | { any (entero ≥ 1), of: [condition], deny }   (additionalProperties: false)
actions: action | [action]
action: string (forma corta) | anyOf de un objeto por acción y alias
  objeto de acción: required [nombre], properties = nombre (forma del valor) + claves de la acción + to/where/chance/when,
                    additionalProperties: false
  forma corta: pattern ^(prefijo de ConditionalEvents)?(nombre|alias)(\s*:…)?$
```
- `trigger` es `enum` de nombres y alias, con `markdownEnumDescriptions` (lo que muestra VS Code al sugerir).
- Las opciones con valores fijos (`priority`, `cooldown_scope`, `limit_scope`, `move_mode`) aceptan minúsculas y
  mayúsculas, como el cargador.
- Las duraciones son número o texto con el patrón de `Durations` (`10s`, `1h30m`, `20t`); `budget` usa el patrón de
  `parseBudgetNanos`; `limit_reset` acepta su lista de valores o una duración; `at` acepta `HH:mm`.
- Los nombres de disparadores y acciones van en minúsculas (el cargador también acepta mayúsculas; el editor marca
  `Message:`). Se documenta.
- Las descripciones de `conditions`, `expr`, `when` y `where` listan las funciones del lenguaje (H1 CA6).
- `$id` es la URL raw de GitHub: `https://raw.githubusercontent.com/leonardos4enz/fabrica-de-plugins/main/docs/omnievents/omnievents.schema.json`.

### 1.4 Tarea Gradle y comando
- `./gradlew :omnievents:schema`: `JavaExec` con el classpath de pruebas (trae la API de Paper) y `SchemaMain` con
  la carpeta `docs/omnievents/`.
- `/omni schema`: en el hilo principal arma los textos con `plugin.triggers()` y `plugin.actions()` (incluye lo que
  registraron otros plugins) y genera los textos. En un hilo asíncrono escribe `omnievents.schema.json` y
  `referencia.md` en la carpeta del plugin. Una escritura a la vez. Responde con las rutas y con la línea
  `# yaml-language-server: $schema=../omnievents.schema.json` (relativa a un archivo en `rules/`; en subcarpetas se
  agrega `../`). Permiso `omnievents.command.schema` (`op`).

### 1.5 Ejemplos
Cada archivo de `rules/ejemplos/` empieza con la línea `# yaml-language-server: $schema=<URL raw>` (H1 CA7).

## 2. Contrato de configuración
No cambia: no hay claves nuevas en las reglas ni en `config.yml`, y `config-version` sigue en 1. Mensajes nuevos en
`messages.yml`: `schema-done` y `schema-busy` (con valor por defecto si faltan).

## 3. Hilos y rendimiento
- Generar es leer el registro y armar textos (unos cientos de KB), solo cuando se pide con `/omni schema`, nunca al
  cargar ni en un evento. La escritura a disco va en un hilo asíncrono (III.2).
- Las descripciones son cadenas constantes: no hay costo al ejecutar reglas.

## 4. Pruebas
- `SchemaTest` (unitarias, sin servidor):
  - todo lo registrado tiene descripción;
  - cada ejemplo de acción carga sin problemas con `ActionParser` y usa todas sus claves documentadas;
  - los ejemplos del plugin validan contra el esquema (CA7);
  - casos negativos (CA5): opción desconocida, acción desconocida en mapa y en forma corta, condición sin `expr` ni
    `any`, `any: 0`, `budget: 5`;
  - la sintaxis de la 006 valida (CA4);
  - `docs/omnievents/*` es igual a lo generado (H2 CA2);
  - una acción de «otro plugin» registrada en caliente aparece en el esquema (H3 CA3).

  El validador es `com.networknt:json-schema-validator` (solo en pruebas). El YAML se lee con SnakeYAML, que ya
  viene con la API de Paper.
- Compuerta 2: `omni schema` por consola y la comprobación de que existen los dos archivos.
- No hace falta escenario en el juego: ninguna capacidad necesita un jugador. La suite 3a corre igual, como
  regresión.

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación | — | Sin cambios: `/omni schema` exige `omnievents.command.schema` (`op`) |
| Manipulación | `/omni schema` pisa un archivo del dueño | Solo escribe dos nombres fijos en la raíz de la carpeta del plugin; nunca toca `rules/`, `data/` ni `config.yml`. Escritura atómica (II.6) |
| Repudio | — | La consola registra quién lo ejecutó (como todo comando) |
| Divulgación | El esquema expone los nombres de acciones de otros plugins | Es información de configuración, no de jugadores; queda en la carpeta del plugin y sin red (II.4) |
| Denegación de servicio | Muchos `/omni schema` seguidos | Una escritura a la vez («ya se está generando»); la generación es barata y no recorre el mundo |
| Elevación de privilegios | Un `$schema` remoto malicioso | El esquema es solo datos para el editor. El plugin no lo lee ni lo descarga, y el `$id` apunta al repositorio propio |

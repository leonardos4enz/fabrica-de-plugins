# Spec 007 — OmniEvents: esquema JSON y referencia generada

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 001, 006 ·
**Origen:** propuesta P3 de la [investigación de plugins exitosos](../../../investigacion/plugins-exitosos.md)
(§4 y §6), elegida por el dueño (2026-10-24)

## Problema
Quien escribe reglas trabaja a ciegas hasta que carga el archivo en el servidor:
- **Sin ayuda en el editor.** No hay autocompletado de disparadores, acciones u opciones, ni aviso de una clave mal
  escrita (`cooldwon`) o de un `any` sin `of`, hasta correr `/omni reload` o `/omni validate`.
- **La documentación se desactualiza.** La lista de disparadores, acciones y funciones del README se escribe a mano;
  cada spec que agrega algo tiene que acordarse de documentarlo, y nada avisa si se olvidó. Es la queja más común de
  Skript, AuraSkills e ItemsAdder.

## Historias
**H1 — Esquema para el editor.** Como *administrador*, quiero un esquema JSON de las reglas para tener autocompletado,
documentación al pasar el ratón y errores en VS Code (extensión YAML de Red Hat) mientras escribo.
- CA1: El esquema describe una regla: cada opción que acepta el cargador, con su tipo, sus valores permitidos
  (`priority`, `cooldown_scope`, `limit_reset`, `move_mode`…) y una descripción en español.
- CA2: `trigger` sugiere cada disparador con su descripción, su grupo y las raíces que ofrece a las expresiones.
- CA3: Cada acción se sugiere con su descripción y la forma de su valor; las acciones de flujo (`if`, `repeat`,
  `while`, `for_each`, `random`) describen sus claves (`then`, `else`, `do`, `as`, `weight`), y todas aceptan los
  modificadores `to`, `where`, `chance` y `when`. También se aceptan la forma corta `"message: hola"` y los alias.
- CA4: Cubre la sintaxis de la spec 006: una condición es un texto o un mapa con `expr` o `any`+`of` y `deny`
  opcional (anidable); `budget` (`0.5ms`, `200us`).
- CA5: Una opción de regla desconocida, una acción desconocida o un mapa de condición sin `expr` ni `any` se marcan
  en el editor.
- CA6: Las expresiones dentro de los textos **no** se validan en el editor (eso sigue en `/omni validate`); la
  descripción de `conditions`, `expr` y `when` lista las funciones del lenguaje.
- CA7: Los ejemplos que trae el plugin no tienen ningún error contra el esquema.

**H2 — Referencia generada.** Como *administrador*, quiero una referencia en Markdown de todo lo que entiende
OmniEvents, que nunca quede desactualizada.
- CA1: Lista cada disparador (grupo, raíces, si se puede cancelar, si es asíncrono), cada acción (grupo, forma del
  valor, claves y un ejemplo), los alias, cada opción de regla y cada función del lenguaje (número de argumentos y
  descripción), con un ejemplo cuando aplica.
- CA2: Sale del mismo registro del código que usa el plugin al cargar (disparadores, acciones, funciones y opciones
  de regla). Una prueba falla si algo registrado no tiene descripción, si el esquema o la referencia publicados no
  coinciden con lo que se genera, o si un ejemplo de la referencia no carga sin errores.

**H3 — Generarlos.** Como *autor del plugin* y como *administrador*, quiero generarlos con un comando.
- CA1: `./gradlew :omnievents:schema` genera `omnievents.schema.json` y la referencia sin servidor.
- CA2: `/omni schema` los escribe en la carpeta del plugin en el servidor, fuera del hilo principal y de forma
  atómica, y responde con la ruta y la línea `# yaml-language-server: $schema=…` para pegar al inicio de un archivo
  de reglas. Permiso `omnievents.command.schema` (`op` por defecto).
- CA3: Las acciones y funciones que registra otro plugin por la API aparecen en lo que genera `/omni schema` (con
  descripción genérica si no dieron una).

## Fuera de alcance
- Validar expresiones, plantillas o nombres de materiales, sonidos y efectos dentro del editor (dependen de la
  versión de Paper): siguen en `/omni validate`.
- Un servidor de lenguaje propio o una extensión de VS Code.
- Las propiedades de los objetos (`player.health`): las da la API de Paper por reflexión.
- El editor visual (P11, descartado por ahora).

## Decisiones (CLARIFY)
| # | Pregunta | Decisión del dueño |
|---|---|---|
| 1 | ¿Número de spec? | 007 (la 003 está reservada para la paridad con ConditionalEvents) |
| 2 | ¿Se publican el esquema y la referencia en el repositorio? | Sí: `docs/omnievents/omnievents.schema.json` y `docs/omnievents/referencia.md`, con una prueba que falla si quedan desactualizados; el `$schema` apunta a su URL raw de GitHub |
| 3 | ¿`/omni schema` escribe en la carpeta del plugin? | Sí: `plugins/OmniEvents/omnievents.schema.json` y `referencia.md`, asíncrono y atómico, con lo que registren otros plugins; no toca los archivos de reglas (responde con la línea para pegar) |
| 4 | ¿Editor estricto? | Sí: opción de regla o acción desconocida es error en VS Code; materiales, sonidos y efectos no se validan |
| 5 | ¿Versión del plugin? | 1.2.0 (comando nuevo; la configuración no cambia) |
| 6 | ¿Capturas? | No (lección de la 004) |

## Preguntas abiertas
- (ninguna)

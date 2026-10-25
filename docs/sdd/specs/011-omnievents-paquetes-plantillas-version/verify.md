# Verify 011 — OmniEvents: paquetes, plantillas y versión

**Fecha:** 2026-10-25 · **Versión:** OmniEvents 1.6.0

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ 143/143 (20 nuevas) | `./gradlew build`: `PaquetesTest` (19: nombres `paquete:regla`; búsqueda relativa de `call` e `in_state`; errores de `pack.yml` con ruta y «¿quisiste decir?»; `:` en el nombre; `enabled: false`; herencia en cadena, ciclo, tope de 8 niveles, plantilla inexistente o que no es plantilla; `${}` con prioridad, tipo, escape y error con ruta; regla global sin cambios; `Versions`; `requires` hasta punto fijo; migraciones en rango, primera carga, bajada, archivo roto, paquete apagado, migración con error; el paquete de ejemplo con sus pruebas) y `SchemaTest` (plantillas en el esquema, `pack.yml` con su esquema, los ejemplos validan). Arnés: 6/6 (`copiarCarpeta`, F-01) |
| 2. Servidor real (1.21.4) | ✔ | `compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni test" "omni pack list" "omni reload" "omni stats" "omni reload" "omni stats"`: 35 reglas, 0 errores, 0 avisos; «Paquete recompensas: primera carga, versión 1.1.0»; autoprueba 14/14; «14 pruebas: todas pasan»; `pack list`: «● recompensas 1.1.0 · 3 reglas · 1 plantillas»; dos `reload` con 10 oyentes, 3 temporizadores, 8 oyentes y 1 tarea de estado, 7 comandos (iguales, ~20 ms); el segundo arranque no repite la primera carga (versión guardada en `data/packs.yml`); log sin errores |
| 3a. En el juego, Paper 1.21.4 | ✔ 39/39 dos veces | 1 escenario nuevo (`011 …`) + los 38 anteriores; antes de F-02, 38/39 |
| 3a. En el juego, Paper 26.1.2 (Java 25) | ✔ 39/39 dos veces | mismo jar; `--paper 26.1.2 --version-bot 26.1` |

### Escenario nuevo
| ID | Qué prueba |
|---|---|
| 011 H1 CA1 · H1 CA2 · H2 CA3 · H3 CA2 · H4 CA1 · H4 CA3 | El paquete de la suite `e2epack` (requiere `recompensas`): `/e2epaquete` sale de una plantilla con `${variable}` y `with`, y su `call: avisar` llega a `e2epack:avisar` (no al `avisar` de `recompensas`); la `pvar` real queda en 3; `/omni pack list` muestra los dos paquetes; `disable` apaga el comando y `enable` lo devuelve (6); con `data/packs.yml` en 1.0.0, un `reload` corre la migración 1.1.0 una vez (log y `gvar`) y el siguiente `reload` no la repite |

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (paquetes, nombres relativos, errores, comandos con nombre completo) | `PaquetesTest` (H1_*), escenario 011, compuerta 2 |
| H2 (plantillas, herencia, parámetros, sin cambios en reglas de antes) | `PaquetesTest` (H2_*), ejemplo `recompensas`, escenario 011 |
| H3 (`requires`, migraciones, versión guardada) | `PaquetesTest` (H3_*), escenario 011 (migración en el servidor), compuerta 2 (primera carga y segundo arranque) |
| H4 (`/omni pack list|info|enable|disable`) | Escenario 011 (`list`, `enable`, `disable`), compuerta 2 (`list` por consola) |
| H5 (registro, esquemas, referencia, ejemplo con `tests:`) | `SchemaTest`, `PaquetesTest.H5_CA2`, `EjemplosPruebasTest` (14 casos) |

No se probó en el juego `/omni pack info` (solo lee lo que ya cubren las unitarias) ni bajar de versión en el
servidor (cubierto por `PaquetesTest.H3_CA2`); riesgo bajo.

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Media | La suite copiaba los ejemplos con `copiarCarpeta` (`tests-e2e/correr.mjs`), que no entraba en subcarpetas: con el paquete de ejemplo en `ejemplos/recompensas/`, `copyFileSync` de una carpeta reventaba (EPERM) y la suite no arrancaba | `copiarCarpeta` pasó a `tests-e2e/lib/servidor.mjs`, recursiva. La prueba del arnés «copiarCarpeta copia también las subcarpetas» falló con el código anterior (comprobado) y pasa con la corrección. Lección en `CLAUDE.md` |
| F-02 | Baja | El escenario 011 borraba la `pvar` por consola y el bot usaba el comando enseguida: el borrado se aplicó después (la consola no espera) y la variable quedó nula | `srv.sincronizar()` después de preparar, como pide la lección 002 F-03. El escenario falló en la primera corrida completa (38/39) y pasa con la corrección |

## Revisión exploratoria (3b, agente)
- **Compatibilidad (IV.5):** una carpeta sin `pack.yml` y una regla sin `extends`/`params` se compilan con su sección
  original (la misma ruta de código que en 1.5.0); los 38 escenarios anteriores y los ejemplos de siempre pasan igual.
  Un nombre con `:` fuera de un paquete solo avisa. No hay migración de datos: `data/packs.yml` es nuevo.
- **Rendimiento:** herencia y parámetros se resuelven al cargar; el `reload` siguió en ~20 ms con el paquete. La
  búsqueda relativa cuesta a lo sumo una consulta más a un mapa. Sin oyentes ni tareas nuevas.
- **Seguridad:** `/omni pack` con `omnievents.command.pack` (`op`); los parámetros vienen de archivos del dueño y lo que
  llega del jugador sigue escapado por `{expr}`; un `data/packs.yml` roto no hace correr migraciones de más.
- **Límites conocidos (documentados):** mover una regla a un paquete cambia su nombre y deja atrás sus datos; `\${`
  deja `${` pero en un mensaje la `{` que sigue se interpola como siempre (para texto literal, `\{`); los nombres
  relativos de una plantilla de otro paquete se buscan desde el paquete de la regla que la hereda.

## README
- `plugins/omnievents/README.md`: sección «Paquetes, plantillas y versión» (`pack.yml`, plantillas, parámetros,
  nombres, migraciones, `/omni pack`), filas en «Opciones de una regla», comando en la lista y Compatibilidad (1.6.0).
- `README.md` raíz: fila de OmniEvents en 1.6.0 y `omnievents-pack.schema.json` en la estructura. Roadmap e índice
  (`docs/sdd/README.md`) con la 011. Esquemas y referencia regenerados con `./gradlew :omnievents:schema`.
- Capturas: ninguna nueva (CLARIFY 5).

# Verify 014 — OmniEvents: paridad con ConditionalEvents

**Fecha:** 2026-10-25 · **Versión:** OmniEvents 1.9.0

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ 182/182 (12 nuevas) | `./gradlew build`: `ParidadCeTest` (5: `centered_message` y `firework` en `tests:`; `firework` mal escrito da error de carga con ruta y «¿quisiste decir?»; `item_repair` con `given`, `cancel` y raíces; `cooldown_bypass` y el permiso automático con `isPermissionSet` —un OP sin el permiso no lo salta, `cooldown-bypass-op` sigue—; registro), `CenteredTest` (4: anchos, negrita, color heredado, varias líneas, línea demasiado larga), `DataStoreTest.S014_H4_CA3_*` (el reinicio de todos toca solo esas claves de los desconectados) e `ImporterTest` (2 nuevas: F-01 y una configuración de CE con cada fila con equivalente, que carga sin errores y sin REVISAR). El ejemplo `estilo_ce.yml` (3 casos) pasa en `EjemplosPruebasTest` y valida contra el esquema regenerado |
| 2. Servidor real (1.21.4) | ✔ | `compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni test" "omni pack list" "omni menu list" "omni reload" "omni stats" "omni reload" "omni stats"`: 54 reglas (las 2 del ejemplo nuevo), 0 errores, 0 avisos; autoprueba 14/14; «28/28 pasan»; `menu list`: «Menús (2, 0 abiertos)»; dos `reload` con 12 oyentes (uno nuevo: `item_repair`), 3 temporizadores, 8 oyentes y 1 tarea de estado, 11 comandos (`/celebrar`), 5 oyentes de regiones y 5 de menús (iguales); segundo arranque igual; log sin errores |
| 3a. En el juego, Paper 1.21.4 (build 232, Java 24) | ✔ 49/49 dos veces | 4 escenarios nuevos (`014 …`) + los 45 anteriores |
| 3a. En el juego, Paper 26.1.2 (build 74, Java 25) | ✔ 49/49 dos veces | Mismo jar |

### Escenarios nuevos
| ID | Qué prueba |
|---|---|
| 014 H1 CA1 | `centered_message` con una línea en negrita y otra normal: Ana recibe «e2e centrado» con 28 espacios delante y «segunda línea e2e» con 26 (los calculados a mano con la fuente) |
| 014 H2 CA1 · H2 CA2 | Bajo un techo de vidrio, `firework` lanza un cohete (el bot ve la entidad) que explota encima de Ana sin quitarle vida; el mismo con `damage:true` sí la lastima (la prueba distingue los dos casos) |
| 014 H3 CA1 · H3 CA2 | Ana renombra una espada en el yunque («e2e reparar ANVIL 'Hoja e2e' costo 1», la espada queda renombrada y paga 1 nivel); con «bloqueada» la regla cancela: no paga niveles y la espada no cambia; en la afiladora quita un encantamiento («GRINDSTONE '' costo 0») |
| 014 H4 CA1 · H4 CA2 · H4 CA3 | Con los permisos dados por `permissions.yml`: `omni cooldown reset * *` responde con las reglas y los jugadores; `e2e_bypass` (permiso automático) y `e2e_bypass_propio` (`cooldown_bypass`) corren dos veces seguidas; la regla de control entra en enfriamiento aunque Ana es OP; `omni check` dice «libre (permiso omnievents.bypasscooldown.e2e_bypass)»; `omni cooldown reset e2e_sin_bypass *` la libera |

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (`centered_message`) | Escenario 014 H1, `CenteredTest`, `ParidadCeTest` |
| H2 (`firework`) | Escenario 014 H2, `ParidadCeTest.H2_*` |
| H3 (`item_repair`) | Escenario 014 H3, `ParidadCeTest.H3_*`, el ejemplo `nombre_reservado` |
| H4 (permiso y `reset *`) | Escenario 014 H4, `ParidadCeTest.H4_*`, `DataStoreTest.S014_*` |
| H5 (importador) | `ImporterTest.S014_H5_*` y `F01_*` |
| H6 (registro, esquema, ejemplos, README) | `SchemaTest` (esquema al día, los ejemplos validan), `EjemplosPruebasTest`, README |

No se probó en el juego: `reset *` sobre archivos de jugadores desconectados (la unitaria lo cubre sobre una carpeta
temporal), `location` de `firework` y el shift-clic del resultado en el yunque. Riesgo bajo.

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Media | El importador traducía `%player%` como `player.name` también dentro de una acción `to_…`, donde en OmniEvents `player` es el destinatario: `to_range: 10;false: message: %player% te saluda` decía a cada jugador su propio nombre. Además ignoraba el `false` de `to_range` (el jugador del evento también lo recibía) | Dentro de `to`, `%player%` → `source.name`, los placeholders se resuelven para `source` y `%to:x%` para el destinatario; `to_range: r;false` agrega `where: player != source`. `ImporterTest.F01_*` falló con el importador de la 1.8.1 (comprobado) y pasa con la corrección |
| F-02 | Baja | `centered_message` era un alias de `message` en el parser (desde la 001): la acción nueva quedaba tapada y no centraba | Se quitó el alias; lo cubre `ParidadCeTest.H1_H2_*` (el registro de la prueba muestra `centered_message`) |
| F-03 | Baja (arnés) | EssentialsX reemplaza `/enchant` («Solo jugadores…» en la consola) y en 26.x `reload` es el de Brigadier (sin `permissions`) | La suite usa `minecraft:enchant` y `bukkit:reload permissions`; lección en `CLAUDE.md` |
| F-04 | Baja (arnés) | Reconectar a Ana en el escenario H4 dejaba la bienvenida (con `delay`) corriendo: su `title` llegaba después de la expulsión del escenario siguiente y el log tenía un error | El escenario espera el título «Bienvenido» antes de seguir |

## Revisión exploratoria (3b, agente)
- **Compatibilidad (IV.5):** una configuración de la 1.8.1 carga igual (los 45 escenarios anteriores pasan). Único cambio
  de comportamiento: quien tenga **dado** `omnievents.bypasscooldown.<regla>` salta ese enfriamiento; nadie lo tiene por
  defecto, ni los OP (`isPermissionSet`).
- **Seguridad:** `rename_text` es texto del jugador y se escapa al interpolar como cualquier otro (II.2). `reset * *` pide
  `omnievents.command.cooldown` (`op`) y solo quita claves de `cooldowns`/`uses`. Los cohetes sin daño llevan una marca
  en su PDC; el oyente que cancela su daño se registra con el primer cohete (III.1).
- **Rendimiento:** `centered_message` mide en memoria; `reset *` lee y escribe los archivos de desconectados fuera del
  hilo principal (III.2). Carrera conocida y documentada (plan §1.5): si un jugador entra justo mientras se recorre su
  archivo, su reinicio puede perderse.
- **Jar de CE:** bajado del release oficial 4.65.1 a `tests-e2e/.cache/ce/` (fuera de git), sha256
  `15847ebb0e17dc3ed95409b1b16b1e69948ce3ac62837f68c551961eec307d92`; analizado con `javap` sin ejecutarlo.

## README
- `plugins/omnievents/README.md`: sección nueva «Desde ConditionalEvents» (qué se traduce, qué no y por qué),
  `centered_message`, `firework`, `item_repair`, `cooldown_bypass`, `reset` con `*`, specs y Compatibilidad (1.9.0).
- `README.md` raíz: fila de OmniEvents en 1.9.0. Roadmap (fila 014, diagnóstico y «paridad con CE, segunda parte»)
  e índice (`docs/sdd/README.md`). Esquemas y referencia regenerados con `./gradlew :omnievents:schema`.
- Capturas: ninguna nueva.

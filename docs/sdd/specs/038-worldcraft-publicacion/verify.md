# Verify 038 — Página de presentación y publicación de Worldcraft

**Fecha:** 2026-11-02 · **Resultado:** Verificado (compuertas 1, 2 y 3a) en Paper 1.21.4; Worldcraft 0.13.0 sin cambios
de código. **Publicado** con el Chrome del dueño (su permiso, como en la 024): SpigotMC y Hangar públicos; Modrinth
completo como borrador, falta el clic «Submit for review» (ver «Publicación»).

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew build` en verde: OmniEvents 209, SkinStatues 69, Worldcraft **303** (297 + 6 de `PresentacionTest`). La prueba nueva falló 5 de 6 antes de escribir las descripciones (solo la tabla de `commands.md` existía) y una vez más con la línea «not affiliated» partida en dos renglones en `commands.md` (corregido) |
| Servidor real (compuerta 2) | `bash tools/compuerta-servidor.sh worldcraft "wc validate" "wc selftest" "wc help mountain" "wc limits"` (Paper 1.21.4 build 232, Java 24, con WorldEdit 7.3.19 y el plugin de ejemplo): «Configuration OK»; todos los selftest (núcleo, entidades, herramientas, naturaleza, fórmulas, creación, copias, API); sin errores en los dos arranques |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **43/43 dos veces seguidas** (tick más largo de la obra de 36 000 bloques: 8,8 y 8 ms). Una corrida anterior dio 42/43 (12,2 ms) porque corrían a la vez las unitarias de gradle: F-01 |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1 | `javap -v` sobre las clases `*Commands` de los jars de la caché (sha256 fijado): 171 en WorldEdit 7.3.19; 265 en FAWE 2.16.2, menos 21 `AnvilCommands` = 244 (igual que la 029) |
| H1 CA2 | Cada comando marcado a mano frente a la 0.13.0: WorldEdit 129 / 5 / 28 / 4 / 5; FAWE 142 / 12 / 68 / 14 / 8 (lo tiene / en parte / falta / no se hará / no hace falta); los scripts del recuento quedaron en el scratchpad, el resultado en `commands.md` y §5 de la investigación |
| H1 CA3 | 102 = entradas de `help:` sin `sp`; 140 contando 19 pinceles, 10 herramientas, 5 de `schem`, 5 de `snapshot` y 3 modos del superpico, sin los 4 grupos |
| H1 CA4 | `docs/worldcraft/presentacion/commands.md` (los 102 por grupo; las 171 y las 79 filas de FAWE con su equivalente o motivo) |
| H1 CA5 | `PresentacionTest`: el 102 sale de `messages.yml` y tiene que estar en las dos descripciones, en `commands.md` y en el póster; la tabla de `commands.md` es exactamente la lista de comandos; todo `/wc x` nombrado existe |
| H2 CA1–CA3 | `docs/site/assets-src/worldcraft.html` → `docs/img/worldcraft/presentacion/` (logo de 512 px y 7 franjas): bloque de pasto con un bloque de oro que sale de la selección y la varita, dibujado en el HTML; franjas 4 (comparación) y 5 (cantidades) |
| H3 CA1–CA2 | `tests-e2e/worldcraft/presentacion.mjs`: `terrain` (`mountain large` y `island large` desde la consola), `shapes` (esfera, cilindro hueco, pirámide, cono), `image` (el logo con `/wc image` del bot); `plugins/worldcraft/run-capturas/` (puerto 25597), mundo nuevo en cada corrida, obras deshechas al terminar |
| H4 CA1–CA3 | `spigotmc.bbcode`, `modrinth-hangar.md`, `publicar.md`; solo 1.21.4 marcada |
| H4 CA4 | Subido con la sesión del dueño en su Chrome, sin escribir credenciales: ver «Publicación» |

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Baja (proceso) | La suite midió 12,2 ms en un tick (presupuesto 8) mientras gradle corría unitarias en paralelo | Repetida sola: 8,8 y 8 ms, 43/43 dos veces. Lección en `CLAUDE.md`: nada pesado en paralelo con la suite |
| F-02 | Baja (arnés) | Desde la consola, una montaña de 720 000 bloques esperaba `/wc confirm` aunque llevara `--now` (es lo que dice el plugin: `--now` salta la vista previa, no la confirmación) y la escena se colgaba | La escena manda `wc confirm` cuando el plugin lo pide |
| F-03 | Baja (arnés) | El visor de las capturas, mirando una obra grande en vivo, quedó con secciones a medio actualizar (montaña en tajadas, isla invisible); y tras el `undo` de una escena, la siguiente veía bloques viejos en el cielo (el servidor sí los deshizo: «33,956 blocks back as they were») | La cámara se va lejos mientras se construye y vuelve; cada escena en su propia corrida con `--solo` |

## Publicación
El dueño pidió subirlo («Sube el plugin… a spigot, modrinth y hangar»). El navegador integrado no tenía su sesión; al
volver, eligió «Use my Chrome» y se subió con sus sesiones abiertas, sin escribir credenciales. En los tres: solo
`Worldcraft-0.13.0.jar`, solo Paper 1.21.4 (SpigotMC: 1.21), MIT, el logo y las descripciones de `docs/worldcraft/presentacion/`.

| Sitio | Enlace | Estado |
|---|---|---|
| SpigotMC | https://www.spigotmc.org/resources/worldcraft.140223/ | Público (Spigot → World Management, ícono de 96 px) |
| Hangar | https://hangar.papermc.io/leonardos4enz/Worldcraft | Público (World Management; palabras clave sin «WorldEdit») |
| Modrinth | https://modrinth.com/plugin/worldcraft-editor | **Borrador completo** (versión 0.13.0 para Paper 1.21.4, utility y worldgen, MIT, enlaces, galería con las 3 escenas y la comparación, declaración de IA: código, recursos y texto). Falta que el dueño pulse **Submit for review** en *Settings*: a las 22:00 la API de Modrinth tardaba ~10 s por consulta y el envío (`PATCH /v3/project`) se cortó por tiempo dos veces |

| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-04 | Baja (publicación) | En Modrinth, el slug `worldcraft` estaba tomado (no lo devuelve la API pública: es de un proyecto que no se ve) | Se usó `worldcraft-editor`, el de reserva de `publicar.md` |
| F-05 | Baja (publicación) | Modrinth no dejaba enviar a revisión: «Your … link could not be verified» para cada enlace a GitHub, que sí responden 200. Su lista revisa los enlaces **desde el navegador** con la API de GitHub sin autenticar (60 consultas por hora por IP), y la IP del dueño ya las había gastado (`api.github.com/rate_limit`: 0 restantes) | Esperar a que se renueve el límite y volver a cargar la página (después de las 21:52 los enlaces pasaron); lección en `CLAUDE.md` y `publicar.md` |
| F-06 | Baja (publicación) | Con los enlaces ya verificados, el botón esperaba la respuesta de `GET /v3/project/…/validate` (más de 30 s) y el envío a revisión terminó en «TimeoutError»: la API de Modrinth estaba lenta | Queda para el dueño: un clic en *Submit for review* cuando Modrinth responda |

## README
Plugin: el póster, las cifras y el enlace a `commands.md` arriba; la compatibilidad dice que la 0.13.0 se volvió a
verificar antes de publicar. Raíz: la fila de Worldcraft con las cifras, la estructura y la sección «Página de
presentación» con los comandos de Worldcraft. Investigación (§5), roadmap y `CLAUDE.md`. Capturas: solo las 3 escenas
de esta spec, con `--solo`.

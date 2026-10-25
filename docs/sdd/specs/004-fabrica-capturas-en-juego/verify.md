# Verificación 004 — Fábrica: documentación con imágenes generadas en el juego

**Fecha:** 2026-10-24 · **Resultado:** Aprobada (con el hallazgo F-01 de OmniEvents pendiente para otra spec)

| Compuerta | Resultado |
|---|---|
| Unitarias | `node --test lib/*.test.mjs lib/capturas/*.test.mjs`: **14/14** (arnés 4 + renderizador 10: componentes y traducciones, recorte, comparación por píxeles, YAML de reglas, anclas, lector de ZIP, anchos de la fuente real) |
| Servidor real | Sin cambios en OmniEvents: la compuerta 2 de la 001/002 sigue vigente (`gradlew :omnievents:dist` compila en cada corrida de capturas) |
| En el juego (3a) | `npm --prefix tests-e2e test`: **21/21 dos veces** en Paper 1.21.4 build 232 después de los cambios a `servidor.mjs` y `bot.mjs` |
| Capturas (esta spec) | `npm --prefix tests-e2e run capturas`: 147 escenas, **0 fallas** y cobertura completa contra el servidor vivo en cada corrida completa. Las diferencias inesperadas entre corridas seguidas bajaron de 30 a 0: cada una se rastreó a su causa (F-05) y se corrigió, o se marcó `varía` cuando es natural (resultado abajo) |
| Revisión exploratoria (3b) | Revisión a ojo de cada imagen durante el desarrollo (prototipo aprobado por el dueño y las 133 imágenes finales): hallazgos abajo |
| README | OmniEvents: imagen de bienvenida y enlace a la galería al inicio, imágenes de `diamantes_contados`, `title`, `bossbar`, `/omni eval`, `debug` y `validate`; cada tabla de acciones y disparadores enlaza a su grupo de la galería; `wait_ticks` agregado (faltaba). Raíz: enlace a la galería, estructura (`docs/<plugin>/galeria.md`, capturas) y sección «Galería con imágenes». `CLAUDE.md` (comandos, regla de capturas al cierre, lecciones) y `verificacion.md` (capturas). Capturas regeneradas |

## Resultado de las dos corridas finales
| Corrida completa | Iguales | Cambiaron (varía) | Inesperadas | Solo texto | Fallaron | Qué se corrigió después |
|---|---|---|---|---|---|---|
| 3 | 121 | 7 | 5 | 14 | 0 | orbes de experiencia y animación de muerte, agua animada, flecha en vuelo |
| 4 (seguida) | 123 | 7 | 3 | 14 | 0 | ídem |
| 5 | 123 | 8 | 2 | 14 | 0 | puntos de experiencia recogidos antes de la foto |
| 6 y 7 (con compilación + seguida) | 122 / 123 | 10 / 9 | 1 / 1 | 14 | 0 | `set_damage`: orbes de la vaca |
| 8 | 122 | 10 | 1 | 14 | 0 | `keep_inventory`: el tp con la mirada se perdía al reaparecer |
| 9 | 121 | 10 | 2 | 14 | 0 | `golpe_critico` (azar de golpes) y `debug` (bloque recién roto): marcadas `varía` |

Cada corrección se comprobó con `--solo` repetido (`igual` en la 2.ª y 3.ª vez) y, para `keep_inventory`, también
dentro de la corrida completa (corrida 9: `igual`). En la corrida 9 las únicas diferencias no marcadas fueron las dos
que después se marcaron `varía`: no quedan escenas inestables sin marcar. Falta, como riesgo, una corrida 10 que lo
confirme de punta a punta.

Las escenas marcadas `varía` (y por qué): `ejemplo-spawn_vip` (segundos de enfriamiento), `ejemplo-arranque`
(número de arranque), `comando-reload` (ms), `comando-var-list` (`arranques`), `comando-stats` (tiempos),
`accion-fly`, `accion-velocity`, `accion-launch`, `accion-summon` (posición en el aire o criaturas con IA),
`accion-random` (azar), `disparador-player_move`, `disparador-player_sprint`, `disparador-player_food_change`,
`disparador-player_shoot` (en movimiento), `disparador-bucket_empty` (agua animada), `ejemplo-golpe_critico`
(golpes al azar), `comando-debug` (bloque recién roto). Tamaño: 133 PNG, **3,5 MB**.

## Cobertura
| Criterio | Prueba |
|---|---|
| H1 CA1 un comando | `npm --prefix tests-e2e run capturas`: compila, instala, copia reglas de demostración, arranca, recorre, apaga en `finally` |
| H1 CA2 salida estable | `docs/img/omnievents/<escena>.png` con id estable; recortes por zona (`hud.mjs` `recortar`, unitaria «H1 CA2») |
| H1 CA3 deja el servidor como estaba | `limpiar` (deop, sin entidades, escenario borrado, spawn y gamerules originales) y retiro de `rules/capturas/`; la suite pasa 21/21 justo después |
| H1 CA4 `--solo` | Usado para rehacer escenas sueltas; cada escena prepara su estado (`antesDeCada`) |
| H2 CA1 render de lo recibido | `capturas.mjs` `foto()`: componentes de `Jugador.vistos` y estado del bot; `hud.mjs` (fuente, chat, barra, títulos, bossbar, HUD, efectos, armadura, mano izquierda, inventario y cofre con tooltip, desconexión, consola) |
| H2 CA2 mundo 3D | `visor.mjs`: prismarine-viewer con los chunks y entidades del bot, en Chrome sin interfaz, solo en 127.0.0.1 |
| H2 CA3 nada inventado | 14 escenas de solo texto con motivo (sonidos, partículas, relámpago, ítems tirados, fuego, escarcha, proxy, paquete de recursos, mesa de encantamientos, azar o tiempos de ejemplos); entidades sin modelo filtradas |
| H2 CA4 aviso en cada imagen | Cabecera de la galería y del README del plugin |
| H2 CA5 assets locales | `assets.mjs` lee `versions/1.21.4/1.21.4.jar` y el índice de assets a `tests-e2e/.cache/` (en `.gitignore`); sin cliente, error con la ruta; no se lee ningún archivo de cuentas |
| H3 CA1 una entrada por capacidad | 61 disparadores, 63 acciones y 19 subcomandos (los que dice el servidor) + opciones |
| H3 CA2 ejemplos | Las 19 reglas de `rules/ejemplos/` (15 con imagen, 4 de solo texto: azar, `timer` de 10 min, `clock` 22:00, permiso VIP) |
| H3 CA3 cobertura contra el servidor vivo | `cobertura()` lee `/omni triggers`, `/omni actions` y `/omni help` de la consola; el comando falla si falta una |
| H3 CA4 galería generada | `galeria.mjs`: índice por grupo y por nombre, imagen, texto alternativo, YAML de la regla (unitaria de `yamlDeRegla`) |
| H4 CA1 comparación | `png.mjs` por píxeles; unitaria «H4 CA1» (no reescribe si es igual) |
| H4 CA2 `varía` | Marcadas en las escenas; el resumen separa «cambiaron» de «inesperadas» |
| H4 CA3 igual con o sin `--solo` | `antes` espera a que termine la bienvenida; cada escena borra las variables que muestra; corridas `--solo` repetidas dan `igual` |
| H5 CA1 README | Ver fila README |
| H5 CA2 regla de cierre | `CLAUDE.md` «Capturas» y `verificacion.md` «Capturas de la galería» |

## Hallazgos
| ID | Severidad | Descripción | Evidencia | Prueba de regresión | Estado |
|---|---|---|---|---|---|
| F-01 | Media | **OmniEvents**: `/omni enable` y `/omni disable` vuelven a registrar los comandos de reglas y llaman a `syncCommands()` y `updateCommands()` aunque la regla no sea un comando; dos seguidos chocan con el envío asíncrono de Paper (`ConcurrentModificationException` en `Commands.sendAsync`). El jugador puede quedar con el autocompletado viejo | `run/e2e.log` en cada escena que enciende una regla de demostración | Pendiente (código del plugin, fuera de esta spec; propuesta en el roadmap) | Abierto |
| F-02 | Media | El visor se colgaba al reconectar un bot (esperaba cerrar su servidor con la página conectada) y la preparación de la escena no tenía tiempo límite: la corrida quedó parada 14 min | Primera corrida completa, tras `accion-kick` | `Camara.recargar` recarga la página sin cerrar el servidor; `antesDeCada` entra en el tiempo límite. Las corridas siguientes pasan `kick`, `despedida` y `player_kick` | Resuelto |
| F-03 | Media | En 1.21.4, `spawn-monsters=false` (el de la suite) borra al instante hasta los monstruos invocados: `cazador` no veía su criatura | `Summoned new Creeper` en el log y el bot sin la entidad | `propiedades: { 'spawn-monsters': 'true' }` en las capturas (`Servidor.prepararPropiedades(ademas)`, sin tocar las de seguridad); `ejemplo-cazador` en verde | Resuelto |
| F-04 | Baja | mineflayer 4.39 en 1.21.4: agacharse se manda con un número donde el protocolo espera el nombre de la acción; volar con `creative.startFlying` no dispara el evento; en creativo no aplica la corrección del servidor tras una rotura cancelada; no logra usar la mesa de encantamientos | Escenas `player_sneak`, `player_toggle_flight`, `bloque_protegido`, `item_enchant` | Paquetes directos (`entity_action` con `start_sneaking`, `abilities`) y reenvío del bloque real; `item_enchant` como solo texto | Resuelto (salvo la mesa, documentada) |
| F-05 | Baja | Imágenes que cambiaban entre corridas sin motivo visible: orbes de experiencia, animación de muerte, agua, flecha en vuelo, anuncios de progreso del juego en los recortes | Comparación por píxeles (corridas 3–5) | `sinRestos`, `announceAdvancements` solo en su escena, `varía` donde es natural | Resuelto |
| F-06 | Baja | Reglas de demostración mal escritas que la galería habría mostrado: `for_each` sobre una lista de textos (solo recorre jugadores o entidades), `stop` con un permiso que un OP siempre tiene, `RIGHT_CLICK_AIR` (la acción se llama `RIGHT_CLICK`) | Escenas sin respuesta | Reglas corregidas; la escena falla si la regla no responde | Resuelto |

## Lección del dueño (después del cierre)
«No me gustó, tardamos horas en terminar, no vale la pena hacer la galería para todo, solo para cosas puntuales.»
Consecuencias: la regla de cierre de `CLAUDE.md` pasa a «capturas solo puntuales»; la cobertura de la galería es un
aviso (`--cobertura` la vuelve a exigir); las 133 imágenes ya generadas se quedan y no se mantienen al día salvo que
se pidan. Para specs futuras: estimar el costo de un alcance «todo» y proponer el subconjunto de más valor.

## Riesgos residuales
- Solo 1.21.4: prismarine-viewer 1.33 no soporta versiones más nuevas. Las capturas muestran la versión verificada.
- El visor no dibuja bloques con entidad (cofres, camas, carteles, mesas), ítems tirados, relámpagos ni partículas, y
  deja una línea punteada en el borde de algunos chunks (cosmético). Las escenas lo dicen en su texto.
- Depende del cliente instalado del dueño y de Chrome/Edge: en otra PC sin ellos el comando se niega con un mensaje.
- El render no es el cliente oficial: sin mano en primera persona, sin modelo del jugador en el inventario, sin
  iluminación ni nubes.
- Las escenas `varía` cambian en cada corrida y se reescriben: revisar sus diffs no aporta.
- La última corrida completa (9) tuvo dos diferencias que se marcaron `varía` después; no se repitió una corrida
  completa más tras marcarlas.

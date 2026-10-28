# Plan 018 — Diseño técnico

## 1. Arquitectura
Sin código nuevo en el plugin (decisión 2): todo es configuración que ya existe (disparadores `player_move`,
`player_death`, `player_gamemode_change`, `player_toggle_flight`, `player_teleport`, `player_quit`, `player_join`,
`timer`, `command` y `call`; acciones `set`/`unset`, `teleport`, `set_block`, `title`, `actionbar`, `firework`, `sound`;
paquetes con `params` de la 011; `tests:` de la 010).

| Archivo | Qué es |
|---|---|
| `plugins/omnievents/src/main/resources/rules/ejemplos/parkour/pack.yml` | Paquete `parkour`, `enabled: false`, parámetros del juego |
| `plugins/omnievents/src/main/resources/rules/ejemplos/parkour/reglas.yml` | Las 18 reglas del juego, con `tests:` |
| `tests-e2e/omnievents/suite.mjs` | Escenario «parkour de punta a punta» |
| `plugins/omnievents/README.md` | Sección «Cómo hacer un minijuego» |
| `docs/site/minigames.md` (+ `SUMMARY.md`, `mkdocs.yml`) | La misma guía en inglés |

### 1.1 Reglas del paquete
| Regla | Disparador | Qué hace |
|---|---|---|
| `inicio` | `player_move` | Bloque al que llega (`to.block.type`) = `${inicio}`, en supervivencia o aventura y sin volar → guarda la salida y `empezar` |
| `checkpoint` | `player_move` | `${checkpoint}` en partida y no visto → `guardar_punto` y aviso |
| `meta` | `player_move` | `${meta}` en partida → tiempo, récords personal y global, `terminar` (pasado el límite: `perder`) |
| `caida` | `player_move` | `to.y < pvar.pk_y - ${caida}` → `volver_al_punto` |
| `reloj` | `timer` 1 s por jugador | Barra de acción y tiempo límite |
| `muerte`, `cambio_de_modo`, `vuelo`, `atajo` | `player_death`, `player_gamemode_change`, `player_toggle_flight`, `player_teleport` | `perder` con el motivo |
| `desconexion`, `partida_vieja` | `player_quit`, `player_join` | `terminar` (H4) |
| `comando` | `command` `/parkour` (`/pk`) | `salir`, `reiniciar`, `checkpoint`, `record`, `construir`, ayuda |
| `empezar`, `perder`, `terminar`, `guardar_punto`, `volver_al_punto`, `construir` | `call` | Lo común |

Decisiones de diseño (todas con la configuración actual):
- **`to`, no `player.location`** (F-01): en `player_move` Bukkit todavía no movió al jugador; la placa pisada y la altura nueva son `to.block` y `to.y`, y `guardar_punto` las recibe por argumentos.
- **Posición en variables por coordenada** (`pk_mundo`, `pk_x`, `pk_y`, `pk_z`, `pk_giro`): `teleport` separa su valor
  por `;` al cargar, así que una ubicación guardada como un solo texto no se puede pasar entera.
- **La hora en `tests:`**: `given` no evalúa expresiones; las reglas usan `default(var.ahora, now())` y las pruebas fijan
  `var.ahora` (patrón documentado en la guía).
- **Atajos que hacen perder**: las causas `COMMAND`, `ENDER_PEARL`, `CHORUS_FRUIT`/`CONSUMABLE_EFFECT`, `SPECTATE` y los
  portales; `teleport` de OmniEvents usa `PLUGIN`, así que volver al checkpoint no cuenta.
- **El recorrido de prueba** (`construir`) va hacia el este con `~` (relativo al jugador): sin depender del giro, el
  mismo en cualquier servidor.

## 2. Contrato de configuración
El contrato es el `pack.yml` del ejemplo:

```yaml
name: parkour
enabled: false              # viene apagado
params:
  inicio: LIGHT_WEIGHTED_PRESSURE_PLATE
  checkpoint: HEAVY_WEIGHTED_PRESSURE_PLATE
  meta: POLISHED_BLACKSTONE_PRESSURE_PLATE
  limite: 300               # segundos
  caida: 3                  # bloques bajo el último punto
  admin: omnievents.parkour.admin
```
Variables persistentes que deja: `pvar.pk_record` (ms), `pvar.pk_partidas`, `pvar.pk_ganadas`, `gvar.pk_record`,
`gvar.pk_record_nombre`; las de la partida (`pk_activo`, `pk_inicio`, `pk_cps`, `pk_caidas`, `pk_vistos` y la posición)
se borran al terminar, salvo la salida (`pk_s_*`) que usa `/parkour reiniciar`.

## 3. Hilos y rendimiento
Todo en el hilo principal (eventos síncronos y `timer`). `player_move` corre en cada cambio de bloque de cada jugador:
las cuatro reglas que lo usan empiezan por una comparación barata (`player.location.block.type == '…'` o
`pvar.pk_activo == true`) y no siguen si falla. El `reloj` solo hace algo con una partida en curso. Las expresiones se
compilan al cargar, como siempre.

## 4. Persistencia
`data/players/<uuid>.yml` y `data/global.yml`, como cualquier `pvar`/`gvar` (guardado atómico de la 001). Si el
servidor se cae en plena partida, `pk_activo` queda en `true`: `partida_vieja` lo limpia al entrar (H4 CA2).

## 5. Amenazas (STRIDE)
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| Suplantación: un jugador usa `/parkour construir` | Bloques puestos en el mundo por un no administrador | Condición `perm('${admin}')` (los OP lo tienen; un permiso no declarado no vale para el resto); probado en `tests:` |
| Manipulación: llegar a la meta con un atajo (`/tp`, perla, volar, creativo) | Récord falso | `atajo`, `vuelo` y `cambio_de_modo` hacen perder; `inicio` no empieza en creativo, espectador ni volando |
| Manipulación: pisar la meta sin pasar por los checkpoints | Récord falso en un recorrido con atajos de mapa | Límite del ejemplo: lo cuenta la guía (`pvar.pk_cps >= N` en la condición de `meta` si el dueño lo quiere) |
| Repudio: quién batió el récord | Récord sin autor | `gvar.pk_record_nombre` y el anuncio a todos; `desconexion` deja una línea en el registro |
| Divulgación | — | Solo tiempos y nombres de jugadores, que ya son públicos |
| Denegación: `player_move` en un servidor lleno | TPS | Condiciones baratas primero; `/omni cpu` las mide (spec 006); viene apagado |
| Elevación: el paquete corre comandos de consola | — | No usa `console` ni `command`; `set_block` solo en `construir` con permiso |

## 6. Pruebas
- `tests:` en cada regla que lo permite (gradle las corre en `EjemplosPruebasTest`; `RuleLoaderTest` exige que carguen
  sin avisos y `SchemaTest` que validen con el esquema).
- Suite en el juego (3a), escenario «H5 CA2»: `/omni pack enable parkour`, `/parkour construir` (bloques donde deben),
  recorrido de prueba elevado en la zona de trabajo: el bot camina, empieza, pasa el checkpoint, cae y vuelve, llega a la
  meta (récord personal y del servidor), pierde por tiempo (`pk_inicio` adelantado por consola) y por un `/tp`, sale con
  `/parkour salir` y se desconecta en plena partida (al volver no queda nada).

# Verify 012 — OmniEvents: integraciones con costos

**Fecha:** 2026-10-25 · **Versión:** OmniEvents 1.7.0

## Compuertas
| Compuerta | Resultado | Evidencia |
|---|---|---|
| 1. Unitarias | ✔ 155/155 (12 nuevas) | `./gradlew build`: `IntegracionesTest` (12: `cost` todo o nada sobre el `Sandbox` con dinero, niveles, experiencia, ítems, `pvar` y `gvar`; `cost_missing` y `cost_missing_list`; sin saldo = sin economía; devolución en orden inverso cuando falla el retiro, con un bolsillo falso; devolución que falla se informa; cantidades evaluadas una vez; errores de `cost` con ruta y «¿quisiste decir?»; asíncrono y sin jugador; avisos sin Vault, WorldGuard ni Citizens; `give_money`/`take_money` en una prueba; regiones y NPC con `given.regions` y `npc: true`; validación de `given.money`/`inventory`/`regions`; F-01 y F-02). Los ejemplos (`integraciones.yml`, 5 casos) pasan en `EjemplosPruebasTest` y validan contra el esquema regenerado. Arnés: 19/19 (3 nuevas en `lib/plugins-extra.test.mjs`) |
| 2. Servidor real (1.21.4) | ✔ | `compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni test" "omni pack list" "omni reload" "omni stats" "omni reload" "omni stats"`: «Integraciones detectadas: Vault, WorldGuard, Citizens»; 39 reglas, 0 errores, 0 avisos; autoprueba 14/14; «20 pruebas: todas pasan»; `pack list` con `recompensas 1.1.0`; dos `reload` con 11 oyentes, 3 temporizadores, 8 oyentes y 1 tarea de estado, 8 comandos y 5 oyentes de regiones (iguales, 18–20 ms); `stats`: «integraciones: Vault (Essentials), WorldGuard, Citizens»; segundo arranque igual; log sin errores |
| 3a. En el juego, Paper 1.21.4 (build 232, Java 24) | ✔ 42/42 dos veces | 3 escenarios nuevos (`012 …`) + los 39 anteriores. Con Vault 1.7.3, EssentialsX 2.21.0, WorldEdit 7.3.19, WorldGuard 7.0.13 y Citizens 2.0.37-b3760 en el servidor de prueba |
| 3a. En el juego, Paper 26.1.2 (build 74, Java 25) | ✔ 42/42 dos veces | mismo jar; Vault 1.7.3, EssentialsX 2.22.0, WorldEdit 7.4.6, WorldGuard 7.0.18 y Citizens 2.0.44-b4268 |

### Escenarios nuevos
| ID | Qué prueba |
|---|---|
| 012 H1 CA1 · H2 CA1 · H2 CA2 | Con 1000 de EssentialsX, 3 diamantes y una ficha, `/e2ecosto` (300 de dinero, 2 diamantes, `pvar.e2e_fichas: 1`) cobra todo: saldo 700, queda 1 diamante y 0 fichas. La segunda vez falta un diamante y la ficha: no se cobra nada (saldo 700, 1 diamante) y `on_cost_fail` dice «1 × DIAMOND, 1 de pvar.e2e_fichas». `give_money: 50` deja 750 |
| 012 H3 CA1 · H3 CA2 | Dos regiones marcadas con WorldEdit y `rg define` por el bot: teletransportarse dentro dispara `region_enter` (con `regions()` y `in_region()` mirando el destino), salir dispara `region_leave`, y la región con `cancel` deja a Ana donde estaba |
| 012 H4 CA1 · H4 CA2 | Un NPC creado con `npc create … --at`: clic derecho (`npc_click`, con `npc.id` e `is_npc(target)`) y golpe (`npc_left_click`) |

## Criterios
| Historia | Cubierta por |
|---|---|
| H1 (dinero con Vault, sin Vault) | Escenario 012 H1/H2 (EssentialsX real), `IntegracionesTest.H1_*`, compuerta 2 (detección y línea de `stats`) |
| H2 (cost todo o nada, devolución, validación, check y tests) | `IntegracionesTest.H2_*`, ejemplo `tienda_arco` con sus pruebas, escenario 012 H1/H2 |
| H3 (regiones) | Escenario 012 H3, `IntegracionesTest.H3_*`, compuerta 2 (5 oyentes de regiones, iguales tras dos `reload`) |
| H4 (NPC) | Escenario 012 H4, `IntegracionesTest.H3_CA1_CA2_H4_*` y `H3_H4_disparadores_registrados` |
| H5 (registro, esquema, ejemplos, softdepend, README) | `SchemaTest` (esquema al día, ejemplos validan), `EjemplosPruebasTest`, `plugin.yml`, README |

No se probó en el juego: `/omni check` y `--simular` con `cost` (lo cubren el código compartido con el disparo y la traza
vista en la 3a, donde un nombre repetido hizo correr `check` sobre la regla de costo: ver F-02), entrar a una región
caminando (el mismo `update` que el teletransporte, desde `PlayerMoveEvent`) y una devolución real cuando EssentialsX
rechaza un retiro (no se puede provocar sin modificar la economía; cubierto con el bolsillo falso). Riesgo bajo.

## Hallazgos
| ID | Sev. | Hallazgo | Resolución |
|---|---|---|---|
| F-01 | Media | Bukkit lee `pvar.fichas: 1` dentro de `cost` como una sección `pvar` con `fichas` dentro: la clave salía como «pvar» desconocida y el ejemplo no cargaba | `Cost.parse` vuelve a juntar `pvar.*` y `gvar.*`. `IntegracionesTest.F01_pvar_con_punto_dentro_de_cost` falló sin la corrección (comprobado) y pasa con ella |
| F-02 | Media | Dos reglas con el mismo nombre en un mismo archivo: YAML se queda con la última sin avisar y la carga daba 0 avisos. La regla `e2e_tienda` nueva de la suite tapó a la de la spec 006 y cuatro escenarios fallaron sin pista | El cargador avisa «la regla está dos veces en este archivo: solo cuenta la última». `IntegracionesTest.F02_regla_repetida_en_un_archivo_avisa` falló sin el aviso (comprobado) y pasa con él; la regla de la suite se llama `e2e_costo` |
| F-03 | Media (arnés) | EssentialsX reemplaza comandos de Minecraft que la suite manda por consola (`list`, `give`, `tp`, `clear`…) y cambia su salida: 9 escenarios anteriores fallaban con EssentialsX instalado | `configurarEssentials` pone `disabled-commands` en el servidor de prueba con los comandos que la suite necesita de Minecraft (`suite.comandosDeMinecraft`); pruebas del arnés en `plugins-extra.test.mjs`. Lección en `CLAUDE.md` |
| F-04 | Baja (arnés) | En 1.21.4 (Java 24) WorldEdit 7.4.6 no carga (compilado para Java 25) y Citizens b4268 no soporta 1.21.4 (Citizens solo sigue el último parche de cada versión) | Con el visto bueno del dueño, para 1.21.4 van WorldEdit 7.3.19 y Citizens 2.0.37-b3760 (último build con `v1_21_R3`), con su sha256 en `lib/plugins-extra.mjs` |

## Revisión exploratoria (3b, agente)
- **Compatibilidad (IV.5):** una regla sin `cost` sigue el mismo camino que en 1.6.0; los 39 escenarios anteriores pasan
  igual con y sin los plugins de terceros. Sin Vault, WorldGuard ni Citizens las reglas cargan con un aviso (unitarias).
- **Seguridad:** el cobro solo en el hilo principal (asíncrono = error al cargar, y se niega en ejecución); cantidades
  ≤ 0 son error al cargar o de la acción; los ítems se devuelven con las pilas originales; la reflexión de Citizens es
  interna y fija. `npc_click` exige el metadato `NPC`.
- **Rendimiento:** `RegionService` solo con reglas de región activas y WorldGuard (5 oyentes; 0 sin reglas), una
  consulta por cambio de bloque. Recarga en ~20 ms como en la 011.
- **Límites conocidos (documentados):** `cost` no va en disparadores asíncronos (usar `call`); sin jugador solo cobra
  `gvar`; `region_enter`/`region_leave` no ven `__global__`.

## README
- `plugins/omnievents/README.md`: sección «Integraciones: costos, Vault, WorldGuard y Citizens», filas en opciones,
  acciones y disparadores, instalación y Compatibilidad (1.7.0 con las versiones probadas de cada dependencia opcional).
- `README.md` raíz: fila de OmniEvents en 1.7.0 y cómo la suite baja los plugins de terceros. Roadmap e índice
  (`docs/sdd/README.md`) con la 012. Esquemas y referencia regenerados con `./gradlew :omnievents:schema`.
- Capturas: ninguna nueva (CLARIFY 6).

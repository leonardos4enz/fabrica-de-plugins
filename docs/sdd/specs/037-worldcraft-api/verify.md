# Verify 037 — API para otros plugins (Worldcraft 0.13.0)

**Fecha:** 2026-11-02 · **Resultado:** Verificado en Paper 1.21.4 (compuertas 1, 2 y 3a); 26.1.2 sin verificar (como la
025–036). Alcance elegido por el dueño en el CLARIFY: solo la API Java y el evento; sin CoreProtect ni puente con WorldEdit.

## Compuertas
| Compuerta | Resultado |
|---|---|
| Unitarias (compuerta 1) | `./gradlew build`: en verde para los cuatro proyectos (OmniEvents 209, SkinStatues 69, Worldcraft **297** = 290 de la 036 + 7 nuevas: `ApiTest` 4, `EventTest` 2, `ConfigTest` 1; el plugin de ejemplo compila contra el contrato). El paquete `api` no nombra clases internas (ADR 0008); el resultado rechazado y hecho; el dueño de las obras de un plugin es fijo por nombre y no es el de la consola; un plugin edita con `default` y los grupos de `api.plugin_groups` (también hereda los bloques prohibidos); el solapamiento de cajas del evento con esquinas en cualquier orden, pegadas y envolventes; cancelar con motivo y volver a dejar seguir; la sección `api` (un `config.yml` de la 0.12.0 sin avisos, un grupo mal escrito con «did you mean», un valor que no es booleano, clave desconocida) |
| Servidor real (compuerta 2) | `plugins/worldcraft/run/` (Paper 1.21.4 build 232, Java 24, con WorldEdit 7.3.19 y el plugin de ejemplo con una zona protegida en x, z 300–310): «Configuration OK»; `selftest` completo con el paso nuevo: «Selftest API passed: the service is registered (API 1), a plugin edit of 27 blocks was made and undone, and one stopped by a listener changed nothing». Desde la consola: `wcx box` a nombre del plugin (27 bloques, en `/wc history #WorldcraftApiExample`), un patrón mal escrito («did you mean 'stone'»), el tope de `default` (3 917 184 de 50 000, el motivo sin el prefijo), `wcx undo`; `/wc set` y `wcx box` en la zona protegida: «Another plugin stopped this edit: This area is protected by WorldcraftApiExample (demo)»; el registro del evento de fin para obras de la consola, del plugin y de `undo`. Logs sin errores del plugin en los dos arranques (el «No key layers» del mundo plano es del juego, ya filtrado) |
| En el juego (compuerta 3a) | `npm --prefix tests-e2e test -- --plugin worldcraft`: **43/43 dos veces seguidas** (41 de antes + 2 nuevos); tick más largo de la obra de 36 000 bloques 8 ms. La suite instala el plugin de ejemplo (`jarsExtra` de `correr.mjs`) con una zona protegida propia (`tests-e2e/worldcraft/api-example/`) |

## Criterios
| CA | Evidencia |
|---|---|
| H1 CA1 | Compuerta 2 (`WorldcraftApi.get()` en el selftest; el ejemplo dice «Using Worldcraft 0.13.0 (API 1)»); 3a |
| H1 CA2 | `ApiTest` (el paquete solo usa tipos de Bukkit y del JDK); [ADR 0008](../../../adr/0008-api-de-worldcraft.md) |
| H1 CA3 | 3a (`api.enabled: false`: «API refused set: The server owner turned Worldcraft's API off») |
| H2 CA1, CA2 | 3a (`wcx select` → `/wc sel` dice la caja; `wcx selection` la lee igual: `cuboid`, esquinas, 27 bloques) |
| H3 CA1 | 3a (`fill 50% stone, 50% andesite`, `box … glass`, `paste` de un `.schem` guardado por Ana con su origen; `stnoe` y `e2e_ap` con «did you mean»); compuerta 2 |
| H3 CA2 | 3a y compuerta 2 (los futuros dicen número, bloques y estado; el tope rechaza con el motivo) |
| H3 CA3 | 3a (como Ana: su «Done» y `/wc undo` de Ana la deshace; como el plugin: el tope de `default` y su historial); `ApiTest` (grupos) |
| H3 CA4 | 3a y compuerta 2 (`wcx undo` deshace lo último del plugin) |
| H3 CA5 | El código (`Api.start`: `runTask` si no es el hilo principal); no hay un caso en la suite que llame desde otro hilo |
| H4 CA1 | 3a (la zona detiene `/wc set`, `--preview` antes de la vista previa, `wcx box` y el `undo` de una obra que quedó adentro; afuera todo sigue); compuerta 2 (un oyente del selftest detiene un `replace` sin cambiar nada) |
| H4 CA2 | 3a (el registro «Edit #N by #WorldcraftApiExample: set glass, 27 blocks changed, asked by WorldcraftApiExample»); compuerta 2 |
| H5 CA1–CA3 | `plugins/worldcraft-api-example` (comandos, zonas, registro, README); 3a |
| H6 CA1 | `ConfigTest.spec037…`; compuerta 2 («Configuration OK») |
| H6 CA2 / README | README de Worldcraft (sección *For plugin developers* con ejemplos de código, `api` en la configuración, cómo actualizar desde la 0.12.0, «Not yet» sin la API, compatibilidad 0.13.0), README del ejemplo, README raíz (fila 0.13.0 y el ejemplo), tabla de paridad (§3 y §4.2), roadmap, lección en `CLAUDE.md`. Sin capturas nuevas |

## Hallazgos
| # | Qué pasó | Arreglo | Prueba |
|---|---|---|---|
| F-01 | Un plugin que encadena obras (`set(...).thenCompose(r -> undo(1))`) recibía «Wait for your running edit to finish»: el futuro se completaba en `onEnd`, que corre dentro del paso del planificador con la obra todavía en su cola (también contaba para `max_jobs`) | El evento de fin y el resultado van al tick siguiente (`Edits.later`) | El paso API del `selftest` (compuerta 2): con el código anterior «Selftest failed: api: the undo gave … REFUSED … Wait for your running edit to finish»; con el arreglo, «Selftest API passed» |
| F-02 | Armado de la compuerta (no del plugin): el selftest que falló por F-01 dejó 27 bloques de vidrio en su caja y la corrida siguiente falló en «nature: an oak in the air was not captured» | `fill … air` de la caja del selftest antes de la siguiente corrida | La corrida siguiente, en verde |

## Riesgos
- La API no tiene aún máscaras que miran alrededor ni formas, terreno y los demás comandos como métodos: se agregan
  cuando otro plugin los pida (roadmap).
- WorldGuard y PlotSquared siguen necesitando WorldEdit (usan su API); el dueño no quiere puente: el README lo dice.
- Un desarrollador compila contra el jar de Worldcraft (no hay repositorio Maven todavía).
- Paper 26.1.2 sin verificar.

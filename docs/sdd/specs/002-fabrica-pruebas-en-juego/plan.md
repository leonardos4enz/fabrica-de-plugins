# Plan 002 — Diseño técnico de las pruebas en el juego

## 1. Arquitectura
Proyecto Node 22 en `tests-e2e/` (raíz del repositorio), sin dependencias más allá de `mineflayer` ~4.39.0.

```
tests-e2e/
  package.json            npm test = unitarias del arnés + suite en el juego
  correr.mjs              corredor: compila, instala, arranca, corre escenarios, apaga, reporta
  lib/servidor.mjs        Servidor: propiedades seguras, olvidar jugadores, arrancar, consola, log, errores
  lib/bot.mjs             Jugador: bot de mineflayer que registra lo que ve y espera con tiempo límite
  lib/paper.mjs           otra versión de Paper (API Fill + sha256) en run-<versión>/
  lib/servidor.test.mjs   unitarias del arnés (node:test)
  <plugin>/suite.mjs      la suite de un plugin: configuración + escenarios
  <plugin>/reglas/        configuración propia de la suite (YAML del plugin)
```

Flujo de `correr.mjs`:
1. `gradlew :<plugin>:dist` con el JDK 24 (salvo `--sin-build`).
2. Copia el jar de `dist/` a `run/plugins/` (borra versiones anteriores del mismo jar) y la configuración de
   `suite.copiar` (ejemplos del código fuente y reglas de la suite).
3. `Servidor.prepararPropiedades()` (H2) y `olvidarJugadores()` (H1 CA3).
4. Arranca Paper con la salida por tubería (`-Dterminal.jline=false`, UTF-8), espera `Done (`.
5. Comandos `suite.preparar` (reglas del juego deterministas) y `suite.antes()` (conectar bots).
6. Cada escenario corre en serie con tiempo límite; al terminar se revisa el log desde su marca (H1 CA5).
7. `finally`: los bots salen, `suite.limpiar` (quitar OP, limpiar el área), `stop`, retirar lo copiado con
   `retirar: true`. Código de salida 0 solo si todo pasa y el log completo no tiene errores.

Corredor propio (≈150 líneas) en vez de `node:test` para los escenarios: un servidor compartido por todos, orden
fijo, filtro `--solo` por ID y la revisión del log después de cada escenario. `node:test` se usa para las unitarias
del arnés.

### Esperas
Todo lo que el bot ve se guarda en `Jugador.vistos` (`{ tipo, texto, json, t }`); un escenario toma una marca y espera
desde ella. Tipos: `chat`, `actionbar`, `title`, `subtitle`, `bossbar`, `sound` (`#id@tono`), `particle`,
`explosion`, `abilities`, `entity`, `kicked`. Ajustes al protocolo de 1.21.4 que mineflayer 4.39 no cubre:
- la barra de acción de Paper usa el paquete `set_action_bar_text` (`action_bar`), no el chat de sistema;
- un mensaje cambiado por el servidor (`set_message`) llega como contenido sin firmar: se registra ese;
- la tabla de nombres de sonidos de minecraft-data no coincide con el registro de 1.21.4: se reconoce el sonido por
  su tono.

La consola atiende los comandos en orden: `Servidor.sincronizar()` manda `seed` y espera su respuesta, así el bot no
actúa antes de que la preparación termine.

## 2. Contrato de configuración (de la suite de un plugin)
```js
export default {
  jar: /^OmniEvents-.*\.jar$/,               // qué jar de dist/ instalar
  copiar: [                                  // origen (raíz del repo) → destino (run/)
    { de: 'plugins/omnievents/src/main/resources/rules/ejemplos', a: 'plugins/OmniEvents/rules/ejemplos' },
    { de: 'tests-e2e/omnievents/reglas', a: 'plugins/OmniEvents/rules/e2e', retirar: true }
  ],
  datosDeJugador: ['plugins/OmniEvents/data/players/{uuid}.yml'],  // se borran al empezar (además del mundo)
  bots: ['Ana_e2e', 'Beto_e2e'],
  puerto: 25599,
  versionPorDefecto: '1.21.4',
  ruidoDelServidor: [/No key layers/],       // líneas de error que no son del plugin
  erroresDelPlugin: [/\[OmniEvents\] Error/], // avisos que cuentan como error
  preparar: ['gamerule …'],                  // consola, una vez
  limpiar: ['deop …'],                       // consola, al final
  antes: async (ctx) => {},                  // conectar bots
  escenarios: [{ id: 'H5 CA1', nombre: '…', timeoutMs: 60000, fn: async (ctx) => {} }]
}
```
`ctx = { srv, bots, version, entrar(nombre), salir(nombre) }`.

Línea de comandos: `npm --prefix tests-e2e test [-- --plugin p] [--sin-build] [--solo regex]
[--paper 26.1.2 --version-bot 26.1 --java-servidor <JDK>]`.

Reglas de OmniEvents de la suite (`tests-e2e/omnievents/reglas/e2e.yml`): el comando `/e2e <grupo>` llama con `call` a
`e2e_<grupo>`, una regla por grupo de acciones; `e2e_golpe` (`player_attack`), `e2e_megafono` y `e2e_eco_chat`
(`player_chat`). Los ejemplos del plugin se prueban tal cual.

## 3. Hilos y rendimiento
- Un proceso de Node; el servidor es un proceso hijo (Java) con la consola por `stdin` y el log por `stdout`.
- Mundo plano, sin ciclo de día, clima ni aparición de monstruos, sin regeneración natural, dificultad fácil:
  la vida, el hambre y las entidades solo cambian por lo que hace la prueba.
- La suite completa tarda ≈45 s más ≈30 s de arranque de Paper.

## 4. Persistencia
- `run/e2e.log`: log completo de la corrida (fuera del repositorio).
- Al empezar se borran los datos de los bots: `world/playerdata`, `world/stats`, `world/advancements` y, desde 26.1,
  `world/players/{data,stats,advancements}`; además `suite.datosDeJugador`.
- El mundo, los datos globales del plugin y los demás jugadores no se tocan.

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación | Cualquiera entra al servidor de prueba con el nombre de un admin (`online-mode=false`) | `server-ip=127.0.0.1` forzado en cada corrida: solo la propia máquina puede conectarse (H2 CA2) |
| Manipulación | La suite modifica un servidor real | Solo escribe en `plugins/<plugin>/run*/`; no hay opción para otra carpeta ni host |
| Repudio | No queda constancia de lo que se probó | Reporte por escenario y `e2e.log` completo de cada corrida |
| Divulgación | Credenciales en el repositorio | Bots `auth: offline`, sin cuentas ni tokens (H2 CA3) |
| Denegación de servicio | Un escenario colgado deja el servidor encendido | Tiempo límite por escenario; `finally` apaga y mata el proceso si no termina en 60 s |
| Elevación de privilegios | Los bots quedan como OP en el servidor de prueba | `limpiar` les quita el OP al final |
| Manipulación | Aceptar el EULA en nombre del dueño | La suite se niega a arrancar sin `eula=true` escrito por el dueño; para otra versión copia su aceptación del servidor de prueba del mismo plugin, nunca la escribe |
| Manipulación | Jar de Paper alterado | Descarga de la API Fill oficial con sha256 verificado (H5) |

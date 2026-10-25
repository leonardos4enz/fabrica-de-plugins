# Fábrica de plugins de Minecraft — guía para sesiones de IA

Repositorio único con varios plugins de Paper, construidos con la misma metodología (SDD) que el proyecto ERP AI.
Cada plugin vive en `plugins/<nombre>/`. Idioma del proyecto: **español** (código y nombres técnicos en inglés).

## Antes de escribir código, leer
1. [Constitución](docs/sdd/constitution.md): principios no negociables (configuración, seguridad, rendimiento).
2. [Metodología SDD](docs/sdd/README.md) y [fase VERIFY](docs/sdd/verificacion.md).
3. [Roadmap](docs/sdd/roadmap.md): qué spec sigue y por qué.
4. Las specs previas del mismo plugin en `docs/sdd/specs/` (sobre todo la del núcleo: contrato de configuración y
   lenguaje de expresiones).

## Reglas de trabajo
- **Una sesión por spec (pedido del dueño).** Cada spec se trabaja en su propia sesión de IA, nueva, de SPECIFY a
  VERIFY, commit y push; esa sesión no toca otra spec. Al cerrar una spec, la siguiente **no** se continúa en la misma
  conversación: se abre una sesión nueva que arranca leyendo este archivo y el roadmap.
- Flujo: `spec.md` → `plan.md` (contrato de configuración y STRIDE) → `tasks.md` → implementar → `verify.md`.
- Toda capacidad nueva es **genérica** y se ejerce con configuración (un ejemplo en `src/main/resources/`).
- Cada bug encontrado se vuelve prueba **y se verifica que la prueba falla con el código anterior**.
- Plugin nuevo: `powershell -File tools/nuevo-plugin.ps1 -Nombre MiPlugin -Spec 00N` (proyecto + spec con plantillas).

## Comandos
```bash
./gradlew build                      # compila y prueba todos los plugins
./gradlew :omnievents:test           # unitarias de un plugin
./gradlew :omnievents:dist           # jar en dist/
./gradlew :omnievents:schema         # esquema JSON y referencia en docs/omnievents/ (spec 007; una prueba exige que estén al día)
bash tools/compuerta-servidor.sh omnievents "omni selftest"   # Paper real (el EULA lo acepta el dueño)
npm --prefix tests-e2e test          # compuerta 3a: bots de mineflayer en el Paper de prueba (spec 002)
npm --prefix tests-e2e test -- --sin-build --solo "H5 CA3"     # un subconjunto
npm --prefix tests-e2e run capturas -- --sin-build --solo "accion-title"   # capturas puntuales (spec 004)
```
Las capturas usan la fuente y el HUD del Minecraft Java 1.21.4 instalado del dueño (`%APPDATA%\.minecraft`) y Chrome
o Edge; los assets se extraen a `tests-e2e/.cache/` y **nunca se copian al repositorio**.
Gradle 8.14 no corre sobre Java 25: usar el JDK 24 (`JAVA_HOME=C:\Program Files\Java\jdk-24`). El código se compila
con `--release 21`.

## Lecciones aprendidas (no repetir)
- **PowerShell 5.1 corrompe UTF-8** con `Get-Content`/`Set-Content`. Usar las herramientas de edición,
  `[IO.File]::WriteAllText(..., UTF8 sin BOM)` o Bash. Y lee como ANSI un `.ps1` sin BOM: los scripts con acentos
  (`tools/nuevo-plugin.ps1`) se guardan en UTF-8 **con** BOM.
- En Git Bash, `python` puede ser el alias de la Microsoft Store y **colgarse** esperando: no usarlo en scripts.
- Los eventos asíncronos de Paper (`AsyncChatEvent`, `AsyncPlayerPreLoginEvent`) no pueden tocar el mundo: el motor
  pasa al hilo principal la parte de las acciones que no es segura (ver plan 001 §3).
- `EntityDamageByEntityEvent` es subclase de `EntityDamageEvent`: un oyente del padre también recibe al hijo.
- **El mapa de comandos de Paper** (`getKnownCommands()`) no permite borrar desde su iterador (`removeIf` revienta):
  juntar las claves y usar `remove(clave)` (001 F-03).
- `onDisable` guarda los datos en un `finally`: cualquier otro paso puede fallar (001 F-04).
- En Paper, `World` (y las entidades) son `Keyed`: no tratarlos como valores de registro (001 F-01).
- `run-paper` 2.3.1 no encuentra versiones: bajar Paper de la API Fill y usar `tools/compuerta-servidor.sh`.
- Sin jugador (consola, temporizador del servidor), un enfriamiento o límite «por jugador» no se aplica (001 F-05).
- Una acción posterior a un `kick` sigue teniendo al jugador, ya desconectado: `pvar` no debe exigir que esté en línea
  (002 F-01). `removeItem` de Bukkit solo quita ítems idénticos: para «por material» recorrer el inventario (002 F-02).
- En la suite en el juego, **la consola no espera**: después de preparar, `srv.sincronizar()` antes de que el bot actúe
  (002 F-03). mineflayer 4.39 no lee la barra de acción de Paper ni el chat cambiado por el servidor y nombra mal los
  sonidos de 1.21.4: `tests-e2e/lib/bot.mjs` lee esos paquetes directamente (002 F-04).
- Un plugin que copia ejemplos solo si su carpeta no existe deja ejemplos viejos en `run/`: la suite los copia del
  código fuente. En 26.1 los datos del jugador viven en `world/players/` (002 F-05).
- Paper 26.x necesita Java 25 (`--java-servidor`); el bot usa `--version-bot 26.1` (mismo protocolo que 26.1.2).
- **Proporción costo/valor (lección del dueño, spec 004):** la galería con una imagen para *cada* capacidad (147
  escenas, 9 corridas completas para estabilizarla) tomó horas y no valió la pena. Antes de una spec de alcance «todo»,
  estimar el costo y proponer el subconjunto que más valor da (las 5–10 capacidades más visibles); el resto, texto.
  Lo mismo para cualquier criterio «cada X tiene Y»: confirmar con el dueño si de verdad es cada uno.
- En 1.21.4, `spawn-monsters=false` borra al instante hasta los monstruos **invocados** (las capturas lo activan). Con
  mineflayer 4.39 en 1.21.4, `setControlState('sneak')` no agacha (manda un número donde va el nombre de la acción) y
  en creativo el bot no aplica la corrección del servidor tras una rotura cancelada (004).
- prismarine-viewer solo dibuja de y = 0 a 255 y no dibuja bloques con entidad (cofres, camas, carteles), ítems
  tirados, relámpagos ni partículas: las escenas de capturas se montan en y = 100 y lo demás va como texto (004).
- **Registrar o desregistrar comandos en caliente cambia el árbol que Paper recorre en otro hilo** al mandarlo a los
  jugadores: dos cambios seguidos dan `ConcurrentModificationException` en `Commands.sendAsync` (004 F-01, resuelto
  en la 005). Registrar solo si cambia el conjunto, ocultar con `testPermissionSilent` y mandar el árbol una vez:
  `syncCommands()` ya llama a `sendCommands` por jugador (no sumarle `updateCommands()`).
- Al subir la versión de un plugin quedan dos jars en `dist/`: la suite y `compuerta-servidor.sh` instalan solo el más
  reciente (005 F-01; antes la suite probaba el viejo y Paper no cargaba ninguno por «Ambiguous plugin name»).
- `/omni debug` y `/omni check` intercalan los valores en cada condición (`player.level (10) >= 3 → true`): un
  escenario que lee esas líneas busca ese texto, no la expresión sola (006 F-03). Otro proyecto de la máquina puede
  ocupar el puerto 25599 de la suite: mirar `netstat -ano | grep :25599` antes de culpar al plugin (006).

## Al cerrar cada spec: actualizar los README (siempre, antes del commit)
Los README son lo que lee el dueño de un servidor; si no dicen algo, para él no existe.
- **README del plugin** (`plugins/<nombre>/README.md`): cada capacidad nueva o cambiada con su ejemplo, y la sección
  **Compatibilidad** al día: versiones de Paper **verificadas** (build, Java del servidor, compuertas que pasaron y
  fecha), versiones **sin verificar**, Java mínimo, dependencias opcionales y lo que no se soporta (Spigot, Folia).
  Solo se escribe «verificada» si la suite pasó en esa versión; lo demás va como «sin verificar».
- **README raíz**: la fila del plugin en la tabla (versión del plugin, versiones de Paper verificadas, estado) y la
  estructura del repositorio si cambió.
- Si la spec es de la fábrica (herramientas, arnés), el README raíz explica cómo usarla.
- **Capturas: solo puntuales** (spec 004). Una imagen nueva solo para lo que el dueño pida o para la capacidad
  estrella de la spec (una o pocas escenas en `tests-e2e/<plugin>/capturas.mjs`, generadas con `--solo`). **No** se
  regenera la galería completa al cerrar cada spec ni se busca una imagen por capacidad.
- En `verify.md`, la fila «README» dice qué se actualizó (y si se regeneraron las capturas).

## Antes de cada commit
- Sin secretos: tokens, contraseñas, claves.
- Sin artefactos: `build/`, `.gradle/`, `dist/`, `run/` y `run-*/` (servidores de prueba), `node_modules/`, `*.class`.

## Commits
Mensaje en español, describiendo el qué y el porqué, terminando con:
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`

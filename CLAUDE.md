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
npm --prefix tests-e2e test -- --plugin skinstatues                # suite de SkinStatues (spec 021)
./gradlew :skinstatues:preview -Pout=x.png -Pheight=48             # SkinStatues: las poses dibujadas sin servidor
./gradlew :worldcraft:terrainPreview -Pout=x.png "-Pwhat=mountain medium;island large"   # Worldcraft: terreno sin servidor
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
- Un plugin que copia ejemplos solo si su carpeta no existe deja ejemplos viejos en `run/`: la suite y
  `compuerta-servidor.sh` (desde la 010, F-02) los copian del código fuente. En 26.1 los datos del jugador viven en `world/players/` (002 F-05).
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
- Las reglas pueden vivir en subcarpetas (paquetes con `pack.yml`, spec 011): toda copia de `rules/` en la suite o
  las herramientas debe ser recursiva (`copiarCarpeta` de `tests-e2e/lib/servidor.mjs`; 011 F-01).
- **Plugins de terceros en el Paper de prueba (spec 012):** `tests-e2e/lib/plugins-extra.mjs` los baja con sha256 fijado
  (solo lo aprobado por el dueño). EssentialsX reemplaza `list`, `give`, `tp`, `clear`… y cambia su salida: la suite
  se los desactiva (`comandosDeMinecraft`, 012 F-03). WorldEdit 7.4 necesita Java 25 y Citizens solo soporta el último
  parche de cada versión: 1.21.4 usa WorldEdit 7.3.19 y Citizens b3760 (012 F-04).
- Bukkit lee `pvar.x: 1` dentro de un mapa como una sección anidada (`pvar` → `x`): volver a juntar (012 F-01). YAML se
  queda en silencio con la última de dos claves iguales: el cargador avisa de reglas repetidas en un archivo (012 F-02).
- **Jugador de Bedrock en la suite (spec 013):** Geyser sigue a una sola versión de Java (2.6.2 para 1.21.4, 2.10.1 para
  26.1) y acepta el cliente sin Xbox de `bedrock-protocol` solo con `enable-proxy-connections: true` (2.6) o
  `advanced.bedrock.validate-bedrock-login: false` (2.10): lo pone `configurarGeyser`. El cliente necesita el RakNet
  nativo (con `jsp-raknet` no conecta) y Floodgate le pone «.» delante del nombre (`.Beb_e2e`: escapar el punto en regex).
- **Spec 014:** EssentialsX también reemplaza `/enchant` (consola: «Solo jugadores…»): usar `minecraft:enchant`. Paper no
  trae comando de permisos: la suite da uno escribiendo `permissions.yml` (`default: true`) + `reload permissions` y lo
  restaura al terminar. Un permiso no declarado vale para los OP en Bukkit: para «solo si está dado», `isPermissionSet`.
  Dentro de una acción con `to`, `player` es el destinatario y `source` el jugador del evento (014 F-01).
- En 26.x las reglas de juego van en snake_case (`natural_health_regeneration`, `advance_time`…): los nombres de
  1.21.4 fallan con «Incorrect argument» y el servidor sigue como si nada. La suite manda las dos formas (008 F-02).
- **Spec 015:** `EntityEquipment.setDropChance` revienta si la entidad no es un `Mob` (soporte de armadura). En 26.x
  `execute if entity` dice «Test passed. Count: N» (1.21.4: «, count:»). `SchemaTest` necesita 1 GB (el `anyOf` de acciones)
  y un ejemplo de acción inválido daba OOM armando el mensaje: un `OutOfMemoryError` en las pruebas suele ser eso. Tras
  cambiar el código, `./gradlew :omnievents:dist` antes de la compuerta 2 (si no, prueba el jar anterior).
- **Spec 016 (ProtocolLib):** en el chat firmado (`PLAYER_CHAT`), `getStructures().getValues()` de ProtocolLib revienta
  con un NPE por el campo de firma nulo: leer cada campo con `readSafely(i)` dentro de un `try` (016 F-03). Del Jenkins de
  dmulloy2, `ProtocolLib.jar` es un plugin de Paper con `api-version: 26.2` (no carga en 26.1.2): usar `ProtocolLib-Spigot.jar`.
  Parar una tanda de la suite en segundo plano corta el `bash` pero **no** el `node` de `correr.mjs` ni su Paper: esperar a
  que libere el puerto 25599 antes de otra corrida (si no, «El servidor se apagó esperando Done» y escenarios rotos).
- **Spec 018 (minijuego con reglas):** en `player_move`, `player.location` es todavía el **origen** (Bukkit dispara antes
  de mover): la placa pisada y la altura nueva son `to.block.type` y `to.y` (018 F-01: la caída no se detectaba). `teleport` (y toda acción `a;b;c`) separa por `;` **al cargar**: una ubicación
  guardada en un solo texto no se puede pasar; guardar mundo, x, y, z y giro en variables separadas. En `tests:`, `given`
  no evalúa expresiones: para la hora, `default(var.ahora, now())` en la regla y `var.ahora` en la prueba. `expect.actions`
  solo lista acciones con efecto (no `set`, `if` ni `call`). `PaquetesTest` carga los ejemplos con una versión fija: un
  paquete de ejemplo con `requires` más alto la necesita subir. Al insertar texto con `String.replace` en Node, pasar una
  función (`() => texto`): un `` $` `` o `$'` en el texto (regex de la suite) se interpreta y corrompe el archivo.
- **Spec 019 (ejemplos del jar):** `OmniEvents.EXAMPLES` es la lista fija de lo que el jar copia a `rules/` la primera vez;
  un archivo de ejemplo que falte ahí no llega a ningún servidor, y la suite y la compuerta 2 no lo ven porque copian del
  código fuente (el `parkour` de la 018 y `estilo_ce.yml` de la 014 nunca se copiaron: 019 F-01). `EjemplosDelJarTest` lo
  exige; para probar lo que crea el jar, la compuerta 2 sin `run/plugins/OmniEvents/rules/`. En un paquete, `${param}` se
  sustituye en los valores pero **no en las claves**: en `tests:` va `region.id: "${vip_region}"` o
  `assert: "pvar.coins == ${daily_coins}"`, nunca `${x}: 1`. Un NPC de Citizens con nombre de cuenta real («Guide») baja
  su skin en segundo plano y Paper puede registrar al apagar un «Nag author(s)… Citizens» que el arnés cuenta como error:
  es ruido de terceros (`ruidoDelServidor`, 019 F-05).

- **Spec 020 (página de presentación):** el visor 3D no dibuja jugadores (`visor.mjs` los filtra) ni aldeanos (prismarine-viewer
  no tiene textura por defecto para `villager`): un NPC de Citizens para una captura va con `--type WANDERING_TRADER` u otra
  criatura con textura `default`, y el visor la dibuja siempre mirando al sur (+z): la cámara se pone al sur, mirando al norte.
  Para hacer clic a una entidad el bot tiene que estar a menos de 3 bloques. `activateEntity` y `attack` giran la cabeza del
  bot: volver a la mirada anterior antes de la foto (si no, los cuadros de un GIF saltan). Silkscreen y Pixelify Sans se leen mal
  en títulos («OHNIEVENTS», «Ɛ»): la página usa Press Start 2P. Las franjas se sacan con puppeteer por elemento (`#franja-N`).

- **Spec 021 (SkinStatues, el segundo plugin):** desde aquí los plugins nuevos van **en inglés** para el dueño del servidor
  y el jugador (mensajes, configuración, README); los documentos de la fábrica siguen en español ([ADR 0005](docs/adr/0005-plugins-en-ingles.md)).
  `Material.isSolid()/isOccluding()` y `Bukkit.createBlockData` necesitan el registro del servidor: no corren en unitarias
  («No RegistryAccess implementation found»); se inyecta la comprobación y lo real se ve en la compuerta 2 (glowstone y
  sea_lantern no son «occluding»: F-02). Paper **no contesta** un autocompletado sin sugerencias: `bot.tabComplete` se
  agota, y eso es la lista vacía. Un escenario que construye limpia el área al empezar (si no, uno que falla tumba a los
  siguientes). **Nunca un paquete Java llamado `build`:** el `.gitignore` ignora toda carpeta `build/` y sus fuentes no
  se suben (`git status --ignored` lo muestra). El JDK 25 de la máquina está en `C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot`. En geometría,
  las escalas no enteras ponen centros de bloque justo en el plano de una cara: comparar con igual y probar con una
  inundación desde dentro (`VoxelizerTest`); y lo que depende de un umbral se decide con el valor pedido, no con uno ya
  ajustado (F-03).
- **Spec 022 (SkinStatues 0.2.0):** las poses del plugin se leen **del jar** (`builtin-poses.yml`) y `poses.yml` solo
  agrega o reemplaza: un archivo que se copia «si no existe» nunca trae lo nuevo a quien actualiza (como la 019 F-01).
  El servidor de prueba `run/` puede tener el mundo y las estatuas del dueño (juega ahí): antes de la suite, apartar
  `world*` y `plugins/SkinStatues` y devolverlos al final (la suite limpia el área de X, Z = 40 y pisa `config.yml`). Tras
  construir lejos, el plugin suelta los tickets y Paper descarga el chunk: para mirar un bloque ahí, `forceload add`
  («That position is not loaded»). Una estatua que quedó de una corrida interrumpida cambia la siguiente: limpiar
  también lo lejano. En Git Bash, `node -e '…'` con un apóstrofo en el texto corta la cadena: usar las herramientas
  de edición. Dos partes que se cruzan (la cara en el brazo en `dabbing`) llenan el «interior» de otra: la prueba de
  cáscara salta esos puntos.
- **Spec 023 (skins por link):** la suite sirve las skins con un HTTPS local (`tests-e2e/lib/https-local.mjs`, la suite

  lo pide con `antesDeArrancar`/`alTerminar`). El `trustStore` que se le pasa a Paper tiene que ser una **copia del
  `cacerts` del JDK** con el certificado agregado: uno con solo el local rompe el HTTPS de Paper («Failed to request
  yggdrasil public key», cuenta como error del log). `permissions.yml` **no puede redefinir** un permiso del `plugin.yml`
  («The permission skinstatues.build is already defined!»): se da como hijo de uno propio (`e2e.x: {default: true,
  children: {skinstatues.build: true}}`). `statue undo` desde la consola deshace la última **de la consola**: para la de
  un jugador, `statue remove <id>`. Paper anota en su log cada comando de un jugador (link incluido): eso no es del plugin.
- **Spec 024 (escenas de SkinStatues):** el visor 3D de las capturas veía solo 3 chunks: `Camara(bot, puerto, { distancia })`
  (12 para una estatua de 100, con `view-distance` 12 en el servidor) y, tras un `tp`, ~20 s hasta que arma los chunks. `tp`
  con enteros centra en el bloque (0 → 0.5): esperar la posición exacta nunca se cumple, mandar decimales. Para un GIF de
  la obra, `build.blocks_per_tick` bajo (12). Un `Promise.race` con `dormir(timeout)` deja node vivo hasta el timeout: usar
  `setTimeout` con `clearTimeout`. **Mirar la skin actual de cada cuenta antes de usarla:** la de Quackity era Mickey Mouse
  (personaje de Disney) y se cambió por DanTDM. El servidor de las escenas es `run-capturas/` (puerto 25597), nunca `run/`.
- **Spec 025 (Worldcraft, editor de mundo):** un trabajo con tope de tiempo por tick mira el reloj **en cada bloque**
  (`System.nanoTime` cuesta ~25 ns): mirarlo cada 64 dejó un tick de 23,5 ms con 8 de presupuesto (F-01). El máximo
  «desde el arranque» queda marcado por picos en frío (clases, primer YAML o MiniMessage, recolector: 144 ms una vez);
  para verificar el presupuesto se mide el tick más largo de **una obra** ya en caliente. Las sugerencias usan
  Damerau (dos letras cambiadas de lugar = 1): con Levenshtein `stnoe` empataba con `snow` (F-02). Un servidor de prueba
  nuevo (`plugins/<plugin>/run/`) se arma copiando el `paper-*.jar`, el `eula.txt` que ya aceptó el dueño y el
  `server.properties` de `plugins/omnievents/run/`. La vista previa con `Player.sendMultiBlockChange` la ve el bot de
  mineflayer como bloques reales (`bot.blockAt`), y otro bot no: así se prueba «solo la ve quien edita».
- **Spec 026 (terreno de Worldcraft):** las formas «bonitas» se ajustan **mirándolas**: `terrainPreview` corre la obra por el
  motor en el mundo en memoria y la dibuja (la primera montaña salió toda roca; se vio en el PNG, no en las pruebas). Lo
  que se pone relativo a los pies del jugador hay que pensarlo con el jugador **en el suelo**: la isla quedaba enterrada
  (F-01). Un `Settings.Reader` sobre una sección pierde el prefijo en los avisos: leer desde `getRoot()` con la ruta
  completa (F-02). En Git Bash, `node -e "…"` con comillas dobles **ejecuta** las comillas invertidas (`` ` ``) del texto
  y `$…` se expande: para insertar código JS o Java, las herramientas de edición. Con mineflayer 4.39 en 1.21.4,
  `activateItem()` llega como `RIGHT_CLICK_AIR` y `swingArm()` como `LEFT_CLICK_AIR` (mirando a más de 5 bloques).
- **Spec 027 (esquemas `.schem`):** el NBT y Sponge v1–v3 son Java propio (`schem/`), escrito desde la especificación; los
  ítems de un cofre pasan por `ItemStack.serializeAsBytes`/`deserializeBytes`, que son NBT con GZip y `DataVersion`: el juego
  actualiza los ítems viejos sin NMS. Los v2 de WorldEdit 7.0–7.2 guardan en `Offset` la posición del **mundo** y lo relativo
  en `Metadata.WEOffsetX/Y/Z`; v3 usa `Offset`. Los carteles guardan cada línea como JSON en un String hasta 1.21.4 y como NBT
  desde 1.21.5 (`DataVersion` 4325). WorldEdit 7.3.19 en la suite de Worldcraft (`pluginsExtra: /worldedit/i`: `correr.mjs`
  acepta una expresión e instala solo esos jars): `//copy` dice «6 blocks affected» (no «were copied»), `//schem save x
  sponge.2` escribe v2 (el de fábrica es v3) y `//undo` dice «Undo successful». El primer `copy` con un cofre tras arrancar
  pagaba ~15 ms de código frío al serializar los ítems (F-01, como la 025 F-04): `warmUp` ya hace esa ida y vuelta. Se repitió
  la lección de la 026 (`node -e` con comillas invertidas en Git Bash corrompió el README): los scripts van a un archivo
  en el scratchpad con la herramienta de escritura.
- **Spec 028 (paridad con WorldEdit):** el inventario de WorldEdit y FAWE salió de sus anotaciones `@Command` en el código
  fuente (con permiso del dueño, solo como dato; Worldcraft se escribe desde cero). Lo que sigue a una lectura por tandas
  (`Edits.read`) corre en el tick siguiente: si no, la obra decía «waiting for your previous edit» por la lectura ya
  terminada (F-02). Lo que busca la cima de una columna no se acota a ±radio de los pies: volando o en una ladera la cima
  queda fuera (F-01). Una esfera de radio r usa r + ½ (radio 1 = 19 bloques, como WorldEdit): al armar un escenario,
  la esquina de un cuadrado de lado r queda fuera.
- **Spec 029 (paridad verificada contra los jars):** el inventario de un plugin sale del **bytecode** con `javap -v` (las
  anotaciones `@Command` son visibles; `@Arg`/`@Switch` quedan como «invisibles» pero javap también las muestra) y se cruza
  con el código fuente y con un servidor real: el código de FAWE tiene comandos **comentados** que no existen en el jar.
  Desde la consola, `help <Plugin>` lista los de primer nivel y `worldedit help -p N <grupo>` da la línea `Usage:` con los
  subcomandos (`//help` no se resuelve en la consola). FAWE y WorldEdit no conviven: FAWE va en `PLUGINS_ALTERNATIVOS` de
  `plugins-extra.mjs` y solo se instala con un filtro que lo nombre (la suite de Worldcraft usa `/^worldedit/i`). En la 3a,
  tras el escenario de navegación mineflayer 4.39 dejó de mandar el giro del bot (`data get entity X Rotation` seguía con el
  anterior): lo que depende de hacia dónde mira el jugador en el servidor se pone con `tp X x y z yaw pitch`. El pasto
  tapado por un bloque sólido se vuelve tierra con los ticks al azar: una cuenta sobre pasto depende del tiempo (usar tierra).
- **Spec 030 (selecciones que no son cajas):** antes de crear una prueba, mirar si el nombre ya existe (`RegionTest` era de
  la 025 y la herramienta de escritura la pisó sin avisar: se recuperó con `git checkout`). `CommandTest` busca cada
  `msg().get("clave")` en `messages.yml`: una clave armada con `+` no se puede revisar (va un `switch` con las claves
  escritas), y `Messages.get` escapa los valores (un valor con etiquetas MiniMessage se ve literal). La selección vive en
  la sesión de cada jugador durante toda la suite: un escenario que cambia la forma la vuelve a `cuboid` en un `finally`
  (si no, los siguientes esperan «Corner 1 at» y fallan). Una envolvente convexa sin casos degenerados: inflar cada
  vértice a las 8 esquinas de su bloque y trabajar al doble con enteros (`long`); los vértices que caen sobre una arista la
  parten en tramos (para el contorno se mide el largo, no la cantidad de segmentos).

- **Spec 031 (entidades de Worldcraft):** sin NMS, una entidad entera (con lo montado) sale de
  `Bukkit.getUnsafe().serializeEntity(e, PASSENGERS)` (NBT con GZip y `DataVersion`, lo lee el `Nbt` de la 027) y vuelve con
  `deserializeEntity(bytes, mundo, false, true)` + `spawnAt`; un colgante se gira con `setFacingDirection(cara, true)`.
  En el Paper de prueba `spawn-monsters=false` borra hasta los monstruos invocados y `difficulty` desde la consola queda
  **guardado en el mundo**: para probar `butcher` en la compuerta 2 se activa un momento y se devuelve; en la suite se usan
  criaturas que no son hostiles. Matar una vaca suelta cuero y carne (ítems que la siguiente prueba cuenta): matar dos
  veces. `data get entity` necesita `limit=1` aunque el selector tenga `dx=0`. En un `.schem`, WorldEdit pega un marco
  con la diferencia entre `Data.Pos` y `TileX/Y/Z`: los dos coherentes (F-01, F-02); y WorldEdit 7.3.19 mismo hace que
  Paper avise «Block-attached entity at invalid position» al pegar un marco a más de 16 bloques (ruido de terceros).
- **Spec 032 (pinceles y herramientas):** un pincel o herramienta calcula la mira con su propio `rayTraceBlocks` y acepta
  `*_AIR` y `*_BLOCK` (mineflayer manda `RIGHT_CLICK_AIR`/`LEFT_CLICK_AIR`; un clic izquierdo al aire solo llega si no hay
  bloque a menos de ~5); para un `LEFT_CLICK_BLOCK` de verdad (superpico) el bot usa `bot.dig(bloque)` en creativo e ignora
  su error (el plugin cancela la rotura). Lo que pregunta al registro (`hasTile`, `isAir`, la tabla de estados) se arma
  como un conjunto en el hilo principal y el cálculo en el otro hilo solo consulta ese conjunto. Un `messages.yml` de una
  versión anterior conserva sus textos (también la ayuda vieja): una línea nueva llega sola, una cambiada no (README).
  `createBlockData` tira una excepción por cada estado inválido: el cycler recuerda lo probado. Se repitió la 002 F-03:
  después de un `setblock` de la consola, `srv.sincronizar()` antes de que el bot haga clic.
- **Spec 033 (biomas, árboles y estructuras):** el juego guarda un bioma por celda de 4×4×4; `getBiome` de Bukkit lee la
  celda exacta, pero `execute if biome` y el cliente usan el «zoom difuso» (cerca del borde toman la celda vecina): una
  prueba mira el centro de una zona de varias celdas. mineflayer 4.39 no nombra los biomas de 1.21.4 (`biome.name`
  vacío): comparar `biome.id`. `generateTree(…, Predicate)` con el predicado en `false` captura el árbol sin tocar el
  mundo (el generador no mira el suelo: el suelo lo elige el plugin). La lista de plantillas no tiene API: se lee del jar
  del servidor (`data/<ns>/structure/**.nbt` por el cargador de clases). El iglú recibe el botín de un marcador de datos
  al generarse (su cofre de plantilla no lo trae): para probar el botín, `ruined_portal/portal_1`. Al guardar un `.schem`
  se arma un portapapeles nuevo: todo lo nuevo del portapapeles (biomas) hay que pasarlo ahí (F-01).

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

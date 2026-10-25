# Investigación: qué hace exitosos a los plugins de Paper y cómo puede ganar OmniEvents

**Fecha:** 2026-10-24 · **Para:** el dueño de la fábrica · **Estado:** propuestas para elegir (nada implementado)

Objetivo: que OmniEvents supere sin duda a ConditionalEvents (Ajneb97) y aprenda de los plugins más exitosos. Fuentes:
páginas de SpigotMC, Hangar, Modrinth, CurseForge y BuiltByBit, wikis oficiales, issues de GitHub y foros (skUnity).

**Límites de la investigación.** No se pudo leer Reddit (ninguna búsqueda devolvió hilos de r/admincraft). SpigotMC
dio 403 en algunas páginas (Citizens, CommandPanels, algunas reseñas): ahí las cifras salen de CurseForge, Modrinth o
de espejos de SpigotMC (speedgot), marcados como tales. De cada plugin se leyó la primera página de reseñas. Dos
artículos son de mineguard.pro, una empresa de hosting y anti-DDoS: se citan como fuente secundaria. Las cifras son
del día de la consulta y cambian.

## 1. Plugin por plugin

### ConditionalEvents (Ajneb97), el rival directo
- **Cifras:** 239.857 descargas y 4,9/5 con 314 valoraciones; versión 4.80.3 del 4-oct-2026
  ([Spigot](https://www.spigotmc.org/resources/conditionalevents.82271/)). 57★ y 122 issues en
  [GitHub](https://github.com/Ajneb97/ConditionalEvents).
- **Por qué gusta:** «reemplaza varios plugins», fácil para principiantes, autor activo, gratis
  ([reseñas](https://www.spigotmc.org/resources/conditionalevents.82271/reviews)). Documentación en GitBook con
  `llms.txt` ([wiki](https://ajneb97.gitbook.io/conditionalevents/)). Agregó MiniMessage en nov-2025
  ([actualización](https://speedgot.ajg0702.us/resources/82271/updates/623005), espejo).
- **Límites del formato:**
  - No mezcla `and` y `or` en una línea, no tiene paréntesis y no documenta la precedencia.
  - Las matemáticas en condiciones se activan evento por evento (`allow_math_formulas_in_conditions`).
  - Para ramificar se usa `execute <grupo>` ([condiciones](https://ajneb97.gitbook.io/conditionalevents/conditions.md)).
  - Acciones con argumentos posicionales separados por `;`, sin bucles ni acción para guardar variables
    ([acciones](https://ajneb97.gitbook.io/conditionalevents/actions.md)).
  - No tiene variables persistentes propias: las reseñas lo combinan con ServerVariables, otro plugin del autor. Las
    variables anidadas son una opción experimental ([config.yml](https://ajneb97.gitbook.io/conditionalevents/config.yml.md)).
  - Comandos: `debug`, `verify`, `call` e `interrupt`; no tiene `eval` ni estadísticas
    ([comandos](https://ajneb97.gitbook.io/conditionalevents/commands-and-permissions.md)).
- **Quejas y peticiones** ([issues](https://github.com/Ajneb97/ConditionalEvents/issues?q=is%3Aissue), reseñas):
  - Lo encuentran «un poco técnico al principio» y hay un bug con «demasiados comandos en eventos».
  - No puede registrar placeholders propios y Folia no está planeado.
  - Piden prioridad de oyentes (#129), un placeholder de «evento en curso» (#127), ítems personalizados (#132), cuenta
    regresiva de eventos repetitivos, rachas de kills (el autor dice que no guarda esos datos) y enfriamientos por grupo.
  - Bugs: coordenadas negativas (#131) y un enfriamiento que se aplicaba a todos los comandos (#118).
  - Los comandos de `player_command` hay que listarlos y reiniciar el servidor.

### Skript y sus addons
- **Cifras:** 613.858 descargas en Spigot (4,4/5 en total, pero 3/5 en la 2.12.2), 289,8 k en
  [Modrinth](https://modrinth.com/plugin/skript), 1,3 k★ en [GitHub](https://github.com/SkriptLang/Skript). Desde la
  2.13 solo funciona en Paper ([Hangar](https://hangar.papermc.io/SkriptLang/Skript)).
- **Éxito:**
  - Sintaxis parecida al inglés.
  - Documentación autogenerada por categorías ([docs](https://docs.skriptlang.org)).
  - Ecosistema de addons: [SkBee](https://github.com/ShaneBeee/SkBee) (NBT, scoreboards, recetas),
    [skript-reflect](https://github.com/SkriptLang/skript-reflect) (Java y eventos propios) y
    [skript-yaml](https://docs.skunity.com/addons/skript-yaml).
- **Quejas:**
  - Reseñas ([reseñas](https://www.spigotmc.org/resources/skript.114544/reviews)): «everything stopped working. no
    errors anywhere», «weak documentation». Los autores de addons llaman «brutal» el cambio de API.
  - La 2.10 rompió scripts y addons, y exigió Java 17 ([2.10.0](https://github.com/skriptlang/skript/releases/tag/2.10.0)).
  - Rendimiento: se interpreta en ejecución, y los bucles periódicos sobre todos los jugadores son el mayor culpable
    ([skUnity](https://forums.skunity.com/threads/what-causes-slow-server-performance-with-skript.9740/latest)).
  - Todas las variables viven en RAM y en un CSV, y con muchas el arranque se vuelve lento
    ([skUnity](https://forums.skunity.com/threads/variables-loading-super-slow.5443/latest)).

### Denizen
- **Cifras:** 92.986 descargas y 4/5 con 131 valoraciones ([Spigot](https://www.spigotmc.org/resources/denizen.21039/));
  246★ en [GitHub](https://github.com/DenizenScript/Denizen).
- **Éxito:**
  - Referencia «meta docs» con una página por comando, etiqueta y evento ([meta](https://meta.denizenscript.com/)).
  - Extensión de VS Code que marca errores y muestra la documentación
    ([guía](https://guide.denizenscript.com/guides/first-steps/script-editor.html)).
  - Su depurador traza cada comando con los argumentos ya evaluados, y `/denizen debug -r` + `submit` genera un enlace
    para compartir el registro ([guía](https://guide.denizenscript.com/guides/first-steps/problem-solving.html)).
  - **Flags persistentes** en cualquier objeto (servidor, jugador, NPC, bloque…), que caducan solas (`expire:1d`)
    ([flags](https://guide.denizenscript.com/guides/basics/flags.html)).
- **Quejas:** página de Spigot desactualizada («doesn't work on modern versions»), soporte que algunos sienten hostil,
  sintaxis que cambia entre versiones y difícil de aprender ([reseñas](https://www.spigotmc.org/resources/denizen.21039/reviews)).

### MythicMobs
- **Cifras:** 394.169 descargas y 4,2/5 con 533 valoraciones ([Spigot](https://www.spigotmc.org/resources/mythicmobs.5702/)).
- **Éxito:**
  - Lenguaje compacto de una línea y soporte en Discord.
  - Cada condición tiene una acción según su resultado (`true`, `false`, `cast`, `castinstead`, `orElseCast`) y admite
    `&&`, `||` y paréntesis ([condiciones](https://wiki.mythiccraft.io/mythicmobs/Skills/Conditions)).
  - Los **targeters** (`@PlayersInRadius{r=5}`), separados de las acciones.
  - **Metaskills** con parámetros, enfriamiento, `OnCooldownSkill` y `OnFailSkill`
    ([metaskills](https://wiki.mythiccraft.io/mythicmobs/Skills/Metaskills)).
- **Quejas:**
  - Tarda en soportar versiones nuevas (es la queja más repetida en las [reseñas](https://www.spigotmc.org/resources/mythicmobs.5702/reviews)).
  - Las matemáticas y los placeholders en habilidades son de pago ([Premium](https://wiki.mythiccraft.io/mythicmobs/Premium-Features)).
  - `~onTimer` tiene advertencias de rendimiento ([onTimer](https://wiki.mythiccraft.io/mythicmobs/Skills/Triggers/onTimer)).
  - Consola sin información útil cuando el plugin se desactiva.

### DeluxeMenus y CommandPanels
- **DeluxeMenus:** unas 818.443 descargas y 4,3/5 con 589 valoraciones
  ([Spigot](https://www.spigotmc.org/resources/deluxemenus.11734/)). Gana por simple y estable.
  - Requisitos en cada punto de contacto (abrir, ver, cada tipo de clic), cada uno con sus acciones de éxito y de
    rechazo, «N de M» (`minimum_requirements` con `optional`) y `stop_at_success`
    ([requisitos](https://wiki.helpch.at/helpchat-plugins/deluxemenus/options-and-configurations/requirements)).
  - Quejas: tarda en soportar versiones nuevas, y su requisito `javascript` depende de un motor JS.
- **CommandPanels:** 30,8 k descargas en Modrinth, compatible con Paper y Folia
  ([Modrinth](https://modrinth.com/plugin/commandpanels)). Tiene un editor web con vista previa sin cuenta y refresca
  cuando cambian los permisos, en vez de sondear.

### BetonQuest
- **Cifras:** 78.653 descargas y 4,8/5 con 146 valoraciones ([Spigot](https://www.spigotmc.org/resources/betonquest.2117/)).
- **Éxito:**
  - La documentación es lo más elogiado («best documentation and tutorials of any plugin»): tutoriales, referencia,
    API y migraciones.
  - Depuración granular: `/bq condition <jugador> pkg>cond` da verdadero o falso, `/bq action` ejecuta una acción
    suelta, y hay depuración por paquete ([depuración](https://betonquest.org/RELEASE/Tutorials/Tools/Debugging-Quest-Logic/)).
  - **Paquetes** en carpetas con espacio de nombres (`paquete>nombre`), `enabled`, `version` para el migrador
    automático y plantillas con herencia ([paquetes](https://betonquest.org/RELEASE/Documentation/Basics/Packages-%26-Templates/)).
  - Más de 40 integraciones.
- **Quejas:**
  - No tiene editor visual, y montajes simples son complicados («minar 100 bloques» es «insanely complex»).
  - Al actualizar sin los pasos manuales se rompen misiones ([reseñas](https://www.spigotmc.org/resources/betonquest.2117/reviews)).
  - Su propia documentación admite que los errores de YAML son difíciles de entender
    ([YAML](https://betonquest.org/RELEASE/Tutorials/Tools/Handling-YAML-Errors/)).
  - No soporta Folia.

### Citizens
- **Cifras:** unas 5,19 M descargas en [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/citizens); 727★
  y 14 issues abiertos en [GitHub](https://github.com/CitizensDev/Citizens2).
- **Éxito:** es el estándar desde 2011, por su API de *traits* y porque los plugins de misiones y tiendas lo esperan
  ([API](https://wiki.citizensnpcs.co/API)). Los comandos de NPC traen opciones genéricas: enfriamiento por jugador y
  global, usos máximos, retardo, **costos** (dinero, experiencia, ítems) y modo secuencial o aleatorio
  ([NPC Commands](https://wiki.citizensnpcs.co/NPC_Commands)).
- **Quejas:**
  - Distribución confusa: de pago en Spigot y gratis en Jenkins
    ([Aternos](https://board.aternos.org/thread/52003-plugin-request-citizens-2-once-again-please-read-tho/)).
  - La opción de ejecutar como OP temporal está desaconsejada por su propia wiki.

### PlaceholderAPI
- **Cifras:** 2.167.350 descargas y 4,5/5 con 482 valoraciones; dice usarse en más de 45.000 servidores; Folia desde
  la 2.11.7 ([Spigot](https://www.spigotmc.org/resources/placeholderapi.6245/)). Tiene más de 240 expansiones en la
  eCloud, descargables desde el juego ([GitHub](https://github.com/PlaceholderAPI/PlaceholderAPI)).
- **Éxito:** se volvió infraestructura: un formato común (`%x_y%`) que todos los plugins leen.
- **Quejas:**
  - Seguridad: el 28-oct-2023, atacantes combinaron un «force op» con una expansión vulnerable para descargar
    expansiones no verificadas, y PAPI las retiró ([2.11.5](https://hangar.papermc.io/HelpChat/PlaceholderAPI/versions/2.11.5)).
  - Las expansiones lentas (SQL en cada petición) hacen quedar mal al plugin que las pide
    ([guía](https://mineguard.pro/en/blog/placeholderapi-minecraft-server-guide), fuente secundaria).

### ItemsAdder, Oraxen y Nexo
- **Precios:** Nexo cuesta 19,99 €, más 39,99 € del complemento para Bedrock ([Nexo](https://nexomc.com/)). ItemsAdder
  cuesta 24,99 USD y está ofuscado ([BuiltByBit](https://builtbybit.com/resources/itemsadder.10839/review/11677/)).
- **Éxito:** paquete de recursos automático y **conversor desde el rival**: Nexo migra Oraxen copiando su carpeta
  ([migración](https://docs.nexomc.com/migrate/oraxen-nexo.md)).
- **Quejas:** fallos al descargar el paquete de recursos de ItemsAdder (existe un plugin solo para reemplazar su
  hosting: [espejo](https://speedgot.ajg0702.us/resources/118574/updates/621641)), roturas al cambiar de versión y
  wikis incompletos.

### TAB (NEZNAMY)
- **Cifras:** 868.276 descargas y 4,8/5 con 612 valoraciones ([Spigot](https://www.spigotmc.org/resources/tab-1-5-1-21-11.57806/)).
- **Éxito:** el **rendimiento como argumento de venta**.
  - Cada placeholder tiene su intervalo de refresco, y `-1` lo marca como constante.
  - `/tab cpu` mide el uso por placeholder y por función.
  - Las funciones desactivadas no cuestan nada ([placeholders](https://github.com/NEZNAMY/TAB/wiki/Placeholders)).
  - `/tab parse` muestra el valor de una expresión, y las condiciones simples tienen nombre
    ([condiciones](https://github.com/NEZNAMY/TAB/wiki/Feature-guide:-Conditional-placeholders)).
- **Quejas:** el soporte oficial es solo de pago, y no acepta peticiones de funciones ([GitHub](https://github.com/NEZNAMY/TAB)).

### AuraSkills
- **Cifras:** unas 14.190 descargas en [Hangar](https://hangar.papermc.io/Archy/AuraSkills).
- **Éxito:** configuración repartida por carpetas (`skills/`, `sources/`, `menus/`) frente a los archivos enormes de
  mcMMO ([comparación](https://mineguard.pro/en/blog/aurelium-skills-vs-mcmmo-rpg-comparison), fuente secundaria). Hay
  un mercado de configuraciones prehechas de pago
  ([BuiltByBit](https://builtbybit.com/resources/auraskills-config-premium-quality.83670/updates)).
- **Quejas:** wiki desactualizada y cambios que piden reiniciar.

### Tendencias que hoy se dan por hechas
- **Editor web de LuckPerms**, sin estado ([detalle técnico](https://raw.githubusercontent.com/LuckPerms/wiki/master/pages/Web-Editor-Technical-Details.md)):
  - Los datos viajan por bytebin y los cambios en vivo por bytesocks.
  - El servidor confía en la clave del editor solo si el administrador la aprueba, y luego solo acepta mensajes firmados.
- **Lo que se da por hecho:**
  - MiniMessage ([Paper](https://docs.papermc.io/adventure/minimessage/api/)).
  - Folia, que solo carga plugins con `folia-supported: true` ([Folia](https://github.com/PaperMC/Folia/wiki/Plugin-Compatibility)).
  - Formularios de Bedrock vía Floodgate ([Floodgate](https://wiki.geysermc.org/floodgate/api/)).
  - bStats ([bStats](https://bstats.org/)).
- **Recarga honesta:** validar y respaldar antes de aplicar, y decir si el cambio es en caliente o pide reiniciar
  ([ejemplo](https://minecraftbible.com/plugins/project/uch-universal-config-hub)).

## 2. Lo que se repite
1. **Las quejas número uno son errores silenciosos y roturas al actualizar** (Skript, BetonQuest, MythicMobs,
   DeluxeMenus, Denizen). OmniEvents ya valida al cargar con archivo, ruta y sugerencia: es una ventaja que hay que
   cuidar y anunciar.
2. **Las herramientas para quien escribe la configuración deciden** cuáles son los preferidos: depurador con valores
   (Denizen), evaluar una condición o acción suelta (BetonQuest), editor con autocompletado (Denizen en VS Code),
   editor web (LuckPerms, CommandPanels).
3. **Cobrar por el lenguaje molesta** (MythicMobs Premium, MythicCrucible, Nexo con Bedrock aparte).
4. **El rendimiento visible vende** (TAB `/tab cpu`); el sondeo periódico es el mayor causante de lag (Skript,
   MythicMobs `onTimer`).
5. **El ecosistema retiene:** formatos que otros leen (PAPI), conversores desde el rival (Nexo) y paquetes compartibles
   (BetonQuest, mercado de configuraciones de AuraSkills).

## 3. Comparación con lo que OmniEvents ya hace
Fuentes de OmniEvents: [README](../../plugins/omnievents/README.md) y [roadmap](../sdd/roadmap.md). ✔ = lo tiene,
◐ = parcial, ✘ = no lo tiene, — = no aplica.

| Capacidad | OmniEvents 1.0.1 | ConditionalEvents | Skript | Denizen | MythicMobs | DeluxeMenus | BetonQuest | TAB |
|---|---|---|---|---|---|---|---|---|
| Lógica completa (`and`/`or`, paréntesis, precedencia) | ✔ | ✘ | ✔ | ✔ | ✔ | ◐ (`!`, N de M) | ◐ (condiciones compuestas) | ◐ (`;` o `\|`) |
| Matemáticas siempre activas | ✔ | ◐ (por evento) | ✔ | ✔ | ◐ (Premium) | ◐ | ◐ | ✘ |
| Control de flujo (`if`, bucles, aleatorio, `delay`) | ✔ | ◐ (grupos `execute`) | ✔ | ✔ | ✔ | ✘ | ◐ | — |
| Variables persistentes propias | ✔ (`pvar`, `gvar`) | ✘ (otro plugin) | ✔ (RAM + CSV) | ✔ (flags) | ◐ (variables) | ✘ | ✔ (puntos, tags) | — |
| Variables con caducidad, en cualquier objeto | ✘ | ✘ | ✘ | ✔ | ◐ | ✘ | ✘ | — |
| Rama propia por cada condición que falla | ◐ (`else` de la regla) | ✘ | ✔ | ✔ | ✔ | ✔ | ◐ | — |
| Enfriamientos y límites con reinicio | ✔ | ◐ | ◐ (a mano) | ◐ (a mano) | ✔ | ◐ | ◐ | — |
| Costos declarativos (dinero, ítems, experiencia) | ✘ | ✘ | ✘ | ✘ | ✘ | ◐ (con requisitos) | ◐ | — |
| Comandos reales con alias, permiso y tab | ✔ | ◐ (reinicia) | ✔ | ✔ | — | ✔ (abrir menú) | ✘ | — |
| Depuración en vivo con valores | ✔ (`/omni debug`) | ◐ (`debug`) | ✘ | ✔ | ◐ | ✘ | ✔ | ◐ (`parse`) |
| Evaluar una expresión suelta | ✔ (`/omni eval`) | ✘ | ✘ | ◐ | ✘ | ✘ | ✔ | ✔ |
| Probar una regla sin efectos (simulación) | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ◐ (acción suelta) | ✘ |
| Estadísticas y CPU por regla | ◐ (tiempo por regla) | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✔ (`/tab cpu`) |
| Validación al cargar con sugerencia | ✔ | ◐ (`verify`) | ◐ | ◐ | ◐ | ✘ | ◐ | ◐ |
| Autocompletado en el editor (esquema, extensión) | ✘ | ✘ | ◐ (terceros) | ✔ (VS Code) | ✘ | ✘ | ◐ | ✘ |
| Editor visual (web o en el juego) | ✘ (en el roadmap) | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |
| Paquetes compartibles con nombre y versión | ✘ | ✘ | ◐ (archivos) | ◐ | ◐ | ◐ | ✔ | — |
| Importador desde el rival | ✔ (de ConditionalEvents) | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |
| Oyentes solo si se usan | ✔ | ✘ | ✘ | ◐ | — | — | — | ✔ |
| Pruebas automáticas en el juego, publicadas | ✔ (22 escenarios) | ✘ | ◐ (pruebas propias) | ✘ | ✘ | ✘ | ◐ | ✘ |
| PlaceholderAPI (leer y exponer) | ✔ | ✔ | ✔ (addon) | ✔ | ✔ | ✔ | ✔ | ✔ |
| Menús de inventario o formularios de Bedrock | ✘ | ✘ | ◐ (addons) | ✔ | — | ✔ | ◐ | — |
| Folia | ✘ | ✘ | ✘ | ✘ | ◐ | ✘ | ✘ | ✔ |
| Gratis y sin funciones de pago | ✔ | ✔ | ✔ | ✔ | ✘ | ✔ | ✔ | ◐ (soporte) |

La tabla es aproximada: resume lo que dicen las fuentes de arriba, no una prueba de cada plugin. En el lenguaje,
el control de flujo, las variables y el diagnóstico, OmniEvents **ya** supera a ConditionalEvents. Donde los otros le
ganan: herramientas de autor (esquema, editor, simulación), caducidad y alcance de las variables, ramas por condición,
paquetes, rendimiento visible y menús.

## 4. Propuestas para innovar
Ordenadas por valor contra costo (S ≈ una sesión, M ≈ 2–3 sesiones, L ≈ 4 o más). Ninguna está implementada.
**Antes de abrir cualquiera, revisar la spec 003** (paridad con ConditionalEvents, de otra sesión) para no duplicarla.

### P1 · Ramas por condición y «N de M» — S, riesgo bajo
**Qué es:** cada condición puede llevar sus propias acciones `deny` y un grupo puede pedir «al menos N de M».
**Por qué gana:** en ConditionalEvents hay que duplicar eventos o usar grupos `execute` para decir *por qué* no se
cumplió. DeluxeMenus y MythicMobs lo tienen, y es lo que más simplifica las reglas de tiendas y permisos.
```yaml
conditions:
  - expr: "player.level >= 10"
    deny: [message: "<red>Necesitas nivel 10 (tienes {player.level})"]
  - expr: "has_item('DIAMOND', 3)"
    deny: [message: "<red>Te faltan diamantes"]
  - any: 2                      # al menos 2 de estas 3
    of: ["perm('vip')", "pvar.racha >= 5", "hour() >= 20"]
```
**Riesgo:** compatibilidad con la forma de lista actual; se mantiene la forma corta (texto = condición sin `deny`).

### P2 · `/omni check`, simulación y traza exportable — S, riesgo bajo
**Qué es:**
- `/omni check <regla> [jugador]` evalúa las condiciones ahora, con cada valor, sin ejecutar nada.
- `/omni run <regla> <jugador> --simular` recorre las acciones y las lista sin aplicarlas.
- `/omni debug export` guarda la traza en `plugins/OmniEvents/trazas/<fecha>.txt` para compartirla, sin red.

**Por qué gana:** ConditionalEvents solo tiene `debug`. BetonQuest y Denizen son los preferidos justamente por esto, y
la traza para compartir (como `/denizen submit`) acelera el soporte.

**Ejemplo:** `/omni check spawn_vip Ana` muestra `✘ perm('vip') → false`, `✔ cooldown → 0s`.

**Riesgo:** simular acciones con efectos externos (`console`) no se puede: se listan sin correr.

### P3 · Esquema JSON y referencia generados del código — S/M, riesgo bajo
**Qué es:**
- `gradlew :omnievents:schema` (y `/omni schema`) genera `omnievents.schema.json` con cada disparador, acción, opción y
  función, más una referencia Markdown, todo desde el mismo registro del código.
- Con la extensión YAML de VS Code, el administrador tiene autocompletado, documentación al pasar el ratón y errores
  mientras escribe.

**Por qué gana:** Denizen es el único con esto, por su extensión, y ningún plugin de eventos YAML lo tiene. La
documentación nunca queda desactualizada, que es la queja de Skript, AuraSkills e ItemsAdder.
```yaml
# yaml-language-server: $schema=https://…/omnievents.schema.json   (o una ruta local)
mi_regla:
  trigger: blo|        # ← sugiere block_break, block_place…
```
**Riesgo:** las expresiones dentro de las cadenas no se validan con el esquema: eso sigue en `/omni validate`.

### P4 · Perfilador `/omni cpu` y presupuesto por regla — S, riesgo bajo
**Qué es:** el tiempo por regla, por condición y por acción (en microsegundos y en % de un tick), los oyentes más
caros y un aviso si una regla pasa su presupuesto.
**Por qué gana:** es lo que hizo famoso a TAB, y ConditionalEvents no lo tiene. Le da al dueño un argumento
medible («OmniEvents gasta 0,3 % del tick»).
```yaml
cara:
  trigger: player_move
  budget: 0.5ms        # avisa en consola y en /omni cpu si el promedio lo pasa
```
**Riesgo:** medir también cuesta; se mide por muestreo (1 de cada N) y solo con el perfilador encendido.

### P5 · Variables con caducidad y en cualquier objeto — M, riesgo medio
**Qué es:** `expire` al guardar, `ttl(pvar.x)` para el tiempo restante, y variables de bloque, entidad e ítem (en el
`PersistentDataContainer` de Paper, que viaja con el objeto).
**Por qué gana:** resuelve peticiones abiertas de ConditionalEvents (rachas, cuenta regresiva, ítems marcados) sin otro
plugin. Hasta ahora solo Denizen lo tiene.
```yaml
- set: "pvar.racha += 1"
  expire: 10m                       # la racha se pierde tras 10 min sin kills
- set: "item.nbt.ligada = player.name"   # el ítem recuerda a su dueño
- actionbar: "Racha x{pvar.racha} · {ttl(pvar.racha)}s"
```
**Riesgo:** el formato de los datos persistentes cambia y necesita migración (constitución IV.5). El PDC tiene un
límite de tamaño.

### P6 · Disparador de estado: «cuando X pasa a ser verdad» — M, riesgo medio
**Qué es:** `trigger: state` con `when:` y acciones `on_enter` y `on_exit`. El motor deduce de qué eventos depende la
expresión (vida → daño y curación; ubicación → movimiento de bloque) y solo la reevalúa ahí, sin sondear. Cuando no
puede deducirlo, usa un intervalo declarado.
**Por qué gana:** en ConditionalEvents y MythicMobs (`onTimer`) esto se hace sondeando cada X ticks, que es la
mayor causa de lag. Ningún rival tiene disparadores por flanco.
```yaml
vida_baja:
  trigger: state
  when: "player.health <= 6"
  on_enter: [title: "<red>¡Peligro!;Busca refugio", effect: "REGENERATION;3;1"]
  on_exit:  [actionbar: "<green>A salvo"]
```
**Riesgo:** deducir las dependencias de una expresión es lo difícil. Hay que probar bien el respaldo con intervalo y el
estado por jugador (memoria).

### P7 · Pruebas de reglas en el YAML — M, riesgo medio
**Qué es:** una sección `tests:` junto a la regla, con el contexto de entrada (propiedades del evento, variables del
jugador) y lo que se espera (acciones, variables). `/omni test [regla]` las corre en el servidor sin efectos, y
`gradlew` las corre en CI.
**Por qué gana:** nadie lo tiene. Ataca la queja número uno de todo el ecosistema: «se rompió al actualizar y no me
enteré». Es la metodología de la fábrica, puesta en manos del dueño del servidor.
```yaml
diamantes:
  trigger: block_break
  conditions: ["block.type == 'DIAMOND_ORE'"]
  actions: [set: "pvar.diamantes += 1"]
  tests:
    - given: { block.type: DIAMOND_ORE, pvar.diamantes: 9 }
      expect: { pvar.diamantes: 10 }
    - given: { block.type: STONE }
      expect: { fired: false }
```
**Riesgo:** fingir eventos y jugadores sin servidor. Empezar por condiciones, `set` y mensajes, y dejar el mundo para después.

### P8 · Paquetes con parámetros, plantillas y versión — M, riesgo medio
**Qué es:** una carpeta con `pack.yml` (nombre, versión, `requires`, parámetros), reglas referidas como `pack:regla`,
`extends:` para heredar de una plantilla, y `/omni pack list|enable|disable`. La versión del paquete alimenta
migraciones automáticas.
**Por qué gana:** BetonQuest y AuraSkills muestran que las configuraciones compartibles crean un mercado; ConditionalEvents
no tiene espacios de nombres. Sumado al importador, cambiarse a OmniEvents se vuelve barato.
```yaml
# rules/recompensas/pack.yml
name: recompensas
version: 1.2.0
params: { moneda: "pvar.monedas", diaria: 50 }
# rules/recompensas/diaria.yml
diaria:
  extends: plantillas:recompensa_basica
  actions: [set: "{param.moneda} += {param.diaria}"]
```
**Riesgo:** resolver la herencia y los nombres bien desde el principio: después cambiarlos rompe configuraciones.

### P9 · Costos declarativos y transaccionales — M (depende de Vault), riesgo medio
**Qué es:** `cost:` cobra dinero (Vault), niveles, ítems o variables **antes** de las acciones, todo o nada, con
`on_cost_fail`.
**Por qué gana:** Citizens lo tiene en los NPC; ConditionalEvents pide encadenar condiciones y comandos de consola (si
uno falla a mitad, cobra igual).
```yaml
cost: { money: 500, items: "DIAMOND;3", pvar.fichas: 1 }
on_cost_fail: [message: "<red>Te falta: {var.cost_missing}"]
```
**Riesgo:** es la spec de integraciones (Vault) del roadmap. Hay que hacer el reembolso si un cobro posterior falla.

### P10 · Menús de inventario y formularios de Bedrock — L, riesgo alto
**Qué es:** un acción `menu:` que abre un inventario declarado en YAML, con requisitos y acciones por clic (con las
expresiones de OmniEvents). Si el jugador viene de Bedrock y está Floodgate, se muestra como formulario nativo.
**Por qué gana:** junta en un solo plugin lo que hoy hacen DeluxeMenus y ConditionalEvents, con un lenguaje mejor.
**Riesgo:** el alcance es grande (paginación, ítems dinámicos, refresco). Recortarlo a menús estáticos con requisitos
en la primera spec.

### P11 · Editor visual sin servidor web propio — L, riesgo alto
**Qué es:** `/omni editor` exporta las reglas a un archivo HTML local, con el esquema de P3 y una vista de árbol para
editar y validar. El resultado se pega o se copia a `rules/`. Más adelante, una sesión en vivo al estilo LuckPerms,
con clave aprobada por el administrador.
**Por qué gana:** es la queja de BetonQuest y la fuerza de CommandPanels y LuckPerms. Ningún plugin de eventos YAML lo
tiene. Hay que decidirlo frente al «editor en el juego» del roadmap.
**Riesgo:** la versión en vivo necesita red, y la constitución (II.4) la pide opcional y activada por el dueño. Tiene
el costo más alto de la lista. Depende de P3.

## 5. Orden recomendado
| # | Propuesta | Costo | Valor | Riesgo |
|---|---|---|---|---|
| 1 | P1 Ramas por condición y «N de M» | S | Alto | Bajo |
| 2 | P2 `/omni check`, simulación y traza | S | Alto | Bajo |
| 3 | P3 Esquema JSON y referencia generada | S/M | Alto | Bajo |
| 4 | P4 Perfilador `/omni cpu` | S | Medio-alto | Bajo |
| 5 | P6 Disparador de estado | M | Alto | Medio |
| 6 | P5 Variables con caducidad y en objetos | M | Alto | Medio |
| 7 | P7 Pruebas de reglas en el YAML | M | Alto (único) | Medio |
| 8 | P8 Paquetes, plantillas y versión | M | Medio-alto | Medio |
| 9 | P9 Costos transaccionales (con Vault) | M | Medio | Medio |
| 10 | P10 Menús y formularios de Bedrock | L | Alto | Alto |
| 11 | P11 Editor visual | L | Medio-alto | Alto |

Sugerencia: P1 + P2 + P4 caben juntas en una spec «herramientas de autor» (son S y tocan lo mismo), y P3 en otra.
Con eso, OmniEvents queda por delante de todos los rivales en experiencia del administrador, en pocas sesiones.

## 6. Elección del dueño (2026-10-24)
**Todas menos P11** (el editor visual queda fuera por ahora). Son 10 propuestas, un alcance grande: se agrupan en
8 specs, una por sesión y en este orden. Las propuestas chicas que tocan lo mismo van juntas. Costo total estimado:
unas 16–20 sesiones.

| Orden | Spec | Propuestas | Costo | Por qué en este lugar |
|---|---|---|---|---|
| 1 | OmniEvents: herramientas de autor | P1 + P2 + P4 | M (3 × S) | El mayor valor por costo; P2 y P4 se apoyan en `/omni debug` y `stats` |
| 2 | OmniEvents: esquema JSON y referencia generada | P3 | S/M | Las specs siguientes agregan sintaxis y la documentan solas |
| 3 | OmniEvents: disparador de estado | P6 | M | Lo más innovador del motor; no cambia los datos persistentes |
| 4 | OmniEvents: variables con caducidad y en objetos | P5 | M | Cambia el formato de los datos: migración con respaldo (constitución IV.5) |
| 5 | OmniEvents: pruebas de reglas en el YAML | P7 | M | Necesita un contexto falso; va mejor con el lenguaje ya estable |
| 6 | OmniEvents: paquetes, plantillas y versión | P8 | M | Definir los nombres antes de que haya paquetes compartidos |
| 7 | OmniEvents: integraciones con costos | P9 + Vault | M | Junta P9 con la spec de integraciones del roadmap |
| 8 | OmniEvents: menús y formularios de Bedrock | P10 | L | Lo más grande: la primera versión, solo menús estáticos con requisitos |

Al abrir cada una: revisar la spec 003 (paridad con ConditionalEvents) para no duplicarla, y confirmar el alcance con
el dueño antes de `plan.md`.

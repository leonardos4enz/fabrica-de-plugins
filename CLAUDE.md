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
- **Una spec por sesión.** Esta sesión trabaja solo su spec, de SPECIFY a VERIFY y commit. La siguiente spec se abre
  en una sesión nueva.
- Flujo: `spec.md` → `plan.md` (contrato de configuración y STRIDE) → `tasks.md` → implementar → `verify.md`.
- Toda capacidad nueva es **genérica** y se ejerce con configuración (un ejemplo en `src/main/resources/`).
- Cada bug encontrado se vuelve prueba **y se verifica que la prueba falla con el código anterior**.
- Plugin nuevo: `powershell -File tools/nuevo-plugin.ps1 -Nombre MiPlugin -Spec 00N` (proyecto + spec con plantillas).

## Comandos
```bash
./gradlew build                      # compila y prueba todos los plugins
./gradlew :omnievents:test           # unitarias de un plugin
./gradlew :omnievents:dist           # jar en dist/
bash tools/compuerta-servidor.sh omnievents "omni selftest"   # Paper real (el EULA lo acepta el dueño)
```
Gradle 8.14 no corre sobre Java 25: usar el JDK 24 (`JAVA_HOME=C:\Program Files\Java\jdk-24`). El código se compila
con `--release 21`.

## Lecciones aprendidas (no repetir)
- **PowerShell 5.1 corrompe UTF-8** con `Get-Content`/`Set-Content`. Usar las herramientas de edición,
  `[IO.File]::WriteAllText(..., UTF8 sin BOM)` o Bash.
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

## Antes de cada commit
- Sin secretos: tokens, contraseñas, claves.
- Sin artefactos: `build/`, `.gradle/`, `dist/`, `run/` (servidores de prueba), `*.class`.

## Commits
Mensaje en español, describiendo el qué y el porqué, terminando con:
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`

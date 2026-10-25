#!/usr/bin/env bash
# Compuerta 2 de VERIFY (docs/sdd/verificacion.md): arranca un Paper real con el plugin, espera a que termine de
# cargar, manda comandos de consola, apaga y vuelve a arrancar para comprobar que los datos persisten.
#
# Uso: bash tools/compuerta-servidor.sh <plugin> "<cmd1>" "<cmd2>" ...
#   p. ej. bash tools/compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni reload" "omni reload" "omni stats"
#
# Requisitos: plugins/<plugin>/run/ con el jar de Paper (paper-*.jar) y eula.txt aceptado POR EL DUEÑO.
# Descarga oficial de Paper (API Fill de PaperMC): https://fill.papermc.io/v3/projects/paper/versions/<ver>/builds/latest
# Logs: run/run1.log (comandos) y run/run2.log (segundo arranque).
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PLUGIN="$1"
shift
RUN="$ROOT/plugins/$PLUGIN/run"
JAVA="${JAVA_HOME:-/c/Program Files/Java/jdk-24}/bin/java"
cd "$RUN" || { echo "no existe $RUN"; exit 1; }
[ -f eula.txt ] && grep -q "eula=true" eula.txt || { echo "el EULA de Minecraft lo acepta el dueño en $RUN/eula.txt"; exit 1; }
JAR="$(ls paper-*.jar | head -1)"
mkdir -p plugins
# Solo el jar más reciente de este plugin: con dos versiones en dist/ Paper no carga ninguna («Ambiguous plugin
# name»; regresión 005 F-01).
PJAR="$(ls -t "$ROOT"/dist/*.jar 2>/dev/null | grep -i "/$PLUGIN-[^/]*\.jar$" | head -1)"
[ -n "$PJAR" ] || { echo "no hay jar de $PLUGIN en dist/: ./gradlew :$PLUGIN:dist"; exit 1; }
find plugins -maxdepth 1 -iname "$PLUGIN-*.jar" -delete
cp "$PJAR" plugins/
# Los ejemplos tal como están en el código fuente: el plugin solo los copia si su carpeta no existe, y la compuerta
# probaba los de una versión vieja (regresión 010 F-02; la suite ya lo hacía, 002 F-05).
NAME="$(grep -m1 '^name:' "$ROOT/plugins/$PLUGIN/src/main/resources/plugin.yml" 2>/dev/null | sed 's/^name:[[:space:]]*//')"
EJ="$ROOT/plugins/$PLUGIN/src/main/resources/rules/ejemplos"
if [ -n "$NAME" ] && [ -d "$EJ" ] && [ -d "plugins/$NAME/rules" ]; then
  rm -rf "plugins/$NAME/rules/ejemplos"
  cp -r "$EJ" "plugins/$NAME/rules/ejemplos"
fi

wait_done() {
  for _ in $(seq 1 300); do
    grep -q "Done (" logs/latest.log 2>/dev/null && return
    sleep 1
  done
}

run_server() { # $1 = log de salida; el resto, comandos
  local out="$1"
  shift
  rm -f logs/latest.log
  {
    wait_done
    sleep 3
    for c in "$@"; do
      echo "$c"
      sleep 2
    done
    echo "stop"
  } | "$JAVA" -Xms512M -Xmx1536M -jar "$JAR" --nogui > /dev/null 2>&1
  cp logs/latest.log "$out"
}

run_server run1.log "$@"
run_server run2.log
echo "== Errores del servidor (run1, run2):"
grep -hnE "Exception|ERROR|SEVERE" run1.log run2.log | grep -v "No key layers" || echo "(ninguno)"

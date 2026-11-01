#!/usr/bin/env bash
# Renderiza un HTML a PNG con Chrome sin ventana: render.sh archivo.html ancho alto
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
f="$1"; w="$2"; h="$3"
abs="$(cd "$(dirname "$f")" && pwd -W)/$(basename "$f")"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --virtual-time-budget=6000 \
  --default-background-color=00000000 --window-size="$w,$h" --screenshot="${abs%.html}.png" "file:///$abs" 2>/dev/null

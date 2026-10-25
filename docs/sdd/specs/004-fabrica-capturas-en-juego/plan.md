# Plan 004 — Diseño técnico de las capturas en el juego

## 1. Arquitectura
Reutiliza el arnés de la spec 002 (`Servidor`, `Jugador`) y agrega un corredor de capturas y un renderizador.

```
tests-e2e/
  capturas.mjs              corredor: compila, instala, arranca, recorre escenas, compara, escribe galería, apaga
  lib/capturas/assets.mjs   busca el cliente instalado y extrae a tests-e2e/.cache/ lo que hace falta (no se versiona)
  lib/capturas/fuente.mjs   fuente de Minecraft: proveedores bitmap, espacio y unihex (unifont a media escala)
  lib/capturas/hud.mjs      componentes de texto → segmentos; chat, barra, títulos, bossbar, HUD, efectos,
                            inventarios con tooltip, pantalla de desconexión, consola
  lib/capturas/visor.mjs    vista 3D: prismarine-viewer (WorldView) sin las entidades que no sabe dibujar
  lib/capturas/png.mjs      comparación de píxeles con la imagen anterior y escritura solo si cambió
  lib/capturas/galeria.mjs  genera docs/<plugin>/galeria.md desde las escenas (y el YAML de cada regla)
  lib/capturas/zip.mjs      lector de ZIP mínimo para el jar del cliente (sin dependencias)
  lib/capturas/*.test.mjs   unitarias (node:test): componentes, traducciones, anchos de la fuente, comparación, ZIP
  <plugin>/capturas.mjs     escenas del plugin (configuración + una función por escena)
  <plugin>/capturas/        reglas de demostración (YAML del plugin), copiadas a rules/capturas/ y retiradas al final
  <plugin>/capturas-*/      material de una sola escena (regla con errores para validate, config de ConditionalEvents)
```

Flujo de `capturas.mjs`:
1. `assets.prepararAssets(version)` — localiza `%APPDATA%/.minecraft` (o `FABRICA_MINECRAFT`), extrae del jar de la
   versión la fuente (`font/`, `textures/font/`), los sprites del HUD, la GUI de contenedores, ítems y efectos, y del
   índice de assets `unifont.zip` y `es_mx.json`. Sin cliente: error con la ruta que buscó.
2. Igual que la suite: compila, copia el jar, los ejemplos y las reglas de demostración; propiedades seguras;
   olvida los datos de los bots; arranca.
3. Prepara el escenario: plataforma de pasto de 33×33 en y = 99, `setworldspawn 12 100 12`, mediodía fijo, sin clima
   ni criaturas naturales. Entran `Beto` (observador) y `Ana` (protagonista, primera entrada: ejemplo `bienvenida`).
4. Abre una vista 3D por bot (prismarine-viewer en `127.0.0.1`, una página de Chrome sin interfaz por bot).
5. Cada escena: restaura el estado (`prepararJugador`), corre sus comandos de consola, hace que el bot actúe, espera
   lo que debe llegar y pide la foto. La foto = captura de la vista 3D del bot elegido + HUD dibujado con lo que ese
   bot recibió desde la marca de la escena, recortado.
6. Cobertura (H3 CA3): lee `/omni triggers`, `/omni actions` y la ayuda de `/omni` desde la consola y falla si algo no
   tiene escena ni entrada de solo texto.
7. Compara cada PNG con el que ya estaba (H4), escribe solo los cambiados, genera la galería y apaga (`finally`).

### Por qué un render y no una captura del cliente
El cliente oficial necesita la cuenta del dueño y no es determinista (nubes, animaciones, partículas). El render parte
de los mismos datos que dibuja el cliente (componentes de texto, estado del jugador, chunks) con sus mismos assets,
así que es reproducible píxel a píxel y se regenera en cada versión sin una persona.

### Fidelidad del HUD (1.21.4, escala de GUI 2, 854×480)
- Fuente: el primer proveedor que tiene el carácter; avance = (última columna con píxeles + 1) × escala + 1;
  negrita = se dibuja dos veces con 1 px de corrimiento y avance + 1; sombra = color / 4 corrida 1 px.
- Chat: abajo a la izquierda (fondo negro 50 %, 320 px de ancho, líneas de 9 px), 10 líneas; con `chatAbierto`,
  20 (como con T abierto: así cabe la salida de `/omni`). Barra de acción a 68 px del borde inferior. Título × 4 y
  subtítulo × 2 al centro. Bossbar de 182 × 5 arriba. Vida, comida, experiencia y hotbar con sus sprites.
- Componentes: `text`, `translate` (con `with` y `%1$s`), `extra`, colores con nombre y `#RRGGBB`, estilos.

## 2. Contrato de configuración (escenas de un plugin)
```js
export default {
  version: '1.21.4',                    // jar del cliente del que salen los assets y versión del bot
  copiar: [{ de, a, retirar }],         // como la suite
  bots: ['Ana', 'Beto'],
  salida: 'docs/img/omnievents',
  galeria: { archivo: 'docs/omnievents/galeria.md', titulo: 'OmniEvents', grupos: [...] },
  cobertura: { disparadores: 'omni triggers', acciones: 'omni actions' },   // se comparan con las escenas
  escenas: [{
    id: 'accion-title',                 // nombre del archivo y ancla de la galería
    grupo: 'Acciones · Mensajes',
    cubre: ['action:title'],            // capacidades que muestra (trigger:x, action:x, command:x, example:x)
    titulo: 'title',
    texto: 'Título y subtítulo en el centro de la pantalla.',
    regla: 'cap_title',                 // regla de demostración cuyo YAML se muestra en la galería
    camara: 'Ana',                      // de qué bot es la vista y el HUD
    recorte: 'titulo',                  // completa | chat | barra | titulo | hud | gui
    varia: false,                       // H4 CA2
    async fn (ctx) { … }                // prepara, actúa, espera; ctx.foto({ chatAbierto, gui, … })
  }, {
    id: 'accion-sound', cubre: ['action:sound'], soloTexto: 'El sonido no se ve: …'
  }]
}
```

### Lo que el visor 3D no dibuja (y cómo se trata)
- Solo y = 0…255: escenario en y = 100. Bloques con entidad propia (cofres, camas, carteles, mesas): no se ven; la
  escena lo dice. Ítems tirados, relámpagos, partículas y jugadores: se filtran (saldrían como cubos magenta) y esas
  acciones quedan como solo texto.
- Ajustes al cliente automático (mineflayer 4.39 en 1.21.4): agacharse se manda con `entity_action` y el nombre de la
  acción; volar, con el paquete `abilities`; tras cancelar la rotura de un bloque en creativo se le reenvía el bloque.
- `spawn-monsters=false` (el de la suite) borra al instante hasta los monstruos invocados: las capturas lo activan
  (`propiedades` de la configuración; las criaturas naturales siguen apagadas con `doMobSpawning false`).

## 3. Hilos y rendimiento
Todo corre en Node fuera del servidor; el servidor no cambia. Una escena dura 1–4 s; la corrida completa, unos
minutos. El visor usa WebGL por software (SwiftShader) en Chrome sin interfaz.

## 4. Análisis de amenazas (STRIDE)
| Amenaza | Riesgo | Mitigación |
|---|---|---|
| Suplantación | Bots sin cuenta | Igual que la 002: `online-mode=false` solo en el servidor de prueba, `server-ip=127.0.0.1` |
| Manipulación | Reglas de demostración en un servidor real | Solo se copian a `plugins/<plugin>/run/` y se retiran al terminar |
| Repudio | — | El log del servidor queda en `run/e2e.log` (el mismo de la suite) |
| Divulgación | Assets de Minecraft redistribuidos | Se leen del cliente del dueño a `tests-e2e/.cache/` (ignorado por git); al repo solo van los PNG finales |
| Divulgación | Visor 3D accesible desde la red | El servidor del visor escucha solo en `127.0.0.1` |
| Divulgación | Credenciales del dueño | No se lee ningún archivo de cuenta del lanzador; solo `versions/<v>/<v>.jar` y `assets/` |
| Denegación | Chrome o el servidor colgados | Tiempo límite por escena; `finally` cierra Chrome, bots y servidor |
| Elevación | OP de los bots | Se retira al terminar (`deop`) |

## 5. Dependencias
`prismarine-viewer` 1.33 (vista 3D, texturas de minecraft-assets), `canvas` 3 (dibujo y PNG), `puppeteer-core` 25
(maneja el Chrome o Edge instalado; no descarga navegador). Van en `optionalDependencies` de `tests-e2e`: si `canvas`
no compila en una máquina, `npm test` sigue funcionando.

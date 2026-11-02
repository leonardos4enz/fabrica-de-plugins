# Plan 038 — Página de presentación y publicación de Worldcraft

## 1. Recuento (H1)
- `inventario` (en el scratchpad de la sesión, fuera del repositorio, como en la 029): descomprime las clases de
  comandos de `worldedit-bukkit-7.3.19.jar` y `FastAsyncWorldEdit-Paper-2.16.2.jar` (los de `tests-e2e/.cache/plugins/`,
  con el sha256 fijado) y lee con `javap -v` cada `@org.enginehub.piston.annotation.Command` (nombre y alias) por clase.
  Resultado: **171** en WorldEdit y **265** en FAWE, de los que 21 son `AnvilCommands` (no registrados, 029): **244**.
  Coincide con la 029.
- Estado de cada uno frente a la 0.13.0: una tabla escrita a mano, comando por comando, revisada contra el README y
  `WorldcraftCommand` (`y` lo tiene, `p` en parte, `n` falta, `x` no se hará, `s` no hace falta).

  | | Total | Lo tiene | En parte | Falta | No se hará | No hace falta |
  |---|---|---|---|---|---|---|
  | WorldEdit 7.3.19 | 171 | **129** (75 %) | 5 | 28 | 4 | 5 |
  | FAWE 2.16.2 | 244 | **142** (58 %) | 12 | 68 | 14 | 8 |
  | FAWE, solo lo que agrega a WorldEdit | 79 | 14 | 8 | 44 | 10 | 3 |

- Worldcraft: **102** subcomandos de `/wc` (las 103 entradas de `help:` en `messages.yml` menos `sp`, alias de
  `superpickaxe`). Contados como WorldEdit (cada pincel, herramienta y subcomando aparte, sin los grupos `brush`,
  `tool`, `schem` y `snapshot`): 98 + 19 pinceles (12 de construcción, 6 modos de terreno y `none`) + 10 herramientas
  (9 y `none`) + 5 de `schem` + 5 de `snapshot` + 3 modos del superpico = **140**.
- `docs/worldcraft/presentacion/commands.md` (en inglés): los comandos de Worldcraft por grupo y la tabla de
  WorldEdit y FAWE con el equivalente de cada uno. §5 nueva en la investigación con el recuento.

## 2. Póster (H2)
Misma técnica que la 020 y la 024: `docs/site/assets-src/worldcraft.html` (`?pieza=…`), salida en
`docs/img/worldcraft/presentacion/` con `npm --prefix tests-e2e run presentacion -- --plugin worldcraft`.

| # | Franja | Capa |
|---|---|---|
| 1 | Cabecera: logo, «Edit the world. Never freeze the server.», píldoras (Paper 1.21.4, 102 commands, no WorldEdit needed, reads `.schem`) | cielo |
| 2 | «A mountain in one word»: la escena `terrain` y los comandos | cielo → pasto |
| 3 | «See it before it happens»: `shapes` y `preview` | pasto → tierra |
| 4 | «Worldcraft vs WorldEdit vs FAWE»: tabla de capacidades (sí/no/en parte) | tierra → piedra |
| 5 | «102 commands»: las cantidades de §1 y los nombres por grupo | piedra |
| 6 | «What it does»: seis tarjetas (sin lag, vista previa, deshacer en disco y seguro, patrones legibles, límites, API) | pizarra |
| 7 | «How to start» como barra de acceso rápido | roca madre |

Logo: un bloque de pasto isométrico partido por una «varita» dorada, con el borde de la selección en partículas
(pixel art propio, sin texturas del juego).

## 3. Escenas (H3)
`tests-e2e/worldcraft/presentacion.mjs` (como `tests-e2e/skinstatues/presentacion.mjs`): servidor en
`plugins/worldcraft/run-capturas/` (puerto 25597, Paper 1.21.4 copiado de `plugins/worldcraft/run/`, el EULA ya
aceptado), mundo de terreno normal con semilla fija, rehecho en cada corrida. Todo desde la consola (`at=…`,
`--at x,y,z --world world`, `--now`), un bot espectador con prismarine-viewer hace la foto.

| Escena | Contenido |
|---|---|
| `terrain` | `/wc mountain large` y `/wc island large` en el llano, de lejos |
| `shapes` | Una esfera, un cilindro hueco y una pirámide sobre el pasto |
| `image` | El logo de la 038 construido con `/wc image` por el bot (solo funciona en el juego), de pie frente a él. La vista previa se descartó: en una foto no se distingue de una obra real |

## 4. Textos y publicación (H4)
`docs/worldcraft/presentacion/`: `spigotmc.bbcode`, `modrinth-hangar.md`, `publicar.md`, `commands.md`. Imágenes
por `raw.githubusercontent.com/leonardos4enz/fabrica-de-plugins/main/…`: primero push, después publicar. Subir con el
navegador y la sesión del dueño; nada de credenciales.

## 5. Pruebas
`PresentacionTest` de Worldcraft (como la de SkinStatues): mismas imágenes en el mismo orden y que existan, `[IMG]` en
una línea, transcripción, «not affiliated» visible; y **el número de comandos** de las descripciones y de
`commands.md` es el de `messages.yml` (102) y cada `/wc <sub>` de `commands.md` existe en la ayuda.

## 6. Contrato de configuración
Sin cambios: Worldcraft 0.13.0 tal cual, con la configuración del código fuente también en el servidor de capturas.
Las obras de las escenas van desde la consola (sin tope de grupo); sobre `confirm.above_blocks` (50 000) la escena manda
`wc confirm` (verify F-02).

## 7. STRIDE
| Amenaza | Respuesta |
|---|---|
| Suplantación (marcas ajenas) | Sin logos ni texturas de Mojang, WorldEdit o FAWE; se los nombra solo para comparar, con «not affiliated» |
| Alteración (exagerar) | Los números salen del bytecode y del código; solo «lo tiene» cuenta; prueba que ata la cifra al código |
| Divulgación | Credenciales nunca se escriben (sesión del dueño); nada de secretos en el repositorio |
| Alteración (mundos) | `run-capturas/` aparte; `run/` de la suite no se toca |
| Repudio, DoS, elevación | No aplica: no cambia el plugin |

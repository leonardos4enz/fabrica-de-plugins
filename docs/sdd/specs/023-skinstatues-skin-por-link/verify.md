# Verificación 023 — SkinStatues: estatuas con el link de una skin

**Fecha:** 2026-11-01 · **Resultado:** Verificado (compuertas 1, 2 y 3a en Paper 1.21.4 y 26.1.2)

| Compuerta | Resultado |
|---|---|
| Unitarias | `./gradlew :skinstatues:test` en verde: 65 pruebas (eran 51). Nueva `SkinLinkTest` (12: link y huella, rechazos sin red, direcciones públicas y privadas v4/v6/mapeadas, `allowed_hosts`, descarga y redirecciones con red y DNS falsos, SSRF por IP, por nombre y por redirección, errores del sitio, formato, modelo, caché); `ConfigTest` +1 (`network.urls`, `allowed_hosts`, `config.yml` de la 0.2.0); `BuildArgsTest` +1 (`--slim`/`--classic`) |
| Servidor real (carga, validate, selftest, reload, consola) | `bash tools/compuerta-servidor.sh skinstatues "statue validate" "statue selftest" "statue build https://i.imgur.com/abc.png 16 --at 200 -60 200" "statue reload" "statue reload"` en Paper 1.21.4 (build 232) **sin** `plugins/SkinStatues/`: «Configuration OK: 30 poses», `selftest` pasa, el link con la red apagada da «Skin links are off» (sin pedir nada), dos `reload` sin errores, segundo arranque sin excepciones. Segunda corrida con `network.urls: true` y **sin** la opción de prueba: `127.0.0.1`, `localhost` (por DNS real), `192.168.1.1`, `[::1]` y `169.254.169.254` dan «is not a public internet address» y `example.com:8080` «only the standard https port»; ninguna estatua |
| En el juego (3a) | `npm --prefix tests-e2e test -- --plugin skinstatues`: **18/18 dos veces seguidas en Paper 1.21.4** (build 232, Java 24) **y en 26.1.2** (build 74, Java 25, bot 26.1), con el jar 0.3.0; los links los sirve un HTTPS local de la corrida (`tests-e2e/lib/https-local.mjs`, certificado hecho con `keytool`), sin internet; log sin errores |
| Revisión exploratoria (3b) | Con la suite y la compuerta 2: los mensajes de cada rechazo se leyeron en el chat del bot y en la consola; el ancho de la estatua delgada detectada (14) y forzada a clásica (16) a 32 de alto; el servidor del dueño (mundo, 7 estatuas, caché, `network.mojang: true`) se apartó antes y se devolvió después |
| README (capacidades, Compatibilidad, tabla raíz) | README del plugin: sección «Skins from a link» (sintaxis, `network.urls`, `skinstatues.build.url`, validación, modelo, sitios públicos, `allowed_hosts`, caché, `url:sitio#huella`), comando y permiso, `config.yml`, Compatibilidad (0.3.0 en 1.21.4 y 26.1.2). README raíz: fila (0.3.0) y el HTTPS local de la suite. Roadmap e índice. Sin capturas nuevas |

## Cobertura
| Criterio | Prueba |
|---|---|
| H1 CA1 | `SkinLinkTest.H1_CA1_baja_la_imagen…`; 3a «build con un link…» (la estatua de `Skin01.png` con los mismos bloques al repetir, `list`/`info`) |
| H1 CA2 | `SkinLinkTest.H1_CA1_CA2_y_H3_CA1…` (`http` → `https`, misma huella); 3a «http pasa a https…» (el servidor local solo habla https) |
| H1 CA3 | `SkinLinkTest.H1_CA3_el_modelo_se_detecta…`, `BuildArgsTest.spec023_H1_CA3…`; 3a «…el modelo delgado se detecta y --classic lo fuerza» (anchos 14 y 16; los dos a la vez se rechazan) |
| H1 CA4 | `ConfigTest.spec023_H1_CA4_y_H2_CA4…` (apagado por defecto, `config.yml` de la 0.2.0); 3a «links: apagados por defecto…»; compuerta 2 |
| H1 CA5 | 3a «links: apagados por defecto, permiso aparte…» (Beto con `skinstatues.build` y sin `.build.url`: «no permission»); el autocompletado sugiere `https://` a quien puede |
| H1 CA6 | `SkinLinkTest.H1_CA6_y_H3_CA2…` (segunda vez sin pedir; vencida y sin respuesta, la copia); 3a «build con un link…» («skin from cache», el servidor local no recibe otro pedido) |
| H2 CA1 | `SkinLinkTest.H2_CA1_solo_un_formato_de_skin` (HTML, 128×128, JPG, vacío, sin `IHDR`), `…cache…` (un HTML no llega a la caché); 3a «solo una skin válida…» (128×128, página, más de 1 MB; la caché no cambia) |
| H2 CA2 | `SkinLinkTest.H2_CA2_links_que_se_rechazan_sin_pedir_nada`; 3a (usuario y contraseña en el link); compuerta 2 (otro puerto) |
| H2 CA3 | `SkinLinkTest.H2_CA3_solo_direcciones_publicas`, `H2_CA3_nada_a_la_red_local…` (IP, nombre que resuelve a la LAN, nombre con una IP pública y una privada, redirecciones a localhost, http, otro puerto u otro esquema); compuerta 2 con el DNS y la red reales |
| H2 CA4 | `SkinLinkTest.H2_CA4_lista_de_sitios`, `H2_CA4_sitios_fuera_de_la_lista…` (también tras redirección), `ConfigTest.spec023…` (sitios inválidos; una lista sin ninguno válido cierra los links); 3a («Links from 127.0.0.1 are not allowed. Allowed sites: …») |
| H2 CA5 | `SkinLinkTest.H2_CA5_errores_del_sitio…` (sin respuesta, 404, más de 3 redirecciones, más de 1 MB, redirección sin destino); 3a (404 y un sitio que manda la cabecera y no termina: «no answer» a los 8 s, el servidor sigue) |
| H2 CA6 | Revisión de `LinkClient.realHttp` (solo `User-Agent` y `Accept`, sin `CookieHandler`, sin seguir redirecciones solo); la imagen solo se escribe en `cache/links/` |
| H3 CA1 | `SkinLinkTest.H1_CA1_CA2_y_H3_CA1…`; 3a «build con un link…» (`list` e `info` con `url:127.0.0.1#huella`) |
| H3 CA2 | `SkinLinkTest.H1_CA6_y_H3_CA2…` (nombres y `.yml` de la caché sin el link); 3a (`data/statues.yml`, nombres de la caché y el log sin el link, salvo la línea «issued server command» que Paper escribe para todo comando) |

## Hallazgos
Ninguno del plugin. Al escribir la suite salieron tres errores **de la suite** (no del plugin, sin prueba de regresión
propia; quedan como lección en CLAUDE.md): el `trustStore` con solo el certificado local rompía el HTTPS de Paper
(«Failed to request yggdrasil public key»), `permissions.yml` no puede redefinir un permiso del `plugin.yml`, y
`statue undo` de la consola deshace la última de la consola, no la de Ana.

## Riesgos residuales
- *DNS rebinding*: se comprueban las direcciones y después `HttpClient` vuelve a resolver; lo cubre la caché de DNS de
  Java (30 s por defecto). Si el dueño la apaga (`networkaddress.cache.ttl=0`), un sitio malicioso podría cambiar de
  dirección entre la comprobación y la conexión. Con `allowed_hosts` el riesgo desaparece para sitios de confianza.
- Un proxy HTTP configurado en la JVM (`https.proxyHost`) haría que la comprobación de direcciones no sea la de la
  conexión real; no es la configuración por defecto.
- Paper anota cada comando de un jugador en su log, link incluido (no es del plugin; el README lo dice).
- La detección del modelo es una heurística: una skin delgada con píxeles opacos en las columnas libres sale clásica
  (`--slim` lo arregla).
- La descarga real por internet no se probó (constitución y pedido: nada de internet en la suite); sí la pila real de
  `HttpClient` con TLS contra el servidor local y el DNS real en la compuerta 2.
- El servidor de prueba del dueño conserva su `config.yml` de la 0.2.0: para usar links hay que agregar `urls: true`
  bajo `network` (o borrar el archivo para que el jar cree el nuevo).

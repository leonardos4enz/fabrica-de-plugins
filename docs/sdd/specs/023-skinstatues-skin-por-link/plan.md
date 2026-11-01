# Plan 023 — Skins por link

Sobre el diseño de la [021](../021-skinstatues-nucleo/plan.md) y la [022](../022-skinstatues-poses-y-coordenadas/plan.md):
el voxelizador, la paleta, el diario y la cola no cambian. Cambian `skin` (link, descarga con guardas, detección del
modelo), `config` (`network.urls`, `network.allowed_hosts`), el comando y el índice (la etiqueta del link).

## 1. Clases nuevas (Java puro, sin Bukkit)

| Clase | Qué hace |
|---|---|
| `skin.SkinLink` | Lee y normaliza el argumento: `looksLike(arg)` (empieza por `http://` o `https://`), `parse(arg)` → URI `https`, sitio, `hash` (SHA-256 del link normalizado, hex) y `label()` = `url:<sitio>#<hash[0..8]>`. Rechaza (clave `invalid-link` con el motivo): más de 512 caracteres, caracteres fuera de los de una URL (RFC 3986), sin sitio, con usuario (`user@`), con puerto distinto de 443, otro esquema. `http` pasa a `https` (H1 CA2) |
| `skin.LinkPolicy` | `isPublic(InetAddress)`: no loopback, cualquiera, enlace local, sitio local (10/8, 172.16/12, 192.168/16), multidifusión, 0/8, 100.64/10 (CGNAT), 192.0.0/24, 198.18/15, 240/4 y difusión; en IPv6 además `fc00::/7` y las IPv4 mapeadas o compatibles se miran como IPv4 (H2 CA3). `hostAllowed(host, patterns)`: igual o `*.dominio` (sin el dominio pelado) (H2 CA4) |
| `skin.LinkClient` | Baja la imagen: valida el link, resuelve el sitio (`Resolver` inyectable) y exige que **todas** sus direcciones sean públicas, pide sin seguir redirecciones (`Http` inyectable, devuelve estado, `Location` y cuerpo con tope de 1 MB + 1), y sigue hasta 3 redirecciones (301/302/303/307/308) revalidando cada destino (esquema, puerto, lista de sitios, direcciones). 200 → bytes (si pasan 1 MB: «too large»); otro estado → `link-error` con el motivo. Bloqueante: se llama desde un hilo asíncrono |
| `Skin.detectSlim(BufferedImage)` | 64×64 y la zona de 2 columnas que solo usan los brazos anchos (x 54–55 de las caras del brazo derecho, y 20–31, y x 46–47 del izquierdo, y 52–63) toda transparente → delgada (H1 CA3). `Skin.withSlim(boolean)` para forzarlo |

`SkinPng.read` gana una comprobación: los bytes 12–15 son `IHDR` (el primer bloque de una PNG), así el tamaño leído es
el de verdad (H2 CA1).

La red real (`LinkClient.realHttp()`): `java.net.http.HttpClient` con `Redirect.NEVER`, tiempo de conexión y de pedido de
8 s, solo `User-Agent: SkinStatues`, sin cookies (`HttpClient` no tiene `CookieHandler` por defecto) y lectura con tope.
`InetAddress.getAllByName` antes de pedir llena la caché de DNS de Java (30 s por defecto) que usa `HttpClient`, así
que el sitio que se conecta es el que se comprobó (ver riesgos).

## 2. Repositorio y caché (`SkinRepository.loadLink`)
- `cache/links/<hash completo>.png` y `.yml` (`time`, `host`): el nombre del archivo es la huella, nunca el link (H3 CA2).
- Fresca (menos de `cache_hours`) → de la caché, sin red. Si no, se baja; si falla con un error de red o del sitio y hay
  copia, se usa la copia (H1 CA6); un error de formato no usa la copia (la imagen nueva no es una skin).
- Se valida con `SkinPng.read(…, SKIN, label)` **antes** de guardar: lo que no es una skin no llega a la caché.
- Modelo: `Skin.detectSlim` sobre la imagen. Sin capa.
- `--slim`/`--classic` (`Boolean slim` en la petición) se aplican después con `withSlim`, para cualquier origen.

## 3. Contrato de configuración (cambios)
```yaml
# config.yml
network:
  mojang: false
  # Skins from a link: /statue build https://i.imgur.com/abc.png 32. OFF by default.
  urls: false
  # Sites allowed for links. Empty = any public site. "*.imgur.com" also allows i.imgur.com.
  allowed_hosts: []
  cache_hours: 24
```
Un `config.yml` de la 0.2.0 sin estas claves carga igual (IV.5): `urls` falso y `allowed_hosts` vacía. Un elemento de
`allowed_hosts` que no es un sitio (`[a-z0-9.-]` con `*.` opcional al principio) se informa con su ruta y se omite.

Comando:
| Subcomando | Cambio |
|---|---|
| `build <name\|link> <height> [pose] … [--slim\|--classic]` | Un link en lugar del nombre (permiso `skinstatues.build.url`); `--slim`/`--classic` |

`plugin.yml`: permiso `skinstatues.build.url` (`default: op`). Mensajes nuevos (en inglés): `links-off`, `invalid-link`,
`link-error`, `host-not-allowed`, `loading-link`, `model-conflict` (`--slim` y `--classic` a la vez). `planned` y `done`
reciben la etiqueta (`url:sitio#huella`) en `{name}` y `source` dice `link (sitio)` o `cache`. Un `messages.yml` de la
0.2.0 sin las claves nuevas usa las del jar.

El índice (`data/statues.yml`) guarda en `skin` la etiqueta `url:<sitio>#<huella>` (el formato no cambia: es texto, y
`:` no puede estar en un nombre de cuenta, así que no se confunde). `list` e `info` la muestran tal cual (H3 CA1).

Para la suite en el juego (y solo con la línea de comandos de Java, no en `config.yml`): `-Dskinstatues.test.localLinks=true`
deja pedir a direcciones locales y a cualquier puerto, para el servidor HTTPS local de la suite. El plugin lo avisa en el
log al arrancar. Quien puede cambiar la línea de comandos del servidor ya es su dueño.

## 4. Hilos
- Comando (hilo principal): reconocer el link, permiso, `network.urls`, `SkinLink.parse` (sin red) y argumentos.
- Asíncrono (como la skin de Mojang): DNS, descarga, validación, caché, voxelización. El resultado vuelve al hilo
  principal para encolar (sin cambios).

## 5. Amenazas (STRIDE, lo nuevo)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación / SSRF | `https://127.0.0.1/…`, `https://192.168.1.1/admin`, un nombre que resuelve a la LAN, o un sitio público que redirige a `http://localhost:8080` | Solo direcciones públicas, comprobadas en cada salto (todas las del nombre); 3 redirecciones; puerto 443; sin usuario en el link |
| Suplantación / SSRF | *DNS rebinding*: el nombre resuelve a una IP pública al comprobar y a una privada al conectar | La comprobación llena la caché de DNS de Java que usa `HttpClient` (30 s); riesgo residual si el dueño la apaga (`networkaddress.cache.ttl=0`) |
| Manipulación | El link trae etiquetas MiniMessage o saltos de línea que llegan al chat o al log | Caracteres de URL solamente; los mensajes muestran el sitio (validado) y escapan todo (II.2); el link no va al log |
| Manipulación | El link apunta fuera de la carpeta al guardar la caché | El archivo se llama por la huella (hex), nunca por el link |
| Repudio | ¿De dónde salió esta estatua? | El índice guarda `url:sitio#huella` y quién la hizo; con el link a mano, la huella se puede comparar |
| Divulgación | La IP del servidor llega a un sitio de terceros; el link (privado) queda en archivos | `network.urls` apagado por defecto, permiso aparte, `allowed_hosts`; el link no se guarda (H3) |
| Denegación de servicio | Respuesta enorme, PNG «bomba», sitio que no contesta, cadena de redirecciones | Tope de 1 MB al leer; tamaño de la cabecera antes de decodificar (solo 64×64 o 64×32); 8 s por pedido; 3 redirecciones; fuera del hilo principal; la cola de estatuas (20) como hoy |
| Elevación de privilegios | Un jugador con `build` usa links | `skinstatues.build.url` aparte, `default: op` |

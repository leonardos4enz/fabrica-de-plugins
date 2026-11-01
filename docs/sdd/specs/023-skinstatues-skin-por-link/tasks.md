# Tareas 023

## Pruebas primero (núcleo sin Bukkit)
- [x] T1 `SkinLinkTest`: links válidos, `http` → `https`, etiqueta y huella, rechazos (largo, caracteres, usuario,
      puerto, esquema, sin sitio) (H1 CA1, CA2; H2 CA2; H3 CA1).
- [x] T2 `SkinLinkTest` (descarga) con red y DNS falsos: descarga, redirección válida, redirección a localhost, nombre que resuelve
      a la LAN, IPs privadas y reservadas (v4, v6, mapeadas), `allowed_hosts`, más de 3 redirecciones, 404, demasiado
      grande, sin respuesta (H2 CA3–CA5).
- [x] T3 `SkinLinkTest` (formato y modelo): detección del modelo (64×64 delgada y clásica, 64×32), `withSlim`; `SkinPng` sin `IHDR` (H1 CA3, H2 CA1).
- [x] T4 `SkinLinkTest` (caché): caché por huella sin el link en el nombre del archivo, copia vencida
      si el sitio falla, un HTML no llega a la caché (H1 CA6, H2 CA1, H3 CA2).
- [x] T5 `BuildArgsTest`: `--slim`, `--classic`, los dos a la vez. `ConfigTest`: `network.urls`, `allowed_hosts` con
      errores y un `config.yml` de la 0.2.0.

## Implementación
- [x] T6 `SkinLink`, `LinkPolicy`, `LinkClient` (con `realHttp`), `Skin.detectSlim`/`withSlim`, `IHDR` en `SkinPng`.
- [x] T7 `SkinRepository.loadLink`; `Settings` (`urls`, `allowedHosts`); `config.yml`, `messages.yml`, `plugin.yml`.
- [x] T8 `StatueCommand.build` con link (permiso, interruptor), `Statues.Request` con link y modelo; autocompletado.
- [x] T9 Versión 0.3.0.

## Cierre
- [x] T10 Arnés: la suite puede dar argumentos de Java al servidor y preparar algo antes de arrancar; suite 3a con un
      servidor HTTPS local (certificado propio de la corrida) y escenarios de links en 1.21.4 y 26.1.2 (dos veces).
- [x] T11 Compuerta 2 (`validate`, `selftest`, `reload`, link con la red apagada); `verify.md`.
- [x] T12 README del plugin (en inglés) y raíz, roadmap, índice, CLAUDE.md si hay lecciones; commit y push.

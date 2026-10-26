# ADR-0004 — ProtocolLib como integración opcional para leer el chat que recibe el jugador

**Estado:** Aceptado · **Fecha:** 2026-10-25 · **Spec:** [016](../sdd/specs/016-omnievents-paridad-conditionalevents-3/spec.md)

## Contexto
ConditionalEvents tiene `protocollib_receive_message` (y la wiki, `player_receive_chat`): reglas que reaccionan a lo
que el jugador **recibe** en el chat y pueden ocultarlo. La API de Paper no da un evento para eso: un mensaje del
sistema (`sendMessage`, `/tellraw`, otros plugins) va directo al paquete. La constitución VI.2 prohíbe NMS y pide que las
integraciones sean opcionales; la 014 dejó la lectura de paquetes como **no** hasta tener un ADR.

## Decisión
- Leer los paquetes `SYSTEM_CHAT`, `DISGUISED_CHAT` y `CHAT` (el contenido sin firmar) con **ProtocolLib**, como CE,
  mediante su API de envolturas (`PacketAdapter`, `getChatComponents`, `getBooleans`): OmniEvents no toca clases de
  Minecraft. PacketEvents queda fuera (una sola biblioteca; la más instalada y la que usa CE 4.65.1).
- **Opcional:** `softdepend: [ProtocolLib]`, compilado con `compileOnly` (no se empaqueta) y detectado en tiempo de
  ejecución. La clase que usa ProtocolLib solo se carga si está; el disparador `player_receive_chat` existe siempre (sobre
  un evento propio) y sin ProtocolLib la regla carga con aviso.
- **Pagar solo por lo que se usa (III.1):** el adaptador de paquetes se registra solo si una regla habilitada usa el
  disparador y se quita al recargar sin ninguna.
- **Texto no confiable (II.2):** lo recibido se trata como el chat del jugador. Un tope de mensajes por jugador y segundo
  acota los bucles (III.4).
- En el servidor de prueba se baja solo de la fuente oficial con sha256 fijado: el release 5.4.0 de GitHub para Paper
  1.21.4 y la build de desarrollo #765 (5.5.0-SNAPSHOT, `ProtocolLib-Spigot.jar`) del Jenkins de dmulloy2 para 26.1.2,
  la primera con soporte de 26.1 (no hay release). Aprobado por el dueño (CLARIFY 016).

## Consecuencias
- (+) Paridad con CE en el último evento que faltaba con equivalente posible; reglas que ocultan o responden a mensajes
  de otros plugins.
- (+) Sin ProtocolLib, nada cambia: ni clases cargadas ni oyentes.
- (−) ProtocolLib sigue a cada versión de Minecraft con retraso; una versión de ProtocolLib que no conoce la del
  servidor puede no leer los paquetes. Los errores se atrapan en el hilo de red (el paquete sigue sin cambios) y el README
  dice qué versiones están verificadas.
- (−) El chat firmado de otros jugadores no trae su texto en el paquete salvo el contenido sin firmar; para eso está
  `player_chat`.

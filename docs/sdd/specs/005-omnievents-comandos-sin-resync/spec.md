# Spec 005 — OmniEvents: activar y desactivar reglas sin re-sincronizar comandos

**Estado:** Implementado y verificado · **Plugin:** OmniEvents · **Depende de:** 001, 002 ·
**Origen:** hallazgo F-01 de la spec 004 (2026-10-24), pedido del dueño de arreglarlo en una spec chica

## Problema
`/omni enable <regla>` y `/omni disable <regla>` desregistran y vuelven a registrar **todos** los comandos de reglas
(`trigger: command`) y reenvían el árbol de comandos a cada jugador, aunque la regla que cambió no sea un comando.
Al reenviar, cada jugador recibe el árbol dos veces (una por `syncCommands()` y otra por `updateCommands()`), y el
desregistro previo manda otro par. Paper arma ese árbol en un hilo aparte: si un segundo cambio llega mientras lo
arma, toca el árbol a medio leer y el log del servidor se llena de `ConcurrentModificationException`
(`Commands.sendAsync`). El jugador puede quedarse con el autocompletado viejo y el administrador ve errores que
parecen del plugin. Lo mismo pasa con `/omni reload` cuando los comandos no cambiaron.

## Historias
**H1 — Cambiar una regla no toca los comandos si no hace falta.** Como *administrador*, quiero activar y desactivar
reglas seguidas (a mano o desde otra regla) sin errores en el log ni trabajo inútil para el servidor.
- CA1: Si el conjunto de comandos de reglas no cambia (nombre, alias, permiso, descripción, uso y autocompletado),
  OmniEvents no desregistra ni vuelve a registrar ninguno: activar o desactivar una regla que no es un comando no
  manda el árbol de comandos a nadie. Vale también para `/omni reload` sin cambios en los comandos.
- CA2: Cuando sí hay que avisar a los jugadores, cada jugador recibe el árbol **una sola vez** por cambio.
- CA3: Varios `enable`/`disable` seguidos (reglas comunes y reglas comando) no dejan
  `ConcurrentModificationException` ni otros errores en el log.

**H2 — Desactivar un comando lo oculta sin desregistrarlo.** Como *jugador*, quiero que un comando de una regla
desactivada desaparezca de mi autocompletado y que, al reactivarla, vuelva a aparecer y funcionar.
- CA1: Con la regla desactivada, el comando no aparece en el árbol que recibe el jugador y no se puede usar (el
  servidor responde como a un comando desconocido, igual que antes).
- CA2: Al reactivarla, el comando vuelve al árbol y funciona sin reconectar.

## Fuera de alcance
- La carrera de Paper en sí (armar el árbol en otro hilo mientras el hilo principal registra comandos): se evita no
  registrando, no se corrige en Paper. Si de verdad cambia el conjunto (activar una regla comando que el archivo
  traía con `enabled: false`, o un reload que cambia comandos) se registra una vez, como hasta ahora.
- Cambios al formato de configuración: no hay ninguno.

## Decisiones (CLARIFY)
| # | Pregunta | Decisión |
|---|---|---|
| 1 | ¿Desactivar una regla comando la quita del mapa de comandos? | No: queda registrada y oculta (su permiso silencioso falla mientras la regla está desactivada). Así no se toca el árbol y no hay carrera. El próximo `reload` la quita si el archivo la trae desactivada |
| 2 | ¿Número de spec? | 005: la 003 está reservada para la paridad con ConditionalEvents (otra sesión) |
| 3 | ¿Versión del plugin? | 1.0.1 (corrección, sin cambios de configuración) |

## Preguntas abiertas
- (ninguna)

# Constitución de la fábrica — Minecraft Plugins

> Principios no negociables. Toda spec, plan, tarea o cambio de código (humano o IA) de **cualquier plugin de la
> fábrica** debe cumplirlos. Un cambio que viole un principio se rechaza aunque "funcione".

## I. Dirigido por configuración
1. El comportamiento de un servidor (qué pasa, cuándo y a quién) se **declara** en archivos YAML; el plugin lo
   interpreta. Nada específico de un servidor se programa a mano.
2. El código solo implementa **capacidades genéricas** (tipos de disparador, de condición, de acción, funciones del
   lenguaje de expresiones). Agregar una capacidad nueva requiere spec.
3. Todo lo que se declara se **valida al cargar**: un error dice archivo, ruta dentro del archivo, qué está mal y, si
   aplica, «¿quisiste decir…?». Un archivo con errores no tumba al resto: se omite lo inválido y se informa.
4. `/<plugin> reload` aplica los cambios sin reiniciar el servidor y sin perder datos persistentes.

## II. Seguridad por diseño
1. **Nada de ejecución de código arbitrario.** Los lenguajes de expresiones son propios y cerrados: solo leen.
   La reflexión expuesta a la configuración se limita a métodos de lectura (`get*`, `is*`, `has*`, `can*`) salvo que
   el dueño lo habilite explícitamente en `config.yml`.
2. **El texto del jugador es entrada no confiable.** Un valor que viene del jugador (chat, comando, nombre de
   ítem, cartel, libro) nunca se interpreta como etiqueta MiniMessage ni puede romper un comando de consola: se
   escapan saltos de línea, `§` y etiquetas al interpolarlo en comandos y mensajes.
3. **Mínimo privilegio.** Cada subcomando administrativo declara su permiso y se deniega por defecto
   (`default: op`). Ningún permiso de administración se da a `true`.
4. **Sin red ni telemetría** salvo que el dueño la active. Ningún plugin descarga ni ejecuta código.
5. **Secretos fuera del repositorio** (tokens, contraseñas de bases de datos, claves de webhooks).
6. **No destructivo por defecto.** Los datos persistentes se guardan de forma atómica (archivo temporal + reemplazo)
   y nunca se borran por una recarga o un error de configuración.

## III. Rendimiento (el TPS es del servidor, no del plugin)
1. **Pagar solo por lo que se usa:** un oyente de eventos se registra solo si hay configuración que lo use.
2. **Compilar al cargar, no al ejecutar:** expresiones, plantillas de texto y acciones se analizan una vez en la
   carga. En el hilo principal no hay análisis de texto, E/S de disco ni red.
3. La API de Bukkit se usa solo en el hilo principal (o en el de la región, si un día se soporta Folia). Los eventos
   asíncronos (chat, pre-login) solo hacen en su hilo lo que es seguro y pasan el resto al hilo principal.
4. Todo bucle declarado por configuración tiene un tope (iteraciones, profundidad de llamadas) para que un error de
   configuración no congele el servidor.

## IV. Calidad
1. El código crítico (analizador y evaluador de expresiones, validación, persistencia) tiene **pruebas unitarias
   antes** de darse por terminado.
2. **Ninguna spec se cierra sin la fase VERIFY completa** ([verificacion.md](verificacion.md)).
3. **Cada bug encontrado se convierte en prueba** que falla con el código anterior, antes de marcarse resuelto.
4. Cada decisión de arquitectura relevante se registra como ADR en `docs/adr/`.
5. Compatibilidad hacia atrás de la configuración: un archivo de una versión anterior sigue cargando; si cambia el
   formato, hay migración automática con respaldo del archivo original.

## V. Experiencia del administrador
1. Mensajes en **español claro** por defecto y traducibles (`messages.yml`); MiniMessage y códigos `&` heredados.
2. Cada plugin trae ejemplos que funcionan tal cual y documentación con un ejemplo por capacidad.
3. Herramientas de diagnóstico de primera clase: depuración en vivo (por qué una condición falló), evaluación
   de expresiones desde el chat y estadísticas de rendimiento por regla.
4. Sin emojis en mensajes del plugin; símbolos tipográficos sobrios (`»`, `•`, `✔`, `✘`) cuando aporten.

## VI. Plataforma
1. Objetivo: **Paper 1.21.4+**, Java 21. Spigot no se soporta (API Adventure nativa).
2. Sin NMS ni código dependiente de la versión del servidor. Integraciones con otros plugins (PlaceholderAPI,
   Vault, WorldGuard…) son **opcionales** (`softdepend`) y se detectan en tiempo de ejecución.

## Gobernanza
Cambiar esta constitución requiere un ADR que explique el motivo.

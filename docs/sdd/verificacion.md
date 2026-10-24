# Fase VERIFY — cómo se da por terminada una spec

Una spec **no está terminada** hasta pasar las tres compuertas, en este orden. Las dos primeras son automáticas y se
repiten igual en cada cambio. La tercera es la revisión de una persona o de un agente usando el plugin como lo haría
un administrador y un jugador.

```
 1. Pruebas unitarias         gradlew test             analizador, evaluador, validación, persistencia
 2. Servidor Paper real       bash tools/compuerta-servidor.sh <plugin> "<comandos>"
      ├─ carga                el plugin habilita sin excepciones con la configuración de ejemplo
      ├─ validación           /<plugin> validate → 0 errores en los ejemplos que trae el plugin
      ├─ autoprueba           /<plugin> selftest → los casos incorporados pasan en el servidor vivo
      └─ recarga              /<plugin> reload dos veces seguidas sin fugas de oyentes ni tareas
 3. Revisión exploratoria     en el juego (persona) o por consola (agente) + reporte en verify.md
```

## 1. Unitarias
```bash
./gradlew test
```
- Prueban el código que no necesita servidor: lenguaje de expresiones, plantillas de texto, duraciones,
  conversión de configuraciones de otros plugins, persistencia sobre una carpeta temporal.
- El nombre de la prueba cita la historia y el criterio cuando cubre uno (`H2_CA3_parentesis_y_precedencia`).

## 2. Servidor real
```bash
bash tools/compuerta-servidor.sh omnievents "omni validate" "omni selftest" "omni reload" "omni reload" "omni stats"
```
- El servidor vive en `plugins/<plugin>/run/` (fuera del repositorio). El jar de Paper se baja de la API oficial
  (`https://fill.papermc.io/v3/projects/paper/versions/<versión>/builds/latest`) y **se verifica su sha256**.
  `run-paper` 2.3.1 ya no encuentra las versiones (usa la API anterior de PaperMC).
- **El EULA de Minecraft lo acepta el dueño**, una vez, por escrito; solo entonces se escribe `eula=true` en
  `run/eula.txt`. El script se niega a arrancar sin él.
- El script manda los comandos por consola, apaga y **vuelve a arrancar** para comprobar que los datos persisten.
- La compuerta pasa si: el log no tiene `ERROR`/`Exception` del plugin, `validate` reporta 0 errores en los ejemplos,
  `selftest` pasa y dos `reload` seguidos dejan el mismo número de oyentes y tareas (lo muestra `/<plugin> stats`).

## 3. Revisión exploratoria

### Lista de comprobación
**Administrador**
- [ ] Cada ejemplo del plugin funciona sin tocarlo
- [ ] Un error de configuración dice archivo, ruta, causa y sugerencia; el resto del plugin sigue funcionando
- [ ] Los mensajes están en español claro, sin trazas de Java en el chat
- [ ] `reload` con un archivo roto no borra lo que ya funcionaba ni los datos guardados

**Jugador**
- [ ] Los mensajes se ven bien (colores, acentos y ñ, MiniMessage y `&`)
- [ ] Un jugador sin permiso no ve los subcomandos administrativos ni su autocompletado
- [ ] Texto malicioso del jugador (`<red>`, `§`, saltos de línea, `; op yo`) no se interpreta

**Rendimiento**
- [ ] `/<plugin> stats` muestra tiempos por regla razonables (< 0.1 ms promedio para reglas simples)
- [ ] Sin reglas de un tipo de evento, no hay oyente registrado para ese evento

### Regla de oro: cada hallazgo se vuelve prueba
1. Se registra en `specs/NNN-…/verify.md` con ID (`F-01`, `F-02`…), severidad y evidencia.
2. Se corrige.
3. Se agrega una prueba (unitaria o caso de `selftest`) que habría fallado antes de la corrección, con un comentario
   `// Regresión (revisión exploratoria NNN): …`.
4. Solo entonces se marca como resuelto.

### Severidad
| Nivel | Criterio |
|---|---|
| Alta | Pérdida de datos, caída del servidor o del TPS, falla de seguridad (ejecución de comandos, escalada de permisos) |
| Media | La función sirve pero confunde, el error no orienta o algo se ve roto |
| Baja | Detalle cosmético o de redacción |

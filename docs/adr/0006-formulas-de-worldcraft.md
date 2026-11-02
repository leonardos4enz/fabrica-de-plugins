# ADR-0006 — Las fórmulas de Worldcraft son un lenguaje propio, numérico, como el de WorldEdit

**Estado:** Aceptado (el dueño, CLARIFY de la spec 034) · **Fecha:** 2026-11-02

## Contexto
La spec 034 trae las fórmulas de WorldEdit (`//generate`, `//deform`, `=y<64`…). La fábrica ya tiene un lenguaje de
expresiones: el de OmniEvents ([ADR 0003](0003-lenguaje-de-expresiones-propio.md)). Alternativas:
(a) sacar el de OmniEvents a un módulo común; (b) un lenguaje nuevo dentro de Worldcraft; (c) una biblioteca de
terceros (exp4j, JEXL) o un motor de scripts.

| | (a) Biblioteca común | (b) Propio de Worldcraft | (c) Terceros |
|---|---|---|---|
| Valores | Textos, listas, jugadores, propiedades de Bukkit; cada valor es un `Object` | Solo `double`, variables en un arreglo | Depende |
| Asignaciones, bucles, `if` | No tiene (son condiciones) | Sí, como WorldEdit | exp4j no; JEXL sí (con acceso a objetos) |
| Velocidad (millones de bloques) | Lenta (cajas y búsquedas por nombre) | Rápida (sin cajas ni búsquedas al evaluar) | — |
| Una fórmula de WorldEdit funciona | No (otra sintaxis) | Sí | No |
| Costo | Cambiar OmniEvents y su suite (otra spec) | Lo mantenemos nosotros | Sombrear el jar; JEXL abre llamadas a métodos (constitución II.1) |

## Decisión
(b): un lenguaje propio en `dev.leo.worldcraft.formula`, escrito desde la documentación pública de WorldEdit (sin su
código, que es GPL), con un analizador de descenso recursivo que produce un árbol inmutable con las variables ya
resueltas a posiciones de un arreglo. Solo números: no hay forma de llamar a Java, leer archivos ni tocar el mundo. Cada
evaluación tiene un tope de vueltas (`formulas.max_steps`) y cada cálculo de un comando un tope de tiempo
(`/wc timeout`, `formulas.max_timeout_ms`), en un hilo propio.

## Consecuencias
- (+) Las fórmulas de los tutoriales de WorldEdit funcionan igual (salvo `query`, fuera de alcance).
- (+) Sin dependencias ni cambios en OmniEvents; una fórmula no puede ejecutar código ni trabar el servidor.
- (−) Dos lenguajes en la fábrica (uno de condiciones con textos, uno numérico). Si un tercer plugin necesita fórmulas
  numéricas, se saca este a un módulo común en esa spec.
- (−) El ruido (`perlin`, `voronoi`, `ridgedmulti`) es propio: misma forma de uso, otros números que WorldEdit.

# ADR-0003 — Lenguaje de expresiones propio, compilado al cargar

**Estado:** Aceptado · **Fecha:** 2026-10-24

## Contexto
ConditionalEvents compara `valor operador valor` por línea, sin paréntesis ni mezcla de `and`/`or`, y evalúa
matemáticas con un motor externo opcional. Alternativas: (a) motor de scripts (JavaScript/Groovy/Lua),
(b) biblioteca de expresiones (SpEL, JEXL, MVEL), (c) lenguaje propio pequeño.

## Decisión
Lenguaje propio: analizador de precedencia (Pratt) que produce un árbol inmutable al cargar la configuración.
Evaluación sin reflexión salvo el acceso a propiedades, que usa un caché de métodos de **solo lectura**.

## Consecuencias
- (+) Seguridad (constitución II.1): no hay forma de ejecutar código ni métodos que modifiquen el mundo.
- (+) Errores de sintaxis con posición exacta al cargar; evaluación rápida en el hilo principal.
- (+) Sin dependencias que sombrear en el jar.
- (−) Lo mantenemos nosotros: cada función nueva pasa por spec y prueba unitaria.

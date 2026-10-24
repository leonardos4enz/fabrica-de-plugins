# ADR-0002 — Plataforma: Paper 1.21.4+ y Java 21, sin NMS

**Estado:** Aceptado · **Fecha:** 2026-10-24

## Contexto
ConditionalEvents soporta desde 1.8.9, lo que lo obliga a ramas por versión y a no usar Adventure ni APIs modernas.

## Decisión
Compilar contra `paper-api:1.21.4` con `--release 21`. Usar Adventure/MiniMessage nativos. Sin NMS. Integraciones
opcionales por `softdepend` y detección en tiempo de ejecución.

## Consecuencias
- (+) Código más simple y moderno; un solo jar para 1.21.4 en adelante.
- (+) Eventos de Paper (`AsyncChatEvent`, `PlayerJumpEvent`, `PlayerArmorChangeEvent`) disponibles sin trucos.
- (−) No funciona en Spigot ni en versiones antiguas. Es intencional (constitución VI).

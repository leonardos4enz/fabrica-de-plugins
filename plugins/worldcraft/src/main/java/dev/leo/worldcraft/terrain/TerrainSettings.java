package dev.leo.worldcraft.terrain;

import dev.leo.worldcraft.pattern.Mask;

import java.util.Map;

/**
 * La sección {@code terrain} de config.yml y todo terrain.yml, ya validados (H4 CA3, CA5, H6 CA1–CA2; plan 026 §2).
 *
 * @param sizes    herramienta → tamaño → {radius, height}
 * @param defaults herramienta → opción → valor (números y el estilo)
 */
public record TerrainSettings(boolean previewFirst, int reach, int maxRadius, Mask ground, Mask cover, Mask buryable,
                              String brushItem, int brushMaxRadius, int brushRange, boolean brushShow,
                              Map<String, Style> styles, Map<String, Map<String, Map<String, Double>>> sizes,
                              Map<String, Map<String, Object>> defaults) {
}

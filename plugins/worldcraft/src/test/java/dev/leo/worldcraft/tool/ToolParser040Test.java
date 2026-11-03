package dev.leo.worldcraft.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 040 H2 y H3 CA5: los pinceles y la herramienta nuevos, en el orden de WorldEdit. */
class ToolParser040Test {
    static final ToolSpec.Caps CAPS = ToolSpec.Caps.DEFAULTS;

    static ToolSpec brush(String type, Set<String> flags, String... words) {
        return ToolParser.parse(true, type, List.of(words), flags, Map.of(), CAPS);
    }

    @Test
    void H2_CA1_CA2_nieve() {
        ToolSpec s = brush("snow", Set.of("stack"), "4");
        assertEquals(ToolKind.SNOW, s.kind());
        assertEquals(4, s.radius());
        assertTrue(s.flag("stack"));
        ToolSpec sm = brush("snowsmooth", Set.of(), "6", "3");
        assertEquals(ToolKind.SNOWSMOOTH, sm.kind());
        assertEquals(6, sm.radius());
        assertEquals(3, sm.number("iterations"));
        assertEquals(1, brush("snowsmooth", Set.of()).number("iterations"));
        assertThrows(IllegalArgumentException.class, () -> brush("snow", Set.of(), "stone"), "la nieve no lleva bloques");
    }

    @Test
    void H2_CA3_bioma_con_el_radio_primero() {
        ToolSpec s = brush("biome", Set.of(), "7", "desert");
        assertEquals(ToolKind.BIOME, s.kind());
        assertEquals(7, s.radius());
        assertEquals("desert", s.text("biome"));
        assertEquals(5, brush("biome", Set.of(), "plains").radius());
        assertThrows(IllegalArgumentException.class, () -> brush("biome", Set.of(), "5"), "falta el bioma");
    }

    @Test
    void H2_CA4_morph_y_dilate() {
        ToolSpec m = brush("morph", Set.of(), "6", "3", "2", "4", "1");
        assertEquals(ToolKind.MORPH, m.kind());
        assertEquals(List.of(6, 3, 2, 4, 1), List.of(m.radius(), m.number("erode_faces"), m.number("erode"), m.number("dilate_faces"), m.number("dilate")));
        ToolSpec d = brush("morph", Set.of());
        assertEquals(List.of(5, 4, 1, 5, 1), List.of(d.radius(), d.number("erode_faces"), d.number("erode"), d.number("dilate_faces"), d.number("dilate")),
                "los de fábrica de WorldEdit");
        ToolSpec dil = brush("dilate", Set.of(), "4");
        assertEquals(ToolKind.DILATE, dil.kind());
        assertEquals(0, dil.number("erode"), "dilate no erosiona");
        assertEquals(1, dil.number("dilate"));
        assertThrows(IllegalArgumentException.class, () -> brush("morph", Set.of(), "5", "9"), "a lo más 6 caras");
    }

    @Test
    void H2_CA5_heightmap() {
        ToolSpec h = brush("heightmap", Set.of("erase"), "hill.png", "8", "12");
        assertEquals(ToolKind.HEIGHTMAP, h.kind());
        assertEquals("hill.png", h.text("image"));
        assertEquals(8, h.radius());
        assertEquals(12, h.number("height"));
        assertTrue(h.flag("erase"));
        assertThrows(IllegalArgumentException.class, () -> brush("heightmap", Set.of(), "8"), "falta la imagen");
    }

    @Test
    void H2_CA6_apply_y_paint_con_item() {
        ToolSpec a = brush("apply", Set.of(), "cube", "3", "item", "iron_hoe");
        assertEquals(ToolKind.APPLY, a.kind());
        assertEquals("cube", a.text("shape"));
        assertEquals(3, a.radius());
        assertEquals("iron_hoe", a.text("item"));
        assertEquals(100, a.number("density"), "apply usa el ítem en todos");
        ToolSpec p = brush("paint", Set.of(), "5", "30", "item", "minecraft:bone_meal");
        assertEquals(ToolKind.APPLY, p.kind());
        assertEquals("sphere", p.text("shape"));
        assertEquals(30, p.number("density"));
        assertEquals("bone_meal", p.text("item"));
        assertEquals(10, brush("paint", Set.of(), "item", "bone_meal").number("density"));
        assertThrows(IllegalArgumentException.class, () -> brush("apply", Set.of(), "4", "iron_hoe"), "falta la palabra item");
        assertNull(ToolKind.parse("paint", true), "/wc brush paint sin item sigue siendo del terreno");
    }

    @Test
    void H2_CA7_feature() {
        ToolSpec f = brush("feature", Set.of(), "mossy_rock", "6", "20");
        assertEquals(ToolKind.FEATURE, f.kind());
        assertEquals("mossy_rock", f.text("feature"));
        assertEquals(6, f.radius());
        assertEquals(20, f.number("density"));
    }

    @Test
    void H3_CA5_stacker() {
        ToolSpec s = ToolParser.parse(false, "stacker", List.of("12", "air,", "water"), Set.of(), Map.of(), CAPS);
        assertEquals(ToolKind.STACKER, s.kind());
        assertEquals(12, s.number("range"));
        assertEquals("air, water", s.mask());
        ToolSpec d = ToolParser.parse(false, "stacker", List.of(), Set.of(), Map.of(), CAPS);
        assertEquals(10, d.number("range"));
        assertNull(d.mask(), "de fábrica, el aire (lo pone quien hace clic)");
    }

    @Test
    void H3_CA3_CA4_alcance_y_mascara_de_mira_viajan_en_el_item() {
        ToolSpec s = brush("sphere", Set.of(), "stone", "3").with("reach", "40").with("trace", ToolSpec.encodeText("water; x"));
        ToolSpec back = ToolSpec.decode(s.encode(), new ToolSpec.Caps(16, 32, 30));
        assertEquals(30, back.number("reach"), "se acota con tools.range");
        assertEquals("water; x", back.trace());
        assertTrue(s.describe().contains("reach 40"), s.describe());
        assertTrue(s.describe().contains("aims at water; x"), s.describe());
    }
}

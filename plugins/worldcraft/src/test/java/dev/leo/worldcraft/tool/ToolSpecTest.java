package dev.leo.worldcraft.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 032 H1–H5: lo que se escribe tras /wc brush y /wc tool, y lo que viaja en el ítem (plan §1.1). */
class ToolSpecTest {
    static final ToolSpec.Caps CAPS = ToolSpec.Caps.DEFAULTS;

    static ToolSpec brush(String... words) {
        return ToolParser.parse(true, words[0], List.of(words).subList(1, words.length), Set.of(), Map.of(), CAPS);
    }

    static ToolSpec tool(String... words) {
        return ToolParser.parse(false, words[0], List.of(words).subList(1, words.length), Set.of(), Map.of(), CAPS);
    }

    @Test
    void H2_el_orden_de_WorldEdit_patron_y_numeros_del_final() {
        ToolSpec s = brush("sphere", "stone", "4");
        assertEquals(ToolKind.SPHERE, s.kind());
        assertEquals("stone", s.pattern());
        assertEquals(4, s.radius());
        ToolSpec c = brush("cylinder", "50%", "stone,", "50%", "dirt", "3", "5");
        assertEquals(ToolKind.CYL, c.kind(), "alias de WorldEdit");
        assertEquals("50% stone, 50% dirt", c.pattern(), "un porcentaje no es un número del final");
        assertEquals(3, c.radius());
        assertEquals(5, c.number("height"));
        assertEquals(2, brush("sphere", "glass").radius(), "de fábrica, como WorldEdit");
        ToolSpec set = brush("set", "cube", "3", "oak_planks");
        assertEquals("cube", set.text("shape"));
        assertEquals(3, set.radius());
        assertEquals("oak_planks", set.pattern());
        ToolSpec rock = brush("blob", "stone,", "andesite", "6");
        assertEquals(ToolKind.ROCK, rock.kind());
        assertEquals(100, rock.number("sphericity"));
        ToolSpec sc = ToolParser.parse(true, "scatter", List.of("poppy"), Set.of("overlay"), Map.of("points", "12"), CAPS);
        assertEquals(12, sc.number("points"));
        assertTrue(sc.flag("overlay"));
        assertEquals(5, sc.radius());
        ToolSpec g = ToolParser.parse(true, "gravity", List.of("4"), Set.of("height"), Map.of(), CAPS);
        assertTrue(g.flag("full"), "--height de WorldEdit: hasta el techo");
        ToolSpec b = ToolParser.parse(true, "butcher", List.of("8"), Set.of("animals", "pets"), Map.of(), CAPS);
        assertEquals(Set.of("animals", "pets"), b.butcherFlags());
        ToolSpec lr = tool("lrbuild", "air", "stone");
        assertEquals("air", lr.text("left"));
        assertEquals("stone", lr.text("right"));
        assertEquals(10, tool("floodfill", "water", "10").number("range"));
    }

    @Test
    void H1_CA2_ida_y_vuelta_por_el_item_y_un_item_armado_a_mano_se_acota() {
        for (ToolSpec s : List.of(brush("sphere", "stone", "4"), brush("rock", "stone", "6", "50", "20", "70"),
                ToolParser.parse(true, "sphere", List.of("glass", "3"), Set.of("hollow"), Map.of("mask", "air"), CAPS),
                tool("lrbuild", "air", "stone"), tool("cycler"), tool("info"))) {
            assertEquals(s, ToolSpec.decode(s.encode(), CAPS), s.encode());
        }
        ToolSpec hacked = ToolSpec.decode("kind=sphere;pattern=stone;radius=9999;height=x;junk", CAPS);
        assertEquals(16, hacked.radius(), "el tope del momento");
        assertEquals(1, hacked.number("height"), "lo que no se entiende queda de fábrica");
        assertNull(ToolSpec.decode("kind=nope;radius=2", CAPS));
        assertNull(ToolSpec.decode(null, CAPS));
        assertEquals("sphere · stone · radius 3 · hollow · only on air",
                ToolParser.parse(true, "sphere", List.of("stone", "3"), Set.of("hollow"), Map.of("mask", "air"), CAPS).describe());
    }

    @Test
    void H7_CA2_errores_con_sugerencia() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> brush("spere", "stone")).getMessage().contains("did you mean 'sphere'"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> tool("rpel", "stone")).getMessage().contains("did you mean 'repl'"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> brush("sphere", "stone", "40")).getMessage().contains("out of range (0..16)"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> brush("sphere")).getMessage().contains("needs blocks"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> brush("set", "cueb", "3", "stone")).getMessage().contains("did you mean 'cube'"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> brush("gravity", "stone")).getMessage().contains("takes no blocks"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> tool("lrbuild", "stone")).getMessage().contains("two blocks"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ToolParser.parse(true, "sphere", List.of("stone"), Set.of("holow"), Map.of(), CAPS))
                .getMessage().contains("did you mean 'hollow'"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ToolParser.parse(true, "sphere", List.of("stone"), Set.of(), Map.of("radus", "3"), CAPS))
                .getMessage().contains("did you mean 'radius'"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> brush("sphere", "stone", "3", "4")).getMessage().contains("at most 1 number"));
    }

    @Test
    void H1_CA5_radius_y_mask_cambian_solo_eso() {
        ToolSpec s = brush("sphere", "stone", "4");
        ToolSpec t = ToolParser.update(s, Map.of("radius", "6", "mask", "air"), CAPS);
        assertEquals(6, t.radius());
        assertEquals("air", t.mask());
        assertEquals("stone", t.pattern());
        assertNull(ToolParser.update(t, Map.of("mask", "*"), CAPS).mask(), "* quita la máscara");
        assertThrows(IllegalArgumentException.class, () -> ToolParser.update(s, Map.of("radius", "99"), CAPS));
        assertThrows(IllegalArgumentException.class, () -> ToolParser.update(tool("info"), Map.of("radius", "3"), CAPS));
        assertThrows(IllegalArgumentException.class, () -> ToolParser.update(s, Map.of("pattern", "dirt"), CAPS));
    }
}

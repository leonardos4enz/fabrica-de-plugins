package dev.leo.worldcraft.tool;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 041: los pinceles y herramientas de FAWE, con sus alias, y lo del pincel de la mano (target, scroll, secondary). */
class ToolParser041Test {
    static final ToolSpec.Caps CAPS = ToolSpec.Caps.DEFAULTS;

    static ToolSpec brush(String type, Set<String> flags, String... words) {
        return ToolParser.parse(true, type, List.of(words), flags, Map.of(), CAPS);
    }

    static ToolSpec tool(String type, String... words) {
        return ToolParser.parse(false, type, List.of(words), Set.of(), Map.of(), CAPS);
    }

    @Test
    void H1_lineas_y_curvas_con_los_alias_de_FAWE() {
        ToolSpec l = brush("l", Set.of("shell", "flat"), "stone", "2");
        assertEquals(ToolKind.LINE, l.kind());
        assertEquals("stone", l.pattern());
        assertEquals(2, l.radius());
        assertTrue(l.flag("shell") && l.flag("flat"));
        assertEquals(0, brush("line", Set.of(), "stone").radius(), "de fábrica, grosor 0");
        assertEquals(ToolKind.SPLINE, brush("spl", Set.of(), "oak_planks").kind());
        assertEquals(ToolKind.SURFACESPLINE, brush("sspline", Set.of(), "dirt_path", "1").kind());
        ToolSpec sw = brush("vaesweep", Set.of(), "4");
        assertEquals(ToolKind.SWEEP, sw.kind());
        assertEquals(4, sw.number("copies"));
        assertEquals(0, brush("sweep", Set.of()).number("copies"), "sin número: una tras otra");
        ToolSpec cat = brush("gravityline", Set.of("facing"), "chain", "1.5", "1");
        assertEquals(ToolKind.CATENARY, cat.kind());
        assertEquals(1.5, cat.decimal("length"), 1e-9);
        assertEquals(1, cat.radius());
        assertEquals(1.2, brush("catenary", Set.of(), "chain").decimal("length"), 1e-9, "de fábrica 1,2 como FAWE");
        assertThrows(IllegalArgumentException.class, () -> brush("catenary", Set.of(), "chain", "0.5"), "más corta que la distancia");
        ToolSpec c = brush("circle", Set.of("fill"), "glass", "6");
        assertEquals(ToolKind.CIRCLE, c.kind());
        assertTrue(c.flag("fill"));
        ToolSpec pull = brush("pull", Set.of(), "4", "2");
        assertEquals(ToolKind.PULL, pull.kind());
        assertEquals(2, pull.number("iterations"));
        ToolSpec rec = brush("recurse", Set.of("depth-first"), "gold_block", "6");
        assertEquals(ToolKind.RECURSIVE, rec.kind());
        assertEquals(6, rec.radius());
    }

    @Test
    void H2_pinceles_avanzados() {
        ToolSpec sh = brush("partition", Set.of(), "air", "8", "12");
        assertEquals(ToolKind.SHATTER, sh.kind());
        assertEquals(List.of(8, 12), List.of(sh.radius(), sh.number("count")));
        assertEquals(List.of(10, 10), List.of(brush("shatter", Set.of(), "air").radius(), brush("shatter", Set.of(), "air").number("count")));
        ToolSpec st = brush("stencil", Set.of("white", "random-rotate"), "moss_block", "6", "spots.png");
        assertEquals(ToolKind.STENCIL, st.kind());
        assertEquals("spots.png", st.text("image"));
        assertEquals("moss_block", st.pattern());
        assertNull(brush("stencil", Set.of(), "moss_block").text("image"), "sin imagen: un cono");
        ToolSpec im = brush("image", Set.of("alpha"), "logo.png", "7");
        assertEquals(ToolKind.IMAGE, im.kind());
        assertEquals("logo.png", im.text("image"));
        ToolSpec h = brush("height", Set.of(), "6", "hill.png", "-4");
        assertEquals(ToolKind.HEIGHT, h.kind());
        assertEquals(-4, h.number("scale"), "escala negativa: baja");
        assertEquals(1, brush("height", Set.of()).number("scale"));
        ToolSpec ps = brush("pschem", Set.of("rotate"), "grass_block", "trees/", "12", "20");
        assertEquals(ToolKind.POPULATE, ps.kind());
        assertEquals("grass_block", ps.text("on"));
        assertEquals("trees/", ps.text("schem"));
        assertEquals(List.of(12, 20), List.of(ps.radius(), ps.number("density")));
        assertEquals(ToolKind.COPYPASTE, brush("copypasta", Set.of("auto-rotate"), "6").kind());
    }

    @Test
    void H4_CA9_CA10_herramientas() {
        assertEquals(ToolKind.INSPECT, tool("inspect").kind());
        ToolSpec f = tool("featuretool", "minecraft:mossy_rock");
        assertEquals(ToolKind.FEATUREPLACER, f.kind());
        assertEquals("mossy_rock", f.text("feature"));
        ToolSpec s = tool("structureplacer", "igloo/top");
        assertEquals(ToolKind.STRUCTUREPLACER, s.kind());
        assertEquals("igloo/top", s.text("structure"));
        assertThrows(IllegalArgumentException.class, () -> tool("featureplacer"), "falta el feature");
    }

    @Test
    void H3_CA4_CA7_lo_del_pincel_de_la_mano_viaja_en_el_item() {
        ToolSpec s = brush("sphere", Set.of(), "stone", "3")
                .withText("smask", "!air").withText("transform", "#offset[0][2][0]").withText("secondary", "kind=sphere;pattern=air;radius=2")
                .with("target", "face").with("toffset", "3");
        ToolSpec back = ToolSpec.decode(s.encode(), CAPS);
        assertEquals("!air", back.decodedText("smask"));
        assertEquals("#offset[0][2][0]", back.decodedText("transform"));
        assertEquals("air", back.secondary(CAPS).pattern(), "el pincel del clic izquierdo");
        assertEquals("face", back.target());
        assertEquals(3, back.number("toffset"));
        assertEquals("block", brush("sphere", Set.of(), "stone").target(), "de fábrica, el primer bloque");
        ToolSpec far = ToolSpec.decode(s.with("toffset", "999").encode(), new ToolSpec.Caps(16, 32, 200, 16));
        assertEquals(16, far.number("toffset"), "acotado a tools.max_target_offset");
    }

    @Test
    void H3_CA6_la_rueda_cambia_el_valor_elegido() {
        ToolSpec s = brush("sphere", Set.of(), "stone", "3").with("scroll", "size");
        assertEquals(4, Scroll.apply(s, 1, CAPS).radius());
        assertEquals(2, Scroll.apply(s, -1, CAPS).radius());
        assertEquals(0, Scroll.apply(s.with("radius", "0"), -1, CAPS).radius(), "no baja de 0");
        ToolSpec t = s.with("scroll", "target");
        assertEquals("forward", Scroll.apply(t, 1, CAPS).target());
        ToolSpec p = s.with("scroll", "pattern").withText("scroll_values", "stone|dirt|glass");
        ToolSpec p1 = Scroll.apply(p, 1, CAPS);
        assertEquals("dirt", p1.pattern());
        assertEquals("glass", Scroll.apply(p1, 1, CAPS).pattern());
        assertEquals("stone", Scroll.apply(Scroll.apply(p1, 1, CAPS), 1, CAPS).pattern(), "da la vuelta");
        assertEquals(List.of("none", "size", "range", "targetoffset", "target", "mask", "pattern", "clipboard"), Scroll.ACTIONS);
        assertEquals(1, Scroll.delta(3, 4));
        assertEquals(-1, Scroll.delta(0, 8), "de la primera a la última casilla es para atrás");
        assertEquals(0, Scroll.delta(1, 5), "saltar con los números no es la rueda");
    }

    @Test
    void H2_CA7_nombres_de_pinceles_guardados() {
        Path root = Path.of("brushes");
        assertEquals(root.resolve("abc").resolve("roca.txt"), Brushes.file(root, "abc", "roca"));
        assertEquals(root.resolve("roca.txt"), Brushes.file(root, null, "roca"));
        for (String bad : List.of("../x", "a/b", "a\\b", "", "x".repeat(40), "a b")) {
            assertThrows(IllegalArgumentException.class, () -> Brushes.file(root, null, bad), bad);
        }
    }
}

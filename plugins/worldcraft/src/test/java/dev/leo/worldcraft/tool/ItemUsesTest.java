package dev.leo.worldcraft.tool;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.pattern.Parser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 040 H2 CA6: lo que hace cada ítem en /wc brush apply|paint … item (item-uses.yml). */
class ItemUsesTest {
    final Parser parser = new Parser(TestBlocks.REGISTRY, Map.of(), Map.of());
    final StateTable table = new StateTable();

    static String jar() throws Exception {
        return Files.readString(Path.of("src/main/resources/item-uses.yml"));
    }

    int id(String s) {
        return table.intern(TestBlocks.REGISTRY.canonical("minecraft:" + s));
    }

    /** Lo que deja la primera regla que cumple (sin «above»), o null. */
    String use(ItemUses u, String item, String block) {
        for (ItemUses.Rule r : u.rules(item)) {
            if (r.above()) continue;
            if (r.mask().compile(table).test(id(block))) return table.state(r.pattern().compile(table).apply(0, 0, 0, id(block), 1));
        }
        return null;
    }

    @Test
    void la_tabla_del_jar_se_lee_sin_avisos() throws Exception {
        Issues issues = new Issues();
        ItemUses u = ItemUses.load(jar(), null, parser, issues);
        assertEquals("[]", issues.all().toString());
        assertTrue(u.items().containsAll(List.of("*_hoe", "*_shovel", "*_axe", "honeycomb", "bone_meal")), u.items().toString());
    }

    @Test
    void cada_item_hace_lo_suyo_y_los_comodines_valen() throws Exception {
        ItemUses u = ItemUses.load(jar(), null, parser, new Issues());
        assertTrue(use(u, "minecraft:iron_hoe", "grass_block").startsWith("minecraft:farmland"));
        assertEquals("minecraft:dirt", use(u, "wooden_hoe", "coarse_dirt"));
        assertEquals("minecraft:dirt_path", use(u, "netherite_shovel", "podzol"));
        assertEquals("minecraft:stripped_oak_log[axis=x]", use(u, "golden_axe", "oak_log[axis=x]"), "el tronco conserva su eje");
        assertEquals("minecraft:waxed_exposed_copper", use(u, "honeycomb", "exposed_copper"));
        assertNull(use(u, "iron_hoe", "stone"), "en piedra la azada no hace nada");
        ItemUses.Rule bone = u.rules("bone_meal").get(0);
        assertTrue(bone.above(), "la harina de hueso pone encima");
        assertTrue(bone.mask().compile(table).test(id("grass_block")));
        assertNull(u.rules("stick"), "un palo no está");
        assertNull(u.rules("hoe"), "el comodín no es «hoe» solo");
    }

    @Test
    void el_archivo_del_dueno_agrega_y_reemplaza() throws Exception {
        Issues issues = new Issues();
        String mine = "uses:\n  bone_meal: [\"above grass_block -> poppy\"]\n  stick: [\"glass -> stone\"]\n";
        ItemUses u = ItemUses.load(jar(), mine, parser, issues);
        assertEquals("[]", issues.all().toString());
        assertEquals(1, u.rules("bone_meal").size(), "reemplaza");
        assertEquals("minecraft:stone", use(u, "stick", "glass"), "agrega");
        assertEquals("minecraft:dirt_path", use(u, "stone_shovel", "dirt"), "lo demás del jar sigue");
    }

    @Test
    void una_regla_mal_escrita_avisa_con_el_item_y_se_salta() throws Exception {
        Issues issues = new Issues();
        String mine = "uses:\n  stick: [\"glass -> stnoe\", \"glass stone\", \"above glass -> stone\"]\n  bad: 5\n";
        ItemUses u = ItemUses.load(jar(), mine, parser, issues);
        String all = issues.all().toString();
        assertTrue(all.contains("item-uses.yml") && all.contains("uses.stick"), all);
        assertTrue(all.contains("stone"), "dice el error del patrón con «did you mean»: " + all);
        assertTrue(all.contains("->"), "dice que falta la flecha: " + all);
        assertTrue(all.contains("uses.bad"), all);
        assertEquals(1, u.rules("stick").size(), "queda la buena");
    }
}

package dev.leo.worldcraft.command;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CommandTest {

    @Test
    void H2_CA1_opciones_en_cualquier_lugar_y_patrones_con_espacios() {
        Args a = Args.parse(new String[]{"set", "70%", "stone,", "--preview", "30%", "andesite"}, 1, Set.of("preview"));
        assertNull(a.problem);
        assertTrue(a.flags.contains("preview"));
        assertEquals("70% stone, 30% andesite", a.join(0));
        Args bad = Args.parse(new String[]{"set", "stone", "--previwe"}, 1, Set.of("preview"));
        assertEquals("Unknown option '--previwe' (did you mean '--preview'?)", bad.problem);
        Args v = Args.parse(new String[]{"undo", "2", "--player", "Ana"}, 1, Set.of("player"));
        assertEquals("Ana", v.values.get("player"));
        assertEquals("2", v.positional.get(0));
        assertEquals("'--player' needs a value", Args.parse(new String[]{"undo", "--player"}, 1, Set.of("player")).problem);
    }

    @Test
    void H3_CA1_replace_con_with_o_sin_el() {
        Args a = Args.parse(new String[]{"replace", "dirt,", "grass_block", "with", "50%", "stone,", "50%", "glass"}, 1, Set.of());
        assertArrayEquals(new String[]{"dirt, grass_block", "50% stone, 50% glass"}, a.maskAndPattern());
        Args b = Args.parse(new String[]{"replace", "dirt,grass_block", "stone"}, 1, Set.of());
        assertArrayEquals(new String[]{"dirt,grass_block", "stone"}, b.maskAndPattern());
        assertNull(Args.parse(new String[]{"replace", "dirt"}, 1, Set.of()).maskAndPattern());
        assertNull(Args.parse(new String[]{"replace", "dirt", "with"}, 1, Set.of()).maskAndPattern());
    }

    @Test
    void H1_CA5_coordenadas_relativas() {
        assertEquals(12, WorldcraftCommand.coord("~2", 10));
        assertEquals(10, WorldcraftCommand.coord("~", 10));
        assertEquals(-64, WorldcraftCommand.coord("-64", 10));
    }

    /** H8 CA4 y CA5: cada subcomando tiene su ayuda con ejemplo, y cada mensaje que nombra el código existe. */
    @Test
    void H8_CA5_cada_subcomando_tiene_ayuda_y_cada_mensaje_existe() throws Exception {
        // Estricto: loadConfiguration se traga un YAML roto y devuelve un archivo vacío (así pasó en la 3a: un
        // «jobs-line:"…"» sin espacio dejó todos los mensajes en «Missing message»).
        YamlConfiguration m = new YamlConfiguration();
        m.loadFromString(Files.readString(Path.of("src/main/resources/messages.yml")));
        String cmd = Files.readString(Path.of("src/main/java/dev/leo/worldcraft/command/WorldcraftCommand.java"));
        Matcher subs = Pattern.compile("\"(help|wand|pos1|pos2|sel|expand|contract|shift|set|replace|walls|faces|copy|cut|paste|rotate|flip|undo|redo|history|confirm|cancel|jobs|limits|reload|validate|selftest|mountain|island|reroll|brush|styles|schem)\"").matcher(cmd);
        Set<String> names = new TreeSet<>();
        while (subs.find()) names.add(subs.group(1));
        assertEquals(33, names.size());
        for (String s : names) {
            for (String k : new String[]{"usage", "text", "example"}) {
                assertNotNull(m.getString("help." + s + "." + k), "falta help." + s + "." + k);
            }
            assertTrue(m.getString("help." + s + ".example").startsWith("/wc " + s), s);
        }
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher k = Pattern.compile("(?:msg\\(\\)|messages\\(\\)|msg)\\.get\\(\\s*\"([a-z0-9-]+)\"").matcher(Files.readString(f));
                while (k.find()) if (m.getString(k.group(1)) == null) missing.add(k.group(1) + " (" + f.getFileName() + ")");
                Matcher t = Pattern.compile("\\? \"([a-z]+-[a-z0-9-]+)\" : \"([a-z]+-[a-z0-9-]+)\"").matcher(Files.readString(f));
                while (t.find()) for (int g = 1; g <= 2; g++) if (m.getString(t.group(g)) == null) missing.add(t.group(g));
            }
        }
        assertEquals(Set.of(), missing);
    }

    /**
     * Spec 028 F-01 (compuerta 2): green no hizo nada con la tierra 2 bloques bajo los pies y radio 1, porque la cima se
     * buscaba solo entre ±radio. Volando o en una ladera la cima queda más abajo: se busca hasta el fondo del mundo.
     */
    @Test
    void spec028_F01_la_cima_se_busca_por_debajo_del_radio() {
        var b = BuildCommands.surfaceBox(5141, 172, 5141, 1, -64, 319);
        assertTrue(b.contains(5141, 170, 5141), b.toString());
        assertTrue(BuildCommands.surfaceBox(0, 70, 0, 3, -64, 319).contains(0, 64, 0), "volando 6 bloques sobre el suelo");
        assertEquals(73, BuildCommands.surfaceBox(0, 70, 0, 3, -64, 319).maxY(), "por encima, el radio");
        assertEquals(-64, BuildCommands.surfaceBox(0, 70, 0, 3, -64, 319).minY());
    }

    @Test
    void spec028_numeros_al_final_y_el_patron_con_espacios() {
        var pos = new java.util.ArrayList<>(java.util.List.of("70%", "stone,", "30%", "andesite", "6", "14"));
        var nums = BuildCommands.trailingNumbers(pos, 2);
        assertEquals(2, nums.size());
        assertArrayEquals(new int[]{6}, nums.get(0));
        assertArrayEquals(new int[]{14}, nums.get(1));
        assertEquals(java.util.List.of("70%", "stone,", "30%", "andesite"), pos);
        var ell = new java.util.ArrayList<>(java.util.List.of("glass", "6,2,3"));
        assertArrayEquals(new int[]{6, 2, 3}, BuildCommands.trailingNumbers(ell, 1).get(0));
        var neg = new java.util.ArrayList<>(java.util.List.of("stone", "4", "-6"));
        assertArrayEquals(new int[]{-6}, BuildCommands.trailingNumbers(neg, 2).get(1));
    }

    /** Spec 029: cada subcomando nuevo tiene su ayuda con ejemplo; schem nombra import. */
    @Test
    void spec029_ayuda_de_seleccion_informacion_y_gmask() throws Exception {
        YamlConfiguration m = new YamlConfiguration();
        m.loadFromString(Files.readString(Path.of("src/main/resources/messages.yml")));
        assertEquals(11, SelectionCommands.SUBS.size());
        for (String s : SelectionCommands.SUBS) {
            for (String k : new String[]{"usage", "text", "example"}) assertNotNull(m.getString("help." + s + "." + k), "falta help." + s + "." + k);
            assertTrue(m.getString("help." + s + ".example").startsWith("/wc " + s), s);
        }
        assertTrue(m.getString("help.schem.usage").contains("import"));
        assertTrue(m.getString("help.paste.usage").contains("--origin"));
        String yml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        assertTrue(yml.contains("worldcraft.schematic.save:"), "el permiso de guardar (H5 CA2)");
    }
}

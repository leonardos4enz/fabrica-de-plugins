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
        Matcher subs = Pattern.compile("\"(help|wand|pos1|pos2|sel|expand|contract|shift|set|replace|walls|faces|copy|cut|paste|rotate|flip|undo|redo|history|confirm|cancel|jobs|limits|reload|validate|selftest)\"").matcher(cmd);
        Set<String> names = new TreeSet<>();
        while (subs.find()) names.add(subs.group(1));
        assertEquals(27, names.size());
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
}

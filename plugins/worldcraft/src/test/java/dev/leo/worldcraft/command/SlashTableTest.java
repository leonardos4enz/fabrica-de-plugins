package dev.leo.worldcraft.command;

import dev.leo.worldcraft.config.Issues;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 039: los nombres de WorldEdit (//set, /brush…) para Worldcraft y sus banderas cortas. */
class SlashTableTest {

    private static SlashTable table() throws Exception {
        Issues issues = new Issues();
        SlashTable t = SlashTable.load(Files.readString(Path.of("src/main/resources/slash-commands.yml")), WorldcraftCommand.subcommands(), issues);
        assertEquals("[]", issues.all().toString(), "la tabla del jar se lee sin avisos");
        return t;
    }

    private static String run(SlashTable t, String label, String... args) {
        SlashTable.Entry e = t.get(label);
        assertNotNull(e, label);
        return String.join(" ", SlashTable.translate(e, args));
    }

    @Test
    void H1_CA1_cada_destino_existe_y_cada_opcion_la_conoce_su_comando() throws Exception {
        SlashTable t = table();
        Set<String> subs = WorldcraftCommand.subcommands();
        for (SlashTable.Entry e : t.entries().values()) {
            assertTrue(subs.contains(e.sub()), e.label() + " → " + e.to());
            Set<String> known = WorldcraftCommand.options(e.sub());
            for (String d : e.defaults()) assertTrue(known.contains(d), e.label() + ": --" + d + " no es de /wc " + e.sub());
            for (var f : e.flags().entrySet()) {
                String v = f.getValue();
                if (v.isEmpty()) continue;
                if (v.startsWith("/")) {
                    assertTrue(subs.contains(v.substring(1).split(" ")[0]), e.label() + " -" + f.getKey() + " → " + v);
                    continue;
                }
                String o = v.startsWith("!") ? v.substring(1) : v;
                assertTrue(known.contains(o), e.label() + " -" + f.getKey() + ": --" + o + " no es de /wc " + e.sub() + " " + known);
            }
        }
    }

    @Test
    void H1_CA1_doble_barra_para_cada_comando_de_wc_salvo_los_de_administracion() throws Exception {
        SlashTable t = table();
        for (String sub : WorldcraftCommand.subcommands()) {
            if (SlashTable.NOT_SLASHED.contains(sub)) assertNull(t.get("/" + sub), "//" + sub + " no se registra");
            else assertNotNull(t.get("/" + sub), "falta //" + sub);
        }
        assertEquals("superpickaxe", t.get("/").sub(), "// solo es el superpico, como en WorldEdit");
        assertEquals(List.of("sel", "clear"), t.get("/desel").to());
        assertEquals("brush", t.get("br").sub());
        assertEquals("generate", t.get("/g").sub());
        assertEquals("mountain", t.get("/mountain").sub());
    }

    /** Todos los comandos de WorldEdit 7.3.19 que la tabla de la 038 marca «yes» se escriben como en WorldEdit. */
    @Test
    void H1_CA1_los_de_worldedit_que_worldcraft_tiene_estan_todos() throws Exception {
        SlashTable t = table();
        String md = Files.readString(Path.of("../../docs/worldcraft/presentacion/commands.md"));
        String we = md.substring(md.indexOf("## Every WorldEdit 7.3.19 command"));
        we = we.substring(0, we.indexOf("\n## ", 5));
        // /mask y /size de una barra van dentro de /wc brush; /drawsel lo hace la vara; /worldedit queda de WorldEdit.
        Set<String> notNames = Set.of("mask", "size", "/drawsel", "worldedit");
        Matcher m = Pattern.compile("\\n\\| `([^`]+)` \\| yes \\|").matcher(we);
        List<String> missing = new ArrayList<>();
        int n = 0;
        while (m.find()) {
            String label = m.group(1).split(" ")[0].substring(1);
            if (notNames.contains(label)) continue;
            n++;
            if (t.get(label) == null) missing.add("/" + label);
        }
        assertTrue(n > 100, "se leyeron " + n);
        assertEquals("[]", missing.toString());
    }

    /** Spec 040: los nombres de una barra de WorldEdit y sus banderas. */
    @Test
    void spec040_los_nombres_nuevos_y_sus_banderas() throws Exception {
        SlashTable t = table();
        assertEquals("searchitem diamond --blocks", run(t, "searchitem", "diamond", "-b"));
        assertEquals("searchitem diamond --items", run(t, "/l", "diamond", "-i"));
        // F-01: //searchitem (el //x que se arma solo) tiene las banderas de /searchitem, como en WorldEdit.
        assertEquals("searchitem diamond --blocks", run(t, "/searchitem", "diamond", "-b"));
        assertEquals("tool navwand", run(t, "/wand", "-n"), "una entrada //x de la tabla no cambia");
        assertEquals("curve --hollow stone 1", run(t, "/curve", "-h", "stone", "1"));
        assertEquals("snowsmooth 2 --mask stone", run(t, "/snowsmooth", "2", "-m", "stone"));
        for (String n : List.of("toggleplace", "placement", "toggleeditwand", "range", "tracemask", "chunkinfo", "listchunks", "tz",
                "/fillr", "/feature", "/update")) assertNotNull(t.get(n), n);
    }

    @Test
    void spec041_los_nombres_de_FAWE() throws Exception {
        SlashTable t = table();
        assertEquals("fall --inside", run(t, "/fall", "-m"));
        assertEquals("place --origin --air --no-entities", run(t, "/place", "-o"), "como //paste: el aire y sin entidades de fábrica");
        assertEquals("gsmask !air", run(t, "/globalsourcemask", "!air"));
        assertEquals("fixblocks", run(t, "/fixconnect"));
        assertEquals("nbtinfo", run(t, "/nbt"));
        assertEquals("wea", run(t, "/worldeditanywhere"));
        assertEquals("target face", run(t, "/tar", "face"));
        for (String n : List.of("/gtexture", "/gtransform", "smask", "transform", "targetoffset", "scroll", "primary", "secondary", "/wer",
                "/registry", "/fixlighting")) assertNotNull(t.get(n), n);
    }

    @Test
    void H3_CA1_banderas_cortas_juntas_o_separadas() throws Exception {
        SlashTable t = table();
        assertEquals("copy --entities --biomes", run(t, "/copy", "-eb"));
        assertEquals("copy --mask stone above grass_block --entities", run(t, "/copy", "-m", "stone", "above", "grass_block", "-e"));
        assertEquals("stack 5 up --select --entities --air", run(t, "/stack", "5", "up", "-s", "-e"));
        assertEquals("hsphere glass 8", run(t, "/sphere", "glass", "8", "-h"), "-h es la hueca");
        assertEquals("hsphere --raised glass 8", run(t, "/sphere", "-hr", "glass", "8"));
        assertEquals("butcher 20 --pets --animals", run(t, "butcher", "20", "-pa"));
        assertEquals("tool navwand", run(t, "/wand", "-n"));
        assertEquals("replace dirt stone", run(t, "/re", "dirt", "stone"), "sin with, como ya funcionaba");
        assertEquals("sel clear", run(t, "/desel"));
        assertEquals("set air", run(t, "/0"));
        assertEquals("superpickaxe", run(t, "/"));
        assertEquals("mountain large --now", run(t, "/mountain", "large", "--now"), "las opciones largas pasan igual");
    }

    @Test
    void H3_CA2_los_valores_de_fabrica_de_worldedit() throws Exception {
        SlashTable t = table();
        assertEquals("paste --air --no-entities", run(t, "/paste"), "//paste pega el aire y deja las entidades, como WorldEdit");
        assertEquals("paste", run(t, "/paste", "-ae"));
        assertEquals("paste --origin --select --no-entities", run(t, "/paste", "-a", "-os"));
        assertEquals("paste --preview --air --no-entities", run(t, "/p", "--preview"));
        assertEquals("paste --air --no-entities", run(t, "/paste", "--air"), "una que ya se escribió no se repite");
        assertEquals("stack 3", run(t, "/stack", "3", "-a"));
        assertEquals("ores", run(t, "/ores", "-d", "-b"), "lo que Worldcraft ya hace solo se acepta");
    }

    @Test
    void H3_CA3_numeros_formulas_y_letras_que_no_estan_pasan_igual() throws Exception {
        SlashTable t = table();
        assertEquals("expand 5 -y", run(t, "/expand", "5", "-y"), "sin banderas: pasa todo");
        assertEquals("shift -5 up", run(t, "/shift", "-5", "up"));
        assertEquals("generate --hollow stone -x>0", run(t, "/g", "-h", "stone", "-x>0"));
        assertEquals("generate stone -x", run(t, "/g", "stone", "-x"), "x no es una bandera de //generate");
        assertEquals("paste -v --air --no-entities", run(t, "/paste", "-v"), "-v no está en Worldcraft: lo dice el comando");
        assertEquals("paste -av --air --no-entities", run(t, "/paste", "-av"), "si una letra no está, no se toca ninguna");
    }

    @Test
    void H1_CA2_el_autocompletado_no_agrega_los_de_fabrica() throws Exception {
        SlashTable t = table();
        assertArrayEquals(new String[]{"paste", "-"}, SlashTable.complete(t.get("/paste"), new String[]{"-"}));
        assertArrayEquals(new String[]{"sel", "clear", ""}, SlashTable.complete(t.get("/desel"), new String[]{""}));
        assertArrayEquals(new String[]{"sel", "cu"}, SlashTable.complete(t.get("/sel"), new String[]{"cu"}));
    }

    @Test
    void H4_CA1_una_tabla_con_errores_avisa() {
        Issues issues = new Issues();
        SlashTable t = SlashTable.load("""
                commands:
                  "/sett": { to: sett }
                  "/x": { to: set, flags: { ab: air } }
                  "/y": { to: set, colour: 3 }
                  "/z": { to: paste, aliases: ["/p"] }
                """, WorldcraftCommand.subcommands(), issues);
        String all = issues.all().toString();
        assertTrue(all.contains("commands./sett.to") && all.contains("did you mean 'set'"), all);
        assertTrue(all.contains("commands./x.flags.ab"), all);
        assertTrue(all.contains("commands./y.colour"), all);
        assertNull(t.get("/sett"));
        assertEquals("paste", t.get("/p").sub());
        assertEquals("set", t.get("/x").sub(), "una bandera mala se salta, la entrada queda");
        Set<String> labels = new TreeSet<>(t.entries().keySet());
        assertTrue(labels.contains("/set") && labels.contains("/y"), labels.toString());
    }
}

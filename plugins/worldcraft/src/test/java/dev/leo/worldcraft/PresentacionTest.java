package dev.leo.worldcraft;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec 038: las descripciones de SpigotMC, Modrinth y Hangar enlazan las mismas imágenes, que existen; dicen «not
 * affiliated»; y la cantidad de comandos que anuncian es la del código (las entradas de la ayuda de messages.yml).
 */
class PresentacionTest {
    static final Path RAIZ = Path.of("../..");
    static final Path DESCRIPCIONES = RAIZ.resolve("docs/worldcraft/presentacion");
    static final String CARPETA_IMAGENES = "docs/img/worldcraft/presentacion/";
    static final String RAW = "https://raw.githubusercontent.com/leonardos4enz/fabrica-de-plugins/main/";
    static final Pattern IMAGEN = Pattern.compile(Pattern.quote(RAW) + "([^\\s\"\\[\\]()]+\\.(?:png|gif))");
    static final String NO_AFILIADO = "Worldcraft is not affiliated with or endorsed by EngineHub (WorldEdit), IntellectualSites (FastAsyncWorldEdit) or Mojang.";
    static final Pattern WC = Pattern.compile("/wc ([a-z0-9_]+)");

    static String leer(String archivo) throws IOException {
        return Files.readString(DESCRIPCIONES.resolve(archivo), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static List<String> imagenes(String texto) {
        List<String> l = new ArrayList<>();
        Matcher m = IMAGEN.matcher(texto);
        while (m.find()) l.add(m.group(1));
        return l;
    }

    /** Los comandos de /wc: cada entrada de help: de messages.yml, menos los atajos («Short for …», como sp). */
    static Set<String> comandos() throws IOException, InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(Files.readString(Path.of("src/main/resources/messages.yml")));
        ConfigurationSection help = y.getConfigurationSection("help");
        Set<String> out = new TreeSet<>();
        for (String k : help.getKeys(false)) if (!help.getString(k + ".text", "").startsWith("Short for")) out.add(k);
        return out;
    }

    static Set<String> alias() throws IOException, InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(Files.readString(Path.of("src/main/resources/messages.yml")));
        Set<String> out = new TreeSet<>();
        for (String k : y.getConfigurationSection("help").getKeys(false)) out.add(k);
        // Alias de WorldEdit que se aceptan sin entrada propia en la ayuda.
        out.addAll(List.of("schematic", "floating_island", "gen", "genbiome", "img", "snap"));
        return out;
    }

    @Test
    void H4_CA1_las_dos_descripciones_enlazan_las_mismas_imagenes_en_el_mismo_orden_y_existen() throws IOException {
        List<String> spigot = imagenes(leer("spigotmc.bbcode"));
        List<String> md = imagenes(leer("modrinth-hangar.md"));
        assertFalse(spigot.isEmpty(), "spigotmc.bbcode no enlaza imágenes");
        assertEquals(spigot, md, "SpigotMC y Modrinth/Hangar deben mostrar las mismas imágenes en el mismo orden");
        for (String ruta : spigot) {
            assertTrue(ruta.startsWith(CARPETA_IMAGENES), "la imagen " + ruta + " no está en " + CARPETA_IMAGENES);
            assertTrue(Files.isRegularFile(RAIZ.resolve(ruta)), "falta la imagen " + ruta);
        }
    }

    @Test
    void H4_CA1_en_spigotmc_las_imagenes_van_en_una_sola_linea() throws IOException {
        // 020 F-05: SpigotMC pone un <br> por cada salto de línea entre [IMG] y deja una franja blanca entre las imágenes.
        String bbcode = leer("spigotmc.bbcode");
        List<String> lineas = bbcode.lines().filter(l -> l.contains("[IMG]")).toList();
        assertEquals(1, lineas.size(), "las [IMG] de spigotmc.bbcode deben ir todas en una sola línea");
        assertEquals(imagenes(bbcode).size(), lineas.get(0).split("\\[IMG]", -1).length - 1,
            "toda imagen de spigotmc.bbcode va como [IMG] en esa línea");
    }

    @Test
    void H4_CA1_las_dos_descripciones_traen_la_transcripcion_y_dicen_que_no_esta_afiliado() throws IOException {
        assertTrue(leer("spigotmc.bbcode").contains("[SPOILER=\"Image transcription\"]"), "spigotmc.bbcode sin transcripción");
        assertTrue(leer("modrinth-hangar.md").contains("<summary>Image transcription</summary>"), "modrinth-hangar.md sin transcripción");
        for (String d : List.of("spigotmc.bbcode", "modrinth-hangar.md", "commands.md")) {
            String texto = leer(d);
            assertTrue(texto.contains(NO_AFILIADO), d + " no dice «" + NO_AFILIADO + "»");
            int plegado = Math.max(texto.indexOf("[SPOILER"), texto.indexOf("<details>"));
            assertTrue(plegado < 0 || texto.indexOf(NO_AFILIADO) < plegado, d + ": la línea «not affiliated» debe verse sin desplegar nada");
        }
    }

    /** La sección de commands.md que empieza con ese título, hasta el siguiente «## ». */
    static String seccion(String md, String titulo) {
        int desde = md.indexOf(titulo);
        assertTrue(desde >= 0, "commands.md sin «" + titulo + "»");
        int hasta = md.indexOf("\n## ", desde + 5);
        return md.substring(desde, hasta < 0 ? md.length() : hasta);
    }

    static final Pattern FILA = Pattern.compile("\\n\\| `([^`]+)` \\| (yes|partly|not yet|no \\(on purpose\\)|not needed) \\|");

    /** Comando → estado de cada fila de una tabla de commands.md. */
    static java.util.Map<String, String> estados(String seccion) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        Matcher m = FILA.matcher(seccion);
        while (m.find()) assertEquals(null, out.put(m.group(1), m.group(2)), "fila repetida: " + m.group(1));
        return out;
    }

    static long contar(java.util.Map<String, String> filas, String estado) {
        return filas.values().stream().filter(estado::equals).count();
    }

    /**
     * Spec 040 H5 CA2: lo que se anuncia frente a WorldEdit y FAWE (cuántos tiene, el porcentaje y la fila de cada estado)
     * sale de contar las filas de commands.md, y las descripciones y el póster dicen lo mismo.
     */
    @Test
    void H5_CA2_las_cifras_frente_a_worldedit_y_fawe_salen_de_la_tabla() throws Exception {
        String md = leer("commands.md");
        var we = estados(seccion(md, "## Every WorldEdit 7.3.19 command"));
        var soloFawe = estados(seccion(md, "## What FAWE 2.16.2 adds to WorldEdit"));
        assertEquals(171, we.size(), "WorldEdit 7.3.19 tiene 171 comandos");
        assertEquals(79, soloFawe.size(), "FAWE agrega 79");
        // FAWE no tiene estos de WorldEdit (lo dice commands.md antes de su tabla).
        List<String> faltanEnFawe = List.of("//pos", "//update", "/placement", "/brush none", "/brush heightmap", "/worldedit report");
        java.util.Map<String, String> fawe = new java.util.LinkedHashMap<>(we);
        for (String c : faltanEnFawe) assertTrue(fawe.remove(c) != null, "commands.md sin la fila " + c);
        fawe.putAll(soloFawe);
        assertEquals(244, fawe.size(), "FAWE 2.16.2 tiene 244 comandos");
        String[] cols = {"yes", "partly", "not yet", "no (on purpose)", "not needed"};
        for (var t : List.of(java.util.Map.entry("WorldEdit 7.3.19", we), java.util.Map.entry("FAWE 2.16.2", fawe),
                java.util.Map.entry("FAWE, only what it adds to WorldEdit", soloFawe))) {
            String b = t.getKey().startsWith("FAWE,") ? "" : "**"; // la fila de lo propio de FAWE va sin negrita
            StringBuilder fila = new StringBuilder("| " + t.getKey() + " | " + t.getValue().size() + " | " + b + contar(t.getValue(), "yes") + b);
            for (int i = 1; i < cols.length; i++) fila.append(" | ").append(contar(t.getValue(), cols[i]));
            assertTrue(md.contains(fila + " |"), "commands.md debe decir: " + fila + " |");
        }
        long sWe = contar(we, "yes"), sFawe = contar(fawe, "yes");
        long pWe = Math.round(100.0 * sWe / 171), pFawe = Math.round(100.0 * sFawe / 244);
        long partWe = contar(we, "partly"), partFawe = contar(fawe, "partly");
        assertTrue(md.contains("**" + sWe + " of WorldEdit's 171 commands** (" + pWe + " %)"), "commands.md: " + sWe + " de 171");
        assertTrue(md.contains("**" + sFawe + " of FastAsyncWorldEdit's 244** (" + pFawe + " %)"), "commands.md: " + sFawe + " de 244");
        String modrinth = leer("modrinth-hangar.md"), spigot = leer("spigotmc.bbcode");
        assertTrue(modrinth.contains("| **" + sWe + "** (" + pWe + " %), " + partWe + " more in part |"), "modrinth-hangar.md: " + sWe);
        assertTrue(modrinth.contains("| **" + sFawe + "** (" + pFawe + " %), " + partFawe + " more in part |"), "modrinth-hangar.md: " + sFawe);
        assertTrue(spigot.contains("Worldcraft has [B]" + sWe + "[/B] (" + pWe + " %), " + partWe + " more in part."), "spigotmc.bbcode: " + sWe);
        assertTrue(spigot.contains("Worldcraft has [B]" + sFawe + "[/B] (" + pFawe + " %), " + partFawe + " more in part."), "spigotmc.bbcode: " + sFawe);
        for (String d : List.of(modrinth, spigot)) {
            assertTrue(d.replaceAll("\\s+", " ").contains(sWe + " of WorldEdit's 171: the same or better (" + pWe + " %), " + partWe + " more in part. "
                    + sFawe + " of FAWE's 244: the same or better (" + pFawe + " %), " + partFawe + " more in part."), "la pregunta «How many commands?»");
        }
        String poster = Files.readString(RAIZ.resolve("docs/site/assets-src/worldcraft.html"));
        assertTrue(poster.contains("<b>" + sWe + "</b><span>of WorldEdit's 171</span><small>the same or better (" + pWe + " %); " + partWe + " more in part"), "póster: " + sWe);
        assertTrue(poster.contains("<b>" + sFawe + "</b><span>of FAWE's 244</span><small>the same or better (" + pFawe + " %); " + partFawe + " more in part"), "póster: " + sFawe);
        String readme = Files.readString(Path.of("README.md"));
        assertTrue(readme.contains("**" + sWe + " of\nWorldEdit's 171 commands** and **" + sFawe + " of FAWE's 244**")
                || readme.contains("**" + sWe + " of WorldEdit's 171 commands** and **" + sFawe + " of FAWE's 244**"), "README de Worldcraft: " + sWe + " y " + sFawe);
    }

    @Test
    void H1_CA5_la_cantidad_de_comandos_anunciada_es_la_del_codigo() throws Exception {
        int n = comandos().size();
        assertEquals(138, n, "cambió la cantidad de comandos de /wc: actualizar las descripciones, commands.md y el póster");
        for (String d : List.of("spigotmc.bbcode", "modrinth-hangar.md", "commands.md")) {
            assertTrue(leer(d).contains(n + " commands"), d + " no dice «" + n + " commands»");
        }
        assertTrue(Files.readString(RAIZ.resolve("docs/site/assets-src/worldcraft.html")).contains(">" + n + "<"),
            "el póster (worldcraft.html) no muestra " + n);
    }

    @Test
    void H1_CA4_la_tabla_de_commands_md_es_exactamente_la_lista_de_comandos() throws Exception {
        String texto = leer("commands.md");
        int desde = texto.indexOf("## The " + comandos().size() + " commands of Worldcraft");
        int hasta = texto.indexOf("Inside the groups:", desde);
        assertTrue(desde >= 0 && hasta > desde, "commands.md sin la sección de los comandos");
        Set<String> tabla = new TreeSet<>();
        Matcher m = WC.matcher(texto.substring(desde, hasta));
        while (m.find()) tabla.add(m.group(1));
        assertEquals(comandos(), tabla, "la tabla de commands.md debe listar cada comando de /wc, una vez");
    }

    @Test
    void H1_CA4_todo_comando_wc_nombrado_existe() throws Exception {
        Set<String> conocidos = alias();
        for (String d : List.of("spigotmc.bbcode", "modrinth-hangar.md", "commands.md")) {
            Matcher m = WC.matcher(leer(d));
            while (m.find()) assertTrue(conocidos.contains(m.group(1)), d + " nombra /wc " + m.group(1) + ", que no existe");
        }
    }
}

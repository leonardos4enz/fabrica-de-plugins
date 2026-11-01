package dev.leo.skinstatues;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 024: las descripciones de SpigotMC, Modrinth y Hangar enlazan las mismas imágenes, que existen, y dicen «not affiliated». */
class PresentacionTest {
    static final Path RAIZ = Path.of("../..");
    static final Path DESCRIPCIONES = RAIZ.resolve("docs/skinstatues/presentacion");
    static final String CARPETA_IMAGENES = "docs/img/skinstatues/presentacion/";
    static final String RAW = "https://raw.githubusercontent.com/leonardos4enz/fabrica-de-plugins/main/";
    static final Pattern IMAGEN = Pattern.compile(Pattern.quote(RAW) + "([^\\s\"\\[\\]]+\\.(?:png|gif))");
    static final String NO_AFILIADO = "SkinStatues is not affiliated with or endorsed by them or by Mojang.";

    static String leer(String archivo) throws IOException {
        return Files.readString(DESCRIPCIONES.resolve(archivo), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static List<String> imagenes(String texto) {
        List<String> l = new ArrayList<>();
        Matcher m = IMAGEN.matcher(texto);
        while (m.find()) l.add(m.group(1));
        return l;
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
    void H4_CA1_las_dos_descripciones_traen_la_transcripcion() throws IOException {
        assertTrue(leer("spigotmc.bbcode").contains("[SPOILER=\"Image transcription\"]"), "spigotmc.bbcode sin transcripción");
        assertTrue(leer("modrinth-hangar.md").contains("<summary>Image transcription</summary>"), "modrinth-hangar.md sin transcripción");
    }

    @Test
    void H4_CA4_las_dos_descripciones_dicen_que_no_esta_afiliado() throws IOException {
        for (String d : List.of("spigotmc.bbcode", "modrinth-hangar.md")) {
            String texto = leer(d);
            assertTrue(texto.contains(NO_AFILIADO), d + " no dice «" + NO_AFILIADO + "»");
            // Visible: fuera de la transcripción plegada.
            int plegado = Math.max(texto.indexOf("[SPOILER"), texto.indexOf("<details>"));
            assertTrue(plegado < 0 || texto.indexOf(NO_AFILIADO) < plegado, d + ": la línea «not affiliated» debe verse sin desplegar nada");
        }
    }
}

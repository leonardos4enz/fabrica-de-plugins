package dev.leo.omnievents.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 020: las descripciones de SpigotMC, Modrinth y Hangar enlazan imágenes que existen y dicen lo que hacen los ejemplos. */
class PresentacionTest {
    static final Path RAIZ = Path.of("../..");
    static final Path DESCRIPCIONES = RAIZ.resolve("docs/omnievents/presentacion");
    static final String RAW = "https://raw.githubusercontent.com/leonardos4enz/fabrica-de-plugins/main/";
    static final Pattern IMAGEN = Pattern.compile(Pattern.quote(RAW) + "([^\\s\"\\[\\]]+\\.(?:png|gif))");

    static String leer(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static List<String> imagenes(String texto) {
        List<String> l = new ArrayList<>();
        Matcher m = IMAGEN.matcher(texto);
        while (m.find()) l.add(m.group(1));
        return l;
    }

    @Test
    void H4_CA2_las_dos_descripciones_enlazan_las_mismas_imagenes_y_existen() throws IOException {
        List<String> spigot = imagenes(leer(DESCRIPCIONES.resolve("spigotmc.bbcode")));
        List<String> md = imagenes(leer(DESCRIPCIONES.resolve("modrinth-hangar.md")));
        assertFalse(spigot.isEmpty(), "spigotmc.bbcode no enlaza imágenes");
        assertEquals(spigot, md, "SpigotMC y Modrinth/Hangar deben mostrar las mismas imágenes en el mismo orden");
        for (String ruta : spigot) assertTrue(Files.isRegularFile(RAIZ.resolve(ruta)), "falta la imagen " + ruta);
    }

    @Test
    void H4_CA3_la_transcripcion_cita_mensajes_que_los_ejemplos_dicen() throws IOException {
        // Lo que la transcripción pone entre comillas sale de las reglas del paquete en inglés (sin los {placeholders}).
        StringBuilder reglas = new StringBuilder();
        try (Stream<Path> s = Files.list(RAIZ.resolve("plugins/omnievents/src/main/resources/rules/examples-en"))) {
            for (Path p : s.sorted().toList()) reglas.append(leer(p).replaceAll("<[^>]+>", ""));
        }
        String texto = reglas.toString();
        for (String citado : List.of("it's their first time here!", "Type /daily for your daily reward", "Daily reward: +",
            "You already claimed today. Come back in", "Mining boost on", "Only VIP players can enter this area.",
            "Try /daily, /shop and /coins.", "says: hey, that hurts!")) {
            assertTrue(texto.contains(citado), "los ejemplos ya no dicen «" + citado + "»: rehacer las escenas y la transcripción");
            for (String d : List.of("spigotmc.bbcode", "modrinth-hangar.md"))
                assertTrue(leer(DESCRIPCIONES.resolve(d)).contains(citado), d + " no transcribe «" + citado + "»");
        }
    }
}

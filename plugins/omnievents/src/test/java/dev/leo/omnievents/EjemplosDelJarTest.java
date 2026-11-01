package dev.leo.omnievents;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Spec 019, F-01: cada archivo de ejemplo del jar se copia a rules/ en un servidor nuevo (el parkour de la 018 no se copiaba). */
class EjemplosDelJarTest {
    @Test
    void F01_cada_ejemplo_del_jar_se_copia() throws IOException {
        Path resources = Path.of("src/main/resources");
        List<String> files;
        try (Stream<Path> s = Files.walk(resources.resolve("rules"))) {
            files = s.filter(Files::isRegularFile).map(p -> resources.relativize(p).toString().replace(File.separatorChar, '/')).sorted().toList();
        }
        assertEquals(files, OmniEvents.EXAMPLES.stream().sorted().toList());
    }
}

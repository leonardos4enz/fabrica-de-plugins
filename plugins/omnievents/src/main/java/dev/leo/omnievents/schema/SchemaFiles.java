package dev.leo.omnievents.schema;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.trigger.Triggers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Los dos archivos que se generan (spec 007, H3) y su escritura atómica. */
public final class SchemaFiles {
    public static final String SCHEMA = "omnievents.schema.json";
    public static final String REFERENCE = "referencia.md";
    public static final String PACK_SCHEMA = "omnievents-pack.schema.json";

    private SchemaFiles() {
    }

    /** Nombre de archivo → contenido. Se arma en el hilo principal (lee el registro). */
    public static Map<String, String> render(Triggers triggers, Actions actions) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(SCHEMA, SchemaGenerator.generate(triggers, actions));
        out.put(PACK_SCHEMA, SchemaGenerator.generatePack(triggers, actions));
        out.put(REFERENCE, ReferenceGenerator.generate(triggers, actions));
        return out;
    }

    /** Escribe cada archivo con un temporal y un reemplazo atómico (constitución II.6). Devuelve las rutas. */
    public static List<Path> write(Path folder, Map<String, String> files) throws IOException {
        Files.createDirectories(folder);
        List<Path> out = new ArrayList<>();
        for (Map.Entry<String, String> e : files.entrySet()) {
            Path target = folder.resolve(e.getKey());
            Path tmp = folder.resolve(e.getKey() + ".tmp");
            Files.writeString(tmp, e.getValue(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            out.add(target);
        }
        return out;
    }
}

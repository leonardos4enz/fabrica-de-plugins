package dev.leo.omnievents.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Texto y archivo de /omni debug export (spec 006, H5): encabezado, problemas de la última carga, las trazas y el YAML
 * de las reglas que aparecen. Sin red: solo escribe en la carpeta del plugin.
 */
public final class TraceExport {
    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TraceExport() {
    }

    public static String render(List<String> header, List<String> problems, List<Trace> traces, Map<String, String> ruleYaml) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Traza de OmniEvents\n");
        header.forEach(h -> sb.append(h).append('\n'));
        sb.append("\n## Problemas de la última carga (").append(problems.size()).append(")\n");
        if (problems.isEmpty()) sb.append("(ninguno)\n");
        problems.forEach(p -> sb.append(p).append('\n'));
        sb.append("\n## Trazas (").append(traces.size()).append(")\n");
        for (Trace t : traces) {
            String when = LocalDateTime.ofInstant(Instant.ofEpochMilli(t.createdMs()), ZoneId.systemDefault()).format(TIME);
            sb.append("\n[").append(when).append("] ").append(t.simulation() ? "simulación de " : "regla ").append(t.rule()).append('\n');
            t.lines().forEach(l -> sb.append("  ").append(l).append('\n'));
        }
        sb.append("\n## Reglas que aparecen (").append(ruleYaml.size()).append(")\n");
        ruleYaml.forEach((name, yaml) -> sb.append("\n# ").append(name).append('\n').append(yaml.endsWith("\n") ? yaml : yaml + "\n"));
        return sb.toString();
    }

    /** Escribe trazas/&lt;fecha-hora&gt;.txt de forma atómica (constitución II.6); con sufijo -2, -3… si ya existe. */
    public static Path write(Path folder, String text, LocalDateTime now) throws IOException {
        Files.createDirectories(folder);
        String base = now.format(FILE);
        Path target = folder.resolve(base + ".txt");
        for (int i = 2; Files.exists(target); i++) target = folder.resolve(base + "-" + i + ".txt");
        Path tmp = folder.resolve(target.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }
}

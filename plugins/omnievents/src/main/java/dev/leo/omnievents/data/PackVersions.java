package dev.leo.omnievents.data;

import dev.leo.omnievents.rule.Pack;
import dev.leo.omnievents.util.Versions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** La última versión cargada de cada paquete, en data/packs.yml (spec 011, H3 CA2). */
public final class PackVersions {
    /** Lo que toca hacer con un paquete: de qué versión a cuál y qué migraciones corren (vacío = solo guardar). */
    public record Step(Pack pack, String from, String to, List<Pack.Migration> migrations) {
    }

    private final File file;
    private final Logger log;
    private final Map<String, String> versions = new LinkedHashMap<>();

    public PackVersions(File dataFolder, Logger log) {
        this.file = new File(new File(dataFolder, "data"), "packs.yml");
        this.log = log;
    }

    /** Lee el archivo. Si falta está vacío; si está roto, avisa y queda vacío (no corre migraciones de más). */
    public void load() {
        versions.clear();
        if (!file.exists()) return;
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            if (log != null) log.warning("data/packs.yml no se pudo leer (" + e.getMessage() + "): se toma como vacío y no corren migraciones");
            return;
        }
        ConfigurationSection s = y.getConfigurationSection("packs");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            String v = s.getString(k);
            if (Versions.valid(v)) versions.put(k, v);
        }
    }

    public String get(String pack) {
        return versions.get(pack);
    }

    /**
     * Qué hacer con cada paquete cargado y activo: la primera vez solo se guarda; con una versión mayor corren las
     * migraciones con guardada &lt; v &lt;= nueva; con una menor, nada (H3 CA2). No cambia lo guardado.
     */
    public List<Step> plan(List<Pack> packs) {
        List<Step> out = new ArrayList<>();
        for (Pack p : packs) {
            if (!p.loaded() || !p.enabled()) continue;
            String old = versions.get(p.name());
            if (old == null) {
                out.add(new Step(p, null, p.version(), List.of()));
                continue;
            }
            int c = Versions.compare(p.version(), old);
            if (c == 0) continue;
            List<Pack.Migration> run = new ArrayList<>();
            if (c > 0) {
                for (Pack.Migration m : p.migrations()) {
                    if (Versions.compare(m.version(), old) > 0 && Versions.compare(m.version(), p.version()) <= 0) run.add(m);
                }
            }
            out.add(new Step(p, old, p.version(), run));
        }
        return out;
    }

    /** Guarda la versión nueva de un paso que sube (o la primera); uno que baja no cambia lo guardado. */
    public void record(Step s) {
        if (s.from() == null || Versions.compare(s.to(), s.from()) > 0) versions.put(s.pack().name(), s.to());
    }

    /** Escritura atómica: temporal + reemplazo (constitución II.6). */
    public void save() throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        y.set("packs", new LinkedHashMap<>(versions));
        File dir = file.getParentFile();
        dir.mkdirs();
        File tmp = new File(dir, "packs.yml.tmp");
        Files.writeString(tmp.toPath(), "# Última versión cargada de cada paquete (OmniEvents, spec 011). No se edita a mano.\n" + y.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

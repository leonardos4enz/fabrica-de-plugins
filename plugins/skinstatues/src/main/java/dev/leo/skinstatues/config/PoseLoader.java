package dev.leo.skinstatues.config;

import dev.leo.skinstatues.model.Pose;
import dev.leo.skinstatues.model.Pose.Angles;
import dev.leo.skinstatues.model.Pose.Part;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** poses.yml (H4 CA1, CA4): una pose con errores se omite y se informa; las demás cargan. */
public final class PoseLoader {
    private static final List<String> ANGLES = List.of("pitch", "yaw", "roll");

    private PoseLoader() {
    }

    public static Map<String, Pose> load(ConfigurationSection root, String file, Issues issues) {
        Map<String, Pose> out = new LinkedHashMap<>();
        List<String> parts = new ArrayList<>();
        for (Part p : Part.values()) parts.add(p.key());
        for (String name : root.getKeys(false)) {
            String id = name.toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_-]{1,32}")) {
                issues.error(file, name, "pose names use letters, digits, '_' or '-' (up to 32)");
                continue;
            }
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) {
                if (root.get(name) == null || "{}".equals(String.valueOf(root.get(name)))) out.put(id, new Pose(id, Map.of()));
                else issues.error(file, name, "a pose is a map of body parts (head, body, right_arm…)");
                continue;
            }
            Map<Part, Angles> angles = new EnumMap<>(Part.class);
            boolean ok = true;
            for (String key : sec.getKeys(false)) {
                Part part = part(key);
                String path = name + "." + key;
                if (part == null) {
                    issues.error(file, path, "unknown body part '" + key + "'" + Issues.hint(key, parts));
                    ok = false;
                    continue;
                }
                ConfigurationSection ps = sec.getConfigurationSection(key);
                if (ps == null) {
                    issues.error(file, path, "expected angles, e.g. { pitch: 20, roll: 10 }");
                    ok = false;
                    continue;
                }
                List<String> allowed = new ArrayList<>(ANGLES);
                if (part.bendKey() != null) allowed.add(part.bendKey());
                double[] v = new double[4];
                for (String k : ps.getKeys(false)) {
                    String kp = path + "." + k;
                    int slot = allowed.indexOf(k.toLowerCase(Locale.ROOT));
                    if (slot < 0) {
                        String extra = (k.equalsIgnoreCase("elbow") || k.equalsIgnoreCase("knee")) && part.bendKey() != null
                                ? " (" + part.key() + " bends with '" + part.bendKey() + "')"
                                : part.bendKey() == null && (k.equalsIgnoreCase("elbow") || k.equalsIgnoreCase("knee"))
                                ? " (only arms bend the elbow and legs the knee)" : Issues.hint(k, allowed);
                        issues.error(file, kp, "unknown key '" + k + "'" + extra);
                        ok = false;
                        continue;
                    }
                    Object raw = ps.get(k);
                    if (!(raw instanceof Number n)) {
                        issues.error(file, kp, "'" + raw + "' is not a number (degrees)");
                        ok = false;
                        continue;
                    }
                    double d = n.doubleValue();
                    boolean bend = slot == 3;
                    if (bend ? d < 0 || d > 150 : d < -180 || d > 180) {
                        issues.error(file, kp, d + " is out of range (" + (bend ? "0..150" : "-180..180") + ")");
                        ok = false;
                        continue;
                    }
                    v[slot] = d;
                }
                angles.put(part, new Angles(v[0], v[1], v[2], v[3]));
            }
            if (ok) out.put(id, new Pose(id, angles));
        }
        // La pose de pie siempre existe (es la de por defecto).
        out.putIfAbsent(Pose.STANDING.name(), Pose.STANDING);
        return out;
    }

    private static Part part(String key) {
        for (Part p : Part.values()) if (p.key().equalsIgnoreCase(key)) return p;
        return null;
    }
}

package dev.leo.skinstatues.config;

import dev.leo.skinstatues.model.Pose;
import dev.leo.skinstatues.model.Pose.Angles;
import dev.leo.skinstatues.model.Pose.Part;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Poses (spec 021 H4; spec 022 H1, H2): las incorporadas del jar y, encima, las de poses.yml. Se cargan en dos etapas
 * (plan 022 §1.2): primero cada archivo a definiciones crudas (un ángulo no escrito es NaN: se hereda) y luego se
 * resuelven `extends` y `mirror`. Una pose con errores se omite y se informa; las demás cargan.
 */
public final class PoseLoader {
    public static final int MAX_DEPTH = 8;
    private static final List<String> ANGLES = List.of("pitch", "yaw", "roll");
    private static final String EXTENDS = "extends", MIRROR = "mirror";

    /** De dónde sale cada pose, para /statue poses (H2 CA5). */
    public enum Origin { BUILTIN, CUSTOM, OVERRIDE }

    public record Result(Map<String, Pose> poses, Map<String, Origin> origins) {
    }

    /** Definición tal como está escrita: `own` tiene por parte {pitch, yaw, roll, doblez}, con NaN si no se escribió. */
    private record Raw(String name, String file, String parent, boolean mirror, Map<Part, double[]> own, boolean ok) {
        boolean sameAs(Raw o) {
            if (!java.util.Objects.equals(parent, o.parent) || mirror != o.mirror || !own.keySet().equals(o.own.keySet())) return false;
            for (Map.Entry<Part, double[]> e : own.entrySet()) if (!Arrays.equals(e.getValue(), o.own.get(e.getKey()))) return false;
            return true;
        }
    }

    private PoseLoader() {
    }

    /** Un solo archivo (sin incorporadas). */
    public static Map<String, Pose> load(ConfigurationSection root, String file, Issues issues) {
        return load(null, null, root, file, issues).poses();
    }

    /**
     * Las incorporadas primero y las del dueño encima: un nombre nuevo agrega, uno repetido reemplaza la incorporada
     * entera (H1 CA3), salvo que sea idéntica (el poses.yml de la 0.1.0 trae las 6 de entonces).
     */
    public static Result load(ConfigurationSection builtin, String builtinFile, ConfigurationSection user, String userFile,
                              Issues issues) {
        Map<String, Raw> raws = new LinkedHashMap<>();
        Map<String, Origin> origins = new LinkedHashMap<>();
        if (builtin != null) {
            for (Raw r : read(builtin, builtinFile, issues)) {
                raws.put(r.name(), r);
                origins.put(r.name(), Origin.BUILTIN);
            }
        }
        if (user != null) {
            for (Raw r : read(user, userFile, issues)) {
                Raw before = raws.put(r.name(), r);
                if (before == null) origins.put(r.name(), Origin.CUSTOM);
                else if (!before.sameAs(r)) origins.put(r.name(), Origin.OVERRIDE);
            }
        }

        Map<String, Pose> resolved = new HashMap<>();
        Set<String> failed = new HashSet<>();
        Map<String, Pose> out = new LinkedHashMap<>();
        for (String name : raws.keySet()) {
            Pose p = resolve(name, raws, resolved, failed, new ArrayDeque<>(), issues);
            if (p != null) out.put(name, p);
        }
        // La pose de pie siempre existe (es la de por defecto).
        if (!out.containsKey(Pose.STANDING.name())) {
            out.put(Pose.STANDING.name(), Pose.STANDING);
            origins.putIfAbsent(Pose.STANDING.name(), Origin.BUILTIN);
        }
        origins.keySet().retainAll(out.keySet());
        return new Result(out, origins);
    }

    // ---------------------------------------------------------------- etapa 1: leer

    private static List<Raw> read(ConfigurationSection root, String file, Issues issues) {
        List<Raw> out = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (Part p : Part.values()) keys.add(p.key());
        keys.add(EXTENDS);
        keys.add(MIRROR);
        for (String name : root.getKeys(false)) {
            String id = name.toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_-]{1,32}")) {
                issues.error(file, name, "pose names use letters, digits, '_' or '-' (up to 32)");
                continue;
            }
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) {
                if (root.get(name) == null || "{}".equals(String.valueOf(root.get(name)))) {
                    out.add(new Raw(id, file, null, false, Map.of(), true));
                } else {
                    issues.error(file, name, "a pose is a map of body parts (head, body, right_arm…)");
                    out.add(new Raw(id, file, null, false, Map.of(), false));
                }
                continue;
            }
            Map<Part, double[]> angles = new EnumMap<>(Part.class);
            String parent = null;
            boolean mirror = false, ok = true;
            for (String key : sec.getKeys(false)) {
                String path = name + "." + key;
                if (key.equalsIgnoreCase(EXTENDS)) {
                    Object raw = sec.get(key);
                    if (raw instanceof ConfigurationSection || raw == null || String.valueOf(raw).isBlank()) {
                        issues.error(file, path, "expected the name of another pose, e.g. extends: waving");
                        ok = false;
                    } else {
                        parent = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
                    }
                    continue;
                }
                if (key.equalsIgnoreCase(MIRROR)) {
                    Object raw = sec.get(key);
                    if (raw instanceof Boolean b) mirror = b;
                    else {
                        issues.error(file, path, "'" + raw + "' is not true or false");
                        ok = false;
                    }
                    continue;
                }
                Part part = part(key);
                if (part == null) {
                    issues.error(file, path, "unknown key '" + key + "'" + Issues.hint(key, keys));
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
                double[] v = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
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
                angles.put(part, v);
            }
            if (mirror && parent == null && ok) {
                issues.error(file, name + "." + MIRROR, "mirror flips the pose it extends: add extends: <pose>");
                ok = false;
            }
            out.add(new Raw(id, file, parent, mirror, angles, ok));
        }
        return out;
    }

    // ---------------------------------------------------------------- etapa 2: resolver

    private static Pose resolve(String name, Map<String, Raw> raws, Map<String, Pose> resolved, Set<String> failed,
                                Deque<String> stack, Issues issues) {
        Pose done = resolved.get(name);
        if (done != null || failed.contains(name)) return done;
        Raw r = raws.get(name);
        if (!r.ok()) {
            failed.add(name);
            return null;
        }
        Pose base = Pose.STANDING;
        if (r.parent() != null) {
            String path = r.name() + "." + EXTENDS;
            String problem = null;
            if (!raws.containsKey(r.parent())) {
                problem = "there is no pose '" + r.parent() + "'" + Issues.hint(r.parent(), raws.keySet());
            } else if (stack.contains(r.parent()) || r.parent().equals(name)) {
                List<String> cycle = new ArrayList<>(stack);
                java.util.Collections.reverse(cycle);
                cycle = new ArrayList<>(cycle.subList(Math.max(0, cycle.indexOf(r.parent())), cycle.size()));
                cycle.add(name);
                cycle.add(r.parent());
                problem = "extends goes in a circle (" + String.join(" → ", cycle) + ")";
            } else {
                stack.push(name);
                base = resolve(r.parent(), raws, resolved, failed, stack, issues);
                stack.pop();
                if (base == null && !failed.contains(name)) problem = "it extends '" + r.parent() + "', which has errors";
                else if (base != null && levels(r.parent(), raws) >= MAX_DEPTH) {
                    // La profundidad es la de la cadena hasta una pose sin extends, no la del orden en que se cargan.
                    problem = "more than " + MAX_DEPTH + " levels of extends";
                    base = null;
                }
            }
            if (problem != null || base == null) {
                if (problem != null) issues.error(r.file(), path, problem);
                failed.add(name);
                return null;
            }
            if (r.mirror()) base = base.mirrored(name);
        }
        Map<Part, Angles> merged = new EnumMap<>(Part.class);
        for (Part p : Part.values()) {
            Angles a = base.of(p);
            double[] own = r.own().get(p);
            if (own != null) {
                a = new Angles(pick(own[0], a.pitch()), pick(own[1], a.yaw()), pick(own[2], a.roll()), pick(own[3], a.bend()));
            }
            if (!a.equals(Angles.NONE)) merged.put(p, a);
        }
        Pose p = new Pose(name, merged);
        resolved.put(name, p);
        return p;
    }

    /** Cuántos extends hay de esta pose (ya resuelta, sin ciclos) hasta una que no extiende ninguna. */
    private static int levels(String name, Map<String, Raw> raws) {
        int n = 0;
        for (String p = raws.get(name).parent(); p != null; p = raws.get(p).parent()) n++;
        return n;
    }

    private static double pick(double own, double inherited) {
        return Double.isNaN(own) ? inherited : own;
    }

    private static Part part(String key) {
        for (Part p : Part.values()) if (p.key().equalsIgnoreCase(key)) return p;
        return null;
    }
}

package dev.leo.worldcraft.config;

import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Parser;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Límites por grupo de permisos (H7, sección limits de config.yml). «default» vale para todos; cada otro grupo se da
 * con el permiso worldcraft.limit.&lt;grupo&gt;. Con varios grupos gana lo más generoso de cada valor: el mayor número,
 * todos los mundos de todos, y un bloque está prohibido solo si lo prohíben todos.
 */
public final class Limits {
    public static final String PERMISSION_PREFIX = "worldcraft.limit.";
    private static final List<String> KEYS = List.of("max_blocks", "max_jobs", "worlds", "blocked_blocks", "max_entities", "blocked_entities");
    /** Spec 031: lo que no se puede crear de fábrica (comandos, generadores, explosivos, bloques que caen, jefes). */
    public static final Set<String> DEFAULT_BLOCKED_ENTITIES = Set.of("minecraft:command_block_minecart",
            "minecraft:spawner_minecart", "minecraft:tnt", "minecraft:tnt_minecart", "minecraft:falling_block",
            "minecraft:wither", "minecraft:ender_dragon");

    /** Un grupo; null en un campo = hereda de default. blocked con la máscara vacía (ANY) no se usa: null = sin prohibidos. */
    public record Group(String name, Long maxBlocks, Integer maxJobs, Set<String> worlds, Mask blocked, boolean blockedSet,
                        Integer maxEntities, Set<String> blockedEntities) {
        public Group(String name, Long maxBlocks, Integer maxJobs, Set<String> worlds, Mask blocked, boolean blockedSet) {
            this(name, maxBlocks, maxJobs, worlds, blocked, blockedSet, null, null);
        }
    }

    /** Lo que le toca a un jugador. */
    public record Resolved(long maxBlocks, int maxJobs, Set<String> worlds, List<Mask> blocked, List<String> groups,
                           int maxEntities, Set<String> blockedEntities) {
        /** Spec 031: un tipo de entidad que el jugador no puede crear (prohibido en todos sus grupos). */
        public boolean isEntityBlocked(String type) {
            return blockedEntities.contains(type);
        }

        public boolean world(String name) {
            return worlds.contains("*") || worlds.contains(name.toLowerCase(Locale.ROOT));
        }

        /** Prohibido si lo prohíben todos los grupos que lo dicen (lista vacía de algún grupo = nada prohibido). */
        public boolean isBlocked(String material, Map<String, String> props) {
            if (blocked.isEmpty()) return false;
            for (Mask m : blocked) if (m == null || !m.test(material, props)) return false;
            return true;
        }
    }

    public static final Group DEFAULT_GROUP = new Group("default", 50_000L, 2, Set.of("*"), null, false, 200, DEFAULT_BLOCKED_ENTITIES);

    private final Map<String, Group> groups;

    private Limits(Map<String, Group> groups) {
        this.groups = groups;
    }

    public Map<String, Group> groups() {
        return groups;
    }

    public static Limits load(ConfigurationSection limits, Parser parser, String file, Issues issues) {
        return load(limits, parser, file, issues, List.of());
    }

    /** entityTypes: los ids de entidad que existen (para avisar un nombre mal escrito); vacío = no se revisa. */
    public static Limits load(ConfigurationSection limits, Parser parser, String file, Issues issues, java.util.Collection<String> entityTypes) {
        Map<String, Group> out = new LinkedHashMap<>();
        if (limits == null) {
            out.put("default", DEFAULT_GROUP);
            return new Limits(out);
        }
        for (String name : limits.getKeys(false)) {
            String path = "limits." + name;
            ConfigurationSection s = limits.getConfigurationSection(name);
            if (s == null) {
                issues.error(file, path, "must be a section with max_blocks, max_jobs, worlds or blocked_blocks");
                continue;
            }
            if (!name.matches("[a-z0-9_-]+")) {
                issues.error(file, path, "group names use lowercase letters, numbers, '_' and '-' (it is part of a permission)");
                continue;
            }
            for (String k : s.getKeys(false)) {
                if (!KEYS.contains(k)) issues.warn(file, path + "." + k, "unknown setting" + Issues.hint(k, KEYS));
            }
            Long max = number(s, "max_blocks", path, file, issues, 1, 2_000_000_000L);
            Long jobs = number(s, "max_jobs", path, file, issues, 1, 100);
            Set<String> worlds = null;
            if (s.contains("worlds")) {
                worlds = new LinkedHashSet<>();
                for (String w : s.getStringList("worlds")) worlds.add(w.toLowerCase(Locale.ROOT));
            }
            Mask blocked = null;
            boolean blockedSet = s.contains("blocked_blocks");
            if (blockedSet) {
                List<String> list = s.getStringList("blocked_blocks");
                if (!list.isEmpty()) {
                    List<Mask.Term> terms = new ArrayList<>();
                    for (int i = 0; i < list.size(); i++) {
                        try {
                            terms.addAll(parser.mask(list.get(i)).terms());
                        } catch (ParseException e) {
                            issues.error(file, path + ".blocked_blocks[" + i + "]", e.getMessage() + ": skipped");
                        }
                    }
                    blocked = terms.isEmpty() ? null : new Mask(terms, String.join(", ", list));
                }
            }
            Long ents = number(s, "max_entities", path, file, issues, 0, 1_000_000);
            Set<String> blockedEnts = null;
            if (s.contains("blocked_entities")) {
                blockedEnts = new LinkedHashSet<>();
                List<String> list = s.getStringList("blocked_entities");
                for (int i = 0; i < list.size(); i++) {
                    String id = entityId(list.get(i));
                    if (!entityTypes.isEmpty() && !entityTypes.contains(id)) {
                        issues.error(file, path + ".blocked_entities[" + i + "]", "unknown entity '" + list.get(i) + "'"
                                + Issues.hint(id.replace("minecraft:", ""), entityTypes.stream().map(t -> t.replace("minecraft:", "")).toList()) + ": skipped");
                        continue;
                    }
                    blockedEnts.add(id);
                }
            }
            out.put(name, new Group(name, max, jobs == null ? null : jobs.intValue(), worlds, blocked, blockedSet,
                    ents == null ? null : ents.intValue(), blockedEnts));
        }
        if (!out.containsKey("default")) {
            issues.warn(file, "limits", "no 'default' group: using max_blocks 50000, max_jobs 2, all worlds");
            out.put("default", DEFAULT_GROUP);
        } else {
            Group g = out.get("default");
            out.put("default", new Group("default", g.maxBlocks() == null ? DEFAULT_GROUP.maxBlocks() : g.maxBlocks(),
                    g.maxJobs() == null ? DEFAULT_GROUP.maxJobs() : g.maxJobs(),
                    g.worlds() == null ? DEFAULT_GROUP.worlds() : g.worlds(), g.blocked(), g.blockedSet(),
                    g.maxEntities() == null ? DEFAULT_GROUP.maxEntities() : g.maxEntities(),
                    g.blockedEntities() == null ? DEFAULT_GROUP.blockedEntities() : g.blockedEntities()));
        }
        return new Limits(out);
    }

    /** «cow», «Minecraft:Cow» → «minecraft:cow». */
    public static String entityId(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        return n.contains(":") ? n : "minecraft:" + n;
    }

    private static Long number(ConfigurationSection s, String key, String path, String file, Issues issues, long lo, long hi) {
        Object v = s.get(key);
        if (v == null) return null;
        if (!(v instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue()) || n.longValue() < lo || n.longValue() > hi) {
            issues.error(file, path + "." + key, "'" + v + "' must be a whole number from " + lo + " to " + hi + ": ignored");
            return null;
        }
        return n.longValue();
    }

    /** Los límites de quien tiene esos permisos de grupo. */
    public Resolved resolve(Predicate<String> hasPermission) {
        Group d = groups.get("default");
        long max = d.maxBlocks();
        int jobs = d.maxJobs();
        Set<String> worlds = new LinkedHashSet<>(d.worlds());
        List<Mask> blocked = new ArrayList<>();
        List<String> names = new ArrayList<>(List.of("default"));
        int ents = d.maxEntities();
        // Spec 031: un tipo está prohibido si lo prohíben todos los grupos (como los bloques).
        Set<String> blockedEnts = null;
        boolean any = false;
        for (Group g : groups.values()) {
            if (g.name().equals("default") || !hasPermission.test(PERMISSION_PREFIX + g.name())) continue;
            any = true;
            names.add(g.name());
            if (g.maxBlocks() != null) max = Math.max(max, g.maxBlocks());
            if (g.maxJobs() != null) jobs = Math.max(jobs, g.maxJobs());
            worlds.addAll(g.worlds() == null ? d.worlds() : g.worlds());
            if (g.maxEntities() != null) ents = Math.max(ents, g.maxEntities());
            Set<String> be = g.blockedEntities() == null ? d.blockedEntities() : g.blockedEntities();
            if (blockedEnts == null) blockedEnts = new LinkedHashSet<>(be);
            else blockedEnts.retainAll(be);
            if (g.blockedSet()) blocked.add(g.blocked());
            else blocked.add(d.blocked());
        }
        if (!any) blocked.add(d.blocked());
        // Sin ninguna máscara declarada (null en todas) no hay nada prohibido.
        if (blocked.stream().allMatch(m -> m == null)) blocked.clear();
        if (blockedEnts == null) blockedEnts = new LinkedHashSet<>(d.blockedEntities());
        return new Resolved(max, jobs, Set.copyOf(worlds), blocked, List.copyOf(names), ents, Set.copyOf(blockedEnts));
    }
}

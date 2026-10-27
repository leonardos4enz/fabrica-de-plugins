package dev.leo.omnievents.schema;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.expr.Functions;
import dev.leo.omnievents.rule.Problems;
import dev.leo.omnievents.rule.Rule;
import dev.leo.omnievents.rule.RuleLoader;
import dev.leo.omnievents.rule.RuleTester;
import dev.leo.omnievents.trigger.TriggerType;
import dev.leo.omnievents.trigger.Triggers;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 017: la documentación pública en inglés (docs/site/) no se queda atrás del código. */
class DocsSiteTest {
    static final Path SITE = Path.of("../../docs/site");
    static final Path PAGES = SITE.resolve("pages");
    static final Pattern YAML_BLOCK = Pattern.compile("```yaml\\n(.*?)```", Pattern.DOTALL);
    static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
    static final Set<String> RULE_KEYS = Set.of("trigger", "type", "menu", "template", "extends");

    static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<Path> pages() throws IOException {
        try (Stream<Path> s = Files.walk(PAGES)) {
            return s.filter(p -> p.toString().endsWith(".md")).sorted().toList();
        }
    }

    /** Un bloque es un conjunto de reglas si cada clave de primer nivel es un mapa con trigger, type, menu, template o extends. */
    static boolean isRuleSet(YamlConfiguration y) {
        Set<String> keys = y.getKeys(false);
        if (keys.isEmpty()) return false;
        int rules = 0;
        for (String k : keys) {
            ConfigurationSection s = y.getConfigurationSection(k);
            if (s != null && s.getKeys(false).stream().anyMatch(RULE_KEYS::contains)) rules++;
        }
        if (rules > 0 && rules < keys.size()) throw new AssertionError("bloque con reglas y otras claves mezcladas: " + keys);
        return rules > 0;
    }

    @Test
    void H1_CA3_los_ejemplos_de_reglas_cargan_y_sus_pruebas_pasan(@TempDir Path tmp) throws IOException, InvalidConfigurationException {
        int loaded = 0, tests = 0;
        StringBuilder errors = new StringBuilder();
        for (Path page : pages()) {
            Matcher m = YAML_BLOCK.matcher(read(page));
            int n = 0;
            while (m.find()) {
                n++;
                YamlConfiguration y = new YamlConfiguration();
                y.loadFromString(m.group(1));
                if (!isRuleSet(y)) continue;
                String where = PAGES.relativize(page) + " bloque " + n;
                Path dir = Files.createDirectories(tmp.resolve("b" + loaded));
                Files.writeString(dir.resolve("doc.yml"), m.group(1), StandardCharsets.UTF_8);
                Problems p = new Problems();
                Map<String, Rule> rules = new RuleLoader(new Actions(null), new Triggers()).loadFolder(dir.toFile(), p);
                p.all().forEach(x -> errors.append(where).append(": ").append(x).append('\n'));
                // Un menú se compila en varias reglas con su ruta (shop.menu, shop.menu.items.x.click); una plantilla no es una regla.
                for (String k : y.getKeys(false)) {
                    if (y.getBoolean(k + ".template")) continue;
                    String key = k.toLowerCase(Locale.ROOT);
                    if (rules.keySet().stream().noneMatch(r -> r.equals(key) || r.startsWith(key + ".")))
                        errors.append(where).append(": no cargó ").append(k).append(" (cargaron ").append(rules.keySet()).append(")\n");
                }
                RuleTester.Summary s = new RuleTester(r -> rules.get(r.toLowerCase(Locale.ROOT)), 1000, 16).runAll(rules.values());
                s.results().stream().filter(r -> !r.ok()).forEach(r -> errors.append(where).append(": ").append(r.rule().name())
                        .append(" «").append(r.test().name()).append("» ").append(r.failures()).append('\n'));
                tests += s.results().size();
                loaded++;
            }
        }
        assertEquals("", errors.toString());
        assertTrue(loaded >= 20 && tests >= 3, "se esperaban al menos 20 bloques de reglas y 3 pruebas: " + loaded + " y " + tests);
    }

    /** Las páginas .md enlazadas en SUMMARY.md, en orden. */
    static List<String> summary() {
        List<String> out = new ArrayList<>();
        Matcher m = LINK.matcher(read(PAGES.resolve("SUMMARY.md")));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** Las rutas del nav: de mkdocs.yml, en orden («- Título: ruta.md»). */
    static List<String> nav() {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("^\\s*- [^:]+:\\s*(\\S+\\.md)\\s*$", Pattern.MULTILINE).matcher(read(SITE.resolve("mkdocs.yml")));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    static String slug(String heading) {
        return heading.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} _-]", "").replace(' ', '-');
    }

    static Set<String> anchors(Path page) {
        return read(page).lines().filter(l -> l.matches("#{1,6} .*")).map(l -> slug(l.replaceFirst("#+ ", ""))).collect(Collectors.toSet());
    }

    @Test
    void H2_CA3_los_indices_coinciden_y_los_enlaces_existen() throws IOException {
        List<String> summary = summary();
        assertEquals(summary, nav(), "SUMMARY.md y el nav de mkdocs.yml deben listar las mismas páginas en el mismo orden");
        List<String> missing = new ArrayList<>();
        for (Path page : pages()) {
            String rel = PAGES.relativize(page).toString().replace('\\', '/');
            if (!rel.equals("SUMMARY.md") && !summary.contains(rel)) missing.add("sin índice: " + rel);
            Matcher m = LINK.matcher(read(page));
            while (m.find()) {
                String target = m.group(1);
                if (target.matches("[a-z]+:.*")) continue;
                String file = target.contains("#") ? target.substring(0, target.indexOf('#')) : target;
                String anchor = target.contains("#") ? target.substring(target.indexOf('#') + 1) : null;
                Path to = file.isEmpty() ? page : page.getParent().resolve(file).normalize();
                if (!Files.isRegularFile(to)) missing.add(rel + " → " + target + " (no existe)");
                else if (anchor != null && !anchors(to).contains(anchor)) missing.add(rel + " → " + target + " (no hay ese título)");
            }
        }
        assertEquals(List.of(), missing);
    }

    /** Los nombres en código de la primera columna de las tablas cuyo encabezado empieza por «| header |». */
    static Set<String> tableNames(Path page, String header) {
        Set<String> out = new TreeSet<>();
        boolean in = false;
        for (String l : read(page).lines().toList()) {
            if (!l.startsWith("|")) {
                in = false;
                continue;
            }
            String first = l.substring(1, Math.max(1, l.indexOf('|', 1))).trim();
            if (first.equals(header)) in = true;
            else if (in) {
                Matcher m = Pattern.compile("`([a-z_]+)[`(]").matcher(first);
                while (m.find()) out.add(m.group(1));
            }
        }
        return out;
    }

    static void sameNames(String what, Set<String> registry, Set<String> page) {
        Set<String> missing = new TreeSet<>(registry);
        missing.removeAll(page);
        Set<String> extra = new TreeSet<>(page);
        extra.removeAll(registry);
        assertEquals("", (missing.isEmpty() ? "" : what + " sin documentar en inglés: " + missing + "\n")
                + (extra.isEmpty() ? "" : what + " documentados que el registro no tiene: " + extra));
    }

    @Test
    void H3_CA1_disparadores_acciones_y_funciones_estan_todos() {
        Path om = PAGES.resolve("omnievents");
        sameNames("disparadores", new Triggers().all().stream().map(TriggerType::name).collect(Collectors.toCollection(TreeSet::new)),
                tableNames(om.resolve("triggers.md"), "Trigger"));
        sameNames("acciones", new TreeSet<>(new Actions(null).names()), tableNames(om.resolve("actions.md"), "Action"));
        sameNames("funciones", Functions.all().stream().map(Functions.Def::name).collect(Collectors.toCollection(TreeSet::new)),
                tableNames(om.resolve("conditions.md"), "Function"));
    }
}

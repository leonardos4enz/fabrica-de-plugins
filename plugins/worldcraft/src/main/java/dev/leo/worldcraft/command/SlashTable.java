package dev.leo.worldcraft.command;

import dev.leo.worldcraft.config.Issues;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Spec 039: los nombres de WorldEdit para Worldcraft (slash-commands.yml del jar) y «//x» para cada subcomando de /wc.
 * Las claves son como se escriben sin la primera barra («/set» es //set, «brush» es /brush, «/» es //).
 */
public final class SlashTable {
    /** Los de administración quedan solo en /wc. */
    public static final Set<String> NOT_SLASHED = Set.of("reload", "validate", "selftest");
    static final String FILE = "slash-commands.yml";
    private static final List<String> KEYS = List.of("to", "aliases", "flags", "defaults");
    private static final Pattern SHORT_FLAGS = Pattern.compile("-[A-Za-z]+");

    /**
     * @param to       el comando de /wc con sus primeras palabras («sel», «clear»)
     * @param defaults las opciones de fábrica de WorldEdit (sin «--»)
     * @param flags    letra → opción («entities»), «!opción» (quita una de fábrica), «/comando» (cambia de comando) o «»
     */
    public record Entry(String label, List<String> to, List<String> defaults, Map<Character, String> flags) {
        public String sub() {
            return to.get(0);
        }
    }

    private final Map<String, Entry> entries;

    private SlashTable(Map<String, Entry> entries) {
        this.entries = entries;
    }

    private static SlashTable builtin;

    /** La tabla del jar (una vez). */
    public static synchronized SlashTable builtin() {
        if (builtin == null) {
            Issues issues = new Issues();
            String text;
            try (InputStream in = SlashTable.class.getResourceAsStream("/" + FILE)) {
                text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                text = "";
            }
            builtin = load(text, WorldcraftCommand.subcommands(), issues);
        }
        return builtin;
    }

    public static SlashTable load(String yaml, Collection<String> subcommands, Issues issues) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            issues.error(FILE, "", "could not be read: " + e.getMessage());
        }
        Map<String, Entry> out = new LinkedHashMap<>();
        // Primero lo generado: //x para cada subcomando; la tabla manda sobre esto.
        for (String sub : subcommands) {
            if (!NOT_SLASHED.contains(sub)) out.put("/" + sub, new Entry("/" + sub, List.of(sub), List.of(), Map.of()));
        }
        ConfigurationSection c = y.getConfigurationSection("commands");
        if (c == null) return new SlashTable(out);
        for (String label : c.getKeys(false)) {
            String path = "commands." + label;
            ConfigurationSection e = c.getConfigurationSection(label);
            if (e == null) {
                issues.error(FILE, path, "is not a section: skipped");
                continue;
            }
            for (String k : e.getKeys(false)) if (!KEYS.contains(k)) issues.warn(FILE, path + "." + k, "unknown setting" + Issues.hint(k, KEYS));
            String toText = e.getString("to", label.replace("/", "")).trim().toLowerCase(Locale.ROOT);
            List<String> to = List.of(toText.split("\\s+"));
            if (!subcommands.contains(to.get(0))) {
                issues.error(FILE, path + ".to", "'" + to.get(0) + "' is not a Worldcraft command" + Issues.hint(to.get(0), subcommands) + ": skipped");
                continue;
            }
            Map<Character, String> flags = new LinkedHashMap<>();
            ConfigurationSection f = e.getConfigurationSection("flags");
            if (f != null) {
                for (String letter : f.getKeys(false)) {
                    if (letter.length() != 1 || !Character.isLetter(letter.charAt(0))) {
                        issues.error(FILE, path + ".flags." + letter, "a flag is one letter: skipped");
                        continue;
                    }
                    flags.put(letter.charAt(0), String.valueOf(f.get(letter, "")).trim().toLowerCase(Locale.ROOT));
                }
            }
            List<String> defaults = e.getStringList("defaults").stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).toList();
            Entry entry = new Entry(label, to, defaults, Map.copyOf(flags));
            out.put(label, entry);
            for (String alias : e.getStringList("aliases")) out.put(alias, new Entry(alias, to, defaults, entry.flags()));
        }
        // Spec 040 F-01: en WorldEdit /x y //x son el mismo comando; el //x que se arma solo toma las banderas de /x
        // (//searchitem -b), salvo que la tabla diga otra cosa para //x.
        for (String label : c.getKeys(false)) {
            if (label.startsWith("/") || c.contains("/" + label)) continue;
            Entry one = out.get(label), two = out.get("/" + label);
            if (one == null || two == null || !two.to().equals(List.of(label))) continue;
            out.put("/" + label, new Entry("/" + label, one.to(), one.defaults(), one.flags()));
        }
        return new SlashTable(out);
    }

    /** Nombre (sin la primera barra) → entrada, en el orden de la tabla. */
    public Map<String, Entry> entries() {
        return entries;
    }

    public Entry get(String label) {
        return entries.get(label);
    }

    /**
     * Los argumentos de /wc: las palabras de «to»; un «-letras» cuyas letras son todas banderas de la entrada se cambia
     * por sus opciones en el mismo lugar («-m &lt;máscara&gt;» queda «--mask &lt;máscara&gt;»); lo demás pasa igual (-5,
     * -x&gt;0). Al final, las de fábrica que ninguna bandera quitó y que no se escribieron.
     */
    public static String[] translate(Entry e, String[] args) {
        List<String> head = e.to();
        List<String> rest = new ArrayList<>();
        Set<String> removed = new HashSet<>(), written = new HashSet<>();
        for (String t : args) {
            if (isFlags(e, t)) {
                for (char ch : t.substring(1).toCharArray()) {
                    String v = e.flags().get(ch);
                    if (v.isEmpty()) continue;
                    if (v.startsWith("!")) removed.add(v.substring(1));
                    else if (v.startsWith("/")) head = Arrays.asList(v.substring(1).split("\\s+"));
                    else {
                        rest.add("--" + v);
                        written.add(v);
                    }
                }
                continue;
            }
            rest.add(t);
            if (t.startsWith("--")) written.add(t.substring(2).toLowerCase(Locale.ROOT));
        }
        for (String d : e.defaults()) if (!removed.contains(d) && !written.contains(d)) rest.add("--" + d);
        List<String> out = new ArrayList<>(head);
        out.addAll(rest);
        return out.toArray(String[]::new);
    }

    /** Para el autocompletado: las palabras de «to» y lo escrito tal cual (la última palabra es la que se completa). */
    public static String[] complete(Entry e, String[] args) {
        List<String> out = new ArrayList<>(e.to());
        out.addAll(Arrays.asList(args));
        return out.toArray(String[]::new);
    }

    private static boolean isFlags(Entry e, String t) {
        if (e.flags().isEmpty() || !SHORT_FLAGS.matcher(t).matches()) return false;
        for (char ch : t.substring(1).toCharArray()) if (!e.flags().containsKey(ch)) return false;
        return true;
    }
}

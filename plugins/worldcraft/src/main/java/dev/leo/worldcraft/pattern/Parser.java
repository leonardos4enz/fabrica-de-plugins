package dev.leo.blockwright.pattern;

import dev.leo.blockwright.block.BlockRegistry;
import dev.leo.blockwright.config.Issues;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Lee patrones y máscaras como los escribe una persona (H2, plan 025 §1.3). Todo error se da antes de tocar el mundo y
 * dice qué parte está mal, con «did you mean» (H2 CA3).
 *
 * <pre>
 * patrón:  stone | oak_stairs[facing=north] | 70% stone, 30% andesite | $preset
 * máscara: stone, dirt | *_wool, !red_wool | #logs | oak_log[axis=y] | !air | $preset
 * </pre>
 */
public final class Parser {
    private static final java.util.regex.Pattern WEIGHT = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*%\\s*(.+)$");
    private static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("^(?:[a-z0-9_.-]+:)?[a-z0-9_./*-]+$");

    private final BlockRegistry registry;
    private final Map<String, String> patternPresets, maskPresets;
    private final Set<String> blockNames = new HashSet<>();

    public Parser(BlockRegistry registry, Map<String, String> patternPresets, Map<String, String> maskPresets) {
        this.registry = registry;
        this.patternPresets = patternPresets;
        this.maskPresets = maskPresets;
        for (String b : registry.blocks()) blockNames.add(b);
    }

    public Pattern pattern(String input) throws ParseException {
        String text = input.trim();
        if (text.isEmpty()) throw new ParseException("Missing pattern: write a block, like 'stone', or a mix, like '70% stone, 30% andesite'");
        if (text.startsWith("$")) {
            Pattern p = pattern(preset(text, patternPresets, "pattern"));
            return new Pattern(p.states(), p.weights(), text);
        }
        List<String> parts = split(text);
        List<String> states = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        int weighted = 0;
        for (String raw : parts) {
            String p = raw.trim();
            if (p.isEmpty()) throw new ParseException("Empty entry in pattern '" + text + "' (two commas together?)");
            Matcher m = WEIGHT.matcher(p);
            double w = 1;
            if (m.matches()) {
                w = Double.parseDouble(m.group(1));
                if (w <= 0) throw new ParseException("'" + p + "': the weight must be more than 0%");
                p = m.group(2).trim();
                weighted++;
            }
            if (p.startsWith("#")) throw new ParseException("'" + p + "' is a tag: tags work in masks (what to replace), a pattern needs blocks");
            if (p.startsWith("!")) throw new ParseException("'" + p + "': '!' works in masks (what to replace), not in a pattern");
            if (p.contains("*")) throw new ParseException("'" + p + "': '*' works in masks (what to replace), a pattern needs exact blocks");
            states.add(state(p));
            weights.add(w);
        }
        if (weighted != 0 && weighted != parts.size()) {
            throw new ParseException("Give every block in '" + text + "' a weight (like '70% stone, 30% andesite') or none");
        }
        return new Pattern(states, weights, text);
    }

    public Mask mask(String input) throws ParseException {
        String text = input.trim();
        if (text.isEmpty()) throw new ParseException("Missing mask: write the blocks to change, like 'dirt, grass_block' or '!air'");
        if (text.startsWith("$")) return new Mask(mask(preset(text, maskPresets, "mask")).terms(), text);
        if (text.equals("*")) return new Mask(List.of(), text);
        List<Mask.Term> terms = new ArrayList<>();
        for (String raw : split(text)) {
            String p = raw.trim();
            if (p.isEmpty()) throw new ParseException("Empty entry in mask '" + text + "' (two commas together?)");
            boolean neg = p.startsWith("!");
            if (neg) p = p.substring(1).trim();
            else if (p.toLowerCase(Locale.ROOT).startsWith("not ")) {
                neg = true;
                p = p.substring(4).trim();
            }
            if (p.matches("^\\d.*%.*")) throw new ParseException("'" + raw.trim() + "': weights (%) work in patterns, not in masks");
            terms.add(term(p, neg));
        }
        return new Mask(terms, text);
    }

    private String preset(String text, Map<String, String> presets, String kind) throws ParseException {
        String name = text.substring(1).trim().toLowerCase(Locale.ROOT);
        String v = presets.get(name);
        if (v == null) {
            throw new ParseException("There is no " + kind + " preset '" + name + "' in presets.yml" + Issues.hint(name, presets.keySet()));
        }
        if (v.trim().startsWith("$")) throw new ParseException("The " + kind + " preset '" + name + "' points to another preset: write the blocks instead");
        return v;
    }

    private Mask.Term term(String p, boolean neg) throws ParseException {
        String lower = p.toLowerCase(Locale.ROOT);
        String props = "";
        int b = lower.indexOf('[');
        if (b >= 0) {
            if (!lower.endsWith("]")) throw new ParseException("'" + p + "': missing ']' at the end of the block state");
            props = lower.substring(b);
            lower = lower.substring(0, b).trim();
        }
        Map<String, String> wanted = dev.leo.blockwright.block.StateTable.parseProperties("x" + props);
        if (lower.startsWith("#")) {
            String tag = lower.substring(1);
            Set<String> ids = registry.tag(tag);
            if (ids == null) throw new ParseException("Unknown block tag '#" + tag + "'" + Issues.hint(withNs(tag), registry.tags()));
            return new Mask.Term(Set.copyOf(ids), wanted, neg);
        }
        if (lower.equals("*")) return new Mask.Term(null, wanted, neg);
        if (!ID.matcher(lower).matches()) throw new ParseException("'" + p + "' is not a block name");
        String id = withNs(lower);
        if (lower.equals("air") || id.equals("minecraft:air")) return new Mask.Term(BlockRegistry.AIR, wanted, neg);
        if (id.contains("*")) {
            java.util.regex.Pattern glob = java.util.regex.Pattern.compile(
                    java.util.regex.Pattern.quote(id).replace("*", "\\E.*\\Q"));
            Set<String> ids = new LinkedHashSet<>();
            for (String bl : blockNames) if (glob.matcher(bl).matches()) ids.add(bl);
            if (ids.isEmpty()) throw new ParseException("'" + p + "' matches no block");
            return new Mask.Term(Set.copyOf(ids), wanted, neg);
        }
        if (!blockNames.contains(id)) throw new ParseException(unknownBlock(lower));
        if (!wanted.isEmpty()) {
            try {
                registry.canonical(id + props); // solo para validar las propiedades
            } catch (IllegalArgumentException e) {
                throw new ParseException("'" + p + "': " + e.getMessage());
            }
        }
        return new Mask.Term(Set.of(id), wanted, neg);
    }

    private String state(String p) throws ParseException {
        String lower = p.toLowerCase(Locale.ROOT).trim();
        int b = lower.indexOf('[');
        String name = b >= 0 ? lower.substring(0, b).trim() : lower;
        if (!ID.matcher(name).matches()) throw new ParseException("'" + p + "' is not a block name");
        String id = withNs(name);
        if (!blockNames.contains(id)) throw new ParseException(unknownBlock(name));
        try {
            return registry.canonical(id + (b >= 0 ? lower.substring(b) : ""));
        } catch (IllegalArgumentException e) {
            throw new ParseException("'" + p + "': " + e.getMessage());
        }
    }

    private String unknownBlock(String name) {
        String hint = Issues.closest(withNs(name), blockNames);
        return "Unknown block '" + name + "'" + (hint == null ? "" : " (did you mean '" + hint.replace("minecraft:", "") + "'?)");
    }

    static String withNs(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    /** Separa por comas fuera de los corchetes de un estado («a[x=1,y=2], b»). */
    static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth = Math.max(0, depth - 1);
            else if (c == ',' && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }
}

package dev.leo.worldcraft.pattern;

import dev.leo.worldcraft.block.BlockRegistry;
import dev.leo.worldcraft.config.Issues;

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
    /** Spec 033: los biomas del servidor («minecraft:plains»), para «$desert». */
    private final Set<String> biomeNames = new HashSet<>();
    /** Spec 034: los topes de las fórmulas de config.yml (formulas.*). */
    private dev.leo.worldcraft.formula.Formula.Limits formulaLimits = dev.leo.worldcraft.formula.Formula.Limits.DEFAULTS;

    public Parser(BlockRegistry registry, Map<String, String> patternPresets, Map<String, String> maskPresets) {
        this.registry = registry;
        this.patternPresets = patternPresets;
        this.maskPresets = maskPresets;
        for (String b : registry.blocks()) blockNames.add(b);
        biomeNames.addAll(registry.biomes());
    }

    /** Spec 033: el id completo de un bioma escrito por alguien («desert» → «minecraft:desert»), o null si no existe. */
    public String biome(String name) {
        String id = withNs(name.trim().toLowerCase(Locale.ROOT));
        return biomeNames.contains(id) ? id : null;
    }

    /** Spec 033: «did you mean» entre los biomas. */
    public String biomeHint(String name) {
        String hint = Issues.closest(withNs(name.trim().toLowerCase(Locale.ROOT)), biomeNames);
        return hint == null ? "" : " (did you mean '" + hint.replace("minecraft:", "") + "'?)";
    }

    /** Spec 034: los topes de las fórmulas (vueltas, largo, megabuf). */
    public Parser formulaLimits(dev.leo.worldcraft.formula.Formula.Limits limits) {
        this.formulaLimits = limits;
        return this;
    }

    public dev.leo.worldcraft.formula.Formula.Limits formulaLimits() {
        return formulaLimits;
    }

    /** Spec 034: una fórmula con esas entradas («x», «y», «z»), con los topes de config.yml. */
    public dev.leo.worldcraft.formula.Formula formula(String text, List<String> inputs) throws ParseException {
        try {
            return dev.leo.worldcraft.formula.Formula.parse(text, inputs, formulaLimits);
        } catch (dev.leo.worldcraft.formula.FormulaException e) {
            throw new ParseException(e.getMessage());
        }
    }

    private static final List<String> XYZ = List.of("x", "y", "z");

    public Pattern pattern(String input) throws ParseException {
        String text = input.trim();
        if (text.isEmpty()) throw new ParseException("Missing pattern: write a block, like 'stone', or a mix, like '70% stone, 30% andesite'");
        if (text.startsWith("$")) {
            Pattern p = pattern(preset(text, patternPresets, "pattern"));
            return new Pattern(p.states(), p.weights(), text, p.changes(), p.pick(), p.also());
        }
        if (text.startsWith("=")) return formulaPattern(text);
        List<String> parts = split(text);
        List<String> states = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        List<java.util.function.UnaryOperator<String>> changes = new ArrayList<>();
        int weighted = 0;
        boolean also = false;
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
            if (p.startsWith("#")) {
                colorEntry(p, states, changes);
                if (states.get(states.size() - 1) == null) also = true;
            } else if (p.startsWith("!")) {
                throw new ParseException("'" + p + "': '!' works in masks (what to replace), not in a pattern");
            } else if (p.contains("*")) {
                throw new ParseException("'" + p + "': '*' works in masks (what to replace), a pattern needs exact blocks");
            } else if (p.startsWith("^")) {
                change(p, states, changes);
            } else {
                states.add(state(p));
                changes.add(null);
            }
            weights.add(w);
        }
        if (weighted != 0 && weighted != parts.size()) {
            throw new ParseException("Give every block in '" + text + "' a weight (like '70% stone, 30% andesite') or none");
        }
        return new Pattern(states, weights, text, changes, null, also ? colorStates() : List.of());
    }

    // ------------------------------------------------------------------ spec 035: patrones por color

    /** Los patrones por color de FAWE (H1): #color[c] es un bloque; los demás cambian el bloque que ya está. */
    public static final List<String> COLOR_WORDS = List.of("color", "darken", "lighten", "saturate", "desaturate", "averagecolor");

    private dev.leo.worldcraft.color.BlockColors colors;
    /** El estado canónico de cada bloque de la tabla (los que puede poner un cambio por color). */
    private Map<String, String> colorCanonical;

    /** Spec 035: la tabla de colores (config.yml, sección colors); sin ella, la del jar con los bloques del servidor. */
    public Parser colors(dev.leo.worldcraft.color.BlockColors colors) {
        this.colors = colors;
        this.colorCanonical = null;
        return this;
    }

    public dev.leo.worldcraft.color.BlockColors colors() {
        if (colors == null) {
            Map<String, Integer> t = new java.util.LinkedHashMap<>(dev.leo.worldcraft.color.BlockColors.builtin());
            t.keySet().retainAll(blockNames);
            colors = new dev.leo.worldcraft.color.BlockColors(t);
        }
        return colors;
    }

    private Map<String, String> colorCanonical() {
        if (colorCanonical == null) {
            Map<String, String> m = new java.util.LinkedHashMap<>();
            for (String id : colors().ids()) {
                String c = canonicalOrNull(id, Map.of());
                if (c != null) m.put(id, c);
            }
            colorCanonical = m;
        }
        return colorCanonical;
    }

    private List<String> colorStates() {
        return List.copyOf(colorCanonical().values());
    }

    /** Spec 035 (imagen): el estado canónico de un bloque de la tabla («minecraft:oak_log» → «…[axis=y]»), o null. */
    public String colorState(String id) {
        return colorCanonical().get(id);
    }

    private void colorEntry(String p, List<String> states, List<java.util.function.UnaryOperator<String>> changes) throws ParseException {
        String body = p.substring(1).trim().toLowerCase(Locale.ROOT);
        String word = body, arg = null;
        int open = body.indexOf('[');
        if (open >= 0) {
            if (!body.endsWith("]")) throw new ParseException("'" + p + "': missing ']' at the end");
            word = body.substring(0, open).trim();
            arg = body.substring(open + 1, body.length() - 1).trim();
        }
        if (!COLOR_WORDS.contains(word)) {
            String hint = Issues.closest(word, COLOR_WORDS);
            if (hint != null) throw new ParseException("Unknown color pattern '#" + word + "' (did you mean '#" + hint + "'?)");
            throw new ParseException("'" + p + "' is a tag: tags work in masks (what to replace); a pattern needs blocks, or a color like '#color[red]' or '#darken'");
        }
        dev.leo.worldcraft.color.BlockColors table = colors();
        Map<String, String> canon = colorCanonical();
        boolean needsArg = !word.equals("darken") && !word.equals("lighten") && !word.equals("desaturate");
        if (needsArg && (arg == null || arg.isEmpty())) {
            throw new ParseException("'#" + word + "' needs a color, like '#" + word + "[red]' or '#" + word + "[#3a7bd5]'");
        }
        if (!needsArg && arg != null && !word.equals("desaturate")) throw new ParseException("'#" + word + "' takes nothing in [ ]");
        int color = 0;
        double percent = 100;
        try {
            if (word.equals("desaturate")) {
                if (arg != null && !arg.isEmpty()) {
                    percent = Double.parseDouble(arg.endsWith("%") ? arg.substring(0, arg.length() - 1) : arg);
                    if (percent < 0 || percent > 100) throw new NumberFormatException();
                }
            } else if (arg != null) {
                color = dev.leo.worldcraft.color.BlockColors.parse(arg);
            }
        } catch (NumberFormatException e) {
            throw new ParseException("'" + p + "': the percent goes from 0 to 100, like '#desaturate[50]'");
        } catch (IllegalArgumentException e) {
            throw new ParseException(e.getMessage());
        }
        if (word.equals("color")) {
            String id = canon.get(table.nearestId(color));
            if (id == null) throw new ParseException("'" + p + "': no block of the color table can be placed");
            states.add(id);
            changes.add(null);
            return;
        }
        final int c = color;
        final double pct = percent;
        final String w = word;
        states.add(null);
        changes.add(current -> {
            String mat = material(current);
            int rgb = table.colorOf(mat);
            if (rgb < 0) return null; // no está en la tabla: se deja
            String id = switch (w) {
                case "darken" -> table.darker(mat);
                case "lighten" -> table.lighter(mat);
                case "saturate" -> table.nearestId(dev.leo.worldcraft.color.BlockColors.multiply(rgb, c));
                case "averagecolor" -> table.nearestId(dev.leo.worldcraft.color.BlockColors.average(rgb, c));
                default -> table.nearestId(dev.leo.worldcraft.color.BlockColors.desaturate(rgb, pct));
            };
            return id == null || id.equals(mat) ? null : canon.get(id);
        });
    }

    /**
     * Spec 034 H5 CA2: «=fórmula -> a, b, c»: el número de la fórmula (x, y, z del mundo) elige el bloque de la lista
     * (0 el primero; acotado a la lista).
     */
    private Pattern formulaPattern(String text) throws ParseException {
        int arrow = topLevel(text, "->");
        if (arrow < 0) {
            throw new ParseException("'" + text + "': after the formula write '->' and the blocks it picks from, like '=(x+z)%2 -> white_wool, black_wool'");
        }
        String body = text.substring(1, arrow).trim(), list = text.substring(arrow + 2).trim();
        if (body.isEmpty()) throw new ParseException("'" + text + "': write a formula after '=', like '=(x+z)%2 -> white_wool, black_wool'");
        if (list.isEmpty()) throw new ParseException("'" + text + "': write the blocks after '->', like 'white_wool, black_wool'");
        dev.leo.worldcraft.formula.Formula f = formula(body, XYZ);
        List<String> states = new ArrayList<>();
        for (String raw : split(list)) {
            String p = raw.trim();
            if (p.isEmpty()) throw new ParseException("Empty entry in pattern '" + text + "' (two commas together?)");
            if (WEIGHT.matcher(p).matches()) throw new ParseException("'" + p + "': with a formula the blocks have no weights (the formula picks one)");
            if (p.startsWith("^") || p.startsWith("#") || p.startsWith("!") || p.contains("*") || p.startsWith("$") || p.startsWith("=")) {
                throw new ParseException("'" + p + "': after '->' write blocks, like 'stone' or 'oak_stairs[facing=north]'");
            }
            states.add(state(p));
        }
        return new Pattern(states, java.util.Collections.nCopies(states.size(), 1.0), text,
                java.util.Collections.nCopies(states.size(), null), f);
    }

    /** Dónde está sep fuera de paréntesis y corchetes, o -1. */
    static int topLevel(String s, String sep) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth = Math.max(0, depth - 1);
            else if (depth == 0 && s.startsWith(sep, i)) return i;
        }
        return -1;
    }

    /**
     * Spec 029 (H3): «^stone_stairs» cambia el tipo y conserva las propiedades que el tipo nuevo acepta; «^[waterlogged=false]»
     * cambia solo esas propiedades. Se valida al leer; el cambio se calcula sobre cada bloque al ponerlo.
     */
    private void change(String p, List<String> states, List<java.util.function.UnaryOperator<String>> changes) throws ParseException {
        String body = p.substring(1).trim().toLowerCase(Locale.ROOT);
        if (body.isEmpty()) throw new ParseException("'^' needs a block (like '^stone_stairs') or a state (like '^[waterlogged=false]')");
        if (body.startsWith("[")) {
            if (!body.endsWith("]")) throw new ParseException("'" + p + "': missing ']' at the end of the block state");
            Map<String, String> wanted = dev.leo.worldcraft.block.StateTable.parseProperties("x" + body);
            if (wanted.isEmpty()) throw new ParseException("'" + p + "': write the state to change, like '^[waterlogged=false]'");
            states.add(null);
            changes.add(current -> {
                Map<String, String> props = new java.util.LinkedHashMap<>(dev.leo.worldcraft.block.StateTable.parseProperties(current));
                for (String k : wanted.keySet()) if (!props.containsKey(k)) return null; // el bloque no tiene esa propiedad
                props.putAll(wanted);
                return canonicalOrNull(material(current), props);
            });
            return;
        }
        if (body.contains("[")) throw new ParseException("'" + p + "': write either '^block' (keeps the state) or '^[state]' (keeps the block)");
        if (!ID.matcher(body).matches()) throw new ParseException("'" + p + "' is not a block name");
        String id = withNs(body);
        if (!blockNames.contains(id)) throw new ParseException(unknownBlock(body));
        String def = registry.canonical(id);
        Map<String, String> accepted = dev.leo.worldcraft.block.StateTable.parseProperties(def);
        states.add(def);
        changes.add(current -> {
            Map<String, String> props = new java.util.LinkedHashMap<>(accepted);
            for (Map.Entry<String, String> e : dev.leo.worldcraft.block.StateTable.parseProperties(current).entrySet()) {
                if (!accepted.containsKey(e.getKey())) continue;
                Map<String, String> one = new java.util.LinkedHashMap<>(props);
                one.put(e.getKey(), e.getValue());
                if (canonicalOrNull(id, one) != null) props = one; // un valor que el tipo nuevo no tiene se queda de fábrica
            }
            return canonicalOrNull(id, props);
        });
    }

    private String canonicalOrNull(String id, Map<String, String> props) {
        try {
            return registry.canonical(dev.leo.worldcraft.block.StateTable.withProperties(id, props));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String material(String state) {
        int b = state.indexOf('[');
        return b < 0 ? state : state.substring(0, b);
    }

    /** Las palabras de máscara de la spec 029 (para el autocompletado y «did you mean»). */
    public static final List<String> MASK_WORDS = List.of("above", "below", "exposed", "surface", "solid", "chance", "and", "not");

    /** Las comandos donde una máscara puede mirar alrededor (para el mensaje de error). */
    static final String NEAR_COMMANDS = "replace, replacenear, removenear, count, trim and copy/cut --mask";

    /**
     * Una máscara de bloques: solo el estado de cada bloque (terreno, pinceles, presets). Spec 029: lo que mira alrededor
     * o al azar se rechaza aquí con dónde sí se puede usar.
     */
    public Mask mask(String input) throws ParseException {
        Mask m = maskNear(input);
        if (m.positional()) {
            throw new ParseException("'" + input.trim() + "' looks at the blocks around, the biome, a formula or picks at random: that works in "
                    + NEAR_COMMANDS + ", not here");
        }
        return m;
    }

    /**
     * Spec 029 (H2): una máscara que además puede mirar la posición: «stone above grass_block», «exposed and !leaves»,
     * «30% chance». La coma es «o», «and» es «y», «!»/«not» niega el término entero.
     */
    public Mask maskNear(String input) throws ParseException {
        String text = input.trim();
        if (text.isEmpty()) throw new ParseException("Missing mask: write the blocks to change, like 'dirt, grass_block' or '!air'");
        // Spec 033: «$nombre» solo es un preset si existe; si no, es un bioma («$desert, $badlands», «$desert and sand»).
        if (text.startsWith("$") && maskPresets.containsKey(text.substring(1).trim().toLowerCase(Locale.ROOT))) {
            return new Mask(maskNear(preset(text, maskPresets, "mask")).terms(), text);
        }
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
            terms.add(combined(p, neg, raw.trim()));
        }
        return new Mask(terms, text);
    }

    private static final java.util.regex.Pattern AND = java.util.regex.Pattern.compile("(?:^|\\s+)and(?:\\s+|$)",java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern NEAR = java.util.regex.Pattern.compile("^(.*?)\\s*\\b(above|below)\\s+(.+)$", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern CHANCE = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*%\\s*chance$", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Un término con sus partes unidas por «and» (spec 029): los bloques se cruzan, lo de la posición se suma. */
    private Mask.Term combined(String p, boolean neg, String raw) throws ParseException {
        List<String> parts = new ArrayList<>();
        for (String a : AND.split(p, -1)) {
            String t = a.trim();
            if (t.isEmpty()) throw new ParseException("'" + raw + "': 'and' needs something on both sides");
            Matcher m = NEAR.matcher(t);
            if (m.matches() && !t.startsWith("^")) {
                String left = m.group(1).trim(), word = m.group(2).toLowerCase(Locale.ROOT) + " " + m.group(3).trim();
                // F-01: «!above grass_block» niega el above, no es una parte vacía.
                if (left.equals("!") || left.equalsIgnoreCase("not")) {
                    parts.add("!" + word);
                    continue;
                }
                if (!left.isBlank()) parts.add(left);
                parts.add(word);
            } else {
                parts.add(t);
            }
        }
        Set<String> materials = null;
        Map<String, String> props = new java.util.LinkedHashMap<>();
        List<Mask.Near> near = new ArrayList<>();
        List<Mask.Term> not = new ArrayList<>();
        for (String whole : parts) {
            // F-01: una parte también se niega («stone and !surface», «#logs and not birch_log»).
            String part = whole;
            boolean partNeg = false;
            if (part.startsWith("!")) {
                partNeg = true;
                part = part.substring(1).trim();
            } else if (part.toLowerCase(Locale.ROOT).startsWith("not ")) {
                partNeg = true;
                part = part.substring(4).trim();
            }
            String lower = part.toLowerCase(Locale.ROOT);
            // F-02: «stone above» sin nada después.
            for (String w : List.of("above", "below")) {
                if (lower.equals(w) || lower.endsWith(" " + w)) {
                    throw new ParseException("'" + raw + "': after '" + w + "' write blocks, like '" + w + " grass_block' or '" + w + " #logs'");
                }
            }
            Mask.Term t = partTerm(part, lower, raw, near);
            if (partNeg) {
                if (t == null) near.set(near.size() - 1, new Mask.Near.Not(near.get(near.size() - 1)));
                else not.add(t);
                continue;
            }
            if (t == null) continue;
            if (t.materials() != null) {
                if (materials == null) materials = new java.util.HashSet<>(t.materials());
                else materials.retainAll(t.materials());
            }
            props.putAll(t.props());
        }
        if (materials != null && materials.isEmpty()) throw new ParseException("'" + raw + "' matches no block (the parts joined with 'and' have nothing in common)");
        return new Mask.Term(materials == null ? null : Set.copyOf(materials), props, neg, near, not);
    }

    /**
     * Una parte de un término: si mira la posición, la agrega a near y devuelve null; si es de estado, la devuelve como
     * término (sin negar).
     */
    private Mask.Term partTerm(String part, String lower, String raw, List<Mask.Near> near) throws ParseException {
        {
            if (lower.startsWith("=")) {
                // Spec 034 H5 CA1: «=y<64», con x, y, z del mundo.
                String body = part.trim().substring(1).trim();
                if (body.isEmpty()) throw new ParseException("'" + raw + "': write a formula after '=', like '=y<64'");
                near.add(new Mask.Near.Formula(formula(body, XYZ)));
                return null;
            }
            Matcher ch = CHANCE.matcher(lower);
            if (ch.matches()) {
                double v = Double.parseDouble(ch.group(1));
                if (v <= 0 || v > 100) throw new ParseException("'" + part + "': the chance must be more than 0% and at most 100%");
                near.add(new Mask.Near.Chance(v / 100.0));
                return null;
            }
            if (lower.matches("^\\d.*%.*")) {
                throw new ParseException("'" + raw + "': weights (%) work in patterns; in a mask write a chance, like '30% chance'");
            }
            if (lower.startsWith("$")) {
                String name = lower.substring(1).trim();
                String id = biome(name);
                if (id == null) {
                    List<String> known = new ArrayList<>(maskPresets.keySet());
                    for (String b : biomeNames) known.add(b.replace("minecraft:", ""));
                    throw new ParseException("'" + part + "' is not a mask preset in presets.yml nor a biome" + Issues.hint(name, known));
                }
                near.add(new Mask.Near.Biome(Set.of(id)));
                return null;
            }
            if (lower.equals("exposed")) {
                near.add(new Mask.Near.Exposed());
                return null;
            }
            if (lower.equals("surface")) {
                near.add(new Mask.Near.Surface());
                return null;
            }
            if (lower.startsWith("above ") || lower.startsWith("below ")) {
                String inner = part.substring(6).trim();
                Mask of = maskNear(inner);
                if (of.positional() || of.terms().size() != 1 || of.terms().get(0).negated()) {
                    throw new ParseException("'" + part + "': after '" + lower.substring(0, 5) + "' write blocks, like '"
                            + lower.substring(0, 5) + " grass_block' or '" + lower.substring(0, 5) + " #logs'");
                }
                near.add(new Mask.Near.Neighbor(lower.startsWith("above") ? -1 : 1, of));
                return null;
            }
            return stateTerm(part);
        }
    }

    /** Solo el estado: un bloque, tag, glob, «*», «solid» o «^[estado]» (cualquier bloque con ese estado, spec 029). */
    private Mask.Term stateTerm(String p) throws ParseException {
        String lower = p.toLowerCase(Locale.ROOT).trim();
        if (lower.equals("solid")) {
            Set<String> ids = new java.util.HashSet<>();
            for (String b : blockNames) if (registry.solid(b)) ids.add(b);
            return new Mask.Term(Set.copyOf(ids), Map.of(), false);
        }
        if (lower.startsWith("^[")) {
            if (!lower.endsWith("]")) throw new ParseException("'" + p + "': missing ']' at the end of the block state");
            Map<String, String> wanted = dev.leo.worldcraft.block.StateTable.parseProperties("x" + lower.substring(1));
            if (wanted.isEmpty()) throw new ParseException("'" + p + "': write a state, like '^[waterlogged=true]'");
            return new Mask.Term(null, wanted, false);
        }
        if (lower.startsWith("^")) throw new ParseException("'" + p + "': in a mask '^' takes only a state, like '^[waterlogged=true]'");
        return term(p, false);
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
        Map<String, String> wanted = dev.leo.worldcraft.block.StateTable.parseProperties("x" + props);
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
        if (!blockNames.contains(id)) {
            // Spec 029: «exposd» se parece más a una palabra de máscara que a un bloque.
            String word = Issues.closest(lower, List.of("exposed", "surface", "solid", "above", "below"));
            if (word != null && !lower.contains(":")) throw new ParseException("Unknown block or word '" + lower + "' (did you mean '" + word + "'?)");
            throw new ParseException(unknownBlock(lower));
        }
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

    /** Separa por comas fuera de los corchetes de un estado («a[x=1,y=2], b») y, spec 034, de los paréntesis de una fórmula. */
    static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[' || c == '(' || c == '{') depth++;
            else if (c == ']' || c == ')' || c == '}') depth = Math.max(0, depth - 1);
            else if (c == ',' && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }
}

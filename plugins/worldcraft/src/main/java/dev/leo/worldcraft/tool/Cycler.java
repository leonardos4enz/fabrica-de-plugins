package dev.leo.worldcraft.tool;

import dev.leo.worldcraft.block.StateTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * /wc tool cycler (spec 032 H5 CA4): el valor siguiente de una propiedad de un estado («minecraft:oak_stairs[facing=north,…]»).
 * La API de Paper no lista los valores posibles: salen del valor actual (verdadero/falso, un número de 0 a 30 o los de
 * Minecraft de abajo) y se quedan los que acepta valid (Bukkit.createBlockData en el servidor; lección 021: el registro
 * no corre en las unitarias). Java puro.
 */
public final class Cycler {
    private Cycler() {
    }

    /** Los valores de las propiedades de Minecraft que no son números ni verdadero/falso. */
    static final List<String> WORDS = List.of(
            "north", "east", "south", "west", "up", "down", "x", "y", "z",
            "bottom", "top", "double", "lower", "upper", "left", "right", "single", "head", "foot",
            "straight", "inner_left", "inner_right", "outer_left", "outer_right",
            "floor", "wall", "ceiling", "single_wall", "double_wall", "none", "low", "tall", "side",
            "north_south", "east_west", "ascending_east", "ascending_west", "ascending_north", "ascending_south",
            "south_east", "south_west", "north_west", "north_east", "compare", "subtract", "save", "load", "corner", "data",
            "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone", "iron_xylophone",
            "cow_bell", "didgeridoo", "bit", "banjo", "pling", "zombie", "skeleton", "creeper", "dragon", "wither_skeleton",
            "piglin", "custom_head", "small", "large", "partial", "full", "tip", "tip_merge", "frustum", "middle", "base",
            "down_east", "down_north", "down_south", "down_west", "up_east", "up_north", "up_south", "up_west",
            "east_up", "north_up", "south_up", "west_up", "inactive", "active", "cooldown", "unstable", "tilted",
            "waiting_for_players", "waiting_for_reward_ejection", "ejecting_reward", "ominous", "triggered", "start",
            "end", "cracked", "weathered", "exposed", "unaffected", "oxidized", "unpowered", "powered");

    /** Las propiedades del estado, en su orden. */
    public static List<String> properties(String state) {
        return new ArrayList<>(StateTable.parseProperties(state).keySet());
    }

    /** La propiedad que sigue a current (la primera si current es null o no está); null si no tiene. */
    public static String nextProperty(String state, String current) {
        List<String> ps = properties(state);
        if (ps.isEmpty()) return null;
        int i = current == null ? -1 : ps.indexOf(current);
        return ps.get((i + 1) % ps.size());
    }

    /** Los valores posibles de la propiedad (en orden), según valid. */
    public static List<String> values(String state, String prop, Predicate<String> valid) {
        Map<String, String> p = StateTable.parseProperties(state);
        String cur = p.get(prop);
        if (cur == null) return List.of();
        List<String> cand;
        if (cur.equals("true") || cur.equals("false")) cand = List.of("false", "true");
        else if (cur.matches("\\d+")) {
            cand = new ArrayList<>();
            for (int i = 0; i <= 30; i++) cand.add(String.valueOf(i));
        } else cand = WORDS;
        int b = state.indexOf('[');
        String material = b < 0 ? state : state.substring(0, b);
        List<String> out = new ArrayList<>();
        for (String v : cand) {
            if (v.equals(cur)) {
                out.add(v);
                continue;
            }
            Map<String, String> q = new LinkedHashMap<>(p);
            q.put(prop, v);
            if (valid.test(StateTable.withProperties(material, q))) out.add(v);
        }
        return out;
    }

    /** El estado con la propiedad en su valor siguiente (o anterior, con back); null si no cambia nada. */
    public static String cycle(String state, String prop, boolean back, Predicate<String> valid) {
        List<String> vs = values(state, prop, valid);
        if (vs.size() < 2) return null;
        Map<String, String> p = new LinkedHashMap<>(StateTable.parseProperties(state));
        int i = vs.indexOf(p.get(prop));
        p.put(prop, vs.get(Math.floorMod(i + (back ? -1 : 1), vs.size())));
        int b = state.indexOf('[');
        return StateTable.withProperties(state.substring(0, b), p);
    }
}

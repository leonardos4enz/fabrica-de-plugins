package dev.leo.worldcraft.entity;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Lo que quita {@code /wc remove <tipo>} (spec 031 H5): los grupos de {@code /remove} de WorldEdit o el id de una entidad.
 * Nunca jugadores.
 */
public record RemoveType(String name, Predicate<EntityInfo> test) {
    public static final List<String> GROUPS = List.of("items", "arrows", "projectiles", "xp", "boats", "minecarts", "tnt",
            "falling", "paintings", "itemframes", "armorstands");

    public boolean matches(EntityInfo e) {
        return !e.is(Kind.PLAYER) && test.test(e);
    }

    /** Un grupo o un tipo que existe en types («cow», «minecraft:cow»); null si no es ninguno. */
    public static RemoveType parse(String text, Collection<String> types) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        Kind k = switch (t) {
            case "items" -> Kind.ITEM;
            case "arrows" -> Kind.ARROW;
            case "projectiles" -> Kind.PROJECTILE;
            case "xp" -> Kind.XP;
            case "boats" -> Kind.BOAT;
            case "minecarts" -> Kind.MINECART;
            case "tnt" -> Kind.TNT;
            case "falling" -> Kind.FALLING;
            case "paintings" -> Kind.PAINTING;
            case "itemframes" -> Kind.ITEM_FRAME;
            case "armorstands" -> Kind.ARMOR_STAND;
            default -> null;
        };
        if (k != null) return new RemoveType(t, e -> e.is(k));
        String id = t.contains(":") ? t : "minecraft:" + t;
        if (!types.contains(id) || id.equals("minecraft:player")) return null;
        return new RemoveType(id.replace("minecraft:", ""), e -> e.type().equals(id));
    }

    /** Lo que se puede escribir (para el autocompletado y «did you mean»): los grupos y los tipos sin «minecraft:». */
    public static List<String> words(Collection<String> types) {
        List<String> out = new java.util.ArrayList<>(GROUPS);
        for (String s : types) if (!s.equals("minecraft:player")) out.add(s.replace("minecraft:", ""));
        return out;
    }
}

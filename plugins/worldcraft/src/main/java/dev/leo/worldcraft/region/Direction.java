package dev.leo.worldcraft.region;

import java.util.List;
import java.util.Locale;

/** Las seis direcciones del mundo, con nombres en palabras (H1 CA4): nada de «-x» ni de ejes. */
public enum Direction {
    UP(0, 1, 0), DOWN(0, -1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), EAST(1, 0, 0), WEST(-1, 0, 0);

    public final int dx, dy, dz;

    Direction(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public static final List<String> NAMES = List.of("up", "down", "north", "south", "east", "west");

    /** Por nombre (también la inicial: u, d, n, s, e, w); null si no es una dirección. */
    public static Direction parse(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        for (Direction d : values()) {
            String n = d.name().toLowerCase(Locale.ROOT);
            if (n.equals(t) || (t.length() == 1 && n.charAt(0) == t.charAt(0))) return d;
        }
        return null;
    }

    /**
     * Hacia dónde mira un jugador: arriba o abajo si mira más de 60° en vertical, si no el punto cardinal más cercano.
     * yaw de Minecraft: 0 = sur, 90 = oeste, 180 = norte, 270 = este; pitch positivo mira hacia abajo.
     */
    public static Direction facing(float yaw, float pitch) {
        if (pitch < -60) return UP;
        if (pitch > 60) return DOWN;
        int q = Math.floorMod(Math.round(yaw / 90f), 4);
        return switch (q) {
            case 0 -> SOUTH;
            case 1 -> WEST;
            case 2 -> NORTH;
            default -> EAST;
        };
    }

    public Direction opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}

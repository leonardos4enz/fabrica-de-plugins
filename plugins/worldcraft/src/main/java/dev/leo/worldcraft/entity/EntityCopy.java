package dev.leo.worldcraft.entity;

import dev.leo.worldcraft.edit.Transform;

import java.util.List;

/**
 * Una entidad copiada (plan 031 §1.2): su tipo, dónde está y hacia dónde mira, y todo lo demás en el NBT binario que da
 * Paper (con lo que lleva montado). En el portapapeles la posición es relativa a quien copió; en el diario, del mundo.
 *
 * @param contains los tipos que lleva: ella, lo montado y el bloque de un {@code falling_block} («block:minecraft:sand»),
 *                 para los prohibidos de los límites
 * @param turn     el giro que falta aplicar a la pared de un marco o cuadro al crearlo (la posición y la mirada ya lo tienen)
 */
public record EntityCopy(String type, double x, double y, double z, float yaw, float pitch, byte[] nbt,
                         List<String> contains, Transform turn) {
    public EntityCopy {
        contains = List.copyOf(contains);
        if (turn == null) turn = Transform.IDENTITY;
    }

    public EntityCopy(String type, double x, double y, double z, float yaw, float pitch, byte[] nbt, List<String> contains) {
        this(type, x, y, z, yaw, pitch, nbt, contains, Transform.IDENTITY);
    }

    public EntityCopy shifted(double dx, double dy, double dz) {
        return new EntityCopy(type, x + dx, y + dy, z + dz, yaw, pitch, nbt, contains, turn);
    }

    /**
     * Girada o volteada alrededor del bloque 0 (el origen del portapapeles): la posición como el bloque que la contiene
     * ({@code M·(p − ½) + ½}), la mirada girando su dirección y la inclinación al revés si se voltea arriba-abajo.
     */
    public EntityCopy transformed(Transform t) {
        if (t.isIdentity()) return this;
        double[] p = new double[3];
        t.apply(x - 0.5, y - 0.5, z - 0.5, p);
        double r = Math.toRadians(yaw);
        double[] d = new double[3];
        t.apply(-Math.sin(r), 0, Math.cos(r), d);
        float ny = (float) wrap(Math.toDegrees(Math.atan2(-d[0], d[2])));
        double[] up = new double[3];
        t.apply(0, 1, 0, up);
        float np = up[1] < 0 ? -pitch : pitch;
        return new EntityCopy(type, p[0] + 0.5, p[1] + 0.5, p[2] + 0.5, ny, np, nbt, contains, turn.then(t));
    }

    /** La pared de un colgante después del giro pendiente: {dx, dy, dz} de su cara. */
    public int[] face(int dx, int dy, int dz) {
        int[] out = new int[3];
        turn.apply(dx, dy, dz, out);
        return out;
    }

    static double wrap(double deg) {
        double d = deg % 360;
        if (d >= 180) d -= 360;
        if (d < -180) d += 360;
        // -0 y 180 redondeados (el giro con senos deja 89.99999)
        double r = Math.rint(d * 1000) / 1000;
        return r == -180 ? 180 : r == -0.0 ? 0 : r;
    }
}

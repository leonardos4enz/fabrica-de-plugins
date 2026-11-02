package dev.leo.worldcraft.region;

/**
 * Spec 030 (sel ellipsoid, sel sphere): la misma fórmula que /wc sphere de la 028 (radio + ½), así una esfera
 * seleccionada es igual a la que se construye.
 */
public final class Ellipsoid extends Columns {
    public final int cx, cy, cz, rx, ry, rz;
    private final boolean sphere;

    public Ellipsoid(int cx, int cy, int cz, int rx, int ry, int rz, boolean sphere) {
        super(new Box(cx - rx, cy - ry, cz - rz, cx + rx, cy + ry, cz + rz));
        if (rx < 0 || ry < 0 || rz < 0) throw new IllegalArgumentException("negative radius");
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.sphere = sphere;
    }

    @Override
    protected int[] compute(int x, int z) {
        double ea = rx + 0.5, eb = ry + 0.5, ec = rz + 0.5;
        double dx = x - cx, dz = z - cz;
        double q = 1 - (dx / ea) * (dx / ea) - (dz / ec) * (dz / ec);
        if (q < 0) return null;
        int h = Math.min(ry, (int) Math.floor(eb * Math.sqrt(q)));
        return new int[]{cy - h, cy + h};
    }

    @Override
    public String kind() {
        return sphere ? "sphere" : "ellipsoid";
    }
}

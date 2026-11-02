package dev.leo.worldcraft.region;

/** Spec 030 (sel cyl): las columnas dentro de la elipse de radios (rx, rz) + ½, de minY a maxY (como /wc cyl de la 028). */
public final class Cylinder extends Columns {
    public final int cx, cz, rx, rz, minY, maxY;

    public Cylinder(int cx, int cz, int rx, int rz, int minY, int maxY) {
        super(Box.of(cx - rx, minY, cz - rz, cx + rx, maxY, cz + rz));
        if (rx < 0 || rz < 0) throw new IllegalArgumentException("negative radius");
        this.cx = cx;
        this.cz = cz;
        this.rx = rx;
        this.rz = rz;
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
    }

    @Override
    protected int[] compute(int x, int z) {
        double ea = rx + 0.5, ec = rz + 0.5, dx = x - cx, dz = z - cz;
        return (dx / ea) * (dx / ea) + (dz / ec) * (dz / ec) > 1 ? null : new int[]{minY, maxY};
    }

    @Override
    public String kind() {
        return "cyl";
    }
}

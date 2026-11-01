package dev.leo.worldcraft.region;

/**
 * Una caja de bloques con los dos extremos incluidos (la selección cuboide, H1). Inmutable; todas las operaciones
 * devuelven una caja nueva. Las coordenadas son de bloque.
 */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public Box {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("min > max");
    }

    /** La caja entre dos esquinas cualesquiera. */
    public static Box of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) width() * height() * depth();
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Agranda la caja n bloques hacia esa dirección (n negativo la achica desde ese lado, sin invertirla). */
    public Box expand(Direction d, int n) {
        int ax = minX, ay = minY, az = minZ, bx = maxX, by = maxY, bz = maxZ;
        switch (d) {
            case UP -> by += n;
            case DOWN -> ay -= n;
            case EAST -> bx += n;
            case WEST -> ax -= n;
            case SOUTH -> bz += n;
            case NORTH -> az -= n;
        }
        if (ax > bx || ay > by || az > bz) throw new IllegalArgumentException("the selection would be empty");
        return new Box(ax, ay, az, bx, by, bz);
    }

    /** Achica la caja n bloques desde el lado de esa dirección. */
    public Box contract(Direction d, int n) {
        return expand(d, -n);
    }

    public Box shift(int dx, int dy, int dz) {
        return new Box(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public Box shift(Direction d, int n) {
        return shift(d.dx * n, d.dy * n, d.dz * n);
    }

    /** Recorta en y al alto del mundo; null si no queda nada. */
    public Box clampY(int worldMin, int worldMaxInclusive) {
        int a = Math.max(minY, worldMin), b = Math.min(maxY, worldMaxInclusive);
        return a > b ? null : new Box(minX, a, minZ, maxX, b, maxZ);
    }

    public Box withY(int a, int b) {
        return new Box(minX, Math.min(a, b), minZ, maxX, Math.max(a, b), maxZ);
    }

    /** «12×5×8» (ancho × alto × fondo). */
    public String size() {
        return width() + "×" + height() + "×" + depth();
    }

    /** Cuántos bloques visita esa forma dentro de la caja (para límites y estimaciones, sin recorrerla). */
    public long count(Shape shape) {
        long w = width(), h = height(), d = depth();
        long ring = (w <= 2 || d <= 2) ? w * d : w * d - (w - 2) * (d - 2);
        return switch (shape) {
            case FILL -> w * h * d;
            case WALLS -> ring * h;
            case FACES -> h <= 2 ? w * h * d : ring * h + 2 * (w * d - ring);
        };
    }
}

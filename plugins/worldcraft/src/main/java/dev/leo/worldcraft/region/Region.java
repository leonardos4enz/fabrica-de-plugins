package dev.leo.worldcraft.region;

/**
 * Spec 030 (plan §1.1): una selección de cualquier forma. La caja ({@link Box}) es una; las de columnas ({@link Columns}:
 * polígono, elipsoide, esfera, cilindro, convexa) y la mágica ({@link PointSet}) son las otras. Java puro.
 */
public interface Region {
    /** La caja que la abarca. */
    Box bounds();

    boolean contains(int x, int y, int z);

    /** Los bloques de verdad (no los de la caja). */
    long volume();

    /** Cuántas posiciones visita esa forma: FILL todo; WALLS la cáscara de costado; FACES toda la cáscara (plan §1.1). */
    long count(Shape shape);

    /** Las y de la columna (x, z) que visita esa forma, de abajo arriba (vacío si ninguna). */
    int[] ys(int x, int z, Shape shape);

    /** [y mínima, y máxima] de la columna, o null si la forma no pasa por ahí. */
    int[] span(int x, int z);

    /** La misma forma dentro de [min, max] en y; null si no queda nada. */
    Region clampY(int min, int max);

    /** «cuboid», «poly», «sphere»… (para los mensajes). */
    String kind();

    /** «12×5×8» de la caja que la abarca. */
    default String size() {
        return bounds().size();
    }
}

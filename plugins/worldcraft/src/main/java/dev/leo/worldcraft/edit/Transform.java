package dev.leo.blockwright.edit;

import java.util.ArrayList;
import java.util.List;

/**
 * Giros de 90° alrededor de y y espejos del portapapeles (H3 CA3). Una matriz entera ortogonal (su inversa es la
 * traspuesta) y la lista de pasos, para girar también el estado de cada bloque en el mismo orden.
 */
public final class Transform {
    public enum Step { ROTATE_90, FLIP_X, FLIP_Y, FLIP_Z }

    /** Fila por fila: x' = m[0]·x + m[1]·y + m[2]·z, etc. */
    private final int[] m;
    private final List<Step> steps;

    private Transform(int[] m, List<Step> steps) {
        this.m = m;
        this.steps = List.copyOf(steps);
    }

    public static final Transform IDENTITY = new Transform(new int[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, List.of());

    /** Gira un cuarto de vuelta en el sentido del reloj visto desde arriba (el norte pasa al este). */
    public Transform rotate90() {
        return then(new int[]{0, 0, -1, 0, 1, 0, 1, 0, 0}, Step.ROTATE_90);
    }

    /** Gira grados múltiplos de 90 (negativos: al revés). */
    public Transform rotate(int degrees) {
        if (degrees % 90 != 0) throw new IllegalArgumentException("only multiples of 90");
        Transform t = this;
        for (int i = 0; i < Math.floorMod(degrees / 90, 4); i++) t = t.rotate90();
        return t;
    }

    public Transform flip(char axis) {
        return switch (axis) {
            case 'x' -> then(new int[]{-1, 0, 0, 0, 1, 0, 0, 0, 1}, Step.FLIP_X);
            case 'y' -> then(new int[]{1, 0, 0, 0, -1, 0, 0, 0, 1}, Step.FLIP_Y);
            case 'z' -> then(new int[]{1, 0, 0, 0, 1, 0, 0, 0, -1}, Step.FLIP_Z);
            default -> throw new IllegalArgumentException("axis " + axis);
        };
    }

    private Transform then(int[] a, Step s) {
        int[] r = new int[9];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            int v = 0;
            for (int k = 0; k < 3; k++) v += a[i * 3 + k] * m[k * 3 + j];
            r[i * 3 + j] = v;
        }
        List<Step> st = new ArrayList<>(steps);
        st.add(s);
        return new Transform(r, st);
    }

    public List<Step> steps() {
        return steps;
    }

    public boolean isIdentity() {
        return steps.isEmpty() || java.util.Arrays.equals(m, IDENTITY.m);
    }

    /** v' = M·v en out. */
    public void apply(int x, int y, int z, int[] out) {
        out[0] = m[0] * x + m[1] * y + m[2] * z;
        out[1] = m[3] * x + m[4] * y + m[5] * z;
        out[2] = m[6] * x + m[7] * y + m[8] * z;
    }

    /** v = Mᵀ·v' en out (la inversa). */
    public void inverse(int x, int y, int z, int[] out) {
        out[0] = m[0] * x + m[3] * y + m[6] * z;
        out[1] = m[1] * x + m[4] * y + m[7] * z;
        out[2] = m[2] * x + m[5] * y + m[8] * z;
    }
}

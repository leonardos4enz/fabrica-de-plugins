package dev.leo.skinstatues.skin;

import java.awt.image.BufferedImage;

/**
 * Skin de jugador lista para la estatua: 64×64 ARGB (la de 64×32 se convierte como lo hace el cliente), modelo clásico o
 * delgado y la capa opcional en 64×32.
 */
public final class Skin {
    public static final int SIZE = 64;

    private final int[] px;
    private final boolean slim;
    private final int[] cape;

    private Skin(int[] px, boolean slim, int[] cape) {
        this.px = px;
        this.slim = slim;
        this.cape = cape;
    }

    public boolean slim() {
        return slim;
    }

    public boolean hasCape() {
        return cape != null;
    }

    /** Píxel ARGB de la skin (primera y segunda capa comparten la imagen). */
    public int pixel(int x, int y) {
        return px[y * SIZE + x];
    }

    /** Si el sombrero (segunda capa de la cabeza) tiene algún píxel desde esa opacidad: sobresale por arriba. */
    public boolean hasHat(int alpha) {
        for (int y = 0; y < 16; y++) for (int x = 32; x < 64; x++) if ((px[y * SIZE + x] >>> 24) >= alpha) return true;
        return false;
    }

    public int capePixel(int x, int y) {
        return cape[y * SIZE + x];
    }

    public Skin withoutCape() {
        return cape == null ? this : new Skin(px, slim, null);
    }

    /** El mismo dibujo con el modelo forzado (`--slim`/`--classic`, spec 023 H1 CA3). */
    public Skin withSlim(boolean s) {
        return s == slim ? this : new Skin(px, s, cape);
    }

    /**
     * Spec 023 H1 CA3: una skin de 64×64 es delgada si las dos columnas que solo usan los brazos anchos (la espalda de
     * cada brazo: x 54–55, y 20–31 en el derecho; x 46–47, y 52–63 en el izquierdo) son todas transparentes. Se mira la
     * imagen tal como llegó (antes de volver opaca la primera capa); la de 64×32 siempre es clásica.
     */
    public static boolean detectSlim(BufferedImage image) {
        if (image.getWidth() != SIZE || image.getHeight() != SIZE) return false;
        for (int y = 20; y < 32; y++) for (int x = 54; x < 56; x++) if ((image.getRGB(x, y) >>> 24) != 0) return false;
        for (int y = 52; y < 64; y++) for (int x = 46; x < 48; x++) if ((image.getRGB(x, y) >>> 24) != 0) return false;
        return true;
    }

    /**
     * Arma la skin desde la imagen ya validada: 64×64 o 64×32. La capa, si hay, es de 64×32 o la antigua de 22×17.
     *
     * @throws IllegalArgumentException si un tamaño no es válido
     */
    public static Skin of(BufferedImage image, boolean slim, BufferedImage capeImage) {
        int w = image.getWidth(), h = image.getHeight();
        if (w != SIZE || (h != SIZE && h != 32)) throw new IllegalArgumentException("skin size " + w + "x" + h);
        int[] px = new int[SIZE * SIZE];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * SIZE + x] = image.getRGB(x, y);
        if (h == 32) legacy(px);
        // Como el cliente: la primera capa siempre es opaca (algunas skins dejan píxeles transparentes en la cara).
        noAlpha(px, 0, 0, 32, 16);
        noAlpha(px, 0, 16, 64, 32);
        noAlpha(px, 16, 48, 48, 64);
        return new Skin(px, slim, capeImage == null ? null : cape(capeImage));
    }

    private static int[] cape(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        boolean ok = (w == 64 && h == 32) || (w == 22 && h == 17);
        if (!ok) throw new IllegalArgumentException("cape size " + w + "x" + h);
        int[] c = new int[SIZE * 32];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) c[y * SIZE + x] = img.getRGB(x, y);
        return c;
    }

    /** Skin antigua de 64×32: los miembros izquierdos son el espejo de los derechos y no hay chaqueta ni mangas. */
    private static void legacy(int[] px) {
        copy(px, 4, 16, 16, 32, 4, 4);
        copy(px, 8, 16, 16, 32, 4, 4);
        copy(px, 0, 20, 24, 32, 4, 12);
        copy(px, 4, 20, 16, 32, 4, 12);
        copy(px, 8, 20, 8, 32, 4, 12);
        copy(px, 12, 20, 16, 32, 4, 12);
        copy(px, 44, 16, -8, 32, 4, 4);
        copy(px, 48, 16, -8, 32, 4, 4);
        copy(px, 40, 20, 0, 32, 4, 12);
        copy(px, 44, 20, -8, 32, 4, 12);
        copy(px, 48, 20, -16, 32, 4, 12);
        copy(px, 52, 20, -8, 32, 4, 12);
        // Truco de Notch: si el sombrero no tiene ningún píxel transparente, no hay sombrero.
        boolean anyTransparent = false;
        for (int y = 0; y < 16 && !anyTransparent; y++) {
            for (int x = 32; x < 64; x++) {
                if ((px[y * SIZE + x] >>> 24) < 128) {
                    anyTransparent = true;
                    break;
                }
            }
        }
        if (!anyTransparent) for (int y = 0; y < 16; y++) for (int x = 32; x < 64; x++) px[y * SIZE + x] = 0;
    }

    /** Copia un rectángulo w×h de (x, y) a (x+dx, y+dy) espejado en horizontal (como el cliente). */
    private static void copy(int[] px, int x, int y, int dx, int dy, int w, int h) {
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                px[(y + dy + i) * SIZE + (x + dx + (w - 1 - j))] = px[(y + i) * SIZE + (x + j)];
            }
        }
    }

    private static void noAlpha(int[] px, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) px[y * SIZE + x] |= 0xFF000000;
    }
}

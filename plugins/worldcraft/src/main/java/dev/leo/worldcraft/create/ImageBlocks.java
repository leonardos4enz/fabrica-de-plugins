package dev.leo.worldcraft.create;

import dev.leo.worldcraft.color.BlockColors;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Una imagen hecha bloques (spec 035 H4, plan §1.4): leerla con guardas, achicarla promediando, elegir el bloque de cada
 * píxel y dónde va cada uno (de pie o acostada, sin espejar). Java puro: corre en el hilo de las imágenes.
 */
public final class ImageBlocks {
    private ImageBlocks() {
    }

    /** El lado más grande que se decodifica (una imagen «bomba» se rechaza por la cabecera, antes de ocupar memoria). */
    public static final int MAX_SIDE = 4096;
    private static final Set<String> FORMATS = Set.of("png", "jpeg", "jpg", "gif");

    /** Píxeles ARGB fila por fila (y = 0 arriba). */
    public record Pixels(int width, int height, int[] argb) {
    }

    /**
     * Lee PNG, JPG o GIF (el primer cuadro).
     *
     * @throws IllegalArgumentException con el motivo en inglés
     */
    public static Pixels read(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) throw new IllegalArgumentException("it is not a PNG, JPG or GIF image");
            ImageReader reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("it is not a PNG, JPG or GIF image");
                }
                reader.setInput(in, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0) throw new IllegalArgumentException("the image is empty");
                if (w > MAX_SIDE || h > MAX_SIDE) {
                    throw new IllegalArgumentException("the image is " + w + "×" + h + " pixels; at most " + MAX_SIDE + " on each side");
                }
                BufferedImage img = reader.read(0);
                return new Pixels(w, h, img.getRGB(0, 0, w, h, null, 0, w));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException iae) throw iae;
            throw new IllegalArgumentException("the image could not be read (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
        }
    }

    /**
     * La imagen con su lado más largo de side píxeles: cada píxel nuevo es el promedio de los que cubre (el alfa pesa el
     * color: un borde transparente no oscurece); al agrandar, el más cercano.
     */
    public static Pixels scale(Pixels p, int side) {
        int max = Math.max(p.width, p.height);
        if (side == max) return p;
        int nw = Math.max(1, (int) Math.round((double) p.width * side / max)), nh = Math.max(1, (int) Math.round((double) p.height * side / max));
        int[] out = new int[nw * nh];
        for (int ty = 0; ty < nh; ty++) {
            int y0 = (int) ((long) ty * p.height / nh), y1 = Math.max(y0 + 1, (int) ((long) (ty + 1) * p.height / nh));
            for (int tx = 0; tx < nw; tx++) {
                int x0 = (int) ((long) tx * p.width / nw), x1 = Math.max(x0 + 1, (int) ((long) (tx + 1) * p.width / nw));
                long a = 0, r = 0, g = 0, b = 0, n = 0;
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        int c = p.argb[y * p.width + x];
                        int al = c >>> 24;
                        a += al;
                        r += (long) ((c >> 16) & 0xFF) * al;
                        g += (long) ((c >> 8) & 0xFF) * al;
                        b += (long) (c & 0xFF) * al;
                        n++;
                    }
                }
                int alpha = (int) (a / n);
                int rgb = a == 0 ? 0 : (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
                out[ty * nw + tx] = alpha << 24 | rgb;
            }
        }
        return new Pixels(nw, nh, out);
    }

    /** El índice de la tabla de cada píxel, o −1 si es transparente (alfa < 128). */
    public static int[] blocks(Pixels p, BlockColors colors) {
        int[] out = new int[p.argb.length];
        for (int i = 0; i < out.length; i++) out[i] = (p.argb[i] >>> 24) < 128 ? -1 : colors.nearest(p.argb[i]);
        return out;
    }

    /** Dónde va cada píxel: el de (columna 0, fila 0) en o; las columnas avanzan por u, las filas por v. */
    public record Placement(int ox, int oy, int oz, int ux, int uy, int uz, int vx, int vy, int vz) {
        public int x(int col, int row) {
            return ox + col * ux + row * vx;
        }

        public int y(int col, int row) {
            return oy + col * uy + row * vy;
        }

        public int z(int col, int row) {
            return oz + col * uz + row * vz;
        }

        /** La columna de una posición (u y v van por los ejes: el producto escalar la deshace). */
        public int col(int x, int y, int z) {
            return (x - ox) * ux + (y - oy) * uy + (z - oz) * uz;
        }

        public int row(int x, int y, int z) {
            return (x - ox) * vx + (y - oy) * vy + (z - oz) * vz;
        }
    }

    /** La dirección cardinal (dx, dz) de una mirada de Bukkit (yaw 0 = sur, 90 = oeste). */
    public static int[] facing(float yaw) {
        int q = Math.floorMod(Math.round(yaw / 90f), 4);
        return switch (q) {
            case 0 -> new int[]{0, 1};
            case 1 -> new int[]{-1, 0};
            case 2 -> new int[]{0, -1};
            default -> new int[]{1, 0};
        };
    }

    /**
     * De pie, como un mural frente al jugador (H4 CA2): a 2 bloques hacia (dx, dz), centrada en él, la fila de abajo a la
     * altura de sus pies. La izquierda de la imagen queda a la izquierda de quien mira (derecha = (−dz, dx)).
     */
    public static Placement wall(int fx, int fy, int fz, int dx, int dz, int width, int height) {
        int rx = -dz, rz = dx, cc = (width - 1) / 2;
        return new Placement(fx + 2 * dx - cc * rx, fy + height - 1, fz + 2 * dz - cc * rz, rx, 0, rz, 0, -1, 0);
    }

    /**
     * Acostada en la capa de los pies (H4 CA2): la fila de abajo a 1 bloque delante y la de arriba la más lejana, como un
     * papel en el suelo leído desde donde está el jugador.
     */
    public static Placement floor(int fx, int fy, int fz, int dx, int dz, int width, int height) {
        int rx = -dz, rz = dx, cc = (width - 1) / 2;
        return new Placement(fx + height * dx - cc * rx, fy, fz + height * dz - cc * rz, rx, 0, rz, -dx, 0, -dz);
    }
}

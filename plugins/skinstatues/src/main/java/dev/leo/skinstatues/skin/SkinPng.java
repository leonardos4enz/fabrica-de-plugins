package dev.leo.skinstatues.skin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Lectura defensiva de PNG (plan 021 §5, denegación de servicio): tamaño del archivo y dimensiones de la cabecera
 * antes de decodificar, así una «bomba» de compresión no llega a ImageIO.
 */
public final class SkinPng {
    public static final int MAX_BYTES = 1024 * 1024;
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public enum Kind {
        SKIN(new int[][]{{64, 64}, {64, 32}}), CAPE(new int[][]{{64, 32}, {22, 17}});

        final int[][] sizes;

        Kind(int[][] sizes) {
            this.sizes = sizes;
        }
    }

    private SkinPng() {
    }

    public static BufferedImage read(byte[] data, Kind kind, String what) throws SkinException {
        if (data.length > MAX_BYTES) throw new SkinException("image-invalid", "file", what, "reason", "larger than 1 MB");
        if (data.length < 24) throw new SkinException("image-invalid", "file", what, "reason", "not a PNG image");
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (data[i] != SIGNATURE[i]) throw new SkinException("image-invalid", "file", what, "reason", "not a PNG image");
        }
        int w = readInt(data, 16), h = readInt(data, 20);
        boolean ok = false;
        StringBuilder expected = new StringBuilder();
        for (int[] s : kind.sizes) {
            ok |= s[0] == w && s[1] == h;
            if (!expected.isEmpty()) expected.append(" or ");
            expected.append(s[0]).append('x').append(s[1]);
        }
        if (!ok) {
            throw new SkinException("image-invalid", "file", what, "reason", "it is " + w + "x" + h + ", expected " + expected);
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) throw new SkinException("image-invalid", "file", what, "reason", "unreadable PNG");
            return img;
        } catch (IOException e) {
            throw new SkinException("image-invalid", "file", what, "reason", "unreadable PNG");
        }
    }

    private static int readInt(byte[] d, int at) {
        return (d[at] & 0xFF) << 24 | (d[at + 1] & 0xFF) << 16 | (d[at + 2] & 0xFF) << 8 | (d[at + 3] & 0xFF);
    }
}

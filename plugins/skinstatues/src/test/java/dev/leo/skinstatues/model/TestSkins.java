package dev.leo.skinstatues.model;

import dev.leo.skinstatues.skin.Skin;

import java.awt.image.BufferedImage;

/** Skins sintéticas para las pruebas: un color fácil de reconocer por región. */
public final class TestSkins {
    public static final int GRAY = 0xFF808080, RED = 0xFFFF0000, GREEN = 0xFF00FF00, BLUE = 0xFF0000FF;
    public static final int YELLOW = 0xFFFFFF00, MAGENTA = 0xFFFF00FF, CYAN = 0xFF00FFFF, ORANGE = 0xFFFF8000;

    private TestSkins() {
    }

    /** Primera capa gris, frente de la cabeza rojo, brazo derecho verde, frente del cuerpo azul; segunda capa vacía. */
    public static BufferedImage image() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        fill(img, 0, 0, 32, 16, GRAY);
        fill(img, 0, 16, 64, 32, GRAY);
        fill(img, 16, 48, 48, 64, GRAY);
        fill(img, 8, 8, 16, 16, RED);
        fill(img, 40, 16, 56, 32, GREEN);
        fill(img, 20, 20, 28, 32, BLUE);
        return img;
    }

    public static BufferedImage cape() {
        BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        fill(img, 0, 0, 64, 32, 0xFF000000);
        fill(img, 1, 1, 11, 17, CYAN);
        fill(img, 12, 1, 22, 17, ORANGE);
        return img;
    }

    public static Skin plain() {
        return Skin.of(image(), false, null);
    }

    public static void fill(BufferedImage img, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) img.setRGB(x, y, argb);
    }
}

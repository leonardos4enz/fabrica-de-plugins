package dev.leo.skinstatues.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * La skin de ejemplo (H9 CA3), original y dibujada por código: un constructor con casco amarillo y chaleco naranja en
 * la segunda capa (para lucir el relieve) y una capa verde con una «S». Nada de Mojang.
 * Uso: ./gradlew :skinstatues:exampleSkin (escribe Example.png y Example-cape.png en src/main/resources/skins/).
 */
public final class ExampleSkinMain {
    static final int SKIN = 0xFFE0A878, SKIN_DARK = 0xFFC88A5C, HAIR = 0xFF5A3A22, WHITE = 0xFFF4F4F4, EYE = 0xFF2B4C8C;
    static final int MOUTH = 0xFF9A5A44, SHIRT = 0xFFD8D8D8, BELT = 0xFF4A3420, JEANS = 0xFF3A5A9A, JEANS_DARK = 0xFF2E4A82;
    static final int BOOT = 0xFF3B2A1A, HAT = 0xFFF2C21B, HAT_DARK = 0xFFC99A10, VEST = 0xFFF07820, STRIPE = 0xFFE6E6E6;
    static final int CAPE = 0xFF2E6B3A, CAPE_EDGE = 0xFF4E9B5A, CAPE_MARK = 0xFFF2C21B;

    private ExampleSkinMain() {
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        dir.mkdirs();
        ImageIO.write(skin(), "png", new File(dir, "Example.png"));
        ImageIO.write(cape(), "png", new File(dir, "Example-cape.png"));
        System.out.println("Example.png and Example-cape.png written to " + dir);
    }

    public static BufferedImage skin() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        // Cabeza: pelo arriba y atrás, cara de frente.
        fill(img, 8, 0, 16, 8, HAIR);                 // arriba
        fill(img, 16, 0, 24, 8, SKIN_DARK);           // abajo
        fill(img, 0, 8, 8, 16, SKIN);                 // derecha
        fill(img, 16, 8, 24, 16, SKIN);               // izquierda
        fill(img, 0, 8, 8, 11, HAIR);
        fill(img, 16, 8, 24, 11, HAIR);
        fill(img, 24, 8, 32, 16, HAIR);               // nuca
        fill(img, 8, 8, 16, 16, SKIN);                // cara
        fill(img, 8, 8, 16, 10, HAIR);                // flequillo
        set(img, 8, 10, HAIR);
        set(img, 15, 10, HAIR);
        set(img, 9, 12, WHITE);
        set(img, 10, 12, EYE);
        set(img, 13, 12, EYE);
        set(img, 14, 12, WHITE);
        set(img, 11, 13, SKIN_DARK);
        set(img, 12, 13, SKIN_DARK);
        fill(img, 10, 14, 14, 15, MOUTH);
        // Casco (segunda capa de la cabeza): domo arriba y tres filas alrededor, con el ala más oscura.
        fill(img, 40, 0, 48, 8, HAT);
        fill(img, 43, 0, 45, 8, HAT_DARK);            // cresta del casco
        for (int x = 32; x < 64; x++) {
            set(img, x, 8, HAT);
            set(img, x, 9, HAT);
            set(img, x, 10, HAT_DARK);
        }
        // Cuerpo: camisa, cinturón y el comienzo del pantalón.
        fill(img, 20, 16, 28, 20, SHIRT);
        fill(img, 28, 16, 36, 20, JEANS);
        fill(img, 16, 20, 40, 32, SHIRT);
        fill(img, 16, 30, 40, 31, BELT);
        fill(img, 16, 31, 40, 32, JEANS);
        // Chaleco (segunda capa del cuerpo): abierto al frente, con franjas reflectantes.
        fill(img, 20, 32, 28, 36, VEST);              // hombros
        fill(img, 16, 36, 40, 46, VEST);
        fill(img, 23, 36, 25, 46, 0);                 // abierto al frente
        fill(img, 16, 42, 40, 43, STRIPE);
        fill(img, 23, 42, 25, 43, 0);
        // Brazos: manga de camisa y mano; la manga del chaleco en la segunda capa.
        arm(img, 40, 16, 44, 48);
        arm(img, 32, 48, 36, 40);
        sleeve(img, 40, 32);
        sleeve(img, 48, 48);
        // Piernas: jeans y botas; las botas también en relieve (segunda capa).
        leg(img, 0, 16);
        leg(img, 16, 48);
        boots(img, 0, 32);
        boots(img, 0, 48);
        return img;
    }

    /** Brazo de 4 de ancho: (u, v) de la caja; top y bottom son las columnas de arriba y abajo. */
    private static void arm(BufferedImage img, int u, int v, int top, int bottom) {
        fill(img, top, v, top + 4, v + 4, SHIRT);
        fill(img, bottom, v, bottom + 4, v + 4, SKIN);
        fill(img, u, v + 4, u + 16, v + 12, SHIRT);
        fill(img, u, v + 12, u + 16, v + 16, SKIN);
    }

    private static void sleeve(BufferedImage img, int u, int v) {
        fill(img, u + 4, v, u + 8, v + 4, VEST);
        fill(img, u, v + 4, u + 16, v + 8, VEST);
    }

    private static void leg(BufferedImage img, int u, int v) {
        fill(img, u + 4, v, u + 8, v + 4, JEANS);
        fill(img, u + 8, v, u + 12, v + 4, BOOT);
        fill(img, u, v + 4, u + 16, v + 14, JEANS);
        fill(img, u + 4, v + 4, u + 5, v + 14, JEANS_DARK); // costura al frente
        fill(img, u, v + 14, u + 16, v + 16, BOOT);
    }

    private static void boots(BufferedImage img, int u, int v) {
        fill(img, u, v + 13, u + 16, v + 16, BOOT);
    }

    public static BufferedImage cape() {
        BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        fill(img, 0, 0, 22, 17, CAPE_EDGE);
        fill(img, 1, 1, 11, 17, CAPE);                // lo que se ve desde atrás
        fill(img, 12, 1, 22, 17, CAPE);               // lo que mira a la espalda
        // Una «S» de 6×8 en el centro de la cara de afuera.
        String[] s = {"..####", ".#....", ".#....", "..###.", ".....#", ".....#", "#....#", ".####."};
        for (int y = 0; y < s.length; y++) {
            for (int x = 0; x < 6; x++) if (s[y].charAt(x) == '#') set(img, 3 + x, 5 + y, CAPE_MARK);
        }
        return img;
    }

    private static void fill(BufferedImage img, int x0, int y0, int x1, int y1, int argb) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) img.setRGB(x, y, argb);
    }

    private static void set(BufferedImage img, int x, int y, int argb) {
        img.setRGB(x, y, argb);
    }
}

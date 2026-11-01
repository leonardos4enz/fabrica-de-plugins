package dev.leo.skinstatues.skin;

import dev.leo.skinstatues.model.TestSkins;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SkinTest {
    static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void H2_CA1_skin_de_64x64_y_capa() throws Exception {
        Skin s = Skin.of(SkinPng.read(png(TestSkins.image()), SkinPng.Kind.SKIN, "a.png"), true,
                SkinPng.read(png(TestSkins.cape()), SkinPng.Kind.CAPE, "c.png"));
        assertTrue(s.slim());
        assertTrue(s.hasCape());
        assertEquals(TestSkins.RED, s.pixel(8, 8));
        assertEquals(TestSkins.CYAN, s.capePixel(1, 1));
        assertFalse(s.withoutCape().hasCape());
    }

    @Test
    void H2_CA1_skin_antigua_64x32_espeja_los_miembros_izquierdos() {
        BufferedImage old = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        TestSkins.fill(old, 0, 0, 64, 32, 0xFF808080);
        TestSkins.fill(old, 32, 0, 64, 16, 0x00000000); // sombrero transparente
        TestSkins.fill(old, 4, 20, 5, 32, TestSkins.RED); // columna izquierda del frente de la pierna derecha
        Skin s = Skin.of(old, false, null);
        // Frente de la pierna izquierda (20..24, 52..64): espejo, así que la columna roja queda a la derecha.
        assertEquals(TestSkins.RED, s.pixel(23, 52));
        assertNotEquals(TestSkins.RED, s.pixel(20, 52));
        assertEquals(0, s.pixel(0, 40) >>> 24, "sin pantalón ni chaqueta");
    }

    @Test
    void H2_CA1_truco_de_Notch_sombrero_opaco_no_es_sombrero() {
        BufferedImage old = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        TestSkins.fill(old, 0, 0, 64, 32, 0xFF808080);
        assertFalse(Skin.of(old, false, null).hasHat(1));
    }

    @Test
    void la_primera_capa_siempre_es_opaca() {
        BufferedImage img = TestSkins.image();
        img.setRGB(8, 8, 0x00FF0000);
        assertEquals(0xFF, Skin.of(img, false, null).pixel(8, 8) >>> 24);
    }

    @Test
    void H2_CA4_imagenes_invalidas() throws Exception {
        assertEquals("image-invalid", assertThrows(SkinException.class,
                () -> SkinPng.read("hola".getBytes(), SkinPng.Kind.SKIN, "x.png")).key());
        BufferedImage big = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        SkinException e = assertThrows(SkinException.class, () -> SkinPng.read(png(big), SkinPng.Kind.SKIN, "x.png"));
        assertTrue(String.valueOf(e.args()[3]).contains("128x128"));
        assertThrows(SkinException.class, () -> SkinPng.read(png(TestSkins.image()), SkinPng.Kind.CAPE, "c.png"));
        assertThrows(SkinException.class, () -> SkinPng.read(new byte[SkinPng.MAX_BYTES + 1], SkinPng.Kind.SKIN, "x.png"));
    }

    @Test
    void la_capa_antigua_de_22x17_sirve() throws Exception {
        BufferedImage c = new BufferedImage(22, 17, BufferedImage.TYPE_INT_ARGB);
        TestSkins.fill(c, 0, 0, 22, 17, TestSkins.CYAN);
        assertTrue(Skin.of(TestSkins.image(), false, SkinPng.read(png(c), SkinPng.Kind.CAPE, "c.png")).hasCape());
    }
}

package dev.leo.omnievents.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 014, H1: centered_message. */
class CenteredTest {
    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static int leading(String s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == ' ') n++;
        return n;
    }

    @Test
    void H1_CA1_ancho_con_letras_angostas_y_negrita() {
        assertEquals(6, Centered.width("A", false));
        assertEquals(2, Centered.width("i", false));
        assertEquals(7, Centered.width("A", true));
        // la negrita no ensancha los espacios
        assertEquals(4, Centered.width(" ", true));
        assertTrue(Centered.width("iiii", false) < Centered.width("WWWW", false));
    }

    @Test
    void H1_CA1_centra_cada_linea_por_su_cuenta() {
        Component msg = MiniMessage.miniMessage().deserialize("<gold><b>EVENTO</b>\n<gray>Empieza pronto, prepárense todos");
        List<Component> lines = Centered.lines(msg);
        assertEquals(2, lines.size());
        String a = plain(lines.get(0)), b = plain(lines.get(1));
        assertEquals("EVENTO", a.trim());
        assertEquals("Empieza pronto, prepárense todos", b.trim());
        // Cálculo a mano: EVENTO en negrita = 6 × (5 + 1 + 1) = 42 px → (154 − 21) / 4 = 33 espacios.
        assertEquals(33, leading(a));
        assertTrue(leading(b) < leading(a), "la línea larga lleva menos espacios");
    }

    @Test
    void H1_CA1_conserva_el_color_heredado() {
        Component msg = MiniMessage.miniMessage().deserialize("<red>hola <b>mundo</b>");
        Component line = Centered.lines(msg).get(0);
        // el trozo en negrita sigue siendo rojo aunque el color venía del padre
        Component last = line.children().get(line.children().size() - 1);
        assertEquals("mundo", plain(last));
        assertEquals(NamedTextColor.RED, last.color());
    }

    @Test
    void H1_CA2_linea_mas_ancha_que_el_chat_no_lleva_espacios() {
        String larga = "W".repeat(80);
        assertEquals(0, Centered.spaces(Centered.width(larga, false)));
        assertEquals(larga, plain(Centered.lines(Component.text(larga)).get(0)));
    }
}

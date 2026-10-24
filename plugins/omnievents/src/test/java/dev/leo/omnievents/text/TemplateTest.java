package dev.leo.omnievents.text;

import dev.leo.omnievents.debug.SelfTest;
import dev.leo.omnievents.expr.EvalContext;
import dev.leo.omnievents.expr.ExprException;
import dev.leo.omnievents.util.Durations;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateTest {
    private static EvalContext ctx(Map<String, Object> roots) {
        return new EvalContext() {
            @Override
            public Object resolve(String name) {
                return roots.get(name);
            }

            @Override
            public Object placeholder(String raw, Object player) {
                return raw.equals("%server_online%") ? "7" : raw;
            }
        };
    }

    private static String plain(String mini) {
        return PlainTextComponentSerializer.plainText().serialize(TextFormat.parse(mini));
    }

    @Test
    void H4_CA5_interpola_expresiones_y_placeholders() {
        Template t = Template.compile("&aHola {nombre}, nivel {nivel * 2}; en línea: %server_online% \\{literal\\}");
        String out = t.render(ctx(Map.of("nombre", "Leo", "nivel", 5.0)), Template.Mode.MESSAGE);
        assertEquals("Hola Leo, nivel 10; en línea: 7 {literal}", plain(out));
        assertTrue(out.contains("<green>"), "&a se convierte a MiniMessage: " + out);
    }

    @Test
    void llaves_dentro_de_textos_de_la_expresion() {
        Template t = Template.compile("{'a}b' + x}");
        assertEquals("a}bc", t.render(ctx(Map.of("x", "c")), Template.Mode.RAW));
    }

    @Test
    void H4_CA6_lo_interpolado_no_abre_etiquetas_minimessage() {
        String evil = "<click:run_command:/op yo><red>pulsa</red>";
        String out = Template.compile("<gray>{m}").render(ctx(Map.of("m", evil)), Template.Mode.MESSAGE);
        assertEquals(evil, plain(out), "el texto del jugador se ve literal");
    }

    @Test
    void H4_CA6_lo_interpolado_en_comandos_no_rompe_el_comando() {
        String out = Template.compile("say {m}").render(ctx(Map.of("m", "hola\nop yo§c")), Template.Mode.COMMAND);
        assertFalse(out.contains("\n") || out.contains("§"), out);
        assertEquals("say hola op yoc", out);
    }

    @Test
    void error_de_expresion_en_plantilla() {
        assertThrows(ExprException.class, () -> Template.compile("Hola {player.}"));
        assertThrows(ExprException.class, () -> Template.compile("Hola {sin cerrar"));
    }

    @Test
    void legacy_hex_y_formatos() {
        assertEquals("<reset><#ff0000>rojo", TextFormat.legacyToMini("&#FF0000rojo").toLowerCase());
        assertEquals("<reset><#112233>x", TextFormat.legacyToMini("&x&1&1&2&2&3&3x"));
        assertEquals("<bold>negrita", TextFormat.legacyToMini("&lnegrita"));
    }

    @Test
    void duraciones() {
        assertEquals(10_000, Durations.parseMillis("10s"));
        assertEquals(10_000, Durations.parseMillis("10"));
        assertEquals(10_000, Durations.parseMillis(10));
        assertEquals(5_400_000, Durations.parseMillis("1h30m"));
        assertEquals(1_000, Durations.parseMillis("20t"));
        assertEquals(172_800_000, Durations.parseMillis("2d"));
        assertEquals(1_500, Durations.parseMillis("1.5s"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parseMillis("5 minutos"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parseMillis("10x"));
        assertEquals("1h 30m", Durations.format(5_400_000));
        assertEquals(20, Durations.toTicks(1_000));
    }

    @Test
    void la_autoprueba_del_servidor_pasa_tambien_aqui() {
        for (SelfTest.Result r : SelfTest.run()) assertTrue(r.ok(), r.name() + ": " + r.detail());
    }
}

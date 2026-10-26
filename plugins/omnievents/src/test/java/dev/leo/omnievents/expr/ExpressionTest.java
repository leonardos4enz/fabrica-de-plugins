package dev.leo.omnievents.expr;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionTest {
    private final TestContext ctx = new TestContext();

    private Object eval(String src) {
        return Expression.compile(src).eval(ctx);
    }

    private boolean test(String src) {
        return Expression.compile(src).test(ctx);
    }

    @Test
    void S015_H3_CA2_CA4_pick_e_is_number() {
        assertEquals("a", eval("pick(['a'])"));
        assertNull(eval("pick([])"));
        Object v = eval("pick('x', 'y', 'z')");
        assertTrue(java.util.Set.of("x", "y", "z").contains(v), String.valueOf(v));
        assertTrue(test("is_number('12.5')"));
        assertTrue(test("is_number(3)"));
        assertFalse(test("is_number('doce')"));
        assertFalse(test("is_number('')"));
    }

    @Test
    void H2_CA1_operadores_logicos_y_de_texto() {
        assertTrue(test("true && !false"));
        assertTrue(test("true and not false"));
        assertTrue(test("false || true"));
        assertTrue(test("false or true"));
        assertTrue(test("'Diamante' contains 'man'"));
        assertTrue(test("'Diamante' !contains 'oro'"));
        assertTrue(test("'/spawn vip' startsWith '/spawn'"));
        assertTrue(test("'hola.yml' endsWith '.yml'"));
        assertTrue(test("'abc123' matches '[a-z]+\\\\d+'"));
        assertTrue(test("'abc' !matches '\\\\d+'"));
        assertTrue(test("'STONE' in ['dirt', 'STONE']"));
        assertTrue(test("'gold' not in ['dirt', 'stone']"));
        assertTrue(test("'Hola' ~= 'HOLA'"));
        assertTrue(test("'Hola' !~= 'adios'"));
        assertTrue(test("'a' equals 'a'"));
        assertTrue(test("'A' equalsIgnoreCase 'a'"));
        assertTrue(test("2 != 3"));
    }

    @Test
    void H2_CA2_aritmetica_ternario_listas_e_indices() {
        assertEquals(14.0, eval("2 + 3 * 4"));
        assertEquals(20.0, eval("(2 + 3) * 4"));
        assertEquals(1.0, eval("10 % 3"));
        assertEquals(2.5, eval("5 / 2"));
        assertEquals(-3.0, eval("-3"));
        assertEquals(1.0, eval("-(-1)"));
        assertEquals("si", eval("3 > 2 ? 'si' : 'no'"));
        assertEquals("b", eval("false ? 'a' : true ? 'b' : 'c'"));
        assertEquals("y", eval("['x', 'y', 'z'][1]"));
        assertEquals("z", eval("['x', 'y', 'z'][-1]"));
        assertEquals("nivel 5", eval("'nivel ' + 5"));
        assertEquals(7.0, eval("'3' + 4"));
        assertEquals("34", eval("'3' + '4'"));
    }

    @Test
    void H2_CA3_precedencia() {
        assertTrue(test("true || false && false"), "a || b && c es a || (b && c)");
        assertFalse(test("(true || false) && false"));
        assertTrue(test("!false == true"), "!a == b es (!a) == b");
        assertTrue(test("1 + 2 == 3"), "la aritmética va antes que la comparación");
        assertTrue(test("2 * 3 > 5 && 1 < 2"));
        assertEquals(2.0, eval("10 - 4 - 4"), "la resta asocia a la izquierda");
        assertEquals(2.0, eval("16 / 4 / 2"));
    }

    @Test
    void H2_CA4_funciones() {
        assertEquals(3.0, eval("max(1, 3, 2)"));
        assertEquals(1.0, eval("min([4, 1, 9])"));
        assertEquals(3.14, eval("round(3.14159, 2)"));
        assertEquals("HOLA", eval("upper('hola')"));
        assertEquals(4.0, eval("len('hola')"));
        assertEquals(List.of("a", "b"), eval("split('a,b', ',')"));
        assertEquals("a-b", eval("join(['a', 'b'], '-')"));
        assertEquals("ola", eval("substr('hola', 1)"));
        assertEquals("Texto", eval("strip('&cTe<bold>xto')"));
        assertEquals("sí", eval("if(1 > 0, 'sí', 1 / 0)"), "if es perezoso: no evalúa la rama falsa");
        assertEquals(5.0, eval("clamp(9, 0, 5)"));
        assertEquals("1,234", eval("format(1234.4)"));
        assertEquals("x", eval("default(null, 'x')"));
        for (int i = 0; i < 50; i++) {
            double r = (double) eval("random(1, 3)");
            assertTrue(r >= 1 && r <= 3);
        }
    }

    @Test
    void H2_CA5_errores_de_sintaxis_con_columna() {
        ExprException e = assertThrows(ExprException.class, () -> Expression.compile("player.level >= "));
        assertTrue(e.column() > 0);
        e = assertThrows(ExprException.class, () -> Expression.compile("1 2"));
        assertEquals(3, e.column());
        e = assertThrows(ExprException.class, () -> Expression.compile("rnadom(1, 2)"));
        assertTrue(e.getMessage().contains("random"), e.getMessage());
        e = assertThrows(ExprException.class, () -> Expression.compile("max()"));
        assertTrue(e.getMessage().contains("argumentos"));
        e = assertThrows(ExprException.class, () -> Expression.compile("'sin cerrar"));
        assertEquals(1, e.column());
        e = assertThrows(ExprException.class, () -> Expression.compile("a & b"));
        assertTrue(e.getMessage().contains("&&"));
        assertThrows(ExprException.class, () -> Expression.compile("(1 + 2"));
        assertThrows(ExprException.class, () -> Expression.compile(""));
    }

    @Test
    void H2_CA7_nulos_no_revientan() {
        assertFalse(test("null < 5"));
        assertFalse(test("null > 5"));
        assertTrue(test("null == null"));
        assertTrue(test("desconocido == null"));
        assertNull(eval("desconocido.propiedad.otra"));
        assertFalse(test("desconocido contains 'x'"));
        assertEquals(5.0, eval("desconocido + 5"));
    }

    @Test
    void division_entre_cero_es_error_de_ejecucion() {
        Expression e = Expression.compile("1 / 0");
        assertThrows(ExprException.class, () -> e.eval(ctx));
    }

    @Test
    void H3_CA3_enums_se_comparan_con_texto_sin_mayusculas() {
        ctx.with("modo", GameMode.SURVIVAL);
        assertTrue(test("modo == 'survival'"));
        assertTrue(test("modo in ['creative', 'SURVIVAL']"));
        assertEquals("SURVIVAL", eval("modo.name"));
    }

    @Test
    void propiedades_de_mapas_listas_y_textos() {
        ctx.with("datos", Map.of("nivel", 7, "nombre", "Leo", "final_damage", 3));
        ctx.with("lista", List.of(1, 2, 3));
        assertEquals(7.0, ((Number) eval("datos.nivel")).doubleValue());
        assertEquals(3.0, ((Number) eval("datos.finalDamage")).doubleValue(), "final_damage y finalDamage son la misma propiedad");
        assertEquals(3.0, eval("lista.size"));
        assertEquals(3.0, eval("datos.nombre.length"));
        assertEquals("Leo", eval("datos['nombre']"));
        assertTrue(test("2 in lista"));
        assertTrue(test("datos contains 'NIVEL'"));
    }

    @Test
    void variables_y_placeholders() {
        ctx.vars.put("pvar.monedas", 10.0);
        ctx.vars.put("var.i", 2.0);
        ctx.placeholders.put("%vault_eco_balance%", "1500.5");
        ctx.placeholders.put("%player_name%", "Leo");
        assertTrue(test("pvar.monedas >= 10 && var.i == 2"));
        assertNull(eval("gvar.nada"));
        assertEquals(1500.5, eval("%vault_eco_balance%"));
        assertTrue(test("%vault_eco_balance% > 1000"));
        assertTrue(test("%player_name% == 'Leo'"));
        assertEquals(1.0, eval("7 % 3"), "un % entre espacios sigue siendo módulo");
    }

    @Test
    void H3_CA2_reflexion_solo_de_lectura() {
        ctx.with("obj", new Bean());
        assertEquals(42.0, ((Number) eval("obj.answer")).doubleValue());
        assertEquals(true, eval("obj.ready"));
        assertEquals(true, eval("obj.hasPermission('x')"));
        ExprException e = assertThrows(ExprException.class, () -> eval("obj.destroy()"));
        assertTrue(e.getMessage().contains("lectura"));
        assertNull(eval("obj.destroy"), "un método que no es de lectura no se ve como propiedad");
    }

    @Test
    void un_mundo_no_se_convierte_en_valor_de_registro() {
        // Regresión (revisión de código 001): World es Keyed en Paper y player.world.name daba 'OVERWORLD'.
        org.bukkit.World w = (org.bukkit.World) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{org.bukkit.World.class}, (proxy, m, args) -> switch (m.getName()) {
                    case "getName" -> "mundo_minero";
                    case "getKey" -> org.bukkit.NamespacedKey.minecraft("overworld");
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    case "toString" -> "World";
                    default -> null;
                });
        ctx.with("w", w);
        assertEquals("mundo_minero", eval("w.name"));
        assertTrue(eval("w") instanceof org.bukkit.World);
    }

    @Test
    void explain_muestra_los_valores() {
        ctx.with("nivel", 3);
        String s = Expression.compile("nivel >= 10").explain(ctx);
        assertTrue(s.contains("(3)") && s.endsWith("false"), s);
    }

    public static class Bean {
        public int getAnswer() {
            return 42;
        }

        public boolean isReady() {
            return true;
        }

        public boolean hasPermission(String p) {
            return true;
        }

        public boolean destroy() {
            throw new IllegalStateException("no debería llamarse");
        }
    }
}

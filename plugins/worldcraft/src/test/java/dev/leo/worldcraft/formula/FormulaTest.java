package dev.leo.worldcraft.formula;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 034 H1: el lenguaje de fórmulas (sintaxis de WorldEdit), sus errores y sus topes (plan §1.1). */
class FormulaTest {
    static final List<String> XYZ = List.of("x", "y", "z");

    static double calc(String text) throws FormulaException {
        return Formula.parse(text, List.of(), Formula.Limits.DEFAULTS).run(1).eval();
    }

    static double at(String text, double x, double y, double z) throws FormulaException {
        return Formula.parse(text, XYZ, Formula.Limits.DEFAULTS).run(1).eval(x, y, z);
    }

    static String error(String text) {
        return assertThrows(FormulaException.class, () -> Formula.parse(text, XYZ, Formula.Limits.DEFAULTS)).getMessage();
    }

    @Test
    void H1_CA1_numeros_y_precedencia_de_WorldEdit() throws Exception {
        assertEquals(7, calc("1 + 2 * 3"));
        assertEquals(9, calc("(1 + 2) * 3"));
        assertEquals(1028, calc("2^10 + sqrt(16)"));
        assertEquals(512, calc("2^3^2"), "la potencia va de derecha a izquierda");
        assertEquals(4, calc("-2^2"), "el signo va antes que la potencia, como WorldEdit");
        assertEquals(8, calc("2**3"));
        assertEquals(1, calc("7 % 3"));
        assertEquals(1500, calc("1.5e3"));
        assertEquals(0.5, calc(".5"));
        assertEquals(1, calc("3 > 2 && 2 >= 2"));
        assertEquals(0, calc("3 < 2 || 1 == 2"));
        assertEquals(1, calc("!0"));
        assertEquals(1, calc("0.1 + 0.2 ~= 0.3"));
        assertEquals(0, calc("0.1 + 0.2 == 0.3"));
        assertEquals(5, calc("1 ? 5 : 6"));
        assertEquals(6, calc("-1 ? 5 : 6"), "verdadero es mayor que 0");
        assertEquals(120, calc("5!"));
        assertEquals(8, calc("1 << 3"));
        assertEquals(-1, calc("~0"));
        assertEquals(3, calc("1 != 2 ? 3 : 4"), "!= no es factorial");
    }

    @Test
    void H1_CA1_variables_sentencias_y_bucles() throws Exception {
        assertEquals(6, calc("a = 3; a * 2"));
        assertEquals(10, calc("a = 1; a += 4; a *= 2; a"));
        assertEquals(4, calc("a = 1; b = a++; c = ++a; b + c"), "a++ da el viejo (1), ++a el nuevo (3)");
        assertEquals(55, calc("s = 0; for (i = 1, 10) { s += i }; s"), "for de WorldEdit, de 1 a 10");
        assertEquals(10, calc("s = 0; for (i = 0; i < 5; i++) { s += i }; s"));
        assertEquals(16, calc("n = 1; while (n < 10) n *= 2; n"));
        assertEquals(1, calc("n = 0; do { n++ } while (n < 1); n"), "do corre al menos una vez");
        assertEquals(4, calc("s = 0; for (i = 0; i < 10; i++) { if (i == 4) break; s++ }; s"));
        assertEquals(5, calc("s = 0; for (i = 0; i < 10; i++) { if (i % 2) continue; s++ }; s"));
        assertEquals(7, calc("if (1 > 2) a = 3; else a = 7; a"));
        assertEquals(42, calc("return 42; 5"));
        assertEquals(3, calc("x = 1; { x = 2; x = 3 }; x"));
    }

    @Test
    void H1_CA2_funciones_y_constantes() throws Exception {
        assertEquals(Math.PI, calc("pi"), 1e-12);
        assertEquals(Math.E, calc("e"), 1e-12);
        assertEquals(1, calc("true + false"));
        assertEquals(3, calc("max(1, 3, 2)"));
        assertEquals(-2, calc("min(4, -2)"));
        assertEquals(2, calc("mod(-1, 3)"), "mod da el resto positivo");
        assertEquals(-1, calc("-1 % 3"), "% es como el de Java y WorldEdit");
        assertEquals(1, calc("abs(-1)"));
        assertEquals(2, calc("cbrt(8)"), 1e-12);
        assertEquals(3, calc("floor(3.7)"));
        assertEquals(4, calc("ceil(3.1)"));
        assertEquals(4, calc("round(3.5)"));
        assertEquals(2, calc("log10(100)"), 1e-12);
        assertEquals(1, calc("ln(e)"), 1e-12);
        assertEquals(Math.atan2(1, 2), calc("atan2(1, 2)"), 1e-12);
        double r = calc("random()");
        assertTrue(r >= 0 && r < 1);
        double n = calc("randint(5)");
        assertTrue(n >= 0 && n < 5 && n == Math.floor(n));
        assertEquals(9, calc("megabuf(3, 9); megabuf(3)"));
        assertEquals(0, calc("megabuf(99)"), "lo que no se escribió lee 0");
        double p = calc("perlin(1, 0.3, 0.7, 0.1, 1, 3, 0.5)");
        assertTrue(Math.abs(p) < 2, "perlin: " + p);
        assertEquals(p, calc("perlin(1, 0.3, 0.7, 0.1, 1, 3, 0.5)"), "la misma semilla da lo mismo");
        assertNotEquals(p, calc("perlin(2, 0.3, 0.7, 0.1, 1, 3, 0.5)"));
        double v = calc("voronoi(1, 2.5, 0, 0, 1)");
        assertTrue(v >= -1 && v <= 1);
        double rm = calc("ridgedmulti(1, 0.3, 0.2, 0.1, 1, 4)");
        assertTrue(rm >= -1 && rm <= 1, "ridgedmulti: " + rm);
    }

    @Test
    void H1_CA2_rotate_y_swap_cambian_las_variables() throws Exception {
        assertEquals(1, at("swap(x, z); x", 0, 0, 1));
        assertEquals(1, at("rotate(x, z, pi/2); z", 1, 0, 0), 1e-12);
        assertEquals(0, at("rotate(x, z, pi/2); x", 1, 0, 0), 1e-12);
    }

    @Test
    void H1_CA1_las_entradas_y_lo_que_queda_en_ellas() throws Exception {
        Formula f = Formula.parse("x^2 + y^2 + z^2 < 1", XYZ, Formula.Limits.DEFAULTS);
        Formula.Run r = f.run(0);
        assertEquals(1, r.eval(0, 0, 0));
        assertEquals(0, r.eval(1, 0, 0), "en el borde es falso (< 1)");
        Formula d = Formula.parse("y -= 0.2; x *= 2", XYZ, Formula.Limits.DEFAULTS);
        Formula.Run dr = d.run(0);
        dr.eval(0.5, 0.5, 0.5);
        assertEquals(1, dr.input(0), 1e-12);
        assertEquals(0.3, dr.input(1), 1e-12);
        assertEquals(0.5, dr.input(2), 1e-12);
        Formula c = Formula.parse("n++", XYZ, Formula.Limits.DEFAULTS);
        Formula.Run cr = c.run(0);
        cr.eval(0, 0, 0);
        assertEquals(1, cr.eval(0, 0, 0), "las variables siguen de un bloque al siguiente, como WorldEdit");
    }

    @Test
    void H1_CA3_errores_con_donde_y_did_you_mean() {
        assertTrue(error("sinn(x)").contains("Unknown function 'sinn' at 1 (did you mean 'sin'?)"), error("sinn(x)"));
        assertTrue(error("x + q").contains("Unknown variable 'q' at 5"), error("x + q"));
        assertTrue(error("X < 1").contains("lowercase"), error("X < 1"));
        assertTrue(error("(x + 1").contains("Expected ')'"), error("(x + 1"));
        assertTrue(error("x + 1)").contains("Unexpected ')'") || error("x + 1)").contains("Expected ';'"), error("x + 1)"));
        assertTrue(error("query(x, y, z, 1, 0)").contains("not supported"), error("query(x, y, z, 1, 0)"));
        assertTrue(error("pi = 3").contains("constant"), error("pi = 3"));
        assertTrue(error("sin(1, 2)").contains("takes 1 value, not 2"), error("sin(1, 2)"));
        assertTrue(error("x $ 2").contains("Unexpected '$' at 3"), error("x $ 2"));
        assertTrue(error("x +").contains("ends too soon"), error("x +"));
        assertTrue(error("x = 1 y = 2").contains("Expected ';'"), error("x = 1 y = 2"));
        assertTrue(error("1 = 2").contains("Only a variable"), error("1 = 2"));
        assertTrue(error("switch (x) { }").contains("not supported"), error("switch (x) { }"));
        assertTrue(error("").contains("Missing formula"));
    }

    @Test
    void H1_CA4_un_bucle_sin_fin_se_corta_por_las_vueltas() throws Exception {
        Formula f = Formula.parse("while (1) { a++ }", List.of(), new Formula.Limits(1000, 1000, 16));
        assertThrows(Formula.TooManySteps.class, () -> f.run(0).eval());
        Formula ok = Formula.parse("for (i = 1, 999) { a++ }; a", List.of(), new Formula.Limits(1000, 1000, 16));
        assertEquals(999, ok.run(0).eval(), "las vueltas se cuentan por evaluación");
        Formula.Run r = ok.run(0);
        r.eval();
        assertDoesNotThrow(() -> r.eval(), "otra evaluación vuelve a contar desde 0");
    }

    @Test
    void H1_CA4_el_plazo_corta_el_calculo() throws Exception {
        Formula f = Formula.parse("for (i = 0; i < 1e9; i++) { a = sin(i) }", List.of(), new Formula.Limits(10_000_000, 1000, 16));
        long t0 = System.nanoTime();
        assertThrows(Formula.TimedOut.class, () -> f.run(0).deadline(System.nanoTime() + 20_000_000L).eval());
        assertTrue(System.nanoTime() - t0 < 2_000_000_000L, "se cortó cerca del plazo");
        assertThrows(Formula.TimedOut.class, () -> Formula.parse("1", List.of(), Formula.Limits.DEFAULTS).run(0).deadline(System.nanoTime() - 1).eval());
    }

    @Test
    void H1_CA4_largo_anidamiento_y_megabuf_acotados() throws Exception {
        String longText = "1" + "+1".repeat(600);
        assertTrue(assertThrows(FormulaException.class, () -> Formula.parse(longText, List.of(), Formula.Limits.DEFAULTS)).getMessage().contains("too long"));
        String deep = "(".repeat(200) + "1" + ")".repeat(200);
        assertTrue(assertThrows(FormulaException.class, () -> Formula.parse(deep, List.of(), Formula.Limits.DEFAULTS)).getMessage().contains("nested too deeply"));
        Formula m = Formula.parse("megabuf(100, 5); megabuf(100)", List.of(), new Formula.Limits(1000, 1000, 16));
        assertEquals(0, m.run(0).eval(), "fuera de megabuf_size no se escribe");
        Formula neg = Formula.parse("megabuf(-1, 5); megabuf(-1)", List.of(), Formula.Limits.DEFAULTS);
        assertEquals(0, neg.run(0).eval());
    }

    @Test
    void H1_un_run_por_hilo() throws Exception {
        Formula f = Formula.parse("n++", XYZ, Formula.Limits.DEFAULTS);
        assertSame(f.local(), f.local());
        Formula.Run[] other = new Formula.Run[1];
        Thread t = new Thread(() -> other[0] = f.local());
        t.start();
        t.join();
        assertNotSame(f.local(), other[0]);
    }

    /** H8 CA2: los ejemplos del README se leen y hacen lo que dicen. */
    @Test
    void H8_CA2_los_ejemplos_del_README() throws Exception {
        assertEquals(1, at("(0.75-sqrt(x^2+z^2))^2+y^2<0.25^2", 0.75, 0, 0), "el toro pasa por su anillo");
        assertEquals(0, at("(0.75-sqrt(x^2+z^2))^2+y^2<0.25^2", 0, 0, 0), "y no por el centro");
        assertEquals(1, at("y < 70 + 4*sin(x/6) + 4*cos(z/6)", 0, 73, 0));
        assertEquals(0, at("y < 70 + 4*sin(x/6) + 4*cos(z/6)", 0, 75, 0));
        assertEquals(1028, calc("2^10 + sqrt(16)"));
    }
}

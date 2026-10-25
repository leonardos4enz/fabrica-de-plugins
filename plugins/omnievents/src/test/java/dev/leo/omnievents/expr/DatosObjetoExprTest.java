package dev.leo.omnievents.expr;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** objeto.data.nombre y ttl() en el lenguaje (spec 009, H1 CA4 y H3 CA1). */
class DatosObjetoExprTest {
    private static Object eval(String src, TestContext ctx) {
        return Expression.compile(src).eval(ctx);
    }

    @Test
    void H3_CA1_objeto_data_nombre_lee_la_variable_del_objeto() {
        Object cofre = new Object();
        TestContext ctx = new TestContext().with("block", cofre);
        ctx.objectVars.put(cofre, new HashMap<>(Map.of("dueño", "Leo", "usos", 3.0)));
        Expression e = Expression.compile("block.data.dueño");
        assertInstanceOf(Node.DataRef.class, e.root());
        assertEquals("block.data.dueño", e.root().text());
        assertEquals("Leo", e.eval(ctx));
        assertEquals(true, eval("block.data.usos >= 3 && block.data.dueño == 'Leo'", ctx));
        assertNull(eval("block.data.nada", ctx));
        assertEquals("Leo", eval("default(block.data.dueño, 'nadie')", ctx));
        assertNull(eval("nada.data.x", ctx), "objeto nulo → nulo");
    }

    @Test
    void H3_CA1_sobre_algo_que_no_es_objeto_data_conserva_su_significado() {
        // Un mapa (o un evento de otro plugin con getData()) sigue leyéndose como propiedad.
        TestContext ctx = new TestContext().with("ev", Map.of("data", Map.of("nivel", 7.0)));
        assertEquals(7.0, eval("ev.data.nivel", ctx));
    }

    @Test
    void H1_CA4_ttl_recibe_una_variable_que_puede_caducar() {
        TestContext ctx = new TestContext();
        ctx.services.put("ttl:PLAYER:racha", 42.5);
        ctx.services.put("ttl:GLOBAL:bono", -1.0);
        assertEquals(42.5, eval("ttl(pvar.racha)", ctx));
        assertEquals(-1.0, eval("ttl(gvar.bono)", ctx));
        assertNull(eval("ttl(pvar.otra)", ctx));
        Object item = "espada";
        ctx.with("item", item).services.put("ttl_data:espada:ligada", 3.0);
        assertEquals(3.0, eval("ttl(item.data.ligada)", ctx));

        for (String bad : List.of("ttl(var.x)", "ttl(5)", "ttl(player.name)", "ttl('pvar.x')")) {
            ExprException ex = assertThrows(ExprException.class, () -> Expression.compile(bad), bad);
            assertTrue(ex.getMessage().contains("pvar.nombre"), ex.getMessage());
        }
    }
}

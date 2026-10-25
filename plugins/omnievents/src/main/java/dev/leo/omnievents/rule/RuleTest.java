package dev.leo.omnievents.rule;

import dev.leo.omnievents.expr.Expression;

import java.util.List;
import java.util.Map;

/**
 * Un caso de prueba de una regla (spec 010, H1): el contexto falso que entra y lo que se espera. Lo arma
 * {@link RuleLoader} al cargar y lo corre {@link RuleTester}.
 *
 * @param given entradas de given en orden (ruta con puntos → valor)
 * @param seed  semilla de random (0 por defecto)
 */
public record RuleTest(String name, String path, List<Map.Entry<String, Object>> given, long seed, Expect expect) {

    /** Una variable esperada al terminar: {@code ref} es su expresión (pvar.x, block.data.y); value null = no existe. */
    public record VarCheck(String key, Expression ref, Object value) {
    }

    /** Lo esperado (H2); null o vacío = no se comprueba. */
    public record Expect(Boolean fired, String branch, List<VarCheck> vars, List<String> messages, List<String> commands,
                         List<String> actions, Boolean cancelled, List<Expression> asserts) {
    }
}

package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.trigger.Triggers;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 010, H4 CA2: gradle corre las pruebas (tests:) de los ejemplos del plugin, como un CI de la fábrica. */
class EjemplosPruebasTest {
    @Test
    void H4_CA2_las_pruebas_de_los_ejemplos_pasan() {
        Problems p = new Problems();
        Map<String, Rule> rules = new RuleLoader(new Actions(null), new Triggers()).loadFolder(new File("src/main/resources/rules"), p);
        assertEquals(0, p.size(), p.all().stream().map(Problems.Problem::toString).collect(Collectors.joining("\n")));
        RuleTester.Summary s = new RuleTester(n -> rules.get(n.toLowerCase()), 1000, 16).runAll(rules.values());
        String failed = s.results().stream().filter(r -> !r.ok())
                .map(r -> r.rule().name() + " «" + r.test().name() + "»: " + r.failures() + "\n  " + String.join("\n  ", r.trace()))
                .collect(Collectors.joining("\n"));
        assertEquals(0, s.failed(), failed);
        long files = rules.values().stream().filter(r -> !r.tests().isEmpty()).map(Rule::file).distinct().count();
        assertTrue(s.results().size() >= 10 && files >= 4, "se esperaban al menos 10 casos en 4 archivos de ejemplo: " + s.results().size() + " en " + files);
    }
}

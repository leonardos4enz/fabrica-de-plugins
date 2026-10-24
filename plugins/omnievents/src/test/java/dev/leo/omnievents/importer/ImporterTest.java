package dev.leo.omnievents.importer;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.rule.Problems;
import dev.leo.omnievents.rule.Rule;
import dev.leo.omnievents.rule.RuleLoader;
import dev.leo.omnievents.trigger.Triggers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImporterTest {
    private final ConditionalEventsImporter imp = new ConditionalEventsImporter();

    @Test
    void H10_CA2_traduce_operadores_y_variables() {
        assertEquals("('/' + cmd.name) == '/checklevel'", imp.convertCondition("%main_command% == /checklevel"));
        assertEquals("args.size < 1", imp.convertCondition("%args_length% < 1"));
        assertEquals("block.type == 'DIAMOND_ORE' || block.type == 'STONE'", imp.convertCondition("%block% == DIAMOND_ORE or %block% == STONE"));
        assertEquals("item.name !contains player.name", imp.convertCondition("%item_name% !contains %player%"));
        assertEquals("message startsWith 'hola'", imp.convertCondition("%message% startsWith hola"));
        assertEquals("%vault_rank% ~= 'vip'", imp.convertCondition("%vault_rank% equalsIgnoreCase vip"));
        assertEquals("args[0] == 'x'", imp.convertCondition("%arg_1% == x"));
        assertEquals("('Mundo ' + player.name) == 'Mundo Leo'", imp.convertCondition("Mundo %player% == Mundo Leo"));
        // Todas las conversiones son expresiones válidas
        for (String c : new String[]{"%main_command% == /checklevel", "%item_name% !contains %player%", "%statistic_deaths% >= 5 and %player_level% < 3"}) {
            Expression.compile(imp.convertCondition(c));
        }
    }

    @Test
    void H10_CA1_CA2_importa_el_ejemplo_del_wiki_con_execute(@TempDir File dir) throws Exception {
        File ce = new File(dir, "ConditionalEvents");
        ce.mkdirs();
        Files.writeString(new File(ce, "config.yml").toPath(), """
                Events:
                  example:
                    type: player_command
                    conditions:
                    - "%main_command% == /checklevel"
                    - "%args_length% < 1 execute error1"
                    - "%parseother_{arg_1}_{player_online}% == no execute error2"
                    actions:
                      default:
                      - "cancel_event: true"
                      - "message: &aLevel of &e%arg_1% &ais: &e%otherplayer_level_{arg_1}%"
                      error1:
                      - "cancel_event: true"
                      - "message: &cYou must use &7/checklevel <player>"
                      error2:
                      - "cancel_event: true"
                      - "message: &cThat player is not online."
                      - "firework: colors:RED;type:BALL"
                    ignore_with_permission: conditionalevents.ignore.example
                    cooldown: 10
                """, StandardCharsets.UTF_8);
        File out = new File(dir, "rules");
        ConditionalEventsImporter.Report rep = new ConditionalEventsImporter().importFolder(ce, out);
        assertEquals(1, rep.events());
        assertEquals(1, rep.review(), "firework no se traduce y queda para revisar");
        String yml = Files.readString(new File(out, "config.yml").toPath(), StandardCharsets.UTF_8);
        assertTrue(yml.contains("REVISAR"), yml);

        Problems p = new Problems();
        Map<String, Rule> rules = new RuleLoader(new Actions(null), new Triggers()).loadFolder(out, p);
        assertEquals(0, p.errors(), p.all().stream().map(Object::toString).collect(Collectors.joining("\n")) + "\n" + yml);
        Rule r = rules.get("example");
        assertNotNull(r);
        assertEquals("player_command", r.trigger().name());
        assertEquals(10_000, r.cooldownMs());
        assertEquals(1, r.conditions().size(), "la condición sin execute queda como condición de la regla");
    }
}

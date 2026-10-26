package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.ActionException;
import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.action.Firework;
import dev.leo.omnievents.api.OmniRuleFireEvent;
import dev.leo.omnievents.expr.Functions;
import dev.leo.omnievents.trigger.ReceivedChatEvent;
import dev.leo.omnievents.trigger.Triggers;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 016: paridad con ConditionalEvents, tercera parte (detalles y ProtocolLib). */
class ParidadCe3Test {
    private final Triggers triggers = new Triggers();

    private static String dump(Problems p) {
        return p.all().stream().map(Problems.Problem::toString).collect(Collectors.joining("\n"));
    }

    private Map<String, Rule> load(String yaml, Problems p) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        RuleLoader loader = new RuleLoader(new Actions(null), triggers);
        Map<String, Rule> out = new LinkedHashMap<>();
        for (String name : y.getKeys(false)) {
            Rule r = loader.parse(name, "prueba.yml", y.getConfigurationSection(name), p.file("prueba.yml"));
            if (r != null) out.put(name.toLowerCase(), r);
        }
        return out;
    }

    private static String report(Map<String, Rule> rules) {
        RuleTester t = new RuleTester(n -> rules.get(n.toLowerCase()), 1000, 16);
        List<RuleTester.Result> res = new ArrayList<>();
        for (Rule r : rules.values()) res.addAll(t.run(r));
        return res.stream().filter(r -> !r.ok()).map(r -> r.test().name() + ": " + r.failures() + "\n  " + String.join("\n  ", r.trace())).collect(Collectors.joining("\n"));
    }

    @Test
    void H1_actionbar_con_duracion_effect_con_icono_y_shot_direction_cargan() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                aviso:
                  trigger: command
                  command: aviso
                  actions:
                    - actionbar: "<gold>Evento en 1 minuto;120"
                    - actionbar: "Con clave"
                      duration: 6s
                    - actionbar: "Con expresión;{20 * 3}"
                    - actionbar: "Sin duración: a;b"
                    - effect: "NIGHT_VISION;60;1;false;false"
                    - firework: "colors:YELLOW type:BALL power:0 shot_direction:-1,1;1,1;-1,1;0.5-1"
                    - firework: "colors:RED shot_direction:0;1;0"
                malo:
                  trigger: command
                  command: malo
                  actions:
                    - actionbar: "x"
                      duration: mucho
                    - firework: "colors:RED shot_direction:1,a;1;1"
                """, p);
        String all = dump(p);
        assertEquals(2, p.errors(), all);
        assertTrue(all.contains("malo.actions[0]") && all.contains("duración de actionbar inválida 'mucho'"), all);
        assertTrue(all.contains("malo.actions[1]") && all.contains("shot_direction: '1,a'"), all);
        assertEquals(7, rules.get("aviso").actions().size());
        // «texto;ticks»: el último trozo numérico (o una {expresión}) es la duración; lo demás es texto.
        assertTrue(Actions.ACTIONBAR_TICKS.matcher("Evento;120").matches());
        assertTrue(Actions.ACTIONBAR_TICKS.matcher("Evento;{20 * 3}").matches());
        assertFalse(Actions.ACTIONBAR_TICKS.matcher("Sin duración: a;b").matches());
        assertEquals(120, Actions.actionbarTicks("120"));
        assertEquals(120, Actions.actionbarTicks("6s"));
    }

    @Test
    void H1_CA3_shot_direction_como_conditionalevents() {
        Firework.ShotDirection d = Firework.ShotDirection.parse("-1,1;1,1;-1,1;0.5-0.5");
        for (int i = 0; i < 50; i++) {
            Vector v = d.roll();
            assertTrue(v.getX() >= -0.5 && v.getX() <= 0.5, v.toString());
            assertEquals(0.5, v.getY(), 1e-9);
            assertTrue(v.getZ() >= -0.5 && v.getZ() <= 0.5, v.toString());
        }
        // Un número solo por eje; sin velocidad, 1. Un negativo solo no es un rango.
        Vector v = Firework.ShotDirection.parse("0;2;-0.5").roll();
        assertEquals(new Vector(0, 2, -0.5), v);
        assertEquals(new Vector(0, 1, 0), Firework.ShotDirection.parse("0;1;0;-1").roll().multiply(-1));
        assertThrows(ActionException.class, () -> Firework.ShotDirection.parse("1;1"));
        assertThrows(ActionException.class, () -> Firework.ShotDirection.parse("1,x;1;1"));
    }

    @Test
    void H2_CA1_funciones_nuevas() {
        dev.leo.omnievents.expr.EvalContext ctx = new dev.leo.omnievents.expr.EvalContext() {
            private final Map<String, Object> vars = Map.of("args", List.of("a", "b", "c", "d"));

            @Override
            public Object resolve(String name) {
                return vars.get(name);
            }
        };
        java.util.function.Function<String, Object> eval = s -> dev.leo.omnievents.expr.Expression.compile(s).eval(ctx);
        assertEquals("b c d", eval.apply("join(slice(args, 1, 4), ' ')"));
        assertEquals("b c d", eval.apply("join(slice(args, 1, 99), ' ')"));
        assertEquals("", eval.apply("join(slice(args, 9, 12), ' ')"));
        assertEquals("ell", eval.apply("slice('hello', 1, 4)"));
        Object r = eval.apply("random(3, 3)");
        assertEquals(3.0, r);
        assertEquals(3.0, eval.apply("random_last()"));
        // relative: mirando al sur (yaw 0, +Z), adelante es +Z y la derecha es el oeste (-X).
        Location south = new Location(null, 10, 64, 10, 0, 0);
        Location l = Functions.relative(south, 2, 1, 3);
        assertEquals(9, l.getX(), 1e-9);
        assertEquals(67, l.getY(), 1e-9);
        assertEquals(12, l.getZ(), 1e-9);
        // Mirando al este (yaw -90, +X): adelante +X, derecha +Z.
        Location east = Functions.relative(new Location(null, 0, 0, 0, -90, 30), 1, 1, 0);
        assertEquals(1, east.getX(), 1e-9);
        assertEquals(1, east.getZ(), 1e-9);
        for (String f : List.of("world", "location", "near", "slice", "relative", "looking_block", "looking_entity", "is_outside", "random_last")) {
            assertNotNull(Functions.find(f), f);
        }
    }

    @Test
    void H4_player_receive_chat_en_tests_con_cancel() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                publicidad:
                  trigger: player_receive_chat
                  conditions: ["lower(message) contains 'compra en' && type == 'SYSTEM'"]
                  actions:
                    - cancel
                    - log: "oculto a {player.name}: {message}"
                  tests:
                    - given: {message: "Compra en mitienda.com", message_legacy: "&cCompra en mitienda.com", json: "{}", type: SYSTEM}
                      expect: {cancelled: true}
                    - given: {message: "hola", type: SYSTEM}
                      expect: {fired: false}
                """, p);
        assertEquals(0, p.errors(), dump(p));
        assertEquals("", report(rules));
        assertTrue(triggers.get("player_receive_chat").roots().containsAll(Set.of("message", "message_legacy", "json", "type", "player")));
        assertTrue(triggers.get("player_receive_chat").cancellable());
        assertTrue(triggers.get("player_receive_chat").async());
        assertEquals(ReceivedChatEvent.class, triggers.get("player_receive_chat").eventClass());
        // Sin ProtocolLib instalado: carga con aviso.
        Problems sin = new Problems();
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("r:\n  trigger: player_receive_chat\n  actions: [cancel]\n");
        new RuleLoader(new Actions(null), triggers).integrations(Set.of()).parse("r", "x.yml", y.getConfigurationSection("r"), sin.file("x.yml"));
        assertEquals(0, sin.errors(), dump(sin));
        assertTrue(dump(sin).contains("necesita ProtocolLib"), dump(sin));
    }

    @Test
    void H3_OmniRuleFireEvent() {
        OmniRuleFireEvent e = new OmniRuleFireEvent(false, "kits:diario", "command", null, "actions");
        assertEquals("kits:diario", e.getRule());
        assertEquals("command", e.getTrigger());
        assertNull(e.getPlayer());
        assertEquals("actions", e.getGroup());
        assertFalse(e.isAsynchronous());
        assertFalse(OmniRuleFireEvent.listened());
        assertTrue(new OmniRuleFireEvent(true, "r", "player_chat", null, "actions").isAsynchronous());
    }

    @Test
    void H5_registro_documenta_lo_nuevo() {
        Actions a = new Actions(null);
        assertTrue(a.doc("actionbar").keys().containsKey("duration"));
        assertTrue(a.doc("effect").value().contains("ícono"));
        assertTrue(a.doc("firework").value().contains("shot_direction"));
        assertTrue(triggers.get("player_receive_chat").description().contains("ProtocolLib"));
    }
}

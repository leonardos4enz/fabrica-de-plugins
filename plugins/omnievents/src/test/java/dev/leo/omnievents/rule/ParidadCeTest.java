package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.trigger.Triggers;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 014: lo que faltaba de ConditionalEvents (centered_message, firework, item_repair, cooldown_bypass). */
class ParidadCeTest {
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

    private static List<RuleTester.Result> run(Map<String, Rule> rules) {
        RuleTester t = new RuleTester(n -> rules.get(n.toLowerCase()), 1000, 16);
        List<RuleTester.Result> out = new ArrayList<>();
        for (Rule r : rules.values()) out.addAll(t.run(r));
        return out;
    }

    private static String report(List<RuleTester.Result> res) {
        return res.stream().filter(r -> !r.ok()).map(r -> r.test().name() + ": " + r.failures() + "\n  " + String.join("\n  ", r.trace())).collect(Collectors.joining("\n"));
    }

    /** Un jugador falso con permisos dados (set) y su valor. */
    private static Player player(boolean op, Map<String, Boolean> perms) {
        return (Player) Proxy.newProxyInstance(ParidadCeTest.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, m, args) -> switch (m.getName()) {
            case "isOp" -> op;
            case "isPermissionSet" -> perms.containsKey(String.valueOf(args[0]));
            // Como Bukkit: un permiso no declarado vale para los OP.
            case "hasPermission" -> perms.getOrDefault(String.valueOf(args[0]), op);
            case "getName" -> "Leo";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null;
        });
    }

    @Test
    void H1_H2_centered_message_y_firework_cargan_y_se_registran_en_tests() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                anuncio:
                  trigger: command
                  command: anuncio
                  actions:
                    - centered_message: "<gold><b>EVENTO</b>\\n<gray>Hola {player.name}"
                    - to: all
                      firework: "colors:RED,#FFAA00 type:STAR power:2 fade:WHITE trail:true flicker:{1 == 1}"
                  tests:
                    - given: {player.name: Leo}
                      expect:
                        messages: ["EVENTO\\nHola Leo"]
                        actions: [centered_message, firework]
                """, p);
        assertEquals(0, p.errors(), dump(p));
        assertEquals("", report(run(rules)));
    }

    @Test
    void H2_CA3_firework_mal_escrito_es_error_de_carga_con_sugerencia() throws Exception {
        Problems p = new Problems();
        load("""
                a:
                  trigger: command
                  command: a
                  actions:
                    - firework: "colors:REDD type:STAR"
                b:
                  trigger: command
                  command: b
                  actions:
                    - firework: "colors:RED tipe:STAR"
                c:
                  trigger: command
                  command: c
                  actions:
                    - firework: "type:STAR power:5"
                d:
                  trigger: command
                  command: d
                  actions:
                    - firework: "colors:RED power:7"
                """, p);
        String all = dump(p);
        assertEquals(4, p.errors(), all);
        assertTrue(all.contains("a.actions[0]") && all.contains("¿quisiste decir 'RED'?"), all);
        assertTrue(all.contains("¿quisiste decir 'type'?"), all);
        assertTrue(all.contains("necesita colors"), all);
        assertTrue(all.contains("power va de 0 a 3"), all);
    }

    @Test
    void H3_item_repair_con_tests_y_cancel() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                excalibur:
                  trigger: item_repair
                  conditions:
                    - "repair_type == 'ANVIL' && rename_text contains 'Excalibur'"
                  actions:
                    - cancel
                    - message: "<red>Nombre reservado: {rename_text}"
                  tests:
                    - name: reservado
                      given: {repair_type: ANVIL, rename_text: "La Excalibur", level_cost: 3}
                      expect: {cancelled: true, messages: ["Nombre reservado: La Excalibur"]}
                    - name: afiladora
                      given: {repair_type: GRINDSTONE, rename_text: ""}
                      expect: {fired: false}
                """, p);
        assertEquals(0, p.errors(), dump(p));
        assertEquals("item_repair", rules.get("excalibur").trigger().name());
        assertEquals("", report(run(rules)));
        assertTrue(triggers.get("item_repair").roots().containsAll(Set.of("item", "repair_type", "base_item", "addition", "rename_text", "level_cost")));
    }

    @Test
    void H4_CA1_permiso_por_regla_y_opcion() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                kit:
                  trigger: command
                  command: kit
                  cooldown: 1d
                  actions: ["message: hola"]
                vip:
                  trigger: command
                  command: vip
                  cooldown: 1d
                  cooldown_bypass: rangos.vip.kit
                  actions: ["message: hola"]
                """, p);
        assertEquals(0, p.errors(), dump(p));
        assertEquals("omnievents.bypasscooldown.kit", rules.get("kit").cooldownBypass());
        assertEquals("rangos.vip.kit", rules.get("vip").cooldownBypass());

        String kit = rules.get("kit").cooldownBypass();
        // Dado → lo salta; sin darlo, un OP no lo salta (un permiso no declarado vale para los OP en Bukkit).
        assertEquals("permiso " + kit, RuleManager.bypassReason(kit, false, player(false, Map.of(kit, true))));
        assertNull(RuleManager.bypassReason(kit, false, player(true, Map.of())));
        // Negado explícitamente → no.
        assertNull(RuleManager.bypassReason(kit, false, player(false, Map.of(kit, false))));
        // cooldown-bypass-op sigue funcionando.
        assertEquals("OP", RuleManager.bypassReason(kit, true, player(true, Map.of())));
        assertNull(RuleManager.bypassReason(kit, true, null));
    }

    @Test
    void H6_registro_documenta_lo_nuevo() {
        Actions a = new Actions(null);
        assertNotNull(a.doc("centered_message"));
        assertNotNull(a.doc("firework"));
        assertNotNull(RuleOptions.all().get("cooldown_bypass"));
    }
}

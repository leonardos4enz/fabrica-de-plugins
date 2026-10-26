package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.action.Slots;
import dev.leo.omnievents.action.TabCompletions;
import dev.leo.omnievents.trigger.Triggers;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 015: paridad con ConditionalEvents, segunda parte. */
class ParidadCe2Test {
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
    void H1_CA1_CA3_casillas_por_nombre_y_numero() {
        assertEquals(Slots.HAND, Slots.parse("hand"));
        assertEquals(40, Slots.parse("OFF_HAND"));
        assertEquals(39, Slots.parse("HELMET"));
        assertEquals(36, Slots.parse("boots"));
        assertEquals(8, Slots.parse(" 8 "));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Slots.parse("HELMT"));
        assertTrue(e.getMessage().contains("¿quisiste decir 'HELMET'?"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Slots.parse("41"));
    }

    @Test
    void H1_claves_nuevas_cargan_y_los_errores_dicen_ruta_y_sugerencia() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                kit:
                  trigger: command
                  command: kit
                  actions:
                    - give_item:
                        material: DIAMOND_SWORD
                        flags: HIDE_ENCHANTS, HIDE_ATTRIBUTES
                        durability: 100
                        item_model: "minecraft:stick"
                        slot: 0
                        slot_replace: true
                    - give_item: {material: PLAYER_HEAD, skull_owner: "{player.name}", slot: HELMET}
                    - remove_item: "PAPER;1;name:<green>Ticket;lorecontains:VIP"
                    - remove_item_slot: "OFF_HAND;1"
                  tests:
                    - given: {player.name: Leo}
                      expect:
                        actions: [give_item, give_item, remove_item, remove_item_slot]
                malo:
                  trigger: command
                  command: malo
                  actions:
                    - give_item: {material: STONE, flags: [HIDE_ENCHANT]}
                    - give_item: {material: STONE, slot: CASCO}
                    - remove_item: "PAPER;1;nombre:x"
                    - remove_item_slot: "MANO;1"
                """, p);
        String all = dump(p);
        assertEquals(4, p.errors(), all);
        assertTrue(all.contains("malo.actions[0]") && all.contains("¿quisiste decir 'HIDE_ENCHANTS'?"), all);
        assertTrue(all.contains("casilla desconocida 'CASCO'"), all);
        assertTrue(all.contains("filtro de remove_item desconocido 'nombre:x'"), all);
        assertTrue(all.contains("casilla desconocida 'MANO'"), all);
        assertEquals("", report(Map.of("kit", rules.get("kit"))));
    }

    @Test
    void H1_CA4_H2_mundo_y_pesca_cargan() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                guardian:
                  trigger: command
                  command: guardian
                  actions:
                    - summon:
                        entity: ZOMBIE
                        name: "<red>Guardián"
                        health: 40
                        amount: 2
                        scale: 1.5
                        equipment: "DIAMOND_HELMET,none,none,IRON_BOOTS"
                        hand_equipment: [IRON_SWORD, SHIELD]
                    - set_block: "~;~;~;OAK_STAIRS[facing=north]"
                    - particle: "FLAME;30"
                      location: "~;~2;~"
                      force: true
                      for_player: true
                    - sound: "ENTITY_WITHER_SPAWN;1;1"
                      location: "~;~;~"
                    - stop_sound: "mi_pack:musica"
                pesca:
                  trigger: player_fish
                  actions:
                    - set_caught_item: "DIAMOND;1;<aqua>Diamante"
                    - set_item: {material: GOLD_INGOT}
                muerte:
                  trigger: player_death
                  actions:
                    - keep_inventory: items
                    - keep_inventory: xp
                    - keep_inventory: "player.level > 10"
                malo:
                  trigger: command
                  command: malo
                  actions:
                    - summon: {name: x}
                    - summon: {entity: ZOMBIE, equipment: "A,B,C,D,E"}
                """, p);
        String all = dump(p);
        assertEquals(2, p.errors(), all);
        assertTrue(all.contains("necesita entity"), all);
        assertTrue(all.contains("equipment lleva hasta 4"), all);
        assertEquals(0, p.warnings(), all);
        assertNotNull(rules.get("pesca"));
    }

    @Test
    void H3_CA1_json_message_propia_con_escape_y_json_invalido() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                hola:
                  trigger: player_chat
                  actions:
                    - json_message: '{"text":"Dijiste: {message}","color":"gold","clickEvent":{"action":"run_command","value":"/spawn"}}'
                  tests:
                    - given: {message: "<red>hola"}
                      expect:
                        messages: ["Dijiste: <red>hola"]
                roto:
                  trigger: command
                  command: roto
                  actions:
                    - json_message: "esto no es JSON {"
                """, p);
        String all = dump(p);
        assertEquals(1, p.errors(), all);
        assertTrue(all.contains("roto.actions[0]") && all.contains("json_message no es JSON de texto válido"), all);
        // El texto del jugador no se interpreta como etiqueta (se ve <red> tal cual).
        assertEquals("", report(Map.of("hola", rules.get("hola"))));
        assertEquals("json_message", rules.get("hola").actions().get(0).type());
    }

    @Test
    void H5_disparadores_nuevos_en_tests_y_tab_complete() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                saltos:
                  trigger: player_statistic
                  conditions: ["statistic == 'JUMP' && new_value % 100 == 0"]
                  actions: ["message: <gold>{new_value} saltos"]
                  tests:
                    - given: {statistic: JUMP, previous_value: 99, new_value: 100}
                      expect: {messages: ["100 saltos"]}
                    - given: {statistic: JUMP, previous_value: 100, new_value: 101}
                      expect: {fired: false}
                ahogo:
                  trigger: player_air_change
                  conditions: ["new_air < old_air && new_air <= 60"]
                  actions: ["cancel", "actionbar: <red>¡Sin aire! {new_air}/{max_air}"]
                  tests:
                    - given: {old_air: 61, new_air: 60, max_air: 300}
                      expect: {cancelled: true, messages: ["¡Sin aire! 60/300"]}
                warp:
                  trigger: player_tab_complete
                  conditions: ["cmd.name == 'warp' && args.size <= 1"]
                  actions: ["tab_complete: set;spawn,tienda,arena"]
                  tests:
                    - given: {cmd: {name: warp}, args: [""], last: ""}
                      expect: {actions: [tab_complete]}
                mending:
                  trigger: item_mend
                  conditions: ["amount > 5"]
                  actions: ["cancel"]
                  tests:
                    - given: {amount: 6}
                      expect: {cancelled: true}
                malo:
                  trigger: player_tab_complete
                  actions: ["tab_complete: poner;a"]
                """, p);
        String all = dump(p);
        assertEquals(1, p.errors(), all);
        assertTrue(all.contains("modo de tab_complete desconocido 'poner'"), all);
        rules.remove("malo");
        assertEquals("", report(rules));
        assertTrue(triggers.get("player_statistic").roots().containsAll(Set.of("statistic", "previous_value", "new_value", "material", "entity_type")));
        assertTrue(triggers.get("player_air_change").roots().containsAll(Set.of("old_air", "new_air", "max_air")));
        assertTrue(triggers.get("player_tab_complete").roots().containsAll(Set.of("buffer", "cmd", "args", "last", "completions")));
        assertTrue(triggers.get("item_mend").roots().containsAll(Set.of("item", "amount", "orb")));
        assertTrue(triggers.get("item_mend").cancellable() && triggers.get("player_air_change").cancellable() && triggers.get("player_statistic").cancellable());
    }

    @Test
    void H5_CA3_tab_complete_como_conditionalevents() {
        List<String> cur = List.of("admin", "spawn");
        assertEquals("", TabCompletions.last("/warp "));
        assertEquals("sp", TabCompletions.last("/warp sp"));
        // set: solo las de la lista que empiezan por lo escrito, ordenadas.
        assertEquals(List.of("arena", "spawn", "tienda"), TabCompletions.apply("set", cur, List.of("tienda", "spawn", "arena"), ""));
        assertEquals(List.of("spawn"), TabCompletions.apply("set", cur, List.of("tienda", "spawn"), "SP"));
        assertEquals(List.of("admin", "spawn", "tienda"), TabCompletions.apply("add", cur, List.of("tienda", "spawn"), ""));
        assertEquals(List.of("spawn"), TabCompletions.apply("remove", cur, List.of("ADMIN"), ""));
        assertEquals(List.of(), TabCompletions.apply("clear", cur, List.of(), ""));
    }

    @Test
    void H6_CA3_registro_documenta_lo_nuevo() {
        Actions a = new Actions(null);
        for (String n : List.of("json_message", "remove_item_slot", "set_caught_item", "tab_complete")) assertNotNull(a.doc(n), n);
        assertTrue(a.doc("give_item").valueKeys().containsKey("slot"));
        assertTrue(a.doc("give_item").valueKeys().containsKey("skull_owner"));
        assertTrue(a.doc("summon").valueKeys().containsKey("equipment"));
        assertTrue(a.doc("particle").keys().containsKey("for_player"));
        assertTrue(a.doc("sound").keys().containsKey("location"));
        for (String f : List.of("pick", "block_at", "is_number")) assertNotNull(dev.leo.omnievents.expr.Functions.find(f), f);
    }
}

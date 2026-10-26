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
                      - "player_command_as_op: op %player%"
                    ignore_with_permission: conditionalevents.ignore.example
                    cooldown: 10
                """, StandardCharsets.UTF_8);
        File out = new File(dir, "rules");
        ConditionalEventsImporter.Report rep = new ConditionalEventsImporter().importFolder(ce, out);
        assertEquals(1, rep.events());
        assertEquals(1, rep.review(), "player_command_as_op no se traduce y queda para revisar (remove_item_slot sí desde la 015)");
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

    // Regresión (spec 014, F-01): dentro de una acción to_… el %player% de ConditionalEvents es el jugador del evento,
    // pero se traducía como player.name, que en OmniEvents es el destinatario: «Ana te saluda» llegaba como
    // «Beto te saluda» a Beto.
    @Test
    void F01_player_dentro_de_to_es_el_jugador_del_evento(@TempDir File dir) throws Exception {
        String yml = importa(dir, """
                Events:
                  saludo:
                    type: player_command
                    conditions:
                    - "%main_command% == /hola"
                    actions:
                      default:
                      - "to_range: 10;false: message: &e%player% &7te saluda, %to:player%"
                """);
        assertTrue(yml.contains("{source.name}"), yml);
        assertTrue(yml.contains("{player.name}"), "%to:player% es el destinatario: " + yml);
        assertTrue(yml.contains("player != source"), "false = sin el jugador del evento: " + yml);
    }

    private static String importa(File dir, String config) throws Exception {
        File ce = new File(dir, "ConditionalEvents");
        ce.mkdirs();
        Files.writeString(new File(ce, "config.yml").toPath(), config, StandardCharsets.UTF_8);
        File out = new File(dir, "rules");
        new ConditionalEventsImporter().importFolder(ce, out);
        String yml = Files.readString(new File(out, "config.yml").toPath(), StandardCharsets.UTF_8);
        Problems p = new Problems();
        new RuleLoader(new Actions(null), new Triggers()).loadFolder(out, p);
        assertEquals(0, p.errors(), p.all().stream().map(Object::toString).collect(Collectors.joining("\n")) + "\n" + yml);
        return yml;
    }

    /** Spec 014, H5: una configuración de CE que usa cada fila con equivalente carga sin errores y sin REVISAR. */
    @Test
    void S014_H5_CA1_CA3_traduce_cada_fila_con_equivalente(@TempDir File dir) throws Exception {
        File ce = new File(dir, "ConditionalEvents");
        ce.mkdirs();
        Files.writeString(new File(ce, "config.yml").toPath(), """
                Config:
                  to_condition_groups:
                    vips:
                    - "%player_has_permission_rango.vip% == yes"
                Events:
                  npc:
                    type: citizens_right_click_npc
                    conditions: ["%npc_id% == 3", "%npc_name% contains Guía"]
                    actions:
                      default: ["centered_message: &6&lBIENVENIDO %player%"]
                  zona:
                    type: wgevents_region_enter
                    conditions: ["%region% == spawn"]
                    actions:
                      default: ["firework: colors:RED,#FFAA00 type:STAR power:1 shot_direction:-1,1;1,1;-1,1;0.5-0.5 trail:true"]
                  reparar:
                    type: item_repair
                    cooldown: 30
                    conditions: ["%repair_type% == ANVIL", "%rename_text% contains Excalibur", "%item_lore_line_1% contains Rara"]
                    actions:
                      default:
                      - "cancel_event: true"
                      - "title: 10;40;10;&cReservado;%rename_text%"
                      - "json_message: {\\"text\\":\\"Clic aquí\\",\\"color\\":\\"red\\"}"
                      cooldown: ["message: &cEspera %time%s"]
                    prevent_cooldown_activation: [aviso]
                  mover:
                    type: item_move
                    conditions: ["%action_type% == PICKUP_ALL", "%cursor:item_name% contains Llave"]
                    actions:
                      default: ["to_condition: vips: message: &aun vip movió algo"]
                  telep:
                    type: player_teleport
                    conditions: ["%from_world% != %to_world%", "%to_y% > 100"]
                    actions:
                      default: ["playsound_resource_pack: mi_sonido;1;1", "throw_coordinate: 0;1.5;0", "keep_items: all"]
                  romper:
                    type: block_break
                    conditions: ["%playerarmor_helmet% == DIAMOND_HELMET", "%world_is_raining% == false"]
                    actions:
                      default:
                      - "set_event_xp: 0"
                      - "give_item: id:DIAMOND_SWORD;amount:1;name:&bEspada;lore:&7Línea 1|&7Línea 2;enchants:SHARPNESS-2|UNBREAKING-1;custom_model_data:7"
                      - "drop_item: location:%block_x%,%block_y%,%block_z%,%block_world%;id:GOLD_INGOT;amount:2"
                      - "set_block: location:%block_x%,%block_y%,%block_z%,%block_world%;id:STONE"
                      - "summon: location:0,64,0,world;entity:ZOMBIE"
                      - "particle: effect:DUST;255;0;0 amount:5 offset:0.1;0.1;0.1 speed:0"
                      - "execute_action_group: suerte:70;nada:30"
                      suerte: ["message: suerte"]
                      nada: ["message: nada"]
                  dano:
                    type: player_damage
                    conditions: ["%damager_type% == ZOMBIE"]
                    actions:
                      default: ["message: %damager_name%"]
                  armadura:
                    type: player_armor
                    conditions: ["%armor_type% == HELMET", "%equip_type% == EQUIP"]
                    actions:
                      default: ["message: ok"]
                  ataque:
                    type: player_attack
                    conditions: ["%attack_type% == ARROW", "%victim_health% < 5"]
                    actions:
                      default: ["to_target: give_potion_effect: POISON;120;1;true"]
                  clic:
                    type: player_command
                    conditions:
                    - "%main_command% == /premio execute dar{%cantidad%=5;%tipo%=oro}"
                    actions:
                      default: ["message: no"]
                      dar: ["message: recibes %cantidad% de %tipo%"]
                """, StandardCharsets.UTF_8);
        File out = new File(dir, "rules");
        ConditionalEventsImporter.Report rep = new ConditionalEventsImporter().importFolder(ce, out);
        String yml = Files.readString(new File(out, "config.yml").toPath(), StandardCharsets.UTF_8);
        assertEquals(0, rep.review(), yml + "\n" + rep.notes());
        Problems p = new Problems();
        Map<String, Rule> rules = new RuleLoader(new Actions(null), new Triggers()).loadFolder(out, p);
        assertEquals(0, p.errors(), p.all().stream().map(Object::toString).collect(Collectors.joining("\n")) + "\n" + yml);
        assertEquals("npc_click", rules.get("npc").trigger().name());
        assertEquals("region_enter", rules.get("zona").trigger().name());
        assertEquals("item_repair", rules.get("reparar").trigger().name());
        assertEquals("inventory_click", rules.get("mover").trigger().name());
        assertEquals("conditionalevents.bypasscooldown.reparar", rules.get("reparar").cooldownBypass());
        for (String s : new String[]{"npc.id == 3", "region.id == 'spawn'", "centered_message", "firework:", "item.lore[0]",
                "var.cooldown_left", "event.action", "cursor.name", "from.world.name", "to.y", "minecraft:mi_sonido", "velocity:",
                "player.helmet.type", "player.world.has_storm", "exp_to_drop = 0", "material: DIAMOND_SWORD", "SHARPNESS:2",
                "model:", "GOLD_INGOT", "summon: ZOMBIE;world;0;64;0", "particle: DUST;5;0.1;0.1;0.1;0;255;0;0", "weight: 70",
                "damager.type", "target.health", "var.cantidad = 5", "{var.cantidad}", "json_message:", "Clic aquí", "where:"}) {
            assertTrue(yml.contains(s), "falta «" + s + "» en\n" + yml);
        }
        assertTrue(rep.notes().stream().anyMatch(n -> n.contains("shot_direction")), rep.notes().toString());
    }

    @Test
    void S015_H6_CA1_CA2_traduce_lo_que_faltaba(@TempDir File dir) throws Exception {
        File ce = new File(dir, "ConditionalEvents");
        ce.mkdirs();
        Files.writeString(new File(ce, "config.yml").toPath(), """
                Events:
                  estadistica:
                    type: player_statistic
                    conditions: ["%statistic_name% == JUMP", "%new_value% > %previous_value%", "%entity% == none"]
                    actions:
                      default: ["message: saltaste %new_value% veces"]
                  aire:
                    type: player_change_air
                    actions:
                      default: ["message: aire"]
                  tab:
                    type: player_tab_complete
                    conditions: ["%main_command% == /warp", "%args_length% == 1"]
                    actions:
                      default: ["tab_complete: set;spawn,tienda", "tab_complete: remove;admin"]
                  pesca:
                    type: player_fish
                    conditions: ["%random_player% != %player%", "%is_number_{arg_1}% == true"]
                    actions:
                      default:
                      - "set_item: id:DIAMOND;amount:1;name:&bDiamante"
                      - "message: %randomword_hola-chau-buenas%"
                  muerte:
                    type: player_death
                    conditions: ["%block_at_10_64_{player_z}_world% == STONE"]
                    actions:
                      default: ["keep_items: items"]
                  kit:
                    type: player_command
                    conditions: ["%main_command% == /kit"]
                    actions:
                      default:
                      - "json_message: {\\"text\\":\\"Hola %player%\\",\\"color\\":\\"gold\\",\\"clickEvent\\":{\\"action\\":\\"run_command\\",\\"value\\":\\"/spawn\\"}}"
                      - "give_item: id:PLAYER_HEAD;amount:1;skull_owner:%player%;slot:HELMET;slot_replace:true"
                      - "give_item: id:DIAMOND_SWORD;flags:HIDE_ENCHANTS,HIDE_ATTRIBUTES;durability:10;item_model:minecraft:stick;slot:0"
                      - "remove_item: PAPER;1;name:&aTicket;lorecontains:VIP"
                      - "remove_item_slot: OFF_HAND;1"
                      - "summon: location:0,64,0,world;entity:ZOMBIE;custom_name:&cGuardián;health:40;amount:2;equipment:DIAMOND_HELMET,none,none,IRON_BOOTS;hand_equipment:IRON_SWORD,none"
                      - "particle: effect:FLAME amount:10 location:5;70;5;world force:true"
                      - "playsound: ENTITY_PLAYER_LEVELUP;1;1;10,64,10,world"
                      - "stopsound_resource_pack: mi_pack:musica"
                      - "set_block: location:1,64,1,world;id:OAK_STAIRS;block_data:facing=north"
                  viejo:
                    type: player_command
                    actions:
                      default: ["remove_item: WOOL;1;datavalue:14"]
                """, StandardCharsets.UTF_8);
        File out = new File(dir, "rules");
        ConditionalEventsImporter.Report rep = new ConditionalEventsImporter().importFolder(ce, out);
        String yml = Files.readString(new File(out, "config.yml").toPath(), StandardCharsets.UTF_8);
        // Solo datavalue (legado de 1.12) queda como REVISAR.
        assertEquals(1, rep.review(), yml + "\n" + rep.notes());
        assertTrue(yml.contains("REVISAR (ConditionalEvents) remove_item: WOOL;1;datavalue:14"), yml);
        Problems p = new Problems();
        Map<String, Rule> rules = new RuleLoader(new Actions(null), new Triggers()).loadFolder(out, p);
        assertEquals(0, p.errors(), p.all().stream().map(Object::toString).collect(Collectors.joining("\n")) + "\n" + yml);
        assertEquals("player_statistic", rules.get("estadistica").trigger().name());
        assertEquals("player_air_change", rules.get("aire").trigger().name());
        assertEquals("player_tab_complete", rules.get("tab").trigger().name());
        for (String s : new String[]{"statistic == 'JUMP'", "new_value > previous_value", "entity_type == 'none'", "tab_complete: set;spawn,tienda",
                "pick(players()).name != player.name", "is_number(args[0])", "pick([''hola'', ''chau'', ''buenas''])", "set_caught_item:",
                "block_at('world', 10, 64, %player_z%).type", "keep_inventory: items", "json_message:", "Hola {player.name}", "skull_owner: '{player.name}'",
                "slot: HELMET", "slot_replace: true", "HIDE_ATTRIBUTES", "durability: '10'", "item_model: minecraft:stick", "name:&aTicket;lorecontains:VIP",
                "remove_item_slot: OFF_HAND;1", "name: '&cGuardián'", "health: '40'", "equipment: DIAMOND_HELMET,none,none,IRON_BOOTS",
                "hand_equipment: IRON_SWORD,none", "location: world;5;70;5", "force: true", "location: world;10;64;10",
                "stop_sound: mi_pack:musica", "OAK_STAIRS[facing=north]"}) {
            assertTrue(yml.contains(s), "falta «" + s + "» en\n" + yml);
        }
    }
}

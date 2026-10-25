package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.action.ExecContext;
import dev.leo.omnievents.action.Sandbox;
import dev.leo.omnievents.menu.Menu;
import dev.leo.omnievents.trigger.Triggers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 013: menús de inventario y formularios de Bedrock, sin servidor. */
class MenusTest {
    @TempDir
    File dir;
    private final RuleLoader loader = new RuleLoader(new Actions(null), new Triggers());
    private final Problems p = new Problems();

    private Map<String, Rule> load(String yaml) throws IOException {
        Files.writeString(new File(dir, "menus.yml").toPath(), yaml, StandardCharsets.UTF_8);
        return loader.loadFolder(dir, p);
    }

    private String dump() {
        return p.all().stream().map(Problems.Problem::toString).collect(Collectors.joining("\n"));
    }

    private boolean has(String text) {
        return p.all().stream().anyMatch(x -> x.toString().contains(text));
    }

    private static String failures(Map<String, Rule> rules) {
        RuleTester.Summary s = new RuleTester(n -> rules.get(n.toLowerCase()), 1000, 16).runAll(rules.values());
        return s.results().stream().filter(r -> !r.ok()).map(r -> r.rule().name() + " «" + r.test().name() + "»: " + r.failures()
                + "\n  " + String.join("\n  ", r.trace())).collect(Collectors.joining("\n"));
    }

    private static ExecContext ctx(Map<String, Object> locals) {
        Map<String, Object> b = new HashMap<>();
        b.put("player", Map.of("name", "Ana"));
        b.put("menu", "tienda");
        ExecContext c = new ExecContext(null, b, new Sandbox(0, 100, 4));
        c.locals().putAll(locals);
        return c;
    }

    private static final String TIENDA = """
            abrir:
              trigger: command
              command: abrir
              actions:
                - open_menu: { menu: tienda, args: { descuento: 10 } }
            tienda:
              menu:
                title: "<green>Tienda de {player.name}"
                rows: 3
                fill: GRAY_STAINED_GLASS_PANE
                conditions:
                  - expr: "var.descuento >= 0"
                    deny: ["message: <red>No"]
                on_open: ["message: abierto"]
                on_close: ["message: cerrado"]
                bedrock: { content: "Elige" }
                items:
                  borde:
                    slot: "0-2"
                    item: "BLACK_STAINED_GLASS_PANE;1; "
                  bloqueado:
                    slot: 13
                    view: "var.vip != true"
                    item: "BARRIER;1;<red>Solo VIP"
                  vip:
                    slot: 13
                    item: "NETHER_STAR;1;<gold>Kit <b>VIP</b>"
                    click:
                      actions: ["message: kit"]
                  espada:
                    slots: [11]
                    material: DIAMOND_SWORD
                    name: "<aqua>Espada"
                    bedrock: { button: "Espada ({var.descuento}% menos)", image: "https://ejemplo.test/espada.png" }
                    left_click:
                      cost: { items: "EMERALD;5" }
                      actions: ["give_item: DIAMOND_SWORD"]
                      on_cost_fail: ["message: Te falta {var.cost_missing}"]
                      close: true
                      tests:
                        - given: { click: left, inventory: { EMERALD: 5 } }
                          expect: { branch: actions, actions: [give_item] }
                        - given: { click: left, inventory: { EMERALD: 1 } }
                          expect: { branch: on_cost_fail, messages: ["Te falta 4 × EMERALD"] }
                    right_click:
                      actions: ["message: precio {var.descuento}"]
                      tests:
                        - given: { click: right, var.descuento: 10 }
                          expect: { messages: ["precio 10"] }
                    shift_click:
                      actions: ["message: shift"]
            """;

    // ------------------------------------------------------------------ H1: el menú

    @Test
    void H1_CA1_CA3_el_menu_se_compila_con_sus_reglas_de_apertura_clics_y_cierre() throws IOException {
        Map<String, Rule> rules = load(TIENDA);
        assertEquals(0, p.size(), dump());
        Menu m = loader.menus().get("tienda");
        assertNotNull(m);
        assertEquals(3, m.rows());
        assertEquals(27, m.size());
        assertNotNull(m.fill());
        assertEquals(4, m.items().size());
        assertEquals(List.of(0, 1, 2), m.items().get(0).slots());
        assertEquals(List.of(11), m.items().get(3).slots());
        // Las reglas del menú se llaman como su ruta y van con las demás (H2 CA3).
        for (String n : List.of("tienda.menu", "tienda.menu.on_close", "tienda.menu.items.vip.click", "tienda.menu.items.espada.left_click",
                "tienda.menu.items.espada.right_click", "tienda.menu.items.espada.shift_click")) {
            assertTrue(rules.containsKey(n), n + " en " + rules.keySet());
        }
        assertEquals("menu_open", m.open().trigger().name());
        assertEquals("menu_close", m.close().trigger().name());
        assertEquals(1, m.open().conditions().size());
        assertEquals(1, m.open().actions().size(), "on_open son las acciones de la apertura");
        assertEquals(1, m.close().actions().size(), "on_close son las acciones del cierre");
        assertFalse(m.open().conditions().get(0).deny().isEmpty(), "el requisito para abrir tiene su deny");
        assertEquals(6, m.rules().size());
    }

    @Test
    void H1_CA4_H3_CA1_disposicion_con_view_y_botones_de_bedrock() throws IOException {
        load(TIENDA);
        Menu m = loader.menus().get("tienda");
        // Sin VIP se ve el bloqueado en la 13; con VIP, el ítem vip (el primero visible de la casilla).
        Map<Integer, Menu.Item> sin = m.layout(ctx(Map.of("descuento", 10.0)), (it, e) -> { throw e; });
        assertEquals("bloqueado", sin.get(13).key());
        assertEquals("espada", sin.get(11).key());
        assertEquals("borde", sin.get(2).key());
        assertNull(sin.get(5));
        Map<Integer, Menu.Item> con = m.layout(ctx(Map.of("descuento", 10.0, "vip", true)), (it, e) -> { throw e; });
        assertEquals("vip", con.get(13).key());
        // Botones: solo los visibles con clic, en orden de casilla; el texto sin formato o bedrock.button.
        List<Menu.Item> sinBotones = Menu.buttons(sin);
        assertEquals(List.of("espada"), sinBotones.stream().map(Menu.Item::key).toList());
        List<Menu.Item> botones = Menu.buttons(con);
        assertEquals(List.of("espada", "vip"), botones.stream().map(Menu.Item::key).toList());
        assertEquals("Espada (10% menos)", Menu.buttonText(botones.get(0), ctx(Map.of("descuento", 10.0))));
        assertEquals("Kit VIP", Menu.buttonText(botones.get(1), ctx(Map.of())));
        assertEquals("https://ejemplo.test/espada.png", botones.get(0).image());
        assertEquals("Elige", m.bedrockContent().source());
    }

    @Test
    void H1_CA4_un_view_mal_escrito_es_un_error_al_cargar() throws IOException {
        load("""
                m:
                  menu:
                    items:
                      a: { slot: 0, view: "funcion_que_no_existe() > 0", item: STONE }
                """);
        assertTrue(has("m.menu.items.a.view") && has("funcion_que_no_existe"), dump());
        assertFalse(loader.menus().containsKey("m"));
    }

    // ------------------------------------------------------------------ H2: el clic es una regla

    @Test
    void H2_CA1_gana_el_clic_mas_especifico_y_bedrock_usa_click_o_left_click() throws IOException {
        load(TIENDA);
        Menu m = loader.menus().get("tienda");
        Menu.Item espada = m.items().get(3), vip = m.items().get(2);
        assertEquals("tienda.menu.items.espada.left_click", espada.clickFor("left").name());
        assertEquals("tienda.menu.items.espada.right_click", espada.clickFor("right").name());
        assertEquals("tienda.menu.items.espada.shift_click", espada.clickFor("shift_left").name());
        assertEquals("tienda.menu.items.espada.shift_click", espada.clickFor("shift_right").name());
        assertNull(espada.clickFor("middle"), "sin click, el clic del medio no hace nada");
        assertEquals("tienda.menu.items.espada.left_click", espada.clickFor("bedrock").name(), "Bedrock: left_click si no hay click");
        assertEquals("tienda.menu.items.vip.click", vip.clickFor("middle").name(), "click recibe todo lo que no tiene uno propio");
        assertEquals("tienda.menu.items.vip.click", vip.clickFor("bedrock").name());
        // close: true solo en el clic que lo declara.
        assertTrue(espada.closes("left"));
        assertFalse(espada.closes("right"));
        assertFalse(vip.closes("left"));
    }

    @Test
    void H2_CA2_tipos_de_clic_de_bukkit() {
        assertEquals("left", Menu.clickType("LEFT"));
        assertEquals("right", Menu.clickType("RIGHT"));
        assertEquals("shift_left", Menu.clickType("SHIFT_LEFT"));
        assertEquals("shift_right", Menu.clickType("SHIFT_RIGHT"));
        assertEquals("middle", Menu.clickType("MIDDLE"));
        assertEquals("number", Menu.clickType("NUMBER_KEY"));
        assertEquals("drop", Menu.clickType("CONTROL_DROP"));
        // El doble clic llega después de un LEFT: no corre el clic dos veces (y se cancela igual).
        assertNull(Menu.clickType("DOUBLE_CLICK"));
        assertNull(Menu.clickType("CREATIVE"));
    }

    @Test
    void H2_CA3_las_pruebas_de_los_clics_corren_con_cost_y_click() throws IOException {
        Map<String, Rule> rules = load(TIENDA);
        assertEquals(0, p.size(), dump());
        assertEquals("", failures(rules));
        RuleTester.Summary s = new RuleTester(n -> rules.get(n.toLowerCase()), 1000, 16).runAll(rules.values());
        assertEquals(3, s.results().size());
        // La regla que abre el menú deja open_menu como efecto, sin abrir nada.
        assertTrue(rules.get("abrir").actions().get(0).type().equals("open_menu"));
    }

    @Test
    void H3_CA3_is_bedrock_en_tests_con_given_bedrock() throws IOException {
        Map<String, Rule> rules = load("""
                hola:
                  trigger: player_join
                  conditions: "is_bedrock()"
                  actions: ["message: hola bedrock"]
                  else: ["message: hola java"]
                  tests:
                    - given: { bedrock: true }
                      expect: { branch: actions, messages: ["hola bedrock"] }
                    - expect: { branch: else, messages: ["hola java"] }
                mal:
                  trigger: player_join
                  actions: ["message: x"]
                  tests:
                    - given: { bedrock: si }
                      expect: { fired: true }
                """);
        assertEquals("", failures(rules));
        assertTrue(has("given.bedrock") && has("true o false"), dump());
    }

    // ------------------------------------------------------------------ H4: validación

    @Test
    void H4_CA1_errores_con_ruta_y_sugerencia() throws IOException {
        Map<String, Rule> rules = load("""
                a:
                  menu:
                    titel: x
                    rows: 7
                    items:
                      uno: { slot: 27, item: STONE }
                b:
                  menu:
                    rows: 2
                    items:
                      uno: { slot: 3, item: DIAMOND_SWROD }
                      dos: { slot: "4-9", item: STONE, clik: {} }
                c:
                  trigger: command
                  menu:
                    items: {}
                d:
                  menu:
                    fill: VIDRIO
                    items:
                      uno:
                        slot: 0
                        item: STONE
                        click: { trigger: block_break, actions: [] }
                      dos:
                        slot: 1
                        item: STONE
                        click: { actoins: ["message: x"] }
                suelta:
                  trigger: menu_click
                  actions: ["message: x"]
                """);
        assertTrue(has("a.menu.titel: clave desconocida del menú (¿quisiste decir 'title'?)"), dump());
        assertTrue(has("a.menu.rows: rows va de 1 a 6"), dump());
        assertTrue(has("b.menu.items.uno.item: material desconocido 'DIAMOND_SWROD' (¿quisiste decir 'DIAMOND_SWORD'?)"), dump());
        assertTrue(has("b.menu.items.dos.clik: clave desconocida del ítem (¿quisiste decir 'click'?)"), dump());
        assertTrue(has("c.trigger: un menú no lleva trigger"), dump());
        assertTrue(has("d.menu.fill: material desconocido 'VIDRIO'"), dump());
        assertTrue(has("d.menu.items.uno.click.trigger: un clic no lleva trigger"), dump());
        assertTrue(has("d.menu.items.dos.click.actoins: opción desconocida (¿quisiste decir 'actions'?)"), dump());
        assertTrue(has("suelta.trigger: el disparador menu_click va dentro de un menú"), dump());
        // Con rows 7 inválido el menú a usa 3 filas para validar: la casilla 27 queda fuera.
        assertTrue(has("a.menu.items.uno.slot: la casilla 27 está fuera del menú (3 filas: de 0 a 26)"), dump());
        assertTrue(has("b.menu.items.dos.slot: la casilla 4-9 está fuera del menú (2 filas: de 0 a 17)") == false, "4-9 cabe en 2 filas");
        assertTrue(loader.menus().isEmpty() || !loader.menus().containsKey("a"), "un menú con errores no carga");
        assertFalse(rules.containsKey("suelta"));
    }

    @Test
    void H4_CA1_casilla_repetida_sin_view_es_un_aviso() throws IOException {
        load("""
                m:
                  menu:
                    items:
                      a: { slot: 4, item: STONE }
                      b: { slot: [3, 4], item: DIRT }
                      c: { slot: 5, item: DIRT, view: "true" }
                      d: { slot: 5, item: STONE }
                """);
        assertEquals(0, p.errors(), dump());
        assertTrue(has("m.menu.items.b: la casilla 4 ya la ocupa a sin view"), dump());
        assertFalse(has("m.menu.items.d"), "c tiene view: d puede verse en la 5");
        assertNotNull(loader.menus().get("m"));
    }

    @Test
    void H4_CA1_open_menu_a_un_menu_que_no_existe() throws IOException {
        Map<String, Rule> rules = load("""
                abrir:
                  trigger: command
                  command: abrir
                  actions: ["open_menu: tiendas"]
                tienda:
                  menu:
                    items:
                      a:
                        slot: 0
                        item: STONE
                        click: { actions: ["open_menu: otro_menu"] }
                dinamico:
                  trigger: command
                  command: din
                  actions: ["open_menu: '{var.cual}'"]
                """);
        assertTrue(has("abrir.actions[0]: open_menu: no hay un menú 'tiendas' (¿quisiste decir 'tienda'?)"), dump());
        assertTrue(has("tienda.menu.items.a.click.actions[0]: open_menu: no hay un menú 'otro_menu'"), dump());
        assertFalse(rules.containsKey("abrir"));
        assertFalse(loader.menus().containsKey("tienda"), "el menú con un clic roto no carga entero");
        assertFalse(rules.containsKey("tienda.menu"));
        assertTrue(rules.containsKey("dinamico"), "un nombre con {expresión} se revisa al correr");
    }

    @Test
    void H4_CA1_raices_del_menu() throws IOException {
        load("""
                m:
                  menu:
                    title: "{block.type}{player.name}{menu}{var.x}"
                    items:
                      a: { slot: 0, item: "STONE;1;{evento.nombre}", view: "slot > 0" }
                """);
        assertTrue(has("m.menu.items.a: 'evento' no existe en el disparador menu_open"), dump());
        assertTrue(has("m.menu.items.a: 'slot' no existe"), dump());
        assertFalse(has("'player' no existe") || has("'menu' no existe"), dump());
    }

    // ------------------------------------------------------------------ paquetes y plantillas (spec 011)

    @Test
    void menus_en_un_paquete_con_extends_y_busqueda_relativa() throws IOException {
        File pack = new File(dir, "tiendas");
        pack.mkdirs();
        Files.writeString(new File(pack, "pack.yml").toPath(), "version: 1.0.0\nparams: { precio: 3 }\n", StandardCharsets.UTF_8);
        Files.writeString(new File(pack, "menus.yml").toPath(), """
                base:
                  template: true
                  menu:
                    rows: 1
                    items:
                      comprar:
                        slot: 0
                        item: "EMERALD;1;Cuesta ${precio}"
                        click: { actions: ["message: compra por ${precio}"] }
                principal:
                  extends: base
                abrir:
                  trigger: command
                  command: tiendas
                  actions: ["open_menu: principal"]
                """, StandardCharsets.UTF_8);
        Map<String, Rule> rules = loader.loadFolder(dir, p);
        assertEquals(0, p.errors(), dump());
        Menu m = loader.menus().get("tiendas:principal");
        assertNotNull(m, loader.menus().keySet().toString());
        assertEquals("tiendas", m.pack());
        assertTrue(rules.containsKey("tiendas:principal.menu.items.comprar.click"), rules.keySet().toString());
        assertEquals("Cuesta 3", m.items().get(0).spec().name().source());
        assertSame(m, RuleLoader.resolveMenu("principal", "tiendas", loader.menus()));
        assertSame(m, RuleLoader.resolveMenu("tiendas:principal", null, loader.menus()));
        assertNull(RuleLoader.resolveMenu("principal", null, loader.menus()));
    }

    @Test
    void H5_CA2_el_ejemplo_de_menus_carga_y_sus_pruebas_pasan() {
        Problems q = new Problems();
        RuleLoader l = new RuleLoader(new Actions(null), new Triggers());
        Map<String, Rule> rules = l.loadFolder(new File("src/main/resources/rules"), q);
        assertEquals(0, q.size(), q.all().stream().map(Problems.Problem::toString).collect(Collectors.joining("\n")));
        assertNotNull(l.menus().get("tienda"));
        assertNotNull(l.menus().get("kits"));
        assertEquals("", failures(rules));
        long casos = rules.values().stream().filter(r -> r.file().endsWith("menus.yml")).mapToLong(r -> r.tests().size()).sum();
        assertTrue(casos >= 5, "casos en menus.yml: " + casos);
    }
}

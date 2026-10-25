package dev.leo.omnievents.menu;

import dev.leo.omnievents.action.ItemSpec;
import dev.leo.omnievents.expr.EvalContext;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.rule.Rule;
import dev.leo.omnievents.text.Template;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * Un menú compilado (spec 013, plan §1.1). El requisito para abrirlo y on_open son la regla open (menu_open), on_close
 * la regla close (menu_close) y cada clic una regla menu_click: todo pasa por el motor de reglas. Inmutable.
 */
public final class Menu {
    /** Tipos de clic que declara un ítem, del más específico al más general. */
    public static final List<String> CLICK_KEYS = List.of("left_click", "right_click", "shift_click", "click");

    /** Un ítem del menú: dónde va, cómo se ve, cuándo se ve y qué hace cada clic. */
    public record Item(String key, List<Integer> slots, ItemSpec spec, Expression view, Map<String, Rule> clicks,
                       Set<String> closing, Template button, String image) {
        /** La regla del clic de ese tipo (left, right, shift_left…, bedrock), o null si no hace nada. */
        public Rule clickFor(String type) {
            String specific = switch (type) {
                case "left", "bedrock" -> "left_click";
                case "right" -> "right_click";
                case "shift_left", "shift_right" -> "shift_click";
                default -> null;
            };
            if (type.equals("bedrock") && clicks.containsKey("click")) return clicks.get("click");
            Rule r = specific == null ? null : clicks.get(specific);
            return r != null ? r : clicks.get("click");
        }

        /** Si el clic de ese tipo cierra el menú (close: true). */
        public boolean closes(String type) {
            Rule r = clickFor(type);
            if (r == null) return false;
            for (Map.Entry<String, Rule> e : clicks.entrySet()) if (e.getValue() == r) return closing.contains(e.getKey());
            return false;
        }
    }

    private final String name;
    private final String file;
    private final String pack;
    private final Template title;
    private final int rows;
    private final ItemSpec fill;
    private final Rule open;
    private final Rule close;
    private final boolean bedrock;
    private final Template bedrockContent;
    private final List<Item> items;

    public Menu(String name, String file, String pack, Template title, int rows, ItemSpec fill, Rule open, Rule close,
                boolean bedrock, Template bedrockContent, List<Item> items) {
        this.name = name;
        this.file = file;
        this.pack = pack;
        this.title = title;
        this.rows = rows;
        this.fill = fill;
        this.open = open;
        this.close = close;
        this.bedrock = bedrock;
        this.bedrockContent = bedrockContent;
        this.items = List.copyOf(items);
    }

    public String name() {
        return name;
    }

    public String file() {
        return file;
    }

    public String pack() {
        return pack;
    }

    public Template title() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return rows * 9;
    }

    public ItemSpec fill() {
        return fill;
    }

    public Rule open() {
        return open;
    }

    public Rule close() {
        return close;
    }

    /** false: también a Bedrock se le muestra el inventario (bedrock.enabled: false). */
    public boolean bedrock() {
        return bedrock;
    }

    public Template bedrockContent() {
        return bedrockContent;
    }

    public List<Item> items() {
        return items;
    }

    /** Las reglas del menú: apertura, cierre y clics. */
    public List<Rule> rules() {
        List<Rule> out = new ArrayList<>();
        out.add(open);
        out.add(close);
        for (Item it : items) for (Rule r : it.clicks().values()) if (!out.contains(r)) out.add(r);
        return out;
    }

    /**
     * Qué ítem va en cada casilla (H1 CA4): en orden de declaración, el primero visible que tenga la casilla libre. Un
     * view que falla al evaluarse oculta el ítem y se informa a onError.
     */
    public Map<Integer, Item> layout(EvalContext ctx, BiConsumer<Item, RuntimeException> onError) {
        Map<Integer, Item> out = new TreeMap<>();
        for (Item it : items) {
            if (it.view() != null) {
                boolean visible;
                try {
                    visible = Values.truthy(it.view().eval(ctx));
                } catch (RuntimeException e) {
                    onError.accept(it, e);
                    visible = false;
                }
                if (!visible) continue;
            }
            for (int s : it.slots()) out.putIfAbsent(s, it);
        }
        return out;
    }

    /** Botones del formulario de Bedrock (H3 CA1): los ítems visibles con clic, una vez cada uno, en orden de casilla. */
    public static List<Item> buttons(Map<Integer, Item> layout) {
        List<Item> out = new ArrayList<>();
        for (Item it : layout.values()) if (it.clickFor("bedrock") != null && !out.contains(it)) out.add(it);
        return out;
    }

    /** Texto del botón de Bedrock: bedrock.button o el nombre del ítem, sin formato; si no hay, el material. */
    public static String buttonText(Item it, EvalContext ctx) {
        Template t = it.button() != null ? it.button() : it.spec().name();
        if (t == null) return it.spec().material() == null ? it.key() : it.spec().material().render(ctx, Template.Mode.RAW);
        return PlainTextComponentSerializer.plainText().serialize(t.component(ctx));
    }

    /** El tipo de clic de Bukkit con el nombre que ven las expresiones (plan §1.3); null si no es un clic del menú. */
    public static String clickType(String bukkit) {
        return switch (bukkit) {
            case "LEFT" -> "left";
            case "RIGHT" -> "right";
            case "SHIFT_LEFT" -> "shift_left";
            case "SHIFT_RIGHT" -> "shift_right";
            case "MIDDLE" -> "middle";
            case "NUMBER_KEY", "SWAP_OFFHAND" -> "number";
            case "DROP", "CONTROL_DROP" -> "drop";
            default -> null;
        };
    }
}

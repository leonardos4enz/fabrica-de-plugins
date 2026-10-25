package dev.leo.omnievents.trigger;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Un disparador: qué evento escucha, de dónde sale el jugador y qué raíces ofrece a las expresiones. */
public final class TriggerType {
    public enum Kind { EVENT, TIMER, CLOCK, CALL, COMMAND, SERVER_START, SERVER_STOP, CUSTOM }

    private final String name;
    private final Kind kind;
    private final String group;
    private final Class<? extends Event> eventClass;
    private final Function<Event, Object> player;
    private final BiConsumer<Event, Map<String, Object>> binder;
    private final Predicate<Event> filter;
    private final Set<String> roots;
    private final boolean async;
    private String description = "";

    private TriggerType(String name, Kind kind, String group, Class<? extends Event> eventClass, Function<Event, Object> player,
                        BiConsumer<Event, Map<String, Object>> binder, Predicate<Event> filter, Set<String> roots, boolean async) {
        this.name = name;
        this.kind = kind;
        this.group = group;
        this.eventClass = eventClass;
        this.player = player;
        this.binder = binder;
        this.filter = filter;
        this.roots = roots;
        this.async = async;
    }

    @SuppressWarnings("unchecked")
    public static <E extends Event> TriggerType event(String name, String group, Class<E> type, Function<E, ?> player,
                                                      BiConsumer<E, Map<String, Object>> binder, Predicate<E> filter,
                                                      boolean async, String... roots) {
        Set<String> r = new LinkedHashSet<>(List.of("event", "world", "location"));
        if (player != null) r.add("player");
        r.addAll(List.of(roots));
        return new TriggerType(name, Kind.EVENT, group, type, player == null ? e -> null : e -> player.apply((E) e),
                binder == null ? (e, m) -> { } : (e, m) -> binder.accept((E) e, m),
                filter == null ? e -> true : e -> filter.test((E) e), r, async);
    }

    public static TriggerType special(String name, Kind kind, String group, String... roots) {
        Set<String> r = new LinkedHashSet<>(List.of(roots));
        return new TriggerType(name, kind, group, null, e -> null, (e, m) -> { }, e -> true, r, false);
    }

    /** Disparador custom para una regla: clase de evento por nombre y ruta al jugador. */
    public static TriggerType custom(Class<? extends Event> type, Function<Event, Object> player) {
        Set<String> r = new LinkedHashSet<>(List.of("event", "player", "world", "location"));
        return new TriggerType("custom", Kind.CUSTOM, "otros", type, player, (e, m) -> { }, e -> true, r, !isSyncEvent(type));
    }

    private static boolean isSyncEvent(Class<? extends Event> type) {
        return !type.getSimpleName().startsWith("Async");
    }

    /** Descripción para el esquema y la referencia (spec 007). Se pone una vez, al registrar. */
    public TriggerType doc(String description) {
        this.description = description;
        return this;
    }

    public String description() {
        return description;
    }

    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    public String group() {
        return group;
    }

    public Class<? extends Event> eventClass() {
        return eventClass;
    }

    public boolean async() {
        return async;
    }

    public Set<String> roots() {
        return roots;
    }

    public boolean hasPlayer() {
        return roots.contains("player");
    }

    public boolean cancellable() {
        return eventClass != null && Cancellable.class.isAssignableFrom(eventClass);
    }

    public boolean accepts(Event e) {
        return eventClass != null && eventClass.isInstance(e) && filter.test(e);
    }

    /** Raíces del contexto para un evento concreto. */
    public Map<String, Object> bind(Event e) {
        Map<String, Object> m = new HashMap<>();
        m.put("event", e);
        Object p = player.apply(e);
        m.put("player", p);
        if (p instanceof Entity ent) {
            m.put("world", ent.getWorld());
            m.put("location", ent.getLocation());
        }
        binder.accept(e, m);
        if (p instanceof Player pl) m.putIfAbsent("item", pl.getInventory().getItemInMainHand());
        return m;
    }
}

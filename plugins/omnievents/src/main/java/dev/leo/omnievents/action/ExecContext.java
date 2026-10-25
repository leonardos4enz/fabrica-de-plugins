package dev.leo.omnievents.action;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.data.DataStore;
import dev.leo.omnievents.data.ObjectData;
import dev.leo.omnievents.expr.EvalContext;
import dev.leo.omnievents.expr.Scope;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.rule.Rule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Contexto de una ejecución: raíces del disparador (player, event, block…), variables locales compartidas y acceso a
 * los datos persistentes. Un contexto hijo (for_each, to:) cambia algunas raíces y comparte las locales.
 */
public final class ExecContext implements EvalContext {
    private final OmniEvents plugin;
    private final Map<String, Object> bindings;
    private final ExecContext parent;
    private final Map<String, Object> locals;
    private final Rule rule;

    public ExecContext(OmniEvents plugin, Rule rule, Map<String, Object> bindings) {
        this(plugin, rule, bindings, null, new HashMap<>());
    }

    private ExecContext(OmniEvents plugin, Rule rule, Map<String, Object> bindings, ExecContext parent, Map<String, Object> locals) {
        this.plugin = plugin;
        this.rule = rule;
        this.bindings = bindings;
        this.parent = parent;
        this.locals = locals;
    }

    /** Hijo con raíces cambiadas; comparte las variables locales. */
    public ExecContext child(Map<String, Object> overrides) {
        return new ExecContext(plugin, rule, overrides, this, locals);
    }

    /** Contexto nuevo (para call) con las mismas raíces y locales nuevas. */
    public ExecContext forCall(Rule target, Map<String, Object> args) {
        Map<String, Object> b = new HashMap<>(flatBindings());
        return new ExecContext(plugin, target, b, null, new HashMap<>(args));
    }

    public Map<String, Object> flatBindings() {
        Map<String, Object> out = parent == null ? new HashMap<>() : parent.flatBindings();
        out.putAll(bindings);
        return out;
    }

    public OmniEvents plugin() {
        return plugin;
    }

    public Rule rule() {
        return rule;
    }

    public Map<String, Object> locals() {
        return locals;
    }

    @Override
    public Object resolve(String name) {
        if (bindings.containsKey(name)) return bindings.get(name);
        if (parent != null) return parent.resolve(name);
        return switch (name) {
            case "server" -> Bukkit.getServer();
            case "rule" -> rule == null ? null : rule.name();
            case "online" -> (double) Bukkit.getOnlinePlayers().size();
            default -> null;
        };
    }

    @Override
    public Set<String> roots() {
        Set<String> s = parent == null ? new HashSet<>(Set.of("server", "rule", "online")) : new HashSet<>(parent.roots());
        s.addAll(bindings.keySet());
        return s;
    }

    /** Lo que está ligado como 'player' (un jugador, u otra entidad en un to: target). */
    public Object subject() {
        return Values.raw(resolve("player"));
    }

    @Override
    public Object player() {
        return subject();
    }

    public Player playerOrNull() {
        return subject() instanceof Player p ? p : null;
    }

    public Player requirePlayer() {
        Player p = playerOrNull();
        if (p == null) throw new ActionException("esta acción necesita un jugador y el disparador no tiene uno (usa 'to:')");
        if (!p.isOnline()) throw new ActionException("el jugador " + p.getName() + " ya no está conectado");
        return p;
    }

    public Entity entityOrNull() {
        Object s = subject();
        if (s instanceof Entity e) return e;
        return resolve("entity") instanceof Entity e ? e : null;
    }

    public CommandSender sender() {
        Object s = Values.raw(resolve("sender"));
        if (s instanceof CommandSender cs) return cs;
        return playerOrNull();
    }

    public Event event() {
        return Values.raw(resolve("event")) instanceof Event e ? e : null;
    }

    /** Lugar de referencia: el del jugador, o el del bloque/entidad del evento. */
    public Location location() {
        Entity e = entityOrNull();
        if (e != null) return e.getLocation();
        Object l = Values.raw(resolve("location"));
        if (l instanceof Location loc) return loc;
        if (Values.raw(resolve("block")) instanceof Block b) return b.getLocation().add(0.5, 0, 0.5);
        return null;
    }

    public World world() {
        Location l = location();
        if (l != null) return l.getWorld();
        return Values.raw(resolve("world")) instanceof World w ? w : Bukkit.getWorlds().get(0);
    }

    @Override
    public Object getVar(Scope scope, String name) {
        return switch (scope) {
            case LOCAL -> locals.get(name);
            case GLOBAL -> plugin.data().global().get(name);
            case PLAYER -> {
                Player p = playerOrNull();
                yield p == null ? null : plugin.data().player(p.getUniqueId()).get(name);
            }
        };
    }

    public void setVar(Scope scope, String name, Object value) {
        setVar(scope, name, value, 0);
    }

    /** Asigna (value null = borra); expireAt en ms de época, 0 = no caduca (spec 009). */
    public void setVar(Scope scope, String name, Object value, long expireAt) {
        DataStore.Holder holder = switch (scope) {
            case LOCAL -> null;
            case GLOBAL -> plugin.data().global();
            case PLAYER -> {
                // Regresión (spec 002, F-01): no se exige que siga conectado. Una acción anterior pudo expulsarlo
                // (filtro_palabras: kick y luego pvar.avisos = 0); sus datos siguen cargados hasta el tick siguiente
                // a la salida, que los guarda.
                Player p = playerOrNull();
                if (p == null) throw new ActionException("pvar necesita un jugador y el disparador no tiene uno (usa 'to:')");
                yield plugin.data().player(p.getUniqueId());
            }
        };
        if (holder == null) {
            if (value == null) locals.remove(name);
            else locals.put(name, DataStore.storable(value));
        } else {
            if (value == null) holder.remove(name);
            else holder.put(name, DataStore.storable(value), expireAt);
            // Spec 008: los estados que leen esta variable se revisan al final del tick.
            Player p = scope == Scope.PLAYER ? playerOrNull() : null;
            plugin.states().varChanged(scope, p == null ? null : p.getUniqueId(), name);
        }
    }

    @Override
    public Object objectVar(Object target, String name) {
        Object raw = Values.raw(target);
        return ObjectData.supports(raw) ? plugin.objectData().get(raw, name) : NOT_OBJECT;
    }

    @Override
    public Object placeholder(String raw, Object player) {
        Object p = player == null ? subject() : Values.raw(player);
        return plugin.placeholders().resolve(raw, p instanceof OfflinePlayer op ? op : null, this);
    }

    @Override
    public Object service(String name, Object... args) {
        Player p = playerOrNull();
        UUID id = p == null ? null : p.getUniqueId();
        return switch (name) {
            case "cooldown" -> plugin.rules().cooldownLeftSeconds(String.valueOf(args[0]), id);
            case "uses" -> (double) plugin.rules().uses(String.valueOf(args[0]), id);
            case "in_state" -> plugin.states().isIn(String.valueOf(args[0]), id);
            case "ttl" -> {
                Scope scope = (Scope) args[0];
                DataStore.Holder h = scope == Scope.GLOBAL ? plugin.data().global() : id == null ? null : plugin.data().player(id);
                yield h == null ? null : h.ttl(String.valueOf(args[1]));
            }
            case "ttl_data" -> ObjectData.supports(Values.raw(args[0])) ? plugin.objectData().ttl(args[0], String.valueOf(args[1])) : null;
            case "pvar_of" -> {
                Object target = Values.raw(args[0]);
                OfflinePlayer op = target instanceof OfflinePlayer o ? o : Bukkit.getPlayerExact(Values.str(target));
                yield op == null ? null : plugin.data().player(op.getUniqueId()).get(String.valueOf(args[1]));
            }
            default -> null;
        };
    }
}

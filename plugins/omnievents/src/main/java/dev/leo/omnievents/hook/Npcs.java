package dev.leo.omnievents.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NPC de Citizens (spec 012, H4) sin dependencia de compilación (plan §1.1): Citizens marca sus entidades con el
 * metadato NPC; el id se lee por una reflexión interna, resuelta una vez y nunca expuesta a las expresiones (II.1).
 */
public final class Npcs {
    private static Method registry, getNpc, getId;
    private static boolean resolved;

    private Npcs() {
    }

    public static boolean isNpc(Object o) {
        return o instanceof Entity e && e.hasMetadata("NPC");
    }

    /** npc.id, npc.name y npc.entity de un NPC; el id es nulo si no se pudo leer. */
    public static Map<String, Object> info(Entity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id(e));
        m.put("name", e.getName());
        m.put("entity", e);
        return m;
    }

    private static Double id(Entity e) {
        try {
            if (!resolved) {
                resolved = true;
                Plugin citizens = Bukkit.getPluginManager().getPlugin("Citizens");
                if (citizens == null) return null;
                Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI", true, citizens.getClass().getClassLoader());
                registry = api.getMethod("getNPCRegistry");
                getNpc = registry.getReturnType().getMethod("getNPC", Entity.class);
                getId = getNpc.getReturnType().getMethod("getId");
            }
            if (getId == null) return null;
            Object npc = getNpc.invoke(registry.invoke(null), e);
            return npc == null ? null : ((Number) getId.invoke(npc)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return null;
        }
    }
}

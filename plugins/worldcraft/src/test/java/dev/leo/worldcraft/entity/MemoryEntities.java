package dev.leo.worldcraft.entity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Entidades en memoria para las unitarias (como MemoryWorld para los bloques). El «NBT» de cada una es su nombre en
 * texto: así se ve que una copia trae lo suyo. Los chunks de slow tardan unos pasos en cargar.
 */
public final class MemoryEntities implements EntityWorld {
    public static final class E {
        public UUID id;
        public String type;
        public double x, y, z;
        public float yaw, pitch;
        public String name;
        public Set<Kind> kinds;
        public int[] face; // la pared de un colgante, o null
    }

    public final Map<UUID, E> all = new LinkedHashMap<>();
    public final Set<Long> slow = new HashSet<>();
    public int waits, spawned, removed;
    /** Se llama antes de quitar cada una (para ver el orden respecto de los bloques). */
    public Consumer<E> onRemove = e -> {
    };
    private int next = 1;

    public E add(String type, double x, double y, double z, String name, Kind... kinds) {
        E e = new E();
        e.id = new UUID(7, next++);
        e.type = type.contains(":") ? type : "minecraft:" + type;
        e.x = x;
        e.y = y;
        e.z = z;
        e.name = name;
        e.kinds = kinds.length == 0 ? Set.of() : Set.of(kinds);
        all.put(e.id, e);
        return e;
    }

    public List<E> at(String type) {
        List<E> out = new ArrayList<>();
        for (E e : all.values()) if (e.type.equals("minecraft:" + type)) out.add(e);
        return out;
    }

    @Override
    public boolean ready(int cx, int cz) {
        long k = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        if (slow.remove(k)) {
            waits++;
            return false;
        }
        return true;
    }

    @Override
    public List<EntityInfo> list(int cx, int cz) {
        List<EntityInfo> out = new ArrayList<>();
        for (E e : all.values()) {
            if (((int) Math.floor(e.x) >> 4) == cx && ((int) Math.floor(e.z) >> 4) == cz) {
                out.add(new EntityInfo(e.id, e.type, e.x, e.y, e.z, e.kinds));
            }
        }
        return out;
    }

    @Override
    public EntityCopy copy(UUID id) {
        E e = all.get(id);
        if (e == null) return null;
        String nbt = e.name + (e.face == null ? "" : "|" + e.face[0] + "," + e.face[1] + "," + e.face[2]);
        return new EntityCopy(e.type, e.x, e.y, e.z, e.yaw, e.pitch, nbt.getBytes(StandardCharsets.UTF_8), List.of(e.type));
    }

    @Override
    public String type(UUID id) {
        E e = all.get(id);
        return e == null ? null : e.type;
    }

    @Override
    public boolean remove(UUID id) {
        E e = all.get(id);
        if (e == null) return false;
        onRemove.accept(e);
        all.remove(id);
        removed++;
        return true;
    }

    @Override
    public UUID spawn(EntityCopy c) {
        String[] parts = new String(c.nbt(), StandardCharsets.UTF_8).split("\\|");
        E e = add(c.type(), c.x(), c.y(), c.z(), parts[0]);
        e.yaw = c.yaw();
        e.pitch = c.pitch();
        if (parts.length > 1) {
            String[] f = parts[1].split(",");
            e.face = c.face(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]));
        }
        spawned++;
        return e.id;
    }
}

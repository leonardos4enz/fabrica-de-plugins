package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.block.BlockRegistry;
import dev.leo.worldcraft.block.StateOps;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.config.Issues;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El registro de bloques del servidor y la tabla de estados de la sesión (plan 025 §1.2): de número a BlockData y de
 * vuelta, giros con BlockData.rotate/mirror y qué estados tienen datos de bloque (cofres, carteles). Hilo principal.
 */
public final class PaperBlocks implements BlockRegistry, StateOps {
    private final StateTable table = new StateTable();
    private final List<BlockData> data = new ArrayList<>();
    private final Map<BlockData, Integer> ids = new HashMap<>();
    private byte[] tile = new byte[64]; // 0 sin saber, 1 no, 2 sí
    private final List<String> blocks;
    private final int air;

    public PaperBlocks() {
        List<String> b = new ArrayList<>();
        for (Material m : Material.values()) if (m.isBlock() && !m.isLegacy()) b.add(m.getKey().toString());
        blocks = List.copyOf(b);
        air = id(Material.AIR.createBlockData());
    }

    public StateTable table() {
        return table;
    }

    public int air() {
        return air;
    }

    /** El número de un BlockData (lo agrega a la tabla si es nuevo). */
    public int id(BlockData d) {
        Integer i = ids.get(d);
        if (i != null) return i;
        int n = table.intern(d.getAsString());
        while (data.size() <= n) data.add(null);
        data.set(n, d.clone());
        ids.put(data.get(n), n);
        return n;
    }

    /** El BlockData de un número (los estados que llegan como texto, del diario o del analizador, se crean una vez). */
    public BlockData data(int id) {
        BlockData d = id < data.size() ? data.get(id) : null;
        if (d == null) {
            try {
                d = Bukkit.createBlockData(table.state(id));
            } catch (IllegalArgumentException e) {
                // Un estado de otra versión del juego que ya no existe: se pone aire (raro: diario viejo tras actualizar).
                d = Material.AIR.createBlockData();
            }
            while (data.size() <= id) data.add(null);
            data.set(id, d);
            ids.putIfAbsent(d, id);
        }
        return d;
    }

    public boolean isAir(int id) {
        return AIR.contains(table.material(id));
    }

    /** Si los bloques de ese estado guardan datos aparte (cofres, carteles, hornos…). */
    public boolean hasTile(int id) {
        if (id >= tile.length) tile = Arrays.copyOf(tile, Math.max(id + 1, tile.length * 2));
        if (tile[id] == 0) {
            boolean t;
            try {
                t = data(id).createBlockState() instanceof TileState;
            } catch (RuntimeException e) {
                t = false;
            }
            tile[id] = t ? (byte) 2 : 1;
        }
        return tile[id] == 2;
    }

    @Override
    public Collection<String> blocks() {
        return blocks;
    }

    @Override
    public String canonical(String state) {
        int b = state.indexOf('[');
        String id = b < 0 ? state : state.substring(0, b);
        Material m = Material.matchMaterial(id);
        if (m == null || !m.isBlock()) throw new IllegalArgumentException("unknown block " + id);
        if (b < 0) return m.createBlockData().getAsString();
        Map<String, String> all = StateTable.parseProperties(m.createBlockData().getAsString());
        for (Map.Entry<String, String> e : StateTable.parseProperties(state).entrySet()) {
            if (!all.containsKey(e.getKey())) {
                throw new IllegalArgumentException("'" + id + "' has no property '" + e.getKey() + "'" + Issues.hint(e.getKey(), all.keySet()));
            }
            try {
                Bukkit.createBlockData(m.getKey() + "[" + e.getKey() + "=" + e.getValue() + "]");
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("'" + e.getValue() + "' is not a value of '" + e.getKey() + "'");
            }
        }
        try {
            return Bukkit.createBlockData(m.getKey() + state.substring(b)).getAsString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid block state");
        }
    }

    @Override
    public Set<String> tag(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.contains(":") ? name : "minecraft:" + name);
        if (key == null) return null;
        Tag<Material> t = Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
        if (t == null) return null;
        return t.getValues().stream().map(m -> m.getKey().toString()).collect(Collectors.toSet());
    }

    @Override
    public Collection<String> tags() {
        List<String> out = new ArrayList<>();
        for (Tag<Material> t : Bukkit.getTags(Tag.REGISTRY_BLOCKS, Material.class)) out.add(((Keyed) t).getKey().toString());
        return out;
    }

    @Override
    public int rotate90(int state) {
        BlockData d = data(state).clone();
        d.rotate(StructureRotation.CLOCKWISE_90);
        return id(d);
    }

    @Override
    public int flip(int state, char axis) {
        if (axis == 'y') {
            try {
                return id(Bukkit.createBlockData(StateOps.flipY(table.state(state))));
            } catch (IllegalArgumentException e) {
                return state; // ese bloque no tiene la versión de arriba abajo
            }
        }
        BlockData d = data(state).clone();
        // FRONT_BACK cambia este por oeste; LEFT_RIGHT, norte por sur (como en las estructuras del juego).
        d.mirror(axis == 'x' ? Mirror.FRONT_BACK : Mirror.LEFT_RIGHT);
        return id(d);
    }
}

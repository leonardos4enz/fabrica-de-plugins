package dev.leo.worldcraft;

import dev.leo.worldcraft.block.BlockRegistry;
import dev.leo.worldcraft.block.StateOps;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.edit.EditWorld;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Un registro de bloques y un mundo en memoria para las unitarias (el registro real necesita el servidor: lección de
 * la 021). Las propiedades por defecto imitan a Minecraft en los pocos bloques con estado que usan las pruebas.
 */
public final class TestBlocks {
    private TestBlocks() {
    }

    /** Bloques sin estado y unos pocos con estado (con sus propiedades y valores por defecto, en orden alfabético). */
    static final Map<String, Map<String, List<String>>> BLOCKS = new LinkedHashMap<>();

    static {
        for (String b : List.of("air", "cave_air", "void_air", "stone", "andesite", "diorite", "granite", "dirt", "grass_block",
                "sand", "gravel", "bedrock", "barrier", "command_block", "glass", "white_wool", "red_wool", "blue_wool",
                "oak_planks", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks", "gold_block", "chest",
                "oak_sign", "chain_command_block", "repeating_command_block", "structure_block", "jigsaw",
                "end_portal_frame", "end_portal", "end_gateway", "nether_portal", "spawner", "light")) {
            BLOCKS.put("minecraft:" + b, Map.of());
        }
        Map<String, List<String>> stairs = new LinkedHashMap<>();
        stairs.put("facing", List.of("north", "south", "east", "west"));
        stairs.put("half", List.of("bottom", "top"));
        stairs.put("shape", List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"));
        stairs.put("waterlogged", List.of("false", "true"));
        BLOCKS.put("minecraft:oak_stairs", stairs);
        BLOCKS.put("minecraft:oak_log", Map.of("axis", List.of("y", "x", "z")));
        BLOCKS.put("minecraft:birch_log", Map.of("axis", List.of("y", "x", "z")));
    }

    public static final BlockRegistry REGISTRY = new BlockRegistry() {
        @Override
        public Collection<String> blocks() {
            return BLOCKS.keySet();
        }

        @Override
        public String canonical(String state) {
            int b = state.indexOf('[');
            String id = b < 0 ? state : state.substring(0, b);
            Map<String, List<String>> props = BLOCKS.get(id);
            if (props == null) throw new IllegalArgumentException("unknown block " + id);
            Map<String, String> given = StateTable.parseProperties(state);
            Map<String, String> out = new TreeMap<>();
            for (Map.Entry<String, List<String>> e : props.entrySet()) out.put(e.getKey(), e.getValue().get(0));
            for (Map.Entry<String, String> e : given.entrySet()) {
                List<String> values = props.get(e.getKey());
                if (values == null) throw new IllegalArgumentException("'" + id + "' has no property '" + e.getKey() + "'");
                if (!values.contains(e.getValue())) {
                    throw new IllegalArgumentException("'" + e.getValue() + "' is not a value of '" + e.getKey() + "'");
                }
                out.put(e.getKey(), e.getValue());
            }
            return StateTable.withProperties(id, new LinkedHashMap<>(out));
        }

        @Override
        public Set<String> tag(String name) {
            String n = name.contains(":") ? name : "minecraft:" + name;
            return switch (n) {
                case "minecraft:logs" -> Set.of("minecraft:oak_log", "minecraft:birch_log");
                case "minecraft:wool" -> Set.of("minecraft:white_wool", "minecraft:red_wool", "minecraft:blue_wool");
                default -> null;
            };
        }

        @Override
        public Collection<String> tags() {
            return List.of("minecraft:logs", "minecraft:wool");
        }
    };

    /** Giros de las escaleras y los troncos (lo demás no cambia), como haría BlockData.rotate/mirror. */
    public static StateOps ops(StateTable table) {
        return new StateOps() {
            @Override
            public int rotate90(int state) {
                return change(state, Map.of("north", "east", "east", "south", "south", "west", "west", "north"),
                        Map.of("x", "z", "z", "x"));
            }

            @Override
            public int flip(int state, char axis) {
                if (axis == 'y') return table.intern(StateOps.flipY(table.state(state)));
                return axis == 'x' ? change(state, Map.of("east", "west", "west", "east"), Map.of())
                        : change(state, Map.of("north", "south", "south", "north"), Map.of());
            }

            private int change(int state, Map<String, String> facing, Map<String, String> axis) {
                Map<String, String> p = new LinkedHashMap<>(table.properties(state));
                if (p.isEmpty()) return state;
                p.computeIfPresent("facing", (k, v) -> facing.getOrDefault(v, v));
                p.computeIfPresent("axis", (k, v) -> axis.getOrDefault(v, v));
                return table.intern(StateTable.withProperties(table.material(state), p));
            }
        };
    }

    /** Un mundo en memoria: todo es piedra bajo y = 0 y aire arriba, salvo lo que se ponga. */
    public static class MemoryWorld implements EditWorld {
        public final StateTable table;
        public final Map<Long, Integer> blocks = new HashMap<>();
        public final Map<Long, byte[]> tiles = new HashMap<>();
        public final Set<Long> loaded = new HashSet<>();
        /** Chunks que tardan en cargar: la primera vez que se piden dicen que no. */
        public final Set<Long> slow = new HashSet<>();
        public int sets, releases;
        public final int air, stone;

        public MemoryWorld(StateTable table) {
            this.table = table;
            air = table.intern("minecraft:air");
            stone = table.intern("minecraft:stone");
        }

        static long key(int x, int y, int z) {
            return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
        }

        @Override
        public String name() {
            return "world";
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 319;
        }

        @Override
        public int get(int x, int y, int z) {
            Integer s = blocks.get(key(x, y, z));
            return s != null ? s : y < 0 ? stone : air;
        }

        public String at(int x, int y, int z) {
            return table.state(get(x, y, z));
        }

        public void put(int x, int y, int z, String state) {
            blocks.put(key(x, y, z), table.intern(state));
        }

        @Override
        public void set(int x, int y, int z, int state) {
            sets++;
            blocks.put(key(x, y, z), state);
            tiles.remove(key(x, y, z));
        }

        @Override
        public byte[] tile(int x, int y, int z) {
            return tiles.get(key(x, y, z));
        }

        @Override
        public void restoreTile(int x, int y, int z, byte[] data) {
            tiles.put(key(x, y, z), data);
        }

        @Override
        public boolean ready(int cx, int cz) {
            long k = dev.leo.worldcraft.region.ChunkOrder.pack(cx, cz);
            if (slow.remove(k)) return false;
            loaded.add(k);
            return true;
        }

        @Override
        public void release(int cx, int cz) {
            releases++;
            loaded.remove(dev.leo.worldcraft.region.ChunkOrder.pack(cx, cz));
        }

        /** Todo lo puesto, como texto (para comparar mundos). */
        public Map<Long, String> snapshot() {
            Map<Long, String> out = new TreeMap<>();
            for (Map.Entry<Long, Integer> e : blocks.entrySet()) out.put(e.getKey(), table.state(e.getValue()));
            return out;
        }
    }
}

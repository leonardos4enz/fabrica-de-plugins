package dev.leo.worldcraft.snapshot;

import dev.leo.worldcraft.schem.Nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * El NBT de un chunk guardado por el juego desde 1.18 (plan 036 §1.2): secciones de 16×16×16 con su paleta de estados y
 * los índices empaquetados en longs (sin partir un valor entre dos), los biomes por celdas de 4×4×4 y los datos de bloque.
 * Java puro: corre en el hilo de esquemas.
 */
public final class ChunkNbt {
    /** La primera versión de datos con este formato (1.18, 21w43a). */
    public static final int MIN_DATA_VERSION = 2860;
    public static final String AIR = "minecraft:air";

    /** Lo que se pudo leer de un chunk. */
    public enum Kind { OK, OLD, UNFINISHED, BROKEN }

    /**
     * Una sección: blocks (4096 índices en palette, (y·16 + z)·16 + x) o null si toda es palette[0]; lo mismo para los
     * biomas (64 celdas, (y·4 + z)·4 + x), biomePalette null si la sección no los trae.
     */
    public record Section(String[] palette, int[] blocks, String[] biomePalette, int[] biomes) {
        public String block(int x, int y, int z) {
            return palette[blocks == null ? 0 : blocks[(y << 8) | (z << 4) | x]];
        }

        public String biome(int x, int y, int z) {
            if (biomePalette == null) return null;
            return biomePalette[biomes == null ? 0 : biomes[((y >> 2) << 4) | ((z >> 2) << 2) | (x >> 2)]];
        }
    }

    /** Un chunk leído; sections por la y de la sección; blockEntities con sus x, y, z, id y datos. */
    public record Data(Kind kind, String problem, int dataVersion, Map<Integer, Section> sections, List<Map<String, Object>> blockEntities) {
        static Data not(Kind kind, String problem) {
            return new Data(kind, problem, 0, Map.of(), List.of());
        }
    }

    private ChunkNbt() {
    }

    public static Data decode(byte[] nbt) {
        Map<String, Object> root;
        try {
            root = Nbt.read(nbt).tag();
        } catch (Nbt.NbtException e) {
            return Data.not(Kind.BROKEN, e.getMessage());
        }
        // Algunos guardan el chunk dentro de «Level» (antes de 1.18): eso es viejo.
        Long dv = Nbt.integer(root, "DataVersion");
        if (dv == null || dv < MIN_DATA_VERSION || root.containsKey("Level")) return Data.not(Kind.OLD, "");
        String status = Nbt.string(root, "Status");
        if (status == null || !(status.equals("full") || status.equals("minecraft:full"))) return Data.not(Kind.UNFINISHED, String.valueOf(status));
        Map<Integer, Section> sections = new TreeMap<>();
        try {
            Nbt.TagList list = Nbt.list(root, "sections");
            if (list != null) {
                for (Object o : list.items()) {
                    if (!(o instanceof Map<?, ?>)) throw new IllegalArgumentException("a section is not a compound");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> s = (Map<String, Object>) o;
                    Long y = Nbt.integer(s, "Y");
                    if (y == null) throw new IllegalArgumentException("a section has no Y");
                    Map<String, Object> bs = Nbt.compound(s, "block_states");
                    Map<String, Object> bi = Nbt.compound(s, "biomes");
                    // Una sección sin bloques (solo luz) es aire, como al cargarla el juego.
                    String[] pal = bs == null ? new String[]{AIR} : blockPalette(Nbt.list(bs, "palette"));
                    int[] blocks = bs == null ? null : unpack(bs.get("data"), Math.max(4, bits(pal.length)), 4096, pal.length);
                    String[] bpal = bi == null ? null : biomePalette(Nbt.list(bi, "palette"));
                    int[] biomes = bpal == null ? null : unpack(bi.get("data"), bits(bpal.length), 64, bpal.length);
                    sections.put(y.intValue(), new Section(pal, blocks, bpal, biomes));
                }
            }
        } catch (IllegalArgumentException e) {
            return Data.not(Kind.BROKEN, e.getMessage());
        }
        List<Map<String, Object>> bes = new ArrayList<>();
        Nbt.TagList be = Nbt.list(root, "block_entities");
        if (be != null) {
            for (Object o : be.items()) {
                if (!(o instanceof Map<?, ?>)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                if (Nbt.string(m, "id") != null && Nbt.integer(m, "x") != null && Nbt.integer(m, "y") != null && Nbt.integer(m, "z") != null) bes.add(m);
            }
        }
        return new Data(Kind.OK, "", dv.intValue(), sections, bes);
    }

    /** Los bits por valor para n entradas (0 para una sola). */
    static int bits(int n) {
        return n <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(n - 1);
    }

    private static String[] blockPalette(Nbt.TagList list) {
        if (list == null || list.items().isEmpty()) throw new IllegalArgumentException("a section has no block palette");
        String[] out = new String[list.items().size()];
        for (int i = 0; i < out.length; i++) {
            if (!(list.items().get(i) instanceof Map<?, ?>)) throw new IllegalArgumentException("a block palette entry is not a compound");
            @SuppressWarnings("unchecked")
            Map<String, Object> e = (Map<String, Object>) list.items().get(i);
            out[i] = state(e);
        }
        return out;
    }

    /** {Name, Properties} → «minecraft:oak_log[axis=y]», las propiedades en orden. */
    static String state(Map<String, Object> e) {
        String name = Nbt.string(e, "Name");
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("a block palette entry has no name");
        if (!name.contains(":")) name = "minecraft:" + name;
        Map<String, Object> props = Nbt.compound(e, "Properties");
        if (props == null || props.isEmpty()) return name;
        StringBuilder sb = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, Object> p : new TreeMap<>(props).entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(p.getKey()).append('=').append(p.getValue());
        }
        return sb.append(']').toString();
    }

    private static String[] biomePalette(Nbt.TagList list) {
        if (list == null || list.items().isEmpty()) throw new IllegalArgumentException("a section has no biome palette");
        String[] out = new String[list.items().size()];
        for (int i = 0; i < out.length; i++) {
            if (!(list.items().get(i) instanceof String s)) throw new IllegalArgumentException("a biome palette entry is not text");
            out[i] = s.contains(":") ? s : "minecraft:" + s;
        }
        return out;
    }

    /**
     * Desempaqueta count valores de bits bits (64 / bits por long, sin partir uno entre dos longs, como el juego desde
     * 1.16); null si la sección es toda el primero (sin data). Un largo o un valor fuera de la paleta es un chunk roto.
     */
    static int[] unpack(Object raw, int bits, int count, int paletteSize) {
        if (paletteSize == 1 || bits == 0) return null;
        if (!(raw instanceof long[] data)) throw new IllegalArgumentException("a section with " + paletteSize + " entries has no data");
        int per = 64 / bits;
        if (data.length != (count + per - 1) / per) throw new IllegalArgumentException("packed data of the wrong length");
        long mask = (1L << bits) - 1;
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int v = (int) ((data[i / per] >>> ((i % per) * bits)) & mask);
            if (v >= paletteSize) throw new IllegalArgumentException("a block points outside the palette");
            out[i] = v;
        }
        return out;
    }

    /** Lo contrario de unpack (para las pruebas y el selftest). */
    public static long[] pack(int[] values, int bits) {
        int per = 64 / bits;
        long[] out = new long[(values.length + per - 1) / per];
        for (int i = 0; i < values.length; i++) out[i / per] |= ((long) values[i]) << ((i % per) * bits);
        return out;
    }

    /** Los datos de un bloque sin la posición ni el id (la forma de un .schem v3). */
    public static Map<String, Object> tileData(Map<String, Object> be) {
        Map<String, Object> d = new java.util.LinkedHashMap<>(be);
        for (String k : List.of("id", "x", "y", "z", "keepPacked")) d.remove(k);
        return d;
    }
}

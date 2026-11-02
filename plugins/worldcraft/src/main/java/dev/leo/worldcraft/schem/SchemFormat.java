package dev.leo.worldcraft.schem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leer los esquemas Sponge v1, v2 y v3 y escribir v3 (spec 027 H1 CA1, H2 CA2), desde la especificación pública. Todo
 * lo que viene del archivo se revisa antes de usarlo: un archivo malo da un motivo claro y nada más (H5 CA2, CA3).
 */
public final class SchemFormat {
    /** Las medidas máximas del formato (short sin signo). */
    public static final int MAX_SIDE = 0xFFFF;

    private SchemFormat() {
    }

    /** El tope descomprimido para un tope de bloques (plan 027 §2): los índices en el peor caso más los datos de bloque. */
    public static long maxUncompressed(long maxBlocks) {
        return 4 * maxBlocks + (32L << 20);
    }

    /** Lee un .schem (con o sin GZip). maxBlocks se mira en la cabecera, antes de reservar nada. */
    public static Schematic read(byte[] file, long maxBlocks) throws Nbt.NbtException {
        Nbt.Root root = Nbt.read(Nbt.gunzip(file, maxUncompressed(maxBlocks)));
        Map<String, Object> s = root.tag();
        if (!s.containsKey("Version") && Nbt.compound(s, "Schematic") != null) s = Nbt.compound(s, "Schematic");
        Long ver = Nbt.integer(s, "Version");
        if (ver == null) throw new Nbt.NbtException("this is not a Sponge schematic (.schem): it has no Version");
        int version = ver.intValue();
        if (version < 1 || version > 3) {
            throw new Nbt.NbtException("this .schem is version " + version + "; Worldcraft reads versions 1 to 3");
        }
        int w = side(s, "Width"), h = side(s, "Height"), l = side(s, "Length");
        long volume = (long) w * h * l;
        if (volume > maxBlocks) {
            throw new Nbt.NbtException("it has " + String.format(java.util.Locale.ROOT, "%,d", volume) + " blocks (" + w + "×" + h + "×" + l
                    + "); the limit is " + String.format(java.util.Locale.ROOT, "%,d", maxBlocks) + " (schematics.max_blocks)");
        }
        Long dv = Nbt.integer(s, "DataVersion");
        int dataVersion = dv == null ? -1 : dv.intValue();

        Map<String, Object> blocksTag = version == 3 ? Nbt.compound(s, "Blocks") : s;
        if (blocksTag == null) throw new Nbt.NbtException("it has no Blocks");
        Map<String, Object> paletteTag = Nbt.compound(blocksTag, "Palette");
        Object dataTag = blocksTag.get(version == 3 ? "Data" : "BlockData");
        if (paletteTag == null) throw new Nbt.NbtException("it has no block palette");
        if (!(dataTag instanceof byte[] data)) throw new Nbt.NbtException("it has no block data");
        String[] palette = palette(paletteTag, volume);
        int[] blocks = VarInts.decode(data, (int) volume);
        for (int b : blocks) {
            if (b >= palette.length || palette[b] == null) throw new Nbt.NbtException("the block data uses a palette entry that does not exist (" + b + ")");
        }

        Map<Integer, Map<String, Object>> tiles = new HashMap<>();
        int bad = 0;
        Nbt.TagList bes = Nbt.list(blocksTag, "BlockEntities");
        if (bes == null && version < 3) bes = Nbt.list(s, "TileEntities");
        if (bes != null) {
            for (Object o : bes.items()) {
                Map<String, Object> be = o instanceof Map ? cast(o) : null;
                int[] pos = be == null ? null : be.get("Pos") instanceof int[] p && p.length == 3 ? p : null;
                String id = be == null ? null : Nbt.string(be, "Id") != null ? Nbt.string(be, "Id") : Nbt.string(be, "id");
                if (pos == null || id == null || pos[0] < 0 || pos[1] < 0 || pos[2] < 0 || pos[0] >= w || pos[1] >= h || pos[2] >= l) {
                    bad++;
                    continue;
                }
                Map<String, Object> d;
                if (version == 3) {
                    d = Nbt.compound(be, "Data");
                    if (d == null) d = new LinkedHashMap<>();
                } else {
                    d = new LinkedHashMap<>(be);
                    d.remove("Pos");
                    d.remove("Id");
                    d.remove("id");
                    d.remove("ContentVersion");
                }
                Map<String, Object> norm = new LinkedHashMap<>();
                norm.put("Id", id);
                norm.put("Data", d);
                tiles.put(pos[0] + pos[2] * w + pos[1] * w * l, norm);
            }
        }
        Nbt.TagList ents = Nbt.list(s, "Entities");

        int[] offset = s.get("Offset") instanceof int[] o && o.length == 3 ? o.clone() : new int[3];
        Map<String, Object> meta = Nbt.compound(s, "Metadata");
        if (version < 3 && meta != null) {
            // WorldEdit 7.0–7.2 guarda en Offset la posición del mundo y lo relativo a quien copió en WEOffsetX/Y/Z.
            Long ox = Nbt.integer(meta, "WEOffsetX"), oy = Nbt.integer(meta, "WEOffsetY"), oz = Nbt.integer(meta, "WEOffsetZ");
            if (ox != null && oy != null && oz != null) offset = new int[]{ox.intValue(), oy.intValue(), oz.intValue()};
        }
        String name = meta == null ? null : Nbt.string(meta, "Name");
        String author = meta == null ? null : Nbt.string(meta, "Author");
        Long date = meta == null ? null : Nbt.integer(meta, "Date");
        List<Map<String, Object>> entities = SchemEntities.read(ents, version, new int[1]);
        return new Schematic(version, dataVersion, w, h, l, offset, palette, blocks, tiles, ents == null ? 0 : ents.items().size(),
                bad, name, author, date == null ? 0 : date, entities);
    }

    private static int side(Map<String, Object> s, String key) throws Nbt.NbtException {
        Long v = Nbt.integer(s, key);
        if (v == null) throw new Nbt.NbtException("it has no " + key);
        int n = (int) (v & 0xFFFF);
        if (n == 0) throw new Nbt.NbtException("it is empty (" + key + " is 0)");
        return n;
    }

    private static String[] palette(Map<String, Object> p, long volume) throws Nbt.NbtException {
        int max = -1;
        for (Map.Entry<String, Object> e : p.entrySet()) {
            if (!(e.getValue() instanceof Integer i) || i < 0) throw new Nbt.NbtException("the block palette is damaged ('" + e.getKey() + "')");
            max = Math.max(max, i);
        }
        if (max < 0) throw new Nbt.NbtException("the block palette is empty");
        if (max > Math.min(volume + p.size(), 1 << 22)) throw new Nbt.NbtException("the block palette is damaged (index " + max + ")");
        String[] out = new String[max + 1];
        for (Map.Entry<String, Object> e : p.entrySet()) {
            int i = (Integer) e.getValue();
            if (out[i] != null) throw new Nbt.NbtException("the block palette uses " + i + " twice");
            out[i] = e.getKey();
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }

    /** Escribe en v3 con GZip (plan 027 §1.4). pluginVersion va en Metadata.Worldcraft.Version. */
    public static byte[] write(Schematic s, String pluginVersion) {
        if (s.width() > MAX_SIDE || s.height() > MAX_SIDE || s.length() > MAX_SIDE) {
            throw new IllegalArgumentException("a schematic side can be at most " + MAX_SIDE + " blocks");
        }
        Map<String, Object> sc = new LinkedHashMap<>();
        sc.put("Version", 3);
        sc.put("DataVersion", s.dataVersion());
        Map<String, Object> meta = new LinkedHashMap<>();
        if (s.name() != null) meta.put("Name", s.name());
        if (s.author() != null) meta.put("Author", s.author());
        meta.put("Date", s.date());
        Map<String, Object> own = new LinkedHashMap<>();
        own.put("Version", pluginVersion);
        meta.put("Worldcraft", own);
        sc.put("Metadata", meta);
        sc.put("Width", (short) s.width());
        sc.put("Height", (short) s.height());
        sc.put("Length", (short) s.length());
        sc.put("Offset", s.offset().clone());
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, Object> palette = new LinkedHashMap<>();
        for (int i = 0; i < s.palette().length; i++) palette.put(s.palette()[i], i);
        blocks.put("Palette", palette);
        blocks.put("Data", VarInts.encode(s.blocks()));
        List<Object> bes = new ArrayList<>();
        int wl = s.width() * s.length();
        s.blockEntities().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            int i = e.getKey();
            Map<String, Object> be = new LinkedHashMap<>();
            be.put("Pos", new int[]{i % s.width(), i / wl, (i % wl) / s.width()});
            be.put("Id", e.getValue().get("Id"));
            Object d = e.getValue().get("Data");
            be.put("Data", d instanceof Map ? d : new LinkedHashMap<>());
            bes.add(be);
        });
        blocks.put("BlockEntities", new Nbt.TagList(Nbt.COMPOUND, bes));
        sc.put("Blocks", blocks);
        // Spec 031 H6 CA1: las entidades, con Pos relativa a la esquina mínima (lo de un archivo leído viene como double[]).
        if (!s.entityList().isEmpty()) {
            List<Object> ents = new ArrayList<>();
            for (Map<String, Object> e : s.entityList()) {
                Map<String, Object> out = new LinkedHashMap<>(e);
                if (out.get("Pos") instanceof double[] p) out.put("Pos", new Nbt.TagList(Nbt.DOUBLE, List.of(p[0], p[1], p[2])));
                ents.add(out);
            }
            sc.put("Entities", new Nbt.TagList(Nbt.COMPOUND, ents));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Schematic", sc);
        return Nbt.gzip(Nbt.write("", root));
    }
}

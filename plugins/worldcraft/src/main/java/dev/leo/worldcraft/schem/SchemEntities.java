package dev.leo.worldcraft.schem;

import dev.leo.worldcraft.entity.EntityCopy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Las entidades de un esquema (spec 031 H6, plan §1.5). En el archivo: v2 {@code {Pos, Id, …datos}} y v3
 * {@code {Pos, Id, Data}}, con {@code Pos} relativa a la esquina mínima. En Worldcraft: {@link EntityCopy} con el NBT
 * binario que lee Paper ({@code deserializeEntity}, que lo actualiza desde el {@code DataVersion}). Java puro.
 */
public final class SchemEntities {
    /** El tope del NBT de una entidad (un soporte con armadura llena de libros cabe de sobra). */
    public static final int MAX_NBT = 2 * 1024 * 1024;
    /** Facing de un marco (dirección 3D del juego): abajo, arriba, norte, sur, oeste, este. */
    private static final int[][] FACING_3D = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    /** facing de un cuadro (dirección 2D del juego): sur, oeste, norte, este. */
    private static final int[][] FACING_2D = {{0, 0, 1}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}};

    private SchemEntities() {
    }

    /** Las entidades de la lista del archivo, en la forma de v3 ({Pos double[3], Id, Data}); las malas se cuentan en bad[0]. */
    public static List<Map<String, Object>> read(Nbt.TagList list, int version, int[] bad) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (list == null) return out;
        for (Object o : list.items()) {
            Map<String, Object> e = o instanceof Map ? cast(o) : null;
            double[] pos = e == null ? null : doubles(e.get("Pos"));
            String id = e == null ? null : Nbt.string(e, "Id") != null ? Nbt.string(e, "Id") : Nbt.string(e, "id");
            if (pos == null || id == null) {
                bad[0]++;
                continue;
            }
            Map<String, Object> data;
            if (version >= 3) {
                data = Nbt.compound(e, "Data");
                data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
            } else {
                data = new LinkedHashMap<>(e);
                data.remove("Pos");
                data.remove("Id");
            }
            Map<String, Object> norm = new LinkedHashMap<>();
            norm.put("Pos", pos);
            norm.put("Id", id);
            norm.put("Data", data);
            out.add(norm);
        }
        return out;
    }

    /**
     * Una entidad del esquema para el portapapeles: posición relativa a quien pega (Pos + offset), la mirada de
     * Rotation y el NBT con id y DataVersion, sin uuid (cada copia tiene el suyo). dataVersion &lt;= 0 usa fallback.
     */
    public static EntityCopy toCopy(Map<String, Object> norm, int[] offset, int dataVersion, int fallbackDataVersion) {
        double[] p = (double[]) norm.get("Pos");
        String id = (String) norm.get("Id");
        Map<String, Object> data = new LinkedHashMap<>(cast(norm.get("Data")));
        data.remove("UUID");
        data.remove("UUIDMost");
        data.remove("UUIDLeast");
        data.put("id", id);
        data.put("DataVersion", dataVersion > 0 ? dataVersion : fallbackDataVersion);
        double x = p[0] + offset[0], y = p[1] + offset[1], z = p[2] + offset[2];
        data.put("Pos", new Nbt.TagList(Nbt.DOUBLE, List.of(x, y, z)));
        // F-02: el bloque de un colgante, junto a su Pos nueva (de WorldEdit vienen los dos del mundo de entonces; separados
        // más de 16 bloques, Paper registra «Block-attached entity at invalid position»).
        tile(data, x, y, z);
        float yaw = 0, pitch = 0;
        if (data.get("Rotation") instanceof Nbt.TagList r && r.items().size() == 2
                && r.items().get(0) instanceof Float a && r.items().get(1) instanceof Float b) {
            yaw = a;
            pitch = b;
        }
        List<String> contains = new ArrayList<>();
        types(data, id, contains, 0);
        return new EntityCopy(id, x, y, z, yaw, pitch, Nbt.gzip(Nbt.write("", data)), contains);
    }

    /** Lo que lleva (ella, lo montado y el bloque de un falling_block) para los prohibidos. */
    static void types(Map<String, Object> data, String id, List<String> out, int depth) {
        out.add(id);
        if (Nbt.compound(data, "BlockState") instanceof Map<String, Object> bs && Nbt.string(bs, "Name") != null) {
            out.add("block:" + Nbt.string(bs, "Name"));
        }
        Nbt.TagList riders = Nbt.list(data, "Passengers");
        if (riders == null || depth >= 16) return;
        for (Object o : riders.items()) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> r = cast(m);
                String rid = Nbt.string(r, "id");
                if (rid != null) types(r, rid, out, depth + 1);
            }
        }
    }

    /**
     * Una entidad del portapapeles (ya {@code baked}: posición relativa a quien copió) para el archivo v3: Pos relativa a
     * la esquina (offset), Data sin uuid, posición ni versión, con la mirada y la pared ya giradas.
     */
    public static Map<String, Object> fromCopy(EntityCopy c, int[] offset) throws Nbt.NbtException {
        Map<String, Object> data = new LinkedHashMap<>(Nbt.read(Nbt.gunzip(c.nbt(), MAX_NBT)).tag());
        data.remove("UUID");
        data.remove("DataVersion");
        String id = data.get("id") instanceof String s ? s : c.type();
        data.put("Rotation", new Nbt.TagList(Nbt.FLOAT, List.of(c.yaw(), c.pitch())));
        // F-01: WorldEdit pega un colgante (marco, cuadro) con la distancia entre su Pos y su TileX/Y/Z de Data: los dos
        // van relativos a quien copió y coherentes (sin Pos, WorldEdit le sumaba el origen dos veces y lo dejaba fuera).
        data.put("Pos", new Nbt.TagList(Nbt.DOUBLE, List.of(c.x(), c.y(), c.z())));
        tile(data, c.x(), c.y(), c.z());
        if (!c.turn().isIdentity()) {
            turnFacing(data, "Facing", FACING_3D, c);
            turnFacing(data, "facing", FACING_2D, c);
        }
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("Pos", new Nbt.TagList(Nbt.DOUBLE, List.of(c.x() - offset[0], c.y() - offset[1], c.z() - offset[2])));
        e.put("Id", id);
        e.put("Data", data);
        return e;
    }

    /** El bloque de un colgante en el bloque de esa posición (TileX/Y/Z hasta 1.21.4, block_pos desde 1.21.5). */
    private static void tile(Map<String, Object> data, double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        if (data.get("TileX") instanceof Integer && data.get("TileY") instanceof Integer && data.get("TileZ") instanceof Integer) {
            data.put("TileX", bx);
            data.put("TileY", by);
            data.put("TileZ", bz);
        }
        if (data.get("block_pos") instanceof int[] bp && bp.length == 3) data.put("block_pos", new int[]{bx, by, bz});
    }

    private static void turnFacing(Map<String, Object> data, String key, int[][] table, EntityCopy c) {
        if (!(data.get(key) instanceof Byte b) || b < 0 || b >= table.length) return;
        int[] v = c.face(table[b][0], table[b][1], table[b][2]);
        for (int i = 0; i < table.length; i++) {
            if (table[i][0] == v[0] && table[i][1] == v[1] && table[i][2] == v[2]) {
                data.put(key, (byte) i);
                return;
            }
        }
    }

    private static double[] doubles(Object o) {
        if (!(o instanceof Nbt.TagList l) || l.items().size() != 3) return null;
        double[] d = new double[3];
        for (int i = 0; i < 3; i++) {
            if (!(l.items().get(i) instanceof Number n)) return null;
            d[i] = n.doubleValue();
            if (!Double.isFinite(d[i])) return null;
        }
        return d;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }
}

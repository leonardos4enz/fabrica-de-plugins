package dev.leo.worldcraft.schem;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Los datos de bloque en su forma estándar (plan 027 §1.1): {@code 'N'}, la versión de datos del juego (int) y el NBT
 * {@code {Id, Data}} como en un esquema v3. Así un esquema cargado no pierde datos de bloque que el plugin no sabe poner
 * (cabezas, estandartes): se vuelven a guardar tal cual (H2 CA3). Los datos de la 025 empiezan por 'C' (contenedor) o
 * 'S' (cartel) y siguen valiendo.
 */
public record TileData(int dataVersion, String id, Map<String, Object> data) {
    public static final byte MARK = 'N';

    public enum Kind { CONTAINER, SIGN, OTHER }

    public static boolean is(byte[] tile) {
        return tile != null && tile.length > 5 && tile[0] == MARK;
    }

    public byte[] encode() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Id", id);
        root.put("Data", data);
        byte[] nbt = Nbt.write("", root);
        return ByteBuffer.allocate(5 + nbt.length).put(MARK).putInt(dataVersion).put(nbt).array();
    }

    public static TileData decode(byte[] tile) throws Nbt.NbtException {
        if (!is(tile)) throw new Nbt.NbtException("not block data");
        int dv = ByteBuffer.wrap(tile, 1, 4).getInt();
        Map<String, Object> root = Nbt.read(Arrays.copyOfRange(tile, 5, tile.length)).tag();
        Map<String, Object> d = Nbt.compound(root, "Data");
        return new TileData(dv, Nbt.string(root, "Id"), d == null ? new LinkedHashMap<>() : d);
    }

    /** Lo que Worldcraft sabe poner: lo de adentro de un contenedor y el texto de un cartel; lo demás, OTHER. */
    public Kind kind() {
        if (Nbt.list(data, "Items") != null) return Kind.CONTAINER;
        if (Nbt.compound(data, "front_text") != null || Nbt.compound(data, "back_text") != null || data.containsKey("Text1")) {
            return Kind.SIGN;
        }
        return Kind.OTHER;
    }
}

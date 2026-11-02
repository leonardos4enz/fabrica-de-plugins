package dev.leo.worldcraft.snapshot;

import dev.leo.worldcraft.schem.Nbt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

/** Arma archivos de región y chunks como los guarda el juego (solo para las pruebas). */
final class McaWriter {
    /** Un chunk: su NBT ya armado y cómo se comprime (1 gzip, 2 zlib, 3 nada, 4 LZ4; +128 = en un .mcc). */
    record Entry(int cx, int cz, byte[] nbt, int compression) {
    }

    private McaWriter() {
    }

    /** Un chunk terminado con las secciones dadas (y → sección) y datos de bloque. */
    static Map<String, Object> chunk(int dataVersion, String status, Map<Integer, Map<String, Object>> sections, List<Map<String, Object>> bes) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("DataVersion", dataVersion);
        root.put("Status", status);
        List<Object> secs = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Object>> e : new TreeMap<>(sections).entrySet()) {
            Map<String, Object> s = new LinkedHashMap<>(e.getValue());
            s.put("Y", (byte) (int) e.getKey());
            secs.add(s);
        }
        root.put("sections", new Nbt.TagList(Nbt.COMPOUND, secs));
        root.put("block_entities", new Nbt.TagList(bes.isEmpty() ? Nbt.END : Nbt.COMPOUND, new ArrayList<>(bes)));
        return root;
    }

    /** Una sección: palette de textos («minecraft:oak_log[axis=y]»), blocks (4096) o null si es uno solo; biomas (64) o null. */
    static Map<String, Object> section(String[] palette, int[] blocks, String[] biomePalette, int[] biomes) {
        Map<String, Object> s = new LinkedHashMap<>();
        Map<String, Object> bs = new LinkedHashMap<>();
        List<Object> pal = new ArrayList<>();
        for (String p : palette) pal.add(entry(p));
        bs.put("palette", new Nbt.TagList(Nbt.COMPOUND, pal));
        if (blocks != null) bs.put("data", ChunkNbt.pack(blocks, Math.max(4, ChunkNbt.bits(palette.length))));
        s.put("block_states", bs);
        if (biomePalette != null) {
            Map<String, Object> bi = new LinkedHashMap<>();
            bi.put("palette", new Nbt.TagList(Nbt.STRING, new ArrayList<>(List.of((Object[]) biomePalette))));
            if (biomes != null) bi.put("data", ChunkNbt.pack(biomes, ChunkNbt.bits(biomePalette.length)));
            s.put("biomes", bi);
        }
        return s;
    }

    static Map<String, Object> entry(String state) {
        Map<String, Object> e = new LinkedHashMap<>();
        int b = state.indexOf('[');
        e.put("Name", b < 0 ? state : state.substring(0, b));
        if (b >= 0) {
            Map<String, Object> props = new LinkedHashMap<>();
            for (String kv : state.substring(b + 1, state.length() - 1).split(",")) {
                String[] p = kv.split("=");
                props.put(p[0], p[1]);
            }
            e.put("Properties", props);
        }
        return e;
    }

    static byte[] nbt(Map<String, Object> root) {
        return Nbt.write("", root);
    }

    static byte[] compress(byte[] data, int type) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        switch (type & 127) {
            case 1 -> {
                try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
                    out.write(data);
                }
            }
            case 2 -> {
                try (DeflaterOutputStream out = new DeflaterOutputStream(bytes)) {
                    out.write(data);
                }
            }
            default -> bytes.write(data);
        }
        return bytes.toByteArray();
    }

    /** La región: devuelve los bytes del .mca; los que van a un .mcc se ponen en external (cx,cz → bytes). */
    static byte[] region(List<Entry> chunks, Map<String, byte[]> external) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] header = new byte[8192];
        out.write(header);
        int sector = 2;
        ByteBuffer h = ByteBuffer.wrap(header);
        for (Entry c : chunks) {
            byte[] comp = compress(c.nbt(), c.compression());
            byte[] body;
            if ((c.compression() & 128) != 0) {
                external.put("c." + c.cx() + "." + c.cz() + ".mcc", comp);
                body = ByteBuffer.allocate(5).putInt(1).put((byte) c.compression()).array();
            } else {
                body = ByteBuffer.allocate(5 + comp.length).putInt(comp.length + 1).put((byte) c.compression()).put(comp).array();
            }
            int sectors = (body.length + 4095) / 4096;
            byte[] padded = new byte[sectors * 4096];
            System.arraycopy(body, 0, padded, 0, body.length);
            h.putInt(4 * ((c.cx() & 31) + (c.cz() & 31) * 32), (sector << 8) | sectors);
            out.write(padded);
            sector += sectors;
        }
        byte[] all = out.toByteArray();
        System.arraycopy(header, 0, all, 0, 8192);
        return all;
    }
}

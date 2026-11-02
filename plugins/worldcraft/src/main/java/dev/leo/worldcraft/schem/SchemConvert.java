package dev.leo.worldcraft.schem;

import dev.leo.worldcraft.edit.Clipboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * Esquema ↔ portapapeles (spec 027 H1, H2). Java puro y sin la tabla de estados: lo que necesita del servidor (la paleta
 * traducida, el texto de cada estado) llega ya calculado del hilo principal, así esto corre en el hilo de esquemas.
 */
public final class SchemConvert {
    private SchemConvert() {
    }

    /**
     * Lo cargado y lo que hay que contarle al jugador.
     *
     * @param placeable datos de bloque que se pondrán al pegar (contenedores y carteles)
     * @param kept      datos de bloque que se conservan para volver a guardar pero no se ponen, por id
     */
    public record Loaded(Clipboard clipboard, int placeable, Map<String, Integer> kept) {
    }

    /** map: de índice de la paleta a número de estado (SchemBlocks); isAir: para soltar datos de un bloque que quedó aire. */
    public static Loaded toClipboard(Schematic s, int[] map, IntPredicate isAir) {
        return toClipboard(s, map, isAir, s.dataVersion());
    }

    /** Spec 031: fallbackDataVersion es la del servidor, para las entidades de un esquema que no dice la suya. */
    public static Loaded toClipboard(Schematic s, int[] map, IntPredicate isAir, int fallbackDataVersion) {
        int[] blocks = s.blocks();
        int[] states = new int[blocks.length];
        for (int i = 0; i < blocks.length; i++) states[i] = map[blocks[i]];
        Map<Integer, byte[]> tiles = new HashMap<>();
        int placeable = 0;
        Map<String, Integer> kept = new TreeMap<>();
        for (Map.Entry<Integer, Map<String, Object>> e : s.blockEntities().entrySet()) {
            if (isAir.test(states[e.getKey()])) continue;
            String id = Nbt.string(e.getValue(), "Id");
            Map<String, Object> data = Nbt.compound(e.getValue(), "Data");
            TileData t = new TileData(s.dataVersion(), id, data == null ? new LinkedHashMap<>() : data);
            if (t.kind() == TileData.Kind.OTHER) kept.merge(id.replace("minecraft:", ""), 1, Integer::sum);
            else placeable++;
            tiles.put(e.getKey(), t.encode());
        }
        int[] o = s.offset();
        Clipboard clip = Clipboard.of(s.width(), s.height(), s.length(), o[0], o[1], o[2], states, tiles);
        List<dev.leo.worldcraft.entity.EntityCopy> ents = new ArrayList<>();
        for (Map<String, Object> e : s.entityList()) ents.add(SchemEntities.toCopy(e, o, s.dataVersion(), fallbackDataVersion));
        clip.entities(ents);
        return new Loaded(clip, placeable, kept);
    }

    /**
     * Un portapapeles sin transformación (ya {@link Clipboard#baked}) a esquema. names: el texto de cada estado que tiene;
     * airState: lo que se escribe donde no se leyó nada. Los datos de bloque tienen que estar en la forma estándar ('N');
     * los demás se saltan.
     */
    public static Schematic fromClipboard(Clipboard c, Map<Integer, String> names, String airState, int dataVersion,
                                          String name, String author, long date) {
        int[] raw = c.rawStates();
        int[] blocks = new int[raw.length];
        Map<String, Integer> palette = new LinkedHashMap<>();
        Map<Integer, Integer> byState = new HashMap<>();
        for (int i = 0; i < raw.length; i++) {
            int s = raw[i];
            Integer idx = byState.get(s);
            if (idx == null) {
                String text = s < 0 ? airState : names.get(s);
                if (text == null) throw new IllegalArgumentException("no name for state " + s);
                idx = palette.computeIfAbsent(text, k -> palette.size());
                byState.put(s, idx);
            }
            blocks[i] = idx;
        }
        String[] pal = new String[palette.size()];
        for (Map.Entry<String, Integer> e : palette.entrySet()) pal[e.getValue()] = e.getKey();
        Map<Integer, Map<String, Object>> bes = new HashMap<>();
        for (Map.Entry<Integer, byte[]> e : c.rawTiles().entrySet()) {
            if (!TileData.is(e.getValue())) continue;
            try {
                TileData t = TileData.decode(e.getValue());
                Map<String, Object> be = new LinkedHashMap<>();
                be.put("Id", t.id());
                be.put("Data", t.data());
                bes.put(e.getKey(), be);
            } catch (Nbt.NbtException ex) {
                // datos dañados: el bloque se guarda sin ellos
            }
        }
        List<Map<String, Object>> ents = new ArrayList<>();
        for (dev.leo.worldcraft.entity.EntityCopy e : c.entities()) {
            try {
                ents.add(SchemEntities.fromCopy(e, c.offset()));
            } catch (Nbt.NbtException ex) {
                // una entidad dañada no se guarda; el resto sí
            }
        }
        return new Schematic(3, dataVersion, c.width(), c.height(), c.depth(), c.offset(), pal, blocks, bes, ents.size(), 0,
                name, author, date, ents);
    }
}

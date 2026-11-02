package dev.leo.worldcraft.snapshot;

import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;
import dev.leo.worldcraft.schem.Nbt;
import dev.leo.worldcraft.schem.TileData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Lo de una copia dentro de la selección (plan 036 §1.2, H3): primero se lee crudo en el hilo de esquemas (estados como
 * texto en una paleta global), en el hilo principal se traduce la paleta a estados del servidor y después se arma el
 * portapapeles. Lo que no se restaura (fuera de la región, chunks que faltan, viejos, sin terminar o rotos, bloques que el
 * servidor no tiene) queda «sin leer»: paste lo deja como está.
 */
public final class Restore {
    private Restore() {
    }

    /** Lo leído de la copia, todavía con los estados como texto. */
    public static final class Raw {
        public final Box box;
        /** La paleta global: el texto de cada estado. */
        public String[] palette;
        /** Cuántos bloques usan cada entrada (para el informe de SchemBlocks). */
        public long[] counts;
        /** Un índice en palette por bloque de la caja ((y·d + z)·w + x), o -1. */
        public final int[] states;
        public final Map<Integer, TileData> tiles = new HashMap<>();
        /** Si se pidieron: el bioma de cada bloque (índice en biomePalette) o -1. */
        public int[] biomes;
        public String[] biomePalette;
        public int chunks, missing, old, unfinished, broken, unsupported;
        /** Un ejemplo de por qué un chunk está roto (para el mensaje). */
        public String problem = "";

        Raw(Box box) {
            this.box = box;
            long v = box.volume();
            if (v > Integer.MAX_VALUE - 8) throw new IllegalArgumentException("too big");
            states = new int[(int) v];
            Arrays.fill(states, -1);
        }

        /** Chunks de la caja que no se pudieron restaurar. */
        public int skipped() {
            return missing + old + unfinished + broken + unsupported;
        }
    }

    /** Los chunks de la caja, región por región (así un .zip tiene una sola región en memoria). */
    public static long[] chunks(Box b) {
        List<long[]> list = new ArrayList<>();
        for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
            for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) list.add(new long[]{cx >> 5, cz >> 5, cx, cz});
        }
        list.sort((a, c) -> a[0] != c[0] ? Long.compare(a[0], c[0]) : a[1] != c[1] ? Long.compare(a[1], c[1])
                : a[2] != c[2] ? Long.compare(a[2], c[2]) : Long.compare(a[3], c[3]));
        long[] out = new long[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = (list.get(i)[2] << 32) | (list.get(i)[3] & 0xffffffffL);
        return out;
    }

    /** Cuántos chunks toca la caja. */
    public static long chunkCount(Box b) {
        return (long) ((b.maxX() >> 4) - (b.minX() >> 4) + 1) * ((b.maxZ() >> 4) - (b.minZ() >> 4) + 1);
    }

    /** Lee de la copia lo de la región (ya recortada a la altura del mundo). */
    public static Raw read(Region region, SnapshotRepo.Chunks src, boolean withBiomes) throws IOException {
        Box box = region.bounds();
        Raw raw = new Raw(box);
        Map<String, Integer> global = new LinkedHashMap<>();
        Map<String, Integer> biomeIndex = new LinkedHashMap<>();
        long[] counts = new long[64];
        if (withBiomes) {
            raw.biomes = new int[raw.states.length];
            Arrays.fill(raw.biomes, -1);
        }
        int w = box.width(), d = box.depth();
        for (long key : chunks(box)) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("stopped");
            int cx = (int) (key >> 32), cz = (int) key;
            raw.chunks++;
            RegionFile.Chunk c = src.chunk(cx, cz);
            switch (c.kind()) {
                case MISSING -> {
                    raw.missing++;
                    continue;
                }
                case UNSUPPORTED -> {
                    raw.unsupported++;
                    raw.problem = c.problem();
                    continue;
                }
                case BROKEN -> {
                    raw.broken++;
                    raw.problem = c.problem();
                    continue;
                }
                default -> {
                }
            }
            ChunkNbt.Data data = ChunkNbt.decode(c.nbt());
            switch (data.kind()) {
                case OLD -> {
                    raw.old++;
                    continue;
                }
                case UNFINISHED -> {
                    raw.unfinished++;
                    continue;
                }
                case BROKEN -> {
                    raw.broken++;
                    raw.problem = data.problem();
                    continue;
                }
                default -> {
                }
            }
            int x0 = Math.max(box.minX(), cx << 4), x1 = Math.min(box.maxX(), (cx << 4) + 15);
            int z0 = Math.max(box.minZ(), cz << 4), z1 = Math.min(box.maxZ(), (cz << 4) + 15);
            for (int sy = box.minY() >> 4; sy <= box.maxY() >> 4; sy++) {
                ChunkNbt.Section s = data.sections().get(sy);
                String[] pal = s == null ? new String[]{ChunkNbt.AIR} : s.palette();
                int[] toGlobal = new int[pal.length];
                for (int i = 0; i < pal.length; i++) {
                    Integer g = global.get(pal[i]);
                    if (g == null) {
                        g = global.size();
                        global.put(pal[i], g);
                        if (g == counts.length) counts = Arrays.copyOf(counts, counts.length * 2);
                    }
                    toGlobal[i] = g;
                }
                int[] biomeGlobal = null;
                if (withBiomes && s != null && s.biomePalette() != null) {
                    biomeGlobal = new int[s.biomePalette().length];
                    for (int i = 0; i < biomeGlobal.length; i++) biomeGlobal[i] = biomeIndex.computeIfAbsent(s.biomePalette()[i], k -> biomeIndex.size());
                }
                int y0 = Math.max(box.minY(), sy << 4), y1 = Math.min(box.maxY(), (sy << 4) + 15);
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int x = x0; x <= x1; x++) {
                            if (!region.contains(x, y, z)) continue;
                            int lx = x & 15, ly = y & 15, lz = z & 15;
                            int g = toGlobal[s == null || s.blocks() == null ? 0 : s.blocks()[(ly << 8) | (lz << 4) | lx]];
                            int i = ((y - box.minY()) * d + (z - box.minZ())) * w + (x - box.minX());
                            raw.states[i] = g;
                            counts[g]++;
                            if (biomeGlobal != null) {
                                int b = s.biomes() == null ? 0 : s.biomes()[((ly >> 2) << 4) | ((lz >> 2) << 2) | (lx >> 2)];
                                raw.biomes[i] = biomeGlobal[b];
                            }
                        }
                    }
                }
            }
            for (Map<String, Object> be : data.blockEntities()) {
                int x = Nbt.integer(be, "x").intValue(), y = Nbt.integer(be, "y").intValue(), z = Nbt.integer(be, "z").intValue();
                if (!box.contains(x, y, z) || !region.contains(x, y, z) || (x >> 4) != cx || (z >> 4) != cz) continue;
                int i = ((y - box.minY()) * d + (z - box.minZ())) * w + (x - box.minX());
                raw.tiles.put(i, new TileData(data.dataVersion(), Nbt.string(be, "id"), ChunkNbt.tileData(be)));
            }
        }
        raw.palette = global.keySet().toArray(new String[0]);
        raw.counts = Arrays.copyOf(counts, global.size());
        raw.biomePalette = biomeIndex.keySet().toArray(new String[0]);
        return raw;
    }

    /**
     * El portapapeles (origen en la esquina mínima de la caja): map traduce la paleta global a estados del servidor (-1 =
     * desconocido, se deja como está); los datos de un bloque que quedó aire o sin restaurar se sueltan; los biomas que el
     * servidor no tiene, sin bioma.
     */
    public static Clipboard toClipboard(Raw raw, int[] map, IntPredicate isAir, Predicate<String> knownBiome) {
        int[] states = new int[raw.states.length];
        for (int i = 0; i < states.length; i++) states[i] = raw.states[i] < 0 ? -1 : map[raw.states[i]];
        Map<Integer, byte[]> tiles = new HashMap<>();
        for (Map.Entry<Integer, TileData> e : raw.tiles.entrySet()) {
            int s = states[e.getKey()];
            if (s >= 0 && !isAir.test(s)) tiles.put(e.getKey(), e.getValue().encode());
        }
        Box b = raw.box;
        Clipboard clip = Clipboard.of(b.width(), b.height(), b.depth(), 0, 0, 0, states, tiles);
        if (raw.biomes != null) {
            String[] pal = raw.biomePalette.clone();
            for (int i = 0; i < pal.length; i++) if (!knownBiome.test(pal[i])) pal[i] = null;
            clip.biomes(pal, raw.biomes);
        }
        return clip;
    }

    /** Cuántos bloques no se restauran por ser desconocidos (map = -1). */
    public static long unknownBlocks(Raw raw, int[] map) {
        long n = 0;
        for (int i = 0; i < map.length; i++) if (map[i] < 0) n += raw.counts[i];
        return n;
    }
}

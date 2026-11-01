package dev.leo.worldcraft.schem;

import java.io.ByteArrayOutputStream;

/** El {@code varint[]} de Sponge (plan 027 §1): 7 bits por byte, del menos al más significativo; el bit alto dice «sigue». */
public final class VarInts {
    private VarInts() {
    }

    /** Exactamente count números; un archivo con menos, más o un número mal formado lanza el motivo. */
    public static int[] decode(byte[] data, int count) throws Nbt.NbtException {
        int[] out = new int[count];
        int i = 0, pos = 0;
        while (pos < data.length) {
            int value = 0, shift = 0, b;
            do {
                if (pos >= data.length) throw new Nbt.NbtException("the block data ends in the middle of a number");
                if (shift > 28) throw new Nbt.NbtException("the block data has a number that is too big");
                b = data[pos++];
                value |= (b & 0x7f) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            if (i >= count) throw new Nbt.NbtException("the block data has more blocks than the size says");
            out[i++] = value;
        }
        if (i != count) throw new Nbt.NbtException("the block data has " + i + " blocks but the size says " + count);
        return out;
    }

    public static byte[] encode(int[] values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(values.length + 16);
        for (int v : values) {
            if (v < 0) throw new IllegalArgumentException("negative palette index");
            while ((v & ~0x7f) != 0) {
                out.write((v & 0x7f) | 0x80);
                v >>>= 7;
            }
            out.write(v);
        }
        return out.toByteArray();
    }
}

package dev.leo.worldcraft.schem;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NbtTest {

    @Test
    void ida_y_vuelta_de_todos_los_tipos() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("b", (byte) -3);
        m.put("s", (short) 40000);
        m.put("i", 123456789);
        m.put("l", Long.MIN_VALUE);
        m.put("f", 1.5f);
        m.put("d", -2.25);
        m.put("ba", new byte[]{1, 2, 3});
        m.put("str", "ñandú «x» 漢字");
        m.put("ia", new int[]{7, -8});
        m.put("la", new long[]{9L, 10L});
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("x", 1);
        m.put("c", inner);
        m.put("list", new Nbt.TagList(Nbt.COMPOUND, List.of(inner, inner)));
        m.put("empty", new Nbt.TagList(Nbt.END, List.of()));
        Nbt.Root r = Nbt.read(Nbt.gunzip(Nbt.gzip(Nbt.write("raiz", m)), 1 << 20));
        assertEquals("raiz", r.name());
        Map<String, Object> o = r.tag();
        assertEquals((byte) -3, o.get("b"));
        assertEquals((short) 40000, o.get("s"));
        assertEquals(123456789, o.get("i"));
        assertEquals(Long.MIN_VALUE, o.get("l"));
        assertEquals(1.5f, o.get("f"));
        assertEquals(-2.25, o.get("d"));
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) o.get("ba"));
        assertEquals("ñandú «x» 漢字", o.get("str"));
        assertArrayEquals(new int[]{7, -8}, (int[]) o.get("ia"));
        assertArrayEquals(new long[]{9L, 10L}, (long[]) o.get("la"));
        assertEquals(1, Nbt.compound(o, "c").get("x"));
        assertEquals(2, Nbt.list(o, "list").items().size());
        assertEquals(0, Nbt.list(o, "empty").items().size());
        assertEquals(List.copyOf(m.keySet()), List.copyOf(o.keySet()), "el orden se conserva");
    }

    @Test
    void H5_CA3_un_archivo_cortado_o_que_no_es_nbt_da_un_motivo() {
        byte[] ok = Nbt.write("", Map.of("a", "hola", "b", new int[]{1, 2, 3}));
        Nbt.NbtException e = assertThrows(Nbt.NbtException.class, () -> Nbt.read(Arrays.copyOf(ok, ok.length - 3)));
        assertEquals("the file is cut short", e.getMessage());
        assertThrows(Nbt.NbtException.class, () -> Nbt.read(new byte[]{8, 0, 0}));
        assertThrows(Nbt.NbtException.class, () -> Nbt.read(new byte[0]));
        assertThrows(Nbt.NbtException.class, () -> Nbt.gunzip(new byte[]{0x1f, (byte) 0x8b, 1, 2, 3}, 1000));
    }

    @Test
    void H5_CA2_un_largo_mentiroso_no_reserva_memoria() throws Exception {
        // Un arreglo de bytes que dice tener 2 000 000 000 de elementos en un archivo de 20 bytes.
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(b);
        o.writeByte(Nbt.COMPOUND);
        o.writeUTF("");
        o.writeByte(Nbt.INT_ARRAY);
        o.writeUTF("x");
        o.writeInt(2_000_000_000);
        o.writeInt(1);
        Nbt.NbtException e = assertThrows(Nbt.NbtException.class, () -> Nbt.read(b.toByteArray()));
        assertEquals("the file is cut short", e.getMessage());
    }

    @Test
    void H5_CA2_profundidad_con_tope() throws Exception {
        // 600 listas una dentro de otra.
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(b);
        o.writeByte(Nbt.COMPOUND);
        o.writeUTF("");
        o.writeByte(Nbt.LIST);
        o.writeUTF("x");
        for (int i = 0; i < 600; i++) {
            o.writeByte(Nbt.LIST);
            o.writeInt(1);
        }
        o.writeByte(Nbt.END);
        o.writeInt(0);
        Nbt.NbtException e = assertThrows(Nbt.NbtException.class, () -> Nbt.read(b.toByteArray()));
        assertTrue(e.getMessage().contains("nested too deep"), e.getMessage());
    }

    @Test
    void H5_CA2_una_bomba_de_gzip_se_corta_en_el_tope() {
        byte[] zeros = new byte[8 << 20]; // 8 MB de ceros: unos 8 KB comprimidos
        byte[] gz = Nbt.gzip(zeros);
        assertTrue(gz.length < 64 * 1024);
        Nbt.NbtException e = assertThrows(Nbt.NbtException.class, () -> Nbt.gunzip(gz, 1 << 20));
        assertTrue(e.getMessage().contains("too big once uncompressed"), e.getMessage());
    }

    @Test
    void nbt_sin_comprimir_tambien_se_lee() throws Exception {
        byte[] raw = Nbt.write("", Map.of("a", 1));
        assertEquals(1, Nbt.read(Nbt.gunzip(raw, 1000)).tag().get("a"));
    }

    @Test
    void varint_de_uno_dos_y_tres_bytes() throws Exception {
        int[] v = {0, 1, 127, 128, 300, 16383, 16384, 2_000_000};
        byte[] e = VarInts.encode(v);
        assertEquals(1 + 1 + 1 + 2 + 2 + 2 + 3 + 3, e.length);
        assertArrayEquals(v, VarInts.decode(e, v.length));
        assertThrows(Nbt.NbtException.class, () -> VarInts.decode(e, v.length + 1));
        assertThrows(Nbt.NbtException.class, () -> VarInts.decode(e, v.length - 1));
        assertThrows(Nbt.NbtException.class, () -> VarInts.decode(new byte[]{(byte) 0x80}, 1));
    }
}

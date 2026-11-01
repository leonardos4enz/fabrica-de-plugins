package dev.leo.worldcraft.schem;

import com.google.gson.JsonParser;
import dev.leo.worldcraft.edit.Scheduler;
import dev.leo.worldcraft.edit.TaskJob;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TaskAndTextTest {

    /** Un reloj que avanza 1 ms por paso: con 8 ms por tick, 8 pasos por tick. */
    @Test
    void los_pasos_sueltos_respetan_el_presupuesto_por_tick() {
        AtomicLong now = new AtomicLong();
        List<Integer> done = new ArrayList<>();
        boolean[] finished = {false};
        Scheduler sch = new Scheduler(now::get, () -> 8, () -> 1_000_000, (j, e) -> fail(e));
        sch.submit(new TaskJob(1, new UUID(1, 1), 20, i -> {
            done.add(i);
            now.addAndGet(1_000_000);
        }, now::get, () -> finished[0] = true, () -> fail("no se canceló")));
        sch.tick();
        assertEquals(8, done.size());
        sch.tick();
        sch.tick();
        assertEquals(20, done.size());
        assertTrue(finished[0]);
        assertTrue(sch.idle());
    }

    @Test
    void cancelar_avisa_y_no_termina() {
        boolean[] cancelled = {false}, finished = {false};
        TaskJob j = new TaskJob(1, new UUID(1, 1), 5, i -> {
        }, () -> 0, () -> finished[0] = true, () -> cancelled[0] = true);
        j.cancel();
        assertTrue(j.step(Long.MAX_VALUE, 100));
        assertTrue(cancelled[0]);
        assertFalse(finished[0]);
    }

    @Test
    void un_texto_con_colores_va_de_json_a_nbt_y_vuelve() {
        String json = "{\"text\":\"Hola \",\"bold\":true,\"color\":\"red\",\"extra\":[{\"text\":\"mundo\",\"italic\":false},\"!\"]}";
        Object nbt = TextNbt.toNbt(JsonParser.parseString(json));
        Map<?, ?> m = (Map<?, ?>) nbt;
        assertEquals((byte) 1, m.get("bold"), "los sí/no van como bytes");
        Nbt.TagList extra = (Nbt.TagList) m.get("extra");
        assertEquals(Nbt.COMPOUND, extra.type(), "una lista mezclada pasa a compuestos");
        assertEquals(JsonParser.parseString("{\"text\":\"Hola \",\"bold\":true,\"color\":\"red\",\"extra\":[{\"text\":\"mundo\",\"italic\":false},{\"text\":\"!\"}]}"),
                TextNbt.toJson(nbt, null));
        assertEquals("solo texto", TextNbt.toNbt(JsonParser.parseString("\"solo texto\"")));
    }
}

package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerTest {
    /** Reloj falso: cada bloque cuesta 1 µs. */
    long now;

    class FakeJob implements Scheduler.Job {
        final int id;
        final UUID owner;
        final Box box;
        long left;
        long done;
        boolean cancelled;

        FakeJob(int id, UUID owner, long blocks, Box box) {
            this.id = id;
            this.owner = owner;
            this.left = blocks;
            this.box = box;
        }

        @Override
        public boolean step(long deadline, int maxBlocks) {
            int n = 0;
            while (left > 0 && n < maxBlocks && now < deadline) {
                now += 1000;
                left--;
                done++;
                n++;
            }
            return left == 0;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public UUID owner() {
            return owner;
        }

        @Override
        public String world() {
            return "world";
        }

        @Override
        public Box bounds() {
            return box;
        }
    }

    final UUID ana = new UUID(0, 1), beto = new UUID(0, 2);
    final List<RuntimeException> errors = new ArrayList<>();
    final Scheduler s = new Scheduler(() -> now, () -> 8, () -> 1_000_000, (j, e) -> errors.add(e));

    @Test
    void H5_CA1_nunca_usa_mas_tiempo_por_tick_que_el_tope() {
        FakeJob big = new FakeJob(1, ana, 1_000_000, Box.of(0, 0, 0, 999, 0, 999));
        s.submit(big);
        int ticks = 0;
        while (!s.idle()) {
            s.tick();
            ticks++;
            assertTrue(s.lastTickNanos() <= 8_000_000 + 64_000, "tick de " + s.lastTickNanos());
        }
        assertEquals(1_000_000, big.done);
        assertEquals(125, ticks, "1 µs por bloque y 8 ms por tick: 8000 bloques por tick");
    }

    @Test
    void H5_CA2_una_operacion_enorme_no_deja_esperando_a_la_chica_de_otro() {
        FakeJob big = new FakeJob(1, ana, 1_000_000, Box.of(0, 0, 0, 999, 0, 999));
        FakeJob small = new FakeJob(2, beto, 6000, Box.of(5000, 0, 0, 5010, 0, 10));
        s.submit(big);
        s.submit(small);
        int ticks = 0;
        while (small.left > 0) {
            s.tick();
            ticks++;
        }
        assertTrue(ticks <= 2, "la chica terminó en " + ticks + " ticks");
        assertTrue(big.done > 0);
    }

    @Test
    void H5_CA6_dos_operaciones_que_se_cruzan_no_corren_a_la_vez() {
        FakeJob first = new FakeJob(1, ana, 20_000, Box.of(0, 0, 0, 10, 10, 10));
        FakeJob second = new FakeJob(2, beto, 100, Box.of(10, 10, 10, 20, 20, 20));
        s.submit(first);
        s.submit(second);
        while (first.left > 0) {
            s.tick();
            assertEquals(0, second.done, "la segunda espera a que termine la primera");
        }
        s.tick();
        assertEquals(100, second.done);
    }

    @Test
    void cada_jugador_hace_las_suyas_en_orden_y_un_error_cancela_solo_esa() {
        FakeJob a1 = new FakeJob(1, ana, 100, Box.of(0, 0, 0, 1, 1, 1));
        Scheduler.Job boom = new FakeJob(2, ana, 5, Box.of(50, 0, 0, 51, 1, 1)) {
            @Override
            public boolean step(long deadline, int maxBlocks) {
                throw new IllegalStateException("boom");
            }
        };
        FakeJob a3 = new FakeJob(3, ana, 100, Box.of(90, 0, 0, 91, 1, 1));
        s.submit(a1);
        s.submit(boom);
        s.submit(a3);
        while (!s.idle()) s.tick();
        assertEquals(100, a1.done);
        assertEquals(100, a3.done);
        assertEquals(1, errors.size());
        assertTrue(((FakeJob) boom).cancelled);
    }

    @Test
    void cancelar_y_apagar() {
        FakeJob a = new FakeJob(1, ana, 1_000_000, Box.of(0, 0, 0, 1, 1, 1));
        FakeJob b = new FakeJob(2, beto, 1_000_000, Box.of(9, 0, 0, 10, 1, 1));
        s.submit(a);
        s.submit(b);
        s.tick();
        assertTrue(s.cancel(1));
        assertTrue(a.cancelled);
        assertEquals(1, s.all().size());
        s.shutdown();
        assertTrue(b.cancelled);
        assertTrue(s.idle());
    }
}

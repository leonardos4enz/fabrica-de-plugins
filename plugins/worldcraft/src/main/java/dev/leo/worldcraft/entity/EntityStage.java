package dev.leo.worldcraft.entity;

import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Una etapa de entidades de una obra (plan 031 §1.2), por tandas dentro del presupuesto del tick: mira el reloj en cada
 * entidad (lección 025 F-01) y espera sin bloquear a que un chunk tenga sus entidades cargadas.
 */
public interface EntityStage {
    /** Cuántos ticks espera un chunk que no carga antes de saltarlo (y contarlo). */
    int MAX_WAIT = 200;

    /** Avanza hasta el plazo; true si terminó. */
    boolean step(long deadlineNanos, LongSupplier clock);

    /** Entidades que copió, quitó o creó. */
    int done();

    /** Las que no pudo: ya no estaban (deshacer seguro) o no se pudieron crear. */
    default int skipped() {
        return 0;
    }

    /**
     * Spec 033: dos etapas, una tras otra (las entidades y después los biomas). Si una es null, la otra; done y skipped
     * son los de la primera (los mensajes de entidades miran esa).
     */
    static EntityStage chain(EntityStage a, EntityStage b) {
        if (a == null) return b;
        if (b == null) return a;
        return new EntityStage() {
            private boolean firstDone;

            @Override
            public boolean step(long deadlineNanos, LongSupplier clock) {
                if (!firstDone) {
                    if (!a.step(deadlineNanos, clock)) return false;
                    firstDone = true;
                }
                return b.step(deadlineNanos, clock);
            }

            @Override
            public int done() {
                return a.done();
            }

            @Override
            public int skipped() {
                return a.skipped();
            }
        };
    }

    /** Algo que se hace una vez (dejar las entidades en el portapapeles al cortar). */
    static EntityStage action(Runnable r) {
        return new EntityStage() {
            @Override
            public boolean step(long deadlineNanos, LongSupplier clock) {
                r.run();
                return true;
            }

            @Override
            public int done() {
                return 0;
            }
        };
    }

    /** Busca las entidades cuyo bloque está en la región y las copia (y, si remove, las quita) hasta max. */
    final class Scan implements EntityStage {
        private final EntityWorld world;
        private final Region region;
        private final Predicate<EntityInfo> filter;
        private final boolean remove;
        private final int max;
        private final EntitySink sink;
        private final long[] chunks;
        private final List<EntityCopy> result = new ArrayList<>();
        private final Map<String, Integer> types = new TreeMap<>();
        private List<EntityInfo> pending;
        private int chunk, next, waited, unloaded;
        private boolean truncated, finished;

        public Scan(EntityWorld world, Region region, Predicate<EntityInfo> filter, boolean remove, int max, EntitySink sink) {
            this.world = world;
            this.region = region;
            this.filter = filter;
            this.remove = remove;
            this.max = max;
            this.sink = sink;
            Box b = region.bounds();
            int cx0 = b.minX() >> 4, cx1 = b.maxX() >> 4, cz0 = b.minZ() >> 4, cz1 = b.maxZ() >> 4;
            chunks = new long[(cx1 - cx0 + 1) * (cz1 - cz0 + 1)];
            int i = 0;
            for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) chunks[i++] = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        }

        @Override
        public boolean step(long deadlineNanos, LongSupplier clock) {
            while (!finished) {
                if (pending == null) {
                    if (chunk >= chunks.length) {
                        finished = true;
                        break;
                    }
                    int cx = (int) (chunks[chunk] >> 32), cz = (int) chunks[chunk];
                    if (!world.ready(cx, cz)) {
                        if (++waited < MAX_WAIT) return false;
                        unloaded++;
                        waited = 0;
                        chunk++;
                        continue;
                    }
                    waited = 0;
                    pending = new ArrayList<>();
                    for (EntityInfo e : world.list(cx, cz)) {
                        if (e.is(Kind.PLAYER)) continue;
                        if (!region.contains((int) Math.floor(e.x()), (int) Math.floor(e.y()), (int) Math.floor(e.z()))) continue;
                        if (filter.test(e)) pending.add(e);
                    }
                    next = 0;
                    chunk++;
                    continue;
                }
                if (next >= pending.size()) {
                    pending = null;
                    continue;
                }
                if (result.size() >= max) {
                    truncated = true;
                    finished = true;
                    break;
                }
                EntityInfo e = pending.get(next++);
                EntityCopy c = world.copy(e.id());
                if (c != null) {
                    if (remove) {
                        if (world.remove(e.id())) sink.removed(e.id(), c);
                        else c = null;
                    }
                    if (c != null) {
                        result.add(c);
                        types.merge(c.type().replace("minecraft:", ""), 1, Integer::sum);
                    }
                }
                if (clock.getAsLong() >= deadlineNanos) return false;
            }
            return true;
        }

        /** Lo copiado, con la posición del mundo. */
        public List<EntityCopy> result() {
            return result;
        }

        /** Cuántas de cada tipo («zombie» → 3). */
        public Map<String, Integer> types() {
            return types;
        }

        /** Llegó al tope: quedaron entidades sin tocar. */
        public boolean truncated() {
            return truncated;
        }

        /** Chunks cuyas entidades no cargaron a tiempo (se saltaron). */
        public int unloaded() {
            return unloaded;
        }

        @Override
        public int done() {
            return result.size();
        }
    }

    /** Una entidad por crear: original es el uuid que el diario conoce (deshacer y rehacer) o null si es nueva. */
    record Item(UUID original, EntityCopy copy) {
    }

    /** Crea las entidades (esperando el chunk de cada una) y las anota: nuevas (added) o recreadas (remapped). */
    final class Spawn implements EntityStage {
        private final EntityWorld world;
        private final Supplier<List<Item>> items;
        private final EntitySink sink;
        private List<Item> list;
        private int next, waited, done, failed;

        /** items puede dar null mientras no está listo (el diario se lee en otro hilo). */
        public Spawn(EntityWorld world, Supplier<List<Item>> items, EntitySink sink) {
            this.world = world;
            this.items = items;
            this.sink = sink;
        }

        @Override
        public boolean step(long deadlineNanos, LongSupplier clock) {
            if (list == null) {
                list = items.get();
                if (list == null) return false;
            }
            while (next < list.size()) {
                Item it = list.get(next);
                EntityCopy c = it.copy();
                int cx = (int) Math.floor(c.x()) >> 4, cz = (int) Math.floor(c.z()) >> 4;
                if (!world.ready(cx, cz)) {
                    if (++waited < MAX_WAIT) return false;
                    waited = 0;
                    next++;
                    failed++; // no se crea en un chunk sin cargar (lo cargaría de golpe)
                    continue;
                }
                waited = 0;
                next++;
                UUID id = world.spawn(c);
                if (id == null) {
                    failed++;
                } else {
                    done++;
                    if (it.original() != null) sink.remapped(it.original(), id);
                    else {
                        EntityCopy now = world.copy(id);
                        sink.added(id, now != null ? now : c);
                    }
                }
                if (clock.getAsLong() >= deadlineNanos) return false;
            }
            return true;
        }

        @Override
        public int done() {
            return done;
        }

        @Override
        public int skipped() {
            return failed;
        }
    }

    /** Una entidad por quitar al deshacer: su uuid de ahora y su tipo (si cambió de tipo, no es la misma). */
    record Target(UUID id, String type) {
    }

    /** Quita las que sigan existiendo (deshacer seguro: las demás se saltan y se cuentan). */
    final class Kill implements EntityStage {
        private final EntityWorld world;
        private final Supplier<List<Target>> targets;
        private List<Target> list;
        private int next, done, skipped;

        public Kill(EntityWorld world, Supplier<List<Target>> targets) {
            this.world = world;
            this.targets = targets;
        }

        @Override
        public boolean step(long deadlineNanos, LongSupplier clock) {
            if (list == null) {
                list = targets.get();
                if (list == null) return false;
            }
            while (next < list.size()) {
                Target t = list.get(next++);
                if (t.type().equals(world.type(t.id())) && world.remove(t.id())) done++;
                else skipped++;
                if (clock.getAsLong() >= deadlineNanos) return false;
            }
            return true;
        }

        @Override
        public int done() {
            return done;
        }

        @Override
        public int skipped() {
            return skipped;
        }
    }
}

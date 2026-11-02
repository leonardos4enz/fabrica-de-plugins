package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.api.EditBuilder;
import dev.leo.worldcraft.api.EditResult;
import dev.leo.worldcraft.api.Selection;
import dev.leo.worldcraft.api.WorldcraftApi;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.edit.EditSource;
import dev.leo.worldcraft.edit.MaskedSource;
import dev.leo.worldcraft.edit.RegionSource;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;
import dev.leo.worldcraft.region.Shape;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BlockVector;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Spec 037 (plan §1.2–1.3): la API para otros plugins. Todo lo que pide pasa por {@link Edits#submit}, la misma puerta
 * que los comandos (límites, evento, planificador, historial y deshacer).
 */
public final class Api implements WorldcraftApi {
    static final String OFF = "The server owner turned Worldcraft's API off (api.enabled in config.yml)";
    private final Worldcraft plugin;

    public Api(Worldcraft plugin) {
        this.plugin = plugin;
    }

    /** El dueño de las obras que un plugin hace a su nombre: fijo por nombre, para que su historial siga tras reiniciar. */
    public static UUID pluginOwner(String pluginName) {
        return UUID.nameUUIDFromBytes(("worldcraft-plugin:" + pluginName).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int apiVersion() {
        return API_VERSION;
    }

    @Override
    public String pluginVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public Optional<Selection> selection(Player player) {
        mainOnly();
        Session ses = plugin.edits().session(player.getUniqueId());
        Region r = ses.region();
        World w = ses.world() == null ? null : Bukkit.getWorld(ses.world());
        return r == null || w == null ? Optional.empty() : Optional.of(new Sel(w, r));
    }

    @Override
    public void select(Player player, World world, BlockVector corner1, BlockVector corner2) {
        mainOnly();
        plugin.edits().session(player.getUniqueId()).selectBox(world.getName(), box(corner1, corner2));
    }

    @Override
    public void clearSelection(Player player) {
        mainOnly();
        plugin.edits().session(player.getUniqueId()).clearSelection();
    }

    @Override
    public EditBuilder edit(Plugin from) {
        if (from == null) throw new IllegalArgumentException("edit(plugin) needs your plugin");
        return new Builder(from);
    }

    private static void mainOnly() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Call this from the main thread (Bukkit's scheduler: runTask)");
    }

    private static Box box(BlockVector a, BlockVector b) {
        return Box.of(a.getBlockX(), a.getBlockY(), a.getBlockZ(), b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    /** Una selección en un momento: la región ya armada no cambia aunque el jugador marque otra cosa. */
    record Sel(World world, Region region) implements Selection {
        @Override
        public String shape() {
            return region.kind();
        }

        @Override
        public BlockVector min() {
            Box b = region.bounds();
            return new BlockVector(b.minX(), b.minY(), b.minZ());
        }

        @Override
        public BlockVector max() {
            Box b = region.bounds();
            return new BlockVector(b.maxX(), b.maxY(), b.maxZ());
        }

        @Override
        public long volume() {
            return region.volume();
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return region.contains(x, y, z);
        }
    }

    private final class Builder implements EditBuilder {
        private final Plugin from;
        private Player as;
        private World world;
        private Region region;
        private String mask;

        Builder(Plugin from) {
            this.from = from;
        }

        @Override
        public EditBuilder as(Player player) {
            as = player;
            return this;
        }

        @Override
        public EditBuilder in(World w, BlockVector corner1, BlockVector corner2) {
            world = w;
            region = box(corner1, corner2);
            return this;
        }

        @Override
        public EditBuilder in(Selection selection) {
            if (!(selection instanceof Sel s)) throw new IllegalArgumentException("Use a selection from WorldcraftApi.selection(player)");
            world = s.world();
            region = s.region();
            return this;
        }

        @Override
        public EditBuilder mask(String m) {
            mask = m;
            return this;
        }

        @Override
        public CompletableFuture<EditResult> set(String pattern) {
            return start("set", (c, f) -> fill(c, f, "set", null, pattern));
        }

        @Override
        public CompletableFuture<EditResult> replace(String maskText, String pattern) {
            return start("replace", (c, f) -> fill(c, f, "replace", maskText, pattern));
        }

        @Override
        public CompletableFuture<EditResult> paste(String schematic, World w, BlockVector at, int rotation, boolean air) {
            return start("paste", (c, f) -> {
                if (w == null || at == null) throw new IllegalArgumentException("paste needs a world and a place");
                if (rotation % 90 != 0) throw new IllegalArgumentException("The rotation must be a multiple of 90");
                Mask.Compiled mc = compiledMask(w);
                plugin.schematics().loadClipboard(schematic).whenComplete((clip, t) -> {
                    if (t != null) {
                        f.completeExceptionally(t instanceof java.util.concurrent.CompletionException && t.getCause() != null ? t.getCause() : t);
                        return;
                    }
                    try {
                        if (rotation != 0) clip.transform(clip.transform().rotate(rotation));
                        int[] o = {at.getBlockX(), at.getBlockY(), at.getBlockZ()};
                        Box target = clip.target(o[0], o[1], o[2]);
                        if (target.clampY(w.getMinHeight(), w.getMaxHeight() - 1) == null) {
                            throw new IllegalArgumentException("The schematic would be outside the world's height");
                        }
                        Edits.Op op = PasteOp.of(plugin, w, clip, o, target, air, true, false);
                        plugin.edits().submit(c, masked(op, mc), f::complete);
                    } catch (RuntimeException e) {
                        f.completeExceptionally(e);
                    }
                });
            });
        }

        @Override
        public CompletableFuture<EditResult> undo(int count) {
            return start("undo", (c, f) -> {
                if (count < 1) throw new IllegalArgumentException("undo needs a count of at least 1");
                plugin.edits().undo(c.who(), c.owner(), c.name(), c.owner(), c.name(), count, false, from, f::complete);
            });
        }

        private void fill(Edits.Caller c, CompletableFuture<EditResult> f, String kind, String maskText, String patternText) {
            if (region == null || world == null) throw new IllegalArgumentException("Say where first: in(world, corner1, corner2) or in(selection)");
            if (patternText == null || patternText.isBlank()) throw new IllegalArgumentException("The pattern is empty");
            var parser = plugin.config().parser();
            Pattern p;
            Mask m = null;
            try {
                p = parser.pattern(patternText);
                if (maskText != null) m = parser.maskNear(maskText);
            } catch (ParseException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            if (m != null && m.needsNeighbors()) throw new IllegalArgumentException("Masks that look around (above, below, exposed...) are not supported by the API");
            Region r = region.clampY(world.getMinHeight(), world.getMaxHeight() - 1);
            if (r == null) throw new IllegalArgumentException("The place is outside the world's height");
            PaperBlocks blocks = plugin.edits().blocks();
            Pattern.Compiled pc = p.compile(blocks.table());
            Mask.Compiled rmc = m == null ? null : plugin.edits().compile(m, world);
            long seed = ThreadLocalRandom.current().nextLong();
            List<Integer> placed = new ArrayList<>();
            for (String st : p.placedStates()) placed.add(blocks.table().intern(st));
            String detail = m == null ? p.text() : m.text() + " -> " + p.text();
            Edits.Op op = new Edits.Op(kind, detail, world, r.bounds(), r.count(Shape.FILL),
                    () -> new RegionSource(r, Shape.FILL, rmc, pc, seed), placed, null);
            plugin.edits().submit(c, masked(op, compiledMask(world)), f::complete);
        }

        /** La máscara de mask(): lo que no pasa se deja (como la máscara global de /wc gmask). */
        private Mask.Compiled compiledMask(World w) {
            if (mask == null || mask.isBlank()) return null;
            Mask m;
            try {
                m = plugin.config().parser().maskNear(mask);
            } catch (ParseException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            if (m.needsNeighbors()) throw new IllegalArgumentException("Masks that look around (above, below, exposed...) are not supported by the API");
            return plugin.edits().compile(m, w);
        }

        private Edits.Op masked(Edits.Op op, Mask.Compiled mc) {
            if (mc == null) return op;
            long seed = ThreadLocalRandom.current().nextLong();
            Supplier<EditSource> src = op.source();
            return new Edits.Op(op.kind(), op.detail() + " (mask " + mask + ")", op.world(), op.bounds(), op.volume(),
                    () -> new MaskedSource(src.get(), mc, seed), op.placed(), op.capture(), op.preview(), op.note(), op.rerollable(),
                    op.after(), op.entities(), op.biomes());
        }

        /** Corre en el hilo principal; un error del que programa completa el futuro con su excepción. */
        private CompletableFuture<EditResult> start(String kind, BiConsumer<Edits.Caller, CompletableFuture<EditResult>> body) {
            CompletableFuture<EditResult> f = new CompletableFuture<>();
            Runnable r = () -> {
                try {
                    if (!plugin.isEnabled() || plugin.edits() == null) throw new IllegalStateException("Worldcraft is not enabled");
                    if (!plugin.config().api().enabled()) {
                        f.complete(EditResult.refused(kind, OFF));
                        return;
                    }
                    body.accept(caller(), f);
                } catch (RuntimeException e) {
                    f.completeExceptionally(e);
                }
            };
            if (Bukkit.isPrimaryThread()) r.run();
            else Bukkit.getScheduler().runTask(plugin, r);
            return f;
        }

        private Edits.Caller caller() {
            if (as != null) return new Edits.Caller(as.getUniqueId(), as.getName(), as, plugin.edits().limits(as), from);
            var cfg = plugin.config();
            return new Edits.Caller(pluginOwner(from.getName()), "#" + from.getName(), null, cfg.api().limits(cfg.limits()), from);
        }
    }
}

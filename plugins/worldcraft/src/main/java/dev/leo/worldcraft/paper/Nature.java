package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.config.Limits;
import dev.leo.worldcraft.config.NatureSettings;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.edit.Snapshot;
import dev.leo.worldcraft.nature.NatureMath;
import dev.leo.worldcraft.nature.TreeKind;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Árboles, plantas y calabazas (plan 033 §1.4) para los comandos y las herramientas: lee la foto por tandas, calcula los
 * lugares en el hilo de cálculo, captura los árboles con {@link Trees.Planter} y termina en una obra del motor (vista previa,
 * límites con los estados de verdad, historial y deshacer). Hilo principal.
 */
public final class Nature {
    private final Worldcraft plugin;

    public Nature(Worldcraft plugin) {
        this.plugin = plugin;
    }

    private Edits edits() {
        return plugin.edits();
    }

    private PaperBlocks blocks() {
        return edits().blocks();
    }

    private dev.leo.worldcraft.config.Messages msg() {
        return plugin.messages();
    }

    private NatureSettings settings() {
        return plugin.config().nature();
    }

    /** H5 CA2, CA3, CA5: árboles sobre el suelo de la región (leída con un bloque más arriba). */
    public void forest(CommandSender who, World w, Region region, TreeKind kind, double density, String kindLabel, String detail,
                       boolean preview, Runnable finished) {
        Box read = readBox(w, region);
        if (read == null || !fits(who, read)) {
            finished.run();
            return;
        }
        Mask.Compiled soil;
        try {
            String extra = kind.extraSoil();
            soil = plugin.config().parser().mask(settings().soil() + (extra.isEmpty() ? "" : ", " + extra)).compile(blocks().table());
        } catch (ParseException e) {
            who.sendMessage(msg().get("error", "message", e.getMessage()));
            finished.run();
            return;
        }
        long seed = ThreadLocalRandom.current().nextLong();
        int max = settings().maxTrees();
        Snapshot snap = new Snapshot(read);
        edits().readOrCancel(who, w, read, kindLabel, snap, () -> {
            IntPredicate isSoil = ids(snap, soil::test), free = ids(snap, free());
            compute(who, () -> NatureMath.spots(snap, region, isSoil, free, density, seed, max), spots -> {
                if (spots.length == 0) {
                    who.sendMessage(msg().get("trees-no-ground"));
                    finished.run();
                    return;
                }
                plant(who, w, spots, kind, seed, kindLabel, detail, preview, finished);
            }, finished);
        }, finished);
    }

    /** H5 CA4: un árbol en ese lugar (el bloque libre sobre el que se mira). */
    public void tree(CommandSender who, World w, int x, int y, int z, TreeKind kind, String kindLabel, Runnable finished) {
        plant(who, w, new long[]{PointsSource.pack(x, y, z)}, kind, ThreadLocalRandom.current().nextLong(), kindLabel, kind.id(), false, finished);
    }

    /** Captura los árboles por tandas y pide la obra con lo capturado. */
    public void plant(CommandSender who, World w, long[] spots, TreeKind kind, long seed, String kindLabel, String detail,
                      boolean preview, Runnable finished) {
        java.util.UUID owner = Edits.owner(who);
        PaperBlocks blocks = blocks();
        IntPredicate log = s -> {
            String m = blocks.table().material(s);
            return m.endsWith("_log") || m.endsWith("_stem") || m.endsWith("_wood") || m.endsWith("_hyphae");
        };
        edits().submitTask(id -> new Trees.Planter(id, owner, plugin, w, blocks, spots, kind, seed, log, r -> {
            // Lo que sigue corre en el tick siguiente, cuando la captura ya salió del planificador (lección 028 F-02).
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (r.planted() == 0) {
                        who.sendMessage(msg().get("trees-none", "count", String.valueOf(r.failed())));
                        return;
                    }
                    who.sendMessage(msg().get(r.failed() > 0 ? "trees-some" : "trees-all", "planted", String.valueOf(r.planted()),
                            "failed", String.valueOf(r.failed())));
                    request(who, w, kindLabel, detail, r.blocks(), preview);
                } finally {
                    finished.run();
                }
            });
        }, finished));
        who.sendMessage(msg().get("trees-growing", "count", String.valueOf(spots.length)));
    }

    // ------------------------------------------------------------------ spec 040: features del juego (ADR 0009)

    /** H1 CA4: un *feature* en ese lugar (los pies o la esquina 1). */
    public void feature(CommandSender who, World w, int x, int y, int z, String id, boolean preview, Runnable finished) {
        features(who, w, new long[]{PointsSource.pack(x, y, z)}, id, ThreadLocalRandom.current().nextLong(), "feature", id, preview, finished);
    }

    /** H2 CA7: *features* sobre el suelo de la región (como el bosque: un lugar libre sobre lo que no es aire ni líquido). */
    public void featureField(CommandSender who, World w, Region region, String id, double density, String label, Runnable finished) {
        Box read = readBox(w, region);
        if (read == null || !fits(who, read)) {
            finished.run();
            return;
        }
        long seed = ThreadLocalRandom.current().nextLong();
        Snapshot snap = new Snapshot(read);
        PaperBlocks b = blocks();
        edits().readOrCancel(who, w, read, label, snap, () -> {
            IntPredicate free = ids(snap, free()), soil = ids(snap, s -> !b.isAir(s) && !free().test(s) && !liquid(s));
            compute(who, () -> NatureMath.spots(snap, region, soil, free, density, seed, settings().maxTrees()), spots -> {
                if (spots.length == 0) {
                    who.sendMessage(msg().get("trees-no-ground"));
                    finished.run();
                    return;
                }
                features(who, w, spots, id, seed, label, id, false, finished);
            }, finished);
        }, finished);
    }

    private boolean liquid(int s) {
        String m = blocks().table().material(s);
        return m.equals("minecraft:water") || m.equals("minecraft:lava");
    }

    private void features(CommandSender who, World w, long[] spots, String id, long seed, String label, String detail, boolean preview,
                          Runnable finished) {
        java.util.UUID owner = Edits.owner(who);
        PaperBlocks blocks = blocks();
        Nms nms = Nms.get();
        boolean[] direct = {false};
        Trees.Capturer cap = (x, y, z, r) -> {
            try {
                var got = nms.capture(w, id, x, y, z, r.nextLong());
                if (got == null) return null;
                Map<Long, Integer> out = new LinkedHashMap<>();
                for (var e : got.entrySet()) out.put(e.getKey(), blocks.id(e.getValue()));
                return out;
            } catch (Nms.Direct d) {
                direct[0] = true;
                return null;
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "The feature " + id + " failed", e);
                return null;
            }
        };
        edits().submitTask(jid -> new Trees.Planter(jid, owner, plugin, w, spots, seed, cap, s -> false, r -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (direct[0]) {
                        who.sendMessage(msg().get("feature-direct", "feature", id));
                        return;
                    }
                    if (r.planted() == 0) {
                        who.sendMessage(msg().get("feature-none", "feature", id));
                        return;
                    }
                    if (spots.length > 1) {
                        who.sendMessage(msg().get(r.failed() > 0 ? "features-some" : "features-all", "planted", String.valueOf(r.planted()),
                                "failed", String.valueOf(r.failed())));
                    }
                    request(who, w, label, detail, r.blocks(), preview);
                } finally {
                    finished.run();
                }
            });
        }, finished));
    }

    /** H6 CA1: plantas sobre el pasto y la arena de la región. */
    public void flora(CommandSender who, World w, Region region, double density, boolean preview) {
        Box read = readBox(w, region);
        if (read == null || !fits(who, read)) return;
        Pattern.Compiled onGrass, onSand;
        Mask.Compiled grass, sand;
        try {
            var parser = plugin.config().parser();
            onGrass = parser.pattern(settings().floraGrass()).compile(blocks().table());
            onSand = parser.pattern(settings().floraSand()).compile(blocks().table());
            grass = parser.mask("grass_block, dirt, podzol, coarse_dirt, rooted_dirt, moss_block").compile(blocks().table());
            sand = parser.mask("sand, red_sand").compile(blocks().table());
        } catch (ParseException e) {
            who.sendMessage(msg().get("error", "message", e.getMessage()));
            return;
        }
        long seed = ThreadLocalRandom.current().nextLong();
        Snapshot snap = new Snapshot(read);
        edits().readOrCancel(who, w, read, "flora", snap, () -> {
            IntPredicate g = ids(snap, grass::test), sa = ids(snap, sand::test), free = ids(snap, free());
            compute(who, () -> NatureMath.flora(snap, region, g, sa, free, density, seed), plants -> {
                Map<Long, Integer> out = new LinkedHashMap<>();
                int air = blocks().air();
                for (NatureMath.Plant p : plants) {
                    int s = (p.onSand() ? onSand : onGrass).apply(p.x(), p.y(), p.z(), air, seed);
                    if (s < 0) continue;
                    if (blocks().table().material(s).equals("minecraft:cactus") && !p.sidesFree()) continue;
                    out.put(PointsSource.pack(p.x(), p.y(), p.z()), s);
                }
                if (out.isEmpty()) {
                    who.sendMessage(msg().get("flora-none"));
                    return;
                }
                request(who, w, "flora", density + "%", out, preview);
            }, () -> {
            });
        }, () -> {
        });
    }

    /** H6 CA2: matas de calabazas sobre el pasto de la región. */
    public void pumpkins(CommandSender who, World w, Region region, double density, boolean preview) {
        Box read = readBox(w, region);
        if (read == null || !fits(who, read)) return;
        int pumpkin = blocks().id(org.bukkit.Material.PUMPKIN.createBlockData());
        int leaves = blocks().id(Bukkit.createBlockData("minecraft:oak_leaves[persistent=true]"));
        int grassId = blocks().id(org.bukkit.Material.GRASS_BLOCK.createBlockData());
        long seed = ThreadLocalRandom.current().nextLong();
        Snapshot snap = new Snapshot(read);
        edits().readOrCancel(who, w, read, "pumpkins", snap, () -> {
            IntPredicate free = ids(snap, free());
            compute(who, () -> NatureMath.pumpkins(snap, region, s -> s == grassId, free, density, seed, pumpkin, leaves), out -> {
                if (out.isEmpty()) {
                    who.sendMessage(msg().get("pumpkins-none"));
                    return;
                }
                request(who, w, "pumpkins", density + "%", out, preview);
            }, () -> {
            });
        }, () -> {
        });
    }

    /** La obra con lo calculado: los estados de verdad van a los límites (blocked_blocks). */
    private void request(CommandSender who, World w, String kind, String detail, Map<Long, Integer> put, boolean preview) {
        long[] pts = put.keySet().stream().mapToLong(Long::longValue).toArray();
        Supplier<dev.leo.worldcraft.edit.EditSource> src = () -> new PointsSource(pts, (x, y, z, c) -> put.get(PointsSource.pack(x, y, z)));
        var probe = src.get();
        edits().request(who, new Edits.Op(kind, detail, w, probe.bounds(), probe.volume(), src, new HashSet<>(put.values()), null), preview);
    }

    /** La región con un bloque más arriba (el aire de encima del suelo), dentro del alto del mundo; null si queda fuera. */
    private Box readBox(World w, Region region) {
        Box b = region.bounds();
        return new Box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY() + 1, b.maxZ()).clampY(w.getMinHeight(), w.getMaxHeight() - 1);
    }

    private boolean fits(CommandSender who, Box read) {
        Limits.Resolved lim = edits().limits(who);
        if (read.volume() > lim.maxBlocks() || read.volume() > Integer.MAX_VALUE - 8) {
            who.sendMessage(msg().get("limit-blocks", "blocks", Edits.fmt(read.volume()), "max", Edits.fmt(lim.maxBlocks()),
                    "groups", String.join(", ", lim.groups())));
            return false;
        }
        return true;
    }

    /** Libre para una planta o un árbol: aire o cobertura (pasto corto, flores), sin los líquidos. */
    private IntPredicate free() {
        var cover = plugin.terrain().masksForCommands().cover();
        PaperBlocks b = blocks();
        return s -> {
            if (b.isAir(s)) return true;
            String m = b.table().material(s);
            return cover.test(s) && !m.equals("minecraft:water") && !m.equals("minecraft:lava");
        };
    }

    /** Lo que cumple test entre los estados de la foto, como un conjunto (el registro se mira aquí, en el hilo principal). */
    private static IntPredicate ids(Snapshot snap, IntPredicate test) {
        Set<Integer> yes = new HashSet<>();
        for (int s : snap.distinct()) if (test.test(s)) yes.add(s);
        return yes::contains;
    }

    private <T> void compute(CommandSender who, Supplier<T> work, java.util.function.Consumer<T> then, Runnable failed) {
        CompletableFuture.supplyAsync(work, plugin.compute()).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().log(Level.SEVERE, "Calculation failed", err);
                who.sendMessage(msg().get("compute-error"));
                failed.run();
                return;
            }
            then.accept(v);
        }));
    }
}

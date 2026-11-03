package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.edit.EditJob;
import dev.leo.worldcraft.edit.RegionSource;
import dev.leo.worldcraft.edit.UndoSource;
import dev.leo.worldcraft.history.HistoryStore;
import dev.leo.worldcraft.history.Journal;
import dev.leo.worldcraft.history.JournalSink;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.ChunkOrder;
import dev.leo.worldcraft.region.Shape;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /wc selftest (H8 CA6, compuerta 2): en una caja de 6×6×6 en el aire sobre el punto de aparición, pone una mezcla
 * con diario, la deshace, la rehace y la deshace otra vez, y comprueba cada paso contra el mundo. El terreno queda
 * como estaba.
 */
public final class SelfTest {
    private final Worldcraft plugin;

    public SelfTest(Worldcraft plugin) {
        this.plugin = plugin;
    }

    public void run(CommandSender s) {
        World w = plugin.getServer().getWorlds().get(0);
        Location sp = w.getSpawnLocation();
        int y = Math.min(sp.getBlockY() + 40, w.getMaxHeight() - 8);
        Box box = new Box(sp.getBlockX() + 8, y, sp.getBlockZ() + 8, sp.getBlockX() + 13, y + 5, sp.getBlockZ() + 13);
        Edits edits = plugin.edits();
        PaperBlocks blocks = edits.blocks();
        List<String> before = read(w, box);
        RegionSource src;
        try {
            src = new RegionSource(box, Shape.FILL, null,
                    plugin.config().parser().pattern("50% stone, 50% glass").compile(blocks.table()), 1234);
        } catch (ParseException e) {
            fail(s, "the test pattern does not parse: " + e.getMessage());
            return;
        }
        HistoryStore.Entry entry = edits.history().begin(Edits.SELFTEST, "Selftest", "set", "selftest", w.getName(), box,
                System.currentTimeMillis());
        var journal = edits.history().journal(Edits.SELFTEST, entry.id());
        PaperWorld pw = new PaperWorld(plugin, w, blocks, ChunkOrder.chunks(box));
        EditJob set = new EditJob(-1, Edits.SELFTEST, "selftest", pw, src, EditJob.Mode.APPLY,
                new JournalSink(journal, blocks.table(), plugin.io(), e -> fail(s, "could not write the history: " + e)),
                null, Long.MAX_VALUE, System::nanoTime);
        s.sendMessage(plugin.messages().get("selftest-start", "box", box.minX() + " " + box.minY() + " " + box.minZ()));
        set.onEnd(() -> {
            pw.close();
            edits.history().finish(entry, set.changed(), set.state() == EditJob.State.DONE);
            List<String> after = read(w, box);
            for (String b : after) {
                if (!b.equals("minecraft:stone") && !b.equals("minecraft:glass")) {
                    fail(s, "after set there is " + b);
                    return;
                }
            }
            step(s, w, box, journal, false, before, "undo", () ->
                    step(s, w, box, journal, true, after, "redo", () ->
                            step(s, w, box, journal, false, before, "undo again", () -> {
                                edits.history().mark(entry, HistoryStore.State.UNDONE);
                                String why = entities(w, box.minX(), box.minY(), box.minZ());
                                if (why != null) {
                                    fail(s, why);
                                    return;
                                }
                                s.sendMessage(plugin.messages().get("selftest-ok", "changed", Edits.fmt(set.changed())));
                                s.sendMessage(plugin.messages().get("selftest-entities-ok"));
                                String tools = tools();
                                if (tools != null) {
                                    fail(s, tools);
                                    return;
                                }
                                s.sendMessage(plugin.messages().get("selftest-tools-ok"));
                                String nature = nature(w, box);
                                if (nature != null) {
                                    fail(s, nature);
                                    return;
                                }
                                String formulas = formulas();
                                if (formulas != null) {
                                    fail(s, formulas);
                                    return;
                                }
                                String create = create();
                                if (create != null) {
                                    fail(s, create);
                                    return;
                                }
                                String restore = restore(s, w);
                                if (restore != null) fail(s, restore);
                                else api(s, w, box);
                            })));
        });
        edits.submitJob(set);
    }

    private void step(CommandSender s, World w, Box box, java.nio.file.Path journal, boolean redo, List<String> expected,
                      String name, Runnable next) {
        CompletableFuture<Journal.Data> data = CompletableFuture.supplyAsync(() -> {
            try {
                return Journal.read(journal);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, plugin.io());
        PaperBlocks blocks = plugin.edits().blocks();
        PaperWorld pw = new PaperWorld(plugin, w, blocks, ChunkOrder.chunks(box));
        EditJob j = new EditJob(-1, Edits.SELFTEST, "selftest " + name, pw, new UndoSource(data, blocks.table(), redo, box),
                EditJob.Mode.APPLY, new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
            }
        }, null, Long.MAX_VALUE, System::nanoTime);
        j.onEnd(() -> {
            pw.close();
            if (j.state() != EditJob.State.DONE) {
                fail(s, name + " did not finish");
                return;
            }
            if (j.skipped() != 0) {
                fail(s, name + " skipped " + j.skipped() + " blocks");
                return;
            }
            List<String> now = read(w, box);
            if (!now.equals(expected)) {
                fail(s, "after " + name + " the blocks are not the expected ones");
                return;
            }
            next.run();
        });
        plugin.edits().submitJob(j);
    }

    /**
     * Spec 031 (compuerta 2): en la caja ya vacía, un soporte con casco y nombre, un marco con una manzana en una pared
     * y un cerdo con una gallina encima. Copia cada uno con PaperEntities, lo crea girado 90° (el marco en otra pared)
     * y pasando por un esquema, y mira que lleguen enteros. Todo se quita al final. null si salió bien.
     */
    private String entities(World w, int x, int y, int z) {
        PaperEntities pe = new PaperEntities(plugin, w);
        List<org.bukkit.entity.Entity> made = new ArrayList<>();
        org.bukkit.block.data.BlockData air = org.bukkit.Material.AIR.createBlockData();
        try {
            w.getBlockAt(x + 1, y, z + 2).setType(org.bukkit.Material.STONE, false);
            w.getBlockAt(x + 4, y, z + 1).setType(org.bukkit.Material.STONE, false);
            org.bukkit.entity.ArmorStand stand = w.spawn(new Location(w, x + 0.5, y, z + 0.5), org.bukkit.entity.ArmorStand.class, a -> {
                a.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_HELMET));
                a.customName(net.kyori.adventure.text.Component.text("WC"));
            });
            made.add(stand);
            org.bukkit.entity.ItemFrame frame = w.spawn(new Location(w, x + 1.5, y + 0.5, z + 1.5), org.bukkit.entity.ItemFrame.class, f -> {
                f.setFacingDirection(org.bukkit.block.BlockFace.NORTH, true);
                f.setItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.APPLE));
            });
            made.add(frame);
            org.bukkit.entity.Pig pig = w.spawn(new Location(w, x + 2.5, y, z + 4.5), org.bukkit.entity.Pig.class, p -> p.setAI(false));
            org.bukkit.entity.Chicken hen = w.spawn(pig.getLocation(), org.bukkit.entity.Chicken.class, c -> c.setAI(false));
            pig.addPassenger(hen);
            made.add(pig);
            made.add(hen);
            if (frame.getFacing() != org.bukkit.block.BlockFace.NORTH) return "entities: the test frame did not hang facing north";

            dev.leo.worldcraft.entity.EntityCopy sc = pe.copy(stand.getUniqueId()), fc = pe.copy(frame.getUniqueId()),
                    pc = pe.copy(pig.getUniqueId());
            if (sc == null || fc == null || pc == null) return "entities: Paper could not copy an entity";
            if (!pc.contains().contains("minecraft:chicken")) return "entities: the copy of the pig does not carry its rider";
            var nbt = dev.leo.worldcraft.schem.Nbt.read(dev.leo.worldcraft.schem.Nbt.gunzip(sc.nbt(), 1 << 20)).tag();
            if (!"minecraft:armor_stand".equals(nbt.get("id")) || !nbt.containsKey("DataVersion")) {
                return "entities: the copied NBT is not what Worldcraft expects (" + nbt.keySet() + ")";
            }
            // El marco, girado 90° alrededor de su bloque y 3 bloques al este: la pared norte pasa a ser la este.
            var rot = dev.leo.worldcraft.edit.Transform.IDENTITY.rotate90();
            var fr = fc.shifted(-(x + 1), -y, -(z + 1)).transformed(rot).shifted(x + 5, y, z + 1);
            java.util.UUID nf = pe.spawn(fr);
            org.bukkit.entity.Entity f2 = nf == null ? null : org.bukkit.Bukkit.getEntity(nf);
            if (f2 != null) made.add(f2);
            if (!(f2 instanceof org.bukkit.entity.ItemFrame frame2)) return "entities: the turned frame was not created";
            if (frame2.getFacing() != org.bukkit.block.BlockFace.EAST) return "entities: the turned frame faces " + frame2.getFacing() + ", not east";
            if (frame2.getLocation().getBlockX() != x + 5 || frame2.getLocation().getBlockZ() != z + 1) {
                return "entities: the turned frame is at " + frame2.getLocation().toVector() + ", not in block " + (x + 5) + " " + y + " " + (z + 1);
            }
            if (frame2.getItem().getType() != org.bukkit.Material.APPLE) return "entities: the turned frame lost its apple";
            // El soporte, pasando por un esquema (NBT sin uuid, con DataVersion) y 2 bloques al sur.
            var viaSchem = dev.leo.worldcraft.schem.SchemEntities.toCopy(dev.leo.worldcraft.schem.SchemEntities.fromCopy(
                    sc.shifted(-x, -y, -z), new int[3]) instanceof java.util.Map<String, Object> m ? normalized(m) : null,
                    new int[]{x, y, z + 2}, TileNbt.serverDataVersion(), TileNbt.serverDataVersion());
            java.util.UUID ns = pe.spawn(viaSchem);
            org.bukkit.entity.Entity s2 = ns == null ? null : org.bukkit.Bukkit.getEntity(ns);
            if (s2 != null) made.add(s2);
            if (!(s2 instanceof org.bukkit.entity.ArmorStand stand2)) return "entities: the armor stand did not come back from a schematic";
            if (stand2.getEquipment().getHelmet().getType() != org.bukkit.Material.DIAMOND_HELMET) return "entities: the armor stand lost its helmet";
            if (stand2.customName() == null) return "entities: the armor stand lost its name";
            if (stand2.getUniqueId().equals(stand.getUniqueId())) return "entities: the copy has the same uuid";
            // El cerdo con su gallina: una sola entidad que se crea con lo montado.
            java.util.UUID np = pe.spawn(pc.shifted(3, 0, 0));
            org.bukkit.entity.Entity p2 = np == null ? null : org.bukkit.Bukkit.getEntity(np);
            if (p2 != null) {
                made.add(p2);
                made.addAll(p2.getPassengers());
            }
            if (p2 == null || p2.getPassengers().isEmpty()) return "entities: the pig came back without its chicken";
            // Quitar con lo montado (como cut y butcher).
            if (!pe.remove(p2.getUniqueId()) || p2.getPassengers().stream().anyMatch(org.bukkit.entity.Entity::isValid)) {
                return "entities: removing the pig left its chicken";
            }
            return null;
        } catch (java.io.IOException | RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Selftest entities", e);
            return "entities: " + e;
        } finally {
            for (org.bukkit.entity.Entity e : made) if (e.isValid()) e.remove();
            w.getBlockAt(x + 1, y, z + 2).setBlockData(air, false);
            w.getBlockAt(x + 4, y, z + 1).setBlockData(air, false);
        }
    }

    /**
     * Spec 032 (compuerta 2): el cycler con el registro real (las unitarias usan una tabla): una escalera tiene 4 caras,
     * 2 mitades y 5 formas, y el trigo 8 edades. null si salió bien.
     */
    private static String tools() {
        java.util.function.Predicate<String> valid = st -> {
            try {
                org.bukkit.Bukkit.createBlockData(st);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        };
        String stair = org.bukkit.Material.OAK_STAIRS.createBlockData().getAsString();
        int[] expected = {4, 2, 5};
        String[] props = {"facing", "half", "shape"};
        for (int i = 0; i < props.length; i++) {
            int n = dev.leo.worldcraft.tool.Cycler.values(stair, props[i], valid).size();
            if (n != expected[i]) return "tools: the cycler finds " + n + " values of " + props[i] + " in " + stair + ", not " + expected[i];
        }
        String wheat = org.bukkit.Material.WHEAT.createBlockData().getAsString();
        if (dev.leo.worldcraft.tool.Cycler.values(wheat, "age", valid).size() != 8) return "tools: the cycler does not find the 8 ages of wheat";
        String turned = dev.leo.worldcraft.tool.Cycler.cycle(stair, "facing", false, valid);
        if (turned == null || !valid.test(turned) || turned.equals(stair)) return "tools: the cycler did not turn the stairs";
        return null;
    }

    /**
     * Spec 033 (compuerta 2): el registro de biomas, un roble capturado de verdad sin que el mundo cambie y la plantilla
     * igloo/top del juego. Dice qué pasó y devuelve null si salió bien.
     */
    private String nature(World w, Box box) {
        List<String> biomes = PaperBiomes.names();
        if (!biomes.contains("minecraft:plains") || !biomes.contains("minecraft:desert")) return "nature: the biome list has no plains or desert";
        if (plugin.config().parser().biome("cherry_grove") == null) return "nature: '$cherry_grove' is not a biome for the masks";
        int x = box.minX() + 2, y = box.minY(), z = box.minZ() + 2;
        List<String> before = read(w, box);
        var tree = Trees.capture(w, plugin.edits().blocks(), x, y, z, dev.leo.worldcraft.nature.TreeKind.OAK, new java.util.Random(7));
        if (tree == null || tree.isEmpty()) return "nature: an oak in the air was not captured";
        var table = plugin.edits().blocks().table();
        if (tree.values().stream().noneMatch(st -> table.material(st).equals("minecraft:oak_log"))) return "nature: the captured oak has no logs";
        if (!read(w, box).equals(before)) return "nature: capturing a tree changed the world";
        org.bukkit.structure.Structure igloo = org.bukkit.Bukkit.getStructureManager().loadStructure(org.bukkit.NamespacedKey.minecraft("igloo/top"), false);
        if (igloo == null || igloo.getPaletteCount() == 0) return "nature: the game template igloo/top did not load";
        var size = igloo.getSize();
        int listed = plugin.structures().all().size();
        plugin.getLogger().info("Selftest nature passed: " + biomes.size() + " biomes; an oak of " + tree.size()
                + " blocks captured without touching the world; igloo/top is " + size.getBlockX() + "×" + size.getBlockY() + "×"
                + size.getBlockZ() + "; " + listed + " templates listed");
        return null;
    }

    /**
     * Spec 034 (compuerta 2): una esfera desde una fórmula, la máscara «=y&lt;0», un bucle cortado por las vueltas y un
     * cálculo cortado por el plazo, con los topes de config.yml. Devuelve null si salió bien.
     */
    private String formulas() {
        var parser = plugin.config().parser();
        try {
            var f = parser.formula("x^2+y^2+z^2<1", List.of("x", "y", "z"));
            Box box = new Box(0, 0, 0, 10, 10, 10);
            var sp = dev.leo.worldcraft.formula.Space.of(dev.leo.worldcraft.formula.Space.Mode.NORMAL, box, new int[3]);
            long[] pts = dev.leo.worldcraft.formula.Shapes.generate(box, f, sp, false, 1, new dev.leo.worldcraft.formula.Shapes.Work(Long.MAX_VALUE));
            java.util.Set<Long> in = new java.util.HashSet<>();
            for (long p : pts) in.add(p);
            if (!in.contains(dev.leo.worldcraft.edit.PointsSource.pack(5, 5, 5)) || in.contains(dev.leo.worldcraft.edit.PointsSource.pack(0, 0, 0))) {
                return "formulas: the sphere of x^2+y^2+z^2<1 is wrong";
            }
            var mask = parser.maskNear("=y<0").compile(plugin.edits().blocks().table());
            int stone = plugin.edits().blocks().table().intern("minecraft:stone");
            if (!mask.test(0, -1, 0, stone, null, 0) || mask.test(0, 1, 0, stone, null, 0)) return "formulas: the mask =y<0 is wrong";
            try {
                parser.formula("while (1) { a++ }", List.of()).run(0).eval();
                return "formulas: an endless loop was not cut";
            } catch (dev.leo.worldcraft.formula.Formula.TooManySteps expected) {
                // bien
            }
            try {
                parser.formula("1", List.of()).run(0).deadline(System.nanoTime() - 1).eval();
                return "formulas: a calculation past its time was not cut";
            } catch (dev.leo.worldcraft.formula.Formula.TimedOut expected) {
                // bien
            }
            plugin.getLogger().info("Selftest formulas passed: a sphere of " + pts.length + " blocks from x^2+y^2+z^2<1, the mask =y<0, a loop cut at "
                    + plugin.config().formulas().maxSteps() + " rounds and a calculation cut at its time");
            return null;
        } catch (dev.leo.worldcraft.pattern.ParseException e) {
            return "formulas: " + e.getMessage();
        }
    }

    /**
     * Spec 035: la tabla de colores con los bloques del servidor (#color y #darken con el registro real), una veta, una
     * cueva y una imagen de 2×2 en memoria (ImageIO del servidor), sin tocar el mundo.
     */
    private String create() {
        try {
            var parser = plugin.config().parser();
            var colors = parser.colors();
            var table = plugin.edits().blocks().table();
            int red = table.intern(parser.pattern("#color[red]").placedStates().get(0));
            if (!table.material(red).equals("minecraft:red_wool")) return "colors: #color[red] is " + table.material(red);
            int light = table.intern(parser.pattern("light_gray_wool").placedStates().get(0));
            int dark = parser.pattern("#darken").compile(table).apply(0, 0, 0, light, 0);
            if (dark < 0) return "colors: #darken left light_gray_wool as it was";
            var box = new Box(0, 0, 0, 31, 31, 31);
            var free = new dev.leo.worldcraft.formula.Shapes.Work(Long.MAX_VALUE);
            var ores = dev.leo.worldcraft.create.Ores.place(box, new dev.leo.worldcraft.create.Ores.Spec(8, 10, 100, 0, 31), 1, free);
            if (ores.veins() != 40) return "ores: " + ores.veins() + " veins instead of 40";
            long[] caves = dev.leo.worldcraft.create.Caves.carve(new Box(0, 0, 0, 63, 31, 63), 3, 40, false, 1, free);
            if (caves.length == 0) return "caves: nothing was cut in 64×32×64";
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, 0xFF000000 | colors.colorOf("minecraft:red_wool"));
            java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", png);
            var px = dev.leo.worldcraft.create.ImageBlocks.read(png.toByteArray());
            int[] b = dev.leo.worldcraft.create.ImageBlocks.blocks(px, colors);
            if (b[1] != -1 || !colors.entries().get(b[0]).id().equals("minecraft:red_wool")) return "image: a 2×2 picture gave the wrong blocks";
            plugin.getLogger().info("Selftest creation passed: " + colors.size() + " block colors (#color[red] = red_wool, #darken of light_gray_wool = "
                    + table.material(dark).replace("minecraft:", "") + "), " + ores.veins() + " ore veins, a cave of " + caves.length
                    + " blocks and a 2×2 picture");
            return null;
        } catch (dev.leo.worldcraft.pattern.ParseException | java.io.IOException | IllegalArgumentException e) {
            return "creation: " + e.getMessage();
        }
    }

    /**
     * Spec 036: el chunk del punto de aparición leído del .mca que escribió Paper (con save-all flush antes) es igual al del
     * mundo, bloque por bloque y con sus datos de bloque; y un mundo temporal con la semilla del mundo se crea, genera ese
     * chunk con la misma capa de abajo y se borra.
     */
    private String restore(CommandSender s, World w) {
        Location sp = w.getSpawnLocation();
        int cx = sp.getBlockX() >> 4, cz = sp.getBlockZ() >> 4;
        Box chunk = Box.chunk(cx, cz, w.getMinHeight(), w.getMaxHeight() - 1);
        PaperBlocks blocks = plugin.edits().blocks();
        java.nio.file.Path folder = w.getWorldFolder().toPath();
        var entry = new dev.leo.worldcraft.snapshot.SnapshotRepo.Entry("selftest", folder.getParent(), false, null, 0, false);
        dev.leo.worldcraft.snapshot.Restore.Raw raw;
        try (var src = dev.leo.worldcraft.snapshot.SnapshotRepo.open(entry, folder.getFileName().toString())) {
            raw = dev.leo.worldcraft.snapshot.Restore.read(chunk, src, true);
        } catch (IOException e) {
            return "backups: could not read the world's region file: " + e.getMessage();
        }
        if (raw.skipped() > 0) return "backups: the spawn chunk is not in the region file yet (" + raw.missing + " missing, " + raw.unfinished
                + " unfinished, " + raw.broken + " broken, " + raw.unsupported + " unsupported " + raw.problem + "): run save-all flush first";
        var res = dev.leo.worldcraft.schem.SchemBlocks.resolve(raw.palette, raw.counts, blocks, blocks.table());
        if (res.unknown() > 0) return "backups: unknown blocks in the region file: " + res.unknownByName().keySet();
        int diff = 0;
        String first = null;
        for (int y = chunk.minY(); y <= chunk.maxY(); y++) for (int z = chunk.minZ(); z <= chunk.maxZ(); z++) for (int x = chunk.minX(); x <= chunk.maxX(); x++) {
            int i = ((y - chunk.minY()) * chunk.depth() + (z - chunk.minZ())) * chunk.width() + (x - chunk.minX());
            int want = blocks.id(w.getBlockData(x, y, z)), got = res.map()[raw.states[i]];
            if (want != got) {
                if (first == null) first = x + " " + y + " " + z + ": the file has " + blocks.table().state(got) + ", the world " + blocks.table().state(want);
                diff++;
            }
        }
        if (diff > 0) return "backups: " + diff + " block(s) of the spawn chunk differ from the region file (" + first + "); run save-all flush first";
        // Un mundo temporal con la misma semilla: la capa de abajo es la misma que la del mundo.
        Regen regen = plugin.regen();
        if (regen.busy() != null) return "regen: a regen is running, try again";
        World temp;
        try {
            temp = regen.acquire(w, w.getSeed(), null, "selftest");
        } catch (Regen.Problem e) {
            return "regen: " + e.getMessage();
        }
        String why = null;
        try {
            temp.getChunkAt(cx, cz);
            int bottom = w.getMinHeight();
            for (int z = chunk.minZ(); z <= chunk.maxZ() && why == null; z++) for (int x = chunk.minX(); x <= chunk.maxX(); x++) {
                String a = temp.getBlockData(x, bottom, z).getAsString(), b = w.getBlockData(x, bottom, z).getAsString();
                if (!a.equals(b)) {
                    why = "regen: at " + x + " " + bottom + " " + z + " the temporary world has " + a + " and the world " + b;
                    break;
                }
            }
        } finally {
            regen.release();
            regen.discard();
        }
        if (why != null) return why;
        s.sendMessage(plugin.messages().get("selftest-restore-ok", "blocks", Edits.fmt(chunk.volume()), "tiles", String.valueOf(raw.tiles.size()),
                "biomes", String.valueOf(raw.biomePalette.length), "ms", String.valueOf(regen.lastCreateMs())));
        return null;
    }


    /**
     * Spec 037 (compuerta 2): la API registrada; una obra a nombre de Worldcraft hecha y deshecha; otra detenida por un
     * oyente que no cambia nada. En una caja de 3×3×3 de la caja del selftest (ya deshecha: aire).
     */
    private void api(CommandSender s, World w, Box box) {
        dev.leo.worldcraft.api.WorldcraftApi api;
        try {
            api = dev.leo.worldcraft.api.WorldcraftApi.get();
        } catch (IllegalStateException e) {
            fail(s, "api: " + e.getMessage());
            return;
        }
        org.bukkit.util.BlockVector a = new org.bukkit.util.BlockVector(box.minX(), box.minY(), box.minZ());
        org.bukkit.util.BlockVector b = new org.bukkit.util.BlockVector(box.minX() + 2, box.minY() + 2, box.minZ() + 2);
        Box small = Box.of(box.minX(), box.minY(), box.minZ(), box.minX() + 2, box.minY() + 2, box.minZ() + 2);
        List<String> before = read(w, small);
        org.bukkit.event.Listener stop = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void on(dev.leo.worldcraft.api.event.WorldcraftEditEvent ev) {
                if (ev.getPlugin() == plugin && ev.getKind().equals("replace")) ev.setCancelReason("selftest");
            }
        };
        plugin.getServer().getPluginManager().registerEvents(stop, plugin);
        Runnable done = () -> org.bukkit.event.HandlerList.unregisterAll(stop);
        api.edit(plugin).in(w, a, b).set("glass").thenCompose(r -> {
            if (!r.done() || r.changed() != 27) throw new IllegalStateException("a set of 27 blocks gave " + r);
            for (String x : read(w, small)) if (!x.equals("minecraft:glass")) throw new IllegalStateException("after the API set there is " + x);
            return api.edit(plugin).in(w, a, b).replace("glass", "stone");
        }).thenCompose(r -> {
            if (r.status() != dev.leo.worldcraft.api.EditResult.Status.REFUSED || !r.reason().contains("selftest")) {
                throw new IllegalStateException("the edit a listener stopped gave " + r);
            }
            for (String x : read(w, small)) if (!x.equals("minecraft:glass")) throw new IllegalStateException("a stopped edit changed a block to " + x);
            return api.edit(plugin).undo(1);
        }).whenComplete((r, t) -> {
            done.run();
            if (t != null) {
                fail(s, "api: " + (t.getCause() != null ? t.getCause().getMessage() : t.getMessage()));
                return;
            }
            if (!r.done() || r.changed() != 27 || !read(w, small).equals(before)) {
                fail(s, "api: the undo gave " + r);
                return;
            }
            s.sendMessage(plugin.messages().get("selftest-api-ok", "version", String.valueOf(api.apiVersion()), "blocks", "27"));
            slash(s);
            game(s, w, box);
        });
    }

    /** Spec 039 (compuerta 2): con los // prendidos, //set es de Worldcraft (o quién lo tiene). */
    private void slash(CommandSender s) {
        if (!plugin.config().slash().enabled()) {
            s.sendMessage(plugin.messages().get("selftest-slash-off"));
            return;
        }
        String owner = plugin.slash().setOwner();
        if (owner != null && !plugin.config().slash().skip().contains("/set")) {
            fail(s, "//set belongs to " + owner + ", not to Worldcraft");
            return;
        }
        s.sendMessage(plugin.messages().get("selftest-slash-ok", "count", String.valueOf(Math.max(0, plugin.slash().count() - 1))));
    }
    /**
     * Spec 040 (compuerta 2, ADR 0009): el puente con el juego. Unos features capturados sin tocar el mundo (la zona queda
     * igual bloque por bloque), uno de minerales cortado y la forma de una valla junto a un bloque sólido.
     */
    private void game(CommandSender s, World w, Box box) {
        Nms nms = Nms.get();
        if (!nms.available()) {
            s.sendMessage(plugin.messages().get("selftest-game-off", "reason", nms.problem()));
            return;
        }
        int x = box.minX() + 40, z = box.minZ() + 40, y = w.getHighestBlockYAt(x, z) + 1;
        Box around = new Box(x - 12, y - 12, z - 12, x + 12, y + 24, z + 12).clampY(w.getMinHeight(), w.getMaxHeight() - 1);
        List<String> before = read(w, around);
        List<String> got = new ArrayList<>();
        for (String id : List.of("minecraft:forest_rock", "minecraft:huge_red_mushroom", "minecraft:amethyst_geode", "minecraft:patch_grass")) {
            if (!nms.hasFeature(id)) continue;
            try {
                var m = nms.capture(w, id, x, y, z, 42);
                if (m != null) got.add(id.replace("minecraft:", "") + " " + m.size());
            } catch (Nms.Direct d) {
                got.add(id.replace("minecraft:", "") + " direct");
            }
        }
        String ore;
        try {
            ore = nms.capture(w, "minecraft:ore_diamond_small", x, Math.max(w.getMinHeight() + 1, y - 3), z, 7) == null ? "nothing" : "captured";
        } catch (Nms.Direct d) {
            ore = "stopped (" + d.getMessage() + ")";
        }
        if (!before.equals(read(w, around))) {
            fail(s, "a game feature changed the world");
            return;
        }
        if (got.isEmpty()) {
            fail(s, "no game feature could be captured at " + x + " " + y + " " + z);
            return;
        }
        // Una valla rodeada de suelo sólido (la forma se calcula sin ponerla) se une al bloque del este.
        String fence = "no solid block found";
        search:
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    int px = x + dx, py = y + dy, pz = z + dz;
                    var east = w.getBlockAt(px + 1, py, pz);
                    if (!east.getBlockData().isFaceSturdy(org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockSupport.FULL)) continue;
                    var shaped = nms.shape(w, px, py, pz, org.bukkit.Bukkit.createBlockData("minecraft:oak_fence"));
                    if (!shaped.getAsString().contains("east=true")) {
                        fail(s, "a fence next to " + east.getType().getKey() + " does not connect: " + shaped.getAsString());
                        return;
                    }
                    fence = "a fence next to " + east.getType().getKey().getKey() + " connects";
                    break search;
                }
            }
        }
        if (fence.startsWith("no ")) {
            fail(s, "//update: " + fence + " near " + x + " " + y + " " + z);
            return;
        }
        plugin.getLogger().info("Selftest game features passed: " + nms.features().size() + " features; captured without touching the world: "
                + String.join(", ", got) + "; ore_diamond_small " + ore + "; " + fence);
        s.sendMessage(plugin.messages().get("selftest-game-ok", "count", String.valueOf(nms.features().size())));
    }

    /** Lo que da fromCopy, en la forma que lee toCopy (Pos como double[], como al leer un archivo). */
    private static java.util.Map<String, Object> normalized(java.util.Map<String, Object> m) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>(m);
        var pos = (dev.leo.worldcraft.schem.Nbt.TagList) m.get("Pos");
        out.put("Pos", new double[]{(Double) pos.items().get(0), (Double) pos.items().get(1), (Double) pos.items().get(2)});
        return out;
    }

    private static List<String> read(World w, Box b) {
        List<String> out = new ArrayList<>();
        for (int x = b.minX(); x <= b.maxX(); x++) for (int y = b.minY(); y <= b.maxY(); y++)
            for (int z = b.minZ(); z <= b.maxZ(); z++) out.add(w.getBlockData(x, y, z).getAsString());
        return out;
    }

    private void fail(CommandSender s, String why) {
        s.sendMessage(plugin.messages().get("selftest-failed", "reason", why));
    }
}

package dev.leo.blockwright.paper;

import dev.leo.blockwright.Blockwright;
import dev.leo.blockwright.edit.EditJob;
import dev.leo.blockwright.edit.RegionSource;
import dev.leo.blockwright.edit.UndoSource;
import dev.leo.blockwright.history.HistoryStore;
import dev.leo.blockwright.history.Journal;
import dev.leo.blockwright.history.JournalSink;
import dev.leo.blockwright.pattern.ParseException;
import dev.leo.blockwright.region.Box;
import dev.leo.blockwright.region.ChunkOrder;
import dev.leo.blockwright.region.Shape;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /bwr selftest (H8 CA6, compuerta 2): en una caja de 6×6×6 en el aire sobre el punto de aparición, pone una mezcla
 * con diario, la deshace, la rehace y la deshace otra vez, y comprueba cada paso contra el mundo. El terreno queda
 * como estaba.
 */
public final class SelfTest {
    private final Blockwright plugin;

    public SelfTest(Blockwright plugin) {
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
                                s.sendMessage(plugin.messages().get("selftest-ok", "changed", Edits.fmt(set.changed())));
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

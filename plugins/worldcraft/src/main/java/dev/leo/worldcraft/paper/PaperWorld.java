package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.edit.EditWorld;
import dev.leo.worldcraft.region.ChunkOrder;
import dev.leo.worldcraft.schem.TileData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * El mundo de Paper para un trabajo (plan 025 §1.4, §3). Cada chunk se carga con getChunkAtAsync (sin bloquear el
 * tick) y se sostiene con un ticket del plugin mientras el trabajo lo usa; se piden por adelantado los siguientes del
 * recorrido. Los datos de bloque que se guardan: lo que hay en un contenedor y el texto de un cartel.
 */
public final class PaperWorld implements EditWorld {
    private static final int PREFETCH = 6;
    private static final byte CONTAINER = 'C', SIGN = 'S';

    private final Plugin plugin;
    private final World world;
    private final PaperBlocks blocks;
    private final long[] order;
    private final Map<Long, Integer> position = new HashMap<>();
    private final Set<Long> held = new HashSet<>(), loading = new HashSet<>();
    private boolean closed;

    /** order: los chunks en el orden en que el trabajo los va a usar (para pedirlos antes). */
    public PaperWorld(Plugin plugin, World world, PaperBlocks blocks, long[] order) {
        this.plugin = plugin;
        this.world = world;
        this.blocks = blocks;
        this.order = order;
        for (int i = 0; i < order.length; i++) position.putIfAbsent(order[i], i);
    }

    public World bukkit() {
        return world;
    }

    @Override
    public String name() {
        return world.getName();
    }

    @Override
    public int minY() {
        return world.getMinHeight();
    }

    @Override
    public int maxY() {
        return world.getMaxHeight() - 1;
    }

    @Override
    public int get(int x, int y, int z) {
        return blocks.id(world.getBlockData(x, y, z));
    }

    @Override
    public void set(int x, int y, int z, int state) {
        world.getBlockAt(x, y, z).setBlockData(blocks.data(state), false);
    }

    @Override
    public byte[] tile(int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        if (!blocks.hasTile(blocks.id(b.getBlockData()))) return null;
        BlockState st = b.getState(false);
        try {
            if (st instanceof Container c) {
                ItemStack[] items = c.getInventory().getContents();
                boolean any = false;
                for (ItemStack i : items) if (i != null && !i.isEmpty()) any = true;
                if (!any) {
                    // Spec 036: un cofre sin abrir de una estructura (el de regen) guarda su tabla de botín, como las plantillas.
                    if (st instanceof org.bukkit.loot.Lootable l && l.getLootTable() != null) {
                        byte[] k = l.getLootTable().getKey().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        byte[] out = new byte[k.length + 1];
                        out[0] = Structures.LOOT;
                        System.arraycopy(k, 0, out, 1, k.length);
                        return out;
                    }
                    return null;
                }
                byte[] raw = ItemStack.serializeItemsAsBytes(items);
                byte[] out = new byte[raw.length + 1];
                out[0] = CONTAINER;
                System.arraycopy(raw, 0, out, 1, raw.length);
                return out;
            }
            if (st instanceof Sign s) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeByte(SIGN);
                boolean any = false;
                for (Side side : Side.values()) {
                    SignSide ss = s.getSide(side);
                    for (int i = 0; i < 4; i++) {
                        Component line = ss.line(i);
                        if (!Component.empty().equals(line)) any = true;
                        out.writeUTF(GsonComponentSerializer.gson().serialize(line));
                    }
                    out.writeBoolean(ss.isGlowingText());
                }
                out.writeBoolean(s.isWaxed());
                return any ? bytes.toByteArray() : null;
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Could not save the contents of the block at " + x + " " + y + " " + z + ": " + e);
        }
        return null;
    }

    @Override
    public void restoreTile(int x, int y, int z, byte[] data) {
        if (data == null || data.length == 0) return;
        BlockState st = world.getBlockAt(x, y, z).getState();
        try {
            if (data[0] == CONTAINER && st instanceof Container c) {
                byte[] raw = new byte[data.length - 1];
                System.arraycopy(data, 1, raw, 0, raw.length);
                ItemStack[] items = ItemStack.deserializeItemsFromBytes(raw);
                ItemStack[] fit = new ItemStack[c.getSnapshotInventory().getSize()];
                System.arraycopy(items, 0, fit, 0, Math.min(items.length, fit.length));
                c.getSnapshotInventory().setContents(fit);
                st.update(true, false);
            } else if (data[0] == SIGN && st instanceof Sign s) {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 1, data.length - 1));
                for (Side side : Side.values()) {
                    SignSide ss = s.getSide(side);
                    for (int i = 0; i < 4; i++) ss.line(i, GsonComponentSerializer.gson().deserialize(in.readUTF()));
                    ss.setGlowingText(in.readBoolean());
                }
                s.setWaxed(in.readBoolean());
                st.update(true, false);
            } else if (data[0] == Structures.LOOT && st instanceof org.bukkit.loot.Lootable l) {
                // Spec 033 H7 CA3: el cofre de una plantilla del juego, con su tabla de botín.
                String key = new String(data, 1, data.length - 1, java.nio.charset.StandardCharsets.UTF_8);
                org.bukkit.NamespacedKey nk = org.bukkit.NamespacedKey.fromString(key);
                org.bukkit.loot.LootTable table = nk == null ? null : org.bukkit.Bukkit.getLootTable(nk);
                if (table != null) {
                    l.setLootTable(table);
                    st.update(true, false);
                }
            } else if (TileData.is(data)) {
                // Spec 027: los datos de un esquema, en la forma estándar del juego.
                TileNbt.restore(st, TileData.decode(data));
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Could not restore the contents of the block at " + x + " " + y + " " + z + ": " + e);
        }
    }

    @Override
    public boolean ready(int cx, int cz) {
        long key = ChunkOrder.pack(cx, cz);
        prefetch(key);
        if (held.contains(key)) return true;
        if (world.isChunkLoaded(cx, cz)) {
            hold(cx, cz, key);
            return true;
        }
        request(cx, cz, key);
        return false;
    }

    private void prefetch(long key) {
        Integer i = position.get(key);
        if (i == null) return;
        for (int k = i + 1; k < Math.min(order.length, i + 1 + PREFETCH); k++) {
            long n = order[k];
            if (!held.contains(n)) request(ChunkOrder.chunkX(n), ChunkOrder.chunkZ(n), n);
        }
    }

    private void request(int cx, int cz, long key) {
        if (closed || !loading.add(key)) return;
        // Paper completa el futuro en el hilo principal.
        world.getChunkAtAsync(cx, cz).whenComplete((chunk, error) -> {
            loading.remove(key);
            if (error == null && !closed) hold(cx, cz, key);
        });
    }

    private void hold(int cx, int cz, long key) {
        if (held.add(key)) world.addPluginChunkTicket(cx, cz, plugin);
    }

    @Override
    public void release(int cx, int cz) {
        long key = ChunkOrder.pack(cx, cz);
        if (held.remove(key)) world.removePluginChunkTicket(cx, cz, plugin);
    }

    /** Suelta todo (al terminar o cancelar el trabajo). */
    public void close() {
        closed = true;
        for (long k : held) world.removePluginChunkTicket(ChunkOrder.chunkX(k), ChunkOrder.chunkZ(k), plugin);
        held.clear();
    }
}

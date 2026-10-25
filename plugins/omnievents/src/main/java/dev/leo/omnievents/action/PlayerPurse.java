package dev.leo.omnievents.action;

import dev.leo.omnievents.expr.Scope;
import dev.leo.omnievents.hook.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** El bolsillo de un jugador conectado (spec 012): Vault, experiencia, inventario y variables. Hilo principal. */
public final class PlayerPurse implements Purse {
    private final ExecContext ctx;
    private final Economy eco;

    public PlayerPurse(ExecContext ctx, Economy eco) {
        this.ctx = ctx;
        this.eco = eco;
    }

    private Player p() {
        return ctx.requirePlayer();
    }

    @Override
    public Double money() {
        return eco.available() ? eco.balance(p()) : null;
    }

    @Override
    public boolean takeMoney(double n) {
        return eco.available() && eco.withdraw(p(), n);
    }

    @Override
    public boolean giveMoney(double n) {
        return eco.available() && eco.deposit(p(), n);
    }

    @Override
    public String formatMoney(double n) {
        return eco.format(n);
    }

    @Override
    public int levels() {
        return p().getLevel();
    }

    @Override
    public void setLevels(int levels) {
        p().setLevel(Math.max(0, levels));
    }

    @Override
    public int exp() {
        return p().calculateTotalExperiencePoints();
    }

    @Override
    public void setExp(int total) {
        p().setExperienceLevelAndProgress(Math.max(0, total));
    }

    @Override
    public int count(String material) {
        Material m = Material.matchMaterial(material);
        return m == null ? 0 : count(p().getInventory(), m);
    }

    /** Cuántos ítems de ese material lleva, tengan o no nombre (inventario y mano secundaria, sin la armadura). */
    public static int count(PlayerInventory inv, Material m) {
        int n = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            if (i >= 36 && i < 40) continue;
            ItemStack it = inv.getItem(i);
            if (it != null && it.getType() == m) n += it.getAmount();
        }
        return n;
    }

    /** Como remove_item (spec 002, F-02): por material, sin la armadura; guarda copias de lo quitado para devolverlo. */
    @Override
    public Object takeItems(String material, int n) {
        Material m = Material.matchMaterial(material);
        if (m == null || count(material) < n) return null;
        PlayerInventory inv = p().getInventory();
        List<ItemStack> taken = new ArrayList<>();
        int left = n;
        for (int i = 0; i < inv.getSize() && left > 0; i++) {
            if (i >= 36 && i < 40) continue;
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() != m) continue;
            int take = Math.min(left, it.getAmount());
            ItemStack copy = it.clone();
            copy.setAmount(take);
            taken.add(copy);
            left -= take;
            if (take == it.getAmount()) inv.setItem(i, null);
            else {
                it.setAmount(it.getAmount() - take);
                inv.setItem(i, it);
            }
        }
        return taken;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void giveBack(Object taken) {
        Player pl = p();
        for (ItemStack it : (List<ItemStack>) taken) {
            Map<Integer, ItemStack> rest = pl.getInventory().addItem(it);
            for (ItemStack r : rest.values()) pl.getWorld().dropItemNaturally(pl.getLocation(), r);
        }
    }

    @Override
    public Object var(Scope scope, String name) {
        return ctx.getVar(scope, name);
    }

    @Override
    public void setVar(Scope scope, String name, Object value) {
        ctx.setVar(scope, name, value);
    }

    @Override
    public String who() {
        Player p = ctx.playerOrNull();
        return p == null ? "(sin jugador)" : p.getName();
    }
}

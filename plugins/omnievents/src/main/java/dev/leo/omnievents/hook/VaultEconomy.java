package dev.leo.omnievents.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Solo se instancia si Vault está instalado (constitución VI.2). El proveedor se busca en cada uso (plan §1.1). */
public final class VaultEconomy implements Economy {
    private static RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> registration() {
        return Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
    }

    private static net.milkbowl.vault.economy.Economy eco() {
        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> r = registration();
        return r == null ? null : r.getProvider();
    }

    @Override
    public boolean available() {
        net.milkbowl.vault.economy.Economy e = eco();
        return e != null && e.isEnabled();
    }

    @Override
    public String provider() {
        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> r = registration();
        return r == null ? null : r.getPlugin().getName();
    }

    @Override
    public double balance(OfflinePlayer p) {
        net.milkbowl.vault.economy.Economy e = eco();
        return e == null ? 0 : e.getBalance(p);
    }

    @Override
    public boolean withdraw(OfflinePlayer p, double n) {
        net.milkbowl.vault.economy.Economy e = eco();
        return e != null && e.withdrawPlayer(p, n).transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer p, double n) {
        net.milkbowl.vault.economy.Economy e = eco();
        return e != null && e.depositPlayer(p, n).transactionSuccess();
    }

    @Override
    public String format(double n) {
        net.milkbowl.vault.economy.Economy e = eco();
        return e == null ? NONE.format(n) : e.format(n);
    }
}

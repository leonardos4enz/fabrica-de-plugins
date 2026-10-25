package dev.leo.omnievents.hook;

import org.bukkit.OfflinePlayer;

/**
 * Dinero de los jugadores (spec 012, H1). La implementación real habla con Vault ({@link VaultEconomy}); sin Vault,
 * {@link #NONE} dice que no hay economía. Solo se usa en el hilo principal.
 */
public interface Economy {
    /** Mensaje de error cuando no hay economía (H1 CA2). */
    String MISSING = "no hay economía: instala Vault y un plugin de economía (EssentialsX, CMI…)";

    /** ¿Hay una economía lista ahora? Se pregunta en cada uso: una economía puede registrarse después de OmniEvents. */
    boolean available();

    /** Nombre del plugin de economía (EssentialsX, CMI…), o null. */
    String provider();

    double balance(OfflinePlayer p);

    /** Retira n (n > 0); false si la economía lo rechaza. */
    boolean withdraw(OfflinePlayer p, double n);

    /** Deposita n (n > 0); false si la economía lo rechaza. */
    boolean deposit(OfflinePlayer p, double n);

    /** El texto de una cantidad con la moneda de la economía. */
    String format(double n);

    Economy NONE = new Economy() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String provider() {
            return null;
        }

        @Override
        public double balance(OfflinePlayer p) {
            return 0;
        }

        @Override
        public boolean withdraw(OfflinePlayer p, double n) {
            return false;
        }

        @Override
        public boolean deposit(OfflinePlayer p, double n) {
            return false;
        }

        @Override
        public String format(double n) {
            return dev.leo.omnievents.expr.Values.formatNumber(n);
        }
    };
}

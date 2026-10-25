package dev.leo.omnievents.hook;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.expr.Values;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/** Solo se carga si PlaceholderAPI está instalado (constitución VI.2). */
public final class PapiHook {
    private PapiHook() {
    }

    public static void install(OmniEvents plugin) {
        plugin.placeholders().hook(PlaceholderAPI::setPlaceholders);
        if (plugin.settings().placeholderApi()) new Expansion(plugin).register();
    }

    /** %omni_var_x%, %omni_gvar_x%, %omni_cooldown_regla%, %omni_uses_regla% (H7 CA3), %omni_state_regla% (spec 008). */
    static final class Expansion extends PlaceholderExpansion {
        private final OmniEvents plugin;

        Expansion(OmniEvents plugin) {
            this.plugin = plugin;
        }

        @Override
        public @NotNull String getIdentifier() {
            return "omni";
        }

        @Override
        public @NotNull String getAuthor() {
            return "Leo";
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getPluginMeta().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, @NotNull String params) {
            int u = params.indexOf('_');
            if (u < 0) return null;
            String kind = params.substring(0, u), arg = params.substring(u + 1);
            return switch (kind) {
                case "var", "pvar" -> player == null || !plugin.data().isLoaded(player.getUniqueId()) ? ""
                        : Values.str(plugin.data().player(player.getUniqueId()).get(arg));
                case "gvar" -> Values.str(plugin.data().global().get(arg));
                case "cooldown" -> Values.formatNumber(plugin.rules().cooldownLeftSeconds(arg, player == null ? null : player.getUniqueId()));
                case "uses" -> String.valueOf(plugin.rules().uses(arg, player == null ? null : player.getUniqueId()));
                case "state" -> String.valueOf(plugin.states().isIn(arg, player == null ? null : player.getUniqueId()));
                default -> null;
            };
        }
    }
}

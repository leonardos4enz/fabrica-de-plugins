package dev.leo.omnievents;

import dev.leo.omnievents.text.TextFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** messages.yml traducible (constitución V.1). Los valores interpolados se escapan siempre. */
public final class Messages {
    private final YamlConfiguration own;
    private final YamlConfiguration defaults;

    public Messages(OmniEvents plugin) {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        this.own = YamlConfiguration.loadConfiguration(f);
        YamlConfiguration d = new YamlConfiguration();
        try (InputStreamReader r = new InputStreamReader(plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            d = YamlConfiguration.loadConfiguration(r);
        } catch (Exception ignored) {
            // sin valores por defecto
        }
        this.defaults = d;
    }

    public String raw(String key) {
        String v = own.getString(key);
        if (v == null) v = defaults.getString(key, "<red>Falta el mensaje '" + key + "'");
        return v;
    }

    /** Mensaje con el prefijo; args = clave, valor, clave, valor… reemplaza {clave}. */
    public Component get(String key, Object... args) {
        String s = raw(key).replace("{prefix}", raw("prefix"));
        for (int i = 0; i + 1 < args.length; i += 2) {
            s = s.replace("{" + args[i] + "}", MiniMessage.miniMessage().escapeTags(String.valueOf(args[i + 1])));
        }
        return TextFormat.parse(s);
    }
}

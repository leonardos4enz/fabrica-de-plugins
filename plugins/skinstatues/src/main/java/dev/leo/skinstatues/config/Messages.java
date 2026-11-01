package dev.leo.skinstatues.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * messages.yml traducible (constitución V.1, ADR 0005: en inglés por defecto). Los valores interpolados se escapan
 * siempre: un nombre o un texto del jugador nunca se lee como etiqueta MiniMessage (II.2).
 */
public final class Messages {
    private final YamlConfiguration own;
    private final YamlConfiguration defaults;

    public Messages(File file, InputStream defaultsStream) {
        this.own = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration d = new YamlConfiguration();
        if (defaultsStream != null) {
            try (InputStreamReader r = new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)) {
                d = YamlConfiguration.loadConfiguration(r);
            } catch (Exception ignored) {
                // sin valores por defecto
            }
        }
        this.defaults = d;
    }

    public String raw(String key) {
        String v = own.getString(key);
        if (v == null) v = defaults.getString(key, "<red>Missing message '" + key + "'");
        return v;
    }

    /** Mensaje con el prefijo; args = clave, valor, clave, valor… reemplaza {clave}. */
    public Component get(String key, Object... args) {
        return MiniMessage.miniMessage().deserialize(text(key, args));
    }

    /** El texto MiniMessage ya armado (sin interpretar). */
    public String text(String key, Object... args) {
        String s = raw(key).replace("{prefix}", raw("prefix"));
        for (int i = 0; i + 1 < args.length; i += 2) {
            s = s.replace("{" + args[i] + "}", MiniMessage.miniMessage().escapeTags(String.valueOf(args[i + 1])));
        }
        return s;
    }
}

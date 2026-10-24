package dev.leo.omnievents.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formato de textos: códigos &amp;/§ heredados → MiniMessage, escape de lo interpolado y saneo para comandos. */
public final class TextFormat {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    private static final Pattern BUKKIT_HEX = Pattern.compile("[&§]x((?:[&§][0-9a-fA-F]){6})");
    private static final Pattern CODE = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");

    private TextFormat() {
    }

    public static Component parse(String miniWithLegacy) {
        return MM.deserialize(legacyToMini(miniWithLegacy));
    }

    /** Convierte &amp;a, §l, &amp;#RRGGBB y &amp;x&amp;R&amp;R… en etiquetas MiniMessage. */
    public static String legacyToMini(String s) {
        if (s.indexOf('&') < 0 && s.indexOf('§') < 0) return s;
        Matcher bx = BUKKIT_HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (bx.find()) {
            String hex = bx.group(1).replaceAll("[&§]", "");
            bx.appendReplacement(sb, Matcher.quoteReplacement("<reset><#" + hex + ">"));
        }
        bx.appendTail(sb);
        s = HEX.matcher(sb.toString()).replaceAll("<reset><#$1>");
        Matcher m = CODE.matcher(s);
        sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(tag(Character.toLowerCase(m.group(1).charAt(0)))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String tag(char c) {
        return switch (c) {
            case '0' -> "<reset><black>";
            case '1' -> "<reset><dark_blue>";
            case '2' -> "<reset><dark_green>";
            case '3' -> "<reset><dark_aqua>";
            case '4' -> "<reset><dark_red>";
            case '5' -> "<reset><dark_purple>";
            case '6' -> "<reset><gold>";
            case '7' -> "<reset><gray>";
            case '8' -> "<reset><dark_gray>";
            case '9' -> "<reset><blue>";
            case 'a' -> "<reset><green>";
            case 'b' -> "<reset><aqua>";
            case 'c' -> "<reset><red>";
            case 'd' -> "<reset><light_purple>";
            case 'e' -> "<reset><yellow>";
            case 'f' -> "<reset><white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            default -> "<reset>";
        };
    }

    /** Texto no confiable dentro de un mensaje: no puede abrir etiquetas MiniMessage ni usar §. */
    public static String escapeForMessage(String untrusted) {
        return MM.escapeTags(untrusted.replace("§", "&"));
    }

    /** Texto no confiable dentro de un comando: sin saltos de línea ni §. */
    public static String sanitizeForCommand(String untrusted) {
        return untrusted.replace("\r", " ").replace("\n", " ").replace("§", "").replace("\u0000", "");
    }

    /** Quita todos los formatos (para registros y comparaciones). */
    public static String plain(String s) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(parse(s));
    }
}

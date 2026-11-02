package dev.leo.worldcraft.net;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Copiado de SkinStatues (ADR 0007). El link de una imagen (spec 023 H1, H2 CA2, H3; spec 035 H4 CA4), ya validado y normalizado a https, sin pedir nada a la red. Su
 * {@link #label()} (`url:sitio#huella`) es lo único que se guarda o se muestra: el link entero no sale de aquí.
 */
public record ImageLink(URI uri, String host, String hash) {
    public static final int MAX_LENGTH = 512;
    /** Caracteres de una URL (RFC 3986: no reservados, reservados y %). */
    private static final Pattern URL_CHARS = Pattern.compile("[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");

    /** Si el argumento se escribió como un link (y no como el nombre de una cuenta). */
    public static boolean looksLike(String arg) {
        String a = arg.toLowerCase(Locale.ROOT);
        return a.startsWith("https://") || a.startsWith("http://");
    }

    /**
     * @param anyPort la suite en el juego (servidor local) usa otro puerto; en un servidor de verdad, solo el 443
     */
    public static ImageLink parse(String text, boolean anyPort) throws LinkException {
        if (text.length() > MAX_LENGTH) throw invalid("longer than " + MAX_LENGTH + " characters");
        if (!URL_CHARS.matcher(text).matches()) throw invalid("it has characters a link cannot have");
        URI u;
        try {
            u = new URI(text).normalize();
        } catch (URISyntaxException e) {
            throw invalid("it is not a valid link");
        }
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) throw invalid("only https:// links");
        if (u.getRawUserInfo() != null) throw invalid("links with a user or password are not allowed");
        String host = u.getHost();
        if (host == null || host.isEmpty()) throw invalid("it has no site");
        host = host.toLowerCase(Locale.ROOT);
        if (u.getPort() != -1 && u.getPort() != 443 && !anyPort) throw invalid("only the standard https port");
        String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
        String norm = "https://" + (host.contains(":") ? "[" + host.replaceAll("^\\[|]$", "") + "]" : host)
                + (u.getPort() == -1 || u.getPort() == 443 ? "" : ":" + u.getPort())
                + path + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
        URI https;
        try {
            https = new URI(norm);
        } catch (URISyntaxException e) {
            throw invalid("it is not a valid link");
        }
        return new ImageLink(https, host.replaceAll("^\\[|]$", ""), sha256(norm));
    }

    /** Lo que se guarda y se muestra: el sitio y los 8 primeros caracteres de la huella (H3 CA1). */
    public String label() {
        return "url:" + host + "#" + hash.substring(0, 8);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LinkException invalid(String reason) {
        return new LinkException("invalid-link", "reason", reason);
    }
}

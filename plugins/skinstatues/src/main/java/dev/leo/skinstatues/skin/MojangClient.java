package dev.leo.skinstatues.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Skin y capa de una cuenta por su nombre (H2 CA2, CA5; plan 021 §1.3). Solo habla con los tres servidores de Mojang y
 * por https. Es bloqueante: se llama desde un hilo asíncrono. La red se inyecta para probar sin internet.
 */
public final class MojangClient {
    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Set<String> HOSTS = Set.of("api.mojang.com", "sessionserver.mojang.com", "textures.minecraft.net");

    public record Response(int status, byte[] body) {
    }

    public interface Http {
        Response get(URI uri) throws IOException;
    }

    public record Profile(String uuid, String name, boolean slim, byte[] skin, byte[] cape) {
    }

    private final Http http;

    public MojangClient(Http http) {
        this.http = http;
    }

    public Profile fetch(String name) throws SkinException {
        if (!NAME.matcher(name).matches()) throw new SkinException("invalid-name", "name", name);
        JsonObject account = json(get(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name), name, true), name);
        if (account == null) throw new SkinException("account-not-found", "name", name);
        String uuid = text(account, "id");
        if (uuid == null || !uuid.matches("[0-9a-fA-F]{32}")) throw new SkinException("mojang-error", "name", name, "reason", "bad profile");
        JsonObject profile = json(get(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid), name, true), name);
        if (profile == null) throw new SkinException("account-not-found", "name", name);
        String realName = text(profile, "name");
        String skinUrl, capeUrl;
        boolean slim;
        try {
            JsonObject textures = null;
            if (profile.get("properties") != null && profile.get("properties").isJsonArray()) {
                for (JsonElement p : profile.getAsJsonArray("properties")) {
                    if (p.isJsonObject() && "textures".equals(text(p.getAsJsonObject(), "name"))) {
                        String value = text(p.getAsJsonObject(), "value");
                        String decoded = new String(Base64.getDecoder().decode(value == null ? "" : value), StandardCharsets.UTF_8);
                        JsonElement t = JsonParser.parseString(decoded).getAsJsonObject().get("textures");
                        if (t != null && t.isJsonObject()) textures = t.getAsJsonObject();
                    }
                }
            }
            JsonObject skin = textures == null || !textures.has("SKIN") ? null : textures.getAsJsonObject("SKIN");
            // Sin skin propia la cuenta usa una de las de Mojang por defecto: esas no se bajan (no hay URL).
            if (skin == null) throw new SkinException("no-skin", "name", name);
            slim = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")
                    && "slim".equalsIgnoreCase(skin.getAsJsonObject("metadata").get("model").getAsString());
            skinUrl = text(skin, "url");
            capeUrl = textures.has("CAPE") ? text(textures.getAsJsonObject("CAPE"), "url") : null;
        } catch (RuntimeException e) {
            throw new SkinException("mojang-error", "name", name, "reason", "unreadable profile");
        }
        byte[] skinPng = get(texture(skinUrl), name, false).body();
        byte[] capePng = capeUrl == null ? null : get(texture(capeUrl), name, false).body();
        return new Profile(uuid.toLowerCase(Locale.ROOT), realName == null ? name : realName, slim, skinPng, capePng);
    }

    /** URL de textura: se fuerza https y solo se acepta el servidor de texturas de Mojang. */
    static URI texture(String url) throws SkinException {
        try {
            URI u = URI.create(url == null ? "" : url);
            if ("http".equalsIgnoreCase(u.getScheme())) u = new URI("https", u.getAuthority(), u.getPath(), u.getQuery(), null);
            if (!"https".equalsIgnoreCase(u.getScheme()) || !"textures.minecraft.net".equalsIgnoreCase(u.getHost())) {
                throw new SkinException("mojang-error", "name", "?", "reason", "texture outside textures.minecraft.net");
            }
            return u;
        } catch (IllegalArgumentException | java.net.URISyntaxException e) {
            throw new SkinException("mojang-error", "name", "?", "reason", "bad texture URL");
        }
    }

    private Response get(URI uri, String name, boolean notFoundIsEmpty) throws SkinException {
        if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost())) {
            throw new SkinException("mojang-error", "name", name, "reason", "host not allowed: " + uri.getHost());
        }
        Response r;
        try {
            r = http.get(uri);
        } catch (IOException e) {
            throw new SkinException("mojang-error", "name", name, "reason", "no answer from " + uri.getHost());
        }
        if (notFoundIsEmpty && (r.status() == 204 || r.status() == 404)) return new Response(r.status(), new byte[0]);
        if (r.status() == 429) throw new SkinException("mojang-error", "name", name, "reason", "too many requests, try again in a minute");
        if (r.status() != 200) throw new SkinException("mojang-error", "name", name, "reason", "HTTP " + r.status() + " from " + uri.getHost());
        if (r.body().length > SkinPng.MAX_BYTES) throw new SkinException("mojang-error", "name", name, "reason", "answer too large");
        return r;
    }

    private static JsonObject json(Response r, String name) throws SkinException {
        if (r.body().length == 0) return null;
        try {
            JsonElement e = JsonParser.parseString(new String(r.body(), StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            throw new SkinException("mojang-error", "name", name, "reason", "unreadable answer");
        }
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? null : e.getAsString();
    }

    /** La red de verdad: java.net.http con tiempo de espera y tope de 1 MB por respuesta. */
    public static Http realHttp() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return uri -> {
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "SkinStatues").GET().build();
            try {
                HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = res.body()) {
                    return new Response(res.statusCode(), in.readNBytes(SkinPng.MAX_BYTES + 1));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
        };
    }
}

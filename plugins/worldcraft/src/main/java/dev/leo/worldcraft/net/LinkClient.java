package dev.leo.worldcraft.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Copiado de SkinStatues (ADR 0007: un arreglo aquí va también allá). Baja la imagen de un link (spec 023 H2; plan 023 §1): solo de direcciones públicas, comprobadas en cada redirección,
 * con tope de tamaño y tiempo de espera. Es bloqueante: se llama desde un hilo asíncrono. La red y el DNS se inyectan
 * para probar sin internet.
 */
public final class LinkClient {
    public static final int MAX_REDIRECTS = 3;

    public record Response(int status, String location, byte[] body) {
    }

    public interface Http {
        Response get(URI uri, int maxBytes) throws IOException;
    }

    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final Http http;
    private final Resolver resolver;
    /** Solo para la suite en el juego (servidor HTTPS local): deja pedir a direcciones locales y a cualquier puerto. */
    private final boolean localLinks;

    public LinkClient(Http http, Resolver resolver, boolean localLinks) {
        this.http = http;
        this.resolver = resolver;
        this.localLinks = localLinks;
    }

    public boolean localLinks() {
        return localLinks;
    }

    /** Los bytes de la imagen, como mucho maxBytes (sin validar como imagen: eso lo hace ImageBlocks.read). */
    public byte[] fetch(ImageLink link, List<String> allowedHosts, int maxBytes) throws LinkException {
        ImageLink at = link;
        for (int hop = 0; ; hop++) {
            check(at, allowedHosts, link);
            Response r;
            try {
                r = http.get(at.uri(), maxBytes);
            } catch (IOException e) {
                throw error(link, "no answer from " + at.host());
            }
            int s = r.status();
            if (s == 301 || s == 302 || s == 303 || s == 307 || s == 308) {
                if (hop >= MAX_REDIRECTS) throw error(link, "too many redirects");
                if (r.location() == null || r.location().isBlank()) throw error(link, "redirect without a destination");
                String next;
                try {
                    next = at.uri().resolve(r.location().trim()).toString();
                } catch (IllegalArgumentException e) {
                    throw error(link, "redirect to an invalid link");
                }
                try {
                    at = ImageLink.parse(next, localLinks);
                } catch (LinkException e) {
                    throw error(link, "redirect to a link that is not allowed (" + e.args()[1] + ")");
                }
                continue;
            }
            if (s != 200) throw error(link, "HTTP " + s + " from " + at.host());
            if (r.body().length > maxBytes) throw error(link, "the file is larger than " + maxBytes / 1024 + " KB (images.max_file_kb)");
            return r.body();
        }
    }

    /** Sitio permitido y todas sus direcciones públicas (H2 CA3, CA4). */
    private void check(ImageLink at, List<String> allowedHosts, ImageLink original) throws LinkException {
        if (!LinkPolicy.hostAllowed(at.host(), allowedHosts)) {
            throw new LinkException("host-not-allowed", "host", at.host(), "hosts", String.join(", ", allowedHosts));
        }
        if (localLinks) return;
        InetAddress[] all;
        try {
            all = resolver.resolve(at.host());
        } catch (UnknownHostException e) {
            throw error(original, "the site " + at.host() + " does not exist");
        }
        if (all == null || all.length == 0) throw error(original, "the site " + at.host() + " does not exist");
        for (InetAddress a : all) {
            if (!LinkPolicy.isPublic(a)) throw error(original, at.host() + " is not a public internet address");
        }
    }

    private static LinkException error(ImageLink link, String reason) {
        return new LinkException("link-error", "host", link.host(), "reason", reason);
    }

    /** DNS de verdad: llena la caché de Java que usa luego HttpClient (plan 023 §1). */
    public static Resolver realResolver() {
        return InetAddress::getAllByName;
    }

    /**
     * La red de verdad: sin seguir redirecciones (se siguen arriba, revisando cada una), sin cookies, tope de bytes y 8 s
     * por pedido contando el cuerpo (un sitio que manda la imagen gota a gota no deja el hilo colgado).
     */
    public static Http realHttp() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return (uri, maxBytes) -> {
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Worldcraft").header("Accept", "image/*").GET().build();
            AtomicReference<InputStream> open = new AtomicReference<>();
            CompletableFuture<Response> f = client.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream()).thenApply(res -> {
                open.set(res.body());
                try (InputStream in = res.body()) {
                    byte[] body = res.statusCode() == 200 ? in.readNBytes(maxBytes + 1) : new byte[0];
                    return new Response(res.statusCode(), res.headers().firstValue("Location").orElse(null), body);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            });
            try {
                return f.get(8, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                f.cancel(true);
                InputStream in = open.get();
                if (in != null) in.close();
                throw new IOException("timed out");
            } catch (ExecutionException e) {
                throw new IOException(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
        };
    }
}

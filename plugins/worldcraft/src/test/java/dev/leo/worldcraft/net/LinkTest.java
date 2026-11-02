package dev.leo.worldcraft.net;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec 035 H4 CA4: la red segura copiada de SkinStatues (ADR 0007), con sus mismas pruebas de SSRF, sin red real
 * (respuestas y DNS falsos). Si se arregla algo aquí, también en SkinLinkTest de SkinStatues.
 */
class LinkTest {
    static final String LINK = "https://i.imgur.com/logo.png";
    static final int MAX = 1024;
    static final byte[] IMAGE = {(byte) 0x89, 'P', 'N', 'G'};

    static final class FakeHttp implements LinkClient.Http {
        final Map<String, LinkClient.Response> answers = new HashMap<>();
        final List<URI> asked = new ArrayList<>();

        @Override
        public LinkClient.Response get(URI uri, int maxBytes) throws IOException {
            asked.add(uri);
            LinkClient.Response r = answers.get(uri.toString());
            if (r == null) throw new IOException("offline");
            return r;
        }

        FakeHttp ok(String url, byte[] body) {
            answers.put(url, new LinkClient.Response(200, null, body));
            return this;
        }

        FakeHttp redirect(String url, String to) {
            answers.put(url, new LinkClient.Response(302, to, new byte[0]));
            return this;
        }
    }

    static final class FakeDns implements LinkClient.Resolver {
        final Map<String, String[]> names = new HashMap<>();

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            if (host.contains(":") || host.matches("[\\d.]+")) return new InetAddress[]{InetAddress.getByName(host)};
            String[] ips = names.get(host);
            if (ips == null) throw new UnknownHostException(host);
            InetAddress[] out = new InetAddress[ips.length];
            for (int i = 0; i < ips.length; i++) out[i] = InetAddress.getByName(ips[i]);
            return out;
        }

        FakeDns add(String host, String... ips) {
            names.put(host, ips);
            return this;
        }
    }

    static FakeDns publicDns() {
        return new FakeDns().add("i.imgur.com", "151.101.4.193").add("cdn.example.com", "93.184.216.34", "2606:2800:220:1::1");
    }

    static LinkException fails(ImageLink link, FakeHttp http, FakeDns dns, List<String> hosts) {
        return assertThrows(LinkException.class, () -> new LinkClient(http, dns, false).fetch(link, hosts, MAX));
    }

    static String reason(LinkException e) {
        for (int i = 0; i + 1 < e.args().length; i += 2) if (e.args()[i].equals("reason")) return String.valueOf(e.args()[i + 1]);
        return "";
    }

    @Test
    void H4_CA4_link_normalizado_y_etiqueta_sin_el_link() throws Exception {
        assertTrue(ImageLink.looksLike("HTTPS://x.com/a.png"));
        assertFalse(ImageLink.looksLike("logo.png"));
        ImageLink l = ImageLink.parse(LINK, false);
        assertEquals("url:i.imgur.com#" + l.hash().substring(0, 8), l.label());
        assertFalse(l.label().contains("logo"), "la etiqueta (historial) no lleva el link");
        assertEquals("https", ImageLink.parse("http://I.IMGUR.com/logo.png", false).uri().getScheme());
        for (String b : new String[]{"ftp://x.com/a.png", "https://user:pw@x.com/a.png", "https://x.com:8080/a.png",
                "https://x.com/<red>a.png", "https://x.com/a b.png", "https://x.com/" + "a".repeat(600)}) {
            assertEquals("invalid-link", assertThrows(LinkException.class, () -> ImageLink.parse(b, false), b).key(), b);
        }
    }

    @Test
    void H4_CA4_solo_direcciones_publicas() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "0.0.0.0", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254",
                "100.64.0.1", "::1", "fe80::1", "fc00::1", "::ffff:127.0.0.1", "64:ff9b::10.0.0.1"}) {
            assertFalse(LinkPolicy.isPublic(InetAddress.getByName(ip)), ip);
        }
        for (String ip : new String[]{"8.8.8.8", "151.101.4.193", "2606:2800:220:1::1"}) assertTrue(LinkPolicy.isPublic(InetAddress.getByName(ip)), ip);
        assertTrue(LinkPolicy.hostAllowed("i.imgur.com", List.of("*.imgur.com")));
        assertFalse(LinkPolicy.hostAllowed("imgur.com.evil.net", List.of("*.imgur.com")));
    }

    @Test
    void H4_CA4_baja_y_sigue_redirecciones_publicas() throws Exception {
        FakeHttp http = new FakeHttp().redirect(LINK, "https://cdn.example.com/s.png").ok("https://cdn.example.com/s.png", IMAGE);
        assertArrayEquals(IMAGE, new LinkClient(http, publicDns(), false).fetch(ImageLink.parse(LINK, false), List.of(), MAX));
        assertEquals(2, http.asked.size());
    }

    @Test
    void H4_CA4_nada_a_la_red_local_ni_por_ip_ni_por_nombre_ni_por_redireccion() throws Exception {
        FakeDns dns = publicDns().add("lan.example.com", "192.168.0.10").add("mixed.example.com", "8.8.8.8", "10.0.0.1");
        for (String link : new String[]{"https://127.0.0.1/a.png", "https://[::1]/a.png", "https://lan.example.com/a.png",
                "https://mixed.example.com/a.png"}) {
            FakeHttp http = new FakeHttp();
            LinkException e = fails(ImageLink.parse(link, false), http, dns, List.of());
            assertTrue(reason(e).contains("not a public"), link + " → " + reason(e));
            assertTrue(http.asked.isEmpty(), "no se pidió nada: " + link);
        }
        for (String to : new String[]{"https://127.0.0.1/admin", "https://localhost/x", "http://169.254.169.254/latest",
                "https://cdn.example.com:8443/x"}) {
            FakeHttp http = new FakeHttp().redirect(LINK, to);
            LinkException e = fails(ImageLink.parse(LINK, false), http, publicDns().add("localhost", "127.0.0.1"), List.of());
            assertEquals("link-error", e.key(), to);
            assertEquals(1, http.asked.size(), "solo se pidió el primero: " + to);
        }
    }

    @Test
    void H4_CA4_sitios_tope_de_bytes_y_redirecciones() throws Exception {
        ImageLink l = ImageLink.parse(LINK, false);
        assertEquals("host-not-allowed", fails(ImageLink.parse("https://cdn.example.com/a.png", false), new FakeHttp(), publicDns(),
                List.of("*.imgur.com")).key());
        FakeHttp big = new FakeHttp().ok(LINK, new byte[MAX + 1]);
        assertTrue(reason(fails(l, big, publicDns(), List.of())).contains("larger than 1 KB (images.max_file_kb)"));
        FakeHttp loop = new FakeHttp();
        for (int i = 0; i < 5; i++) loop.redirect(i == 0 ? LINK : "https://i.imgur.com/" + i, "https://i.imgur.com/" + (i + 1));
        assertTrue(reason(fails(l, loop, publicDns(), List.of())).contains("too many redirects"));
        assertTrue(reason(fails(l, new FakeHttp(), publicDns(), List.of())).contains("no answer"));
    }

    @Test
    void la_suite_puede_usar_un_servidor_local_solo_con_la_opcion_de_prueba() throws Exception {
        ImageLink l = ImageLink.parse("https://127.0.0.1:8443/a.png", true);
        FakeHttp http = new FakeHttp().ok("https://127.0.0.1:8443/a.png", IMAGE);
        assertNotNull(new LinkClient(http, new FakeDns(), true).fetch(l, List.of(), MAX));
        assertThrows(LinkException.class, () -> ImageLink.parse("https://127.0.0.1:8443/a.png", false));
    }
}

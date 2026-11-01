package dev.leo.skinstatues.skin;

import dev.leo.skinstatues.model.TestSkins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 023: skins por link, sin red real (respuestas y DNS falsos, como MojangClientTest). */
class SkinLinkTest {
    static final String LINK = "https://i.imgur.com/Skin01.png";

    /** Red falsa: link → respuesta; cuenta lo que se pidió. */
    static final class FakeHttp implements LinkClient.Http {
        final Map<String, LinkClient.Response> answers = new HashMap<>();
        final List<URI> asked = new ArrayList<>();

        @Override
        public LinkClient.Response get(URI uri) throws IOException {
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

    /** DNS falso: sitio → direcciones; un sitio que no está no existe. */
    static final class FakeDns implements LinkClient.Resolver {
        final Map<String, String[]> names = new HashMap<>();

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            // Una IP escrita en el link «resuelve» a sí misma, como en el DNS de verdad.
            if (host.contains(":") || host.matches("[\\d.]+")) return new InetAddress[]{InetAddress.getByName(host)};
            String[] ips = names.get(host);
            if (ips == null) throw new UnknownHostException(host);
            InetAddress[] out = new InetAddress[ips.length];
            for (int i = 0; i < ips.length; i++) out[i] = InetAddress.getByName(ips[i]); // literales: sin red
            return out;
        }

        FakeDns add(String host, String... ips) {
            names.put(host, ips);
            return this;
        }
    }

    static FakeDns publicDns() {
        return new FakeDns().add("i.imgur.com", "151.101.4.193").add("cdn.example.com", "93.184.216.34", "2606:2800:220:1::1")
                .add("textures.minecraft.net", "13.107.246.40");
    }

    static byte[] skinPng() throws Exception {
        return SkinTest.png(TestSkins.image());
    }

    static SkinException fails(SkinLink link, FakeHttp http, FakeDns dns, List<String> hosts) {
        return assertThrows(SkinException.class, () -> new LinkClient(http, dns, false).fetch(link, hosts));
    }

    static String reason(SkinException e) {
        for (int i = 0; i + 1 < e.args().length; i += 2) if (e.args()[i].equals("reason")) return String.valueOf(e.args()[i + 1]);
        return "";
    }

    // ---------------------------------------------------------------- el link (sin red)

    @Test
    void H1_CA1_CA2_y_H3_CA1_link_normalizado_etiqueta_y_huella() throws Exception {
        assertTrue(SkinLink.looksLike("HTTPS://x.com/a.png"));
        assertTrue(SkinLink.looksLike("http://x.com/a.png"));
        assertFalse(SkinLink.looksLike("Notch"));
        SkinLink l = SkinLink.parse(LINK, false);
        assertEquals("i.imgur.com", l.host());
        assertEquals(64, l.hash().length());
        assertEquals("url:i.imgur.com#" + l.hash().substring(0, 8), l.label());
        assertFalse(l.label().contains("Skin01"), "la etiqueta no lleva el link");
        // http pasa a https; el sitio, a minúsculas; el :443 y el fragmento no cambian la huella.
        SkinLink h = SkinLink.parse("http://I.IMGUR.com:443/Skin01.png#x", false);
        assertEquals("https", h.uri().getScheme());
        assertEquals(l.hash(), h.hash());
        assertNotEquals(l.hash(), SkinLink.parse("https://i.imgur.com/skin01.png", false).hash(), "la ruta distingue mayúsculas");
    }

    @Test
    void H2_CA2_links_que_se_rechazan_sin_pedir_nada() {
        String[] bad = {
                "ftp://x.com/a.png", "https://", "https:///a.png", "https://user:pw@x.com/a.png", "https://x.com:8080/a.png",
                "https://x.com/<red>a.png", "https://x.com/a b.png", "https://x.com/a\n.png", "https://x.com/" + "a".repeat(600),
                "https://x.com/\"a\".png", "https://x.com/a{b}.png"};
        for (String b : bad) {
            assertEquals("invalid-link", assertThrows(SkinException.class, () -> SkinLink.parse(b, false), b).key(), b);
        }
    }

    // ---------------------------------------------------------------- direcciones

    @Test
    void H2_CA3_solo_direcciones_publicas() throws Exception {
        String[] priv = {"127.0.0.1", "127.8.9.1", "0.0.0.0", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1",
                "169.254.169.254", "100.64.0.1", "100.127.1.1", "192.0.0.8", "198.18.0.1", "224.0.0.1", "240.0.0.1",
                "255.255.255.255", "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "fec0::1", "::ffff:127.0.0.1",
                "::ffff:192.168.0.1", "::127.0.0.1", "64:ff9b::10.0.0.1", "2001:db8::1", "ff02::1"};
        for (String ip : priv) assertFalse(LinkPolicy.isPublic(InetAddress.getByName(ip)), ip);
        String[] pub = {"8.8.8.8", "151.101.4.193", "172.32.0.1", "100.128.0.1", "2606:2800:220:1::1", "::ffff:8.8.8.8",
                "64:ff9b::808:808"};
        for (String ip : pub) assertTrue(LinkPolicy.isPublic(InetAddress.getByName(ip)), ip);
    }

    @Test
    void H2_CA4_lista_de_sitios() {
        assertTrue(LinkPolicy.hostAllowed("cualquiera.com", List.of()));
        List<String> l = List.of("textures.minecraft.net", "*.imgur.com");
        assertTrue(LinkPolicy.hostAllowed("textures.minecraft.net", l));
        assertTrue(LinkPolicy.hostAllowed("i.imgur.com", l));
        assertFalse(LinkPolicy.hostAllowed("imgur.com", l), "*.imgur.com es de los subdominios");
        assertFalse(LinkPolicy.hostAllowed("evilimgur.com", l));
        assertFalse(LinkPolicy.hostAllowed("imgur.com.evil.net", l));
    }

    // ---------------------------------------------------------------- descarga

    @Test
    void H1_CA1_baja_la_imagen_y_sigue_una_redireccion_publica() throws Exception {
        FakeHttp http = new FakeHttp().redirect(LINK, "https://cdn.example.com/s.png").ok("https://cdn.example.com/s.png", skinPng());
        byte[] got = new LinkClient(http, publicDns(), false).fetch(SkinLink.parse(LINK, false), List.of());
        assertArrayEquals(skinPng(), got);
        assertEquals(2, http.asked.size());
        // Una redirección relativa también.
        FakeHttp rel = new FakeHttp().redirect(LINK, "/other.png").ok("https://i.imgur.com/other.png", skinPng());
        assertNotNull(new LinkClient(rel, publicDns(), false).fetch(SkinLink.parse(LINK, false), List.of()));
    }

    @Test
    void H2_CA3_nada_a_la_red_local_ni_por_ip_ni_por_nombre_ni_por_redireccion() throws Exception {
        FakeDns dns = publicDns().add("lan.example.com", "192.168.0.10").add("mixed.example.com", "8.8.8.8", "10.0.0.1");
        for (String link : new String[]{"https://127.0.0.1/a.png", "https://[::1]/a.png", "https://192.168.1.1/a.png",
                "https://lan.example.com/a.png", "https://mixed.example.com/a.png"}) {
            FakeHttp http = new FakeHttp();
            SkinException e = fails(SkinLink.parse(link, false), http, dns, List.of());
            assertEquals("link-error", e.key(), link);
            assertTrue(reason(e).contains("not a public"), link + " → " + reason(e));
            assertTrue(http.asked.isEmpty(), "no se pidió nada: " + link);
        }
        // Un sitio público que redirige a localhost (o a http, o a otro puerto): se para antes de pedirlo.
        for (String to : new String[]{"https://127.0.0.1/admin", "https://localhost/x", "http://169.254.169.254/latest",
                "https://cdn.example.com:8443/x", "ftp://cdn.example.com/x"}) {
            FakeHttp http = new FakeHttp().redirect(LINK, to);
            FakeDns d = publicDns().add("localhost", "127.0.0.1");
            SkinException e = fails(SkinLink.parse(LINK, false), http, d, List.of());
            assertEquals("link-error", e.key(), to);
            assertEquals(1, http.asked.size(), "solo se pidió el primero: " + to);
        }
        // Un sitio que no existe.
        assertTrue(reason(fails(SkinLink.parse("https://nadie.example/a.png", false), new FakeHttp(), dns, List.of()))
                .contains("does not exist"));
    }

    @Test
    void H2_CA4_sitios_fuera_de_la_lista_y_redirecciones_fuera_de_la_lista() throws Exception {
        List<String> only = List.of("*.imgur.com");
        FakeHttp http = new FakeHttp();
        SkinException e = fails(SkinLink.parse("https://cdn.example.com/a.png", false), http, publicDns(), only);
        assertEquals("host-not-allowed", e.key());
        assertTrue(http.asked.isEmpty());
        FakeHttp r = new FakeHttp().redirect(LINK, "https://cdn.example.com/s.png").ok("https://cdn.example.com/s.png", skinPng());
        assertEquals("host-not-allowed", fails(SkinLink.parse(LINK, false), r, publicDns(), only).key());
        assertEquals(1, r.asked.size());
    }

    @Test
    void H2_CA5_errores_del_sitio_demasiadas_redirecciones_y_archivos_grandes() throws Exception {
        SkinLink l = SkinLink.parse(LINK, false);
        assertTrue(reason(fails(l, new FakeHttp(), publicDns(), List.of())).contains("no answer"));
        FakeHttp nf = new FakeHttp();
        nf.answers.put(LINK, new LinkClient.Response(404, null, new byte[0]));
        assertTrue(reason(fails(l, nf, publicDns(), List.of())).contains("HTTP 404"));
        FakeHttp loop = new FakeHttp();
        for (int i = 0; i < 5; i++) loop.redirect(i == 0 ? LINK : "https://i.imgur.com/" + i, "https://i.imgur.com/" + (i + 1));
        assertTrue(reason(fails(l, loop, publicDns(), List.of())).contains("too many redirects"));
        assertEquals(LinkClient.MAX_REDIRECTS + 1, loop.asked.size());
        FakeHttp big = new FakeHttp().ok(LINK, new byte[SkinPng.MAX_BYTES + 1]);
        assertTrue(reason(fails(l, big, publicDns(), List.of())).contains("larger than 1 MB"));
        FakeHttp noLoc = new FakeHttp();
        noLoc.answers.put(LINK, new LinkClient.Response(301, null, new byte[0]));
        assertTrue(reason(fails(l, noLoc, publicDns(), List.of())).contains("without a destination"));
    }

    @Test
    void la_suite_puede_usar_un_servidor_local_solo_con_la_opcion_de_prueba() throws Exception {
        SkinLink l = SkinLink.parse("https://127.0.0.1:8443/a.png", true);
        FakeHttp http = new FakeHttp().ok("https://127.0.0.1:8443/a.png", skinPng());
        assertNotNull(new LinkClient(http, new FakeDns(), true).fetch(l, List.of()));
        assertThrows(SkinException.class, () -> SkinLink.parse("https://127.0.0.1:8443/a.png", false));
    }

    // ---------------------------------------------------------------- formato y modelo

    @Test
    void H2_CA1_solo_un_formato_de_skin() throws Exception {
        String[] what = {"html", "128", "jpg", "empty", "noihdr"};
        BufferedImage big = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        byte[] png = skinPng();
        byte[] noIhdr = png.clone();
        noIhdr[12] = 'X';
        byte[][] data = {"<html>hola</html>".getBytes(StandardCharsets.UTF_8), SkinTest.png(big),
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                new byte[0], noIhdr};
        for (int i = 0; i < data.length; i++) {
            final byte[] d = data[i];
            assertEquals("image-invalid", assertThrows(SkinException.class, () -> SkinPng.read(d, SkinPng.Kind.SKIN, "x"), what[i]).key(), what[i]);
        }
    }

    @Test
    void H1_CA3_el_modelo_se_detecta_de_la_imagen() {
        BufferedImage classic = TestSkins.image();
        assertFalse(Skin.detectSlim(classic));
        BufferedImage slim = TestSkins.image();
        TestSkins.fill(slim, 54, 20, 56, 32, 0);
        TestSkins.fill(slim, 46, 52, 48, 64, 0);
        assertTrue(Skin.detectSlim(slim));
        BufferedImage half = TestSkins.image();
        TestSkins.fill(half, 54, 20, 56, 32, 0); // solo el brazo derecho: no alcanza
        assertFalse(Skin.detectSlim(half));
        assertFalse(Skin.detectSlim(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB)), "la de 64×32 es clásica");
        Skin s = Skin.of(slim, true, null);
        assertFalse(s.withSlim(false).slim());
        assertSame(s, s.withSlim(true));
    }

    // ---------------------------------------------------------------- caché

    @Test
    void H1_CA6_y_H3_CA2_cache_por_huella_y_copia_vencida(@TempDir Path dir) throws Exception {
        BufferedImage slim = TestSkins.image();
        TestSkins.fill(slim, 54, 20, 56, 32, 0);
        TestSkins.fill(slim, 46, 52, 48, 64, 0);
        FakeHttp http = new FakeHttp().ok(LINK, SkinTest.png(slim));
        Clock t0 = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);
        Path cache = dir.resolve("cache");
        LinkClient client = new LinkClient(http, publicDns(), false);
        SkinRepository repo = new SkinRepository(dir.resolve("skins"), cache, null, client, t0);
        SkinLink l = SkinLink.parse(LINK, false);

        SkinRepository.Result r = repo.loadLink(l, List.of(), 24);
        assertEquals("link (i.imgur.com)", r.source());
        assertTrue(r.skin().slim(), "modelo detectado");
        assertFalse(r.skin().hasCape());
        assertEquals("cache", repo.loadLink(l, List.of(), 24).source());
        assertEquals(1, http.asked.size(), "la segunda vez no se pide");

        // Los archivos de la caché llevan la huella, nunca el link.
        try (Stream<Path> files = Files.walk(cache)) {
            for (Path p : files.toList()) {
                assertFalse(p.toString().contains("Skin01") || p.toString().contains("imgur.com"), p.toString());
                if (Files.isRegularFile(p) && p.toString().endsWith(".yml")) {
                    assertFalse(Files.readString(p).contains("Skin01"), "el .yml no guarda el link");
                }
            }
        }

        // Vencida y el sitio no contesta: sirve la copia. Vencida y el sitio manda otra cosa: no se usa ni se guarda.
        http.answers.clear();
        SkinRepository later = new SkinRepository(dir.resolve("skins"), cache, null, client, Clock.offset(t0, Duration.ofHours(30)));
        assertEquals("cache", later.loadLink(l, List.of(), 24).source());
        http.ok(LINK, "<html>now a page</html>".getBytes(StandardCharsets.UTF_8));
        assertEquals("image-invalid", assertThrows(SkinException.class, () -> later.loadLink(l, List.of(), 24)).key());
        SkinLink other = SkinLink.parse("https://i.imgur.com/page.png", false);
        http.ok("https://i.imgur.com/page.png", "<html/>".getBytes(StandardCharsets.UTF_8));
        assertThrows(SkinException.class, () -> later.loadLink(other, List.of(), 24));
        assertFalse(Files.exists(cache.resolve("links").resolve(other.hash() + ".png")), "lo que no es una skin no llega a la caché");
    }
}

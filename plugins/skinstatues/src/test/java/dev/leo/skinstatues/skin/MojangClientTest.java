package dev.leo.skinstatues.skin;

import dev.leo.skinstatues.model.TestSkins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Sin red real: respuestas falsas con la forma de las de Mojang (H2 CA2–CA5). */
class MojangClientTest {
    static final String UUID = "069a79f444e94726a5befca90e38aaf5";

    static final class FakeHttp implements MojangClient.Http {
        final Map<String, MojangClient.Response> answers = new HashMap<>();
        final List<URI> asked = new ArrayList<>();

        @Override
        public MojangClient.Response get(URI uri) throws IOException {
            asked.add(uri);
            MojangClient.Response r = answers.get(uri.toString());
            if (r == null) throw new IOException("offline");
            return r;
        }

        void json(String url, String body) {
            answers.put(url, new MojangClient.Response(200, body.getBytes(StandardCharsets.UTF_8)));
        }
    }

    static FakeHttp account(String skinUrl, String capeUrl, boolean slim) throws Exception {
        FakeHttp h = new FakeHttp();
        h.json("https://api.mojang.com/users/profiles/minecraft/Builder", "{\"id\":\"" + UUID + "\",\"name\":\"Builder\"}");
        String tex = "{\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl + "\"" + (slim ? ",\"metadata\":{\"model\":\"slim\"}" : "") + "}"
                + (capeUrl == null ? "" : ",\"CAPE\":{\"url\":\"" + capeUrl + "\"}") + "}}";
        h.json("https://sessionserver.mojang.com/session/minecraft/profile/" + UUID, "{\"id\":\"" + UUID
                + "\",\"name\":\"Builder\",\"properties\":[{\"name\":\"textures\",\"value\":\""
                + Base64.getEncoder().encodeToString(tex.getBytes(StandardCharsets.UTF_8)) + "\"}]}");
        h.answers.put("https://textures.minecraft.net/texture/abc", new MojangClient.Response(200, SkinTest.png(TestSkins.image())));
        h.answers.put("https://textures.minecraft.net/texture/cape", new MojangClient.Response(200, SkinTest.png(TestSkins.cape())));
        return h;
    }

    @Test
    void H2_CA2_skin_modelo_y_capa_por_nombre() throws Exception {
        FakeHttp h = account("http://textures.minecraft.net/texture/abc", "https://textures.minecraft.net/texture/cape", true);
        MojangClient.Profile p = new MojangClient(h).fetch("Builder");
        assertTrue(p.slim());
        assertNotNull(p.cape());
        assertEquals("Builder", p.name());
        assertTrue(h.asked.stream().allMatch(u -> u.getScheme().equals("https")), "http se pasa a https");
    }

    @Test
    void H2_CA5_solo_texturas_de_mojang() throws Exception {
        FakeHttp h = account("https://evil.example.com/skin.png", null, false);
        SkinException e = assertThrows(SkinException.class, () -> new MojangClient(h).fetch("Builder"));
        assertEquals("mojang-error", e.key());
        assertTrue(h.asked.stream().noneMatch(u -> u.getHost().contains("evil")), "nunca se pregunta a otro servidor");
    }

    @Test
    void H2_CA4_cuenta_inexistente_y_sin_respuesta() {
        FakeHttp h = new FakeHttp();
        h.answers.put("https://api.mojang.com/users/profiles/minecraft/Nadie", new MojangClient.Response(204, new byte[0]));
        assertEquals("account-not-found", assertThrows(SkinException.class, () -> new MojangClient(h).fetch("Nadie")).key());
        assertEquals("mojang-error", assertThrows(SkinException.class, () -> new MojangClient(h).fetch("Otro")).key());
    }

    @Test
    void H1_CA5_nombre_invalido_no_llega_a_la_red() {
        FakeHttp h = new FakeHttp();
        for (String bad : new String[]{"../x", "a b", "<red>x", "nombre_demasiado_largo", "", "x;op"}) {
            assertEquals("invalid-name", assertThrows(SkinException.class, () -> new MojangClient(h).fetch(bad)).key());
        }
        assertTrue(h.asked.isEmpty());
    }

    @Test
    void H2_CA2_cache_y_red_apagada(@TempDir Path dir) throws Exception {
        FakeHttp h = account("https://textures.minecraft.net/texture/abc", null, false);
        Clock t0 = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);
        Path skins = dir.resolve("skins"), cache = dir.resolve("cache");
        SkinRepository repo = new SkinRepository(skins, cache, new MojangClient(h), t0);

        assertEquals("skin-not-found", assertThrows(SkinException.class, () -> repo.load("Builder", false, 24)).key(),
                "H2 CA3: con la red apagada no se pregunta a nadie");
        assertTrue(h.asked.isEmpty());

        assertEquals("Mojang", repo.load("Builder", true, 24).source());
        int calls = h.asked.size();
        assertEquals("cache", repo.load("builder", true, 24).source(), "la segunda vez sale de la caché");
        assertEquals(calls, h.asked.size());

        // Vencida y sin red: igual sirve lo que ya se bajó.
        h.answers.clear();
        SkinRepository later = new SkinRepository(skins, cache, new MojangClient(h), Clock.offset(t0, java.time.Duration.ofHours(30)));
        assertEquals("cache", later.load("Builder", true, 24).source());
    }

    @Test
    void H2_CA1_archivo_local_con_opciones(@TempDir Path dir) throws Exception {
        Path skins = dir.resolve("skins");
        Files.createDirectories(skins);
        Files.write(skins.resolve("Ana.PNG"), SkinTest.png(TestSkins.image()));
        Files.write(skins.resolve("ana-cape.png"), SkinTest.png(TestSkins.cape()));
        Files.writeString(skins.resolve("Ana.yml"), "model: slim\ncape: ana-cape.png\n");
        SkinRepository repo = new SkinRepository(skins, dir.resolve("cache"), new MojangClient(new FakeHttp()), Clock.systemUTC());
        SkinRepository.Result r = repo.load("ana", false, 24);
        assertTrue(r.skin().slim());
        assertTrue(r.skin().hasCape());

        Files.writeString(skins.resolve("Ana.yml"), "cape: ../../server.properties\n");
        assertEquals("image-invalid", assertThrows(SkinException.class, () -> repo.load("ana", false, 24)).key(),
                "la capa solo puede ser un archivo de skins/");
    }
}

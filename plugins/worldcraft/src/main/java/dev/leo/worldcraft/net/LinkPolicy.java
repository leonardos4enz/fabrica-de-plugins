package dev.leo.worldcraft.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Copiado de SkinStatues (spec 023; ADR 0007: un arreglo aquí va también allá). Adónde puede ir un link (spec 023 H2 CA3, CA4): solo direcciones públicas y, si el dueño la dio, la lista de sitios. */
public final class LinkPolicy {
    /** Un elemento de allowed_hosts: un sitio, con «*.» delante para sus subdominios. */
    public static final Pattern HOST_PATTERN = Pattern.compile("(\\*\\.)?[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*");

    private LinkPolicy() {
    }

    /** Si la dirección es de internet (no de esta máquina, de la LAN ni reservada). */
    public static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) return false;
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            // IPv4 mapeada (::ffff:a.b.c.d) o compatible (::a.b.c.d): se mira como IPv4.
            boolean zeros = true;
            for (int i = 0; i < 10; i++) zeros &= b[i] == 0;
            if (zeros && ((b[10] == (byte) 0xFF && b[11] == (byte) 0xFF) || (b[10] == 0 && b[11] == 0))) {
                return v4Public(new byte[]{b[12], b[13], b[14], b[15]});
            }
            if ((b[0] & 0xFE) == 0xFC) return false;           // fc00::/7, local única
            if ((b[0] & 0xFF) == 0xFE && (b[1] & 0xC0) == 0xC0) return false; // fec0::/10, sitio local antiguo
            if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0D && (b[3] & 0xFF) == 0xB8) return false; // 2001:db8::/32
            if (b[0] == 0x00 && b[1] == 0x64 && b[2] == (byte) 0xFF && b[3] == (byte) 0x9B) {      // 64:ff9b::/96 (NAT64)
                return v4Public(new byte[]{b[12], b[13], b[14], b[15]});
            }
            return true;
        }
        return a instanceof Inet4Address && v4Public(b);
    }

    private static boolean v4Public(byte[] b) {
        int a0 = b[0] & 0xFF, a1 = b[1] & 0xFF, a2 = b[2] & 0xFF;
        if (a0 == 0 || a0 == 10 || a0 == 127 || a0 >= 224) return false;      // 0/8, 10/8, 127/8, multidifusión y 240/4
        if (a0 == 100 && a1 >= 64 && a1 <= 127) return false;                // 100.64/10 (CGNAT)
        if (a0 == 169 && a1 == 254) return false;                            // enlace local
        if (a0 == 172 && a1 >= 16 && a1 <= 31) return false;                 // 172.16/12
        if (a0 == 192 && a1 == 168) return false;                            // 192.168/16
        if (a0 == 192 && a1 == 0 && (a2 == 0 || a2 == 2)) return false;      // 192.0.0/24, 192.0.2/24
        if (a0 == 198 && (a1 == 18 || a1 == 19)) return false;               // 198.18/15
        if (a0 == 198 && a1 == 51 && a2 == 100) return false;                // 198.51.100/24
        return !(a0 == 203 && a1 == 0 && a2 == 113);                         // 203.0.113/24
    }

    /** Vacía = cualquier sitio; si no, igual a uno o subdominio de un «*.dominio». */
    public static boolean hostAllowed(String host, List<String> patterns) {
        if (patterns.isEmpty()) return true;
        String h = host.toLowerCase(Locale.ROOT);
        for (String p : patterns) {
            if (p.startsWith("*.") ? h.endsWith(p.substring(1)) : h.equals(p)) return true;
        }
        return false;
    }
}

package dev.leo.omnievents.hook;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import dev.leo.omnievents.trigger.ReceivedChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * Lee con ProtocolLib los mensajes del chat que va a recibir un jugador y los pasa como {@link ReceivedChatEvent}
 * (spec 016, ADR 0004). Solo se carga si ProtocolLib está; el adaptador de paquetes solo existe mientras alguna regla
 * habilitada use player_receive_chat ({@link #listen}). Corre en el hilo de red: nada de lo que hace aquí toca el
 * mundo, y cualquier error se atrapa (el paquete sigue sin cambios).
 */
public final class ChatPackets {
    private final Plugin plugin;
    private final IntSupplier perSecond;
    private final PacketAdapter adapter;
    private volatile boolean listening;
    /** Por jugador: {segundo, mensajes en ese segundo} (tope contra una regla que se responde a sí misma). */
    private final Map<UUID, long[]> rate = new ConcurrentHashMap<>();
    private volatile long lastWarn;
    private volatile boolean readWarned;

    public ChatPackets(Plugin plugin, IntSupplier perSecond) {
        this.plugin = plugin;
        this.perSecond = perSecond;
        this.adapter = new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.SYSTEM_CHAT,
                PacketType.Play.Server.DISGUISED_CHAT, PacketType.Play.Server.CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                handle(event);
            }
        };
    }

    /** Escucha los paquetes solo si hace falta (constitución III.1). Se llama tras registrar los oyentes. */
    public synchronized void listen(boolean on) {
        if (on == listening) return;
        if (on) ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
        else ProtocolLibrary.getProtocolManager().removePacketListener(adapter);
        listening = on;
        if (!on) rate.clear();
    }

    public boolean listening() {
        return listening;
    }

    private void handle(PacketEvent event) {
        if (event.isCancelled()) return;
        try {
            Player player = event.getPlayer();
            if (player == null || event.isPlayerTemporary()) return;
            PacketContainer packet = event.getPacket();
            PacketType t = event.getPacketType();
            // La barra de acción (overlay) no cuenta, como en ConditionalEvents.
            if (t == PacketType.Play.Server.SYSTEM_CHAT && Boolean.TRUE.equals(packet.getBooleans().readSafely(0))) return;
            Component c = read(packet, t);
            if (c == null) return;
            if (!allow(player)) return;
            String type = t == PacketType.Play.Server.SYSTEM_CHAT ? "SYSTEM" : t == PacketType.Play.Server.DISGUISED_CHAT ? "DISGUISED" : "PLAYER";
            ReceivedChatEvent e = new ReceivedChatEvent(!Bukkit.isPrimaryThread(), player, PlainTextComponentSerializer.plainText().serialize(c),
                    LegacyComponentSerializer.legacyAmpersand().serialize(c), GsonComponentSerializer.gson().serialize(c), type);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) event.setCancelled(true);
        } catch (RuntimeException | LinkageError ex) {
            if (!readWarned) {
                readWarned = true;
                plugin.getLogger().warning("player_receive_chat: no se pudo leer un paquete de chat (" + ex + "); se deja pasar sin cambios. Revisa la versión de ProtocolLib.");
            }
        }
    }

    /**
     * El texto del paquete: un componente de chat, uno de Adventure (Paper) o, del chat firmado, su contenido. Cada campo
     * se lee por separado: en el chat firmado, ProtocolLib falla al convertir los campos nulos (la firma) y eso no debe
     * impedir leer los demás (regresión 016 F-03).
     */
    private static Component read(PacketContainer packet, PacketType t) {
        var chat = packet.getChatComponents();
        for (int i = 0; i < chat.size(); i++) {
            final int k = i;
            WrappedChatComponent w = safely(() -> chat.readSafely(k));
            if (w != null && w.getJson() != null) return GsonComponentSerializer.gson().deserialize(w.getJson());
        }
        var raw = packet.getModifier();
        for (int i = 0; i < raw.size(); i++) {
            final int k = i;
            if (safely(() -> raw.readSafely(k)) instanceof Component c) return c;
        }
        if (t == PacketType.Play.Server.CHAT) {
            // Chat firmado sin contenido sin firmar: el texto va en el cuerpo firmado (SignedMessageBody.content).
            var structures = packet.getStructures();
            for (int i = 0; i < structures.size(); i++) {
                final int k = i;
                InternalStructure s = safely(() -> structures.readSafely(k));
                String text = s == null ? null : safely(() -> s.getStrings().readSafely(0));
                if (text != null) return Component.text(text);
            }
        }
        return null;
    }

    private static <T> T safely(java.util.function.Supplier<T> f) {
        try {
            return f.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean allow(Player p) {
        long now = System.currentTimeMillis(), second = now / 1000;
        long[] r = rate.computeIfAbsent(p.getUniqueId(), k -> new long[2]);
        synchronized (r) {
            if (r[0] != second) {
                r[0] = second;
                r[1] = 0;
            }
            if (++r[1] <= perSecond.getAsInt()) return true;
        }
        if (now - lastWarn > 60_000) {
            lastWarn = now;
            plugin.getLogger().warning("player_receive_chat: " + p.getName() + " recibió más de " + perSecond.getAsInt()
                    + " mensajes en un segundo; los demás no disparan reglas (¿una regla que se responde a sí misma? protocollib.max-messages-per-second)");
        }
        return false;
    }

    /** Al salir un jugador. */
    public void forget(UUID player) {
        rate.remove(player);
    }
}

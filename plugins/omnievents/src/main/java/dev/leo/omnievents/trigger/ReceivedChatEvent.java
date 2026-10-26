package dev.leo.omnievents.trigger;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Un jugador va a recibir un mensaje del chat (spec 016, disparador player_receive_chat). Lo lanza el oyente de
 * paquetes de ProtocolLib ({@code hook.ChatPackets}) en el hilo de red; esta clase no depende de ProtocolLib, así que el
 * disparador existe aunque no esté. Cancelarlo hace que el jugador no vea el mensaje.
 */
public final class ReceivedChatEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String message;
    private final String legacy;
    private final String json;
    private final String type;
    private boolean cancelled;

    public ReceivedChatEvent(boolean async, Player player, String message, String legacy, String json, String type) {
        super(async);
        this.player = player;
        this.message = message;
        this.legacy = legacy;
        this.json = json;
        this.type = type;
    }

    public Player getPlayer() {
        return player;
    }

    /** El texto sin formato. */
    public String getMessage() {
        return message;
    }

    /** El texto con códigos & (como %normal_message% de ConditionalEvents). */
    public String getLegacy() {
        return legacy;
    }

    /** El mensaje en JSON de texto de Minecraft. */
    public String getJson() {
        return json;
    }

    /** SYSTEM, DISGUISED o PLAYER: el paquete del que salió. */
    public String getType() {
        return type;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

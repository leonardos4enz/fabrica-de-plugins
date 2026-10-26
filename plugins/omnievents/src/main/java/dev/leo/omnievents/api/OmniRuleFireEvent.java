package dev.leo.omnievents.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Una regla pasó sus condiciones, su límite, su enfriamiento y su costo y va a correr sus acciones (spec 016; el
 * equivalente de ConditionalEventsEvent). No se puede cancelar. Es asíncrono si la regla corre fuera del hilo principal
 * (player_chat, player_receive_chat…): mirar {@link #isAsynchronous()} antes de tocar el mundo. Solo se crea si hay
 * oyentes; las simulaciones y las pruebas (tests:) no lo lanzan.
 */
public final class OmniRuleFireEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String rule;
    private final String trigger;
    private final Player player;
    private final String group;

    public OmniRuleFireEvent(boolean async, String rule, String trigger, Player player, String group) {
        super(async);
        this.rule = rule;
        this.trigger = trigger;
        this.player = player;
        this.group = group;
    }

    /** true si algún plugin escucha el evento (para no crearlo en vano). */
    public static boolean listened() {
        return HANDLERS.getRegisteredListeners().length > 0;
    }

    /** El nombre completo de la regla (paquete:regla si está en un paquete). */
    public String getRule() {
        return rule;
    }

    /** El disparador de la regla (player_join, command, timer…). */
    public String getTrigger() {
        return trigger;
    }

    /** El jugador del disparo, o null si no hay (temporizador del servidor, consola). */
    public Player getPlayer() {
        return player;
    }

    /** El grupo de acciones que va a correr: por ahora siempre "actions". */
    public String getGroup() {
        return group;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

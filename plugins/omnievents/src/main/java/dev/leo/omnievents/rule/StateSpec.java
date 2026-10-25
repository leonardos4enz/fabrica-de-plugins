package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.ActionNode;
import dev.leo.omnievents.trigger.StateDeps;
import dev.leo.omnievents.trigger.TriggerType;

import java.util.List;

/**
 * Lo propio de una regla state (spec 008): las condiciones de when, on_exit, de qué depende y cómo se revisa. El
 * on_enter va en las actions de la regla, para que pase por permiso, límite y enfriamiento como cualquier disparo.
 *
 * @param checkEveryTicks respaldo por intervalo (0 = sin respaldo)
 * @param fingerprint     when + worlds + per_player: si no cambia al recargar, el estado se conserva (H3 CA2)
 */
public record StateSpec(List<Condition> when, List<ActionNode> onExit, StateDeps deps, long checkEveryTicks,
                        boolean perPlayer, boolean initialSilent, List<TriggerType> watch, String fingerprint) {
}

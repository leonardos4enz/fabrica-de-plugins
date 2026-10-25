package dev.leo.omnievents.hook;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Formularios de Bedrock (spec 013, H3): con Floodgate, un menú se muestra a un jugador de Bedrock como formulario de
 * botones. Sin Floodgate, {@link #NONE}: nadie es de Bedrock y no se manda nada.
 */
public interface Forms {
    /** Un botón: texto e imagen opcional (URL si empieza con http, si no ruta de textura). */
    record Button(String text, String image) {
    }

    boolean isBedrock(Player p);

    /**
     * Manda un formulario de botones. Los avisos llegan en el hilo de red: quien los recibe los pasa al principal.
     *
     * @return false si no se pudo mandar (el jugador no es de Bedrock o falló Floodgate)
     */
    boolean send(Player p, String title, String content, List<Button> buttons, IntConsumer onButton, Runnable onClose);

    Forms NONE = new Forms() {
        @Override
        public boolean isBedrock(Player p) {
            return false;
        }

        @Override
        public boolean send(Player p, String title, String content, List<Button> buttons, IntConsumer onButton, Runnable onClose) {
            return false;
        }
    };
}

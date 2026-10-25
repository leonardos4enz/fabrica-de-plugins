package dev.leo.omnievents.hook;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Formularios con la API de Floodgate y Cumulus (spec 013, plan §1.4) sin dependencia de compilación: los métodos se
 * resuelven una vez, con el cargador de clases de Floodgate, y nunca se exponen a las expresiones (constitución II.1).
 */
public final class FloodgateForms implements Forms {
    private final Method getInstance, isFloodgatePlayer, sendForm, builder, title, content, button, buttonImage, onValid, onClosed, build, clickedId;
    private final Object imageUrl, imagePath;

    /** @throws ReflectiveOperationException si la API de esta versión de Floodgate no es la esperada */
    public FloodgateForms(Plugin floodgate) throws ReflectiveOperationException {
        ClassLoader cl = floodgate.getClass().getClassLoader();
        Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true, cl);
        Class<?> form = Class.forName("org.geysermc.cumulus.form.Form", true, cl);
        Class<?> simple = Class.forName("org.geysermc.cumulus.form.SimpleForm", true, cl);
        Class<?> imageType = Class.forName("org.geysermc.cumulus.util.FormImage$Type", true, cl);
        Class<?> response = Class.forName("org.geysermc.cumulus.response.SimpleFormResponse", true, cl);
        getInstance = api.getMethod("getInstance");
        isFloodgatePlayer = api.getMethod("isFloodgatePlayer", UUID.class);
        sendForm = api.getMethod("sendForm", UUID.class, form);
        builder = simple.getMethod("builder");
        Class<?> b = builder.getReturnType();
        title = b.getMethod("title", String.class);
        content = b.getMethod("content", String.class);
        button = b.getMethod("button", String.class);
        buttonImage = b.getMethod("button", String.class, imageType, String.class);
        onValid = b.getMethod("validResultHandler", Consumer.class);
        onClosed = b.getMethod("closedOrInvalidResultHandler", Runnable.class);
        build = b.getMethod("build");
        clickedId = response.getMethod("clickedButtonId");
        imageUrl = enumValue(imageType, "URL");
        imagePath = enumValue(imageType, "PATH");
    }

    private static Object enumValue(Class<?> type, String name) {
        for (Object c : type.getEnumConstants()) if (((Enum<?>) c).name().equals(name)) return c;
        throw new IllegalStateException("FormImage.Type sin " + name);
    }

    @Override
    public boolean isBedrock(Player p) {
        try {
            return (boolean) isFloodgatePlayer.invoke(getInstance.invoke(null), p.getUniqueId());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean send(Player p, String formTitle, String formContent, List<Button> buttons, IntConsumer onButton, Runnable onClose) {
        try {
            Object b = builder.invoke(null);
            title.invoke(b, formTitle);
            if (formContent != null && !formContent.isEmpty()) content.invoke(b, formContent);
            for (Button bt : buttons) {
                if (bt.image() == null || bt.image().isBlank()) button.invoke(b, bt.text());
                else buttonImage.invoke(b, bt.text(), bt.image().startsWith("http") ? imageUrl : imagePath, bt.image());
            }
            Consumer<Object> valid = r -> {
                try {
                    onButton.accept(((Number) clickedId.invoke(r)).intValue());
                } catch (ReflectiveOperationException e) {
                    onClose.run();
                }
            };
            onValid.invoke(b, valid);
            onClosed.invoke(b, onClose);
            Object result = sendForm.invoke(getInstance.invoke(null), p.getUniqueId(), build.invoke(b));
            return !(result instanceof Boolean ok) || ok;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}

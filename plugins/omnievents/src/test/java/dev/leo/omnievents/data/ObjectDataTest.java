package dev.leo.omnievents.data;

import dev.leo.omnievents.action.ActionException;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Variables de objetos (spec 009, H3): nombres en claves de Bukkit, tipos y límites. El PDC se prueba en el juego. */
class ObjectDataTest {
    @Test
    void H3_CA2_los_nombres_se_codifican_de_ida_y_vuelta_en_claves_validas() {
        for (String name : List.of("dueño", "ligada", "miVariable", "ÁRBOL_2", "x", "a_b_c", "año-2")) {
            String key = ObjectData.encode(name);
            assertEquals(name, ObjectData.decode(key), key);
            new NamespacedKey(ObjectData.NS, key);   // revienta si la clave tiene caracteres no válidos
            assertTrue(key.matches("[a-z0-9._-]+"), key);
        }
        assertEquals("due-f1-o", ObjectData.encode("dueño"));
        assertEquals("mi.variable", ObjectData.encode("miVariable"));
        assertEquals("ligada", ObjectData.encode("ligada"));
    }

    @Test
    void H3_CA4_tipos_y_largo_maximo_del_texto() {
        assertEquals(5.0, ObjectData.storable(5, 10));
        assertEquals(true, ObjectData.storable(true, 10));
        assertEquals("Leo", ObjectData.storable("Leo", 10));
        assertEquals("1, 2", ObjectData.storable(List.of(1, 2), 10), "una lista se guarda como texto");
        ActionException e = assertThrows(ActionException.class, () -> ObjectData.storable("x".repeat(11), 10));
        assertTrue(e.getMessage().contains("max-text-length"), e.getMessage());
    }

    @Test
    void H3_CA5_solo_bloques_entidades_e_items() {
        assertFalse(ObjectData.supports("texto"));
        assertFalse(ObjectData.supports(null));
        assertFalse(ObjectData.supports(5.0));
    }
}

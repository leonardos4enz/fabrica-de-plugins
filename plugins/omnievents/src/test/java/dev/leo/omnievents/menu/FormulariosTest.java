package dev.leo.omnievents.menu;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 013, H3 CA4: la respuesta de un formulario de Bedrock solo vale si es del vigente y con un botón en rango. */
class FormulariosTest {
    @Test
    void H3_CA4_respuesta_del_formulario_vigente_y_en_rango() {
        Menu.Item a = new Menu.Item("a", List.of(0), null, null, Map.of(), java.util.Set.of(), null, null);
        Menu.Item b = new Menu.Item("b", List.of(1), null, null, Map.of(), java.util.Set.of(), null, null);
        MenuService.FormSession s = new MenuService.FormSession(null, Map.of(), 7, List.of(a, b));
        assertTrue(MenuService.validResponse(s, 7, 0));
        assertTrue(MenuService.validResponse(s, 7, 1));
        assertFalse(MenuService.validResponse(s, 7, 2), "botón fuera de rango");
        assertFalse(MenuService.validResponse(s, 7, -1), "botón negativo");
        assertFalse(MenuService.validResponse(s, 6, 0), "un formulario viejo");
        assertFalse(MenuService.validResponse(null, 7, 0), "sin formulario vigente (cerrado, recargado o el jugador salió)");
    }
}

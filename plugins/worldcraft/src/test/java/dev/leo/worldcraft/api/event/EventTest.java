package dev.leo.worldcraft.api.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 037 H4: lo que un plugin de protección mira del evento. */
class EventTest {
    @Test
    void H4_CA1_solapamiento_de_cajas_con_esquinas_en_cualquier_orden() {
        // La obra toca de 0 0 0 a 9 9 9.
        assertTrue(EditEventBase.overlaps(0, 0, 0, 9, 9, 9, 9, 9, 9, 20, 20, 20), "una esquina compartida");
        assertTrue(EditEventBase.overlaps(0, 0, 0, 9, 9, 9, 20, 5, 5, -20, 6, 6), "esquinas al revés");
        assertFalse(EditEventBase.overlaps(0, 0, 0, 9, 9, 9, 10, 0, 0, 20, 9, 9), "pegada al lado");
        assertFalse(EditEventBase.overlaps(0, 0, 0, 9, 9, 9, 0, 10, 0, 9, 20, 9), "encima");
        assertTrue(EditEventBase.overlaps(0, 0, 0, 9, 9, 9, -5, -5, -5, 50, 50, 50), "la zona envuelve la obra");
    }

    @Test
    void H4_CA1_cancelar_con_motivo() {
        WorldcraftEditEvent ev = new WorldcraftEditEvent(null, new java.util.UUID(0, 9), "#Arena", null, "set", "stone",
                null, new int[]{1, 2, 3}, new int[]{4, 5, 6}, 64);
        assertFalse(ev.isCancelled());
        assertEquals("", ev.getCancelReason());
        ev.setCancelReason("This area is protected");
        assertTrue(ev.isCancelled());
        assertEquals("This area is protected", ev.getCancelReason());
        assertEquals(4, ev.getMaximum().getBlockX());
        assertEquals(2, ev.getMinimum().getBlockY());
        ev.setCancelled(false);
        assertFalse(ev.isCancelled(), "otro oyente lo puede dejar seguir");
    }
}

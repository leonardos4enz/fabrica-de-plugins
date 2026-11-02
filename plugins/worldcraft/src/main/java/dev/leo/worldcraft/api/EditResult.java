package dev.leo.worldcraft.api;

/**
 * How an edit ended.
 *
 * @param id      its number in the history (/wc history), or -1 if it never ran (the last one for an undo of several)
 * @param kind    "set", "replace", "paste" or "undo"
 * @param changed blocks it changed
 * @param reason  why it was refused (limits, world, another plugin, API off), or empty
 */
public record EditResult(int id, String kind, long changed, Status status, String reason) {
    public enum Status {
        /** It ran to the end. */
        DONE,
        /** It started and was stopped (/wc cancel, the server stopping); what changed stays and can be undone. */
        CANCELLED,
        /** It never started: see {@link #reason()}. */
        REFUSED
    }

    public static EditResult refused(String kind, String reason) {
        return new EditResult(-1, kind, 0, Status.REFUSED, reason);
    }

    public boolean done() {
        return status == Status.DONE;
    }
}

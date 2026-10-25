package dev.leo.omnievents.action;

import dev.leo.omnievents.expr.Scope;

/**
 * De dónde cobra y a dónde devuelve un cost (spec 012, plan §1.3): el jugador real ({@link PlayerPurse}) o el estado
 * falso de una prueba ({@link Sandbox#purse}). Las unitarias usan uno propio para hacer fallar un cobro.
 */
public interface Purse {
    /** Saldo, o null si no hay economía. */
    Double money();

    boolean takeMoney(double n);

    boolean giveMoney(double n);

    String formatMoney(double n);

    int levels();

    void setLevels(int levels);

    /** Puntos de experiencia en total. */
    int exp();

    void setExp(int total);

    /** Ítems de ese material (en mayúsculas) en el inventario, sin la armadura. */
    int count(String material);

    /** Quita n ítems del material; devuelve lo quitado para poder devolverlo, o null si no alcanzó (sin quitar nada). */
    Object takeItems(String material, int n);

    /** Devuelve lo que quitó {@link #takeItems}. */
    void giveBack(Object taken);

    Object var(Scope scope, String name);

    void setVar(Scope scope, String name, Object value);

    /** Quién paga, para el log de una devolución que falla. */
    String who();
}

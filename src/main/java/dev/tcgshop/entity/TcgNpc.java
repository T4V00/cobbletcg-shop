package dev.tcgshop.entity;

/**
 * Datos visibles de los NPC del mod (se sincronizan al cliente con DataTracker):
 * rol (texto sobre la cabeza), título de Pokémon opcional, piel y emoción del bocadillo.
 */
public interface TcgNpc {
    /** Emociones del bocadillo. */
    int EMOTE_NONE = 0, EMOTE_MONEY = 1, EMOTE_LOVE = 2, EMOTE_DEAL = 3, EMOTE_ANGRY = 4, EMOTE_THINK = 5;

    /** Clave de traducción del rol ("tcgshop.role.customer"...). */
    String roleKey();

    /** Título "especie|tier" o vacío. */
    String npcTitle();

    /** Índice de piel (ver NpcIdentity.SKINS). */
    int npcSkin();

    int emote();

    /** Quiere hablar contigo (trato pendiente): se dibuja un "!" sobre su cabeza. */
    default boolean wantsAttention() { return false; }

    /** Paciencia restante mientras espera un trato (0-1000), o -1 si no espera nada. */
    default int waitPermille() { return -1; }
}

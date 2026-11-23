package dev.tcgshop.entity;

/**
 * Visible data of the mod's NPCs (synced to the client with DataTracker):
 * role (text above the head), optional Pokémon title, skin and speech-bubble emotion.
 */
public interface TcgNpc {
    /** Speech-bubble emotions. */
    int EMOTE_NONE = 0, EMOTE_MONEY = 1, EMOTE_LOVE = 2, EMOTE_DEAL = 3, EMOTE_ANGRY = 4, EMOTE_THINK = 5;

    /** Translation key of the role ("tcgshop.role.customer"...). */
    String roleKey();

    /** "species|tier" title or empty. */
    String npcTitle();

    /** Skin index (see NpcIdentity.SKINS). */
    int npcSkin();

    int emote();

    /** Wants to talk to you (pending deal): a "!" is drawn above its head. */
    default boolean wantsAttention() { return false; }

    /** Remaining patience while waiting for a deal (0-1000), or -1 if not waiting for anything. */
    default int waitPermille() { return -1; }
}

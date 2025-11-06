package com.aisocialgame.backend.service.game.undercover;

/**
 * Enumerates the lifecycle stages of the Undercover game mode.  The values map
 * directly to the主持人逻辑 documented under {@code doc/Undercover_dev.md} so
 * that both the backend state machine and any realtime events can rely on the
 * same canonical vocabulary.
 */
public enum UndercoverStage {
    LOBBY_READY,
    ROLE_DISTRIBUTION,
    DISCUSSION,
    VOTE_MAIN,
    VOTE_PK_PREP,
    DISCUSSION_PK,
    VOTE_PK,
    ELIMINATION,
    GAME_END
}

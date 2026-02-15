package net.serverplugins.api.broadcast;

/** Type of broadcast message delivery method. */
public enum BroadcastType {
    /** Standard chat message */
    CHAT,

    /** Action bar message (above hotbar) */
    ACTION_BAR,

    /** Title message (large text in center of screen) */
    TITLE,

    /** Boss bar message (bar at top of screen) */
    BOSS_BAR
}

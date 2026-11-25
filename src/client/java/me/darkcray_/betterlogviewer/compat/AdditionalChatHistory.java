package me.darkcray_.betterlogviewer.compat;

import net.minecraft.network.chat.Component;

public interface AdditionalChatHistory {
    AdditionalChatHistory NONE = new AdditionalChatHistory() {};

    default void track(long owner, Object tab, Object result) {}
    default boolean contains(long owner) { return true; }
    default void replace(long owner, Component content) {}
    default void remove(long owner) {}
    default void clear() {}
}

package me.darkcray_.betterlogviewer.coreprotect;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.chat.Component;

public record LogEntry(long id, String player, LogAction action, String material, int amount, Instant time, boolean exactTime, boolean rolledBack, List<Component> originals, List<Component> itemDetails) {
    private static final AtomicLong IDS = new AtomicLong();

    public LogEntry {
        originals = originals.stream().map(Component::copy).map(c -> (Component)c).toList();
        itemDetails = itemDetails.stream().map(Component::copy).map(c -> (Component)c).toList();
    }

    public static long nextId() { return IDS.incrementAndGet(); }

    public LogEntry withCoordinates(Component coordinates) {
        var lines = new java.util.ArrayList<>(originals);
        lines.add(coordinates);
        var resolved = action == LogAction.REMOVE && coordinates.getString().contains("(a:item)") ? LogAction.WITHDRAW : action;
        return new LogEntry(id, player, resolved, material, amount, time, exactTime, rolledBack, lines, itemDetails);
    }
}

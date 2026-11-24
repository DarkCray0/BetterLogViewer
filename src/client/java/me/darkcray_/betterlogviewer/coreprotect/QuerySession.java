package me.darkcray_.betterlogviewer.coreprotect;

import java.util.List;
import java.util.TreeMap;
import net.minecraft.network.chat.Component;

public final class QuerySession {
    public final long id;
    public final Component header;
    public LanguageProfile language;
    public boolean inspection;
    public boolean closed, loading;
    public int lastServerPage = 1, viewedServerPage = 1, totalServerPages = 1, displayTick;
    public Component displayed;
    public final TreeMap<Integer, List<LogEntry>> pages = new TreeMap<>();
    public final TreeMap<Integer, Component> footers = new TreeMap<>();
    public final TreeMap<Integer, List<Component>> tails = new TreeMap<>();

    public QuerySession(long id, Component header, LanguageProfile language) {
        this.id = id; this.header = header.copy(); this.language = language;
    }

    public int retainedCount() { return pages.values().stream().mapToInt(List::size).sum(); }
    public List<LogEntry> presented() { return pages.getOrDefault(viewedServerPage, List.of()); }

    public void putPage(int page, List<LogEntry> entries) {
        pages.put(page, List.copyOf(entries)); lastServerPage = page; viewedServerPage = page;
    }

    public int navigationTarget(int requested, String label, boolean current) {
        if (current) return pages.containsKey(requested) || requested > 0 && requested <= totalServerPages ? requested : 0;
        String text = CoreProtectText.plain(label);
        if (text.startsWith("◀") || text.startsWith("▶")) {
            Integer neighbor = text.startsWith("◀") ? pages.lowerKey(viewedServerPage) : pages.higherKey(viewedServerPage);
            return neighbor == null ? 0 : neighbor;
        }
        return pages.containsKey(requested) ? requested : 0;
    }
}

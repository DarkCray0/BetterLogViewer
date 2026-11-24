package me.darkcray_.betterlogviewer.pagination;

import java.util.ArrayDeque;
import java.util.function.Consumer;
import me.darkcray_.betterlogviewer.coreprotect.CoreProtectCommands;
import me.darkcray_.betterlogviewer.coreprotect.QuerySession;

public final class LookupQueue {
    public record Request(QuerySession session, String command, int page) {
        public Request {
            if (!Integer.valueOf(page).equals(CoreProtectCommands.pageCommand(command))) throw new IllegalArgumentException("Only native page requests are allowed");
        }
    }

    private final ArrayDeque<Request> pending = new ArrayDeque<>();
    private final Consumer<Request> send, timeout;
    private Request active;
    private long lastSent = Long.MIN_VALUE / 2, deadline;
    public LookupQueue(Consumer<Request> send, Consumer<Request> timeout) { this.send = send; this.timeout = timeout; }

    public void enqueue(Request request) {
        if (active != null && active.session() == request.session() && active.page() == request.page() || pending.stream().anyMatch(r -> r.session() == request.session() && r.page() == request.page())) return;
        pending.addLast(request); request.session().loading = true;
    }

    public Request active() { return active; }

    public void tick(long now, int interval, int timeoutSeconds) {
        if (active != null && now >= deadline) {
            Request expired = active; active = null; expired.session().loading = false; timeout.accept(expired);
        }
        if (active == null && !pending.isEmpty() && now - lastSent >= interval) {
            active = pending.removeFirst(); lastSent = now; deadline = now + timeoutSeconds * 1000L; send.accept(active);
        }
    }

    public void complete(Request request) {
        if (request == null || active != request) return;
        active = null; request.session().loading = false;
    }

    public void cancel(QuerySession session) {
        pending.stream().filter(r -> session == null || r.session() == session).forEach(r -> r.session().loading = false);
        pending.removeIf(r -> session == null || r.session() == session);
        if (active != null && (session == null || active.session() == session)) { active.session().loading = false; active = null; }

    }
    public void clear() { cancel(null); }
}

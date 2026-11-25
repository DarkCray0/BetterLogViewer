package me.darkcray_.betterlogviewer.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import me.darkcray_.betterlogviewer.compat.AdditionalChatHistory;
import me.darkcray_.betterlogviewer.compat.ChatPlusHistory;
import me.darkcray_.betterlogviewer.coreprotect.*;
import me.darkcray_.betterlogviewer.mixin.ChatAccess;
import me.darkcray_.betterlogviewer.pagination.LookupQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;

public final class ChatMessageManager {
    private static final int MAX_QUERIES = 20, MAX_RECORDS = 10_000, REQUEST_INTERVAL = 1000, REQUEST_TIMEOUT = 10;
    public final ChatActions actions = new ChatActions();
    public final LinkedHashMap<Long, QuerySession> sessions = new LinkedHashMap<>();
    public final LookupQueue queue;
    private final CoreProtectParser parser;
    private final AdditionalChatHistory additionalChat = FabricLoader.getInstance().isModLoaded("chatplus") ? new ChatPlusHistory() : AdditionalChatHistory.NONE;
    private Capture capture;
    private QuerySession serverContext, pageHintSession;
    private int pageHint;
    private boolean sending;
    private long nextSession, quarantineUntil, pageHintDeadline;

    private static final class Capture {
        final QuerySession session;
        final LookupQueue.Request request;
        final List<LogEntry> records = new ArrayList<>();
        final List<Component> tail = new ArrayList<>();
        int page;
        List<LogEntry> previousPage;
        LogEntry pending;
        long lastMessage = System.currentTimeMillis();

        Capture(QuerySession session, int page, LookupQueue.Request request) {
            this.session = session; this.page = page; this.request = request; previousPage = session.pages.get(page);
        }
    }

    public ChatMessageManager(CoreProtectParser parser) {
        this.parser = parser;
        queue = new LookupQueue(this::send, request -> {
            if (capture != null && capture.request == request) finish();
            quarantineUntil = System.currentTimeMillis() + REQUEST_TIMEOUT * 1000L;
            serverContext = null; refreshAll();
        });
    }

    private Minecraft mc() { return Minecraft.getInstance(); }
    private ChatAccess chat() { return (ChatAccess)mc().gui.hud.getChat(); }

    private void log(Component message) {
        chat().blv$log(new GuiMessage(mc().gui.hud.getGuiTicks(), message, null, GuiMessageSource.SYSTEM_SERVER, GuiMessageTag.systemSinglePlayer()));
    }

    public void observeCommand(String command) {
        if (sending || !CoreProtectCommands.isCoreProtect(command)) return;
        boolean overlapping = queue.active() != null || capture != null && capture.pending != null;
        if (capture != null) finish();
        queue.clear(); pageHintSession = null;
        if (overlapping) quarantineUntil = System.currentTimeMillis() + REQUEST_TIMEOUT * 1000L;
        Integer page = CoreProtectCommands.pageCommand(command);
        if (page != null && serverContext != null && !serverContext.closed) {
            pageHintSession = serverContext; pageHint = page;
            pageHintDeadline = System.currentTimeMillis() + REQUEST_TIMEOUT * 1000L;
        } else serverContext = null;
        refreshAll();
    }

    public boolean receive(Component message) {
        if (System.currentTimeMillis() < quarantineUntil) return false;
        String plain = CoreProtectText.plain(message);
        if (queue.active() != null && plain.startsWith("CoreProtect - ") && searching(plain)) {
            log(message); return true;
        }
        if (capture != null && plain.equals("-----") && !capture.records.isEmpty()) {
            capture.tail.add(message.copy()); capture.lastMessage = System.currentTimeMillis(); log(message); return true;
        }
        var parsed = parser.parse(message, capture == null ? null : capture.session.language, capture != null && capture.session.inspection);
        if (parsed.isEmpty()) {
            if (queue.active() != null && plain.startsWith("CoreProtect - ") && !searching(plain)) {
                var request = queue.active();
                if (capture != null && capture.request == request) finish();
                queue.complete(request); refresh(request.session());
            }
            return false;
        }
        var result = parsed.get();
        if (result instanceof CoreProtectParser.Header(LanguageProfile profile, boolean inspection)) {
            if (capture != null) finish();
            var request = queue.active();
            QuerySession session;
            int page = 1;
            if (request != null) { session = request.session(); page = request.page(); }
            else if (pageHintSession != null && !pageHintSession.closed) { session = pageHintSession; page = pageHint; }
            else session = create(message, profile);
            pageHintSession = null;
            if (profile != null) session.language = profile;
            if (inspection) session.inspection = true;
            capture = new Capture(session, page, request); serverContext = session;
            if (session.displayed == null) {
                session.displayed = build(session);
                chat().blv$add(session.displayed, null, GuiMessageSource.SYSTEM_SERVER, GuiMessageTag.systemSinglePlayer());
            } else { log(message); refresh(session); }
            refreshAll(); return true;
        }
        if (capture == null) return false;
        capture.lastMessage = System.currentTimeMillis();
        switch (result) {
            case CoreProtectParser.Row row -> {
                if (capture.session.language == null) capture.session.language = row.profile();
                capture.pending = null;
                if (append(row.entry())) return false;
                if (row.needsCoordinates()) capture.pending = row.entry();
                log(message);
                return true;
            }
            case CoreProtectParser.Coordinates _ when capture.pending != null -> {
                var entry = capture.pending.withCoordinates(message);
                capture.records.set(capture.records.size() - 1, entry);
                capture.session.pages.put(capture.page, List.copyOf(capture.records));
                capture.pending = null;
                log(message);
                refresh(capture.session);
                return true;
            }
            case CoreProtectParser.Footer footer -> {
                if (footer.page() != capture.page && capture.request != null) {
                    finish();
                    return false;
                }
                if (footer.page() != capture.page) {
                    if (capture.previousPage == null) capture.session.pages.remove(capture.page);
                    else capture.session.pages.put(capture.page, capture.previousPage);
                    capture.page = footer.page();
                }
                capture.session.totalServerPages = footer.total();
                capture.session.footers.put(footer.page(), message.copy());
                log(message);
                finish();
                return true;
            }
            case CoreProtectParser.Empty _ -> {
                capture.tail.add(message.copy());
                log(message);
                finish();
                return true;
            }
            default -> {}
        }
        return false;
    }

    private static boolean searching(String text) {
        String lower = text.toLowerCase(Locale.ROOT); return lower.contains("search") || lower.contains("поиск");
    }

    private QuerySession create(Component header, LanguageProfile language) {
        while (sessions.size() >= MAX_QUERIES) evict(sessions.values().iterator().next());
        var session = new QuerySession(++nextSession, header, language); sessions.put(session.id, session); return session;
    }

    private boolean append(LogEntry entry) {
        var session = capture.session;
        int total = sessions.values().stream().mapToInt(QuerySession::retainedCount).sum();
        int replacing = session.pages.getOrDefault(capture.page, List.of()).size();
        if (total - replacing + capture.records.size() + 1 > MAX_RECORDS) return true;
        capture.records.add(entry);
        session.pages.put(capture.page, List.copyOf(capture.records)); session.viewedServerPage = capture.page;
        refresh(session); return false;
    }

    private void finish() {
        var finished = capture; if (finished == null) return;
        capture = null;
        finished.session.putPage(finished.page, finished.records);
        finished.session.tails.put(finished.page, List.copyOf(finished.tail));
        queue.complete(finished.request); refresh(finished.session);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (pageHintSession != null && now >= pageHintDeadline) pageHintSession = null;
        if (capture != null && now - capture.lastMessage > 2000) finish();
        queue.tick(now, REQUEST_INTERVAL, REQUEST_TIMEOUT);
    }

    private void send(LookupQueue.Request request) {
        if (mc().getConnection() == null || request.session().closed || serverContext != request.session()) {
            queue.complete(request); refresh(request.session()); return;
        }
        if (capture != null) finish();
        sending = true;
        try { mc().getConnection().sendCommand(CoreProtectCommands.withoutSlash(request.command())); }
        finally { sending = false; }
    }

    private void loadServerPage(QuerySession session, int page, String command) {
        if (session.closed) return;
        if (session.pages.containsKey(page)) {
            session.viewedServerPage = page; refresh(session); return;
        }
        if (serverContext != session || session.loading || page < 1 || page > session.totalServerPages) return;
        queue.enqueue(new LookupQueue.Request(session, command, page));
    }

    private Component build(QuerySession session) {
        actions.remove(session.id);
        var text = Component.empty().append(session.header.copy());
        for (var entry : session.presented()) for (var original : entry.originals()) text.append("\n").append(original.copy());
        for (var tail : session.tails.getOrDefault(session.viewedServerPage, List.of())) text.append("\n").append(tail.copy());
        var footer = session.footers.get(session.viewedServerPage);
        if (footer != null) text.append("\n").append(actions.pagination(session.id, footer, link -> session.navigationTarget(link.page(), link.label(), serverContext == session), (page, command) -> loadServerPage(session, page, command)));
        return text;
    }

    public void refresh(QuerySession session) {
        if (session.closed || session.displayed == null) return;
        var messages = chat().blv$messages();
        int index = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).content() == session.displayed) { index = i; break; }
        }
        if (index < 0 && additionalChat.contains(session.id)) { actions.remove(session.id); return; }
        var replacement = build(session);
        if (index >= 0) {
            GuiMessage old = messages.get(index);
            var updated = new GuiMessage(old.addedTime(), replacement, old.signature(), old.source(), old.tag());
            messages.set(index, updated);
            rebuildChat(old, updated);
        }
        additionalChat.replace(session.id, replacement);
        session.displayed = replacement;
    }

    public boolean ownsMessage(Component message) {
        return sessions.values().stream().anyMatch(session -> !session.closed && session.displayed == message);
    }

    public void trackChatPlusMessage(Component original, Object tab, Object result) {
        for (var session : sessions.values()) {
            if (!session.closed && session.displayed == original) {
                additionalChat.track(session.id, tab, result);
                return;
            }
        }
    }

    private void rebuildChat(GuiMessage previous, GuiMessage replacement) {
        var access = chat();
        int scroll = access.blv$scroll();
        boolean unread = access.blv$unread();
        var lines = access.blv$lines();
        GuiMessage.Line anchor = scroll > 0 && scroll < lines.size() ? lines.get(scroll) : null;
        int offset = 0;
        if (anchor != null) {
            int start = scroll;
            while (start > 0 && lines.get(start - 1).parent() == anchor.parent()) start--;
            offset = scroll - start;
        }

        access.blv$scroll(0);
        access.blv$refresh();

        int restored = scroll;
        if (anchor != null) {
            var parent = anchor.parent() == previous ? replacement : anchor.parent();
            int start = -1, end = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).parent() == parent) {
                    if (start < 0) start = i;
                    end = i;
                }
            }
            if (start >= 0) restored = start + Math.min(offset, end - start);
        }
        int maximum = Math.max(0, lines.size() - mc().gui.hud.getChat().getLinesPerPage());
        restored = Math.clamp(restored, 0, maximum);
        access.blv$scroll(restored);
        access.blv$unread(unread && restored > 0);
    }

    private void refreshAll() { for (var session : List.copyOf(sessions.values())) refresh(session); }

    private void evict(QuerySession session) {
        session.closed = true; queue.cancel(session); actions.remove(session.id); sessions.remove(session.id);
        additionalChat.remove(session.id);
        if (serverContext == session) serverContext = null;
        if (pageHintSession == session) pageHintSession = null;
    }

    public void clear() {
        for (var session : List.copyOf(sessions.values())) evict(session);
        capture = null; serverContext = null; pageHintSession = null;
        queue.clear(); actions.clear(); quarantineUntil = 0; pageHintDeadline = 0;
        additionalChat.clear();
    }
}

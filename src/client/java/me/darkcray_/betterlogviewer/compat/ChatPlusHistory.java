package me.darkcray_.betterlogviewer.compat;

import com.ebicep.chatplus.features.chattabs.ChatTab;
import com.ebicep.chatplus.features.chattabs.ChatTab.ChatPlusGuiMessage;
import com.ebicep.chatplus.features.chattabs.ChatTab.ChatPlusGuiMessageLine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

public final class ChatPlusHistory implements AdditionalChatHistory {
    private record View(ChatTab tab, ChatPlusGuiMessage message, Component header) {}
    private final Map<Long, List<View>> views = new HashMap<>();

    @Override
    public void track(long owner, Object tab, Object result) {
        if (!(tab instanceof ChatTab chatTab) || !(result instanceof ChatTab.NewMessageResult added) || added.getNewDisplayMessageResult() == null) return;
        var message = added.getChatTabAddNewMessageEvent().getChatPlusGuiMessage();
        views.computeIfAbsent(owner, _ -> new ArrayList<>()).add(new View(chatTab, message, message.getGuiMessage().content().copy()));
    }

    @Override
    public boolean contains(long owner) {
        var retained = views.get(owner);
        if (retained == null) return true;
        retained.removeIf(view -> !view.tab().getMessages().contains(view.message()));
        if (retained.isEmpty()) views.remove(owner);
        return retained.isEmpty();
    }

    @Override
    public void replace(long owner, Component content) {
        if (contains(owner)) return;
        for (var view : views.get(owner)) {
            var previous = view.message().getGuiMessage();
            var formatted = view.header().copy();
            for (int i = 1; i < content.getSiblings().size(); i++) formatted.append(content.getSiblings().get(i).copy());
            view.message().setGuiMessage(new GuiMessage(previous.addedTime(), formatted, previous.signature(), previous.source(), previous.tag()));
            rebuild(view.tab());
        }
    }

    private void rebuild(ChatTab tab) {
        int scroll = tab.getChatScrollbarPos();
        boolean unread = tab.getNewMessageSinceScroll();
        boolean filtered = tab.getWasFiltered() || tab.getFilterChat();
        var lines = tab.getDisplayedMessages();
        int anchorIndex = lines.size() - 1 - scroll;
        ChatPlusGuiMessageLine anchor = scroll > 0 && anchorIndex >= 0 && anchorIndex < lines.size() ? lines.get(anchorIndex) : null;
        int offset = 0;
        if (anchor != null) {
            int end = anchorIndex;
            while (end + 1 < lines.size() && lines.get(end + 1).getLinkedMessage() == anchor.getLinkedMessage()) end++;
            offset = end - anchorIndex;
        }

        tab.setChatScrollbarPos(0);
        tab.setRescaleChat(true);
        tab.refreshDisplayMessages();
        if (filtered || tab.getFilterChat()) {
            tab.setFilterChat(true);
            tab.refreshDisplayMessages();
        }

        lines = tab.getDisplayedMessages();
        int restored = scroll;
        if (anchor != null) {
            int start = -1, end = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).getLinkedMessage() == anchor.getLinkedMessage()) {
                    if (start < 0) start = i;
                    end = i;
                }
            }
            if (start >= 0) restored = lines.size() - 1 - end + Math.min(offset, end - start);
        }
        tab.setScrollPos(restored);
        tab.setNewMessageSinceScroll(unread && tab.getChatScrollbarPos() > 0);
    }

    @Override
    public void remove(long owner) { views.remove(owner); }

    @Override
    public void clear() { views.clear(); }
}

package me.darkcray_.betterlogviewer.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;
import me.darkcray_.betterlogviewer.coreprotect.CoreProtectCommands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class ChatActions {
    public record PageLink(int page, String command, String label) {}
    private record Action(long owner, Runnable run) {}
    private final Map<Identifier, Action> actions = new HashMap<>();

    public Component pagination(long owner, Component original, ToIntFunction<PageLink> resolve, BiConsumer<Integer, String> load) {
        var copy = original.copy(); copy.getSiblings().clear();
        if (original.getStyle().getClickEvent() instanceof ClickEvent.RunCommand(String command)) {
            Integer page = CoreProtectCommands.pageCommand(command);
            if (page != null) {
                String label = original.visit((_, part) -> part.isBlank() ? Optional.empty() : Optional.of(part), Style.EMPTY).orElse("");
                int target = resolve.applyAsInt(new PageLink(page, command, label));
                Identifier id = Identifier.fromNamespaceAndPath("betterlogviewer", "page/" + UUID.randomUUID());
                actions.put(id, new Action(owner, () -> { if (target > 0) load.accept(target, command); }));
                Style style = original.getStyle().withClickEvent(new ClickEvent.Custom(id, Optional.empty()));
                if (target == 0) {
                    var tooltip = Component.empty();
                    if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component value)) tooltip.append(value.copy()).append("\n");
                    tooltip.append(Component.translatable("blv.page_not_cached"));
                    style = style.withHoverEvent(new HoverEvent.ShowText(tooltip));
                }
                copy.setStyle(style);
            }
        }

        for (var sibling : original.getSiblings()) copy.append(pagination(owner, sibling, resolve, load));
        return copy;
    }

    public boolean click(Style style) {
        if (!(style.getClickEvent() instanceof ClickEvent.Custom custom) || !custom.id().getNamespace().equals("betterlogviewer")) return false;
        var action = actions.get(custom.id());
        if (action != null) action.run().run();
        return true;
    }

    public void remove(long owner) { actions.values().removeIf(action -> action.owner() == owner); }
    public void clear() { actions.clear(); }
}

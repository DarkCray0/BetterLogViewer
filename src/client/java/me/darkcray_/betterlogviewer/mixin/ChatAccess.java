package me.darkcray_.betterlogviewer.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatComponent.class)
public interface ChatAccess {
    @Accessor("allMessages") List<GuiMessage> blv$messages();
    @Invoker("refreshTrimmedMessages") void blv$refresh();
    @Invoker("addMessage") void blv$add(Component content, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag);
    @Invoker("logChatMessage") void blv$log(GuiMessage message);
}

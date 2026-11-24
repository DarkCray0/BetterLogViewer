package me.darkcray_.betterlogviewer.mixin;

import me.darkcray_.betterlogviewer.BetterLogViewerClient;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "addServerSystemMessage", at = @At("HEAD"), cancellable = true)
    private void blv$receive(Component message, CallbackInfo ci) {
        if (BetterLogViewerClient.messages() != null && BetterLogViewerClient.messages().receive(message)) ci.cancel();
    }
}

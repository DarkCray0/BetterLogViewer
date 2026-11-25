package me.darkcray_.betterlogviewer.mixin;

import me.darkcray_.betterlogviewer.BetterLogViewerClient;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void blv$click(Style style, boolean insertions, CallbackInfoReturnable<Boolean> cir) {
        var messages = BetterLogViewerClient.messages();
        if (messages != null && messages.actions.click(style)) cir.setReturnValue(true);
    }
}

package me.darkcray_.betterlogviewer.mixin;

import com.ebicep.chatplus.features.chattabs.ChatTabAddNewMessageEvent;
import me.darkcray_.betterlogviewer.BetterLogViewerClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.ebicep.chatplus.features.CompactMessages$CompactMode", remap = false)
public abstract class ChatPlusCompactMixin {
    @Inject(method = "handleNewMessage", at = @At("HEAD"), cancellable = true, remap = false)
    private void blv$keepQuery(ChatTabAddNewMessageEvent event, CallbackInfo ci) {
        var messages = BetterLogViewerClient.messages();
        if (messages != null && messages.ownsMessage(event.getRawComponent())) ci.cancel();
    }
}

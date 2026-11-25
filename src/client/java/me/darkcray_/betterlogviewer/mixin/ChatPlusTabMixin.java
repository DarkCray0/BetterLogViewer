package me.darkcray_.betterlogviewer.mixin;

import com.ebicep.chatplus.features.chattabs.AddNewMessageEvent;
import com.ebicep.chatplus.features.chattabs.ChatTab;
import me.darkcray_.betterlogviewer.BetterLogViewerClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.ebicep.chatplus.features.chattabs.ChatTab", remap = false)
public abstract class ChatPlusTabMixin {
    @Inject(method = "addNewMessage", at = @At("RETURN"), remap = false)
    private void blv$track(AddNewMessageEvent event, CallbackInfoReturnable<ChatTab.NewMessageResult> cir) {
        var messages = BetterLogViewerClient.messages();
        if (messages != null) messages.trackChatPlusMessage(event.getRawComponent(), this, cir.getReturnValue());
    }
}

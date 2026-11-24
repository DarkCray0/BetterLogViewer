package me.darkcray_.betterlogviewer.mixin;

import me.darkcray_.betterlogviewer.BetterLogViewerClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void blv$command(String command, CallbackInfo ci) { BetterLogViewerClient.messages().observeCommand(command); }

    @Inject(method = "sendUnattendedCommand", at = @At("HEAD"))
    private void blv$clickCommand(String command, Screen screen, CallbackInfo ci) { BetterLogViewerClient.messages().observeCommand(command); }
}

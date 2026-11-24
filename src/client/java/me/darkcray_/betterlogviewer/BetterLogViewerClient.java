package me.darkcray_.betterlogviewer;

import me.darkcray_.betterlogviewer.chat.ChatMessageManager;
import me.darkcray_.betterlogviewer.coreprotect.CoreProtectParser;
import me.darkcray_.betterlogviewer.coreprotect.LanguageProfile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class BetterLogViewerClient implements ClientModInitializer {
    private static ChatMessageManager messages;
    public static ChatMessageManager messages() { return messages; }

    @Override
    public void onInitializeClient() {
        var profiles = FabricLoader.getInstance().getConfigDir().resolve("betterlogviewer/profiles");
        messages = new ChatMessageManager(new CoreProtectParser(LanguageProfile.load(profiles)));
        ClientTickEvents.END_CLIENT_TICK.register(_ -> messages.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> messages.clear());
        ClientPlayConnectionEvents.JOIN.register((_, _, _) -> messages.clear());
    }
}

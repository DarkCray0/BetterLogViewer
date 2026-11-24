package me.darkcray_.betterlogviewer.coreprotect;

import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

public final class CoreProtectText {
    private static final Pattern LEGACY_FORMAT = Pattern.compile("(?i)§[0-9a-fk-orx]");

    private CoreProtectText() {}
    public static String plain(Component component) { return plain(component.getString()); }

    public static String plain(String text) {
        return LEGACY_FORMAT.matcher(text).replaceAll("").replace('\u00a0', ' ').strip().replaceAll("\\s+", " ");
    }

    public static boolean hasLegacyColor(Component component) {
        return Pattern.compile("(?i)§[0-9a-fx]").matcher(component.getString()).find();
    }
}

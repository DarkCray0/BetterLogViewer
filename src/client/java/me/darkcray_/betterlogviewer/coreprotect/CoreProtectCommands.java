package me.darkcray_.betterlogviewer.coreprotect;

import java.util.regex.Pattern;

public final class CoreProtectCommands {
    public static final String COMMAND_ROOT = "(?:co|coreprotect|coreprotect:(?:co|coreprotect))";
    private static final Pattern COMMAND = Pattern.compile("^/?" + COMMAND_ROOT + "(?:\\s+.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE = Pattern.compile("^/?" + COMMAND_ROOT + "\\s+(?:lookup|l|page)\\s+(\\d+)(?::[1-9]\\d*)?$", Pattern.CASE_INSENSITIVE);

    private CoreProtectCommands() {}
    public static boolean isCoreProtect(String command) { return COMMAND.matcher(command.strip()).matches(); }

    public static Integer pageCommand(String command) {
        var match = PAGE.matcher(command.strip());
        if (!match.matches()) return null;
        try { int page = Integer.parseInt(match.group(1)); return page > 0 ? page : null; }
        catch (NumberFormatException ignored) { return null; }
    }

    public static String withoutSlash(String command) {
        String text = command.strip(); return text.startsWith("/") ? text.substring(1) : text;
    }
}

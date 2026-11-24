package me.darkcray_.betterlogviewer.coreprotect;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

public final class CoreProtectParser {
    public sealed interface Result permits Header, Row, Coordinates, Footer, Empty {}
    public record Header(LanguageProfile profile, boolean inspection) implements Result {}
    public record Row(LogEntry entry, LanguageProfile profile, boolean needsCoordinates) implements Result {}
    public record Coordinates() implements Result {}
    public record Footer(int page, int total) implements Result {}
    public record Empty() implements Result {}
    private static final Pattern COORDINATES = Pattern.compile("\\(x(-?\\d+)/y(-?\\d+)/z(-?\\d+)(?:/([^()]+))?\\)");
    private static final Pattern TELEPORT = Pattern.compile("^/?" + CoreProtectCommands.COMMAND_ROOT + "\\s+(?:teleport|tp)\\s+wid:(\\d+)\\s+.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSPECT = Pattern.compile("^----- CoreProtect ----- \\(x.*\\)$");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.US);
    private final List<LanguageProfile> profiles;

    public CoreProtectParser(List<LanguageProfile> profiles) { this.profiles = List.copyOf(profiles); }

    public Optional<Result> parse(Component message, LanguageProfile active, boolean inspection) {
        String text = CoreProtectText.plain(message);
        for (var profile : profiles) {
            if (profile.headerPattern.matcher(text).matches()) return Optional.of(new Header(profile, false));
            boolean container = profile.containerPattern != null && profile.containerPattern.matcher(text).matches();
            boolean interaction = profile.interactionPattern != null && profile.interactionPattern.matcher(text).matches();
            if ((container || interaction) && isInspection(message)) return Optional.of(new Header(profile, true));
        }
        if (INSPECT.matcher(text).matches() && isInspection(message)) return Optional.of(new Header(null, true));
        if (active == null && !inspection) return Optional.empty();
        var candidates = active != null ? List.of(active) : profiles;
        for (var profile : candidates) {
            var footer = profile.pagePattern.matcher(text);
            if (footer.matches()) {
                try {
                    int page = Integer.parseInt(footer.group("page")), total = Integer.parseInt(footer.group("total"));
                    if (page > 0 && total >= page) return Optional.of(new Footer(page, total));
                } catch (RuntimeException ignored) { }
            }
            if (profile.noResults.stream().anyMatch(text::equals)) return Optional.of(new Empty());
            var row = profile.rowPattern.matcher(text);
            if (!row.matches()) continue;
            try {
                var action = LogAction.valueOf(profile.actions.get(row.group("verb").toLowerCase(Locale.ROOT)));
                var times = new ArrayList<Instant>();
                var details = new ArrayList<Component>();
                boolean[] strike = {false};
                boolean[] colored = {CoreProtectText.hasLegacyColor(message)};
                message.visit((style, _) -> {
                    strike[0] |= style.isStrikethrough();
                    colored[0] |= style.getColor() != null;
                    if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component value)) {
                        try { times.add(ZonedDateTime.parse(CoreProtectText.plain(value), TIMESTAMP).toInstant()); }
                        catch (RuntimeException ignored) { if (!value.getString().isBlank()) details.add(value); }
                    }
                    return Optional.empty();
                }, Style.EMPTY);
                boolean exactTime = !times.isEmpty();
                if (!exactTime) {
                    if (!colored[0] || profile.timeUnits == null) return Optional.empty();
                    var age = Pattern.compile("^(\\d+(?:[.,]\\d+)?)\\s*/?\\s*([\\p{L}]+)\\s+").matcher(text);
                    if (!age.find()) return Optional.empty();
                    Integer multiplier = profile.timeUnits.get(age.group(2).toLowerCase(Locale.ROOT));
                    if (multiplier == null) return Optional.empty();
                    double elapsed = Double.parseDouble(age.group(1).replace(',', '.')) * multiplier;
                    if (!Double.isFinite(elapsed) || elapsed > 31536000000L) return Optional.empty();
                    times.add(Instant.now().minusSeconds((long)elapsed));
                }
                String amountText = row.group("amount");
                if (action.itemChange() && amountText == null) return Optional.empty();
                int amount = amountText == null ? 1 : Integer.parseInt(amountText);
                if (amount < 1) return Optional.empty();
                String material = row.group("material").replace("(↓)", "").toLowerCase(Locale.ROOT);
                if (!material.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_]+(?:\\[[a-z0-9_=,]+])?")) return Optional.empty();
                if (!material.contains(":")) material = "minecraft:" + material;
                var entry = new LogEntry(LogEntry.nextId(), row.group("player"), action, material, amount, times.getFirst(), exactTime, strike[0], List.of(message), distinct(details));
                return Optional.of(new Row(entry, profile, !inspection && !hasCoordinates(message)));
            } catch (RuntimeException ignored) { return Optional.empty(); }
        }
        if ((text.startsWith("^ ") || text.startsWith("(x")) && hasCoordinates(message)) return Optional.of(new Coordinates());
        return Optional.empty();
    }

    private static List<Component> distinct(List<Component> values) {
        var result = new ArrayList<Component>();
        for (var value : values) if (result.stream().noneMatch(v -> v.equals(value))) result.add(value);
        return result;
    }

    private static boolean hasCoordinates(Component message) {
        var match = COORDINATES.matcher(CoreProtectText.plain(message));
        if (!match.find()) return false;
        try {
            for (int group = 1; group <= 3; group++) Integer.parseInt(match.group(group));
            return true;
        } catch (NumberFormatException error) { return false; }
    }

    private static boolean isInspection(Component message) {
        if (!hasCoordinates(message)) return false;
        return message.visit((style, _) -> {
            if (style.getClickEvent() instanceof ClickEvent.RunCommand(String command1)) {
                var command = TELEPORT.matcher(command1.strip());
                if (command.matches()) {
                    try {
                        Integer.parseInt(command.group(1));
                        return Optional.of(true);
                    } catch (NumberFormatException ignored) { }
                }
            }
            return Optional.<Boolean>empty();
        }, Style.EMPTY).orElse(false);
    }
}

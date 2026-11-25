package me.darkcray_.betterlogviewer.coreprotect;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

public final class LanguageProfile {
    public String id;
    public String header;
    public String row;
    public String page;
    public List<String> noResults;
    public Map<String, String> actions;
    public Map<String, Integer> timeUnits;
    public String containerHeader, interactionHeader, signHeader;
    public String time, searching;
    public List<RowPattern> rows;
    public transient Pattern headerPattern, rowPattern, pagePattern;
    public transient Pattern containerPattern, interactionPattern, signPattern;
    public transient Pattern timePattern;
    private transient List<RowRule> compiledRows;

    private record Catalog(List<String> languages) {}
    private record RowRule(Pattern pattern, LogAction action) {}
    public record RowPattern(String pattern, LogAction action) {}
    public record RowMatch(Matcher matcher, LogAction action) {
        public String field(String name) {
            try { return matcher.group(name); }
            catch (IllegalArgumentException ignored) { return null; }
        }
    }

    private static final Pattern LEGACY_TIME = Pattern.compile("^(?<age>\\d+(?:[.,]\\d+)?)\\s*/?\\s*(?<unit>[\\p{L}]+)\\s+");

    public LanguageProfile compile() {
        if (id == null || actions == null || noResults == null || header == null || row == null || page == null) throw new IllegalArgumentException("Incomplete language profile");
        headerPattern = Pattern.compile(header);
        rowPattern = Pattern.compile(row);
        pagePattern = Pattern.compile(page);
        if (containerHeader != null) containerPattern = Pattern.compile(containerHeader);
        if (interactionHeader != null) interactionPattern = Pattern.compile(interactionHeader);
        if (signHeader != null) signPattern = Pattern.compile(signHeader);
        timePattern = time == null ? LEGACY_TIME : Pattern.compile(time);
        if (searching != null) searching = CoreProtectText.plain(searching);
        timeUnits = timeUnits == null ? Map.of() : timeUnits;
        compiledRows = new ArrayList<>();
        if (rows != null) {
            for (var rule : rows) {
                if (rule == null || rule.pattern() == null || rule.action() == null) throw new IllegalArgumentException("Incomplete row pattern");
                compiledRows.add(new RowRule(Pattern.compile(rule.pattern()), rule.action()));
            }
        }
        if (timeUnits.values().stream().anyMatch(v -> v == null || v < 1 || v > 31536000)) throw new IllegalArgumentException("Invalid relative time unit");
        for (String name : actions.values()) LogAction.valueOf(name);
        return this;
    }

    public boolean lookupHeader(String text) { return headerPattern.matcher(text).matches(); }

    public boolean inspectionHeader(String text) {
        return containerPattern != null && containerPattern.matcher(text).matches()
            || interactionPattern != null && interactionPattern.matcher(text).matches()
            || signPattern != null && signPattern.matcher(text).matches();
    }

    public Optional<Matcher> page(String text) {
        var match = pagePattern.matcher(text);
        return match.matches() ? Optional.of(match) : Optional.empty();
    }

    public Optional<RowMatch> row(String text) {
        for (var rule : compiledRows) {
            var match = rule.pattern().matcher(text);
            if (match.matches()) return Optional.of(new RowMatch(match, rule.action()));
        }
        if (rowPattern != null) {
            var match = rowPattern.matcher(text);
            if (match.matches()) {
                String verb = new RowMatch(match, null).field("verb");
                String action = verb == null ? null : actions.get(verb.toLowerCase(Locale.ROOT));
                if (action != null) return Optional.of(new RowMatch(match, LogAction.valueOf(action)));
            }
        }
        return Optional.empty();
    }

    public boolean searching(String text) { return searching != null && searching.equalsIgnoreCase(text); }

    public static List<LanguageProfile> load(Path directory) {
        var profiles = new ArrayList<LanguageProfile>();
        var gson = new Gson();
        List<String> languages;
        try (var stream = LanguageProfile.class.getResourceAsStream("/assets/betterlogviewer/profiles/index.json")) {
            if (stream == null) throw new IOException("Missing language catalog");
            var catalog = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Catalog.class);
            if (catalog == null || catalog.languages() == null || catalog.languages().isEmpty()) throw new IOException("Empty language catalog");
            languages = catalog.languages();
        } catch (IOException | RuntimeException error) { throw new IllegalStateException("Cannot load parser language catalog", error); }
        for (String language : languages) {
            if (!language.matches("[a-z]{2}(?:-[a-z]{2})?")) throw new IllegalStateException("Invalid bundled language id " + language);
            String resource = "/assets/betterlogviewer/profiles/" + language + ".json";
            try (var stream = LanguageProfile.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Missing " + resource);
                profiles.add(gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), LanguageProfile.class).compile());
            } catch (IOException | RuntimeException error) { throw new IllegalStateException("Cannot load bundled parser profile", error); }
        }
        if (directory != null && Files.isDirectory(directory)) {
            try (var files = Files.list(directory)) {
                for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                    try (var reader = Files.newBufferedReader(path)) {
                        var profile = gson.fromJson(reader, LanguageProfile.class).compile();
                        profiles.removeIf(old -> old.id.equals(profile.id));
                        profiles.add(profile);
                    } catch (IOException | RuntimeException error) {
                        LoggerFactory.getLogger("BetterLogViewer").warn("Ignoring invalid language profile {}", path, error);
                    }
                }
            } catch (IOException error) { LoggerFactory.getLogger("BetterLogViewer").warn("Cannot read language profiles", error); }
        }
        return List.copyOf(profiles);
    }
}

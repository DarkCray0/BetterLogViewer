package me.darkcray_.betterlogviewer.coreprotect;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    public String containerHeader, interactionHeader;
    public transient Pattern headerPattern, rowPattern, pagePattern;
    public transient Pattern containerPattern, interactionPattern;

    public LanguageProfile compile() {
        if (id == null || actions == null || noResults == null) throw new IllegalArgumentException("Incomplete language profile");
        headerPattern = Pattern.compile(header);
        rowPattern = Pattern.compile(row);
        pagePattern = Pattern.compile(page);
        if (containerHeader != null) containerPattern = Pattern.compile(containerHeader);
        if (interactionHeader != null) interactionPattern = Pattern.compile(interactionHeader);
        if (timeUnits != null && timeUnits.values().stream().anyMatch(v -> v == null || v < 1 || v > 31536000)) throw new IllegalArgumentException("Invalid relative time unit");
        for (String name : actions.values()) LogAction.valueOf(name);
        return this;
    }

    public static List<LanguageProfile> load(Path directory) {
        var profiles = new ArrayList<LanguageProfile>();
        var gson = new Gson();
        for (String language : List.of("en", "ru")) {
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

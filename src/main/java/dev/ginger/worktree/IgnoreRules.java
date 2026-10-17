package dev.ginger.worktree;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class IgnoreRules {

    public static final String FILE_NAME = ".gingerignore";

    private record Rule(Pattern pattern, boolean anchored, boolean directoryOnly) {
    }

    private final List<Rule> rules;

    private IgnoreRules(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static IgnoreRules none() {
        return new IgnoreRules(List.of());
    }

    public static IgnoreRules load(Path root) throws IOException {
        Path file = root.resolve(FILE_NAME);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return none();
        }
        return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    public static IgnoreRules parse(List<String> lines) throws IOException {
        List<Rule> rules = new ArrayList<>();
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("!") || line.contains("**") || line.contains("[") || line.contains("\\")) {
                throw new IOException(FILE_NAME + " line " + (n + 1) + ": unsupported pattern '" + line + "'");
            }
            boolean directoryOnly = line.endsWith("/");
            if (directoryOnly) {
                line = line.substring(0, line.length() - 1);
            }
            boolean anchored = line.contains("/");
            if (line.startsWith("/")) {
                line = line.substring(1);
            }
            if (line.isEmpty() || line.startsWith("/") || line.contains("//")) {
                throw new IOException(FILE_NAME + " line " + (n + 1) + ": invalid pattern '" + lines.get(n).strip() + "'");
            }
            rules.add(new Rule(toRegex(line), anchored, directoryOnly));
        }
        return new IgnoreRules(rules);
    }

    public boolean isIgnored(String path) {
        String[] parts = path.split("/");
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                prefix.append('/');
            }
            prefix.append(parts[i]);
            boolean directory = i < parts.length - 1;
            if (matches(prefix.toString(), parts[i], directory)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String path, String name, boolean directory) {
        for (Rule rule : rules) {
            if (rule.directoryOnly() && !directory) {
                continue;
            }
            if (rule.pattern().matcher(rule.anchored() ? path : name).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Pattern toRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> regex.append("[^/]*");
                case '?' -> regex.append("[^/]");
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }
}

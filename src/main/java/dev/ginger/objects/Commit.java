package dev.ginger.objects;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record Commit(ObjectId tree, List<ObjectId> parents, String author, Instant timestamp, String message) {

    public Commit {
        Objects.requireNonNull(tree, "tree");
        parents = List.copyOf(parents);
        if (author == null || author.isBlank() || author.contains("\n")) {
            throw new IllegalArgumentException("invalid author: " + author);
        }
        timestamp = timestamp.truncatedTo(ChronoUnit.SECONDS);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("empty commit message");
        }
        message = message.strip() + "\n";
    }

    public String summary() {
        return message.lines().findFirst().orElse("");
    }

    public StoredObject toObject() {
        StringBuilder text = new StringBuilder();
        text.append("tree ").append(tree).append('\n');
        parents.forEach(p -> text.append("parent ").append(p).append('\n'));
        text.append("author ").append(author).append('\n');
        text.append("date ").append(timestamp).append('\n');
        text.append('\n').append(message);
        return new StoredObject(ObjectType.COMMIT, text.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static Commit fromObject(StoredObject object) throws CorruptObjectException {
        if (object.type() != ObjectType.COMMIT) {
            throw new CorruptObjectException("not a commit: " + object.type().label());
        }
        String text = new String(object.content(), StandardCharsets.UTF_8);
        int split = text.indexOf("\n\n");
        if (split < 0) {
            throw new CorruptObjectException("commit has no message");
        }

        ObjectId tree = null;
        List<ObjectId> parents = new ArrayList<>();
        String author = null;
        Instant timestamp = null;
        try {
            for (String line : text.substring(0, split).split("\n")) {
                int space = line.indexOf(' ');
                String key = space < 0 ? line : line.substring(0, space);
                String value = space < 0 ? "" : line.substring(space + 1);
                switch (key) {
                    case "tree" -> tree = ObjectId.of(value);
                    case "parent" -> parents.add(ObjectId.of(value));
                    case "author" -> author = value;
                    case "date" -> timestamp = Instant.parse(value);
                    default -> throw new CorruptObjectException("unknown commit header: " + key);
                }
            }
            if (tree == null || author == null || timestamp == null) {
                throw new CorruptObjectException("commit is missing required headers");
            }
            return new Commit(tree, parents, author, timestamp, text.substring(split + 2));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new CorruptObjectException(e.getMessage());
        }
    }
}

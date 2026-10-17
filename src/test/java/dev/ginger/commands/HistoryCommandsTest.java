package dev.ginger.commands;

import dev.ginger.TestRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryCommandsTest {

    @TempDir
    Path tmp;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(bytes, true);
    private TestRepo t;

    @BeforeEach
    void setUp() throws IOException {
        t = TestRepo.init(tmp);
    }

    private String output() {
        String text = bytes.toString();
        bytes.reset();
        return text;
    }

    @Test
    void commitUsesConfiguredAuthorAndClock() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);
        CommitCommand commit = new CommitCommand(out, tmp,
                Map.of("GINGER_AUTHOR_NAME", "Ada", "GINGER_AUTHOR_EMAIL", "ada@example.com"), clock);
        t.write("a.txt", "a").add("a.txt");

        assertEquals(0, commit.execute(List.of("-m", "Initial commit")));
        assertTrue(output().matches("\\[main \\(root-commit\\) [0-9a-f]{7}] Initial commit\\R"));

        new LogCommand(out, tmp).execute(List.of());
        String log = output();
        assertTrue(log.contains("Author: Ada <ada@example.com>\n"), log);
        assertTrue(log.contains("Date:   2026-05-01T12:00:00Z\n"), log);

        assertEquals(1, commit.execute(List.of("-m", "again")));
        assertEquals("nothing to commit\n", output());
    }

    @Test
    void logShowsNewestFirst() throws Exception {
        LogCommand log = new LogCommand(out, tmp.resolve("."));
        log.execute(List.of());
        assertEquals("No commits yet on branch main\n", output());

        t.write("a.txt", "1").add("a.txt").commit("first");
        t.write("a.txt", "2").add("a.txt").commit("second");
        t.write("a.txt", "3").add("a.txt").commit("third");

        log.execute(List.of());
        String text = output();
        int third = text.indexOf("    third");
        int second = text.indexOf("    second");
        int first = text.indexOf("    first");
        assertTrue(third >= 0 && third < second && second < first, text);
    }

    @Test
    void diffShowsWorkTreeAndStagedChanges() throws Exception {
        t.write("a.txt", "one\n").add("a.txt").commit("first");
        t.write("a.txt", "two\n");
        DiffCommand diff = new DiffCommand(out, tmp);

        diff.execute(List.of());
        assertTrue(output().contains("-one\n+two\n"));
        diff.execute(List.of("--staged"));
        assertEquals("", output());

        t.add("a.txt");
        diff.execute(List.of());
        assertEquals("", output());
        diff.execute(List.of("--staged"));
        assertTrue(output().contains("-one\n+two\n"));
    }

    @Test
    void statusReportsSections() throws Exception {
        t.write("a.txt", "a").add("a.txt").commit("first");
        t.write("a.txt", "b").write("new.txt", "n");

        new StatusCommand(out, tmp).execute(List.of());

        assertEquals("""
                On branch main

                Changes not staged for commit:
                  modified:   a.txt

                Untracked files:
                  new.txt
                """, output());
    }
}

package dev.ginger.commands;

import dev.ginger.TestRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchCommandsTest {

    @TempDir
    Path tmp;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(bytes, true);
    private TestRepo t;
    private BranchCommand branch;
    private SwitchCommand switchCommand;

    @BeforeEach
    void setUp() throws IOException {
        t = TestRepo.init(tmp);
        branch = new BranchCommand(out, tmp);
        switchCommand = new SwitchCommand(out, tmp);
    }

    private String output() {
        String text = bytes.toString();
        bytes.reset();
        return text;
    }

    @Test
    void noCommitsYet() throws Exception {
        branch.execute(List.of());
        assertEquals("", output());
        assertThrows(IOException.class, () -> branch.execute(List.of("feature")));
    }

    @Test
    void listsBranchesAndMarksCurrent() throws Exception {
        t.write("a.txt", "a").add("a.txt").commit("first");
        branch.execute(List.of("feature-login"));
        branch.execute(List.of());
        assertEquals("  feature-login\n* main\n", output());

        switchCommand.execute(List.of("feature-login"));
        assertEquals("Switched to branch 'feature-login'\n", output());
        branch.execute(List.of());
        assertEquals("* feature-login\n  main\n", output());

        switchCommand.execute(List.of("feature-login"));
        assertEquals("Already on 'feature-login'\n", output());
    }

    @Test
    void rejectsInvalidAndDuplicateNames() throws Exception {
        t.write("a.txt", "a").add("a.txt").commit("first");

        assertThrows(Exception.class, () -> branch.validate(List.of("bad name")));
        assertThrows(Exception.class, () -> branch.validate(List.of("../escape")));
        assertThrows(IOException.class, () -> branch.execute(List.of("main")));
        assertThrows(IOException.class, () -> switchCommand.execute(List.of("missing")));
    }

    @Test
    void checkoutAcceptsShortCommitId() throws Exception {
        String first = t.write("a.txt", "1").add("a.txt").commit("first").abbreviate();
        t.write("a.txt", "2").add("a.txt").commit("second");

        new CheckoutCommand(out, tmp).execute(List.of(first));

        assertTrue(output().startsWith("HEAD is now at " + first + " first"));
        assertEquals("1", t.read("a.txt"));
        branch.execute(List.of());
        assertTrue(output().startsWith("* (HEAD detached at " + first + ")\n"));
    }
}
